import { JavaRandom } from './java-random';

/** Java int arithmetic, including overflow before a seed reaches java.util.Random. */
export const melodyInt = (value: number): number => value | 0;
export const MELODY_EPSILON = 0.01; // Constants.DBL_ERR

export function cumulativeWeights(weights: readonly number[]): number[] {
  const total = weights.reduce((sum, weight) => sum + Math.max(0, weight), 0);
  let accumulated = 0;
  const result = weights.map((weight, index) => total === 0
    ? Math.round((index + 1) * 100 / weights.length)
    : accumulated += Math.max(0, Math.round(weight * 100 / total)));
  result[result.length - 1] = 100;
  return result;
}

/** Rhythm.regenerateDurations: the outer, chord-length block rhythm. */
export function blockRhythm(seed: number, limit: number, weights: readonly number[]): number[] {
  const pool = [1, 2, 3, 4];
  const random = new JavaRandom(seed);
  const result: number[] = [];
  let sum = 0, same = 0, lastIndex = -2147483648, retries = 0, longest = 0, longestIndex = 0;
  while (sum < limit - MELODY_EPSILON) {
    let duration = 1, chosen = 0, last = false;
    const chance = random.nextInt(100);
    for (let index = 0; index < pool.length; index++) {
      if (index < pool.length - 1 && pool[index + 1] > limit - sum + MELODY_EPSILON) {
        duration = limit - sum;
        if (duration < 1 - MELODY_EPSILON && result.length) {
          longest = longest - 1 + duration;
          result[longestIndex] = longest;
          duration = 1;
        }
        last = true; break;
      }
      if (chance < weights[index]) { duration = pool[index]; chosen = index; break; }
    }
    if (last) { result.push(duration); break; }
    if (lastIndex === chosen) same++; else { lastIndex = chosen; same = 0; }
    if (same < 10 || chosen === 0 || retries === 2) {
      sum += duration; result.push(duration);
      if (duration > longest) { longest = duration; longestIndex = result.length - 1; }
      if (retries === 2) retries = 0;
    } else retries++;
  }
  return result;
}

/** Rhythm.makeDurations: preserve Java's weight redistribution and final remainder. */
export function noteRhythm(seed: number, limit: number, count: number, speed: number): number[] {
  const short = limit < 1 - MELODY_EPSILON;
  const pool = short ? [0.125, 0.25, 0.5, 0.75, 1, 1.5, 2] : [0.25, 0.5, 0.75, 1, 1.5, 2];
  const quick = (speed - 50) * 2, slow = -quick;
  const weights = cumulativeWeights(short
    ? [100 + quick, 100 + quick, 300 + quick, 100 + quick, 300 + slow, 100 + slow, 100 + slow]
    : [100 + quick, 300 + quick, 100 + quick, 300 + slow, 100 + slow, 100 + slow]);
  const random = new JavaRandom(seed), result: number[] = [];
  let sum = 0, maximum = pool.length, remaining = count, adjust = 0;
  while (remaining > 0) {
    let duration = pool[0], last = false;
    const chance = random.nextInt(100);
    const allowed = limit - sum - remaining * duration + duration + MELODY_EPSILON;
    for (let index = maximum - 1; index >= 0; index--) {
      if (pool[index] <= allowed) break;
      maximum = index;
      if (index > 1) adjust += Math.trunc((weights[index] - weights[index - 1]) / (index - 1));
    }
    for (let index = 0; index < maximum; index++) {
      if (index < maximum - 1 && pool[index + 1] > limit - sum) {
        duration = limit - sum; last = true; break;
      }
      if (chance < weights[index] + adjust) { duration = pool[index]; break; }
    }
    sum += duration; result.push(duration);
    if (last) break;
    remaining--;
  }
  if (Math.abs(sum - limit) >= MELODY_EPSILON) result[result.length - 1] += Math.abs(limit - sum);
  return result;
}

export function directionChanges(block: readonly number[]): number {
  let direction = block[block.length - 1] - block[0], count = 0;
  for (let index = 1; index < block.length; index++) {
    const current = block[index] - block[index - 1];
    if (direction > 0 && current < 0) { direction = -1; count++; }
    else if (direction < 0 && current > 0) { direction = 1; count++; }
  }
  return count;
}

export function blockVariance(block: readonly number[]): number {
  const low = Math.min(block[0], block[block.length - 1]), high = Math.max(block[0], block[block.length - 1]);
  return Math.max(0, ...block.slice(1, -1).map((note) => Math.max(low - note, note - high)));
}

function shuffle(values: number[], random: JavaRandom): void {
  for (let count = values.length; count > 1; count--) {
    const index = random.nextInt(count);
    [values[count - 1], values[index]] = [values[index], values[count - 1]];
  }
}

/** MelodyBlockUtils.blockChangeSequence / smartShuffleMaxDirChange. */
export function blockChangeSequence(first: number, next: number, seed: number, count: number, maximum: number): number[] {
  let random = new JavaRandom(seed), change = first - next;
  const result = Array.from({ length: count }, () => {
    const value = random.nextInt(maximum * 2 + 1) - maximum; change += value; return value;
  });
  const indices = result.map((_, index) => index).filter((index) => change > 0
    ? result[index] !== -maximum : change < 0 ? result[index] !== maximum : true);
  random = new JavaRandom(seed);
  const increment = change > 0 ? -1 : 1;
  for (let index = 0; index < Math.abs(change) && indices.length; index++) {
    const selected = random.nextInt(indices.length), target = indices[selected];
    result[target] += increment;
    if (Math.abs(result[target]) === maximum) indices.splice(selected, 1);
  }
  random = new JavaRandom(seed);
  if (result.length <= 1) return result;
  if (!result.some((value) => value < 0) || !result.some((value) => value > 0) || result.length < 3) {
    shuffle(result, random); return result;
  }
  result.sort((a, b) => a - b);
  let maxNegative = 0, firstPositive = 0;
  for (let index = 1; index < result.length; index++) {
    if (result[index] >= 0 && maxNegative === 0) maxNegative = index - 1;
    if (result[index] > 0 && firstPositive === 0) { firstPositive = index; break; }
  }
  const zeroCount = firstPositive - maxNegative - 1;
  const negative = result.slice(0, maxNegative + 1), positive = result.slice(firstPositive);
  shuffle(negative, random); shuffle(positive, random);
  // P1b fixes melodyMaxDirChanges to 2, matching the supported Java configuration.
  if (random.nextBoolean()) negative.unshift(positive.splice(random.nextInt(positive.length), 1)[0]);
  const combined = random.nextBoolean() ? [...negative, ...positive] : [...positive, ...negative];
  for (let index = 0; index < zeroCount; index++) combined.splice(random.nextInt(combined.length), 0, 0);
  return combined;
}

/** The current generated-block branch, not the legacy melody skeleton. */
export function generateBlock(change: number, jump: number, random: JavaRandom,
  forcedLength: number | undefined, variance: number, remainingDirections: number): number[] {
  const generator = new JavaRandom(random.nextInt());
  const length = forcedLength ?? generator.nextInt(2) + 3;
  const notes = Array<number>(length).fill(0), last = length - 1;
  notes[last] = Math.abs(change) + generator.nextInt(jump * 2 + 1) - jump;
  if (change < 0 && notes[last] > 0) notes[last] *= -1;
  let low = Math.min(0, notes[last]), high = Math.max(0, notes[last]);
  const restricted = high - low !== 0;
  let directions = remainingDirections;
  for (let index = 1; index < last; index++) {
    const cannotChange = restricted && directions < 1;
    const usableVariance = cannotChange ? 0 : Math.trunc((variance + 1) / 2);
    const ascending = notes[last] - notes[index - 1] > 0;
    const upper = cannotChange ? ascending ? notes[last] : notes[index - 1] : high;
    const lower = cannotChange ? ascending ? notes[index - 1] : notes[last] : low;
    notes[index] = generator.nextInt(usableVariance * 2 + upper - lower + 1) + lower - usableVariance;
    if (low - notes[index] > 0) { variance -= low - notes[index]; low = notes[index]; }
    if (notes[index] - high > 0) { variance -= notes[index] - high; high = notes[index]; }
    if (!restricted && !cannotChange) directions = remainingDirections - directionChanges([...notes.slice(0, index + 1), notes[last]]);
    variance = Math.max(0, variance);
  }
  return notes;
}
