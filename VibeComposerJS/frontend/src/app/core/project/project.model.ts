import type { ScaleMode } from '../music/harmony';
import type { ArrangedPart } from '../music/part-role';
import type { PhraseNote } from '../music/phrase-note';
import type { MelodyGlobalSettings } from '../music/part-settings.model';
import type { TrackGenerationPolicies, InstrumentGenerationPolicy } from '../music/generation-policy.model';
import type { ArrangementSection } from './arrangement.model';
import type { CompositionTrack } from './track.model';

export interface CompositionProject {
  readonly humanization?: { readonly timingBeats: number; readonly durationBeats: number };
  readonly midiPadding?: Readonly<Partial<Record<ArrangedPart, number>>>;
  readonly harmonySettings?: import('../music/harmony-voicing').HarmonySettings;
  readonly melodyGlobal?: MelodyGlobalSettings;
  readonly schemaVersion: 12;
  readonly name: string;
  readonly key: string;
  readonly scale: ScaleMode;
  readonly transposeSemitones?: number;
  readonly tempoBpm: number;
  readonly randomizeTempoOnCompose?: boolean;
  readonly randomizeHarmonyOnCompose?: boolean;
  readonly randomizeArrangementOnCompose?: boolean;
  readonly arrangementPreview?: boolean;
  readonly arrangementManual?: boolean;
  /** Omission keeps automatic variation generation disabled; realized choices remain independent. */
  readonly arrangementVariationGeneration?: import('../music/arrangement-variation.model').VariationGenerationSettings;
  readonly randomizeTransposeOnCompose?: boolean;
  /** Decimal text preserves Java long seeds when JSON is exported or persisted. */
  readonly seed: string;
  /** One-based diatonic chord degrees in the selected key and scale. */
  readonly progression: readonly number[];
  /** Explicit Chordlet edits; null entries continue to follow the diatonic mode. */
  readonly progressionChords?: readonly (import('../music/harmony').ProgressionChord | null)[];
  readonly arrangement: readonly ArrangementSection[];
  /** Role-level phrase edits retained by the phrase editing API. */
  readonly editedPhrases: Readonly<Partial<Record<ArrangedPart, readonly PhraseNote[]>>>;
  /** Independent tracks own their generator settings and mix. */
  readonly tracks: readonly CompositionTrack[];
  readonly trackGenerationPolicies?: Readonly<Partial<TrackGenerationPolicies>>;
  readonly instrumentGenerationPolicies?: Readonly<Partial<Record<ArrangedPart, InstrumentGenerationPolicy>>>;
  readonly trackRerollCounts?: Readonly<Partial<Record<ArrangedPart, number>>>;
}
