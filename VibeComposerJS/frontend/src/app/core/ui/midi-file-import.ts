import type { PhraseNote } from '../project/project.model';

/** Parse off the UI thread, with cancellation when the editor closes or changes track. */
export function readMidiFile(buffer: ArrayBuffer, maximumBeats: number, maximumNotes: number, signal: AbortSignal): Promise<PhraseNote[]> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) { reject(new Error('Import cancelled.')); return; }
    const worker = new Worker(new URL('./midi-import.worker', import.meta.url), { type: 'module' });
    const finish = (error?: string, notes: PhraseNote[] = []) => {
      clearTimeout(timeout);
      signal.removeEventListener('abort', abort);
      worker.terminate();
      if (error) reject(new Error(error)); else resolve(notes);
    };
    const abort = () => finish('Import cancelled.');
    const timeout = setTimeout(() => finish('This MIDI file took too long to read.'), 15000);
    signal.addEventListener('abort', abort, { once: true });
    worker.onmessage = ({ data }: MessageEvent<{ notes?: PhraseNote[]; error?: string }>) => finish(data.error, data.notes);
    worker.onerror = () => finish('Cannot read this MIDI file.');
    worker.postMessage({ buffer, maximumBeats, maximumNotes }, [buffer]);
  });
}
