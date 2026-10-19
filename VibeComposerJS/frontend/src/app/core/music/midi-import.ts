import { Midi } from '@tonejs/midi';
import type { PhraseNote } from '../project/project.model';
import { MAX_MIDI_IMPORT_BYTES } from './midi-import-limits';
import type { ImportedMidiTrack } from './midi-import-options';

/** Read only the first physical note track, skipping conductor/metadata tracks.
 * Keep its channels together even though Tone splits them into instrument tracks.
 * Tick timing deliberately ignores the source tempo: a beat remains a beat.
 */
export function importMidiPhrase(buffer: ArrayBuffer, maximumBeats: number, maximumNotes = 2048): PhraseNote[] {
  if (!Number.isFinite(maximumBeats) || maximumBeats <= 0) throw new Error('The phrase has no available duration.');
  return readMidiTrack(buffer, maximumNotes).notes.filter(note => note.startBeat < maximumBeats)
    .map(note => ({ ...note, durationBeats: Math.min(note.durationBeats, maximumBeats - note.startBeat) }));
}

export function readMidiTrack(buffer: ArrayBuffer, maximumNotes = 2048): ImportedMidiTrack {
  const bytes = new Uint8Array(buffer);
  const view = new DataView(buffer);
  const tag = (offset: number, text: string) => [...text].every((char, index) => bytes[offset + index] === char.charCodeAt(0));
  if (bytes.length > MAX_MIDI_IMPORT_BYTES || bytes.length < 14 || !tag(0, 'MThd')) throw new Error('Drop a valid MIDI (.mid or .midi) file up to 8 MB.');
  const headerLength = view.getUint32(4);
  const ppq = view.getUint16(12);
  if (headerLength < 6 || !ppq || ppq & 0x8000) throw new Error('This MIDI time format is not supported. Use beat-based MIDI timing.');
  const count = view.getUint16(10);
  let offset = 8 + headerLength;
  for (let trackIndex = 0; trackIndex < count; trackIndex++) {
    if (offset + 8 > bytes.length || !tag(offset, 'MTrk')) throw new Error('The MIDI file contains an invalid track.');
    const end = offset + 8 + view.getUint32(offset + 4);
    if (end > bytes.length) throw new Error('The MIDI file is incomplete.');
    const single = new Uint8Array(14 + end - offset);
    single.set(bytes.subarray(0, 14));
    const header = new DataView(single.buffer);
    header.setUint32(4, 6);
    header.setUint16(8, 0);
    header.setUint16(10, 1);
    single.set(bytes.subarray(offset, end), 14);
    const midi = new Midi(single);
    const notes = midi.tracks.flatMap(track => track.notes).sort((a, b) => a.ticks - b.ticks || a.midi - b.midi);
    offset = end;
    if (!notes.length) continue;
    const valid = notes.filter(note => Number.isFinite(note.ticks) && note.ticks >= 0
      && Number.isFinite(note.durationTicks) && note.durationTicks > 0 && Number.isFinite(note.ticks + note.durationTicks));
    const durationBeats = valid.reduce((end, note) => Math.max(end, (note.ticks + note.durationTicks) / ppq), 0);
    return { durationBeats, notes: valid
      .slice(0, Math.max(0, Math.min(2048, maximumNotes)))
      .map((note, index) => ({ id: `import-${index}`, midi: note.midi, startBeat: note.ticks / ppq,
        durationBeats: note.durationTicks / ppq,
        velocity: Math.max(1, Math.min(127, Math.round(note.velocity * 127))) })) };
  }
  return { notes: [], durationBeats: 0 };
}
