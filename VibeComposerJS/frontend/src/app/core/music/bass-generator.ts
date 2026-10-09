import { getDiatonicChords, getPitchClass } from './harmony';
import type { ScaleMode } from './harmony';
import { JavaRandom } from './java-random';
import type { BassRhythm, BassSettings } from '../project/project.model';
import { decodePartPatch, velocityBounds } from './part-settings';
import { partFillMask } from './chord-span-fill';
import { effectivePartSeed, spannedPattern, spannedVelocities } from './part-processing';
import { BASS_RHYTHMS } from './rhythm-patterns';

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

/** Build a bass phrase from the current diatonic roots and serialized settings. */
export function generateBassline(
  seed: bigint | number,
  key: string,
  scale: ScaleMode,
  progression: readonly number[],
  rhythm: BassRhythm,
  noteVariation: number,
  settings: Partial<BassSettings> = {},
): BassNoteEvent[] {
  if (progression.length < 1 || progression.length > 32
      || !Number.isInteger(noteVariation) || noteVariation < 0 || noteVariation > 100
      || !BASS_RHYTHMS.includes(rhythm) || !decodePartPatch('bass', settings)) {
    throw new RangeError('Bass generation settings are outside the supported range.');
  }

  if (settings.generationEnabled === false) return [];
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
  const javaSeed = effectivePartSeed(seed, settings);
  const partSeed = BigInt.asIntN(32, javaSeed + 10_000n);
  const dynamics = new JavaRandom(partSeed);
  const noteVariationRandom = new JavaRandom(BigInt.asIntN(32, partSeed + 2n));
  const events: BassNoteEvent[] = [];
  const [velocityMin, velocityMax] = velocityBounds(settings);
  const fill = partFillMask(triads.length, settings);
  let chordStart = 0;
  let skipNotes = 0;
  const joinMode = settings.patternJoinMode ?? 'NOJOIN';
  const stretchedByNote = joinMode === 'JOIN' ? 1 : 0;

  for (let chordIndex = 0; chordIndex < triads.length; chordIndex++) {
    // Java bass skips the entire chord before consuming its shared dynamics/variation streams.
    if (!fill[chordIndex]) { skipNotes = 0; chordStart += BEATS_PER_CHORD; continue; }
    const gridSettings = { ...settings, rhythm: rhythm === 'alternating' ? 'full' as const : rhythm };
    const grid = rhythm === 'alternating' ? undefined : spannedPattern(gridSettings, chordIndex);
    const velocities = rhythm === 'alternating' ? undefined : spannedVelocities(gridSettings, chordIndex);
    // Java's lookahead deliberately uses the next slice before Pattern flip, independently of Fill.
    const span = settings.chordSpan ?? 1;
    const nextGrid = grid && joinMode !== 'NOJOIN' && chordIndex % span < span - 1
      ? spannedPattern({ ...gridSettings, patternFlip: false }, chordIndex + 1) : undefined;
    const durations = rhythm === 'alternating'
      ? makeAlternatingDurations(
        BigInt.asIntN(32, partSeed + BigInt(chordIndex % 2)),
        BEATS_PER_CHORD,
      )
      : Array(grid!.length).fill(BEATS_PER_CHORD / grid!.length) as number[];
    let noteStart = chordStart;
    let nextP = -1;

    for (let noteIndex = 0; noteIndex < durations.length; noteIndex++) {
      const duration = durations[noteIndex];
      const velocity = velocities ? velocities[noteIndex % velocities.length] : dynamics.nextInt(velocityMax - velocityMin + 1) + velocityMin;
      const isActive = rhythm === 'alternating' || (grid![noteIndex] > 0
        && !(noteIndex <= nextP && stretchedByNote === 1) && skipNotes === 0);
      if (grid && skipNotes > 0) skipNotes--;
      let durationMultiplier = 1;
      if (grid && joinMode !== 'NOJOIN' && grid[noteIndex] > 0 && noteIndex >= nextP) {
        nextP = noteIndex + 1;
        while (nextP < grid.length) {
          if (noteStart - chordStart + duration * durationMultiplier > BEATS_PER_CHORD) break;
          if (Math.sign(grid[nextP]) !== stretchedByNote && grid[nextP] !== -1) break;
          durationMultiplier++;
          nextP++;
        }
      }
      if (grid && nextP >= grid.length && nextGrid) {
        skipNotes = 0;
        while (skipNotes < nextGrid.length && (nextGrid[skipNotes] === stretchedByNote || nextGrid[skipNotes] === -1)) skipNotes++;
        durationMultiplier += skipNotes;
      }
      const soundingDuration = duration * durationMultiplier;
      let pitch = triads[chordIndex][0];

      if (isActive && noteIndex > 0 && soundingDuration < QUARTER + 1e-9
          && noteVariationRandom.nextInt(100) < noteVariation) {
        pitch = triads[chordIndex][noteVariationRandom.nextInt(triads[chordIndex].length - 1) + 1];
      }

      if (isActive && velocity > 0) {
        events.push({
          midi: pitch,
          startBeat: noteStart,
          durationBeats: soundingDuration,
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
