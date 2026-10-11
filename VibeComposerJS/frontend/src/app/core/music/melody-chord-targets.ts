import { chordPitches, getPitchClass, progressionChords, type ProgressionChord, type ScaleMode } from './harmony';
import { JavaRandom } from './java-random';
import type { MelodyNote } from './melody-expansion';

const MAJOR = [0, 2, 4, 5, 7, 9, 11], MINOR = [0, 2, 3, 5, 7, 8, 10];

/** Chordlets store absolute sounding roots; Melody repairs precede key/scale conversion.
 * Preserve voicing order (including inversion and repeated extensions) for Java's nearest-pitch ties.
 */
export function melodyRepairChords(key: string, scale: ScaleMode, degrees: readonly number[],
  custom?: readonly (ProgressionChord | null)[]): number[][] {
  const tonic = getPitchClass(key)!;
  return progressionChords(key, scale, degrees, custom).map(chord =>
    chordPitches({ ...chord, root: (chord.root - tonic + 12) % 12 }, 60).map(pitch => {
      const index = MINOR.indexOf(pitch % 12);
      return pitch + (scale === 'natural-minor' && index >= 0 ? MAJOR[index] - MINOR[index] : 0);
    }));
}

/** MelodyExpansion.applyChordNoteTargets: the production sorted copy is unused;
 * corrections visit original note order, including first notes restored later by pause/fill repair.
 */
export function applyMelodyChordTargets(byChord: readonly (readonly MelodyNote[])[],
  chords: readonly (readonly number[])[], target: number): void {
  if (target <= 0) return;
  const pitches = chords.map(chord => chord.map(pitch => pitch % 12));
  let found = 0, sounded = 0;
  byChord.forEach((notes, index) => {
    for (const note of notes) if (note.midi >= 0) {
      sounded++;
      if (pitches[index % pitches.length].includes(note.midi % 12)) found++;
    }
  });
  let missing = Math.ceil(sounded * target / 100) - found;
  for (let index = 0; index < byChord.length && missing > 0; index++) {
    let budget = Math.max(1, Math.trunc((missing + 4) / byChord.length));
    const chord = pitches[index % pitches.length];
    for (const note of byChord[index]) {
      if (note.midi < 0) continue;
      const pitch = note.midi % 12;
      if (!chord.includes(pitch)) {
        let closest = chord[0];
        for (const candidate of chord.slice(1)) if (Math.abs(candidate - pitch) < Math.abs(closest - pitch)) closest = candidate;
        note.midi += closest - pitch;
        missing--; budget--;
      }
      if (missing <= 0 || budget <= 0) break;
    }
  }
}

/** MelodyNoteUtils.replaceNearChordNotes: one seeded scale-step correction per eligible note. */
export function replaceMelodyAvoidNotes(byChord: readonly (readonly MelodyNote[])[],
  chords: readonly (readonly number[])[], seed: number, level: number): void {
  if (level <= 0) return;
  const random = new JavaRandom(seed);
  byChord.forEach((notes, index) => {
    const safe = new Set([0, 7]), avoid = new Set<number>();
    for (const pitch of chords[index % chords.length]) {
      safe.add(pitch % 12); avoid.add((pitch + 1) % 12);
      if (level >= 2) { avoid.add((pitch + 11) % 12); avoid.add((pitch + 2) % 12); }
    }
    for (const pitch of safe) avoid.delete(pitch);
    for (const note of notes) {
      if (note.midi < 0 || !avoid.has(note.midi % 12)) continue;
      const pitch = note.midi % 12, degree = MAJOR.indexOf(pitch), step = random.nextBoolean() ? 1 : -1;
      note.midi += MAJOR[(degree + step + 7) % 7] - pitch
        + (degree === 6 && step === 1 ? 12 : degree === 0 && step === -1 ? -12 : 0);
    }
  });
}
