import type { ScaleMode } from '../music/harmony';

export type BassRhythm = 'alternating' | 'full' | 'half' | 'tresillo' | 'sparse';
export type ChordRhythm = 'full' | 'half' | 'tresillo' | 'sparse' | 'single';
export type ChordVoicing = 'close' | 'open';
export type ArpeggioPattern = 'up' | 'down' | 'up-down' | 'random';
export type ArpeggioRate = 'eighth' | 'sixteenth';
export type DrumGroove = 'rock' | 'four-on-floor' | 'half-time' | 'sparse';
export type SectionType =
  | 'INTRO' | 'VERSE1' | 'VERSE2' | 'VERSE3' | 'CHORUS1' | 'CHORUS2' | 'HALF_CHORUS'
  | 'BREAKDOWN' | 'CHILL' | 'BUILDUP1' | 'BUILDUP2' | 'CHORUS3' | 'CLIMAX' | 'OUTRO';
export type ArrangedPart = 'bass' | 'chords' | 'arpeggio' | 'drums';

export interface ArrangementSection {
  readonly id: string;
  readonly type: SectionType;
  readonly measures: number;
  readonly parts: Readonly<Record<ArrangedPart, boolean>>;
}

export interface BassSettings {
  readonly rhythm: BassRhythm;
  /** Chance, in percent, of choosing a chord tone instead of its root. */
  readonly noteVariation: number;
}

export interface ChordSettings {
  readonly rhythm: ChordRhythm;
  readonly voicing: ChordVoicing;
  readonly noteLengthPercent: number;
}

export interface ArpeggioSettings {
  readonly pattern: ArpeggioPattern;
  readonly rate: ArpeggioRate;
  readonly octaves: 1 | 2;
}

export interface DrumSettings {
  readonly groove: DrumGroove;
  readonly swingPercent: number;
}

export interface CompositionProject {
  readonly schemaVersion: 5;
  readonly name: string;
  readonly key: string;
  readonly scale: ScaleMode;
  readonly tempoBpm: number;
  /** Decimal text preserves Java long seeds when JSON is exported or persisted. */
  readonly seed: string;
  /** One-based diatonic chord degrees in the selected key and scale. */
  readonly progression: readonly number[];
  readonly bass: BassSettings;
  readonly chords: ChordSettings;
  readonly arpeggio: ArpeggioSettings;
  readonly drums: DrumSettings;
  readonly arrangement: readonly ArrangementSection[];
}

export const SECTION_TYPES: readonly SectionType[] = [
  'INTRO', 'VERSE1', 'VERSE2', 'VERSE3', 'CHORUS1', 'CHORUS2', 'HALF_CHORUS',
  'BREAKDOWN', 'CHILL', 'BUILDUP1', 'BUILDUP2', 'CHORUS3', 'CLIMAX', 'OUTRO',
];

export const ARRANGED_PARTS: readonly ArrangedPart[] = ['bass', 'chords', 'arpeggio', 'drums'];

const ALL_PARTS: Readonly<Record<ArrangedPart, boolean>> = {
  bass: true,
  chords: true,
  arpeggio: true,
  drums: true,
};

export const DEFAULT_ARRANGEMENT: readonly ArrangementSection[] = [
  { id: 'section-1', type: 'INTRO', measures: 2, parts: { ...ALL_PARTS, arpeggio: false, drums: false } },
  { id: 'section-2', type: 'VERSE1', measures: 4, parts: { ...ALL_PARTS, arpeggio: false } },
  { id: 'section-3', type: 'CHORUS1', measures: 4, parts: ALL_PARTS },
  { id: 'section-4', type: 'VERSE2', measures: 4, parts: { ...ALL_PARTS, arpeggio: false } },
  { id: 'section-5', type: 'CHORUS2', measures: 4, parts: ALL_PARTS },
  { id: 'section-6', type: 'OUTRO', measures: 2, parts: { ...ALL_PARTS, arpeggio: false, drums: false } },
];

export const DEFAULT_BASS_SETTINGS: BassSettings = {
  rhythm: 'alternating',
  noteVariation: 20,
};

export const DEFAULT_CHORD_SETTINGS: ChordSettings = {
  rhythm: 'full',
  voicing: 'close',
  noteLengthPercent: 100,
};

export const DEFAULT_ARPEGGIO_SETTINGS: ArpeggioSettings = {
  pattern: 'up',
  rate: 'eighth',
  octaves: 1,
};

export const DEFAULT_DRUM_SETTINGS: DrumSettings = {
  groove: 'rock',
  swingPercent: 50,
};

export const DEFAULT_PROJECT: CompositionProject = {
  schemaVersion: 5,
  name: 'Untitled composition',
  key: 'C',
  scale: 'major',
  tempoBpm: 120,
  seed: '42',
  progression: [1, 5, 6, 4],
  bass: DEFAULT_BASS_SETTINGS,
  chords: DEFAULT_CHORD_SETTINGS,
  arpeggio: DEFAULT_ARPEGGIO_SETTINGS,
  drums: DEFAULT_DRUM_SETTINGS,
  arrangement: DEFAULT_ARRANGEMENT,
};
