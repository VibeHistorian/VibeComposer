import type { CompositionTrack } from '../project/track.model';
import type { PhraseNote } from '../music/phrase-note';
import { voicePitchAt, type VoicePitchChange } from '../music/track-voicing';
import { protectedAudioOutput } from './output-limiter';
import type { InstrumentEngine, InstrumentVoice } from './instrument-engine';
import type { InstrumentTone } from './instrument-tone';
import { scheduleInstrumentNote } from './schedule-voice';

interface LiveVoice {
  readonly gain: GainNode;
  readonly detune: ConstantSourceNode;
  readonly root: number;
  readonly voices: InstrumentVoice[];
}

/** Held-key priority and pitch state for one independent live audition track. */
export class LiveTrackVoicing {
  private readonly held = new Map<symbol, PhraseNote>();
  private winner?: symbol;
  private change?: VoicePitchChange;
  private voice?: LiveVoice;
  private releasing?: LiveVoice;

  constructor(readonly engine: InstrumentEngine, readonly mode: 'mono' | 'legato', readonly program: number) {}

  press(track: CompositionTrack, note: PhraseNote, masterVolume: number, tone?: InstrumentTone): () => void {
    const key = Symbol();
    this.held.set(key, note);
    this.select(track, masterVolume, tone);
    return () => {
      if (!this.held.delete(key)) return;
      this.select(track, masterVolume, tone);
    };
  }

  dispose(): void {
    this.held.clear();
    const now = this.engine.context.currentTime;
    this.endVoice(now);
    this.cutRelease(now);
  }

  private select(track: CompositionTrack, masterVolume: number, tone?: InstrumentTone): void {
    let key: symbol | undefined, note: PhraseNote | undefined;
    for (const [heldKey, heldNote] of this.held) { key = heldKey; note = heldNote; }
    if (key === this.winner) return;
    this.winner = key;
    const context = this.engine.context;
    const now = context.currentTime + .003;
    const prior = this.change ? voicePitchAt(this.change, now) : note?.midi;
    if (!note) {
      if (prior !== undefined) this.change = { time: now, end: now, from: prior, to: prior };
      this.endVoice(now, true);
      return;
    }
    const glide = (track.glideMs ?? 0) / 1000;
    const from = glide && (this.voice || this.mode === 'mono') ? prior ?? note.midi : note.midi;
    if (this.mode === 'mono') this.endVoice(now);
    if (!this.voice) {
      this.cutRelease(now);
      const gain = context.createGain(), pan = context.createStereoPanner(), detune = context.createConstantSource();
      gain.gain.value = 0.8 * track.mix.volumePercent / 100 * masterVolume / 100;
      pan.pan.value = track.mix.panPercent / 100;
      gain.connect(pan); pan.connect(protectedAudioOutput(context));
      detune.offset.value = (from - note.midi) * 100;
      detune.start(now);
      const cleanup = () => { detune.stop(); detune.disconnect(); gain.disconnect(); pan.disconnect(); };
      let voices: InstrumentVoice[];
      try { voices = scheduleInstrumentNote(this.engine, gain, this.program, track.role === 'drums', note.midi, note.velocity, now, 3600, detune, tone); }
      catch (error) { cleanup(); throw error; }
      if (!voices.length) { cleanup(); return; }
      const voice = this.voice = { gain, detune, root: note.midi, voices };
      let remaining = voices.length;
      for (const part of voices) part.onEnded(() => {
        part.dispose();
        if (--remaining !== 0) return;
        cleanup();
        if (this.voice === voice) this.voice = undefined;
        if (this.releasing === voice) this.releasing = undefined;
      });
    }
    const { detune, root } = this.voice;
    detune.offset.cancelScheduledValues(now);
    detune.offset.setValueAtTime((from - root) * 100, now);
    const end = from !== note.midi && glide ? now + glide : now;
    if (end > now) detune.offset.linearRampToValueAtTime((note.midi - root) * 100, end);
    this.change = { time: now, end, from, to: note.midi };
  }

  private endVoice(time: number, released = false): void {
    const voice = this.voice;
    this.voice = undefined;
    if (!voice) return;
    if (released && voice.voices.some(part => part.release)) {
      this.releasing = voice;
      for (const part of voice.voices) part.release?.(time);
      return;
    }
    this.fadeOut(voice, time);
  }

  private cutRelease(time: number): void {
    const voice = this.releasing;
    this.releasing = undefined;
    if (voice) this.fadeOut(voice, time);
  }

  private fadeOut(voice: LiveVoice, time: number): void {
    // Fade before the handoff so no two live voices sound together.
    const now = this.engine.context.currentTime;
    const end = Math.max(now, time);
    voice.gain.gain.setValueAtTime(voice.gain.gain.value, now);
    voice.gain.gain.linearRampToValueAtTime(0, end);
    for (const part of voice.voices) {
      try { part.stop(end); } catch { /* The gain still fades if an operator has already ended. */ }
    }
  }
}
