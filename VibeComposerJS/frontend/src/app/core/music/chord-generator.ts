import type { ChordSettings } from '../project/project.model';
import { getDiatonicChords, getPitchClass } from './harmony';
import type { ScaleMode } from './harmony';
import { JavaRandom } from './java-random';
import { decodePartPatch, velocityBounds } from './part-settings';
import { partFillMask } from './chord-span-fill';
import { effectivePartSeed, spannedPattern, spannedVelocities, expandedVoices } from './part-processing';
import { swingNotes } from './phrase-swing';


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
      || settings.noteLengthPercent < 25 || settings.noteLengthPercent > 200
      || (settings.voicing !== 'close' && settings.voicing !== 'open') || !decodePartPatch('chords', settings)) {
    throw new RangeError('Chord generation settings are outside the supported range.');
  }

  if (settings.generationEnabled === false) return [];
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
    return { symbol: chord.symbol, pitches: expandedVoices(pitches, settings) };
  });

  const signedSeed = effectivePartSeed(seed, settings);
  const partSeed = BigInt.asIntN(32, signedSeed + 20_000n);
  const events: ChordHitEvent[] = [];
  const [velocityMin, velocityMax] = velocityBounds(settings);
  const fill = partFillMask(progression.length, settings);
  const pauseRandom = new JavaRandom(BigInt.asIntN(32, partSeed + 51n));
  const timeline: Array<ChordHitEvent & { rhythm: number; duration: number; active: boolean }> = [];

  for (let chordIndex = 0; chordIndex < chordDefinitions.length; chordIndex++) {
    if (!fill[chordIndex]) { timeline.push({ pitches: [], symbol: '', startBeat: chordIndex * 4, durationBeats: 4, velocity: 0, chordIndex, rhythm: 4, duration: 4, active: false }); continue; }
    const chord = chordDefinitions[chordIndex];
    const pattern = spannedPattern(settings, chordIndex);
    const velocityPattern = spannedVelocities(settings, chordIndex);
    const stepDuration = BEATS_PER_CHORD / pattern.length;
    const velocityRandom = new JavaRandom(BigInt.asIntN(32, partSeed + BigInt(chordIndex)));

    for (let step = 0; step < pattern.length; step++) {
      const velocity = velocityPattern ? velocityPattern[step % velocityPattern.length] : velocityRandom.nextInt(velocityMax - velocityMin + 1) + velocityMin;
      const paused = pauseRandom.nextInt(100) < (settings.pauseChance ?? 0);
      timeline.push({ rhythm: stepDuration, duration: stepDuration * settings.noteLengthPercent / 100, active: pattern[step] > 0 && velocity > 0 && !paused,
        pitches: chord.pitches,
        symbol: chord.symbol,
        startBeat: chordIndex * BEATS_PER_CHORD + step * stepDuration,
        durationBeats: stepDuration * settings.noteLengthPercent / 100,
        velocity,
        chordIndex,
      });
    }
  }

  swingNotes(timeline, (settings.hitsPerPattern ?? 8) % 2 === 0 ? settings.swingPercent ?? 50 : 50);
  let time = 0;
  for (const note of timeline) {
    if (note.active) events.push({ pitches: note.pitches, symbol: note.symbol, startBeat: time, durationBeats: note.duration, velocity: note.velocity, chordIndex: note.chordIndex });
    time += note.rhythm;
  }
  return events;
}
