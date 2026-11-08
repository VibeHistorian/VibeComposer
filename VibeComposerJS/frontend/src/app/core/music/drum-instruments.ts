/** Maximum distinct percussion sounds and chromatic output slots per project. */
export const MAX_DRUM_SOUNDS = 16;

/** Java InstUtils.DRUM_INST_NAMES: percussion note numbers, never GM programs. */
export const CLASSIC_DRUM_INSTRUMENTS: readonly { readonly midi: number; readonly name: string }[] = Object.freeze([
  { midi: 35, name: 'Bass kick' }, { midi: 36, name: 'Kick' }, { midi: 37, name: 'Side stick' },
  { midi: 38, name: 'Snare' }, { midi: 39, name: 'Clap' }, { midi: 40, name: 'Electric snare' },
  { midi: 41, name: 'Floor tom' }, { midi: 42, name: 'Closed hi-hat' }, { midi: 44, name: 'Pedal hi-hat' },
  { midi: 46, name: 'Open hi-hat' }, { midi: 47, name: 'Low-mid tom' }, { midi: 53, name: 'Ride' },
  { midi: 54, name: 'Tambourine' }, { midi: 60, name: 'High bongo' }, { midi: 61, name: 'Low bongo' },
  { midi: 82, name: 'Shaker' },
]);

export type DrumFamily = 'kicks' | 'snares' | 'hats' | 'toms' | 'cymbals' | 'other';
const EXTRA_DRUMS = [
  [43, 'Low floor tom'], [45, 'Low tom'], [48, 'High-mid tom'], [49, 'Crash cymbal'],
  [50, 'High tom'], [51, 'Ride cymbal'], [52, 'Chinese cymbal'], [55, 'Splash cymbal'],
  [56, 'Cowbell'], [57, 'Crash cymbal 2'], [58, 'Vibraslap'], [59, 'Ride cymbal 2'],
  [62, 'Muted conga'], [63, 'High conga'], [64, 'Low conga'], [65, 'High timbale'],
  [66, 'Low timbale'], [67, 'High agogo'], [68, 'Low agogo'], [69, 'Cabasa'],
  [70, 'Maracas'], [71, 'Short whistle'], [72, 'Long whistle'], [73, 'Short guiro'],
  [74, 'Long guiro'], [75, 'Claves'], [76, 'High wood block'], [77, 'Low wood block'],
  [78, 'Muted cuica'], [79, 'Open cuica'], [80, 'Muted triangle'], [81, 'Open triangle'],
] as const;
export function drumFamily(pitch: number): DrumFamily {
  if ([35, 36].includes(pitch)) return 'kicks';
  if ([37, 38, 39, 40].includes(pitch)) return 'snares';
  if ([42, 44, 46].includes(pitch)) return 'hats';
  if ([41, 43, 45, 47, 48, 50].includes(pitch)) return 'toms';
  if ([49, 51, 52, 53, 55, 57, 59].includes(pitch)) return 'cymbals';
  return 'other';
}
export const DRUM_INSTRUMENTS = Object.freeze([
  ...CLASSIC_DRUM_INSTRUMENTS, ...EXTRA_DRUMS.map(([midi, name]) => ({ midi, name })),
].sort((a, b) => a.midi - b.midi).map(instrument => ({ ...instrument, family: drumFamily(instrument.midi) })));

/** Original percussion pitch -> external MIDI note. Stored with the project. */
export type DrumSlots = Readonly<Record<string, number>>;

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
export function mapDrumPitch(pitch: number, mapping: DrumMapping, slots?: DrumSlots): number {
  if (mapping === 'general-midi') return pitch;
  if (slots) {
    const mapped = slots[String(pitch)];
    if (mapped === undefined) throw new RangeError(`Drum ${pitch} has no project MIDI slot.`);
    return mapped;
  }
  const index = CLASSIC_DRUM_INSTRUMENTS.findIndex(instrument => instrument.midi === pitch);
  return index < 0 ? pitch : 36 + index;
}

export function drumInstrumentLabel(pitch: number, mapping: DrumMapping, slots?: DrumSlots): string {
  return `${drumInstrumentName(pitch)} · ${mapping === 'chromatic' && slots && slots[String(pitch)] === undefined ? 'unassigned' : mapDrumPitch(pitch, mapping, slots)}`;
}
