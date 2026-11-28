import type { DrumSlots } from '../music/drum-instruments';
import type { PhraseNote } from '../music/phrase-note';
import { Dialog } from '@angular/cdk/dialog';
import type { Injector } from '@angular/core';
import { fitImportedMidi } from '../music/midi-import-options';
import { MAX_MIDI_IMPORT_BYTES } from '../music/midi-import-limits';
import type { ScaleMode } from '../music/scales';
import type { MidiProjectImport } from '../project/midi-project-import';
import { MidiImportDialogComponent, type MidiImportDialogData } from '../../features/midi-import/midi-import-dialog.component';
import type { MidiImportRequest, MidiImportResponse, MidiImportResult } from './midi-import-protocol';

export interface MidiDropOptions {
  readonly injector: Injector;
  readonly transpose: number;
  readonly root: number;
  readonly mode: ScaleMode;
  readonly snap: boolean;
  readonly drumSlots?: DrumSlots;
}

/** Parse off the UI thread, with cancellation when the editor closes or changes track. */
export async function readMidiFile(buffer: ArrayBuffer, maximumBeats: number, maximumNotes: number, signal: AbortSignal, options: MidiDropOptions): Promise<PhraseNote[] | null> {
  const { transpose, root, mode, snap } = options;
  const { track, choices } = await runMidiImport({ kind: 'phrase', buffer, maximumNotes, transpose, root, mode, snap }, signal);
  if (signal.aborted) return null;
  if (options.drumSlots) {
    const inverse = new Map(Object.entries(options.drumSlots).map(([pitch, slot]) => [slot, Number(pitch)]));
    if (track.notes.every(note => inverse.has(note.midi))) choices.push({ id: 'chromatic-drums',
      label: 'Chromatic 36–51 → project drum sounds', pitches: track.notes.map(note => inverse.get(note.midi)!) });
  }
  if (track.durationBeats <= maximumBeats && !choices.length && !options.drumSlots) return fitImportedMidi(track, maximumBeats);
  return showNoteInsertOptions(options.injector, signal, {
    track, phraseBeats: maximumBeats, choices, drumImport: options.drumSlots !== undefined,
  });
}

export async function readMidiProjectFile(file: File, signal: AbortSignal): Promise<MidiProjectImport> {
  if (file.size > MAX_MIDI_IMPORT_BYTES) throw new Error('Choose a MIDI file up to 8 MB.');
  return runMidiImport({ kind: 'project', buffer: await file.arrayBuffer(), filename: file.name }, signal);
}

export function showNoteInsertOptions(injector: Injector, signal: AbortSignal, data: MidiImportDialogData): Promise<PhraseNote[] | null> {
  if (signal.aborted) return Promise.resolve(null);
  const dialog = injector.get(Dialog).open<PhraseNote[]>(MidiImportDialogComponent, {
    ariaLabel: data.paste ? 'Paste notes options' : 'MIDI import options', data,
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

function runMidiImport<R extends MidiImportRequest>(request: R, signal: AbortSignal): Promise<MidiImportResult<R>> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) { reject(new Error('Import cancelled.')); return; }
    const worker = new Worker(new URL('./midi-import.worker', import.meta.url), { type: 'module' });
    const finish = (response: MidiImportResponse<R>) => {
      clearTimeout(timeout);
      signal.removeEventListener('abort', abort);
      worker.terminate();
      if ('error' in response) reject(new Error(response.error)); else resolve(response.result);
    };
    const abort = () => finish({ error: 'Import cancelled.' });
    const timeout = setTimeout(() => finish({ error: 'This MIDI file took too long to read.' }), 15000);
    signal.addEventListener('abort', abort, { once: true });
    worker.onmessage = ({ data }: MessageEvent<MidiImportResponse<R>>) => finish(data);
    worker.onerror = () => finish({ error: 'Cannot read this MIDI file.' });
    try {
      worker.postMessage(request, [request.buffer]);
    } catch {
      finish({ error: 'Cannot read this MIDI file.' });
    }
  });
}
