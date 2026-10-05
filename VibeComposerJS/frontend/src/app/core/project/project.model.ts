import type { ScaleMode } from '../music/harmony';

export type BassRhythm = 'alternating' | 'full' | 'half' | 'tresillo' | 'sparse';

export interface BassSettings {
  readonly rhythm: BassRhythm;
  /** Chance, in percent, of choosing a chord tone instead of its root. */
  readonly noteVariation: number;
}

export interface CompositionProject {
  readonly schemaVersion: 2;
  readonly name: string;
  readonly key: string;
  readonly scale: ScaleMode;
  readonly tempoBpm: number;
  /** Decimal text preserves Java long seeds when JSON is exported or persisted. */
  readonly seed: string;
  /** One-based diatonic chord degrees in the selected key and scale. */
  readonly progression: readonly number[];
  readonly bass: BassSettings;
}

export const DEFAULT_BASS_SETTINGS: BassSettings = {
  rhythm: 'alternating',
  noteVariation: 20,
};

export const DEFAULT_PROJECT: CompositionProject = {
  schemaVersion: 2,
  name: 'Untitled composition',
  key: 'C',
  scale: 'major',
  tempoBpm: 120,
  seed: '42',
  progression: [1, 5, 6, 4],
  bass: DEFAULT_BASS_SETTINGS,
};
