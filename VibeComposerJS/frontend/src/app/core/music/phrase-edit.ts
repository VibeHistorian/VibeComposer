import type { PhraseNote } from '../project/project.model';

export type PhraseDragMode = 'move' | 'start' | 'duration' | 'pitch' | 'velocity';
const clamp = (value: number, low: number, high: number) => Math.max(low, Math.min(high, value));

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
  const minimum = 0.05;
  if (mode === 'move') beats = clamp(beats, Math.max(...notes.map(n => start - n.startBeat)),
    Math.min(...notes.map(n => end - n.startBeat - n.durationBeats)));
  if (mode === 'start') beats = clamp(beats, Math.max(...notes.map(n => start - n.startBeat)),
    Math.min(...notes.map(n => n.durationBeats - minimum)));
  if (mode === 'duration') beats = clamp(beats, Math.max(...notes.map(n => minimum - n.durationBeats)),
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

export interface PhraseEditSnapshot<T extends PhraseNote = PhraseNote> {
  readonly notes: readonly T[];
  readonly selection: readonly string[];
  readonly restore: boolean;
  readonly restoredTrackIds?: readonly string[];
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
    if (current && current.restore === snapshot.restore && JSON.stringify(current.restoredTrackIds) === JSON.stringify(snapshot.restoredTrackIds)
      && JSON.stringify(current.notes) === JSON.stringify(snapshot.notes)) return;
    this.entries = this.entries.slice(0, this.index + 1);
    this.entries.push(this.copy(snapshot));
    this.index++;
  }
  undo(): PhraseEditSnapshot<T> | undefined { return this.canUndo ? this.copy(this.entries[--this.index]) : undefined; }
  redo(): PhraseEditSnapshot<T> | undefined { return this.canRedo ? this.copy(this.entries[++this.index]) : undefined; }
  private copy(snapshot: PhraseEditSnapshot<T>): PhraseEditSnapshot<T> {
    return { notes: snapshot.notes.map(n => ({ ...n })), selection: [...snapshot.selection], restore: snapshot.restore,
      ...(snapshot.restoredTrackIds ? { restoredTrackIds: [...snapshot.restoredTrackIds] } : {}) };
  }
}
