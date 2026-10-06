import { Injectable, signal } from '@angular/core';
import type { ArrangedPart, CompositionProject, PhraseNote } from '../project/project.model';
import { ARRANGED_PARTS } from '../project/project.model';
import { layOutPhrase, phraseForProject } from '../music/phrase';

type PlaybackState = 'stopped' | 'playing' | 'paused';
interface ScheduledNote extends PhraseNote { readonly part: ArrangedPart; }
interface PartBus { readonly gain: GainNode; readonly pan: StereoPannerNode; }

const SCHEDULER_INTERVAL_MS = 25;
const SCHEDULE_AHEAD_SECONDS = 0.18;

@Injectable({ providedIn: 'root' })
export class AudioPlaybackService {
  private readonly playbackState = signal<PlaybackState>('stopped');
  private readonly position = signal(0);
  private readonly duration = signal(0);
  private readonly loopState = signal(false);
  private readonly playbackError = signal('');

  readonly state = this.playbackState.asReadonly();
  readonly beat = this.position.asReadonly();
  readonly durationBeats = this.duration.asReadonly();
  readonly loopEnabled = this.loopState.asReadonly();
  readonly error = this.playbackError.asReadonly();

  private context?: AudioContext;
  private master?: GainNode;
  private noise?: AudioBuffer;
  private scheduler?: ReturnType<typeof setInterval>;
  private originTime = 0;
  private secondsPerBeat = 0.5;
  private stopAtBeat: number | null = null;
  private notes: ScheduledNote[] = [];
  private nextNote = 0;
  private cycle = 0;
  private readonly buses = new Map<ArrangedPart, PartBus>();
  private readonly programs = new Map<ArrangedPart, number>();
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
      const safeStart = startBeat >= this.duration() ? 0 : Math.max(0, startBeat);
      this.position.set(safeStart);
      this.originTime = context.currentTime - safeStart * this.secondsPerBeat;
      this.nextNote = this.notes.findIndex((note) => note.startBeat >= safeStart - 0.0001);
      if (this.nextNote < 0) this.nextNote = this.notes.length;
      this.cycle = 0;
      this.stopAtBeat = null;
      this.playbackState.set('playing');
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
    const absoluteBeat = this.currentAbsoluteBeat();
    const beat = this.loopEnabled() && this.duration() > 0
      ? absoluteBeat % this.duration()
      : Math.min(absoluteBeat, this.duration());
    this.position.set(beat);
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
    const enabled = !this.loopEnabled();
    this.loopState.set(enabled);
    if (enabled) {
      this.stopAtBeat = null;
    } else if (this.state() === 'playing' && this.duration() > 0) {
      this.stopAtBeat = (Math.floor(this.currentAbsoluteBeat() / this.duration()) + 1) * this.duration();
    }
  }

  updateMix(project: CompositionProject): void {
    if (!this.context || this.state() === 'stopped') return;
    const anySolo = ARRANGED_PARTS.some((part) => project.mix[part].solo);
    const now = this.context.currentTime;
    for (const part of ARRANGED_PARTS) {
      const settings = project.mix[part];
      this.programs.set(part, settings.program);
      const audible = !settings.muted && (!anySolo || settings.solo);
      const bus = this.buses.get(part);
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

  private prepareMix(project: CompositionProject, context: AudioContext): void {
    for (const bus of this.buses.values()) {
      bus.pan.disconnect();
      bus.gain.disconnect();
    }
    this.buses.clear();
    this.master?.disconnect();
    this.master = context.createGain();
    this.master.gain.value = 0.8;
    this.master.connect(context.destination);
    const anySolo = ARRANGED_PARTS.some((part) => project.mix[part].solo);

    for (const part of ARRANGED_PARTS) {
      const settings = project.mix[part];
      this.programs.set(part, settings.program);
      const audible = !settings.muted && (!anySolo || settings.solo);
      const gain = context.createGain();
      gain.gain.value = audible ? settings.volumePercent / 100 : 0;
      const pan = context.createStereoPanner();
      pan.pan.value = settings.panPercent / 100;
      gain.connect(pan);
      pan.connect(this.master);
      this.buses.set(part, { gain, pan });
    }
  }

  private createSchedule(project: CompositionProject): ScheduledNote[] {
    return ARRANGED_PARTS.flatMap((part) => {
      return layOutPhrase(project, part, phraseForProject(project, part))
        .map((note) => ({ ...note, part }));
    }).sort((left, right) => left.startBeat - right.startBeat || left.midi - right.midi);
  }

  private tick(): void {
    if (!this.context || this.state() !== 'playing') return;
    const absoluteBeat = this.currentAbsoluteBeat();
    const stopAt = this.loopEnabled() ? Number.POSITIVE_INFINITY : this.stopAtBeat ?? this.duration();
    if (absoluteBeat >= stopAt) {
      this.clearScheduler();
      this.stopSources();
      this.position.set(this.duration());
      this.playbackState.set('stopped');
      return;
    }
    this.position.set(this.loopEnabled() && this.duration() > 0 ? absoluteBeat % this.duration() : absoluteBeat);
    this.scheduleAhead();
  }

  private scheduleAhead(): void {
    if (!this.context || !this.master || this.duration() <= 0) return;
    const currentBeat = this.currentAbsoluteBeat();
    const horizonBeat = currentBeat + SCHEDULE_AHEAD_SECONDS / this.secondsPerBeat;
    let scheduled = 0;
    while (scheduled < 5000) {
      if (this.nextNote >= this.notes.length) {
        if (!this.loopEnabled() || this.notes.length === 0) return;
        this.cycle++;
        this.nextNote = 0;
      }
      const note = this.notes[this.nextNote];
      const absoluteStartBeat = note.startBeat + this.cycle * this.duration();
      if (absoluteStartBeat > horizonBeat) return;
      this.nextNote++;
      scheduled++;
      if (absoluteStartBeat < currentBeat - 0.04) continue;
      this.scheduleNote(note, absoluteStartBeat);
    }
  }

  private scheduleNote(note: ScheduledNote, absoluteBeat: number): void {
    if (!this.context) return;
    const context = this.context;
    const bus = this.buses.get(note.part);
    if (!bus) return;
    const startTime = Math.max(context.currentTime + 0.003, this.originTime + absoluteBeat * this.secondsPerBeat);
    const duration = Math.max(0.035, note.durationBeats * this.secondsPerBeat);
    if (note.part === 'drums' && note.midi === 36) {
      this.scheduleKick(bus.gain, note.velocity, startTime, duration);
    } else if (note.part === 'drums') {
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
    oscillator.type = this.oscillatorType(note.part, this.programs.get(note.part) ?? 0);
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

  private clearScheduler(): void {
    if (this.scheduler !== undefined) {
      clearInterval(this.scheduler);
      this.scheduler = undefined;
    }
  }

  private stopSources(): void {
    for (const source of this.activeSources) {
      try { source.stop(); } catch { /* It may have ended between scheduler ticks. */ }
    }
    this.activeSources.clear();
  }
}
