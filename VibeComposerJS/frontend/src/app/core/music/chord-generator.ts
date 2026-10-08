import type { ChordSettings } from '../project/project.model';
import { getDiatonicChords, getPitchClass } from './harmony';
import type { ScaleMode } from './harmony';
import { JavaRandom } from './java-random';
import { velocityBounds } from './part-settings';
import { partFillMask } from './chord-span-fill';
import { chordRhythmMask } from './rhythm-pattern';

export interface ChordHitEvent {
  readonly pitches: readonly number[];
  readonly symbol: string;
  readonly startBeat: number;
  readonly durationBeats: number;
  readonly velocity: number;
  readonly chordIndex: number;
}

const BEATS_PER_CHORD = 4;

/** Generate seeded chord hits from diatonic progression degrees. */
export function generateChordPart(
  seed: bigint | number,
  key: string,
  scale: ScaleMode,
  progression: readonly number[],
  settings: ChordSettings,
): ChordHitEvent[] {
  if (typeof seed === 'number' && !Number.isSafeInteger(seed)) {
    throw new RangeError('Numeric seeds must be safe integers; use bigint for 64-bit seeds.');
  }
  if (progression.length < 1 || progression.length > 32
      || !Number.isInteger(settings.noteLengthPercent)
      || settings.noteLengthPercent < 25 || settings.noteLengthPercent > 125
      || (settings.voicing !== 'close' && settings.voicing !== 'open')) {
    throw new RangeError('Chord generation settings are outside the supported range.');
  }

  const diatonicChords = getDiatonicChords(key, scale);
  const chordDefinitions = progression.map((degree) => {
    const chord = diatonicChords.find((candidate) => candidate.degree === degree);
    if (!chord) {
      throw new RangeError(`Chord degree ${degree} is not available in ${key} ${scale}.`);
    }
    const rootPitchClass = getPitchClass(chord.root);
    if (rootPitchClass === undefined) {
      throw new RangeError(`Unknown chord root: ${chord.root}.`);
    }

    const root = 60 + rootPitchClass;
    const third = chord.quality === 'major' ? 4 : 3;
    const fifth = chord.quality === 'diminished' ? 6 : 7;
    const pitches = settings.voicing === 'open'
      ? [root, root + fifth, root + 12 + third]
      : [root, root + third, root + fifth];
    return { symbol: chord.symbol, pitches };
  });

  const signedSeed = BigInt.asIntN(32, BigInt(seed));
  const partSeed = BigInt.asIntN(32, signedSeed + 20_000n);
  const events: ChordHitEvent[] = [];
  const [velocityMin, velocityMax] = velocityBounds(settings);
  const fill = partFillMask(progression.length, settings);
  const pattern = chordRhythmMask(settings);
  const stepDuration = BEATS_PER_CHORD / pattern.length;

  for (let chordIndex = 0; chordIndex < chordDefinitions.length; chordIndex++) {
    if (!fill[chordIndex]) continue;
    const chord = chordDefinitions[chordIndex];
    const velocityRandom = new JavaRandom(BigInt.asIntN(32, partSeed + BigInt(chordIndex)));

    for (let step = 0; step < pattern.length; step++) {
      const velocity = velocityRandom.nextInt(velocityMax - velocityMin + 1) + velocityMin;
      if (pattern[step] < 1) {
        continue;
      }
      events.push({
        pitches: chord.pitches,
        symbol: chord.symbol,
        startBeat: chordIndex * BEATS_PER_CHORD + step * stepDuration,
        durationBeats: stepDuration * settings.noteLengthPercent / 100,
        velocity,
        chordIndex,
      });
    }
  }

  return events;
}
