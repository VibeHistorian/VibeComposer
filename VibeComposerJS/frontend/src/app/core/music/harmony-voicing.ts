import { JavaRandom } from './java-random';
import { getPitchClass, type ScaleMode } from './harmony';
import { convertScalePitch, scaleNotes } from './scales';

export interface HarmonySettings {
  readonly generation?: 'CURRENT' | 'FUNCTIONAL' | 'FIFTHS' | 'POP_LOOP' | 'MODAL' | 'RANDOM';
  readonly flattenVoicingChance?: number;
  readonly flattenLargeChords?: boolean;
  readonly progressiveVoicing?: boolean;
}

export const DEFAULT_HARMONY_SETTINGS = { generation: 'CURRENT', flattenVoicingChance: 100, flattenLargeChords: false, progressiveVoicing: false } as const;

export function decodeHarmonySettings(value: unknown): HarmonySettings | undefined {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
  const result: Record<string, number | boolean | string> = {};
  for (const [key, field] of Object.entries(value)) {
    if (key === 'generation') {
      if (!['CURRENT', 'FUNCTIONAL', 'FIFTHS', 'POP_LOOP', 'MODAL', 'RANDOM'].includes(field as string)) return undefined;
    } else if (key === 'flattenVoicingChance') {
      if (!Number.isInteger(field) || field < 0 || field > 100) return undefined;
    } else if (key === 'flattenLargeChords' || key === 'progressiveVoicing') {
      if (typeof field !== 'boolean') return undefined;
    } else return undefined;
    result[key] = field;
  }
  return result;
}

/** Canonical C/Ionian arrays stay independent: roots are never derived from voiced chords. */
export interface GenerationHarmony {
  readonly roots: readonly (readonly number[])[];
  readonly voicings: readonly (readonly number[])[];
  /** Preserve explicit sounding Chordlets where inverse scale conversion is ambiguous. */
  readonly soundingRoots?: readonly (readonly number[])[];
  readonly soundingVoicings?: readonly (readonly number[])[];
  readonly melodySeedOffset?: number;
}

/** Carry canonical octave moves onto the original sounding tones, preserving chromatic Chordlets. */
export function voicedSoundingChords(raw: readonly (readonly number[])[], voiced: readonly (readonly number[])[],
  sounding: readonly (readonly number[])[]): number[][] {
  return voiced.map((chord,index) => {
    const used = new Set<number>();
    return chord.map(pitch => {
      const candidates = raw[index].map((original,tone) => ({ original,tone }))
        .filter(candidate => !used.has(candidate.tone) && (candidate.original - pitch) % 12 === 0)
        .sort((a,b) => Math.abs(a.original - pitch) - Math.abs(b.original - pitch));
      const candidate = candidates[0];
      used.add(candidate.tone);
      return sounding[index][candidate.tone] + pitch - candidate.original;
    }).sort((a,b) => a-b);
  });
}

/** MidiUtils squish passes, including skipped inversion draws and the average epsilon. */
export function flattenProgression(chords: readonly (readonly number[])[], roots: readonly (readonly number[])[],
  seed: bigint | number, settings: HarmonySettings = {}, inversions: readonly number[] = []): number[][] {
  if (!decodeHarmonySettings(settings) || chords.length !== roots.length || !chords.length
    || chords.some(chord => !chord.length || chord.some(pitch => !Number.isInteger(pitch)))
    || roots.some(chord => !chord.length || chord.some(pitch => !Number.isInteger(pitch)))
    || inversions.some(index => !Number.isInteger(index) || index < 0 || index >= chords.length)) throw new RangeError('Invalid harmony voicing inputs.');
  const options = { ...DEFAULT_HARMONY_SETTINGS, ...settings }, random = new JavaRandom(seed);
  const ignored = new Set(inversions);
  const average = (values: readonly (readonly number[])[]) => {
    let sum = 0, count = 0.001;
    values.forEach((chord, index) => { const notes = ignored.has(index) ? roots[index] : chord;
      count += notes.length; sum += notes.reduce((total, note) => total + note, 0); });
    return sum / count;
  };
  let mean = average(options.progressiveVoicing ? chords.slice(0, 1) : chords);
  const result: number[][] = [];
  chords.forEach((original, index) => {
    const chord = [...original];
    if (!ignored.has(index)) {
      if (options.progressiveVoicing) chord.sort((a,b) => a-b);
      if (random.nextInt(100) < options.flattenVoicingChance && (chord.length <= 3 || options.flattenLargeChords)) {
        if (mean - chord[0] > 6) chord[0] += 12;
        if (chord[chord.length - 1] - mean > 6) chord[chord.length - 1] -= 12;
      }
      chord.sort((a,b) => a-b);
    }
    result.push(chord);
    if (options.progressiveVoicing) mean = average(result);
  });
  return result;
}

/** Convert canonical pitches after voicing; part transpose remains a separate operation. */
export function soundingHarmony(chords: readonly (readonly number[])[], key: string, scale: ScaleMode, octaveOffset = 0): number[][] {
  const major = scaleNotes('major'), tonic = getPitchClass(key)!;
  return chords.map(chord => chord.map(pitch => {
    return convertScalePitch(pitch, major, scaleNotes(scale)) + tonic + octaveOffset;
  }));
}
