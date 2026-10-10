import { JavaRandom } from './java-random';

export const STRUM_TYPES = ['ARP_U', 'ARP_D', 'RAND_U', 'RAND', 'RAND_D', 'RAND_WU', 'HUMAN_U', 'HUMAN', 'HUMAN_D'] as const;
export type StrumType = typeof STRUM_TYPES[number];
export interface ChordTone { readonly midi: number; readonly offset: number }

/** Java's special fractional timing choices are thousandths of a beat, not milliseconds. */
export function strumBeatAmount(amount: number): number {
  return amount === 666 ? 0.6666666666666 : amount === 333 ? 0.3333333333333
    : amount === 31 ? 0.03125 : amount === 62 ? 0.0625 : amount / 1000;
}

/** Chord.makeAndStoreNotesBackwards followed by StrumType.adjustNoteOffsets.
 * Include rest/zero-velocity draws before dropping silent tones; keep the first anchor.
 */
export function strumChord(pitches: readonly number[], random: JavaRandom, type: StrumType,
  amount: number, pauseChance: number): ChordTone[] {
  const notes = [...pitches].reverse().map(midi => ({ midi: random.nextInt(100) < pauseChance ? -1 : midi, offset: 0 }));
  const flam = strumBeatAmount(amount);
  const offsets = notes.map((_, index) => type.startsWith('ARP') ? flam * index
    : type === 'RAND_WU' ? index * flam + random.nextDouble() * flam
    : random.nextDouble() * flam * (type.startsWith('RAND') ? notes.length : 1));
  const sort = ['RAND_U', 'RAND_D', 'RAND_WU', 'HUMAN_U', 'HUMAN_D'].includes(type);
  const reverse = ['ARP_U', 'RAND_U', 'RAND_WU', 'HUMAN_U'].includes(type);
  if (sort) offsets.sort((a, b) => a - b);
  if (reverse) offsets.reverse();
  notes.forEach((note, index) => note.offset = offsets[index]);
  notes[type === 'RAND' ? random.nextInt(notes.length) : reverse ? notes.length - 1 : 0].offset = 0;
  return notes;
}
