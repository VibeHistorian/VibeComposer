import type { ScaleMode } from '../music/harmony';

export interface CompositionProject {
  readonly schemaVersion: 1;
  readonly name: string;
  readonly key: string;
  readonly scale: ScaleMode;
  readonly tempoBpm: number;
  /** Decimal text preserves Java long seeds when JSON is exported or persisted. */
  readonly seed: string;
  /** One-based diatonic chord degrees in the selected key and scale. */
  readonly progression: readonly number[];
}

export const DEFAULT_PROJECT: CompositionProject = {
  schemaVersion: 1,
  name: 'Untitled composition',
  key: 'C',
  scale: 'major',
  tempoBpm: 120,
  seed: '42',
  progression: [1, 5, 6, 4],
};
