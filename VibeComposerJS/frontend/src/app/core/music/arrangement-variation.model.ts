/** Policies select choices; persisted choices are never rerolled during phrase resolution. */
export interface VariationPolicy {
  readonly chance: number;
  readonly maxChanges: number;
}

export type VariationKind = 'melody-speed' | 'melody-direction' | 'melody-register' | 'arp-activity' | 'arp-density' | 'arp-contour' | 'arp-register'
  | 'bass-weight' | 'chord-space' | 'chord-transition' | 'drum-activity' | 'drum-density' | 'drum-ghosts';
export type VariationGesture = 'establish' | 'thin' | 'build-pulse' | 'reinforce-foundation' | 'release-tension' | 'sustain' | 'resolve';

export interface ArrangementVariation {
  readonly trackId: string;
  readonly kind: VariationKind;
  readonly amount: -1 | 1;
  readonly enabled: boolean;
}

export interface SectionVariations {
  /** Realized musical purpose; current intent is still derived from the arrangement. */
  readonly gesture?: VariationGesture;
  /** Exact signed Java long, independently salted from phrase generation. */
  readonly seed: string;
  readonly policy: VariationPolicy;
  readonly choices: readonly ArrangementVariation[];
}
