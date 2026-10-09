import type { RhythmSettings } from '../project/project.model';
import { STATIC_RHYTHM_DEFINITIONS, STATIC_RHYTHM_PATTERNS, type StaticRhythmPattern } from './rhythm-patterns';
export { STATIC_RHYTHM_PATTERNS, type StaticRhythmPattern } from './rhythm-patterns';

/** Java RhythmPattern.getPatternByLength: rotate the padded eight-slot repeats before truncation. */
export function rhythmPatternMask(pattern: StaticRhythmPattern, hits: number, shift = 0, flipped = false): number[] {
  if (!STATIC_RHYTHM_PATTERNS.includes(pattern) || !Number.isInteger(hits) || hits < 1 || hits > 32
    || !Number.isInteger(shift) || shift < 0 || shift > 8 || typeof flipped !== 'boolean') {
    throw new RangeError('Rhythm pattern settings are outside the supported range.');
  }
  const definition = Object.values(STATIC_RHYTHM_DEFINITIONS).find((candidate) => candidate.javaName === pattern)!;
  const length = Math.ceil(hits / 8) * 8;
  return Array.from({ length: hits }, (_, index) => {
    const value = definition.mask[((index - shift + length) % length) % 8];
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

/** InstPart.getFinalPatternCopy rotates the complete custom list, then the consumer takes Hits. */
export function customPatternMask(pattern: readonly number[], hits: number, shift = 0, flipped = false): number[] {
  if (!Array.isArray(pattern) || pattern.length !== 32 || Array.from(pattern).some((slot) => slot !== 0 && slot !== 1)
    || !Number.isInteger(hits) || hits < 1 || hits > 32 || !Number.isInteger(shift) || shift < 0 || shift > 8
    || typeof flipped !== 'boolean') throw new RangeError('Custom rhythm settings are outside the supported range.');
  return Array.from({ length: hits }, (_, index) => {
    const value = pattern[(index - shift + 32) % 32];
    return flipped ? 1 - value : value;
  });
}

/** Shared by generation and the contextual preview; Pulses is stored independently of Hits. */
export function partRhythmMask(settings: Pick<RhythmSettings,
  'rhythm' | 'hitsPerPattern' | 'patternShift' | 'patternFlip' | 'euclideanPulses' | 'customPattern'>): number[] {
  const hits = settings.hitsPerPattern ?? 8;
  const shift = settings.patternShift ?? 0;
  const flipped = settings.patternFlip ?? false;
  const pulses = settings.euclideanPulses ?? 4;
  if (!Number.isInteger(pulses) || pulses < 0 || pulses > 32) {
    throw new RangeError('Euclidean pulses are outside the supported range.');
  }
  if (settings.rhythm === 'custom') return customPatternMask(settings.customPattern ?? Array(32).fill(1), hits, shift, flipped);
  return settings.rhythm === 'euclid'
    ? euclideanPatternMask(hits, Math.min(pulses, hits), shift, flipped)
    : rhythmPatternMask(STATIC_RHYTHM_DEFINITIONS[settings.rhythm]?.javaName, hits, shift, flipped);
}

/** Retained for existing chord callers. */
export const chordRhythmMask = partRhythmMask;
