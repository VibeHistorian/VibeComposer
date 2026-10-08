import type { ChordSettings } from '../project/project.model';

export const STATIC_RHYTHM_PATTERNS = ['FULL', 'ALT', 'ONEPER4', 'TRESILLO', 'SINGLE', 'ONESIX'] as const;
export type StaticRhythmPattern = typeof STATIC_RHYTHM_PATTERNS[number];

const PATTERNS: Readonly<Record<StaticRhythmPattern, readonly number[]>> = {
  FULL: [1, 1, 1, 1, 1, 1, 1, 1], ALT: [1, 0, 1, 0, 1, 0, 1, 0],
  ONEPER4: [1, 0, 0, 0, 1, 0, 0, 0], TRESILLO: [1, 0, 0, 1, 0, 0, 1, 0],
  SINGLE: [1, 0, 0, 0, 0, 0, 0, 0], ONESIX: [1, 0, 0, 0, 0, 1, 0, 0],
};

/** Java RhythmPattern.getPatternByLength: rotate the padded eight-slot repeats before truncation. */
export function rhythmPatternMask(pattern: StaticRhythmPattern, hits: number, shift = 0, flipped = false): number[] {
  if (!STATIC_RHYTHM_PATTERNS.includes(pattern) || !Number.isInteger(hits) || hits < 1 || hits > 32
    || !Number.isInteger(shift) || shift < 0 || shift > 8 || typeof flipped !== 'boolean') {
    throw new RangeError('Rhythm pattern settings are outside the supported range.');
  }
  const length = Math.ceil(hits / 8) * 8;
  return Array.from({ length: hits }, (_, index) => {
    const value = PATTERNS[pattern][((index - shift + length) % length) % 8];
    return flipped ? 1 - value : value;
  });
}

/** Port of RhythmPattern.makeEuclideanPattern, including Java's grouping and rotation order. */
export function euclideanPatternMask(hits: number, pulses: number, shift = 0, flipped = false): number[] {
  if (!Number.isInteger(hits) || hits < 1 || hits > 32 || !Number.isInteger(pulses) || pulses < 0 || pulses > hits
    || !Number.isInteger(shift) || shift < 0 || shift > 8 || typeof flipped !== 'boolean') {
    throw new RangeError('Euclidean pattern settings are outside the supported range.');
  }
  let first = Array.from({ length: pulses }, () => [1]);
  let second = Array.from({ length: hits - pulses }, () => [0]);
  let minimum = Math.min(first.length, second.length);
  let threshold = 0;
  while (minimum > threshold) {
    threshold = 1;
    for (let index = 0; index < minimum; index++) first[index].push(...second[index]);
    if (minimum === first.length) {
      second = second.slice(Math.min(second.length - 1, minimum));
    } else {
      second = first.slice(minimum);
      first = first.slice(0, minimum);
    }
    minimum = Math.min(first.length, second.length);
  }
  const grouped = [...first.flat(), ...second.flat()];
  return Array.from({ length: hits }, (_, index) => {
    const value = grouped[(index - shift % grouped.length + grouped.length) % grouped.length];
    return flipped ? 1 - value : value;
  });
}

const CHORD_RHYTHMS: Readonly<Record<Exclude<ChordSettings['rhythm'], 'euclid'>, StaticRhythmPattern>> = {
  full: 'FULL', half: 'ALT', tresillo: 'TRESILLO', sparse: 'ONEPER4', single: 'SINGLE', 'one-six': 'ONESIX',
};

/** Shared by generation and the contextual preview; Pulses is stored independently of Hits. */
export function chordRhythmMask(settings: Pick<ChordSettings,
  'rhythm' | 'hitsPerPattern' | 'patternShift' | 'patternFlip' | 'euclideanPulses'>): number[] {
  const hits = settings.hitsPerPattern ?? 8;
  const shift = settings.patternShift ?? 0;
  const flipped = settings.patternFlip ?? false;
  const pulses = settings.euclideanPulses ?? 4;
  if (!Number.isInteger(pulses) || pulses < 0 || pulses > 32) {
    throw new RangeError('Euclidean pulses are outside the supported range.');
  }
  return settings.rhythm === 'euclid'
    ? euclideanPatternMask(hits, Math.min(pulses, hits), shift, flipped)
    : rhythmPatternMask(CHORD_RHYTHMS[settings.rhythm], hits, shift, flipped);
}
