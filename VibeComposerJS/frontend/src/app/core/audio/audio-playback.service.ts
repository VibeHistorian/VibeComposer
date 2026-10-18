import { effect, inject, Injectable, signal, untracked } from '@angular/core';
import { MidiOutputService } from './midi-output.service';
import type { ArrangedPart, CompositionProject, CompositionTrack, PhraseNote } from '../project/project.model';
import { layOutTrackPhrase } from '../music/phrase';
import { TinySynthEngine } from './tiny-synth-engine';
import { beatToSeconds, secondsToBeat, projectTempoMap, sectionDurationBeats, type TempoPoint } from '../music/section-context';

type PlaybackState = 'stopped' | 'playing' | 'paused';
interface ScheduledNote extends PhraseNote { readonly trackId: string; readonly role: ArrangedPart; }
interface PartBus { readonly gain: GainNode; readonly pan: StereoPannerNode; }
export interface PlaybackRange { readonly startBeat: number; readonly endBeat: number; }

const SCHEDULER_INTERVAL_MS = 25;
const SCHEDULE_AHEAD_SECONDS = 0.18;
const RELOAD_FADE_SECONDS = 0.012;

@Injectable({ providedIn: 'root' })
export class AudioPlaybackService {
  private readonly midi = inject(MidiOutputService);
  private readonly playbackState = signal<PlaybackState>('stopped');
  private readonly position = signal(0);
  private readonly duration = signal(0);
  private readonly loopState = signal(true);
  private readonly liveState = signal(true);
  private readonly playbackError = signal('');

  readonly state = this.playbackState.asReadonly();
  readonly beat = this.position.asReadonly();
  readonly durationBeats = this.duration.asReadonly();
  readonly loopEnabled = this.loopState.asReadonly();
  readonly liveEnabled = this.liveState.asReadonly();
  readonly error = this.playbackError.asReadonly();

  private context?: AudioContext;
  private master?: GainNode;
  private synth?: TinySynthEngine;
  private scheduler?: ReturnType<typeof setInterval>;
  private originTime = 0;
  private tempoMap: readonly TempoPoint[] = [{ beat: 0, seconds: 0, bpm: 120 }];
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

  constructor() {
    effect(() => {
      this.midi.selectedOutput();
      this.midi.drumMapping();
      untracked(() => {
        if (this.state() === 'playing') this.restartAt(this.currentSongBeat());
        else this.stopSources();
      });
    });
  }

  async audition(track: CompositionTrack, notes: readonly PhraseNote[]): Promise<void> {
    if (!notes.length) return;
    try {
      const context = this.getContext();
      if (context.state === 'suspended') await context.resume();
      if (this.midi.selectedOutput()) {
        const pitches = new Set<number>();
        for (const note of notes) {
          if (pitches.has(note.midi)) continue;
          pitches.add(note.midi);
          this.midi.schedule(track, note.midi, note.velocity, performance.now() + 3, 0.3);
        }
        return;
      }
      const gain = context.createGain();
      const pan = context.createStereoPanner();
      gain.gain.value = 0.8 * track.mix.volumePercent / 100;
      pan.pan.value = track.mix.panPercent / 100;
      gain.connect(pan);
      pan.connect(context.destination);
      const pitches = new Set<number>();
      const voices = notes.filter(note => {
        if (pitches.has(note.midi)) return false;
        pitches.add(note.midi);
        return true;
      }).flatMap(note => this.synth!.schedule(gain, track.mix.program, track.role === 'drums',
        note.midi, note.velocity, context.currentTime + 0.003, 0.3));
      let remaining = voices.length;
      const disconnect = () => { gain.disconnect(); pan.disconnect(); };
      if (!remaining) disconnect();
      for (const voice of voices) {
        voice.source.onended = () => {
          voice.disconnect();
          voice.source.disconnect();
          voice.envelope.disconnect();
          if (--remaining === 0) disconnect();
        };
        voice.source.stop(voice.stopTime);
      }
    } catch (error: unknown) {
      this.playbackError.set(error instanceof Error ? error.message : 'Note preview could not start.');
    }
  }

  async toggle(project: CompositionProject): Promise<void> {
    if (this.state() === 'playing') {
      this.pause();
      return;
    }
    const resumeAt = this.state() === 'paused' || this.beat() < this.duration() ? this.beat() : 0;
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

      this.tempoMap = projectTempoMap(project);
      this.duration.set(project.arrangement.reduce((sum, section) => sum + sectionDurationBeats(project, section), 0));
      this.notes = this.createSchedule(project);
      this.scheduleSignature = this.signature(project, this.notes);
      const safeStart = this.safeStartBeat(startBeat);
      this.position.set(safeStart);
      this.originTime = context.currentTime - this.absoluteSeconds(safeStart);
      this.resetQueue(safeStart);
      this.playbackState.set('playing');
      this.resumeSustainedNotes(safeStart);
      this.scheduleAhead();
      this.scheduler = setInterval(() => this.tick(), SCHEDULER_INTERVAL_MS);
    } catch (error: unknown) {
      this.clearScheduler();
      this.stopSources();
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

  /** Seek without starting audio; leaving the loop range disables looping. */
  seek(project: CompositionProject, targetBeat: number): void {
    if (!Number.isFinite(targetBeat)) return;
    const playing = this.state() === 'playing';
    if (!playing) this.duration.set(project.arrangement.reduce((sum, section) => sum + sectionDurationBeats(project, section), 0));
    const beat = Math.min(this.duration(), Math.max(0, targetBeat));
    const { startBeat, endBeat } = this.loopBounds();
    if (this.loopEnabled() && (beat < startBeat || beat >= endBeat)) this.loopState.set(false);
    if (playing) this.restartAt(beat);
    else this.position.set(beat);
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

  /** Keep the transport state, but restart with the newly composed project and loop origin. */
  restartAfterCompose(project: CompositionProject): void {
    const startBeat = this.loopEnabled() ? this.loopBounds().startBeat : 0;
    if (this.state() === 'playing') void this.start(project, startBeat);
    else this.seek(project, startBeat);
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
    const duration = project.arrangement.reduce((sum, section) => sum + sectionDurationBeats(project, section), 0);
    if (duration <= 0 || (!this.loopEnabled() && beat >= duration)) {
      this.stop();
      this.duration.set(duration);
      this.position.set(duration);
      return;
    }
    this.tempoMap = projectTempoMap(project);
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
      this.midi.configure(track, audible);
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
    this.synth = new TinySynthEngine(this.context);
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
      projectTempoMap(project),
      project.arrangement.reduce((sum, section) => sum + sectionDurationBeats(project, section), 0),
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
    const horizonSeconds = this.absoluteSeconds(currentBeat) + SCHEDULE_AHEAD_SECONDS;
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
      if (this.absoluteSeconds(absoluteStartBeat) > horizonSeconds) return;
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
    const startTime = Math.max(context.currentTime + 0.003, this.originTime + this.absoluteSeconds(absoluteBeat));
    const duration = Math.max(0.035, this.absoluteSeconds(absoluteBeat + note.durationBeats) - this.absoluteSeconds(absoluteBeat));
    if (this.midi.selectedOutput()) {
      const project = this.mixProject;
      const track = project?.tracks.find(track => track.id === note.trackId);
      if (track && !track.mix.muted && (!project!.tracks.some(track => track.mix.solo) || track.mix.solo)) {
        this.midi.schedule(track, note.midi, note.velocity,
          performance.now() + (startTime - context.currentTime) * 1000, duration);
      }
      return;
    }
    if (!this.synth) return;
    const voices = this.synth.schedule(bus.gain, this.programs.get(note.trackId) ?? 0,
      note.role === 'drums', note.midi, note.velocity, startTime, duration);
    for (const voice of voices) {
      this.activeSources.add(voice.source);
      voice.source.onended = () => {
        this.activeSources.delete(voice.source);
        voice.disconnect();
        voice.source.disconnect();
        voice.envelope.disconnect();
      };
      voice.source.stop(voice.stopTime);
    }
  }

  private currentAbsoluteBeat(): number {
    if (!this.context) return 0;
    const seconds = Math.max(0, this.context.currentTime - this.originTime);
    const { startBeat, endBeat } = this.loopBounds();
    const endSeconds = beatToSeconds(this.tempoMap, endBeat);
    if (!this.loopEnabled() || seconds < endSeconds) return secondsToBeat(this.tempoMap, seconds);
    const startSeconds = beatToSeconds(this.tempoMap, startBeat), length = endSeconds - startSeconds;
    const cycles = Math.floor((seconds - endSeconds) / length) + 1;
    return cycles * (endBeat - startBeat) + secondsToBeat(this.tempoMap, startSeconds + (seconds - endSeconds) % length);
  }

  private absoluteSeconds(beat: number): number {
    const { startBeat, endBeat } = this.loopBounds();
    if (!this.loopEnabled() || beat < endBeat) return beatToSeconds(this.tempoMap, beat);
    const length = endBeat - startBeat, cycles = Math.floor((beat - startBeat) / length);
    const startSeconds = beatToSeconds(this.tempoMap, startBeat);
    const cycleSeconds = beatToSeconds(this.tempoMap, endBeat) - startSeconds;
    return cycles * cycleSeconds + beatToSeconds(this.tempoMap, startBeat + (beat - startBeat) % length);
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
    this.originTime = now - this.absoluteSeconds(beat);
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
    this.midi.silence();
    for (const source of this.activeSources) {
      try { source.stop(stopTime); } catch { /* It may have ended between scheduler ticks. */ }
    }
    if (stopTime === undefined) this.activeSources.clear();
  }
}
