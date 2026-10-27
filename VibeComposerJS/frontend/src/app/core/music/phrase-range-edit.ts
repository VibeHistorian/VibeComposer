import type { PhraseNote } from './phrase-note';
import type { CompositionTrack } from '../project/track.model';

/** A section-relative replacement window. Margins are explicitly owned pickups/tails,
 * independent of body notes that may fall outside a shorter destination section. */
export interface PhraseRangeEdit {
  readonly startBeat: number;
  readonly endBeat: number;
  readonly pickupBeats?: number;
  readonly tailBeats?: number;
  readonly notes: readonly PhraseNote[];
}

export function hasManualPhrase(track: CompositionTrack): boolean {
  return track.editedPhrase !== undefined || !!track.phraseEdits?.length;
}

/** Apply in save order. Missing notes inside a window mean silence, not inheritance.
 * Use onset ownership; sustained notes keep their complete durations. */
export function applyPhraseRangeEdits(source: readonly PhraseNote[], edits: readonly PhraseRangeEdit[] | undefined,
  sectionEnd: number, bounds = { startBeat: -4, endBeat: sectionEnd + 4 }): PhraseNote[] {
  let notes = [...source];
  for (const edit of edits ?? []) {
    const bodyEnd = Math.min(edit.endBeat, sectionEnd);
    const pickupStart = Math.max(-(edit.pickupBeats ?? 0), bounds.startBeat);
    const tailEnd = Math.min(sectionEnd + (edit.tailBeats ?? 0), bounds.endBeat);
    const replaced = (beat: number) => beat >= edit.startBeat && beat < bodyEnd
      || edit.pickupBeats !== undefined && beat >= pickupStart && beat < 0
      || edit.tailBeats !== undefined && beat >= sectionEnd && beat < tailEnd;
    notes = notes.filter(note => !replaced(note.startBeat));
    for (const note of edit.notes) {
      const beat = note.startBeat;
      if (beat >= edit.startBeat && beat < bodyEnd
        || edit.pickupBeats !== undefined && beat >= pickupStart && beat < 0) notes.push({ ...note });
      else if (edit.tailBeats !== undefined && beat >= edit.endBeat) {
        const startBeat = sectionEnd + beat - edit.endBeat;
        if (startBeat < tailEnd) notes.push({ ...note, startBeat });
      }
    }
  }
  return notes;
}

export function appendPhraseRangeEdit(previous: readonly PhraseRangeEdit[] | undefined, edit: PhraseRangeEdit): PhraseRangeEdit[] {
  // A later full replacement makes covered windows redundant, including empty ones.
  return [...(previous ?? []).filter(old => !(edit.startBeat <= old.startBeat && edit.endBeat >= old.endBeat
    && (old.pickupBeats === undefined || (edit.pickupBeats ?? 0) >= old.pickupBeats)
    && (old.tailBeats === undefined || (edit.tailBeats ?? 0) >= old.tailBeats))),
    { ...edit, notes: edit.notes.map(note => ({ ...note })) }];
}
