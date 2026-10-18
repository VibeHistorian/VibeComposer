import { JavaRandom } from './java-random';
import { chordPitches, progressionChords, scaleNotes, type ProgressionChord, type ScaleMode } from './harmony';
import { generateDiatonicProgression } from './progression-generator';
import type { HarmonySettings } from './harmony-voicing';
import type { MelodyGlobalSettings } from '../project/project.model';

export const HARMONY_GENERATIONS = [
  { value: 'CURRENT', label: 'Legacy' }, { value: 'FUNCTIONAL', label: 'Functional' },
  { value: 'FIFTHS', label: 'Fifths' }, { value: 'POP_LOOP', label: 'Pop loop' },
  { value: 'MODAL', label: 'Modal' }, { value: 'RANDOM', label: 'Random' },
] as const;
const FIFTHS = [0, 3, 6, 2, 5, 1, 4];
const POP = [[0,4,5,3], [0,5,3,4], [5,3,0,4], [0,3,5,4]];
const CLOSED = [[0,1,4,0], [0,3,4,0], [5,1,4,0], [5,3,4,0], [2,5,4,0], [0,5,4,0],
  [0,3,6,0], [5,3,6,0], [0,5,3,0], [0,1,3,0]];
const OPEN = [[0,5,1,4], [0,3,1,4], [5,0,1,4], [0,3,4,5], [0,1,4,5]];
const mod = (n: number, d = 12) => (n % d + d) % d;
export function mixContextualSeed(seed: bigint): bigint {
  let n = BigInt.asUintN(64, seed);
  n = BigInt.asUintN(64, (n ^ n >> 30n) * 0xbf58476d1ce4e5b9n);
  n = BigInt.asUintN(64, (n ^ n >> 27n) * 0x94d049bb133111ebn);
  return BigInt.asIntN(64, n ^ n >> 31n);
}

/** Java HarmonicProgressionPlanner's phrase schemas and bounded, seeded beam search.
 * Custom events are hard constraints, including their quality and inversion.
 */
export function generateHarmony(seed: bigint | number, length: number, key: string, scale: ScaleMode,
  settings: HarmonySettings = {}, melody: MelodyGlobalSettings = {},
  locks?: readonly (ProgressionChord | null)[]): { degrees: number[]; chords?: readonly (ProgressionChord | null)[] } {
  if (!Number.isInteger(length) || length < 1 || length > 32) throw new RangeError('Progression length must be 1..32.');
  if (typeof seed === 'number' && !Number.isSafeInteger(seed)) throw new RangeError('Use bigint for 64-bit harmony seeds.');
  const baseSeed = BigInt(seed);
  let strategy = settings.generation ?? 'CURRENT';
  if (strategy === 'RANDOM') strategy = HARMONY_GENERATIONS[new JavaRandom(baseSeed).nextInt(5)].value;
  if (strategy === 'CURRENT') return { degrees: generateDiatonicProgression(seed, length), ...(locks ? { chords: locks } : {}) };
  const notes = scaleNotes(scale), vocabulary = progressionChords(key, scale, [1,2,3,4,5,6,7]);
  if (strategy === 'FUNCTIONAL' && notes[2] === 3 && notes[4] === 7) {
    vocabulary[4] = { ...vocabulary[4], spice: '7', intervals: undefined };
    if (notes[1] === 2) vocabulary[1] = { ...vocabulary[1], spice: 'm7', intervals: [0,3,6,10] };
  }
  const degreeOf = (chord: ProgressionChord | null | undefined) => chord ? vocabulary.findIndex(c => c.root === chord.root) : -1;
  const first = degreeOf(locks?.[0]), last = degreeOf(locks?.[length - 1]);
  let plans = CLOSED.filter(plan => plan[2] !== 6 || notes[6] === 11);
  if (last === 4 || last === 5 || melody.contextualMelody && (melody.ending ?? 'LOOP') !== 'CLOSED') plans = [...plans, ...OPEN];
  if (plans.some(plan => plan[3] === last)) plans = plans.filter(plan => plan[3] === last);
  if (plans.some(plan => plan[0] === first)) plans = plans.filter(plan => plan[0] === first);
  const functional = plans[new JavaRandom(mixContextualSeed(baseSeed ^ 0x66756e6374696f6en)).nextInt(plans.length)];
  const pop = POP[new JavaRandom(mixContextualSeed(baseSeed ^ 0x7374727563747572n)).nextInt(POP.length)];
  let phase = new JavaRandom(mixContextualSeed(baseSeed ^ 0x666966746873n)).nextInt(7);
  if (first >= 0) phase = FIFTHS.indexOf(first);
  else if (last >= 0) phase = mod(FIFTHS.indexOf(last) - length + 1, 7);
  const transition = (a: ProgressionChord, b: ProgressionChord) => {
    if (a.root === b.root) return -6;
    const ap = chordPitches(a, 0).map(p => mod(p)), bp = chordPitches(b, 0).map(p => mod(p));
    const tonic = vocabulary[0].root;
    return (mod(b.root - a.root) === 5 ? 3 : 0)
      + (mod(a.root - tonic) === 7 && ap.includes(mod(a.root + 4)) && b.root === tonic ? 5 : 0)
      + (mod(a.root - tonic) === 2 && mod(b.root - tonic) === 7 ? 3 : 0)
      + ap.filter(p => bp.includes(p)).length * 0.5;
  };
  type Candidate = { path: ProgressionChord[]; degrees: number[]; score: number; serial: number };
  let beam: Candidate[] = [{ path: [], degrees: [], score: 0, serial: 0 }], serial = 1;
  for (let index = 0; index < length; index++) {
    const choices = locks?.[index] ? [locks[index]!] : vocabulary;
    const random = new JavaRandom(baseSeed ^ 0x9e3779b97f4a7c15n * BigInt(index + 1));
    const variation = choices.map(() => random.nextDouble() * 1.5), next: Candidate[] = [];
    for (const state of beam) choices.forEach((chord, choice) => {
      const degree = degreeOf(chord), previous = state.path[index - 1];
      let score = state.score + variation[choice] + (previous ? transition(previous, chord) : 0);
      if (strategy === 'FUNCTIONAL') {
        const expected = index === length - 1 ? functional[3] : index === length - 2 ? functional[2]
          : index === length - 3 ? functional[1] : index === 0 ? functional[0] : functional[index % 4];
        if (degree === expected) score += index >= length - 3 ? 10 : index === 0 ? 8 : 2;
      } else if (strategy === 'FIFTHS') {
        const prior = degreeOf(previous), expected = prior >= 0 ? (prior + 3) % 7 : FIFTHS[(phase + index) % 7];
        score += degree === expected ? 16 : -5;
      } else if (strategy === 'POP_LOOP') {
        score += degree === pop[index % 4] ? 10 : -2;
        if (index === length - 1 && state.path.length) score += transition(chord, state.path[0]);
      } else {
        score += degree === 0 ? 5 : degree === 3 || degree === 6 ? 3 : 0;
        if (previous?.root === chord.root) score += 2;
      }
      next.push({ path: [...state.path, chord], degrees: [...state.degrees, degree < 0 ? 1 : degree + 1], score, serial: serial++ });
    });
    next.sort((a,b) => b.score - a.score || a.serial - b.serial);
    beam = next.slice(0,32);
  }
  return { degrees: beam[0].degrees, chords: beam[0].path };
}
