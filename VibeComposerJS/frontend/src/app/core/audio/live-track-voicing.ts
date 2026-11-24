import type { CompositionTrack } from '../project/track.model';
import type { PhraseNote } from '../music/phrase-note';
import { voicePitchAt, type VoicePitchChange } from '../music/track-voicing';
import { protectedAudioOutput } from './output-limiter';
import type { InstrumentEngine, InstrumentVoice } from './instrument-engine';

/** Held-key priority and pitch state for one independent live audition track. */
export class LiveTrackVoicing {
  private readonly held = new Map<symbol, PhraseNote>();
  private winner?: symbol;
  private change?: VoicePitchChange;
  private voice?: { gain: GainNode; detune: ConstantSourceNode; root: number; voices: InstrumentVoice[] };

  constructor(readonly engine: InstrumentEngine, readonly mode: 'mono' | 'legato') {}

  press(track: CompositionTrack, note: PhraseNote, masterVolume: number): () => void {
    const key = Symbol();
    this.held.set(key, note);
    this.select(track, masterVolume);
    return () => {
      if (!this.held.delete(key)) return;
      this.select(track, masterVolume);
    };
  }

  dispose(): void {
    this.held.clear();
    this.endVoice(this.engine.context.currentTime);
  }

  private select(track: CompositionTrack, masterVolume: number): void {
    let key: symbol | undefined, note: PhraseNote | undefined;
    for (const [heldKey, heldNote] of this.held) { key = heldKey; note = heldNote; }
    if (key === this.winner) return;
    this.winner = key;
    const context = this.engine.context;
    const now = context.currentTime + .003;
    const prior = this.change ? voicePitchAt(this.change, now) : note?.midi;
    if (!note) {
      if (prior !== undefined) this.change = { time: now, end: now, from: prior, to: prior };
      this.endVoice(now);
      return;
    }
    const glide = (track.glideMs ?? 0) / 1000;
    const from = glide && (this.voice || this.mode === 'mono') ? prior ?? note.midi : note.midi;
    if (this.mode === 'mono') this.endVoice(now);
    if (!this.voice) {
      const gain = context.createGain(), pan = context.createStereoPanner(), detune = context.createConstantSource();
      gain.gain.value = 0.8 * track.mix.volumePercent / 100 * masterVolume / 100;
      pan.pan.value = track.mix.panPercent / 100;
      gain.connect(pan); pan.connect(protectedAudioOutput(context));
      detune.offset.value = (from - note.midi) * 100;
      detune.start(now);
      const cleanup = () => { detune.stop(); detune.disconnect(); gain.disconnect(); pan.disconnect(); };
      let voices: InstrumentVoice[];
      try { voices = this.engine.schedule(gain, track.mix.program, track.role === 'drums', note.midi, note.velocity, now, 3600, detune); }
      catch (error) { cleanup(); throw error; }
      if (!voices.length) { cleanup(); return; }
      const voice = this.voice = { gain, detune, root: note.midi, voices };
      let remaining = voices.length;
      for (const part of voices) part.onEnded(() => {
        part.dispose();
        if (--remaining !== 0) return;
        cleanup();
        if (this.voice === voice) this.voice = undefined;
      });
    }
    const { detune, root } = this.voice;
    detune.offset.cancelScheduledValues(now);
    detune.offset.setValueAtTime((from - root) * 100, now);
    const end = from !== note.midi && glide ? now + glide : now;
    if (end > now) detune.offset.linearRampToValueAtTime((note.midi - root) * 100, end);
    this.change = { time: now, end, from, to: note.midi };
  }

  private endVoice(time: number): void {
    const voice = this.voice;
    this.voice = undefined;
    if (!voice) return;
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
