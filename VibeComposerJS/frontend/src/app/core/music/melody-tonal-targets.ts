import type { MelodyNote } from './melody-expansion';
import type { ScaleMode } from './harmony';
import { modeTargetPitch } from './scales';

/** MelodyExpansion's tonic and mode targets run before interval repair, pauses and mode conversion.
 * Mutates only the freshly expanded generation notes; all historical/project values remain immutable.
 */
export function applyMelodyTonalTargets(byChord: readonly (readonly MelodyNote[])[], scale: ScaleMode,
  tonicTarget: number, modeTarget: number, notesSeedOffset: number): void {
  const all = byChord.flat(), counts = Array<number>(12).fill(0);
  for (const note of all) if (note.midi >= 0) counts[note.midi % 12]++;
  const sounded = all.filter(note => note.midi >= 0).length;
  let surplus = counts[0] - Math.floor(sounded * tonicTarget / 100);
  if (tonicTarget > 0 && notesSeedOffset === 0 && surplus < 0) {
    // Visit alternating chords from the end, then the other parity; protect each chord's first note.
    for (const last of [byChord.length - 1, byChord.length - 2]) {
      for (let chord = last; chord >= 0 && surplus < 0; chord -= 2) {
        const notes = byChord[chord];
        for (let index = notes.length - 1; index > 0; index--) {
          const note = notes[index];
          if (note.midi < 0) continue;
          if (note.midi % 12 === 2) { note.midi -= 2; surplus++; break; }
          if (note.midi % 12 === 11) { note.midi++; surplus++; break; }
        }
      }
    }
  }
  if (modeTarget <= 0) return;
  const modePitch = modeTargetPitch(scale);
  if (modePitch < 0) return;
  // Production retains the original histogram after tonic repair; existing mode notes are candidates too.
  let missing = Math.ceil(sounded * modeTarget / 100) - counts[modePitch];
  if (missing <= 0) return;
  const distance = (pitch: number) => {
    const difference = Math.abs(pitch % 12 - modePitch);
    return Math.min(difference, Math.abs(pitch % 12 + 12 - modePitch), Math.abs(pitch % 12 - 12 - modePitch));
  };
  const candidates = byChord.flatMap(notes => notes.slice(1)).sort((left, right) => distance(left.midi) - distance(right.midi));
  for (const note of candidates) {
    if (note.midi < 0) continue;
    const pitch = note.midi % 12, jump = modePitch - pitch;
    note.midi = note.midi - pitch + modePitch + (jump > 6 ? -12 : jump < -6 ? 12 : 0);
    if (--missing <= 0) break;
  }
}
