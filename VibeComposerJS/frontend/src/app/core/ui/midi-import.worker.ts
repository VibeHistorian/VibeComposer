/// <reference lib="webworker" />
import { readMidiTrack } from '../music/midi-import';
import { midiScaleChoices } from '../music/midi-import-options';
import { importMidiProject } from '../project/midi-project-import';
import type { MidiImportRequest, MidiImportResponse } from './midi-import-protocol';

addEventListener('message', ({ data }: MessageEvent<MidiImportRequest>) => {
  const reply = (response: MidiImportResponse) => postMessage(response);
  try {
    if (data.kind === 'project') {
      reply({ result: importMidiProject(data.buffer, data.filename) });
      return;
    }
    const imported = readMidiTrack(data.buffer, data.maximumNotes);
    const track = { ...imported, notes: imported.notes.map(note => ({ ...note, midi: Math.max(0, Math.min(127, note.midi - data.transpose)) })) };
    reply({ result: { track, choices: data.snap ? midiScaleChoices(track.notes, data.root, data.mode) : [] } });
  } catch (error) {
    reply({ error: error instanceof Error ? error.message : 'Cannot read this MIDI file.' });
  }
});
