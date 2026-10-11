export type ScaleMode = 'major' | 'natural-minor';

export const CHORD_SPICES = ['', 'm', 'maj7', 'm7', '7', 'mM7', 'sus2', 'sus4', 'sus7',
  'maj6', 'm6', 'maj9', 'm9', 'maj13', 'm13', 'aug', 'dim'] as const;
export type ChordSpice = typeof CHORD_SPICES[number];
export interface ProgressionChord { readonly root: number; readonly spice: ChordSpice; readonly inversion: number }
// Production MidiUtils spice intervals (including its identical maj9/m9 voicing).
const SPICE_INTERVALS: readonly (readonly number[])[] = [[0,4,7],[0,3,7],[0,4,7,11],[0,3,7,10],[0,4,7,10],
  [0,3,7,11],[0,2,7],[0,5,7],[0,5,7,10],[0,4,7,9],[0,3,7,9],[0,4,7,11,14],
  [0,4,7,11,14],[0,4,7,11,14,21],[0,3,7,10,14,21],[0,4,8],[0,3,6]];

export function validProgressionChords(value: unknown, count: number): value is readonly (ProgressionChord | null)[] | undefined {
  return value === undefined || Array.isArray(value) && value.length === count && value.every(chord => chord === null
    || chord && typeof chord === 'object' && !Array.isArray(chord)
      && Object.keys(chord).every(key => ['root','spice','inversion'].includes(key))
      && Number.isInteger(chord.root) && chord.root >= 0 && chord.root < 12
      && CHORD_SPICES.includes(chord.spice) && Number.isInteger(chord.inversion) && Math.abs(chord.inversion) <= 5);
}

export function progressionChords(key: string, scale: ScaleMode, degrees: readonly number[],
  custom?: readonly (ProgressionChord | null)[]): ProgressionChord[] {
  const diatonic = getDiatonicChords(key, scale);
  const offsets = scale === 'major' ? MAJOR : NATURAL_MINOR;
  return degrees.map((degree, index) => custom?.[index] ?? (() => {
    const chord = diatonic.find(chord => chord.degree === degree)!;
    return { root: (getPitchClass(key)! + offsets[degree - 1].offset) % 12,
      spice: chord.quality === 'minor' ? 'm' : chord.quality === 'diminished' ? 'dim' : '', inversion: 0 };
  })());
}

export function chordPitches(chord: ProgressionChord, octaveBase: number, open = false): number[] {
  const intervals = SPICE_INTERVALS[CHORD_SPICES.indexOf(chord.spice)];
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

export interface DiatonicChord {
  degree: number;
  romanNumeral: string;
  symbol: string;
  root: string;
  quality: 'major' | 'minor' | 'diminished';
}

const NOTE_LETTERS = ['C', 'D', 'E', 'F', 'G', 'A', 'B'];
const NATURAL_PITCHES = [0, 2, 4, 5, 7, 9, 11];
const KEY_PITCHES: Readonly<Record<string, number>> = {
  C: 0, 'C♯': 1, 'D♭': 1, D: 2, 'D♯': 3, 'E♭': 3, E: 4, F: 5,
  'F♯': 6, 'G♭': 6, G: 7, 'G♯': 8, 'A♭': 8, A: 9, 'A♯': 10, 'B♭': 10, B: 11,
};

export function getPitchClass(noteName: string): number | undefined {
  return KEY_PITCHES[noteName];
}

const MAJOR = [
  { offset: 0, roman: 'I', quality: 'major' },
  { offset: 2, roman: 'ii', quality: 'minor' },
  { offset: 4, roman: 'iii', quality: 'minor' },
  { offset: 5, roman: 'IV', quality: 'major' },
  { offset: 7, roman: 'V', quality: 'major' },
  { offset: 9, roman: 'vi', quality: 'minor' },
  { offset: 11, roman: 'vii°', quality: 'diminished' },
] as const;

const NATURAL_MINOR = [
  { offset: 0, roman: 'i', quality: 'minor' },
  { offset: 2, roman: 'ii°', quality: 'diminished' },
  { offset: 3, roman: 'III', quality: 'major' },
  { offset: 5, roman: 'iv', quality: 'minor' },
  { offset: 7, roman: 'v', quality: 'minor' },
  { offset: 8, roman: 'VI', quality: 'major' },
  { offset: 10, roman: 'VII', quality: 'major' },
] as const;

export const KEYS = ['C', 'C♯', 'D', 'E♭', 'E', 'F', 'F♯', 'G', 'A♭', 'A', 'B♭', 'B'];

export function getDiatonicChords(key: string, scale: ScaleMode): DiatonicChord[] {
  const tonic = KEY_PITCHES[key];
  if (tonic === undefined) {
    return [];
  }

  const degrees = scale === 'major' ? MAJOR : NATURAL_MINOR;
  const tonicLetter = NOTE_LETTERS.indexOf(key[0]);
  return degrees.map((degree, index) => {
    const letterIndex = (tonicLetter + index) % NOTE_LETTERS.length;
    const pitch = (tonic + degree.offset) % 12;
    const accidentalOffset = ((pitch - NATURAL_PITCHES[letterIndex] + 6) % 12) - 6;
    const accidental = accidentalOffset > 0
      ? '♯'.repeat(accidentalOffset)
      : '♭'.repeat(-accidentalOffset);
    const root = `${NOTE_LETTERS[letterIndex]}${accidental}`;
    const qualitySuffix = degree.quality === 'minor' ? 'm' : degree.quality === 'diminished' ? '°' : '';
    return {
      degree: index + 1,
      romanNumeral: degree.roman,
      symbol: `${root}${qualitySuffix}`,
      root,
      quality: degree.quality,
    };
  });
}
