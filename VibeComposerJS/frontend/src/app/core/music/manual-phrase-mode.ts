import type { CompositionProject } from '../project/project.model';
import type { PhraseNote } from './phrase-note';
import { BASE_KEY, getPitchClass } from './harmony';
import { convertScalePitch, scaleNotes, type ScaleMode } from './scales';

/** Convert a whole manual phrase only when every pitch belongs to its old scale.
 * Stored pitches exclude global transpose. Preserve note identity, timing and velocity.
 */
export function convertManualPhrase<T extends PhraseNote>(notes: readonly T[], fromMode: ScaleMode, toMode: ScaleMode): readonly T[] {
  if (!notes.length || fromMode === toMode) return notes;
  const tonic = getPitchClass(BASE_KEY)!;
  const from = scaleNotes(fromMode), to = scaleNotes(toMode);
  const degree = (midi: number) => from.findIndex(pitch => pitch % 12 === (midi - tonic + 12) % 12);
  if (!notes.every(note => degree(note.midi) >= 0)) return notes;
  return notes.map(note => {
    const relative = note.midi - tonic;
    const octave = Math.floor(relative / 12) * 12;
    let midi = tonic + octave + convertScalePitch(relative - octave, from, to);
    if (midi < 0) midi += 12;
    if (midi > 127) midi -= 12;
    return midi === note.midi ? note : { ...note, midi };
  });
}

export function followManualPhraseMode(previous: CompositionProject, next: CompositionProject): CompositionProject {
  const globalChanged = previous.scale !== next.scale;
  if (!globalChanged && previous.arrangement === next.arrangement) return next;
  const convert = (notes: readonly PhraseNote[]) => convertManualPhrase(notes, previous.scale, next.scale);
  return { ...next, ...(globalChanged ? {
    tracks: next.tracks.map(track => track.role === 'drums' || track.editedPhrase === undefined && !track.phraseEdits?.length ? track : {
      ...track, ...(track.editedPhrase ? { editedPhrase: convert(track.editedPhrase) } : {}),
      ...(track.phraseEdits ? { phraseEdits: track.phraseEdits.map(edit => ({ ...edit,
        notes: convert(edit.notes),
      })) } : {}),
    }),
    editedPhrases: Object.fromEntries(Object.entries(next.editedPhrases).map(([role, notes]) => [role,
      role === 'drums' || !notes ? notes : convert(notes)])),
  } : {}),
    arrangement: next.arrangement.map(section => {
      const before = previous.arrangement.find(candidate => candidate.id === section.id);
      if (!before) return section;
      const oldScale = before.context?.scale ?? previous.scale;
      const newScale = section.context?.scale ?? next.scale;
      const changed = oldScale !== newScale;
      if (!changed && !globalChanged) return section;
      const mode = section.manualPhraseMode;
      const from = globalChanged ? next.scale : mode?.from ?? next.scale;
      const to = changed ? newScale : mode?.to ?? newScale;
      return { ...section, manualPhraseMode: from === to ? undefined : { from, to },
        ...(changed && section.trackPhrases ? { trackPhrases: Object.fromEntries(Object.entries(section.trackPhrases).map(([id, notes]) => [id,
        next.tracks.find(track => track.id === id)?.role === 'drums' ? notes
          : convertManualPhrase(notes, oldScale, newScale)])) } : {}) };
    }),
  };
}
