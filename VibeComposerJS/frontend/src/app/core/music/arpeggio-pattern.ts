import { JavaRandom } from './java-random';

export const ARPEGGIO_PATTERNS = ['up', 'down', 'up-down', 'down-up', 'frog-up', 'frog-down', 'random', 'custom'] as const;
export type ArpeggioPattern = typeof ARPEGGIO_PATTERNS[number];

/** Collections.shuffle's backwards Fisher-Yates loop, including its draw order. */
export function javaShuffle<T>(values: T[], random: JavaRandom): T[] {
  for (let size = values.length; size > 1; size--) {
    const index = random.nextInt(size);
    [values[size - 1], values[index]] = [values[index], values[size - 1]];
  }
  return values;
}

export function rotateLeft<T>(values: readonly T[], amount: number): T[] {
  if (!values.length) return [];
  const shift = ((amount % values.length) + values.length) % values.length;
  return [...values.slice(shift), ...values.slice(0, shift)];
}

/** ArpPattern.getPatternByLength rotates the doubled source before trimming and repeating. */
export function arpeggioPattern(pattern: ArpeggioPattern, hits: number, voices: number, repeat = 1,
  rotate = 0, custom: readonly number[] = [0, 1, 2], random?: JavaRandom): number[] {
  let source: number[];
  if (pattern === 'random') {
    source = javaShuffle(Array.from({ length: hits }, (_, index) => index % 32), random!);
    source = rotateLeft(source, rotate);
  } else if (pattern === 'custom') {
    source = [...custom];
    while (source.length < hits) source.push(...custom);
    source = rotateLeft(source, rotate).slice(0, hits);
  } else {
    const used = voices < 3 && pattern === 'frog-up' ? 'up' : voices < 3 && pattern === 'frog-down' ? 'down' : pattern;
    let current = used === 'down' || used === 'down-up' || used === 'frog-down' ? voices - 1 : 0;
    let adding = used === 'frog-up' ? 2 : used === 'frog-down' ? -2 : used === 'down-up' ? -1 : 1;
    source = Array.from({ length: hits * 2 }, () => {
      const value = current;
      if (used === 'up') current = current >= voices - 1 ? 0 : current + 1;
      else if (used === 'down') current = current === 0 ? voices - 1 : current - 1;
      else if (used === 'frog-up') {
        if (current === voices - 1 && adding === 2) current = 0;
        else { current += adding; adding = adding === 2 ? -1 : 2; }
      } else if (used === 'frog-down') {
        if (current < 2 && adding === -2) current = voices - 1;
        else { current = Math.max(0, Math.min(voices - 1, current + adding)); adding = adding === -2 ? 1 : -2; }
      } else {
        if (adding === 1 && current === voices - 1) adding = -1;
        else if (adding === -1 && current === 0) adding = 1;
        current += adding;
      }
      return value;
    });
    source = rotateLeft(source, rotate).slice(0, hits);
  }
  return Array.from({ length: repeat }, () => source).flat();
}

/** MidiUtils.getXthChordNote, including Java's negative division and wider voicings. */
export function chordNote(index: number, chord: readonly number[]): number {
  const pitch = chord[((index % chord.length) + chord.length) % chord.length];
  const adjustment = index >= chord.length ? 12 * Math.trunc(index / chord.length)
    : index < 0 ? -12 + 12 * Math.trunc(index / chord.length) : 0;
  const result = pitch + adjustment * (1 + Math.floor(Math.abs(chord[chord.length - 1] - chord[0]) / 12));
  // Java returns null outside the MIDI range; the event pipeline represents that as a rest.
  return result <= 0 || result >= 127 ? -1 : result;
}

export function closestMajorPitch(pitch: number): number {
  const scale = [0, 2, 4, 5, 7, 9, 11], pc = ((pitch % 12) + 12) % 12;
  let closest = 0, distance = pc;
  for (const candidate of scale.slice(1)) if (Math.abs(pc - candidate) < distance) {
    closest = candidate; distance = Math.abs(pc - candidate);
  }
  if (Math.abs(pc + 12 - 11) < distance) { closest = 11; distance = Math.abs(pc + 12 - 11); }
  if (Math.abs(pc - 12) < distance) closest = 0;
  return pitch - pc + closest;
}
