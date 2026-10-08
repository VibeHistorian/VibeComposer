import type { CommonPartSettings } from '../project/project.model';

export const CHORD_SPAN_FILLS = ['ALL', 'ODD', 'EVEN', 'F1', 'F2', 'F3', 'F4', 'F12', 'F23', 'F34', 'HALF1', 'HALF2'] as const;
export type ChordSpanFill = typeof CHORD_SPAN_FILLS[number];

const PATTERNS: Readonly<Record<Exclude<ChordSpanFill, 'HALF1' | 'HALF2'>, readonly number[]>> = {
  ALL: [1, 1, 1, 1, 1, 1, 1, 1], ODD: [0, 1, 0, 1, 0, 1, 0, 1], EVEN: [1, 0, 1, 0, 1, 0, 1, 0],
  F1: [1, 0, 0, 0, 1, 0, 0, 0], F2: [0, 1, 0, 0, 0, 1, 0, 0], F3: [0, 0, 1, 0, 0, 0, 1, 0],
  F4: [0, 0, 0, 1, 0, 0, 0, 1], F12: [1, 1, 0, 0, 1, 1, 0, 0], F23: [0, 1, 1, 0, 0, 1, 1, 0],
  F34: [0, 0, 1, 1, 0, 0, 1, 1],
};

/** Port of ChordSpanFill.getPatternByLength(length, flipped). Slots are zero-indexed chords. */
export function chordSpanFillMask(length: number, fill: ChordSpanFill = 'ALL', flipped = false): number[] {
  if (!Number.isInteger(length) || length < 0 || length > 32 || !CHORD_SPAN_FILLS.includes(fill)
      || typeof flipped !== 'boolean') throw new RangeError('Chord fill settings are outside the supported range.');
  const halfway = Math.floor(length / 2);
  return Array.from({ length }, (_, index) => {
    const value = fill === 'HALF1' ? Number(index < halfway)
      : fill === 'HALF2' ? Number(index >= halfway) : PATTERNS[fill][index % 8];
    return flipped ? 1 - value : value;
  });
}

export function partFillMask(length: number, settings: CommonPartSettings): number[] {
  return chordSpanFillMask(length, settings.chordSpanFill, settings.fillFlip);
}
