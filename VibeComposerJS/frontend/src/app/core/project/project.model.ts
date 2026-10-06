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
  /** Legacy Section part chance percentages for bass, chords, arpeggio, and drums. */
  readonly partChances: Readonly<Record<ArrangedPart, number>>;
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
  readonly schemaVersion: 6;
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

/** Default role chances from Arrangement.defaultSections in the Java application. */
export const DEFAULT_SECTION_PART_CHANCES: Readonly<Record<SectionType, Readonly<Record<ArrangedPart, number>>>> = {
  INTRO: { bass: 10, chords: 40, arpeggio: 25, drums: 20 },
  VERSE1: { bass: 60, chords: 30, arpeggio: 25, drums: 40 },
  VERSE2: { bass: 60, chords: 40, arpeggio: 50, drums: 50 },
  VERSE3: { bass: 80, chords: 40, arpeggio: 70, drums: 60 },
  CHORUS1: { bass: 90, chords: 50, arpeggio: 35, drums: 60 },
  CHORUS2: { bass: 100, chords: 60, arpeggio: 50, drums: 70 },
  HALF_CHORUS: { bass: 100, chords: 60, arpeggio: 50, drums: 80 },
  BREAKDOWN: { bass: 60, chords: 60, arpeggio: 25, drums: 40 },
  CHILL: { bass: 30, chords: 70, arpeggio: 70, drums: 10 },
  BUILDUP1: { bass: 40, chords: 10, arpeggio: 20, drums: 70 },
  BUILDUP2: { bass: 60, chords: 20, arpeggio: 40, drums: 90 },
  CHORUS3: { bass: 100, chords: 80, arpeggio: 80, drums: 85 },
  CLIMAX: { bass: 100, chords: 100, arpeggio: 100, drums: 100 },
  OUTRO: { bass: 70, chords: 60, arpeggio: 40, drums: 10 },
};

function defaultSection(id: string, type: SectionType, measures: number): ArrangementSection {
  return {
    id,
    type,
    measures,
    parts: { ...ALL_PARTS },
    partChances: { ...DEFAULT_SECTION_PART_CHANCES[type] },
  };
}

export const DEFAULT_ARRANGEMENT: readonly ArrangementSection[] = [
  defaultSection('section-1', 'INTRO', 2),
  defaultSection('section-2', 'VERSE1', 4),
  defaultSection('section-3', 'CHORUS1', 4),
  defaultSection('section-4', 'VERSE2', 4),
  defaultSection('section-5', 'CHORUS2', 4),
  defaultSection('section-6', 'OUTRO', 2),
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
  schemaVersion: 6,
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
