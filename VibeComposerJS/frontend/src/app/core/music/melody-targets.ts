import { JavaRandom } from './java-random';

const DIRECTIONS = [
  [-1,-1,1,2], [-1,0,0,1], [0,-1,-1,1], [0,-1,1,0], [0,-1,0,1],
  [0,-1,1,1], [0,1,-1,0], [0,1,-1,1], [0,1,1,1], [0,1,1,2],
  [0,0,-1,1], [1,-1,1,2], [1,-1,0,1], [2,0,1,2], [2,0,-1,1],
];

/** MelodyTargetUtils: mode 2, variation 3, ASC, randomized directions.
 * The supported diatonic progression is expressed in tonic-relative scale degrees,
 * just like the block consumer. Extended target policies remain P5 inputs.
 */
export function chooseMelodyTargets(seed: number, progression: readonly number[]): number[] {
  const directions = DIRECTIONS[new JavaRandom(seed).nextInt(DIRECTIONS.length)];
  const random = new JavaRandom((seed + 1) | 0);
  const offsets = progression.map((_, index) => (random.nextInt(6) + 1) * Math.abs(directions[index % directions.length]))
    .sort((a, b) => a - b);
  const choices = progression.map(degree => [0, 2, 4].flatMap(step => {
    const value = (degree - 1 + step) % 7;
    return [value, value - 7];
  }).sort((a, b) => a - b));
  // Preserve Java's directional search, including its initial upper-bound fallback.
  const closest = (values: readonly number[], target: number) => {
    let result = values[values.length - 1], distance = Math.abs(target - result);
    for (let index = 1; index < values.length; index++) {
      if (target > values[index]) continue;
      const nextDistance = Math.abs(target - values[index]);
      if (nextDistance < distance) { result = values[index]; distance = nextDistance; }
    }
    return result;
  };
  const correction = new JavaRandom(seed);
  const targets = offsets.map((offset, index) => choices[index].includes(offset) ? offset
    : closest(choices[index], offset + (correction.nextInt(100) < 75 ? 1 : 0)));
  if (targets.length > 3 && targets[targets.length - 1] === targets[targets.length - 3]) {
    targets[targets.length - 1] = closest(choices[targets.length - 1], targets[targets.length - 1] + 2);
  }
  if (new JavaRandom(seed).nextInt(100) < 90 && !targets.includes(0)) {
    for (let index = 0; index < Math.floor(targets.length / 2); index++) {
      if (choices[index].includes(0) && Math.abs(targets[index]) <= 2) { targets[index] = 0; break; }
    }
  }
  return targets;
}
