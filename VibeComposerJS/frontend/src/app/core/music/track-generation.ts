import type { ArpeggioGenerationPolicy, ArpeggioSettings } from '../project/project.model';
import { JavaRandom } from './java-random';
import { CHORD_SPAN_FILLS } from './chord-span-fill';
import { partPatchesEqual } from './part-settings';

export const DEFAULT_ARPEGGIO_POLICY: ArpeggioGenerationPolicy = Object.freeze({
  fixedHits: 4, randomHits: true, sameHits: true, powerOfTwo: true,
  fills: true, patterns: true, transpose: true, randomSpan: true,
  maxSplit: 20, lengthMin: 75, lengthMax: 100, maxRepeat: 2, shiftChance: 50,
  voicesMode: 'AT_MOST', voices: 4, voicesChance: 50, velocityMin: 65, velocityMax: 89,
});

export interface PolicyControl {
  readonly key: keyof ArpeggioGenerationPolicy;
  readonly label: string;
  readonly minimum?: number;
  readonly maximum?: number;
  readonly options?: readonly string[];
  readonly advanced?: boolean;
}
export const ARPEGGIO_POLICY_CONTROLS: readonly PolicyControl[] = [
  { key: 'fixedHits', label: 'Arp hits', minimum: 1, maximum: 32 },
  { key: 'randomHits', label: 'Random hits' }, { key: 'sameHits', label: 'One hit count' },
  { key: 'powerOfTwo', label: 'Limit 2ⁿ' }, { key: 'fills', label: 'Fills' }, { key: 'patterns', label: 'Patterns' },
  { key: 'maxSplit', label: 'Max split %', minimum: 0, maximum: 100 },
  { key: 'lengthMin', label: 'Min length %', minimum: 25, maximum: 200 },
  { key: 'lengthMax', label: 'Max length %', minimum: 25, maximum: 200 },
  { key: 'transpose', label: 'Transpose', advanced: true },
  { key: 'randomSpan', label: 'Random span', advanced: true },
  { key: 'maxRepeat', label: 'Max repeat', minimum: 1, maximum: 4, advanced: true },
  { key: 'shiftChance', label: 'Shift chance %', minimum: 0, maximum: 100, advanced: true },
  { key: 'voicesMode', label: 'Voices mode', options: ['NONE', 'FIXED', 'AT_MOST'], advanced: true },
  { key: 'voices', label: 'Voices limit', minimum: 3, maximum: 6, advanced: true },
  { key: 'voicesChance', label: 'Voices chance %', minimum: 0, maximum: 100, advanced: true },
  { key: 'velocityMin', label: 'Min velocity', minimum: 1, maximum: 127, advanced: true },
  { key: 'velocityMax', label: 'Max velocity', minimum: 1, maximum: 127, advanced: true },
];

export function decodeArpeggioPolicy(value: unknown): ArpeggioGenerationPolicy | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const fields = value as Record<string, unknown>;
  if (Object.keys(fields).length !== ARPEGGIO_POLICY_CONTROLS.length) return undefined;
  for (const control of ARPEGGIO_POLICY_CONTROLS) {
    const field = fields[control.key];
    if (control.options ? !control.options.includes(field as string)
      : control.minimum !== undefined ? !Number.isInteger(field) || (field as number) < control.minimum || (field as number) > control.maximum!
        : typeof field !== 'boolean') return undefined;
  }
  if ((fields['lengthMin'] as number) > (fields['lengthMax'] as number)
    || (fields['velocityMin'] as number) > (fields['velocityMax'] as number)) return undefined;
  return { ...fields } as unknown as ArpeggioGenerationPolicy;
}

export function validTrackGenerationMetadata(policies: unknown, counts: unknown): boolean {
  if (policies !== undefined && (!policies || typeof policies !== 'object' || Array.isArray(policies)
    || Object.keys(policies).some(key => key !== 'arpeggio')
    || ('arpeggio' in policies && !decodeArpeggioPolicy((policies as { arpeggio?: unknown }).arpeggio)))) return false;
  if (counts !== undefined && (!counts || typeof counts !== 'object' || Array.isArray(counts)
    || Object.entries(counts).some(([key, count]) => key !== 'arpeggio' || !Number.isInteger(count)
      || count < 0 || count > 2147483647))) return false;
  return true;
}

/** ArpGUI's weighted candidates, rather than uniformly selecting arbitrary powers of two. */
export function chooseArpeggioHits(random: JavaRandom, policy: ArpeggioGenerationPolicy): number {
  if (!policy.randomHits) return policy.fixedHits;
  if (policy.powerOfTwo) return [2, 4, 4, 8, 8, 8, 8][random.nextInt(7)];
  let hits = random.nextInt(7) + 2; // Java MAXIMUM_PATTERN_LENGTH = 8.
  if (hits === 5) hits = random.nextInt(7) + 2;
  if (hits === 7) hits++;
  return hits;
}

/** Production ChordSpanFill weights omit F23/HALF1 from automatic choices. */
export function weightedChordFill(weight: number) {
  if (!Number.isInteger(weight) || weight < 0 || weight >= 100) throw new RangeError('Fill weight must be 0–99.');
  return CHORD_SPAN_FILLS[[60, 72, 84, 86, 88, 90, 92, 95, 95, 98, 98, 100].findIndex(limit => weight < limit)];
}

/** Deterministic translation of the supported reroll branches; legacy GUI uses unseeded Randoms. */
export function rerollArpeggioSettings(seed: bigint, policy: ArpeggioGenerationPolicy,
  sources: readonly ArpeggioSettings[]): ArpeggioSettings[] {
  if (!decodeArpeggioPolicy(policy)) throw new RangeError('Invalid Arpeggio reroll policy.');
  const random = new JavaRandom(seed);
  const sharedHits = policy.sameHits ? chooseArpeggioHits(random, policy) : undefined;
  return sources.map(source => {
    const hits = sharedHits ?? chooseArpeggioHits(random, policy);
    const span = policy.randomSpan ? random.nextInt(2) + 1 : source.chordSpan ?? 1;
    const repeat = span === 1 ? random.nextInt(policy.maxRepeat) + 1 : 1;
    const fast = repeat * hits / span >= 16;
    const split = random.nextInt(1 + (fast ? Math.floor(policy.maxSplit / 3) : policy.maxSplit));
    const length = random.nextInt(1 + policy.lengthMax - policy.lengthMin) + policy.lengthMin;
    const transpose = policy.transpose ? (random.nextInt(3) - 1) * 12 : source.transpose;
    const useVoices = policy.voicesMode !== 'NONE' && random.nextInt(100) < policy.voicesChance;
    const voiceCount = useVoices ? policy.voicesMode === 'FIXED' ? policy.voices
      : random.nextInt(policy.voices - 2) + 3 : source.chordNotesStretch;
    // Only currently supported static rhythms; MELODY1 awaits the section melody dependency.
    const rhythms = ['full', 'half', 'sparse', 'tresillo', 'single', 'one-six'] as const;
    const rhythm = policy.patterns ? (random.nextInt(100) < 30 ? rhythms[random.nextInt(rhythms.length)] : 'full') : source.rhythm;
    const fill = policy.fills ? weightedChordFill(random.nextInt(100)) : source.chordSpanFill;
    const maxShift = Math.min(rhythm === 'full' ? 0 : rhythm === 'half' ? 1 : rhythm === 'sparse' ? 3 : 7, hits - 1);
    const shift = policy.patterns ? (random.nextInt(100) < policy.shiftChance && maxShift > 0 ? random.nextInt(maxShift) + 1 : 0) : source.patternShift;
    const next: ArpeggioSettings = { ...source, hitsPerPattern: hits, chordSpan: span, patternRepeat: repeat,
      exceptionChance: split, noteLengthMultiplier: length, velocityMin: policy.velocityMin, velocityMax: policy.velocityMax,
      stretchEnabled: useVoices,
      ...(transpose !== undefined ? { transpose } : {}),
      ...(voiceCount !== undefined ? { chordNotesStretch: voiceCount } : {}),
      ...(policy.patterns ? { rhythm, patternShift: shift, patternFlip: false, useCustomVelocities: false } : {}),
      ...(policy.fills ? { chordSpanFill: fill, fillFlip: false } : {}) };
    return partPatchesEqual(source, next) ? source : next;
  });
}
