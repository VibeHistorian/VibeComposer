import { JavaRandom } from './java-random';
import { blockVariance, cumulativeWeights, directionChanges, generateBlock } from './melody-blocks';

export const MELODY_BLOCK_FAMILIES = ['Scaley', 'Neighbory', 'Arpy', 'Chordy', 'Wavy', 'Interval', 'Note'] as const;
export const DEFAULT_BLOCK_CHANGE_PREFERENCE = Object.freeze([0, 7, 4, 3, 1, 2, 5, 6]);
export const DEFAULT_BLOCK_TYPE_WEIGHTS = Object.freeze([100, 100, 100, 100, 100, 100, 100]);

/** Production BlockType catalogue, in enum and insertion order. */
export const CATALOGUE: readonly (readonly (readonly number[])[])[] = [
  [[0,1,2], [0,1,2,3], [0,1,4], [0,1,2,1], [0,4,2], [0,1,2,0], [0,1,2,4], [0,0,0]],
  [[0,-1,0], [0,1,0,1], [0,1,-1], [0,-1,0,1], [0,-1,2], [0,1,-1,0], [0,-1,0,2], [0,1,3,2], [0,0,-1,0]],
  [[0,2,0,2], [0,2,1], [0,2,4,2], [0,2,1,3], [0,2,3], [0,3,1,2], [0,1,4,5], [0,1,7,6], [0,1,6,7]],
  [[0,2,4], [0,4,2], [0,4,2,7], [0,7,4,2]],
  [[0,-2,3,4], [0,-2,2,6], [0,-2,3,6], [0,-2,-1,2], [0,-1,-2,1], [0,1,-1,2], [0,2,-1,-2], [0,-3,-2,-1]],
  [[0,0], [0,1], [0,2], [0,3], [0,4], [0,5], [0,7]],
  [[0]],
];
interface Shape { readonly type: number; readonly notes: readonly number[] }
const ALL_BLOCKS: readonly Shape[] = CATALOGUE.flatMap((blocks, type) => blocks.map(notes => ({ type, notes })));
const changeOf = (notes: readonly number[]) => notes[notes.length - 1] - notes[0];
const inverse = (notes: readonly number[]) => notes.map(note => note === 0 ? 0 : -note);

/** Literal BlockType.containsBlock fallback classification, including its inverted comparison. */
function classify(notes: readonly number[]): number {
  for (const candidate of ALL_BLOCKS) {
    if (candidate.notes.length !== notes.length) continue;
    let direct = true, inverted = true;
    for (let index = 0; index < notes.length; index++) {
      if (notes[index] !== candidate.notes[index]) direct = false;
      else if (-notes[index] !== candidate.notes[index]) inverted = false;
    }
    if (direct || inverted) return candidate.type;
  }
  throw new RangeError('Unknown Melody catalogue block.');
}

/** MelodyBlockUtils.getRandomForTypeAndBlockChangeAndLength. */
function typedBlock(type: number | undefined, change: number, length: number | undefined,
  random: JavaRandom, approximation: number): readonly number[] | undefined {
  const clamped = Math.max(-7, Math.min(7, change));
  const blocks = type === undefined ? ALL_BLOCKS.map(block => block.notes) : CATALOGUE[type];
  const viable = blocks.filter(notes => length === 1 || ((length === undefined || notes.length === length)
    && Math.abs(changeOf(notes) - Math.abs(clamped)) <= approximation));
  if (!viable.length) return undefined;
  const notes = viable[random.nextInt(viable.length)];
  return changeOf(notes) === -clamped ? inverse(notes) : notes;
}

/** Production approximate selection: signed direct/inverted pools, constraints, ranking and thinning. */
export function chooseCatalogueBlock(change: number, approximation: number, random: JavaRandom,
  length: number | undefined, variance: number, directions: number,
  preference: readonly number[], weights: readonly number[]): Shape {
  let viable: Shape[] = [];
  for (let value = Math.max(change - approximation, -7); value <= Math.min(change + approximation, 7); value++) {
    viable.push(...ALL_BLOCKS.filter(block => changeOf(block.notes) === value
      && (length === undefined || block.notes.length === length)));
    viable.push(...ALL_BLOCKS.filter(block => changeOf(block.notes) === -value
      && (length === undefined || block.notes.length === length)).map(block => ({ type: block.type, notes: inverse(block.notes) })));
  }
  viable = viable.filter(block => blockVariance(block.notes) <= variance && directionChanges(block.notes) <= directions);
  if (!viable.length) {
    const notes = typedBlock(undefined, change, length, random, 4);
    if (!notes) throw new RangeError('No Melody catalogue block fits the requested length.');
    return { type: classify(notes), notes };
  }
  if (preference.length) viable.sort((left, right) => preference.indexOf(Math.abs(changeOf(left.notes)))
    - preference.indexOf(Math.abs(changeOf(right.notes))));
  if (weights.some(weight => weight > 0)) {
    const counts = CATALOGUE.map((_, type) => viable.filter(block => block.type === type).length);
    const adjusted = Array<number>(CATALOGUE.length).fill(0);
    let matching = 0;
    // Production intentionally loops over the first five families here.
    for (let type = 0; type < 5; type++) {
      adjusted[type] = Math.max(weights[type] > 0 && counts[type] > 0 ? 1 : 0, Math.trunc(counts[type] * weights[type] / 100));
      if (counts[type] > 0) matching += adjusted[type];
    }
    if (matching > 0) for (let index = viable.length - 1; index >= 0; index--) {
      const type = viable[index].type;
      if (adjusted[type] < counts[type]) { viable.splice(index, 1); counts[type]--; }
    }
  }
  const selected = preference.length ? Math.min(viable.length - 1, Math.trunc(random.nextDouble() ** 2 * viable.length))
    : random.nextInt(viable.length);
  return viable[selected];
}

/** MelodyBlockSkeletonGenerator.selectBlockShape, retaining its post-selection draw order. */
export function selectMelodyBlock(change: number, jump: number, random: JavaRandom,
  generated: boolean, length: number | undefined, variance: number, directions: number,
  preference: readonly number[], weights: readonly number[], startingDegree: number, previousType: number): Shape {
  let selected: Shape = generated
    ? { type: 2147483647, notes: generateBlock(change, jump, random, length, variance, directions) }
    : chooseCatalogueBlock(change, jump, random, length, variance, directions, preference, weights);
  const chordyMismatch = selected.type === 3 && ![0, 2, 3, 5].includes((startingDegree + 70) % 7);
  if (selected.type !== 2147483647 && (selected.type === previousType || chordyMismatch)) {
    const types = CATALOGUE.flatMap((blocks, type) => type !== selected.type && blocks.some(notes => changeOf(notes) === Math.abs(change)) ? [type] : []);
    if (types.length) {
      const cumulative = cumulativeWeights(types.map(type => weights[type]));
      const draw = random.nextInt(100);
      const type = types[cumulative.findIndex(value => draw < value)];
      const notes = typedBlock(type, change, selected.notes.length, random, 0);
      if (notes) selected = { type, notes };
    }
  }
  return { type: selected.type, notes: [...selected.notes] };
}
