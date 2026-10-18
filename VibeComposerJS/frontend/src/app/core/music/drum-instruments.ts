/** Java InstUtils.DRUM_INST_NAMES: percussion note numbers, never GM programs. */
export const DRUM_INSTRUMENTS: readonly { readonly midi: number; readonly name: string }[] = Object.freeze([
  { midi: 35, name: 'Bass kick' }, { midi: 36, name: 'Kick' }, { midi: 37, name: 'Side stick' },
  { midi: 38, name: 'Snare' }, { midi: 39, name: 'Clap' }, { midi: 40, name: 'Electric snare' },
  { midi: 41, name: 'Floor tom' }, { midi: 42, name: 'Closed hi-hat' }, { midi: 44, name: 'Pedal hi-hat' },
  { midi: 46, name: 'Open hi-hat' }, { midi: 47, name: 'Low-mid tom' }, { midi: 53, name: 'Ride' },
  { midi: 54, name: 'Tambourine' }, { midi: 60, name: 'High bongo' }, { midi: 61, name: 'Low bongo' },
  { midi: 82, name: 'Shaker' },
]);

export function drumInstrumentName(midi: number): string {
  return DRUM_INSTRUMENTS.find((instrument) => instrument.midi === midi)?.name ?? `Percussion ${midi}`;
}

export function isDrumPitch(value: unknown): value is number {
  return DRUM_INSTRUMENTS.some((instrument) => instrument.midi === value);
}

export type DrumMapping = 'general-midi' | 'chromatic';
export type DrumMappingPreference = DrumMapping | 'auto';

export function resolveDrumMapping(preference: DrumMappingPreference, externalOutput: boolean): DrumMapping {
  return preference === 'auto' ? externalOutput ? 'chromatic' : 'general-midi' : preference;
}

/** Java InstUtils.DRUM_INST_NUMBERS_SEMI maps the ordered drum pool to notes 36–51. */
export function mapDrumPitch(pitch: number, mapping: DrumMapping): number {
  if (mapping === 'general-midi') return pitch;
  const index = DRUM_INSTRUMENTS.findIndex(instrument => instrument.midi === pitch);
  return index < 0 ? pitch : 36 + index;
}

export function drumInstrumentLabel(pitch: number, mapping: DrumMapping): string {
  return `${drumInstrumentName(pitch)} · ${mapDrumPitch(pitch, mapping)}`;
}
