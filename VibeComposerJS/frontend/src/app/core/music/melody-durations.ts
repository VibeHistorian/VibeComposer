import type { MelodySettings } from './part-settings.model';
import { JavaRandom } from './java-random';
import { cumulativeWeights } from './melody-blocks';

/** Ten Java duration lanes. Positive beats sound; negative beats are rests. Empty lanes are unused. */
export type MelodyDurations = readonly (readonly number[])[];
export const DEFAULT_MELODY_DURATIONS: MelodyDurations = Object.freeze([
  Object.freeze([1, -0.5, 0.5, 1, -1]), ...Array.from({ length: 9 }, () => Object.freeze([] as number[])),
]);
export const DEFAULT_DURATION_CHANCES: readonly number[] = Object.freeze(Array(10).fill(50));
const EPS = 1e-7;

export function isMelodyDurations(value: unknown): value is MelodyDurations {
  return Array.isArray(value) && value.length === 10 && Array.from(value).every(row => Array.isArray(row)
    && row.length <= 64 && Array.from(row).every(duration => typeof duration === 'number' && Number.isFinite(duration)
      && Math.abs(duration) >= 1 / 960 - EPS && Math.abs(duration) <= 4)
    && row.reduce((sum, duration) => sum + Math.abs(duration), 0) <= 4 + EPS);
}

/** Java pads the chosen lane with trailing silence to fill one chord. */
export function paddedMelodyDurations(row: readonly number[], flipped = false): number[] {
  const result = [...row], remaining = 4 - row.reduce((sum, duration) => sum + Math.abs(duration), 0);
  if (remaining > EPS) result.push(-remaining);
  return flipped ? result.map(duration => -duration) : result;
}

export function melodyDurationError(settings: MelodySettings): string | null {
  const rows: unknown = settings.customMelodyDurations ?? DEFAULT_MELODY_DURATIONS;
  if (!isMelodyDurations(rows)) {
    const lane = Array.isArray(rows) && rows.length === 10
      ? rows.findIndex(row => !isMelodyDurations([row, ...Array.from({ length: 9 }, () => [])])) : -1;
    return `${lane >= 0 ? 'Lane ' + (lane + 1) + ': ' : ''}Use up to 64 nonzero durations per lane, totaling at most four beats.`;
  }
  const chances = settings.customDurationChances ?? DEFAULT_DURATION_CHANCES;
  if (chances.length !== 10 || Array.from(chances).some(value => !Number.isInteger(value) || value < 0 || value > 100))
    return 'Each lane chance must be an integer from 0 to 100.';
  const active = rows.flatMap((row, index) => row.length && (!settings.customDurationRandomWeighting || chances[index] > 0) ? [index] : []);
  if (!active.length) return 'Draw at least one rhythm lane with a positive chance when weighted selection is enabled.';
  for (const index of active) if (!paddedMelodyDurations(rows[index], settings.patternFlip).some(duration => duration > 0))
    return `Lane ${index + 1} contains only pauses${settings.patternFlip ? ' after Flip' : ''}. Add a note or disable this lane.`;
  return null;
}

/** Java chooses by block identity (or chord index), then uses the same stream to group 3–4 notes. */
export function melodyDurationBlocks(settings: MelodySettings, seed: number, chord: number, blockOffset = -1): number[][] {
  const error = melodyDurationError(settings);
  if (error) throw new RangeError(error);
  const rows = settings.customMelodyDurations ?? DEFAULT_MELODY_DURATIONS;
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
  const durations = paddedMelodyDurations(rows[active[index]], settings.patternFlip);
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

export function melodyDurationPreview(settings: MelodySettings): { start: number; duration: number; rest: boolean }[] {
  const row = (settings.customMelodyDurations ?? DEFAULT_MELODY_DURATIONS).find((row, index) => row.length
    && (!settings.customDurationRandomWeighting || (settings.customDurationChances ?? DEFAULT_DURATION_CHANCES)[index] > 0)) ?? [];
  let start = 0;
  return paddedMelodyDurations(row, settings.patternFlip).map(value => {
    const slot = { start, duration: Math.abs(value), rest: value < 0 }; start += slot.duration; return slot;
  });
}
