import { dragPhraseNotes, snapMidiToScale, type PhraseDragMode } from '../../core/music/phrase-edit';
import type { PhraseNote } from '../../core/music/phrase-note';
import type { ScorePoint, ScoreTimeGeometry } from './score-geometry';

/** The renderer reads this snapshot; only pointer handlers own gesture state and edits. */
export interface ScoreInteractionPreview {
  readonly notes: readonly PhraseNote[];
  readonly selectionBox: { readonly x: number; readonly y: number; readonly endX: number; readonly endY: number } | null;
  readonly active: boolean;
}

/** Gesture state stores musical previews only; rendering consumes these without committing. */
export interface ScoreDrag<Note extends PhraseNote = PhraseNote> {
  readonly note: Note;
  readonly x: number;
  readonly y: number;
  readonly pointerId: number;
  readonly mode: PhraseDragMode;
  readonly originals: readonly Note[];
  readonly duplicate: boolean;
  readonly lockPitch: boolean;
  current: PhraseNote;
  preview: readonly PhraseNote[];
}

export interface ScoreDragContext {
  readonly time: ScoreTimeGeometry;
  readonly laneHeight: number;
  readonly snapBeats: number;
  readonly shiftKey: boolean;
  readonly scalePitchClasses: readonly number[];
  readonly startBeat: number;
  readonly endBeat: number;
}

export function previewScoreDrag(drag: ScoreDrag, delta: ScorePoint, context: ScoreDragContext): readonly PhraseNote[] {
  const rawDelta = delta.x / (context.time.width * context.time.horizontalScale) * context.time.totalBeats;
  const grid = context.shiftKey ? 0 : context.snapBeats;
  const anchor = drag.mode === 'duration' ? drag.note.durationBeats : drag.note.startBeat;
  const beatDelta = grid ? Math.round((anchor + rawDelta) / grid) * grid - anchor : rawDelta;
  const pitchDelta = drag.lockPitch || (context.shiftKey && drag.mode === 'move') ? 0 : -Math.round(delta.y / context.laneHeight);
  let preview = dragPhraseNotes(drag.originals, drag.mode, beatDelta, pitchDelta, -Math.round(delta.y), context.startBeat, context.endBeat);
  if ((drag.mode === 'pitch' || drag.mode === 'move') && pitchDelta !== 0 && !drag.lockPitch) {
    preview = preview.map(note => ({ ...note, midi: snapMidiToScale(note.midi, context.scalePitchClasses) }));
  }
  return preview;
}

/** Called on release only. Cancellation discards the state without producing an edit. */
export function scoreDragChanged(drag: ScoreDrag): boolean {
  const { note, current, duplicate } = drag;
  return duplicate || current.startBeat !== note.startBeat || current.durationBeats !== note.durationBeats
    || current.midi !== note.midi || current.velocity !== note.velocity;
}
