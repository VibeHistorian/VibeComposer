import { scaleNotes, type ScaleMode } from './scales';
export { SCALE_DEFINITIONS, SCALE_MODES, isScaleMode, scaleNotes, type ScaleMode } from './scales';

export const CHORD_SPICES = ['', 'm', 'maj7', 'm7', '7', 'mM7', 'sus2', 'sus4', 'sus7',
  'maj6', 'm6', 'maj9', 'm9', 'maj13', 'm13', 'aug', 'dim'] as const;
export type ChordSpice = typeof CHORD_SPICES[number];
export interface ProgressionChord { readonly root: number; readonly spice: ChordSpice; readonly inversion: number;
  /** Exact mode-derived voicing when it cannot be expressed by a named chord spice. */
  readonly intervals?: readonly number[];
}
// Production MidiUtils spice intervals (including its identical maj9/m9 voicing).
const SPICE_INTERVALS: readonly (readonly number[])[] = [[0,4,7],[0,3,7],[0,4,7,11],[0,3,7,10],[0,4,7,10],
  [0,3,7,11],[0,2,7],[0,5,7],[0,5,7,10],[0,4,7,9],[0,3,7,9],[0,4,7,11,14],
  [0,4,7,11,14],[0,4,7,11,14,21],[0,3,7,10,14,21],[0,4,8],[0,3,6]];

export function validProgressionChords(value: unknown, count: number): value is readonly (ProgressionChord | null)[] | undefined {
  return value === undefined || Array.isArray(value) && value.length === count && value.every(chord => chord === null
    || chord && typeof chord === 'object' && !Array.isArray(chord)
      && Object.keys(chord).every(key => ['root','spice','inversion','intervals'].includes(key))
      && Number.isInteger(chord.root) && chord.root >= 0 && chord.root < 12
      && CHORD_SPICES.includes(chord.spice) && Number.isInteger(chord.inversion) && Math.abs(chord.inversion) <= 5
      && (chord.intervals === undefined || Array.isArray(chord.intervals) && chord.intervals.length >= 1 && chord.intervals.length <= 7
        && chord.intervals[0] === 0 && chord.intervals.every((n: unknown) => Number.isInteger(n) && (n as number) >= 0 && (n as number) <= 36)));
}

export function progressionChords(key: string, scale: ScaleMode, degrees: readonly number[],
  custom?: readonly (ProgressionChord | null)[]): ProgressionChord[] {
  const diatonic = getDiatonicChords(key, scale);
  return degrees.map((degree, index) => custom?.[index] ?? (() => {
    const chord = diatonic.find(chord => chord.degree === degree)!;
    const intervals = chord.pitches.map(pitch => pitch - chord.pitches[0]);
    const spiceIndex = SPICE_INTERVALS.findIndex(candidate => candidate.length === intervals.length && candidate.every((n,i) => n === intervals[i]));
    return { root: getPitchClass(chord.root)!, spice: spiceIndex < 0 ? '' : CHORD_SPICES[spiceIndex], inversion: 0,
      ...(spiceIndex < 0 ? { intervals } : {}) };
  })());
}

export function chordPitches(chord: ProgressionChord, octaveBase: number, open = false): number[] {
  const intervals = chord.intervals ?? SPICE_INTERVALS[CHORD_SPICES.indexOf(chord.spice)];
  const notes = intervals.map(interval => octaveBase + chord.root + interval);
  if (open && notes.length === 3) { notes[1] += 12; notes.sort((a,b) => a-b); }
  const change = (chord.inversion + notes.length * 100) % notes.length;
  const minimum = 12 * Math.floor(Math.abs(chord.inversion) / notes.length);
  const maximum = change === 0 ? minimum : minimum + 12;
  return notes.map((note, index) => note + (chord.inversion < 0
    ? -(index >= change ? maximum : minimum) : index >= change ? minimum : maximum)).sort((a,b) => a-b);
}

export function effectiveKey(key: string, transpose: number): string {
  return KEYS[((getPitchClass(key) ?? 0) + transpose % 12 + 12) % 12];
}

/** Keep root colors tied to their position in the current key across transposition. */
export function chordRootHue(root: number, key: string): number {
  return ((root - (getPitchClass(key) ?? 0)) % 12 + 12) % 12 * 30;
}

/** Use the actual voicing intervals, counting each pitch class once. */
export function chordKeyOverlap(chord: ProgressionChord, key: string, scale: ScaleMode): { matching: number; total: number; ratio: number } {
  const tonic = getPitchClass(key)!;
  const notesInKey = new Set(scaleNotes(scale).map(note => (note + tonic) % 12));
  const notes = new Set(chordPitches(chord, 0).map(note => (note % 12 + 12) % 12));
  const matching = [...notes].filter(note => notesInKey.has(note)).length;
  return { matching, total: notes.size, ratio: matching / notes.size };
}

export interface DiatonicChord {
  degree: number;
  romanNumeral: string;
  symbol: string;
  root: string;
  quality: 'major' | 'minor' | 'diminished' | 'augmented' | 'other';
  pitches: readonly number[];
}

const NOTE_LETTERS = ['C', 'D', 'E', 'F', 'G', 'A', 'B'];
const NATURAL_PITCHES = [0, 2, 4, 5, 7, 9, 11];
const KEY_PITCHES: Readonly<Record<string, number>> = {
  C: 0, 'C♯': 1, 'D♭': 1, D: 2, 'D♯': 3, 'E♭': 3, E: 4, F: 5,
  'F♯': 6, 'G♭': 6, G: 7, 'G♯': 8, 'A♭': 8, A: 9, 'A♯': 10, 'B♭': 10, B: 11,
};

export function getPitchClass(noteName: string): number | undefined {
  const known = KEY_PITCHES[noteName];
  if (known !== undefined) return known;
  // Diatonic spelling can produce E♯, C♭ or double accidentals in supported keys.
  const match = /^([A-G])(♯*|♭*)$/.exec(noteName);
  if (!match) return undefined;
  const natural = NATURAL_PITCHES[NOTE_LETTERS.indexOf(match[1])];
  const offset = match[2].startsWith('♯') ? match[2].length : -match[2].length;
  return (natural + offset % 12 + 12) % 12;
}

export const KEYS = ['C', 'C♯', 'D', 'E♭', 'E', 'F', 'F♯', 'G', 'A♭', 'A', 'B♭', 'B'];

export function getDiatonicChords(key: string, scale: ScaleMode): DiatonicChord[] {
  const tonic = KEY_PITCHES[key];
  if (tonic === undefined) {
    return [];
  }

  const notes = scaleNotes(scale);
  const romans = ['I','II','III','IV','V','VI','VII'];
  const tonicLetter = NOTE_LETTERS.indexOf(key[0]);
  return notes.map((offset, index) => {
    const letterIndex = (tonicLetter + index) % NOTE_LETTERS.length;
    const pitch = (tonic + offset) % 12;
    const accidentalOffset = ((pitch - NATURAL_PITCHES[letterIndex] + 6) % 12) - 6;
    const accidental = accidentalOffset > 0
      ? '♯'.repeat(accidentalOffset)
      : '♭'.repeat(-accidentalOffset);
    const root = `${NOTE_LETTERS[letterIndex]}${accidental}`;
    const pitches = [0,2,4].map(step => 60 + tonic + notes[(index + step) % 7] + 12 * Math.floor((index + step) / 7));
    const intervals = pitches.map(note => note - pitches[0]);
    const quality = intervals[1] === 4 && intervals[2] === 7 ? 'major'
      : intervals[1] === 3 && intervals[2] === 7 ? 'minor'
      : intervals[1] === 3 && intervals[2] === 6 ? 'diminished'
      : intervals[1] === 4 && intervals[2] === 8 ? 'augmented' : 'other';
    const qualitySuffix = quality === 'minor' ? 'm' : quality === 'diminished' ? '°' : quality === 'augmented' ? '+'
      : quality === 'other' ? `[${intervals.join(',')}]` : '';
    return {
      degree: index + 1,
      romanNumeral: (quality === 'minor' || quality === 'diminished' ? romans[index].toLowerCase() : romans[index])
        + (quality === 'diminished' ? '°' : quality === 'augmented' ? '+' : ''),
      symbol: `${root}${qualitySuffix}`,
      root,
      quality, pitches: pitches.map(note => note - 12 * Math.floor((pitches[0] - 60) / 12)),
    };
  });
}
