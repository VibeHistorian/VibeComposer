import type { ArrangedPart, ArrangementSection, CommonPartSettings, CompositionTrack, PartSettingsPatch } from '../project/project.model';
import { CHORD_SPAN_FILLS } from './chord-span-fill';

export interface PartControl {
  readonly key: string;
  readonly label: string;
  readonly kind: 'number' | 'choice' | 'boolean';
  readonly minimum?: number;
  readonly maximum?: number;
  readonly step?: number;
  readonly unit?: string;
  readonly options?: readonly (string | number)[];
  readonly defaultValue?: string | number | boolean;
  readonly description?: string;
}

const number = (key: string, label: string, minimum: number, maximum: number, defaultValue?: number, unit = '%'): PartControl =>
  ({ key, label, kind: 'number', minimum, maximum, defaultValue, unit });
const choice = (key: string, label: string, options: readonly (string | number)[]): PartControl => ({ key, label, kind: 'choice', options });
const fill: readonly PartControl[] = [
  { ...choice('chordSpanFill', 'Fill', CHORD_SPAN_FILLS), defaultValue: 'ALL',
    description: 'Choose active progression chords. Java ODD plays slots 2, 4, …; EVEN plays 1, 3, …. HALF1 uses the first floor(n/2) chords; HALF2 uses the rest.' },
  { key: 'fillFlip', label: 'Fill flip', kind: 'boolean', defaultValue: false,
    description: 'Invert which progression chords play. ALL + Fill flip produces silence.' },
];
const pitched: readonly PartControl[] = [{ ...number('transpose', 'Transpose', -36, 36, 0, ' st'), step: 12 },
  number('velocityMin', 'Min velocity', 1, 127, 69, ''), number('velocityMax', 'Max velocity', 1, 127, 89, '')];

/** Only expose settings with a working musical consumer in this buildout slice. */
export const PART_CONTROLS: Readonly<Record<ArrangedPart, readonly PartControl[]>> = {
  melody: [],
  bass: [...fill, choice('rhythm', 'Rhythm', ['alternating', 'full', 'half', 'tresillo', 'sparse']),
    number('noteVariation', 'Chord tone variation', 0, 100), { key: 'octaveInterval', label: 'Octave interval', kind: 'boolean' },
    ...pitched, number('noteLengthMultiplier', 'Note length', 25, 200, 100)],
  chords: [...fill, { ...choice('rhythm', 'Rhythm pattern', ['full', 'half', 'tresillo', 'sparse', 'single', 'one-six']),
    description: 'Java patterns: full = FULL, half = ALT, sparse = ONEPER4, one-six = ONESIX. This grid is separate from chord fill.' },
    { ...number('hitsPerPattern', 'Hits', 1, 32, 8, ''),
      description: 'Grid subdivisions per four-beat chord. Pattern rests mean fewer sounded notes than the Hits value.' },
    { ...number('patternShift', 'Shift', 0, 8, 0, ''),
      description: 'Rotate the repeated eight-slot pattern right before trimming it to the Hits value, matching Java.' },
    { key: 'patternFlip', label: 'Pattern flip', kind: 'boolean', defaultValue: false,
      description: 'Invert notes and rests inside the chord rhythm grid. Fill still determines which chords play.' },
    choice('voicing', 'Voicing', ['close', 'open']), number('noteLengthPercent', 'Note length', 25, 125), ...pitched],
  arpeggio: [...fill, choice('pattern', 'Pitch direction', ['up', 'down', 'up-down', 'random']),
    choice('rate', 'Rate', ['eighth', 'sixteenth']), choice('octaves', 'Octaves', [1, 2]),
    ...pitched, number('noteLengthMultiplier', 'Note length', 25, 200, 100)],
  drums: [...fill, choice('groove', 'Groove', ['rock', 'four-on-floor', 'half-time', 'sparse']),
    number('swingPercent', 'Swing', 50, 75), number('noteLengthMultiplier', 'Note length', 25, 200, 100)],
};

export function settingsValues(settings: PartSettingsPatch, role: ArrangedPart): Readonly<Record<string, string | number | boolean>> {
  return Object.fromEntries(PART_CONTROLS[role].map((control) => [control.key,
    (settings as Record<string, string | number | boolean>)[control.key] ?? control.defaultValue!]));
}

/** Patch equality includes explicit inheritance boundaries and ignores object property order. */
export function partPatchesEqual(left: PartSettingsPatch | undefined, right: PartSettingsPatch | undefined): boolean {
  const leftValues = left as Record<string, unknown> | undefined;
  const rightValues = right as Record<string, unknown> | undefined;
  return Object.keys(leftValues ?? {}).length === Object.keys(rightValues ?? {}).length
    && Object.entries(leftValues ?? {}).every(([key, value]) => value === rightValues?.[key]);
}

export function decodePartPatch(role: ArrangedPart, value: unknown, enforceSteps = false): PartSettingsPatch | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const result: Record<string, string | number | boolean> = {};
  for (const [key, field] of Object.entries(value)) {
    const control = PART_CONTROLS[role].find((candidate) => candidate.key === key);
    if (!control) return undefined;
    if (control.kind === 'number' && (!Number.isInteger(field) || (field as number) < control.minimum! || (field as number) > control.maximum!)) return undefined;
    // Saved values remain readable; new edits follow the control's allowed steps.
    if (enforceSteps && control.step && ((field as number) - control.minimum!) % control.step !== 0) return undefined;
    if (control.kind === 'choice' && !control.options!.includes(field as string | number)) return undefined;
    if (control.kind === 'boolean' && typeof field !== 'boolean') return undefined;
    result[key] = field as string | number | boolean;
  }
  return result as PartSettingsPatch;
}

export function validVelocityRange(settings: PartSettingsPatch, role: ArrangedPart): boolean {
  const values = settingsValues(settings, role);
  return role === 'melody' || role === 'drums' || (values['velocityMin'] as number) <= (values['velocityMax'] as number);
}

export function velocityBounds(settings: CommonPartSettings): readonly [number, number] {
  const minimum = settings.velocityMin ?? 69;
  const maximum = settings.velocityMax ?? 89;
  if (!Number.isInteger(minimum) || !Number.isInteger(maximum) || minimum < 1 || maximum > 127 || minimum > maximum) {
    throw new RangeError('Velocity bounds are outside the supported range.');
  }
  return [minimum, maximum];
}

/** Keep omitted common fields omitted on import, rather than creating implicit overrides. */
export function decodeCommonPartSettings(role: ArrangedPart, value: unknown): CommonPartSettings | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const common = Object.fromEntries(Object.entries(value).filter(([key]) =>
    ['transpose', 'velocityMin', 'velocityMax', 'noteLengthMultiplier', 'chordSpanFill', 'fillFlip',
      'hitsPerPattern', 'patternShift', 'patternFlip'].includes(key)));
  const decoded = decodePartPatch(role, common);
  return decoded && validVelocityRange(decoded, role) ? decoded : undefined;
}

export function resolvePartTrack(track: CompositionTrack, section?: ArrangementSection): CompositionTrack {
  if (!section) return track;
  return { ...track, generatorSettings: { ...track.generatorSettings,
    ...section.rolePartOverrides?.[track.role], ...section.trackPartOverrides?.[track.id] } } as CompositionTrack;
}

export function hasPartOverrides(section: ArrangementSection, role: ArrangedPart, trackId?: string): boolean {
  return !!Object.keys(section.rolePartOverrides?.[role] ?? {}).length
    || !!Object.keys(trackId ? section.trackPartOverrides?.[trackId] ?? {} : {}).length;
}
