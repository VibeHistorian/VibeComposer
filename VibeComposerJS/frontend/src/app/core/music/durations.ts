import type { CommonPartSettings } from './part-settings.model';
import { JavaRandom } from './java-random';
import { cumulativeWeights } from './melody-blocks';

/** Ten Java duration lanes. Positive beats sound; negative beats are rests. Empty lanes are unused. */
export type Durations = readonly (readonly number[])[];
export const DEFAULT_DURATIONS: Durations = Object.freeze([
  Object.freeze([1, -0.5, 0.5, 1, -1]), ...Array.from({ length: 9 }, () => Object.freeze([] as number[])),
]);
export const DEFAULT_DURATION_CHANCES: readonly number[] = Object.freeze(Array(10).fill(50));
const EPS = 1e-7;

export function isDurationRow(row: unknown): row is readonly number[] {
  return Array.isArray(row)
    && row.length <= 64 && Array.from(row).every(duration => typeof duration === 'number' && Number.isFinite(duration)
      && Math.abs(duration) >= 1 / 960 - EPS && Math.abs(duration) <= 4 + EPS)
    && row.reduce((sum, duration) => sum + Math.abs(duration), 0) <= 4 + EPS;
}

export function isDurations(value: unknown): value is Durations {
  return Array.isArray(value) && value.length === 10 && Array.from(value).every(isDurationRow);
}

export function joinDurationPauses(row: readonly number[]): number[] {
  const joined: number[] = [];
  for (const duration of row) {
    const last = joined.length - 1;
    if (duration < 0 && joined[last] < 0) joined[last] += duration;
    else joined.push(duration);
  }
  return joined;
}

/** Pad to one chord, then join pauses before Flip so each silent run becomes one note. */
export function paddedDurations(row: readonly number[], flipped = false): number[] {
  const result = [...row], remaining = 4 - row.reduce((sum, duration) => sum + Math.abs(duration), 0);
  if (remaining > EPS) result.push(-remaining);
  const joined = joinDurationPauses(result);
  return flipped ? joinDurationPauses(joined.map(duration => -duration)) : joined;
}

export function durationError(settings: CommonPartSettings): string | null {
  const rows: unknown = settings.customDurations ?? DEFAULT_DURATIONS;
  if (!isDurations(rows)) {
    const lane = Array.isArray(rows) && rows.length === 10
      ? rows.findIndex(row => !isDurationRow(row)) : -1;
    return `${lane >= 0 ? 'Lane ' + (lane + 1) + ': ' : ''}Use up to 64 nonzero durations per lane, totaling at most four beats.`;
  }
  const chances = settings.customDurationChances ?? DEFAULT_DURATION_CHANCES;
  if (chances.length !== 10 || Array.from(chances).some(value => !Number.isInteger(value) || value < 0 || value > 100))
    return 'Each lane chance must be an integer from 0 to 100.';
  const active = rows.flatMap((row, index) => row.length && (!settings.customDurationRandomWeighting || chances[index] > 0) ? [index] : []);
  if (!active.length) return 'Draw at least one rhythm lane with a positive chance when weighted selection is enabled.';
  for (const index of active) if (!paddedDurations(rows[index], settings.patternFlip).some(duration => duration > 0))
    return `Lane ${index + 1} contains only pauses${settings.patternFlip ? ' after Flip' : ''}. Add a note or disable this lane.`;
  return null;
}

/** Java chooses by block identity (or chord index), then uses the same stream to group 3–4 notes. */
export function durationBlocks(settings: CommonPartSettings, seed: number, chord: number, blockOffset = -1): number[][] {
  const error = durationError(settings);
  if (error) throw new RangeError(error);
  const rows = settings.customDurations ?? DEFAULT_DURATIONS;
  const active = rows.flatMap((row, index) => row.length ? [index] : []);
  let index = ((blockOffset >= 0 ? blockOffset : chord) % active.length + active.length) % active.length;
  const random = new JavaRandom((seed + 12 + index) | 0);
  if (settings.customDurationRandomWeighting) {
    const chances = settings.customDurationChances ?? DEFAULT_DURATION_CHANCES;
    const eligible = active.filter(row => chances[row] > 0);
    const weights = cumulativeWeights(eligible.map(row => chances[row]));
    const roll = random.nextInt(100);
    index = active.indexOf(eligible[weights.findIndex(weight => roll < weight)]);
  }
  const durations = paddedDurations(rows[active[index]], settings.patternFlip);
  const blocks: number[][] = [];
  let block: number[] = [], notes = 0;
  for (const duration of durations) {
    block.push(duration);
    if (duration > 0) notes++;
    if (notes === 3 && random.nextBoolean() || notes >= 4) { blocks.push(block); block = []; notes = 0; }
  }
  if (notes) blocks.push(block);
  else if (block.length && blocks.length) blocks[blocks.length - 1].push(...block);
  return blocks;
}

export function durationPreview(settings: CommonPartSettings): { start: number; duration: number; rest: boolean }[] {
  const row = (settings.customDurations ?? DEFAULT_DURATIONS).find((row, index) => row.length
    && (!settings.customDurationRandomWeighting || (settings.customDurationChances ?? DEFAULT_DURATION_CHANCES)[index] > 0)) ?? [];
  let start = 0;
  return paddedDurations(row, settings.patternFlip).map(value => {
    const slot = { start, duration: Math.abs(value), rest: value < 0 }; start += slot.duration; return slot;
  });
}
