import type { ArrangedPart, ArrangementSection, CommonPartSettings, CompositionTrack, PartSettingsPatch } from '../project/project.model';
import { CHORD_SPAN_FILLS } from './chord-span-fill';
import { BASS_RHYTHMS, RHYTHM_PATTERNS } from './rhythm-patterns';
import { DEFAULT_CUSTOM_VELOCITIES, isVelocityPattern } from './velocity-pattern';

export type PartSettingValue = string | number | boolean | readonly number[];

export interface PartControl {
  readonly key: string;
  readonly label: string;
  readonly kind: 'number' | 'integer' | 'integers' | 'choice' | 'boolean' | 'pattern' | 'velocities';
  readonly minimum?: number;
  readonly maximum?: number;
  readonly step?: number;
  readonly allowedValues?: readonly number[];
  readonly unit?: string;
  readonly options?: readonly (string | number)[];
  readonly defaultValue?: PartSettingValue;
  readonly description?: string;
  /** Opt in when a numeric value is too long for the knob's center. */
  readonly showValueBelow?: boolean;
  readonly advanced?: boolean;
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

export const MELODY_TRANSPOSES: readonly number[] = Object.freeze(Array.from({ length: 7 }, (_, index) => index * 12 - 36)
  .flatMap((octave) => [octave, octave + 5, octave + 7]).filter((value) => value <= 36));

const rhythmControls: readonly PartControl[] = [
  { ...choice('rhythm', 'Rhythm pattern', RHYTHM_PATTERNS),
    description: 'Choose a rhythm within each chord. Euclid distributes Pulses across Hits; Fill independently selects which chords play.' },
    { ...number('hitsPerPattern', 'Hits', 1, 32, 8, ''),
      description: 'Grid subdivisions per four-beat chord. Pattern rests mean fewer sounded notes than the Hits value.' },
    { ...number('euclideanPulses', 'Pulses', 0, 32, 4, ''),
      description: 'Sounded slots in the Euclidean rhythm before Pattern flip. Uses at most Hits pulses; the requested value is retained when Hits is reduced.' },
    { ...number('patternShift', 'Shift', 0, 8, 0, ''),
      description: 'Rotate the rhythm right. Static patterns rotate padded eight-slot repeats before trimming; Euclid rotates the Hits-length grid.' },
    { key: 'patternFlip', label: 'Pattern flip', kind: 'boolean', defaultValue: false,
      description: 'Invert notes and rests inside the chord rhythm grid. Fill still determines which chords play.' },
    { key: 'customPattern', label: 'Custom grid', kind: 'pattern', defaultValue: Object.freeze(Array(32).fill(1)),
      description: 'Click or drag to paint sounded slots and rests; Enter or Space toggles a slot. Edits follow Shift and Pattern flip. Hidden cells are retained when Hits changes.' },
    { key: 'useCustomVelocities', label: 'Custom velocities', kind: 'boolean', defaultValue: false,
      description: 'Use the velocity grid instead of random Min/Max velocity. Disabling retains your grid.' },
    { key: 'customVelocities', label: 'Velocity grid', kind: 'velocities', defaultValue: DEFAULT_CUSTOM_VELOCITIES,
      description: 'Velocity per displayed subdivision (0–127). Zero silences a hit. Values stay at their subdivision when rhythm Shift/Flip changes, and hidden cells survive Hits changes.' }
];

const span = number('chordSpan', 'Span', 1, 4, 1, ' chords');
const pause = number('pauseChance', 'Pause', 0, 100, 0);
const split = number('exceptionChance', 'Split', 0, 100, 0);
const swing = number('swingPercent', 'Swing', 0, 100, 50);
const voices: readonly PartControl[] = [number('chordNotesStretch', 'Voices', 2, 6, 3, ''),
  { key: 'stretchEnabled', label: 'Use voices', kind: 'boolean', defaultValue: false }];
const common: readonly PartControl[] = [
  { key: 'generationEnabled', label: 'Generate notes', kind: 'boolean', defaultValue: true,
    description: 'Enable generated notes for this part. Saved manual phrases remain explicit replacements; mixer mute and section presence are independent.' },
  { key: 'patternSeed', label: 'Seed', kind: 'integer', minimum: -2147483648, maximum: 2147483647, defaultValue: 0,
    description: 'Java int part seed. Zero follows the project/track seed; this changes notes without rerolling settings.' },
  number('offset', 'Offset', -1000, 1000, 0, ' /1000 beat'),
  number('feedbackCount', 'Delays', 0, 5, 0, ''),
  number('feedbackDuration', 'FB duration', -2000, 2000, 750, ' /1000 beat'),
  number('feedbackVol', 'FB velocity', 10, 150, 65),
];

/** Only expose settings with a working musical consumer in this buildout slice. */
export const PART_CONTROLS: Readonly<Record<ArrangedPart, readonly PartControl[]>> = {
  melody: [...fill, number('speed', 'Speed', -100, 100, 50, ''),
    { key: 'fillPauses', label: 'Fill pauses', kind: 'boolean', defaultValue: false,
      description: 'Extend preceding notes through generated pauses. Fill determines which pauses add note duration.' },
    { key: 'chordNoteChoices', label: 'Note targets', kind: 'integers', minimum: -14, maximum: 14, defaultValue: [0, 2, 2, 4],
      description: 'Comma-separated diatonic offsets from the tonic (0) in the fourth octave. The list repeats across progression chords.' },
    { key: 'melodyPatternOffsets', label: 'Block structure', kind: 'integers', minimum: -16, maximum: 16, defaultValue: [1, 2, 1, 3],
      description: 'Comma-separated block identities. Repeated numbers reuse a pattern; negative numbers invert it. This is separate from a rhythm grid.' },
    number('maxBlockChange', 'Max block change', 0, 7, 7, ''), number('blockJump', 'Block jump', 0, 4, 1, ''),
    { key: 'patternFlexible', label: 'Flex', kind: 'boolean', defaultValue: true,
      description: 'Adapt the last block of a reused pattern toward its new target.' },
    number('pauseChance', 'Pause', 0, 100, 0), number('swingPercent', 'Swing', 0, 100, 50),
    number('accents', 'Accent', 0, 100, 100),
    { ...number('transpose', 'Transpose', -36, 36, 0, ' st'), allowedValues: MELODY_TRANSPOSES },
    number('velocityMin', 'Min velocity', 1, 127, 80, ''), number('velocityMax', 'Max velocity', 1, 127, 105, ''),
    number('noteLengthMultiplier', 'Note length', 25, 200, 100),
    ...common],
  bass: [...fill, ...rhythmControls.map(control => control.key === 'rhythm' ? { ...control, options: BASS_RHYTHMS } : control), span,
    number('noteVariation', 'Note variance', 0, 100), { key: 'octaveInterval', label: 'Octave interval', kind: 'boolean' },
    { ...choice('patternJoinMode', 'Pattern join', ['NOJOIN', 'EXPAND', 'JOIN']), defaultValue: 'NOJOIN', advanced: true,
      description: 'Grid rhythms only. NOJOIN keeps subdivisions; EXPAND sustains through rests; JOIN sustains through consecutive hits and suppresses their retriggers. Java also looks into the next chord within a Span group, before its flip/fill. Omission retains NOJOIN in this translation.' },
    ...pitched, number('noteLengthMultiplier', 'Note length', 25, 200, 100), ...common],
  chords: [...fill, ...rhythmControls, span, pause, swing, ...voices,
    choice('voicing', 'Voicing', ['close', 'open']), number('noteLengthPercent', 'Note length', 25, 200), ...pitched, ...common],
  arpeggio: [...fill, ...rhythmControls.map(control => control.key === 'rhythm' ? { ...control, defaultValue: 'full' } : control), span,
    number('patternRepeat', 'Repeat', 1, 4, 1, ''), pause, split, swing, ...voices, choice('pattern', 'Pitch direction', ['up', 'down', 'up-down', 'random']),
    choice('octaves', 'Octaves', [1, 2]),
    ...pitched, number('noteLengthMultiplier', 'Note length', 25, 200, 100), ...common],
  drums: [...fill, ...rhythmControls.map(control => control.key === 'hitsPerPattern' ? { ...control, defaultValue: 4 } : control),
    number('velocityMin', 'Min velocity', 1, 127, 69, ''), number('velocityMax', 'Max velocity', 1, 127, 89, ''),
    span, pause, split, swing, number('noteLengthMultiplier', 'Note length', 25, 200, 100), ...common],
};

export function settingsValues(settings: PartSettingsPatch, role: ArrangedPart): Readonly<Record<string, PartSettingValue>> {
  return Object.fromEntries(PART_CONTROLS[role].map((control) => [control.key,
    (settings as Record<string, PartSettingValue>)[control.key]
      ?? (role === 'arpeggio' && control.key === 'hitsPerPattern' && (settings as { rate?: string }).rate === 'sixteenth' ? 16 : control.defaultValue!)]));
}

/** Arrays are complete musical values, compared by content rather than reference. */
export function partValuesEqual(left: unknown, right: unknown): boolean {
  return Array.isArray(left) && Array.isArray(right)
    ? left.length === right.length && left.every((value, index) => value === right[index]) : left === right;
}

/** Patch equality includes explicit inheritance boundaries and ignores object property order. */
export function partPatchesEqual(left: PartSettingsPatch | undefined, right: PartSettingsPatch | undefined): boolean {
  const leftValues = left as Record<string, unknown> | undefined;
  const rightValues = right as Record<string, unknown> | undefined;
  return Object.keys(leftValues ?? {}).length === Object.keys(rightValues ?? {}).length
    && Object.entries(leftValues ?? {}).every(([key, value]) => partValuesEqual(value, rightValues?.[key]));
}

/** Array-valued settings belong to their copied track/snapshot, including melody target lists. */
export function copyPartSettings<T extends PartSettingsPatch>(settings: T): T {
  return Object.fromEntries(Object.entries(settings).map(([key, value]) => [key,
    Array.isArray(value) ? [...value] : value])) as T;
}

export function decodePartPatch(role: ArrangedPart, value: unknown, enforceSteps = false): PartSettingsPatch | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const result: Record<string, PartSettingValue> = {};
  for (const [key, field] of Object.entries(value)) {
    // Existing scoped rate values remain readable. Hits replaces this coarse UI control.
    if (role === 'arpeggio' && key === 'rate') {
      if (field !== 'eighth' && field !== 'sixteenth') return undefined;
      result[key] = field;
      continue;
    }
    const control = PART_CONTROLS[role].find((candidate) => candidate.key === key);
    if (!control) return undefined;
    if ((control.kind === 'number' || control.kind === 'integer') && (!Number.isInteger(field) || (field as number) < control.minimum! || (field as number) > control.maximum!)) return undefined;
    // Saved values remain readable; new edits follow the control's allowed steps.
    if (enforceSteps && control.step && ((field as number) - control.minimum!) % control.step !== 0) return undefined;
    if (enforceSteps && control.allowedValues && !control.allowedValues.includes(field as number)) return undefined;
    if (control.kind === 'choice' && !control.options!.includes(field as string | number)) return undefined;
    if (control.kind === 'boolean' && typeof field !== 'boolean') return undefined;
    if (control.kind === 'pattern' && (!Array.isArray(field) || field.length !== 32
      || Array.from(field).some((slot) => slot !== 0 && slot !== 1))) return undefined;
    if (control.kind === 'velocities' && !isVelocityPattern(field)) return undefined;
    if (control.kind === 'integers' && (!Array.isArray(field) || field.length < 1 || field.length > 32
      || Array.from(field).some((slot) => !Number.isInteger(slot) || slot < control.minimum! || slot > control.maximum!))) return undefined;
    result[key] = Array.isArray(field) ? [...field] : field as PartSettingValue;
  }
  return result as PartSettingsPatch;
}

export function validVelocityRange(settings: PartSettingsPatch, role: ArrangedPart): boolean {
  const values = settingsValues(settings, role);
  return (values['velocityMin'] as number) <= (values['velocityMax'] as number);
}

export function velocityBounds(settings: CommonPartSettings, defaults: readonly [number, number] = [69, 89]): readonly [number, number] {
  const minimum = settings.velocityMin ?? defaults[0];
  const maximum = settings.velocityMax ?? defaults[1];
  if (!Number.isInteger(minimum) || !Number.isInteger(maximum) || minimum < 1 || maximum > 127 || minimum > maximum) {
    throw new RangeError('Velocity bounds are outside the supported range.');
  }
  return [minimum, maximum];
}

/** Keep omitted common fields omitted on import, rather than creating implicit overrides. */
export function decodeCommonPartSettings(role: ArrangedPart, value: unknown): CommonPartSettings | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const decoded = decodePartPatch(role, value);
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
