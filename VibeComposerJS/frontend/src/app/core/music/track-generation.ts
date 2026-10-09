import type { ArpeggioGenerationPolicy, ArpeggioSettings, ArrangedPart, CompositionTrack, BassSettings,
  ChordSettings, DrumSettings, MelodySettings, TrackGenerationPolicies, TrackGenerationPolicy } from '../project/project.model';
import { ARRANGED_PARTS } from '../project/project.model';
import { JavaRandom } from './java-random';
import { CHORD_SPAN_FILLS } from './chord-span-fill';
import { partPatchesEqual } from './part-settings';
import { STATIC_RHYTHM_DEFINITIONS, type StaticRhythm, type RhythmPattern } from './rhythm-patterns';

export const DEFAULT_ARPEGGIO_POLICY: ArpeggioGenerationPolicy = Object.freeze({
  fixedHits: 4, randomHits: true, sameHits: true, powerOfTwo: true,
  fills: true, patterns: true, transpose: true, randomSpan: true,
  maxSplit: 20, lengthMin: 75, lengthMax: 100, maxRepeat: 2, shiftChance: 50,
  voicesMode: 'AT_MOST', voices: 4, voicesChance: 50, velocityMin: 65, velocityMax: 89,
});

export interface PolicyControl {
  readonly key: string;
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

export const DEFAULT_TRACK_POLICIES: TrackGenerationPolicies = Object.freeze({
  arpeggio: DEFAULT_ARPEGGIO_POLICY,
  bass: Object.freeze({}),
  chords: Object.freeze({ fills: true, patterns: true, transpose: true, varyLength: true, delay: false,
    shiftChance: 60, voicesMode: 'AT_MOST', voices: 5, voicesChance: 50, velocityMin: 65, velocityMax: 89 }),
  drums: Object.freeze({ patterns: true, fills: true, maxSwing: 20, randomOffset: false, hitsMultiplier: '1', shiftChance: 50 }),
  melody: Object.freeze({ rerollSeeds: true, sameSeed: false, patterns: true }),
});

export const TRACK_POLICY_CONTROLS: Readonly<Record<ArrangedPart, readonly PolicyControl[]>> = {
  arpeggio: ARPEGGIO_POLICY_CONTROLS,
  bass: [],
  chords: [
    { key: 'varyLength', label: 'Vary length' }, { key: 'fills', label: 'Fills' }, { key: 'patterns', label: 'Patterns' },
    { key: 'transpose', label: 'Transpose' }, { key: 'delay', label: 'Delay', advanced: true },
    ...ARPEGGIO_POLICY_CONTROLS.filter(control => ['shiftChance', 'voicesMode', 'voices', 'voicesChance', 'velocityMin', 'velocityMax'].includes(control.key)),
  ],
  drums: [
    { key: 'patterns', label: 'Patterns' }, { key: 'fills', label: 'Fills' },
    { key: 'maxSwing', label: 'Max swing ±', minimum: 0, maximum: 50 },
    { key: 'randomOffset', label: 'Random offset' },
    { key: 'hitsMultiplier', label: 'On reroll hits ×', options: ['1', '1/2', '3/4', '3/2', '2'] },
    { key: 'shiftChance', label: 'Shift chance %', minimum: 0, maximum: 100, advanced: true },
  ],
  melody: [{ key: 'rerollSeeds', label: 'Reroll seeds' }, { key: 'sameSeed', label: 'Same seed' },
    { key: 'patterns', label: 'Block patterns' }],
};

export function decodeTrackGenerationPolicy<R extends ArrangedPart>(role: R, value: unknown): TrackGenerationPolicies[R] | undefined {
  if (!ARRANGED_PARTS.includes(role) || !value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const fields = value as Record<string, unknown>, controls = TRACK_POLICY_CONTROLS[role];
  if (Object.keys(fields).length !== controls.length) return undefined;
  for (const control of controls) {
    const field = fields[control.key];
    if (control.options ? !control.options.includes(field as string)
      : control.minimum !== undefined ? !Number.isInteger(field) || (field as number) < control.minimum || (field as number) > control.maximum!
        : typeof field !== 'boolean') return undefined;
  }
  if (typeof fields['lengthMin'] === 'number' && fields['lengthMin'] > (fields['lengthMax'] as number)
    || typeof fields['velocityMin'] === 'number' && fields['velocityMin'] > (fields['velocityMax'] as number)) return undefined;
  return { ...fields } as unknown as TrackGenerationPolicies[R];
}

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
    || Object.entries(policies).some(([key, policy]) => !ARRANGED_PARTS.includes(key as ArrangedPart)
      || !decodeTrackGenerationPolicy(key as ArrangedPart, policy)))) return false;
  if (counts !== undefined && (!counts || typeof counts !== 'object' || Array.isArray(counts)
    || Object.entries(counts).some(([key, count]) => !ARRANGED_PARTS.includes(key as ArrangedPart) || !Number.isInteger(count)
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

const VIABLE_RHYTHMS = ['full', 'half', 'sparse', 'tresillo', 'single', 'one-six', 'full'] as const;
const SECONDARY_FILLS = ['ALL', 'ALL', 'EVEN', 'ODD', 'HALF1', 'HALF2'] as const;
const MELODY_PATTERNS: readonly (readonly number[])[] = [
  [1,2,1,3], [1,2,1,3,1,2,1,4], [1,2,1,3,2,2,3,4], [1,2,3,4,1,2,1,3],
  [1,1,2,3], [1,2,3,1], [1,2,3,2], [1,2,3,3], [1,2,2,1], [1,2,2,3],
  [1,1,2,1], [1,1,1,2], [1,1,-1,2], [1,2,-2,-1], [1,2,-1,2],
  [1,-1,2,3], [1,-1,2,2], [1,-1,-1,1], [1,-1,1,2],
];

/** MelodyPatternUtils with the supported path's fixed alternating-pattern chance of zero. */
export function chooseMelodyPattern(seed: number): number[] {
  const random = new JavaRandom(seed);
  random.nextInt(100);
  return [...MELODY_PATTERNS[random.nextInt(MELODY_PATTERNS.length)]];
}

function rerollBass(random: JavaRandom, seed: bigint, source: BassSettings, order: number): BassSettings {
  if (order === 1) {
    random.nextInt(10); // Bass pause is GUI pattern baking, outside the current phrase consumer.
    const maximum = 59 + random.nextInt(30), minimum = 40 + random.nextInt(25);
    return { ...source, fillFlip: false, transpose: 0,
    useCustomVelocities: false,
    velocityMax: Math.max(maximum, minimum), velocityMin: minimum, noteLengthMultiplier: 80 + random.nextInt(25) };
  }
  const fill = SECONDARY_FILLS[random.nextInt(6)];
  random.nextInt(40);
  const next = { ...source, fillFlip: false, chordSpanFill: fill, patternSeed: Number(BigInt.asIntN(32, seed)),
    velocityMax: 49 + random.nextInt(20), velocityMin: 30 + random.nextInt(20),
    transpose: order % 2 === 0 ? 0 : 12, noteLengthMultiplier: 60 + random.nextInt(40) };
  const rhythm = random.nextInt(100) < 50 ? VIABLE_RHYTHMS[random.nextInt(7)] : 'single';
  let hits = 4;
  while (random.nextBoolean() && hits < 16) hits *= 2;
  if (hits / (source.chordSpan ?? 1) >= 8) hits /= 2;
  return { ...next, rhythm, hitsPerPattern: hits * 2, useCustomVelocities: false };
}

function rerollChords(random: JavaRandom, policy: TrackGenerationPolicies['chords'], source: ChordSettings, program: number): ChordSettings {
  // The translated model stores programs, not Java's instrument pool. Use its pluck catalogue,
  // preferring CHORD for the overlapping acoustic bass; other GM pads use LONG_PAD behavior.
  const pluck = [1,3,5,8,10,11,12,13,15,25,27,33,34,35,36,45,46,88].includes(program);
  const pad = program >= 89 && program <= 95;
  const next = { ...source, velocityMin: policy.velocityMin, velocityMax: policy.velocityMax };
  if (policy.transpose) Object.assign(next, { transpose: (random.nextInt(3) - 1) * 12 });
  if (policy.delay) Object.assign(next, { offset: [0, 62, 125, 250, 333][random.nextInt(5)] });
  if (policy.fills) Object.assign(next, { chordSpanFill: pad ? 'ALL' : weightedChordFill(random.nextInt(100)), fillFlip: false });
  const useVoices = policy.voicesMode !== 'NONE' && random.nextInt(100) < policy.voicesChance;
  Object.assign(next, { stretchEnabled: useVoices });
  if (useVoices) Object.assign(next, { chordNotesStretch: policy.voicesMode === 'FIXED' ? policy.voices : random.nextInt(policy.voices - 2) + 3 });
  if (policy.varyLength) Object.assign(next, { noteLengthPercent: random.nextInt(26) + (pluck ? 50 : 85) });
  if (policy.patterns) {
    const rhythm = !pad && random.nextInt(100) < (pluck ? 25 : 10) ? VIABLE_RHYTHMS[random.nextInt(7)] : 'single';
    let maxShift = Math.min(rhythm === 'full' ? 0 : rhythm === 'half' ? 1 : rhythm === 'sparse' ? 3 : 7, (source.hitsPerPattern ?? 8) - 1);
    let shift = 0;
    if (random.nextInt(100) < policy.shiftChance) {
      if (random.nextInt(100) >= policy.shiftChance) maxShift = Math.floor(maxShift / 2);
      if (maxShift > 0) shift = random.nextInt(maxShift) + 1;
    }
    const frequency = STATIC_RHYTHM_DEFINITIONS[rhythm].mask.reduce<number>((sum, value) => sum + value, 0) / 8;
    Object.assign(next, { rhythm, patternShift: shift, patternFlip: false, useCustomVelocities: false,
      pauseChance: random.nextInt(Math.floor(50 * frequency) + 1) });
  }
  return next;
}

// Production DrumDefaults/DrumSettings blueprint tuples: rhythm, hits, span, initial shift.
type DrumBlueprint = readonly [RhythmPattern, number, number, number];
const DRUM_BLUEPRINTS: readonly (readonly DrumBlueprint[])[] = [
  [['sparse',8,1,0], ['one-six',8,1,0], ['tresillo',8,1,0], ['half',8,2,0], ['half',8,1,0], ['custom',16,1,0]],
  [['sparse',8,1,2], ['one-six',8,1,2], ['half',8,2,1], ['single',8,1,6], ['sparse',8,1,3]],
  [['sparse',16,1,2], ['one-six',16,1,2], ['tresillo',8,1,2], ['half',8,2,1], ['full',8,2,0]],
  [['sparse',8,1,2], ['single',8,1,6]],
  [['sparse',16,1,3], ['one-six',16,1,3], ['tresillo',8,1,5], ['half',8,2,1], ['full',8,2,0]],
];
const COOL_DRUM_PATTERNS = [
  [1,0,1,0,0,1,0,1,0,0,0,0,0,0,0,0], [1,0,1,1,0,0,0,1,0,0,0,1,0,0,0,0],
  [1,0,0,1,0,0,0,1,0,1,0,1,0,0,0,0], [1,0,0,0,0,0,0,1,0,0,1,0,0,0,1,0],
];

function rerollDrums(random: JavaRandom, policy: TrackGenerationPolicies['drums'], source: DrumSettings, swing: number, offset: number): DrumSettings {
  const family = source.pitch <= 37 ? 0 : source.pitch <= 41 ? 1 : source.pitch <= 45 ? 2 : source.pitch <= 53 ? 3 : 4;
  const flexible = family === 2 || family === 4;
  const next = { ...source, swingPercent: flexible ? swing : 50,
    ...(policy.randomOffset && flexible ? { offset } : {}) };
  if (policy.patterns) {
    const candidates = DRUM_BLUEPRINTS[family], [rhythm, hits, span, shift] = candidates[random.nextInt(candidates.length)];
    const multipliers = { '1': 1, '1/2': .5, '3/4': .75, '3/2': 1.5, '2': 2 };
    Object.assign(next, { rhythm, hitsPerPattern: Math.max(1, Math.min(32, Math.floor(hits * multipliers[policy.hitsMultiplier]))),
      chordSpan: span, patternShift: shift, patternFlip: false, useCustomVelocities: false,
      velocityMin: source.pitch === 37 ? 50 : flexible ? 40 : family === 3 ? 50 : 65,
      velocityMax: source.pitch === 37 ? 74 : flexible ? 74 : family === 3 ? 84 : 99,
      pauseChance: rhythm === 'full' ? random.nextInt(flexible ? 21 : 1) : 0,
      exceptionChance: random.nextInt(family === 0 ? 3 : flexible ? 11 : 1) });
    if (rhythm === 'custom') {
      const pattern = COOL_DRUM_PATTERNS[random.nextInt(4)];
      Object.assign(next, { customPattern: [...pattern, ...pattern] });
    }
    if (family >= 2 && random.nextInt(100) < policy.shiftChance) Object.assign(next, { patternShift: random.nextInt(shift + 1) });
  }
  if (policy.fills) Object.assign(next, { chordSpanFill: flexible ? weightedChordFill(random.nextInt(100)) : 'ALL', fillFlip: false });
  return next;
}

function rerollMelody(random: JavaRandom, seed: bigint, policy: TrackGenerationPolicies['melody'], source: MelodySettings, order: number, sharedSeed?: number): MelodySettings {
  const speed = random.nextInt(25), maxBlockChange = 3 + random.nextInt(5);
  // Consume creation draws for the three extended parameters whose musical consumers remain fixed in P1b.
  random.nextInt(15); random.nextInt(2); random.nextInt(50);
  const next = { ...source, speed, maxBlockChange, chordSpanFill: SECONDARY_FILLS[random.nextInt(6)], fillFlip: false };
  if (order > 1) Object.assign(next, { fillPauses: true, pauseChance: 50 + random.nextInt(40),
    velocityMax: 64 + random.nextInt(20), velocityMin: 40 + random.nextInt(20), transpose: order % 2 === 0 ? 0 : -12,
    noteLengthMultiplier: 70 + random.nextInt(40) });
  else Object.assign(next, { fillPauses: random.nextBoolean(), pauseChance: random.nextInt(35), transpose: 12,
    velocityMax: 79 + random.nextInt(30), velocityMin: 50 + random.nextInt(25), noteLengthMultiplier: 100 + random.nextInt(25) });
  if (policy.rerollSeeds) Object.assign(next, { patternSeed: sharedSeed ?? random.nextInt() });
  if (policy.patterns) {
    const patternSeed = next.patternSeed || Number(BigInt.asIntN(32, seed));
    Object.assign(next, { melodyPatternOffsets: chooseMelodyPattern((patternSeed + order) | 0) });
  }
  return next;
}

/** Stable role order is supplied separately so locked tracks do not renumber the primary/secondary branches. */
export function rerollRoleSettings(seed: bigint, role: ArrangedPart, policy: TrackGenerationPolicy,
  sources: readonly CompositionTrack[], orders: readonly number[]): CompositionTrack['generatorSettings'][] {
  if (!decodeTrackGenerationPolicy(role, policy) || sources.some(track => track.role !== role) || sources.length !== orders.length)
    throw new RangeError('Invalid role reroll inputs.');
  if (role === 'arpeggio') return rerollArpeggioSettings(seed, policy as ArpeggioGenerationPolicy, sources.map(track => track.generatorSettings as ArpeggioSettings));
  const random = new JavaRandom(role === 'bass' || role === 'melody' ? BigInt.asIntN(32, seed) : seed);
  const drumPolicy = policy as TrackGenerationPolicies['drums'];
  const offset = role === 'drums' && drumPolicy.randomOffset ? random.nextInt(100) - 50 : 0;
  const swing = role === 'drums' ? 50 + random.nextInt(drumPolicy.maxSwing * 2 + 1) - drumPolicy.maxSwing : 50;
  const melodyPolicy = policy as TrackGenerationPolicies['melody'];
  const sharedSeed = role === 'melody' && melodyPolicy.rerollSeeds && melodyPolicy.sameSeed ? random.nextInt() : undefined;
  return sources.map((track, index) => {
    const source = track.generatorSettings;
    const next = role === 'bass' ? rerollBass(random, seed, source as BassSettings, orders[index])
      : role === 'chords' ? rerollChords(random, policy as TrackGenerationPolicies['chords'], source as ChordSettings, track.mix.program)
      : role === 'drums' ? rerollDrums(random, drumPolicy, source as DrumSettings, swing, offset)
      : rerollMelody(random, seed, melodyPolicy, source as MelodySettings, orders[index], sharedSeed);
    return partPatchesEqual(source, next) ? source : next;
  });
}
