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
