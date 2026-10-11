import type { PhraseNote } from '../project/project.model';

/** Sixteenth onset pitches, with zero for an empty/rest slot. Independent of arrangement presence. */
export interface MelodyContext {
  readonly pitches: readonly (readonly number[])[];
  /** False when the source track is missing or its generated melody is disabled. */
  readonly available?: boolean;
}

/** MelodyPatternUtils.patternFromNotes: bucket onsets; later onsets in a bucket replace earlier ones. */
export function melodyContext(notes: readonly Pick<PhraseNote, 'midi' | 'startBeat'>[], chordCount: number, flip = false): MelodyContext {
  const pitches = Array.from({ length: chordCount }, () => Array<number>(16).fill(0));
  const populated = new Set<number>();
  for (const note of [...notes].sort((a, b) => a.startBeat - b.startBeat)) {
    if (note.startBeat < 0 || note.startBeat >= chordCount * 4) continue;
    const chord = Math.floor((note.startBeat + 1e-9) / 4);
    populated.add(chord);
    // Production buckets end 0.01 beat before each subdivision boundary.
    const slot = Math.floor((note.startBeat - chord * 4 + 0.01) / 0.25);
    if (pitches[chord] && slot >= 0 && slot < 16) pitches[chord][slot] = Math.max(0, note.midi);
  }
  // Production returns an empty chord grid before inversion when there are no source notes.
  if (flip) pitches.forEach((grid, chord) => {
    if (populated.has(chord)) grid.forEach((pitch, slot) => grid[slot] = pitch > 0 ? 0 : 1);
  });
  return { pitches };
}

export function melodyRhythm(context: MelodyContext | undefined, chordCount: number): number[] {
  return Array.from({ length: chordCount }, (_, chord) => context?.pitches[chord] ?? Array<number>(16).fill(0)).flat();
}
