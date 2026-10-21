import { isScaleMode } from './scales';
import { JavaRandom } from './java-random';
import { contextualTargets } from './contextual-randomizer';
import type { MelodyGlobalSettings, MelodySettings } from './part-settings.model';
import { DEFAULT_MELODY_GLOBAL, decodeMelodyGlobal } from './melody-global-settings';
import { melodyRootChords } from './melody-chord-targets';
import { getPitchClass, validProgressionChords, type ProgressionChord, type ScaleMode } from './harmony';

const MAJOR = [0, 2, 4, 5, 7, 9, 11];

function nearestPitch(pitch: number, circular = false): number {
  let closest = MAJOR[0];
  for (const candidate of MAJOR.slice(1)) if (Math.abs(candidate - pitch) < Math.abs(closest - pitch)) closest = candidate;
  if (circular && Math.abs(pitch + 12 - 11) < Math.abs(closest - pitch)) closest = 11;
  if (circular && Math.abs(pitch - 12) < Math.abs(closest - pitch)) closest = 0;
  return closest;
}

/** MelodyBlockUtils.getStartingNote, including its mode-2 final target rule and index octave quirks. */
export function startingMelodyTarget(roots: readonly (readonly number[])[], choices: readonly number[], index: number, mode: number): number {
  const chord = roots[index % roots.length];
  const choice = choices[mode === 2 && index === choices.length ? index - 1 : index % choices.length];
  let pitch = mode === 2 ? 60 : chord[0];
  if (mode === 0) {
    const span = 1 + Math.trunc(Math.abs(chord[chord.length - 1] - chord[0]) / 12);
    const indexedPitch = (value: number) => chord[(value % chord.length + chord.length) % chord.length]
      + (value >= chord.length ? 12 * Math.trunc(value / chord.length) : value < 0 ? -12 + 12 * Math.trunc(value / chord.length) : 0) * span;
    let selected = choice;
    pitch = indexedPitch(selected);
    // Java returns null for extreme indices. Keep valid imported/scoped lists usable by choosing the nearest valid index.
    while (pitch <= 0 || pitch >= 127) { selected -= Math.sign(selected); pitch = indexedPitch(selected); }
  }
  const semitone = pitch % 12;
  return Math.trunc(pitch / 12) * 7 + MAJOR.indexOf(MAJOR.includes(semitone) ? semitone : nearestPitch(semitone, true))
    + (mode > 0 ? choice : 0);
}

const DIRECTIONS = [
  [-1,-1,1,2], [-1,0,0,1], [0,-1,-1,1], [0,-1,1,0], [0,-1,0,1],
  [0,-1,1,1], [0,1,-1,0], [0,1,-1,1], [0,1,1,1], [0,1,1,2],
  [0,0,-1,1], [1,-1,1,2], [1,-1,0,1], [2,0,1,2], [2,0,-1,1],
];

/** Supported MelodyPanel.overridePatterns fields; do not copy start or identity. */
export function forceMelodyOutline(first: MelodySettings, target: MelodySettings): MelodySettings {
  return { ...target,
    chordNoteChoices: [...(first.chordNoteChoices ?? [0, 2, 2, 4])],
    melodyPatternOffsets: [...(first.melodyPatternOffsets ?? [1, 2, 1, 3])],
    maxBlockChange: first.maxBlockChange ?? 7, blockJump: first.blockJump ?? 1,
    splitChance: first.splitChance ?? 0,
    speed: first.speed ?? 50,
  };
}

/** MelodyTargetUtils's three block target modes, with typed global inputs and unsquished root chords.
 * Keep Java's directional closest-note search and root-insertion indexing.
 */
export function chooseMelodyTargets(seed: number, progression: readonly number[], globalSettings: MelodyGlobalSettings = {},
  key = 'C', scale: ScaleMode = 'major', customChords?: readonly (ProgressionChord | null)[]): number[] {
  if (!Number.isInteger(seed) || seed < -2147483648 || seed > 2147483647
    || !progression.length || progression.length > 32
    || progression.some(degree => !Number.isInteger(degree) || degree < 1 || degree > 7)
    || getPitchClass(key) === undefined || !isScaleMode(scale)
    || !decodeMelodyGlobal(globalSettings) || !validProgressionChords(customChords, progression.length)) throw new RangeError('Invalid Melody target inputs.');
  const globals = { ...DEFAULT_MELODY_GLOBAL, ...globalSettings };
  if (globals.contextualMelody) return contextualTargets(seed, progression, key, scale, globals, customChords);
  const mode = globals.blockTargetMode, roots = melodyRootChords(key, scale, progression, customChords);
  const rootOffsets = roots.map(chord => -MAJOR.indexOf(MAJOR.includes(chord[0] % 12) ? chord[0] % 12 : nearestPitch(chord[0] % 12)));
  const direction = globals.targetDirection === 'ASC' ? 1 : globals.targetDirection === 'DESC' ? -1 : 0;
  const directions = globals.useDirectionsFromProgression
    ? roots.map((chord, index) => index === 0 ? 0 : Math.sign(chord[0] - roots[index - 1][0]))
    : DIRECTIONS[new JavaRandom(seed).nextInt(DIRECTIONS.length)];
  const random = new JavaRandom((seed + 1) | 0);
  const variation = globals.targetNoteVariation * (direction === 0 ? 1 : 2);
  const offsets = progression.map((_, index) => {
    const original = directions[index % directions.length];
    const value = direction === 0 ? original : Math.abs(original) * direction;
    return (random.nextInt(variation) + 1) * value + (direction === 0 && value < 0 ? 1 : 0);
  });
  if (direction !== 0) offsets.sort((a, b) => direction * (a - b));
  if (mode === 0) {
    if (offsets.length > 3 && offsets[offsets.length - 1] === offsets[offsets.length - 3]) offsets[offsets.length - 1] += new JavaRandom(seed).nextInt(100) < 75 ? 1 : -1;
    return offsets;
  }
  const choices = roots.map(chord => {
    const diatonic = chord.flatMap(pitch => {
      const value = MAJOR.indexOf(pitch % 12);
      return value >= 0 ? [value, value - 7] : [];
    });
    return (diatonic.length ? diatonic : chord.map(pitch => MAJOR.indexOf(nearestPitch(pitch % 12, true)))).sort((a,b) => a-b);
  });
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
  const targets = offsets.map((offset, index) => (choices[index].includes(offset) ? offset
    : closest(choices[index], offset + (correction.nextInt(100) < 75 ? (direction >= 0 ? 1 : -1) : 0))) + (mode === 1 ? rootOffsets[index] : 0));
  if (mode === 2 && targets.length > 3 && targets[targets.length - 1] === targets[targets.length - 3]) {
    const change = direction !== 0 ? direction * 2 : new JavaRandom(seed).nextBoolean() ? 2 : -2;
    targets[targets.length - 1] = closest(choices[targets.length - 1], targets[targets.length - 1] + change);
  }
  if (mode === 2 && new JavaRandom(seed).nextInt(100) < 90 && !targets.includes(0)) {
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
      if (roots[selected].some(pitch => pitch % 12 === 0) && Math.abs(targets[selected]) <= 2) { targets[index] = 0; break; }
    }
  }
  return targets;
}
