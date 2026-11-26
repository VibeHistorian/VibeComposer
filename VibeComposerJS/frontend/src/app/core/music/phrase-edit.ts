import type { PhraseNote } from './phrase-note';
import type { TrackGlides } from './phrase-glides';

export type PhraseDragMode = 'move' | 'start' | 'duration' | 'pitch' | 'velocity';
const clamp = (value: number, low: number, high: number) => Math.max(low, Math.min(high, value));
const MIN_DRAG_DURATION = 0.05;

export function snapMidiToScale(midi: number, pitchClasses: readonly number[]): number {
  const pitch = clamp(Math.round(midi), 0, 127);
  if (!pitchClasses.length) return pitch;
  for (let distance = 0; distance < 12; distance++) {
    for (const candidate of [pitch - distance, pitch + distance]) {
      if (candidate >= 0 && candidate <= 127 && pitchClasses.includes(candidate % 12)) return candidate;
    }
  }
  return pitch;
}

/** Compare musical content independently of note IDs and array order. */
export function phraseNotesEqual(left: readonly PhraseNote[], right: readonly PhraseNote[]): boolean {
  const musicalValues = (notes: readonly PhraseNote[]) => notes.map(n => [n.startBeat, n.midi, n.durationBeats, n.velocity])
    .sort((a, b) => a[0] - b[0] || a[1] - b[1] || a[2] - b[2] || a[3] - b[3]);
  return JSON.stringify(musicalValues(left)) === JSON.stringify(musicalValues(right));
}

/** Apply one shared delta, preserving the spacing of a selection at phrase/MIDI boundaries. */
export function dragPhraseNotes(notes: readonly PhraseNote[], mode: PhraseDragMode, beats: number,
  pitch: number, velocity: number, start: number, end: number): PhraseNote[] {
  if (!notes.length) return [];
  if (mode === 'move') beats = clamp(beats, Math.max(...notes.map(n => start - n.startBeat)),
    Math.min(...notes.map(n => end - n.startBeat - n.durationBeats)));
  if (mode === 'start') beats = clamp(beats, Math.max(...notes.map(n => start - n.startBeat)),
    Math.min(...notes.map(n => n.durationBeats - MIN_DRAG_DURATION)));
  if (mode === 'duration') beats = clamp(beats, Math.max(...notes.map(n => MIN_DRAG_DURATION - n.durationBeats)),
    Math.min(...notes.map(n => end - n.startBeat - n.durationBeats)));
  pitch = clamp(pitch, -Math.min(...notes.map(n => n.midi)), 127 - Math.max(...notes.map(n => n.midi)));
  velocity = clamp(velocity, 1 - Math.min(...notes.map(n => n.velocity)), 127 - Math.max(...notes.map(n => n.velocity)));
  return notes.map(n => ({ ...n,
    startBeat: n.startBeat + (mode === 'move' || mode === 'start' ? beats : 0),
    durationBeats: n.durationBeats + (mode === 'duration' ? beats : mode === 'start' ? -beats : 0),
    midi: n.midi + (mode === 'move' || mode === 'pitch' ? pitch : 0),
    velocity: n.velocity + (mode === 'velocity' ? velocity : 0),
  }));
}

/** Taper the dragged note's velocity change to zero at the opposite onset. */
export function scalePhraseVelocities(notes: readonly PhraseNote[], edge: PhraseNote, anchor: number, delta: number): readonly PhraseNote[] {
  const span = edge.startBeat - anchor;
  const change = clamp(edge.velocity + delta, 1, 127) - edge.velocity;
  if (!span || !change) return notes;
  return notes.map(note => {
    const weight = (note.startBeat - anchor) / span;
    const velocity = clamp(Math.round(note.velocity + change * weight), 1, 127);
    return velocity === note.velocity ? note : { ...note, velocity };
  });
}

/** Stretch around the opposite selection edge, keeping the same scale factor for every note. */
export function scalePhraseNotes(notes: readonly PhraseNote[], anchor: number, edge: number, target: number,
  start: number, end: number): readonly PhraseNote[] {
  const span = edge - anchor;
  if (!span || target === edge || anchor < start || anchor > end) return notes;
  const minimumFactor = Math.min(1, MIN_DRAG_DURATION / Math.abs(span));
  const maximumFactor = ((span < 0 ? start : end) - anchor) / span;
  if (maximumFactor < minimumFactor) return notes;
  const factor = clamp((target - anchor) / span, minimumFactor, maximumFactor);
  if (factor === 1) return notes;
  return notes.map(note => {
    const startBeat = Math.max(start, anchor + (note.startBeat - anchor) * factor);
    return { ...note, startBeat, durationBeats: Math.min(note.durationBeats * factor, end - startBeat) };
  });
}

export interface PhraseEditSnapshot<T extends PhraseNote = PhraseNote> {
  readonly notes: readonly T[];
  readonly selection: readonly string[];
  readonly restore: boolean;
  readonly phraseTranspose?: number;
  readonly restoredTrackIds?: readonly string[];
  readonly glides?: TrackGlides;
}

/** Draft history is independent of project history; a completed gesture records once. */
export class PhraseEditHistory<T extends PhraseNote = PhraseNote> {
  private entries: PhraseEditSnapshot<T>[] = [];
  private index = 0;
  get canUndo(): boolean { return this.index > 0; }
  get canRedo(): boolean { return this.index < this.entries.length - 1; }
  reset(snapshot: PhraseEditSnapshot<T>): void { this.entries = [this.copy(snapshot)]; this.index = 0; }
  record(snapshot: PhraseEditSnapshot<T>): void {
    const current = this.entries[this.index];
    if (current && current.restore === snapshot.restore && (current.phraseTranspose ?? 0) === (snapshot.phraseTranspose ?? 0) && JSON.stringify(current.restoredTrackIds) === JSON.stringify(snapshot.restoredTrackIds)
      && JSON.stringify(current.notes) === JSON.stringify(snapshot.notes)
      && JSON.stringify(current.glides) === JSON.stringify(snapshot.glides)) return;
    this.entries = this.entries.slice(0, this.index + 1);
    this.entries.push(this.copy(snapshot));
    this.index++;
  }
  undo(): PhraseEditSnapshot<T> | undefined { return this.canUndo ? this.copy(this.entries[--this.index]) : undefined; }
  redo(): PhraseEditSnapshot<T> | undefined { return this.canRedo ? this.copy(this.entries[++this.index]) : undefined; }
  private copy(snapshot: PhraseEditSnapshot<T>): PhraseEditSnapshot<T> {
    return { phraseTranspose: snapshot.phraseTranspose, notes: snapshot.notes.map(n => ({ ...n })), selection: [...snapshot.selection], restore: snapshot.restore,
      ...(snapshot.restoredTrackIds ? { restoredTrackIds: [...snapshot.restoredTrackIds] } : {}),
      ...(snapshot.glides ? { glides: snapshot.glides } : {}) };
  }
}
