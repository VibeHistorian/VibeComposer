import { computed, inject, Injectable, signal } from '@angular/core';
import { midiPanValue } from './mix-values';
import type { CompositionTrack } from '../project/track.model';
import { UiPreferencesService } from '../ui/ui-preferences.service';
import type { DrumSlots } from '../music/drum-instruments';
import { drumInstrumentLabel, mapDrumPitch, resolveDrumMapping } from '../music/drum-instruments';
import type { GlideLane } from '../music/phrase-glides';
import { MIDI_EXPRESSION_CC, PITCH_BEND_SENSITIVITY } from '../music/midi-glides';
import { midiVoicingControls, type VoiceMode } from '../music/track-voicing';

@Injectable({ providedIn: 'root' })
export class MidiOutputService {
  private readonly preferences = inject(UiPreferencesService);
  private access?: MIDIAccess;
  private readonly output = signal<MIDIOutput | null>(null);
  private readonly ports = signal<readonly MIDIOutput[]>([]);
  private readonly pending = signal(false);
  private readonly message = signal('');
  private readonly channels = new Set<number>();
  private readonly glideValues = new Map<number, Partial<Record<GlideLane, number>>>();
  private readonly configurations = new Map<number, { trackId: string; program: number | null; volume: number; pan: number;
    voiceMode: VoiceMode; glideMs: number }>();
  readonly selectedOutput = this.output.asReadonly();
  readonly drumMapping = computed(() => resolveDrumMapping(this.preferences.drumMapping(), this.selectedOutput() !== null));
  drumLabel(pitch: number, slots?: DrumSlots): string { return drumInstrumentLabel(pitch, this.drumMapping(), slots); }
  readonly outputs = this.ports.asReadonly();
  readonly busy = this.pending.asReadonly();
  readonly error = this.message.asReadonly();
  readonly label = computed(() => this.output()?.name || (this.output() ? 'MIDI output' : 'MIDI Port...'));
  readonly supported = typeof navigator !== 'undefined' && typeof navigator.requestMIDIAccess === 'function';
  readonly ready = signal(false);

  constructor() {
    globalThis.addEventListener?.('pagehide', this.silence);
  }

  async refresh(): Promise<void> {
    if (this.busy()) return;
    this.message.set('');
    if (!this.supported) {
      this.message.set(globalThis.isSecureContext === false
        ? 'MIDI requires HTTPS or localhost.' : 'Web MIDI is unavailable in this browser.');
      return;
    }
    this.pending.set(true);
    try {
      if (!this.access) {
        this.access = await navigator.requestMIDIAccess({ sysex: false });
        this.access.addEventListener('statechange', this.refreshPorts);
      }
      this.refreshPorts();
      this.ready.set(true);
    } catch (error) {
      this.message.set(error instanceof DOMException && error.name === 'NotAllowedError'
        ? 'MIDI access was denied. Allow MIDI in your browser’s site settings, then retry.'
        : 'Could not access MIDI devices. Try again.');
    } finally {
      this.pending.set(false);
    }
  }

  async select(id: string | null): Promise<void> {
    if (this.busy()) return;
    const next = this.outputs().find(port => port.id === id) ?? null;
    if (id !== null && !next) return;
    if (next === this.output()) return;
    this.pending.set(true);
    this.message.set('');
    try {
      if (next) {
        await next.open();
        if (next.state !== 'connected') throw new Error('Disconnected');
      }
      const previous = this.output();
      this.silence();
      this.output.set(next);
      if (previous) void previous.close().catch(() => {});
    } catch {
      this.message.set('Could not open that MIDI port. Check its connection or try another port.');
    } finally {
      this.pending.set(false);
    }
  }

  /** Web MIDI timestamps use performance.now(), rather than AudioContext time. */
  schedule(track: CompositionTrack, pitch: number, velocity: number, start: number, duration: number, slots?: DrumSlots): void {
    if (track.role === 'drums') pitch = mapDrumPitch(pitch, this.drumMapping(), slots);
    const channel = track.midiChannel - 1;
    this.configure(track);
    this.send([0x90 | channel, pitch, velocity], start);
    this.send([0x80 | channel, pitch, 0], start + duration * 1000);
  }

  scheduleGlide(channelNumber: number, lane: GlideLane, value: number, timestamp: number): void {
    const channel = channelNumber - 1;
    const values = this.glideValues.get(channel) ?? {};
    if (values[lane] === value) return;
    const firstPitch = lane === 'pitch' && values.pitch === undefined;
    values[lane] = value;
    this.glideValues.set(channel, values);
    if (firstPitch) for (const [controller, setting] of PITCH_BEND_SENSITIVITY) {
      this.send([0xb0 | channel, controller, setting], timestamp);
    }
    if (lane === 'pitch') {
      const unsigned = value + 8192;
      this.send([0xe0 | channel, unsigned & 127, unsigned >> 7], timestamp);
    } else this.send([0xb0 | channel, MIDI_EXPRESSION_CC, value], timestamp);
  }

  hold(track: CompositionTrack, pitch: number, velocity: number, slots?: DrumSlots): () => void {
    if (track.role === 'drums') pitch = mapDrumPitch(pitch, this.drumMapping(), slots);
    const output = this.output(), channel = track.midiChannel - 1;
    this.configure(track); this.send([0x90 | channel, pitch, velocity]);
    return () => { try { output?.send([0x80 | channel, pitch, 0]); } catch { /* Disconnected input preview output. */ } };
  }

  configure(track: CompositionTrack, audible = true): void {
    if (!this.output()) return;
    const channel = track.midiChannel - 1;
    const program = track.role === 'drums' ? null : track.mix.program;
    const volume = audible ? this.channelVolume(track.mix.volumePercent) : 0;
    const pan = midiPanValue(track.mix.panPercent);
    const previous = this.configurations.get(channel);
    const voiceMode = track.voiceMode ?? 'poly', glideMs = voiceMode === 'poly' ? 0 : track.glideMs ?? 0;
    const sameMix = previous?.program === program && previous.volume === volume && previous.pan === pan;
    const programChanged = previous?.program !== program;
    if (program !== null && (programChanged || !sameMix && voiceMode === 'poly')) this.send([0xc0 | channel, program]);
    if ((track.voiceMode !== undefined || previous?.voiceMode && previous.voiceMode !== 'poly')
      && (previous?.voiceMode !== voiceMode || previous.glideMs !== glideMs || programChanged)) {
      const controls = midiVoicingControls({ voiceMode, glideMs });
      if (!previous || programChanged || (previous.voiceMode === 'poly') !== (voiceMode === 'poly')) {
        const [controller, value] = controls[0];
        this.send([0xb0 | channel, controller, value]);
      }
      for (const [controller, value] of controls.slice(1)) this.send([0xb0 | channel, controller, value]);
    }
    if (sameMix) {
      previous.trackId = track.id;
      previous.voiceMode = voiceMode;
      previous.glideMs = glideMs;
      return;
    }
    this.configurations.set(channel, { trackId: track.id, program, volume, pan, voiceMode, glideMs });
    this.channels.add(channel);
    this.send([0xb0 | channel, 7, volume]);
    if (track.role !== 'drums' || previous?.pan !== pan) this.send([0xb0 | channel, 10, pan]);
  }

  updateVolume(track: CompositionTrack, volumePercent: number): void {
    const channel = track.midiChannel - 1;
    const configuration = this.configurations.get(channel);
    // A shared channel follows the track that last configured it.
    if (!configuration || configuration.trackId !== track.id) return;
    const volume = this.channelVolume(volumePercent);
    if (configuration.volume === volume) return;
    configuration.volume = volume;
    this.send([0xb0 | channel, 7, volume]);
  }

  private channelVolume(volumePercent: number): number {
    return Math.max(0, Math.min(127, Math.round(volumePercent * 127 / 100)));
  }

  readonly silence = (): void => {
    const output = this.output();
    if (output) {
      try {
        // TypeScript's DOM declarations omit this standard Web MIDI method.
        (output as MIDIOutput & { clear(): void }).clear();
        for (const channel of new Set([...this.channels, ...this.glideValues.keys()])) {
          if (this.channels.has(channel)) {
            output.send([0xb0 | channel, 64, 0]);
            output.send([0xb0 | channel, 123, 0]);
            output.send([0xb0 | channel, 120, 0]);
          }
          const glides = this.glideValues.get(channel);
          const voicing = this.configurations.get(channel)?.voiceMode;
          if (voicing && voicing !== 'poly') for (const [controller, value] of midiVoicingControls({})) {
            output.send([0xb0 | channel, controller, value]);
          }
          if (glides?.pitch !== undefined) output.send([0xe0 | channel, 0, 64]);
          if (glides?.volume !== undefined) output.send([0xb0 | channel, MIDI_EXPRESSION_CC, 127]);
        }
      } catch { /* A removed port may no longer accept messages. */ }
    }
    this.channels.clear();
    this.configurations.clear();
    this.glideValues.clear();
  };

  ngOnDestroy(): void {
    globalThis.removeEventListener?.('pagehide', this.silence);
    this.access?.removeEventListener('statechange', this.refreshPorts);
    this.silence();
    void this.output()?.close().catch(() => {});
  }

  private readonly refreshPorts = (): void => {
    this.ports.set(Array.from(this.access?.outputs.values() ?? []).filter(port => port.state === 'connected'));
    const output = this.output();
    if (output && !this.outputs().includes(output)) {
      this.silence();
      this.output.set(null);
      this.message.set('MIDI port disconnected. Using the internal synth.');
    }
  };

  private send(data: number[], timestamp?: number): void {
    try {
      this.output()?.send(data, timestamp);
    } catch {
      this.silence();
      this.output.set(null);
      this.message.set('MIDI output failed. Using the internal synth.');
    }
  }
}
