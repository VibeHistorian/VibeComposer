/// <reference lib="webworker" />
import { importMidiPhrase } from '../music/midi-import';

addEventListener('message', ({ data }: MessageEvent<{ buffer: ArrayBuffer; maximumBeats: number; maximumNotes: number }>) => {
  try {
    postMessage({ notes: importMidiPhrase(data.buffer, data.maximumBeats, data.maximumNotes) });
  } catch (error) {
    postMessage({ error: error instanceof Error ? error.message : 'Cannot read this MIDI file.' });
  }
});
