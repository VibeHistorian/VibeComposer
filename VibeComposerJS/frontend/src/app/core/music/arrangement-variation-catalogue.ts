import type { ArrangementVariation, VariationKind, VariationGenerationOptions } from './arrangement-variation.model';
import type { ArrangedPart } from './part-role';

export interface VariationDefinition {
  readonly label: string;
  readonly roles: readonly ArrangedPart[];
  readonly directions: readonly (-1 | 1)[];
  readonly activity: boolean;
  readonly register: boolean;
  readonly consumer: 'settings' | 'timed';
  readonly speedDelta?: number;
}

/** Consumer capabilities only. Context recommendations and allocation budgets belong to the planner. */
export const VARIATION_DEFINITIONS = {
  'drum-end-fill': { label: 'Snare section-end fill', roles: ['drums'], directions: [1], activity: true, register: false, consumer: 'timed' },
  'arp-subdivision-ramp': { label: "Arp subdivision ramp", roles: ["arpeggio"], directions: [1], activity: true, register: false, consumer: 'timed' },
  'accompaniment-velocity-ramp': { label: "Accompaniment velocity ramp", roles: ["arpeggio","chords"], directions: [-1, 1], activity: false, register: false, consumer: 'timed' },
  'arp-end-fill': { label: "Arp section-end fill", roles: ["arpeggio"], directions: [1], activity: true, register: false, consumer: 'timed' },
  'pre-drop-cut': { label: "Pre-drop accompaniment cut", roles: ["arpeggio","chords"], directions: [-1], activity: false, register: false, consumer: 'timed' },
  'melody-range': { label: "Contextual melody range", roles: ["melody"], directions: [-1, 1], activity: false, register: false, consumer: 'settings' },
  'melody-speed': { label: "Melody speed", roles: ["melody"], directions: [-1, 1], activity: true, register: false, consumer: 'settings', speedDelta: 15 },
  'melody-direction': { label: "Melody direction", roles: ["melody"], directions: [-1, 1], activity: false, register: false, consumer: 'settings' },
  'melody-register': { label: "Melody octave register", roles: ["melody"], directions: [-1, 1], activity: false, register: true, consumer: 'settings' },
  'arp-activity': { label: "Arp activity", roles: ["arpeggio"], directions: [-1, 1], activity: true, register: false, consumer: 'settings' },
  'arp-contour': { label: "Arp chord-relative contour", roles: ["arpeggio"], directions: [-1, 1], activity: false, register: false, consumer: 'settings' },
  'arp-register': { label: "Arp octave register", roles: ["arpeggio"], directions: [-1, 1], activity: false, register: true, consumer: 'settings' },
  'arp-density': { label: "Arp rhythm density", roles: ["arpeggio"], directions: [-1, 1], activity: true, register: false, consumer: 'settings' },
  'bass-weight': { label: "Bass octave weight", roles: ["bass"], directions: [-1, 1], activity: false, register: false, consumer: 'settings' },
  'chord-space': { label: "Chord spacing", roles: ["chords"], directions: [-1, 1], activity: false, register: false, consumer: 'settings' },
  'chord-transition': { label: "Late chord anticipation", roles: ["chords"], directions: [-1, 1], activity: false, register: false, consumer: 'settings' },
  'drum-activity': { label: "Cymbal activity", roles: ["drums"], directions: [-1, 1], activity: true, register: false, consumer: 'settings' },
  'drum-density': { label: "Hat / ride density", roles: ["drums"], directions: [-1, 1], activity: true, register: false, consumer: 'settings' },
  'drum-ghosts': { label: "Snare ghost notes", roles: ["drums"], directions: [-1, 1], activity: true, register: false, consumer: 'settings' },
} as const satisfies Record<VariationKind, VariationDefinition>;

/** Preserve historical per-role order: tied candidate selection consumes this ordering. */
export const VARIATION_KINDS_BY_ROLE: Record<ArrangedPart, readonly VariationKind[]> = {
  melody: ['melody-speed', 'melody-direction', 'melody-register', 'melody-range'], arpeggio: ['arp-activity', 'arp-density', 'arp-contour', 'arp-register', 'pre-drop-cut', 'arp-end-fill', 'accompaniment-velocity-ramp', 'arp-subdivision-ramp'], bass: ['bass-weight'],
  chords: ['chord-space', 'chord-transition', 'pre-drop-cut', 'accompaniment-velocity-ramp'], drums: ['drum-activity', 'drum-density', 'drum-ghosts', 'drum-end-fill'],
};

export function supportsVariation(role: ArrangedPart, kind: unknown, amount?: unknown): kind is VariationKind {
  if (typeof kind !== 'string' || !Object.hasOwn(VARIATION_DEFINITIONS, kind)) return false;
  const definition: VariationDefinition = VARIATION_DEFINITIONS[kind as VariationKind];
  return definition.roles.includes(role) && (amount === undefined || definition.directions.some(direction => direction === amount));
}

/** Strength uses speed points, independently of signed direction. */
export function validMelodySpeedDelta(value: unknown): value is number {
  return Number.isInteger(value) && (value as number) >= 1 && (value as number) <= 200;
}

export function validVariationParameters(kind: VariationKind, value: unknown): boolean {
  if (value === undefined) return true;
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const fields = value as Record<string, unknown>;
  const strength = variationStrength(kind);
  return !!strength && Object.keys(fields).length === 1 && Object.hasOwn(fields, strength.key)
    && Number.isInteger(fields[strength.key]) && (fields[strength.key] as number) >= 1 && (fields[strength.key] as number) <= strength.max;
}

export interface VariationStrength {
  readonly key: keyof NonNullable<ArrangementVariation['parameters']>;
  readonly label: string;
  readonly max: number;
  readonly defaultValue: number;
}

/** Units and hard input bounds; musical adapters still enforce their own effective bounds. */
export function variationStrength(kind: VariationKind, amount: -1 | 1 = 1): VariationStrength | undefined {
  switch (kind) {
    case 'pre-drop-cut': return { key: 'cutQuarters', label: 'Cut length (quarter beats)', max: 8, defaultValue: 4 };
    case 'arp-end-fill': case 'drum-end-fill': return { key: 'fillLimit', label: 'Maximum fill retriggers', max: 8, defaultValue: 4 };
    case 'arp-subdivision-ramp': return { key: 'subdivisionLimit', label: 'Maximum added attacks', max: 64, defaultValue: 64 };
    case 'melody-speed': return { key: 'speedDelta', label: 'Speed points', max: 200, defaultValue: 15 };
    case 'melody-range': return { key: 'rangeDelta', label: 'Semitones per boundary', max: 12, defaultValue: 2 };
    case 'chord-transition': return { key: 'transitionDelta', label: 'Chance percentage points', max: 75, defaultValue: 25 };
    case 'accompaniment-velocity-ramp': return { key: 'velocityDelta', label: 'Velocity percentage points', max: 50, defaultValue: amount > 0 ? 15 : 20 };
    case 'arp-density': case 'drum-density': return { key: 'densitySteps', label: 'Density steps', max: 3, defaultValue: 1 };
    case 'arp-activity': return { key: 'activitySteps', label: 'Subdivision steps', max: 3, defaultValue: 1 };
    case 'drum-activity': return { key: 'pauseDelta', label: 'Activity percentage points', max: 100, defaultValue: 15 };
    case 'arp-contour': return { key: 'contourSteps', label: 'Contour height (diatonic steps)', max: 7, defaultValue: 2 };
    default: return undefined;
  }
}

export function melodySpeedDelta(choice: ArrangementVariation): number {
  return choice.parameters?.speedDelta ?? VARIATION_DEFINITIONS['melody-speed'].speedDelta;
}

export function validVelocityDelta(value: unknown): value is number {
  return Number.isInteger(value) && (value as number) >= 1 && (value as number) <= 50;
}

export function velocityDelta(choice: ArrangementVariation): number {
  return choice.parameters?.velocityDelta ?? (choice.amount > 0 ? 15 : 20);
}

/** Capture insertion/reroll preferences before checking musical eligibility. */
export function variationParameters(kind: VariationKind, amount: -1 | 1, options?: VariationGenerationOptions): ArrangementVariation['parameters'] {
  const spec = variationStrength(kind, amount);
  if (!spec) return undefined;
  const value = kind === 'melody-speed' ? options?.melodySpeedDelta ?? spec.defaultValue
    : kind === 'accompaniment-velocity-ramp' && amount < 0 ? options?.velocityFallDelta ?? spec.defaultValue
    : options?.strengths?.[kind];
  return value === undefined ? undefined : { [spec.key]: value };
}
