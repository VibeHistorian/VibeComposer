import { Midi } from '@tonejs/midi';
import type { PhraseNote } from './phrase-note';
import { midiFileStructure } from './midi-file-structure';
import type { ImportedMidiTrack } from './midi-import-options';
import { MAX_PHRASE_NOTES } from '../project/project-limits';

/** Read only the first physical note track, skipping conductor/metadata tracks.
 * Keep its channels together even though Tone splits them into instrument tracks.
 * Tick timing deliberately ignores the source tempo: a beat remains a beat.
 */
export function importMidiPhrase(buffer: ArrayBuffer, maximumBeats: number, maximumNotes = MAX_PHRASE_NOTES): PhraseNote[] {
  if (!Number.isFinite(maximumBeats) || maximumBeats <= 0) throw new Error('The phrase has no available duration.');
  return readMidiTrack(buffer, maximumNotes).notes.filter(note => note.startBeat < maximumBeats)
    .map(note => ({ ...note, durationBeats: Math.min(note.durationBeats, maximumBeats - note.startBeat) }));
}

export function readMidiTrack(buffer: ArrayBuffer, maximumNotes = MAX_PHRASE_NOTES): ImportedMidiTrack {
  const { ppq, singleTrackFiles } = midiFileStructure(buffer);
  for (const single of singleTrackFiles()) {
    const midi = new Midi(single);
    const notes = midi.tracks.flatMap(track => track.notes).sort((a, b) => a.ticks - b.ticks || a.midi - b.midi);
    if (!notes.length) continue;
    const valid = notes.filter(note => Number.isFinite(note.ticks) && note.ticks >= 0
      && Number.isFinite(note.durationTicks) && note.durationTicks > 0 && Number.isFinite(note.ticks + note.durationTicks));
    const durationBeats = valid.reduce((end, note) => Math.max(end, (note.ticks + note.durationTicks) / ppq), 0);
    return { durationBeats, notes: valid
      .slice(0, Math.max(0, Math.min(MAX_PHRASE_NOTES, maximumNotes)))
      .map((note, index) => ({ id: `import-${index}`, midi: note.midi, startBeat: note.ticks / ppq,
        durationBeats: note.durationTicks / ppq,
        velocity: Math.max(1, Math.min(127, Math.round(note.velocity * 127))) })) };
  }
  return { notes: [], durationBeats: 0 };
}
