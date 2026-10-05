import { JavaRandom } from './java-random';

// This is the degree form of MidiUtils.createChordProgressionRulesMap().
// The legacy generator walks these rules backwards, then reverses the result.
const BACKWARD_TRANSITIONS: Readonly<Record<number, readonly number[]>> = {
  1: [4, 5],
  2: [6],
  3: [6, 5],
  4: [1, 2, 6],
  5: [1, 2, 3, 4, 6],
  6: [1, 2, 3, 5],
  7: [1, 3, 4],
};

const STARTING_DEGREES = [1, 4, 5, 6] as const;

/**
 * Generate a diatonic progression using the legacy chord-transition rules.
 * Random choices use the same java.util.Random stream and bounded integer
 * behavior as the Java generator. This degree-only model keeps chord roots
 * diatonic; the legacy spicy voicing pass is outside its current data shape.
 */
export function generateDiatonicProgression(seed: bigint | number, length: number): number[] {
  if (!Number.isInteger(length) || length < 1 || length > 32) {
    throw new RangeError('Progression length must be an integer from 1 through 32.');
  }

  const progressionRandom = new JavaRandom(seed);
  const repeatRandom = new JavaRandom(seed);
  const reversedProgression: number[] = [];
  let nextDegrees: readonly number[] = STARTING_DEGREES;
  let canRepeatFirstChord = true;
  let lastUnspicedDegree: number | undefined;
  let mirroredLastDegree: number | undefined;

  for (let index = 0; index < length; index++) {
    const selectedDegree = nextDegrees[progressionRandom.nextInt(nextDegrees.length)];
    // The Java generator checks its spice chance after choosing each chord.
    // Consume that draw here to preserve the following progression choices.
    progressionRandom.nextInt(100);

    const isLastChord = index === length - 1;
    let degree: number;
    if (isLastChord && mirroredLastDegree !== undefined) {
      degree = mirroredLastDegree;
    } else if ((length < 8 || !isLastChord) && canRepeatFirstChord && reversedProgression.length === 1
        && repeatRandom.nextInt(100) < 10) {
      degree = lastUnspicedDegree!;
      canRepeatFirstChord = false;
    } else {
      degree = selectedDegree;
    }

    reversedProgression.push(degree);
    nextDegrees = BACKWARD_TRANSITIONS[degree];
    lastUnspicedDegree = degree;

    // The legacy eight chord form repeats the fourth chord at the end.
    if (length === 8 && reversedProgression.length === 4) {
      mirroredLastDegree = degree;
    }
  }

  return reversedProgression.reverse();
}
