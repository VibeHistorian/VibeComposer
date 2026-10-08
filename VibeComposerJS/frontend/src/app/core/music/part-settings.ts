import type { ArrangedPart, ArrangementSection, CommonPartSettings, CompositionTrack, PartSettingsPatch } from '../project/project.model';

export interface PartControl {
  readonly key: string;
  readonly label: string;
  readonly kind: 'number' | 'choice' | 'boolean';
  readonly minimum?: number;
  readonly maximum?: number;
  readonly step?: number;
  readonly unit?: string;
  readonly options?: readonly (string | number)[];
  readonly defaultValue?: number;
}

const number = (key: string, label: string, minimum: number, maximum: number, defaultValue?: number, unit = '%'): PartControl =>
  ({ key, label, kind: 'number', minimum, maximum, defaultValue, unit });
const choice = (key: string, label: string, options: readonly (string | number)[]): PartControl => ({ key, label, kind: 'choice', options });
const pitched: readonly PartControl[] = [{ ...number('transpose', 'Transpose', -36, 36, 0, ' st'), step: 12 },
  number('velocityMin', 'Min velocity', 1, 127, 69, ''), number('velocityMax', 'Max velocity', 1, 127, 89, '')];

/** Only expose settings with a working musical consumer in this buildout slice. */
export const PART_CONTROLS: Readonly<Record<ArrangedPart, readonly PartControl[]>> = {
  bass: [choice('rhythm', 'Rhythm', ['alternating', 'full', 'half', 'tresillo', 'sparse']),
    number('noteVariation', 'Chord tone variation', 0, 100), { key: 'octaveInterval', label: 'Octave interval', kind: 'boolean' },
    ...pitched, number('noteLengthMultiplier', 'Note length', 25, 200, 100)],
  chords: [choice('rhythm', 'Rhythm', ['full', 'half', 'tresillo', 'sparse', 'single']),
    choice('voicing', 'Voicing', ['close', 'open']), number('noteLengthPercent', 'Note length', 25, 125), ...pitched],
  arpeggio: [choice('pattern', 'Pitch direction', ['up', 'down', 'up-down', 'random']),
    choice('rate', 'Rate', ['eighth', 'sixteenth']), choice('octaves', 'Octaves', [1, 2]),
    ...pitched, number('noteLengthMultiplier', 'Note length', 25, 200, 100)],
  drums: [choice('groove', 'Groove', ['rock', 'four-on-floor', 'half-time', 'sparse']),
    number('swingPercent', 'Swing', 50, 75), number('noteLengthMultiplier', 'Note length', 25, 200, 100)],
};

export function settingsValues(settings: PartSettingsPatch, role: ArrangedPart): Readonly<Record<string, string | number | boolean>> {
  return Object.fromEntries(PART_CONTROLS[role].map((control) => [control.key,
    (settings as Record<string, string | number | boolean>)[control.key] ?? control.defaultValue!]));
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
  return role === 'drums' || (values['velocityMin'] as number) <= (values['velocityMax'] as number);
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
    ['transpose', 'velocityMin', 'velocityMax', 'noteLengthMultiplier'].includes(key)));
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
