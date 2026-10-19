import type { PhraseNote } from '../project/project.model';
import { Dialog } from '@angular/cdk/dialog';
import type { Injector } from '@angular/core';
import { fitImportedMidi, type ImportedMidiTrack, type MidiScaleChoice } from '../music/midi-import-options';
import type { ScaleMode } from '../music/scales';
import { MidiImportDialogComponent } from '../../shared/midi-import-dialog.component';

export interface MidiDropOptions {
  readonly injector: Injector;
  readonly transpose: number;
  readonly root: number;
  readonly mode: ScaleMode;
  readonly snap: boolean;
}

/** Parse off the UI thread, with cancellation when the editor closes or changes track. */
export async function readMidiFile(buffer: ArrayBuffer, maximumBeats: number, maximumNotes: number, signal: AbortSignal, options: MidiDropOptions): Promise<PhraseNote[] | null> {
  const { track, choices } = await readTrack(buffer, maximumNotes, signal, options);
  if (signal.aborted) return null;
  if (track.durationBeats <= maximumBeats && !choices.length) return fitImportedMidi(track, maximumBeats);
  const dialog = options.injector.get(Dialog).open<PhraseNote[]>(MidiImportDialogComponent, {
    ariaLabel: 'MIDI import options', data: { track, phraseBeats: maximumBeats, choices },
  });
  return new Promise(resolve => {
    const abort = () => dialog.close();
    signal.addEventListener('abort', abort, { once: true });
    dialog.closed.subscribe(notes => {
      signal.removeEventListener('abort', abort);
      resolve(signal.aborted ? null : notes ?? null);
    });
    if (signal.aborted) dialog.close();
  });
}

type PreparedTrack = { track: ImportedMidiTrack; choices: MidiScaleChoice[] };

function readTrack(buffer: ArrayBuffer, maximumNotes: number, signal: AbortSignal, options: MidiDropOptions): Promise<PreparedTrack> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) { reject(new Error('Import cancelled.')); return; }
    const worker = new Worker(new URL('./midi-import.worker', import.meta.url), { type: 'module' });
    const finish = (error?: string, result: PreparedTrack = { track: { notes: [], durationBeats: 0 }, choices: [] }) => {
      clearTimeout(timeout);
      signal.removeEventListener('abort', abort);
      worker.terminate();
      if (error) reject(new Error(error)); else resolve(result);
    };
    const abort = () => finish('Import cancelled.');
    const timeout = setTimeout(() => finish('This MIDI file took too long to read.'), 15000);
    signal.addEventListener('abort', abort, { once: true });
    worker.onmessage = ({ data }: MessageEvent<PreparedTrack & { error?: string }>) => finish(data.error, data);
    worker.onerror = () => finish('Cannot read this MIDI file.');
    worker.postMessage({ buffer, maximumNotes, transpose: options.transpose, root: options.root, mode: options.mode, snap: options.snap }, [buffer]);
  });
}
