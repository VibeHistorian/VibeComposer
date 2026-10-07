import { Injectable, signal } from '@angular/core';
import type { ArrangedPart, CompositionProject, PhraseNote } from '../project/project.model';
import { layOutTrackPhrase } from '../music/phrase';

type PlaybackState = 'stopped' | 'playing' | 'paused';
interface ScheduledNote extends PhraseNote { readonly trackId: string; readonly role: ArrangedPart; }
interface PartBus { readonly gain: GainNode; readonly pan: StereoPannerNode; }
export interface PlaybackRange { readonly startBeat: number; readonly endBeat: number; }

const SCHEDULER_INTERVAL_MS = 25;
const SCHEDULE_AHEAD_SECONDS = 0.18;
const RELOAD_FADE_SECONDS = 0.012;

@Injectable({ providedIn: 'root' })
export class AudioPlaybackService {
  private readonly playbackState = signal<PlaybackState>('stopped');
  private readonly position = signal(0);
  private readonly duration = signal(0);
  private readonly loopState = signal(false);
  private readonly liveState = signal(false);
  private readonly playbackError = signal('');

  readonly state = this.playbackState.asReadonly();
  readonly beat = this.position.asReadonly();
  readonly durationBeats = this.duration.asReadonly();
  readonly loopEnabled = this.loopState.asReadonly();
  readonly liveEnabled = this.liveState.asReadonly();
  readonly error = this.playbackError.asReadonly();

  private context?: AudioContext;
  private master?: GainNode;
  private noise?: AudioBuffer;
  private scheduler?: ReturnType<typeof setInterval>;
  private originTime = 0;
  private secondsPerBeat = 0.5;
  private selectedLoopRange: PlaybackRange | null = null;
  private mixProject?: CompositionProject;
  private notes: ScheduledNote[] = [];
  private queuedNotes: ScheduledNote[] = [];
  private nextNote = 0;
  private cycle = 0;
  private scheduleSignature = '';
  private readonly buses = new Map<string, PartBus>();
  private readonly programs = new Map<string, number>();
  private readonly activeSources = new Set<AudioScheduledSourceNode>();

  async toggle(project: CompositionProject): Promise<void> {
    if (this.state() === 'playing') {
      this.pause();
      return;
    }
    const resumeAt = this.state() === 'paused' ? this.beat() : 0;
    await this.start(project, resumeAt);
  }

  async start(project: CompositionProject, startBeat = 0): Promise<void> {
    this.playbackError.set('');
    try {
      const context = this.getContext();
      if (context.state === 'suspended') await context.resume();
      this.clearScheduler();
      this.stopSources();
      this.prepareMix(project, context);

      this.secondsPerBeat = 60 / project.tempoBpm;
      this.duration.set(project.arrangement.reduce((sum, section) => sum + section.measures * 4, 0));
      this.notes = this.createSchedule(project);
      this.scheduleSignature = this.signature(project, this.notes);
      const safeStart = this.safeStartBeat(startBeat);
      this.position.set(safeStart);
      this.originTime = context.currentTime - safeStart * this.secondsPerBeat;
      this.resetQueue(safeStart);
      this.playbackState.set('playing');
      this.resumeSustainedNotes(safeStart);
      this.scheduleAhead();
      this.scheduler = setInterval(() => this.tick(), SCHEDULER_INTERVAL_MS);
    } catch (error: unknown) {
      this.playbackState.set('stopped');
      this.playbackError.set(error instanceof Error ? error.message : 'Audio playback could not start.');
      console.error('Audio playback could not start.', error);
    }
  }

  pause(): void {
    if (this.state() !== 'playing' || !this.context) return;
    this.position.set(this.currentSongBeat());
    this.clearScheduler();
    this.stopSources();
    this.playbackState.set('paused');
  }

  stop(): void {
    this.clearScheduler();
    this.stopSources();
    this.position.set(0);
    this.playbackState.set('stopped');
  }

  toggleLoop(): void {
    const beat = this.state() === 'playing' ? this.currentSongBeat() : this.beat();
    const enabled = !this.loopEnabled();
    this.loopState.set(enabled);
    if (this.state() === 'playing') this.restartAt(this.safeStartBeat(beat));
  }

  setLoopRange(range: PlaybackRange | null): void {
    if (range?.startBeat === this.selectedLoopRange?.startBeat && range?.endBeat === this.selectedLoopRange?.endBeat) return;
    const beat = this.state() === 'playing' ? this.currentSongBeat() : this.beat();
    this.selectedLoopRange = range;
    if (this.loopEnabled() && this.state() === 'playing') {
      this.restartAt(this.safeStartBeat(beat));
    }
  }

  toggleLive(): void {
    this.liveState.set(!this.liveEnabled());
  }

  /** Replace queued audio at the current musical position without pausing the transport. */
  reload(project: CompositionProject): void {
    if (!this.liveEnabled() || this.state() !== 'playing' || !this.context) return;
    // Build first so generation time does not leave a gap in the old audio.
    const notes = this.createSchedule(project);
    const signature = this.signature(project, notes);
    this.updateMix(project);
    if (signature === this.scheduleSignature) return;

    const beat = this.currentSongBeat();
    const duration = project.arrangement.reduce((sum, section) => sum + section.measures * 4, 0);
    if (duration <= 0 || (!this.loopEnabled() && beat >= duration)) {
      this.stop();
      this.duration.set(duration);
      this.position.set(duration);
      return;
    }
    this.secondsPerBeat = 60 / project.tempoBpm;
    this.duration.set(duration);
    this.notes = notes;
    this.scheduleSignature = signature;
    this.restartAt(this.safeStartBeat(beat % duration));
  }

  updateMix(project: CompositionProject): void {
    this.mixProject = project;
    if (!this.context || this.state() === 'stopped') return;
    const anySolo = project.tracks.some((track) => track.mix.solo);
    const now = this.context.currentTime;
    for (const track of project.tracks) {
      const settings = track.mix;
      this.programs.set(track.id, settings.program);
      const audible = !settings.muted && (!anySolo || settings.solo);
      const bus = this.buses.get(track.id);
      if (!bus) continue;
      bus.gain.gain.setTargetAtTime(audible ? settings.volumePercent / 100 : 0, now, 0.02);
      bus.pan.pan.setTargetAtTime(settings.panPercent / 100, now, 0.02);
    }
  }

  private getContext(): AudioContext {
    if (this.context) return this.context;
    if (!globalThis.AudioContext) {
      throw new Error('This browser does not support Web Audio playback.');
    }
    this.context = new AudioContext();
    return this.context;
  }

  private prepareMix(project: CompositionProject, context: AudioContext, crossfade = false): void {
    this.mixProject = project;
    const oldBuses = [...this.buses.values()];
    const oldMaster = this.master;
    const disconnect = () => {
      for (const bus of oldBuses) {
        bus.pan.disconnect();
        bus.gain.disconnect();
      }
      oldMaster?.disconnect();
    };
    if (crossfade && oldMaster) {
      oldMaster.gain.cancelScheduledValues(context.currentTime);
      oldMaster.gain.setValueAtTime(oldMaster.gain.value, context.currentTime);
      oldMaster.gain.linearRampToValueAtTime(0, context.currentTime + RELOAD_FADE_SECONDS);
      setTimeout(disconnect, RELOAD_FADE_SECONDS * 1000 + SCHEDULER_INTERVAL_MS);
    } else {
      disconnect();
    }
    this.buses.clear();
    this.programs.clear();
    this.master = context.createGain();
    this.master.gain.value = crossfade ? 0 : 0.8;
    if (crossfade) this.master.gain.linearRampToValueAtTime(0.8, context.currentTime + RELOAD_FADE_SECONDS);
    this.master.connect(context.destination);
    const anySolo = project.tracks.some((track) => track.mix.solo);

    for (const track of project.tracks) {
      const settings = track.mix;
      this.programs.set(track.id, settings.program);
      const audible = !settings.muted && (!anySolo || settings.solo);
      const gain = context.createGain();
      gain.gain.value = audible ? settings.volumePercent / 100 : 0;
      const pan = context.createStereoPanner();
      pan.pan.value = settings.panPercent / 100;
      gain.connect(pan);
      pan.connect(this.master);
      this.buses.set(track.id, { gain, pan });
    }
  }

  private createSchedule(project: CompositionProject): ScheduledNote[] {
    return project.tracks.flatMap((track) => layOutTrackPhrase(project, track)
      .map((note) => ({ ...note, trackId: track.id, role: track.role })))
      .sort((left, right) => left.startBeat - right.startBeat || left.midi - right.midi);
  }

  private signature(project: CompositionProject, notes: readonly ScheduledNote[]): string {
    // Compare audible events, ignoring labels and note IDs; mix controls already update their buses.
    return JSON.stringify([
      project.tempoBpm,
      project.arrangement.reduce((sum, section) => sum + section.measures * 4, 0),
      project.tracks.map((track) => [track.id, track.role, track.mix.program]),
      notes.map((note) => [note.trackId, note.role, note.startBeat, note.durationBeats, note.midi, note.velocity]),
    ]);
  }

  private resumeSustainedNotes(beat: number): void {
    const { endBeat } = this.loopBounds();
    for (const note of this.queuedNotes) {
      if (note.role === 'drums') continue;
      const noteEnd = this.loopEnabled() ? Math.min(endBeat, note.startBeat + note.durationBeats)
        : note.startBeat + note.durationBeats;
      const remaining = noteEnd - beat;
      if (note.startBeat < beat && remaining > 0) {
        this.scheduleNote({ ...note, durationBeats: remaining }, beat);
      }
    }
  }

  private tick(): void {
    if (!this.context || this.state() !== 'playing') return;
    const absoluteBeat = this.currentAbsoluteBeat();
    const stopAt = this.loopEnabled() ? Number.POSITIVE_INFINITY : this.duration();
    if (absoluteBeat >= stopAt) {
      this.clearScheduler();
      this.stopSources();
      this.position.set(this.duration());
      this.playbackState.set('stopped');
      return;
    }
    this.position.set(this.currentSongBeat());
    this.scheduleAhead();
  }

  private scheduleAhead(): void {
    if (!this.context || !this.master || this.duration() <= 0) return;
    const currentBeat = this.currentAbsoluteBeat();
    const horizonBeat = currentBeat + SCHEDULE_AHEAD_SECONDS / this.secondsPerBeat;
    const { startBeat, endBeat } = this.loopBounds();
    const cycleBeats = endBeat - startBeat;
    let scheduled = 0;
    while (scheduled < 5000) {
      if (this.nextNote >= this.queuedNotes.length) {
        if (!this.loopEnabled() || this.queuedNotes.length === 0) return;
        this.cycle++;
        this.nextNote = 0;
      }
      const note = this.queuedNotes[this.nextNote];
      const absoluteStartBeat = note.startBeat + this.cycle * cycleBeats;
      if (absoluteStartBeat > horizonBeat) return;
      this.nextNote++;
      scheduled++;
      if (absoluteStartBeat < currentBeat - 0.04) continue;
      this.scheduleNote(this.loopEnabled()
        ? { ...note, durationBeats: Math.min(note.durationBeats, endBeat - note.startBeat) } : note, absoluteStartBeat);
    }
  }

  private scheduleNote(note: ScheduledNote, absoluteBeat: number): void {
    if (!this.context) return;
    const context = this.context;
    const bus = this.buses.get(note.trackId);
    if (!bus) return;
    const startTime = Math.max(context.currentTime + 0.003, this.originTime + absoluteBeat * this.secondsPerBeat);
    const duration = Math.max(0.035, note.durationBeats * this.secondsPerBeat);
    if (note.role === 'drums' && note.midi === 36) {
      this.scheduleKick(bus.gain, note.velocity, startTime, duration);
    } else if (note.role === 'drums') {
      this.scheduleNoise(bus.gain, note.midi === 38 ? 'snare' : 'hat', note.velocity, startTime, duration);
    } else {
      this.scheduleTone(bus.gain, note, startTime, duration);
    }
  }

  private scheduleTone(destination: AudioNode, note: ScheduledNote, start: number, duration: number): void {
    if (!this.context) return;
    const context = this.context;
    const oscillator = context.createOscillator();
    const envelope = context.createGain();
    oscillator.type = this.oscillatorType(note.role, this.programs.get(note.trackId) ?? 0);
    oscillator.frequency.value = 440 * 2 ** ((note.midi - 69) / 12);
    const release = Math.min(0.09, duration * 0.3);
    const peak = Math.max(0.0001, note.velocity / 127 * 0.18);
    envelope.gain.setValueAtTime(0.0001, start);
    envelope.gain.linearRampToValueAtTime(peak, start + 0.008);
    envelope.gain.setValueAtTime(peak, Math.max(start + 0.009, start + duration - release));
    envelope.gain.exponentialRampToValueAtTime(0.0001, start + duration);
    oscillator.connect(envelope);
    envelope.connect(destination);
    this.startSource(oscillator, start, start + duration + 0.02, envelope);
  }

  private scheduleKick(destination: AudioNode, velocity: number, start: number, duration: number): void {
    if (!this.context) return;
    const oscillator = this.context.createOscillator();
    const envelope = this.context.createGain();
    oscillator.type = 'sine';
    oscillator.frequency.setValueAtTime(125, start);
    oscillator.frequency.exponentialRampToValueAtTime(43, start + Math.min(0.12, duration));
    const peak = Math.max(0.0001, velocity / 127 * 0.4);
    envelope.gain.setValueAtTime(peak, start);
    envelope.gain.exponentialRampToValueAtTime(0.0001, start + Math.min(0.2, duration + 0.04));
    oscillator.connect(envelope);
    envelope.connect(destination);
    this.startSource(oscillator, start, start + Math.min(0.2, duration + 0.04), envelope);
  }

  private scheduleNoise(destination: AudioNode, type: 'snare' | 'hat', velocity: number, start: number, duration: number): void {
    if (!this.context) return;
    const source = this.context.createBufferSource();
    const envelope = this.context.createGain();
    const filter = this.context.createBiquadFilter();
    source.buffer = this.getNoiseBuffer();
    filter.type = 'highpass';
    filter.frequency.value = type === 'hat' ? 6500 : 1500;
    const soundDuration = Math.min(type === 'hat' ? 0.055 : 0.16, Math.max(0.035, duration));
    const peak = Math.max(0.0001, velocity / 127 * (type === 'hat' ? 0.09 : 0.22));
    envelope.gain.setValueAtTime(peak, start);
    envelope.gain.exponentialRampToValueAtTime(0.0001, start + soundDuration);
    source.connect(filter);
    filter.connect(envelope);
    envelope.connect(destination);
    this.startSource(source, start, start + soundDuration + 0.01, filter, envelope);
  }

  private startSource(source: AudioScheduledSourceNode, start: number, stop: number, ...nodes: AudioNode[]): void {
    this.activeSources.add(source);
    source.onended = () => {
      this.activeSources.delete(source);
      source.disconnect();
      for (const node of nodes) node.disconnect();
    };
    source.start(start);
    source.stop(stop);
  }

  private getNoiseBuffer(): AudioBuffer {
    if (this.noise) return this.noise;
    if (!this.context) throw new Error('Audio is not initialized.');
    const length = Math.floor(this.context.sampleRate * 0.2);
    const buffer = this.context.createBuffer(1, length, this.context.sampleRate);
    const data = buffer.getChannelData(0);
    for (let index = 0; index < length; index++) data[index] = Math.random() * 2 - 1;
    this.noise = buffer;
    return buffer;
  }

  private oscillatorType(part: ArrangedPart, program: number): OscillatorType {
    if (part === 'bass' || (program >= 24 && program <= 39)) return 'triangle';
    if (program <= 15) return 'sine';
    if (program <= 23) return 'square';
    if (program >= 40 && program <= 55) return 'sawtooth';
    if (program >= 56 && program <= 79) return 'square';
    if (program >= 80 && program <= 87) return 'sawtooth';
    if (program >= 88 && program <= 95) return 'triangle';
    return part === 'arpeggio' ? 'square' : 'sine';
  }

  private currentAbsoluteBeat(): number {
    return this.context ? Math.max(0, (this.context.currentTime - this.originTime) / this.secondsPerBeat) : 0;
  }

  private loopBounds(): PlaybackRange {
    const duration = this.duration();
    const startBeat = Math.max(0, this.selectedLoopRange?.startBeat ?? 0);
    const endBeat = Math.min(duration, this.selectedLoopRange?.endBeat ?? duration);
    return endBeat > startBeat ? { startBeat, endBeat } : { startBeat: 0, endBeat: duration };
  }

  private currentSongBeat(): number {
    const beat = this.currentAbsoluteBeat();
    const { startBeat, endBeat } = this.loopBounds();
    return this.loopEnabled() && endBeat > startBeat && beat >= endBeat
      ? startBeat + (beat - startBeat) % (endBeat - startBeat)
      : Math.min(beat, this.duration());
  }

  private safeStartBeat(beat: number): number {
    if (this.loopEnabled()) {
      const { startBeat, endBeat } = this.loopBounds();
      return beat >= startBeat && beat < endBeat ? beat : startBeat;
    }
    return beat >= this.duration() ? 0 : Math.max(0, beat);
  }

  private resetQueue(beat: number): void {
    const { startBeat, endBeat } = this.loopBounds();
    this.queuedNotes = this.loopEnabled()
      ? this.notes.filter((note) => note.startBeat >= startBeat && note.startBeat < endBeat) : this.notes;
    this.nextNote = this.queuedNotes.findIndex((note) => note.startBeat >= beat);
    if (this.nextNote < 0) this.nextNote = this.queuedNotes.length;
    this.cycle = 0;
  }

  private restartAt(beat: number): void {
    if (!this.context || !this.mixProject) return;
    const now = this.context.currentTime;
    this.stopSources(now + RELOAD_FADE_SECONDS);
    this.prepareMix(this.mixProject, this.context, true);
    this.originTime = now - beat * this.secondsPerBeat;
    this.position.set(beat);
    this.resetQueue(beat);
    this.resumeSustainedNotes(beat);
    this.scheduleAhead();
  }

  private clearScheduler(): void {
    if (this.scheduler !== undefined) {
      clearInterval(this.scheduler);
      this.scheduler = undefined;
    }
  }

  private stopSources(stopTime?: number): void {
    for (const source of this.activeSources) {
      try { source.stop(stopTime); } catch { /* It may have ended between scheduler ticks. */ }
    }
    if (stopTime === undefined) this.activeSources.clear();
  }
}
