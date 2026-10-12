import type { PhraseNote } from '../project/project.model';

export type PhraseDragMode = 'move' | 'start' | 'duration' | 'pitch' | 'velocity';
const clamp = (value: number, low: number, high: number) => Math.max(low, Math.min(high, value));

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

export interface PhraseEditSnapshot {
  readonly notes: readonly PhraseNote[];
  readonly selection: readonly string[];
  readonly restore: boolean;
}

/** Draft history is independent of project history; a completed gesture records once. */
export class PhraseEditHistory {
  private entries: PhraseEditSnapshot[] = [];
  private index = 0;
  get canUndo(): boolean { return this.index > 0; }
  get canRedo(): boolean { return this.index < this.entries.length - 1; }
  reset(snapshot: PhraseEditSnapshot): void { this.entries = [this.copy(snapshot)]; this.index = 0; }
  record(snapshot: PhraseEditSnapshot): void {
    const current = this.entries[this.index];
    if (current && current.restore === snapshot.restore && JSON.stringify(current.notes) === JSON.stringify(snapshot.notes)) return;
    this.entries = this.entries.slice(0, this.index + 1);
    this.entries.push(this.copy(snapshot));
    this.index++;
  }
  undo(): PhraseEditSnapshot | undefined { return this.canUndo ? this.copy(this.entries[--this.index]) : undefined; }
  redo(): PhraseEditSnapshot | undefined { return this.canRedo ? this.copy(this.entries[++this.index]) : undefined; }
  private copy(snapshot: PhraseEditSnapshot): PhraseEditSnapshot {
    return { notes: snapshot.notes.map(n => ({ ...n })), selection: [...snapshot.selection], restore: snapshot.restore };
  }
}
