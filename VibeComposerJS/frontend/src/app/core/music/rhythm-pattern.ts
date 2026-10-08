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
