import { getDiatonicChords, getPitchClass } from './harmony';
import type { ScaleMode } from './harmony';
import { JavaRandom } from './java-random';
import type { BassRhythm } from '../project/project.model';

export interface BassNoteEvent {
  readonly midi: number;
  readonly startBeat: number;
  readonly durationBeats: number;
  readonly velocity: number;
  readonly chordIndex: number;
}

const BEATS_PER_CHORD = 4;
const SIXTEENTH = 0.25;
const EIGHTH = 0.5;
const QUARTER = 1;
const DURATION_POOL = [SIXTEENTH, EIGHTH, QUARTER, 1.5, 2, 2.5, 3, 4] as const;
const DURATION_WEIGHTS = [5, 25, 45, 55, 75, 85, 95, 100] as const;
const RHYTHM_PATTERNS: Readonly<Record<Exclude<BassRhythm, 'alternating'>, readonly number[]>> = {
  full: [1, 1, 1, 1, 1, 1, 1, 1],
  half: [1, 0, 1, 0, 1, 0, 1, 0],
  tresillo: [1, 0, 0, 1, 0, 0, 1, 0],
  sparse: [1, 0, 0, 0, 1, 0, 0, 0],
};

/** Build a bass phrase from the current diatonic roots and serialized settings. */
export function generateBassline(
  seed: bigint | number,
  key: string,
  scale: ScaleMode,
  progression: readonly number[],
  rhythm: BassRhythm,
  noteVariation: number,
): BassNoteEvent[] {
  if (progression.length < 1 || progression.length > 32
      || !Number.isInteger(noteVariation) || noteVariation < 0 || noteVariation > 100
      || !['alternating', 'full', 'half', 'tresillo', 'sparse'].includes(rhythm)) {
    throw new RangeError('Bass generation settings are outside the supported range.');
  }

  const chords = getDiatonicChords(key, scale);
  const triads = progression.map((degree) => {
    const chord = chords.find((candidate) => candidate.degree === degree);
    if (!chord) {
      throw new RangeError(`Chord degree ${degree} is not available in ${key} ${scale}.`);
    }
    const root = getPitchClass(chord.root);
    if (root === undefined) {
      throw new RangeError(`Unknown chord root: ${chord.root}.`);
    }
    const third = chord.quality === 'major' ? 4 : 3;
    const fifth = chord.quality === 'diminished' ? 6 : 7;
    const midiRoot = 36 + root;
    return [midiRoot, midiRoot + third, midiRoot + fifth];
  });

  const roots = triads.map(([root]) => root);
  let rootAverage = roots.reduce((sum, root) => sum + root, 0) / roots.length;
  for (let index = 0; index < triads.length; index++) {
    const distance = roots[index] - rootAverage;
    if (Math.abs(distance) >= 5 - 1e-9) {
      const adjustment = distance > 0 ? -12 : 12;
      triads[index] = triads[index].map((pitch) => pitch + adjustment);
      rootAverage += adjustment / triads.length;
    }
  }

  if (typeof seed === 'number' && !Number.isSafeInteger(seed)) {
    throw new RangeError('Numeric seeds must be safe integers; use bigint for 64-bit seeds.');
  }
  const javaSeed = BigInt.asIntN(32, BigInt(seed));
  const partSeed = BigInt.asIntN(32, javaSeed + 10_000n);
  const dynamics = new JavaRandom(partSeed);
  const noteVariationRandom = new JavaRandom(BigInt.asIntN(32, partSeed + 2n));
  const events: BassNoteEvent[] = [];
  let chordStart = 0;

  for (let chordIndex = 0; chordIndex < triads.length; chordIndex++) {
    const durations = rhythm === 'alternating'
      ? makeAlternatingDurations(
        BigInt.asIntN(32, partSeed + BigInt(chordIndex % 2)),
        BEATS_PER_CHORD,
      )
      : makePatternDurations(rhythm, BEATS_PER_CHORD);
    let noteStart = chordStart;

    for (let noteIndex = 0; noteIndex < durations.length; noteIndex++) {
      const duration = durations[noteIndex];
      const velocity = dynamics.nextInt(21) + 69;
      const isActive = rhythm === 'alternating' || RHYTHM_PATTERNS[rhythm][noteIndex % 8] > 0;
      let pitch = triads[chordIndex][0];

      if (isActive && noteIndex > 0 && duration < QUARTER + 1e-9
          && noteVariationRandom.nextInt(100) < noteVariation) {
        pitch = triads[chordIndex][noteVariationRandom.nextInt(triads[chordIndex].length - 1) + 1];
      }

      if (isActive) {
        events.push({
          midi: pitch,
          startBeat: noteStart,
          durationBeats: duration,
          velocity,
          chordIndex,
        });
      }
      noteStart += duration;
    }
    chordStart += BEATS_PER_CHORD;
  }

  return events;
}

function makePatternDurations(rhythm: Exclude<BassRhythm, 'alternating'>, limit: number): number[] {
  const pattern = RHYTHM_PATTERNS[rhythm];
  const stepDuration = BEATS_PER_CHORD / pattern.length;
  const result: number[] = [];
  let remaining = limit;
  while (remaining > 1e-9) {
    const duration = Math.min(stepDuration, remaining);
    result.push(duration);
    remaining -= duration;
  }
  return result;
}

/** Port of Rhythm.regenerateDurations(4, sixteenthNote / 2) for a four beat chord. */
function makeAlternatingDurations(seed: bigint, limit: number): number[] {
  const random = new JavaRandom(seed);
  const durations: number[] = [];
  let durationSum = 0;
  let sameDurationCount = 0;
  let lastDurationIndex = Number.MIN_SAFE_INTEGER;
  let retryCount = 0;
  let longestDurationIndex = 0;
  let longestDuration = 0;
  const shortestNote = SIXTEENTH / 2;

  while (durationSum < limit - 1e-9) {
    let duration = shortestNote;
    const chance = random.nextInt(100);
    let chosenIndex = 0;
    let lastNote = false;

    for (let index = 0; index < DURATION_POOL.length; index++) {
      if (index < DURATION_POOL.length - 1
          && DURATION_POOL[index + 1] > limit - durationSum + 1e-9) {
        duration = limit - durationSum;
        if (duration < shortestNote - 1e-9 && durations.length > 0) {
          longestDuration = longestDuration - shortestNote + duration;
          durations[longestDurationIndex] = longestDuration;
          duration = shortestNote;
        }
        lastNote = true;
        break;
      }
      if (chance < DURATION_WEIGHTS[index]) {
        duration = DURATION_POOL[index];
        chosenIndex = index;
        break;
      }
    }

    if (lastNote) {
      durations.push(duration);
      break;
    }
    if (lastDurationIndex === chosenIndex) {
      sameDurationCount++;
    } else {
      lastDurationIndex = chosenIndex;
      sameDurationCount = 0;
    }

    if (sameDurationCount < 4 || chosenIndex === 0 || retryCount === 2) {
      durationSum += duration;
      durations.push(duration);
      if (duration > longestDuration) {
        longestDuration = duration;
        longestDurationIndex = durations.length - 1;
      }
      if (retryCount === 2) {
        retryCount = 0;
      }
    } else {
      retryCount++;
    }
  }

  return durations;
}
