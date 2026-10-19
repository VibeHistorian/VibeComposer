/// <reference lib="webworker" />
import { readMidiTrack } from '../music/midi-import';
import { midiScaleChoices } from '../music/midi-import-options';
import type { ScaleMode } from '../music/scales';

addEventListener('message', ({ data }: MessageEvent<{ buffer: ArrayBuffer; maximumNotes: number; transpose: number; root: number; mode: ScaleMode; snap: boolean }>) => {
  try {
    const imported = readMidiTrack(data.buffer, data.maximumNotes);
    const track = { ...imported, notes: imported.notes.map(note => ({ ...note, midi: Math.max(0, Math.min(127, note.midi - data.transpose)) })) };
    postMessage({ track, choices: data.snap ? midiScaleChoices(track.notes, data.root, data.mode) : [] });
  } catch (error) {
    postMessage({ error: error instanceof Error ? error.message : 'Cannot read this MIDI file.' });
  }
});
