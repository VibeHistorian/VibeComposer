import type { ArrangedPart } from './part-role';
import type { DrumRerollStyleId } from './drum-reroll-styles';

export type InstrumentPool = 'AUTO' | 'ALL' | 'MELODY' | 'BASS' | 'CHORD' | 'PLUCK' | 'LONG_PAD' | 'DRUM' | 'DRUM_FULL' | 'DRUM_ACOUSTIC' | 'DRUM_HAND';

export interface InstrumentGenerationPolicy {
  readonly randomize: boolean;
  readonly pool: InstrumentPool;
  readonly sameInstrument?: boolean;
  readonly sustainChance?: number;
}

export interface SeedGenerationPolicy {
  readonly rerollSeeds?: boolean;
  readonly sameSeed?: boolean;
}

/** Rules for choosing stored part values, separate from phrase generation inputs. */
export interface ArpeggioGenerationPolicy extends SeedGenerationPolicy {
  readonly onCompose?: boolean;
  readonly fixedHits: number;
  readonly randomHits: boolean;
  readonly sameHits: boolean;
  readonly powerOfTwo: boolean;
  readonly fills: boolean;
  readonly patterns: boolean;
  readonly transpose: boolean;
  readonly randomSpan: boolean;
  readonly maxSplit: number;
  readonly lengthMin: number;
  readonly lengthMax: number;
  readonly maxRepeat: number;
  readonly shiftChance: number;
  readonly voicesMode: 'NONE' | 'FIXED' | 'AT_MOST';
  readonly voices: number;
  readonly voicesChance: number;
  readonly velocityMin: number;
  readonly velocityMax: number;
}

export interface ChordGenerationPolicy extends SeedGenerationPolicy {
  readonly onCompose?: boolean;
  readonly strum?: boolean;
  readonly struminess?: number;
  readonly split?: boolean;
  readonly maxSplit?: number;
  readonly maxStrumPause?: number;
  readonly expandChance?: number;
  readonly fills: boolean;
  readonly patterns: boolean;
  readonly transpose: boolean;
  readonly varyLength: boolean;
  readonly delay: boolean;
  readonly shiftChance: number;
  readonly voicesMode: 'NONE' | 'FIXED' | 'AT_MOST';
  readonly voices: number;
  readonly voicesChance: number;
  readonly velocityMin: number;
  readonly velocityMax: number;
}

export interface DrumGenerationPolicy extends SeedGenerationPolicy {
  readonly rerollStyle?: DrumRerollStyleId;
  readonly density?: number;
  readonly stereoWidth?: number;
  readonly experiment?: number;
  readonly layering?: number;
  readonly ring?: number;
  readonly dynamicChance?: number;
  readonly overrandomizeChance?: number;
  readonly melodyChance?: number;
  readonly onCompose?: boolean;
  readonly patterns: boolean;
  readonly fills: boolean;
  readonly maxSwing: number;
  readonly randomOffset: boolean;
  readonly hitsMultiplier: '1' | '1/2' | '3/4' | '3/2' | '2';
  readonly shiftChance: number;
}

export const DEFAULT_DRUM_KIT_POLICY = Object.freeze({ density: 45, stereoWidth: 55, experiment: 20, layering: 25, ring: 25 });

export interface MelodyGenerationPolicy extends SeedGenerationPolicy {
  readonly onCompose?: boolean;
  readonly targetsOnCompose?: boolean;
  readonly patternsOnCompose?: boolean;
  readonly rerollSeeds: boolean;
  readonly sameSeed: boolean;
  readonly patterns: boolean;
}

/** Bass uses its audited creation branches with an optional compose reroll. */
export interface TrackGenerationPolicies {
  readonly bass: SeedGenerationPolicy & { readonly onCompose?: boolean };
  readonly chords: ChordGenerationPolicy;
  readonly arpeggio: ArpeggioGenerationPolicy;
  readonly drums: DrumGenerationPolicy;
  readonly melody: MelodyGenerationPolicy;
}

export type TrackGenerationPolicy = TrackGenerationPolicies[ArrangedPart];

export type TrackGenerationPolicyPatch = Partial<ChordGenerationPolicy & ArpeggioGenerationPolicy & DrumGenerationPolicy & MelodyGenerationPolicy>;
