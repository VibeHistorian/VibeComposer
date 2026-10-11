import { JavaRandom } from './java-random';
import type { MelodyGlobalSettings, MelodySettings } from '../project/project.model';
import { DEFAULT_MELODY_GLOBAL, decodeMelodyGlobal } from './melody-global-settings';

const DIRECTIONS = [
  [-1,-1,1,2], [-1,0,0,1], [0,-1,-1,1], [0,-1,1,0], [0,-1,0,1],
  [0,-1,1,1], [0,1,-1,0], [0,1,-1,1], [0,1,1,1], [0,1,1,2],
  [0,0,-1,1], [1,-1,1,2], [1,-1,0,1], [2,0,1,2], [2,0,-1,1],
];

/** Current MelodyPanel.overridePatterns fields; do not copy lead/start or identity. */
export function forceMelodyOutline(first: MelodySettings, target: MelodySettings): MelodySettings {
  return { ...target,
    chordNoteChoices: [...(first.chordNoteChoices ?? [0, 2, 2, 4])],
    melodyPatternOffsets: [...(first.melodyPatternOffsets ?? [1, 2, 1, 3])],
    maxBlockChange: first.maxBlockChange ?? 7, blockJump: first.blockJump ?? 1,
    maxNoteExceptions: first.maxNoteExceptions ?? 0, doubledRhythmChance: first.doubledRhythmChance ?? 0,
    splitChance: first.splitChance ?? 0, noteExceptionChance: first.noteExceptionChance ?? 0,
    speed: first.speed ?? 50,
  };
}

/** MelodyTargetUtils's tonic-relative target mode (2), with typed global inputs.
 * Diatonic triads are expressed as tonic-relative major-scale degrees.
 * Keep Java's directional closest-note search and root-insertion indexing.
 */
export function chooseMelodyTargets(seed: number, progression: readonly number[], globalSettings: MelodyGlobalSettings = {}): number[] {
  if (!Number.isInteger(seed) || seed < -2147483648 || seed > 2147483647
    || !progression.length || progression.length > 32
    || progression.some(degree => !Number.isInteger(degree) || degree < 1 || degree > 7)
    || !decodeMelodyGlobal(globalSettings)) throw new RangeError('Invalid Melody target inputs.');
  const globals = { ...DEFAULT_MELODY_GLOBAL, ...globalSettings };
  const direction = globals.targetDirection === 'ASC' ? 1 : globals.targetDirection === 'DESC' ? -1 : 0;
  const directions = globals.useDirectionsFromProgression
    ? progression.map((degree, index) => index === 0 ? 0 : Math.sign(degree - progression[index - 1]))
    : DIRECTIONS[new JavaRandom(seed).nextInt(DIRECTIONS.length)];
  const random = new JavaRandom((seed + 1) | 0);
  const variation = globals.targetNoteVariation * (direction === 0 ? 1 : 2);
  const offsets = progression.map((_, index) => {
    const original = directions[index % directions.length];
    const value = direction === 0 ? original : Math.abs(original) * direction;
    return (random.nextInt(variation) + 1) * value + (direction === 0 && value < 0 ? 1 : 0);
  });
  if (direction !== 0) offsets.sort((a, b) => direction * (a - b));
  const choices = progression.map(degree => [0, 2, 4].flatMap(step => {
    const value = (degree - 1 + step) % 7;
    return [value, value - 7];
  }).sort((a, b) => a - b));
  // Preserve Java's directional search, including its initial upper-bound fallback.
  const closest = (values: readonly number[], target: number) => {
    let result = direction <= 0 ? values[0] : values[values.length - 1], distance = Math.abs(target - result);
    for (let index = 1; index < values.length; index++) {
      if (direction === 1 && target > values[index] || direction === -1 && target < values[index]) continue;
      const nextDistance = Math.abs(target - values[index]);
      if (nextDistance < distance) { result = values[index]; distance = nextDistance; }
    }
    return result;
  };
  const correction = new JavaRandom(seed);
  const targets = offsets.map((offset, index) => choices[index].includes(offset) ? offset
    : closest(choices[index], offset + (correction.nextInt(100) < 75 ? (direction >= 0 ? 1 : -1) : 0)));
  if (targets.length > 3 && targets[targets.length - 1] === targets[targets.length - 3]) {
    const change = direction !== 0 ? direction * 2 : new JavaRandom(seed).nextBoolean() ? 2 : -2;
    targets[targets.length - 1] = closest(choices[targets.length - 1], targets[targets.length - 1] + change);
  }
  if (new JavaRandom(seed).nextInt(100) < 90 && !targets.includes(0)) {
    const order = Array.from({ length: direction === 0 ? targets.length : Math.floor(targets.length / 2) }, (_, index) => index);
    if (direction === 0) {
      const shuffleRandom = new JavaRandom((seed + 1324) | 0);
      for (let count = order.length; count > 1; count--) {
        const index = shuffleRandom.nextInt(count);
        [order[count - 1], order[index]] = [order[index], order[count - 1]];
      }
    }
    for (let index = 0; index < order.length; index++) {
      const selected = order[index];
      // Production tests a shuffled chord but writes the iteration index.
      if (choices[selected].includes(0) && Math.abs(targets[selected]) <= 2) { targets[index] = 0; break; }
    }
  }
  return targets;
}
