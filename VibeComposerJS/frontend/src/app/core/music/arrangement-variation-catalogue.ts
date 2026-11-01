import type { ArrangementVariation, VariationKind } from './arrangement-variation.model';
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
  chords: ['chord-space', 'chord-transition', 'pre-drop-cut', 'accompaniment-velocity-ramp'], drums: ['drum-activity', 'drum-density', 'drum-ghosts'],
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
  if (kind === 'chord-transition' || kind === 'melody-range') {
    const key = kind === 'chord-transition' ? 'transitionDelta' : 'rangeDelta';
    return Object.keys(fields).length === 1 && Object.hasOwn(fields, key)
      && Number.isInteger(fields[key]) && (fields[key] as number) >= 1 && (fields[key] as number) <= (kind === 'chord-transition' ? 75 : 12);
  }
  if (kind === 'accompaniment-velocity-ramp') return Object.keys(fields).length === 1
    && Object.hasOwn(fields, 'velocityDelta') && validVelocityDelta(fields['velocityDelta']);
  if (kind !== 'melody-speed') return false;
  return Object.keys(fields).length === 1 && Object.hasOwn(fields, 'speedDelta') && validMelodySpeedDelta(fields['speedDelta']);
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
