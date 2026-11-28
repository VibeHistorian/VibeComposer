import type { ImportedMidiTrack, MidiScaleChoice } from '../music/midi-import-options';
import type { ScaleMode } from '../music/scales';
import type { MidiProjectImport } from '../project/midi-project-import';

interface PhraseImportRequest {
  readonly kind: 'phrase';
  readonly buffer: ArrayBuffer;
  readonly maximumNotes: number;
  readonly transpose: number;
  readonly root: number;
  readonly mode: ScaleMode;
  readonly snap: boolean;
}

interface ProjectImportRequest {
  readonly kind: 'project';
  readonly buffer: ArrayBuffer;
  readonly filename: string;
}

export type MidiImportRequest = PhraseImportRequest | ProjectImportRequest;
export type MidiImportResult<R extends MidiImportRequest> = R extends PhraseImportRequest
  ? { track: ImportedMidiTrack; choices: MidiScaleChoice[] } : MidiProjectImport;
export type MidiImportResponse<R extends MidiImportRequest = MidiImportRequest> =
  { result: MidiImportResult<R> } | { error: string };
