import { chordPitches, getPitchClass, scaleNotes, CHORD_SPICES, type ProgressionChord, type ScaleMode } from './harmony';
import { JavaRandom } from './java-random';
import type { HarmonySettings } from './harmony-voicing';

const mod = (n: number) => (n % 12 + 12) % 12;

/** Color an already planned progression without changing its functional roots or random stream.
 * The chance gates each unlocked slot; strategy and resolution determine its vocabulary.
 * Exact intervals avoid the legacy m9 spelling's major third and retain unusual modes.
 */
export function spiceHarmony(seed: bigint, key: string, scale: ScaleMode, degrees: readonly number[],
  chords: readonly ProgressionChord[], settings: HarmonySettings,
  locks?: readonly (ProgressionChord | null)[]): ProgressionChord[] {
  const chance = settings.spiciness ?? 0;
  if (!chance) return [...chords];
  const notes = scaleNotes(scale), tonic = getPitchClass(key)!;
  const strategy = settings.generation ?? 'CURRENT';
  return chords.map((chord, index) => {
    if (locks?.[index]) return chord;
    const random = new JavaRandom(seed ^ 0x5350494345n ^ BigInt(index + 1) * 0x9e3779b97f4a7c15n);
    if (random.nextInt(100) >= chance) return chord;
    const degree = degrees[index] - 1;
    const triad = chordPitches({ ...chord, inversion: 0 }, 0).map(p => mod(p - chord.root));
    const intervalAt = (offset: number) => mod(notes[(degree + offset) % 7] - notes[degree]);
    const seventh = intervalAt(6), ninth = intervalAt(1);
    const candidates: { chord: ProgressionChord; weight: number }[] = [];
    const add = (intervals: number[], weight: number) => {
      const unique = [...new Set(intervals)].sort((a,b) => a-b);
      if (new Set(unique.map(mod)).size <= new Set(triad).size) return;
      const spice = CHORD_SPICES.find(spice => {
        const pitches = chordPitches({ root: 0, spice, inversion: 0 }, 0);
        return pitches.length === unique.length && pitches.every((p,i) => p === unique[i]);
      });
      candidates.push({ chord: { ...chord, spice: spice ?? '', intervals: spice === undefined ? unique : undefined }, weight });
    };
    add([...triad, seventh], strategy === 'POP_LOOP' ? 2 : 6);
    // Pop favors stable added-sixth colors; other strategies admit richer diatonic ninths.
    if (triad.includes(7) && intervalAt(5) === 9) add([...triad, 9], strategy === 'POP_LOOP' ? 6 : 2);
    if (ninth === 2 && !triad.includes(1)) add([...triad, seventh, 14], strategy === 'POP_LOOP' ? 1 : 3);
    // Repeated-degree/pentatonic scales can collapse stacked thirds. Use a distinct
    // added mode tone so a selected spice slot still gains a real harmonic color.
    if (!candidates.length) {
      for (const offset of [1,3,5,6,2,4]) add([...triad, intervalAt(offset)], 1);
    }
    const next = chords[index + 1] ?? chords[0];
    // Applied dominants require an actual descending-fifth resolution to a major/minor
    // target. Modal harmony avoids importing tonal leading tones.
    const target = chordPitches({ ...next, inversion: 0 }, 0).map(p => mod(p - next.root));
    if (strategy !== 'MODAL' && mod(next.root - chord.root) === 5 && target.includes(7)
      && (target.includes(3) || target.includes(4))) {
      candidates.push({ chord: { ...chord, spice: '7', intervals: undefined },
        weight: strategy === 'FUNCTIONAL' || strategy === 'FIFTHS' ? 12 : 3 });
    }
    // A minor-major seventh is a tonic-minor color, not a default on every minor chord.
    if (strategy === 'FUNCTIONAL' && chord.root === tonic && triad.includes(3) && notes[6] === 11) {
      candidates.push({ chord: { ...chord, spice: 'mM7', intervals: undefined }, weight: 3 });
    }
    if (!candidates.length) return chord;
    let choice = random.nextInt(candidates.reduce((sum, candidate) => sum + candidate.weight, 0));
    return candidates.find(candidate => (choice -= candidate.weight) < 0)!.chord;
  });
}
