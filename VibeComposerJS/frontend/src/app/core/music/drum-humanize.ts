import { JavaRandom } from './java-random';
import { javaShuffle } from './arpeggio-pattern';

/** RhythmOffsetUtils.generateRhythmOffsets; the production bounded adjustment and shuffle. */
export function rhythmOffsets(size: number, variation: number, seed: bigint): number[] {
  if (size < 1) return [];
  if (size === 1) return [0];
  const random = new JavaRandom(seed), offsets: number[] = [];
  let sum = 0;
  for (let index = 0; index < size - 1; index++) {
    const value = -variation + 2 * variation * random.nextDouble();
    offsets.push(value); sum += value;
  }
  let last = -sum;
  if (Math.abs(sum) > variation) {
    let excess = sum;
    for (let index = 0; index < size - 1 && Math.abs(excess) > 0.00001; index++) {
      let adjustment = (random.nextDouble() - 0.5) * 2 * Math.min(Math.abs(excess), variation - Math.abs(offsets[index]));
      if ((excess < 0 && adjustment < 0) || (excess > 0 && adjustment > 0)) adjustment *= -1;
      offsets[index] += adjustment; excess += adjustment;
    }
    last = -excess;
  }
  offsets.push(Math.max(-variation, Math.min(variation, last)));
  return javaShuffle(offsets, random);
}

/** JMusicUtilsCustom drum timing at normal stretch. Rest events consume the same positions. */
export function drumHumanize(count: number, amount: number, seed: bigint | number): { offsets: number[]; durations: number[] } {
  const root = new JavaRandom(BigInt.asIntN(64, BigInt(seed) + 999n));
  const random = new JavaRandom(BigInt.asIntN(64, root.nextLong() + 4001n));
  return { offsets: [0, ...rhythmOffsets(count - 1, amount / 10000, random.nextLong())],
    durations: [0, ...rhythmOffsets(count - 1, amount / 10000, random.nextLong())] };
}
