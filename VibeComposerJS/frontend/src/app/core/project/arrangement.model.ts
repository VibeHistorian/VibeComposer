import type { ScaleMode } from '../music/harmony';
import type { ArrangedPart } from '../music/part-role';
import type { PhraseNote } from '../music/phrase-note';
import type { PartSettingsByRole, PartSettingsPatch } from '../music/part-settings.model';

export type SectionType =
  | 'INTRO' | 'VERSE1' | 'VERSE2' | 'VERSE3' | 'CHORUS1' | 'CHORUS2' | 'HALF_CHORUS'
  | 'BREAKDOWN' | 'CHILL' | 'BUILDUP1' | 'BUILDUP2' | 'CHORUS3' | 'CLIMAX' | 'OUTRO';

export interface ArrangementSection {
  /** Sparse section context; omission inherits project/part values. */
  readonly context?: SectionMusicalContext;
  readonly customChords?: readonly (import('../music/harmony').ProgressionChord | null)[];
  readonly customDurations?: readonly number[];
  /** Realized harmony selections; explicit section chords take precedence. */
  readonly useMelodyHarmony?: boolean;
  readonly swapMelody?: boolean;
  readonly id: string;
  readonly type: SectionType;
  readonly measures: number;
  /** Optional one-chord-per-bar override; absent sections follow the Create progression. */
  readonly chordDegrees?: readonly number[];
  /** Materialized arrangement state; chance settings live with section type preferences. */
  readonly trackPresence: Readonly<Record<string, boolean>>;
  readonly rolePartOverrides?: Readonly<Partial<{ [R in ArrangedPart]: Partial<PartSettingsByRole[R]> }>>;
  /** Manual phrase for this section and independent track, in section-relative beats. */
  readonly trackPhrases?: Readonly<Record<string, readonly PhraseNote[]>>;
  /** Use generated notes here even when the global track has a manual phrase. */
  readonly generatedTrackIds?: readonly string[];
  readonly trackPartOverrides?: Readonly<Record<string, PartSettingsPatch>>;
}

export interface SectionMusicalContext {
  readonly velocityPercent?: Readonly<Partial<Record<ArrangedPart, number>>>;
  readonly tempoBpm?: number;
  readonly swingPercent?: number;
  readonly key?: string;
  readonly scale?: ScaleMode;
  readonly keyChange?: number;
  /** Direct transposition or an ending ii–V cadence in the selected section key. */
  readonly keyChangeType?: 'direct' | 'two-five-one';
  readonly beatDurationMultiplier?: 0.5 | 1 | 2;
}

export const SECTION_TYPES: readonly SectionType[] = [
  'INTRO', 'VERSE1', 'VERSE2', 'VERSE3', 'CHORUS1', 'CHORUS2', 'HALF_CHORUS',
  'BREAKDOWN', 'CHILL', 'BUILDUP1', 'BUILDUP2', 'CHORUS3', 'CLIMAX', 'OUTRO',
];
