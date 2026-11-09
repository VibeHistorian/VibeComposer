import { harmonyScaleNotes, scaleNotes, type ScaleMode } from './scales';
export { SCALE_DEFINITIONS, SCALE_MODES, isScaleMode, scaleNotes, type ScaleMode } from './scales';

export const CHORD_SPICES = ['', 'm', 'maj7', 'm7', '7', 'mM7', 'sus2', 'sus4', 'sus7',
  'maj6', 'm6', 'maj9', 'm9', 'maj13', 'm13', 'aug', 'dim',
  'add9', 'add11', '6/9', 'maj7#11', 'maj9#11',
  'madd9', 'madd11', 'm6/9', 'm11', 'mM9', 'm7b5',
  '9', '11', '13', '7b5', '7#5', '7b9', '7#9', '7#11', '7b13', '9sus4', 'dim7'] as const;
export type ChordSpice = typeof CHORD_SPICES[number];
export const CHORD_SPICE_GROUPS = [
  { label: 'good', spices: ['', 'maj6', 'maj7', 'maj9', 'maj13', 'add9', 'add11', '6/9', 'maj7#11', 'maj9#11'] },
  { label: 'bad', spices: ['m', 'm6', 'm7', 'm9', 'm11', 'm13', 'madd9', 'madd11', 'm6/9', 'mM7', 'mM9', 'm7b5'] },
  { label: 'ugly', spices: ['sus2', 'sus4', 'aug', 'dim', 'dim7'] },
  { label: 'dom', spices: ['7', '9', '11', '13', '7b5', '7#5', '7b9', '7#9', '7#11', '7b13', 'sus7', '9sus4'] },
] as const satisfies readonly { label: string; spices: readonly ChordSpice[] }[];
export const SCALE_INTERVALS = [2, 3, 4, 5, 6, 7] as const;

/** Resolve a manual dyad in the current scale. Chromatic roots borrow the nearest
 * scale degree's interval shape. Repeated experimental degrees stay below an octave. */
export function scaleIntervalChord(chord: ProgressionChord, degree: typeof SCALE_INTERVALS[number],
  key: string, scale: ScaleMode): ProgressionChord {
  const notes = scaleNotes(scale), offset = (chord.root - getPitchClass(key)! + 12) % 12;
  let anchor = 0;
  const distance = (pitch: number) => Math.min((pitch - offset + 12) % 12, (offset - pitch + 12) % 12);
  for (let i = 1; i < notes.length; i++) if (distance(notes[i]) < distance(notes[anchor])) anchor = i;
  const target = anchor + degree - 1;
  const interval = notes[target % notes.length] + 12 * Math.floor(target / notes.length) - notes[anchor];
  // Some experimental scales repeat degrees or include the octave as degree VII.
  const distinct = notes.map(pitch => (pitch - notes[anchor] + 12) % 12).filter(pitch => pitch > 0);
  const resolved = interval > 0 && interval < 12 ? interval
    : interval <= 0 ? Math.min(...distinct) : Math.max(...distinct);
  return { ...chord, spice: '', inversion: 0, intervals: [0, resolved] };
}
export interface ProgressionChord { readonly root: number; readonly spice: ChordSpice; readonly inversion: number;
  /** Distinguish a protected root-position '.0' from automatic voicing. */
  readonly explicitZeroInversion?: true;
  /** Exact voicing for manual intervals and mode-derived chords without a named spice. */
  readonly intervals?: readonly number[];
}
// Root-position formulas in semitones. Thirteenths omit the optional eleventh;
// dominant 11 omits the third, as in a ninth suspended-fourth voicing.
// Added tones have no seventh; m7b5 is half-diminished, dim7 fully diminished.
const SPICE_FORMULAS: Record<ChordSpice, readonly number[]> = {
  '': [0,4,7], m: [0,3,7], maj7: [0,4,7,11], m7: [0,3,7,10], '7': [0,4,7,10],
  mM7: [0,3,7,11], sus2: [0,2,7], sus4: [0,5,7], sus7: [0,5,7,10],
  maj6: [0,4,7,9], m6: [0,3,7,9], maj9: [0,4,7,11,14], m9: [0,3,7,10,14],
  maj13: [0,4,7,11,14,21], m13: [0,3,7,10,14,21], aug: [0,4,8], dim: [0,3,6],
  add9: [0,4,7,14], add11: [0,4,7,17], '6/9': [0,4,7,9,14],
  'maj7#11': [0,4,7,11,18], 'maj9#11': [0,4,7,11,14,18],
  madd9: [0,3,7,14], madd11: [0,3,7,17], 'm6/9': [0,3,7,9,14],
  m11: [0,3,7,10,14,17], mM9: [0,3,7,11,14], m7b5: [0,3,6,10],
  '9': [0,4,7,10,14], '11': [0,7,10,14,17], '13': [0,4,7,10,14,21],
  '7b5': [0,4,6,10], '7#5': [0,4,8,10], '7b9': [0,4,7,10,13],
  '7#9': [0,4,7,10,15], '7#11': [0,4,7,10,18], '7b13': [0,4,7,10,20],
  '9sus4': [0,5,7,10,14], dim7: [0,3,6,9],
};

export function validProgressionChords(value: unknown, count: number): value is readonly (ProgressionChord | null)[] | undefined {
  return value === undefined || Array.isArray(value) && value.length === count && value.every(chord => chord === null
    || chord && typeof chord === 'object' && !Array.isArray(chord)
      && Object.keys(chord).every(key => ['root','spice','inversion','intervals','explicitZeroInversion'].includes(key))
      && Number.isInteger(chord.root) && chord.root >= 0 && chord.root < 12
      && CHORD_SPICES.includes(chord.spice) && Number.isInteger(chord.inversion) && Math.abs(chord.inversion) <= 5
      && (chord.explicitZeroInversion === undefined || chord.explicitZeroInversion === true && chord.inversion === 0)
      && (chord.intervals === undefined || Array.isArray(chord.intervals) && chord.intervals.length >= 1 && chord.intervals.length <= 7
        && chord.intervals[0] === 0 && chord.intervals.every((n: unknown) => Number.isInteger(n) && (n as number) >= 0 && (n as number) <= 36)));
}

export function inversionSuffix(chord: ProgressionChord): string {
  return chord.inversion || chord.explicitZeroInversion ? `.${chord.inversion}` : '';
}

/** Wheel order: -5 … -1, automatic, explicit 0 … 5; overflow returns to automatic. */
export function stepChordInversion(chord: ProgressionChord, movement: number): ProgressionChord {
  const { explicitZeroInversion, ...base } = chord;
  const position = chord.inversion > 0 || explicitZeroInversion ? chord.inversion + 1 : chord.inversion;
  const next = position + movement;
  if (next < -5 || next > 6 || next === 0) return { ...base, inversion: 0 };
  return next === 1 ? { ...base, inversion: 0, explicitZeroInversion: true }
    : { ...base, inversion: next > 1 ? next - 1 : next };
}

export function progressionChords(key: string, scale: ScaleMode, degrees: readonly number[],
  custom?: readonly (ProgressionChord | null)[]): ProgressionChord[] {
  const diatonic = getDiatonicChords(key, scale);
  return degrees.map((degree, index) => custom?.[index] ?? (() => {
    const chord = diatonic.find(chord => chord.degree === degree)!;
    const intervals = chord.pitches.map(pitch => pitch - chord.pitches[0]);
    const spice = CHORD_SPICES.find(spice => SPICE_FORMULAS[spice].length === intervals.length
      && SPICE_FORMULAS[spice].every((n,i) => n === intervals[i]));
    return { root: getPitchClass(chord.root)!, spice: spice ?? '', inversion: 0,
      ...(spice === undefined ? { intervals } : {}) };
  })());
}

export function chordPitches(chord: ProgressionChord, octaveBase: number, open = false): number[] {
  const intervals = chord.intervals ?? SPICE_FORMULAS[chord.spice];
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

/** Key-relative Roman function, with case and symbols taken from the actual triad. */
export function chordRomanNumeral(chord: ProgressionChord, key: string): string {
  const offset = ((chord.root - (getPitchClass(key) ?? 0)) % 12 + 12) % 12;
  const numeral = ['I', '♭II', 'II', '♭III', 'III', 'IV', '♯IV', 'V', '♭VI', 'VI', '♭VII', 'VII'][offset];
  const intervals = (chord.intervals ?? SPICE_FORMULAS[chord.spice]).map(note => note % 12);
  const minor = intervals.includes(3) && !intervals.includes(4);
  const diminished = minor && intervals.includes(6) && !intervals.includes(7);
  const augmented = intervals.includes(4) && intervals.includes(8) && !intervals.includes(7);
  return (minor ? numeral.toLowerCase() : numeral) + (diminished ? '°' : augmented ? '+' : '');
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

  const notes = harmonyScaleNotes(scale);
  const romans = ['I','II','III','IV','V','VI','VII'];
  const tonicLetter = NOTE_LETTERS.indexOf(key[0]);
  return notes.map((offset, index) => {
    const letterIndex = (tonicLetter + index) % NOTE_LETTERS.length;
    const pitch = (tonic + offset) % 12;
    const accidentalOffset = ((pitch - NATURAL_PITCHES[letterIndex] + 6) % 12) - 6;
    const accidental = accidentalOffset > 0
      ? '♯'.repeat(accidentalOffset)
      : '♭'.repeat(-accidentalOffset);
    const root = scaleNotes(scale).length === 7 ? `${NOTE_LETTERS[letterIndex]}${accidental}` : KEYS[pitch];
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
