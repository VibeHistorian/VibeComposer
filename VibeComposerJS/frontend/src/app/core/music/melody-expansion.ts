import type { MelodySettings } from '../project/project.model';
import { JavaRandom } from './java-random';
import { melodyInt as int, MELODY_EPSILON as EPS } from './melody-blocks';

const MAJOR = [0, 2, 4, 5, 7, 9, 11];
export interface MelodyNote { midi: number; rhythm: number; duration: number; velocity: number; offset: number }

/** MelodyNoteUtils.getAllowedPitchFromRange; Java's normalized range can wrap an octave. */
function allowedPitch(minimum: number, maximum: number, position: number, random: JavaRandom): number {
  const low = minimum % 12;
  let high = maximum % 12;
  if (high <= low) high += 12;
  const pitches = [...MAJOR, ...MAJOR.map(pitch => pitch + 12)]
    .filter(pitch => pitch >= low && pitch <= high && (position > 0.66 || (pitch !== 11 && pitch !== 23)));
  let pitch = pitches[random.nextInt(pitches.length)];
  while (pitch < minimum) pitch += 12;
  return pitch;
}

/** MelodyExpansion.expandSkeletonNotes: dynamics are drawn once per original skeleton note. */
export function expandMelody(skeleton: readonly MelodyNote[][], settings: MelodySettings,
  seed: number, notesSeedOffset: number, minimum: number, maximum: number): MelodyNote[][] {
  const orderSeed = int(seed + 1);
  const splitRandom = new JavaRandom(int(orderSeed + 4));
  let pitchRandom = new JavaRandom(int(seed + 8));
  let exceptionRandom = new JavaRandom(int(seed + 9));
  const leadingRandom = new JavaRandom(int(orderSeed + notesSeedOffset + 15));
  const velocityRandom = new JavaRandom(int(orderSeed + 1 + notesSeedOffset));
  const accentRandom = new JavaRandom(int(orderSeed + 20));
  const all = skeleton.flat(), result = skeleton.map(() => [] as MelodyNote[]);
  const accent = settings.accents ?? 100;
  let index = 0;
  skeleton.forEach((notes, chord) => {
    if (chord > 0) { pitchRandom = new JavaRandom(int(orderSeed + 8)); exceptionRandom = new JavaRandom(int(orderSeed + 9)); }
    let time = 0;
    for (const note of notes) {
      const duration = note.rhythm, position = time / 4;
      let velocity = velocityRandom.nextInt(Math.max(1, maximum - minimum)) + minimum;
      if (position < EPS && accentRandom.nextInt(100) < accent) {
        velocity = Math.min(127, velocity + 15 + accentRandom.nextInt(11) - 5 + Math.trunc(accent / 20));
      }
      note.velocity = velocity;
      time += duration;
      const next = all[(index + 1) % all.length];
      // The leading draw occurs even when disabled; split draws short-circuit on duration.
      const leading = leadingRandom.nextInt(100) < (settings.leadChordsChance ?? 0)
        && duration > 0.375 * 1.1 && index < all.length - 1 && time + next.rhythm > 4;
      const split = (duration > 0.5 * 1.4 && splitRandom.nextInt(100) < (settings.splitChance ?? 0)) || leading;
      if (split) {
        let low = Math.min(note.midi, next.midi), high = Math.max(note.midi, next.midi);
        if (exceptionRandom.nextInt(100) < 33 && !leading) {
          if (note.midi >= next.midi) high += 4; else low -= 4;
        }
        const pitch = allowedPitch(low, high, position, pitchRandom);
        const dotted = [1.5, 6, 3, 0.75, 0.375].some(value => Math.abs(value - duration) < EPS);
        const multiplier = dotted && splitRandom.nextBoolean() ? 1 / 3 : 0.5;
        const rhythm = duration * multiplier;
        // jMusic Note's constructor duration is 90% of rhythm; swing can replace it later.
        const add = (midi: number, velocity: number) => result[chord].push({ midi, rhythm,
          duration: rhythm * 0.9, velocity: Math.max(0, velocity), offset: 0 });
        add(note.midi, velocity); add(pitch, velocity - 10);
        if (multiplier < 0.4) add(splitRandom.nextBoolean() ? note.midi : pitch, velocity - 20);
      } else result[chord].push(note);
      index++;
    }
  });
  return result;
}

/** MelodyExpansion.finalizeExpandedMelody, after pause/fill and first-note accent repair. */
export function delayMelodyStarts(chords: readonly MelodyNote[][], settings: MelodySettings, seed: number): void {
  const random = new JavaRandom(int(seed + 26));
  for (const notes of chords) {
    const note = notes[0];
    if (!note) continue;
    const preferred = [0.25, 0.5, 0.75][random.nextInt(3)];
    if (note.midi < 0 || random.nextInt(100) < (settings.startNoteChance ?? 100)) continue;
    const sixteenths = Math.floor(note.duration / 0.25);
    if (sixteenths < 2) note.midi = -1;
    else {
      const delay = sixteenths === 2 ? 0.25 : Math.min(preferred, sixteenths <= 4 ? 0.5 : 0.75);
      note.offset += delay; note.duration -= delay;
    }
  }
}
