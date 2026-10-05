export type ScaleMode = 'major' | 'natural-minor';

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
