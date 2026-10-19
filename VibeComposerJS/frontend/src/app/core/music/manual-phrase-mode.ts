import type { CompositionProject, PhraseNote } from '../project/project.model';
import { getPitchClass } from './harmony';
import { scaleNotes, type ScaleMode } from './scales';

/** Convert a whole manual phrase only when every pitch belongs to its old key/mode.
 * Stored pitches exclude global transpose. Preserve note identity, timing and velocity.
 */
function convertPhrase(notes: readonly PhraseNote[] | undefined, key: string, fromMode: ScaleMode, toMode: ScaleMode): readonly PhraseNote[] | undefined {
  if (!notes?.length || fromMode === toMode) return notes;
  const root = getPitchClass(key)!;
  const from = scaleNotes(fromMode), to = scaleNotes(toMode);
  const degree = (midi: number) => from.findIndex(pitch => pitch % 12 === ((midi - root) % 12 + 12) % 12);
  if (!notes.every(note => degree(note.midi) >= 0)) return notes;
  return notes.map(note => {
    let midi = root + Math.floor((note.midi - root) / 12) * 12 + to[degree(note.midi)];
    if (midi < 0) midi += 12;
    if (midi > 127) midi -= 12;
    return midi === note.midi ? note : { ...note, midi };
  });
}

export function followManualPhraseMode(previous: CompositionProject, next: CompositionProject): CompositionProject {
  if (previous.scale === next.scale) return next;
  return { ...next,
    tracks: next.tracks.map(track => track.role === 'drums' || track.editedPhrase === undefined ? track : {
      ...track, editedPhrase: convertPhrase(track.editedPhrase, previous.key, previous.scale, next.scale),
    }),
    editedPhrases: Object.fromEntries(Object.entries(next.editedPhrases).map(([role, notes]) => [role,
      role === 'drums' ? notes : convertPhrase(notes, previous.key, previous.scale, next.scale)])),
    arrangement: next.arrangement.map(section => {
      if (!section.trackPhrases || section.context?.scale !== undefined) return section;
      return { ...section, trackPhrases: Object.fromEntries(Object.entries(section.trackPhrases).map(([id, notes]) => [id,
        next.tracks.find(track => track.id === id)?.role === 'drums' ? notes
          : convertPhrase(notes, section.context?.key ?? previous.key, previous.scale, next.scale)!])) };
    }),
  };
}
