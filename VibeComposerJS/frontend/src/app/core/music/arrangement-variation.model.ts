/** Policies select choices; persisted choices are never rerolled during phrase resolution. */
export interface VariationPolicy {
  readonly chance: number;
  readonly maxChanges: number;
  /** Default for future Melody speed choices; saved choices retain their own delta. */
  readonly melodySpeedDelta?: number;
}

export type VariationKind = 'melody-speed' | 'melody-direction' | 'melody-register' | 'melody-range' | 'arp-activity' | 'arp-density' | 'arp-contour' | 'arp-register'
  | 'accompaniment-velocity-ramp' | 'arp-end-fill' | 'pre-drop-cut' | 'bass-weight' | 'chord-space' | 'chord-transition' | 'drum-activity' | 'drum-density' | 'drum-ghosts';
export type VariationGesture = 'establish' | 'thin' | 'build-pulse' | 'reinforce-foundation' | 'release-tension' | 'sustain' | 'resolve';

export interface ArrangementVariation {
  readonly trackId: string;
  readonly kind: VariationKind;
  readonly amount: -1 | 1;
  readonly enabled: boolean;
  readonly origin?: 'authored';
  /** Exact realization seed pinned independently of later section rerolls. */
  readonly seed?: string;
  /** Present only on Melody speed choices in this first strength slice. */
  readonly parameters?: { readonly speedDelta: number };
}

export interface SectionVariations {
  /** Realized musical purpose; current intent is still derived from the arrangement. */
  readonly gesture?: VariationGesture;
  /** Exact signed Java long, independently salted from phrase generation. */
  readonly seed: string;
  readonly policy: VariationPolicy;
  readonly choices: readonly ArrangementVariation[];
}
