import type { ScaleMode } from '../music/harmony';
import type { ArrangedPart } from '../music/part-role';
import type { PhraseNote } from '../music/phrase-note';
import type { MelodyGlobalSettings } from '../music/part-settings.model';
import type { TrackGenerationPolicies, InstrumentGenerationPolicy } from '../music/generation-policy.model';
import type { ArrangementSection } from './arrangement.model';
import type { CompositionTrack } from './track.model';

export const PROJECT_SCHEMA_VERSION = 14;

export interface CompositionProject {
  readonly drumSlots?: import('../music/drum-instruments').DrumSlots;
  readonly humanization?: { readonly timingBeats: number; readonly durationBeats: number };
  readonly midiPadding?: Readonly<Partial<Record<ArrangedPart, number>>>;
  readonly harmonySettings?: import('../music/harmony-voicing').HarmonySettings;
  readonly melodyGlobal?: MelodyGlobalSettings;
  readonly schemaVersion: typeof PROJECT_SCHEMA_VERSION;
  readonly name: string;
  readonly scale: ScaleMode;
  readonly transposeSemitones?: number;
  readonly masterVolumePercent?: number;
  /** Built-in synth key or loaded SF2 filename. Defaults to TinySynth. */
  readonly soundbankName?: string;
  readonly tempoBpm: number;
  readonly randomizeTempoOnCompose?: boolean;
  readonly randomizeModeOnCompose?: boolean;
  readonly randomizeHarmonyOnCompose?: boolean;
  readonly randomizeArrangementOnCompose?: boolean;
  readonly arrangementPreview?: boolean;
  readonly arrangementManual?: boolean;
  /** Musical policy for future variations; generation switches belong to UI preferences. */
  readonly arrangementVariationPolicy?: import('../music/arrangement-variation.model').VariationPolicy;
  readonly randomizeTransposeOnCompose?: boolean;
  /** Decimal text preserves Java long seeds when JSON is exported or persisted. */
  readonly seed: string;
  /** One-based diatonic chord degrees in the selected scale from C. */
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
