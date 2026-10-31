/** Policies select choices; persisted choices are never rerolled during phrase resolution. */
export interface VariationPolicy {
  readonly chance: number;
  readonly maxChanges: number;
}

export interface VariationGenerationSettings {
  readonly enabled: boolean;
  readonly policy: VariationPolicy;
}

export type VariationKind = 'melody-speed' | 'melody-direction' | 'arp-activity' | 'bass-weight' | 'chord-space' | 'drum-activity';

export interface ArrangementVariation {
  readonly trackId: string;
  readonly kind: VariationKind;
  readonly amount: -1 | 1;
  readonly enabled: boolean;
}

export interface SectionVariations {
  /** Exact signed Java long, independently salted from phrase generation. */
  readonly seed: string;
  readonly policy: VariationPolicy;
  readonly choices: readonly ArrangementVariation[];
}
