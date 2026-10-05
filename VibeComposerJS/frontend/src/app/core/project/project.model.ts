import type { ScaleMode } from '../music/harmony';

export type BassRhythm = 'alternating' | 'full' | 'half' | 'tresillo' | 'sparse';
export type ChordRhythm = 'full' | 'half' | 'tresillo' | 'sparse' | 'single';
export type ChordVoicing = 'close' | 'open';
export type ArpeggioPattern = 'up' | 'down' | 'up-down' | 'random';
export type ArpeggioRate = 'eighth' | 'sixteenth';
export type DrumGroove = 'rock' | 'four-on-floor' | 'half-time' | 'sparse';

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
  readonly schemaVersion: 4;
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
}

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
  schemaVersion: 4,
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
};
