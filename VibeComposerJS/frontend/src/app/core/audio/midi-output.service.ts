import { computed, inject, Injectable, signal } from '@angular/core';
import { midiPanValue } from './mix-values';
import type { CompositionTrack } from '../project/track.model';
import { UiPreferencesService } from '../ui/ui-preferences.service';
import type { DrumSlots } from '../music/drum-instruments';
import { drumInstrumentLabel, mapDrumPitch, resolveDrumMapping } from '../music/drum-instruments';

@Injectable({ providedIn: 'root' })
export class MidiOutputService {
  private readonly preferences = inject(UiPreferencesService);
  private access?: MIDIAccess;
  private readonly output = signal<MIDIOutput | null>(null);
  private readonly ports = signal<readonly MIDIOutput[]>([]);
  private readonly pending = signal(false);
  private readonly message = signal('');
  private readonly channels = new Set<number>();
  private readonly configurations = new Map<number, { trackId: string; program: number | null; volume: number; pan: number }>();
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
    if (previous?.program === program && previous.volume === volume && previous.pan === pan) {
      previous.trackId = track.id;
      return;
    }
    this.configurations.set(channel, { trackId: track.id, program, volume, pan });
    this.channels.add(channel);
    if (program !== null) this.send([0xc0 | channel, program]);
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
        for (const channel of this.channels) {
          output.send([0xb0 | channel, 64, 0]);
          output.send([0xb0 | channel, 123, 0]);
          output.send([0xb0 | channel, 120, 0]);
        }
      } catch { /* A removed port may no longer accept messages. */ }
    }
    this.channels.clear();
    this.configurations.clear();
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
