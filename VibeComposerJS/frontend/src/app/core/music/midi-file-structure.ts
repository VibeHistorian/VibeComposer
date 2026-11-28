import { MAX_MIDI_IMPORT_BYTES } from './midi-import-limits';

export function midiFileStructure(buffer: ArrayBuffer) {
  const bytes = new Uint8Array(buffer);
  const view = new DataView(buffer);
  const tag = (offset: number, text: string) => [...text].every((char, index) => bytes[offset + index] === char.charCodeAt(0));
  if (bytes.length > MAX_MIDI_IMPORT_BYTES || bytes.length < 14 || !tag(0, 'MThd')) {
    throw new Error('Choose a valid MIDI (.mid or .midi) file up to 8 MB.');
  }
  const headerLength = view.getUint32(4);
  const ppq = view.getUint16(12);
  if (headerLength < 6 || !ppq || ppq & 0x8000) throw new Error('This MIDI time format is not supported. Use beat-based MIDI timing.');
  if (8 + headerLength > bytes.length) throw new Error('The MIDI header is incomplete.');
  const trackCount = view.getUint16(10);
  return { ppq, format: view.getUint16(8), trackCount, singleTrackFiles: function* () {
    let offset = 8 + headerLength;
    for (let index = 0; index < trackCount; index++) {
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
      yield single;
      offset = end;
    }
  } };
}
