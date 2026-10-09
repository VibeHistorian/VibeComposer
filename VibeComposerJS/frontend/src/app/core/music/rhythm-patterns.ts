/** Shared stored rhythm identities and Java static masks. UI, validation and generators use this catalogue. */
export const STATIC_RHYTHM_DEFINITIONS = {
  full: { javaName: 'FULL', mask: [1, 1, 1, 1, 1, 1, 1, 1] },
  half: { javaName: 'ALT', mask: [1, 0, 1, 0, 1, 0, 1, 0] },
  tresillo: { javaName: 'TRESILLO', mask: [1, 0, 0, 1, 0, 0, 1, 0] },
  sparse: { javaName: 'ONEPER4', mask: [1, 0, 0, 0, 1, 0, 0, 0] },
  single: { javaName: 'SINGLE', mask: [1, 0, 0, 0, 0, 0, 0, 0] },
  'one-six': { javaName: 'ONESIX', mask: [1, 0, 0, 0, 0, 1, 0, 0] },
} as const;

export type StaticRhythm = keyof typeof STATIC_RHYTHM_DEFINITIONS;
export type StaticRhythmPattern = typeof STATIC_RHYTHM_DEFINITIONS[StaticRhythm]['javaName'];
export const STATIC_RHYTHMS = Object.keys(STATIC_RHYTHM_DEFINITIONS) as StaticRhythm[];
export const STATIC_RHYTHM_PATTERNS = Object.values(STATIC_RHYTHM_DEFINITIONS).map((definition) => definition.javaName);
export const RHYTHM_PATTERNS = [...STATIC_RHYTHMS, 'euclid', 'custom'] as const;
export type RhythmPattern = typeof RHYTHM_PATTERNS[number];

/** The current bass consumer supports static subsets plus its own generated alternating durations. */
export const BASS_RHYTHMS = ['alternating', ...RHYTHM_PATTERNS.filter((pattern) =>
  pattern !== 'single' && pattern !== 'one-six' && pattern !== 'euclid' && pattern !== 'custom')] as const;
export type BassRhythm = typeof BASS_RHYTHMS[number];
