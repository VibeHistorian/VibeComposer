import type { ArpeggioSettings } from '../project/project.model';
import { getDiatonicChords, getPitchClass } from './harmony';
import type { ScaleMode } from './harmony';
import { JavaRandom } from './java-random';
import { decodePartPatch, velocityBounds } from './part-settings';
import { partFillMask } from './chord-span-fill';
import { effectivePartSeed, spannedPattern, spannedVelocities, expandedVoices } from './part-processing';
import { partRhythmMask } from './rhythm-pattern';
import { swingNotes } from './phrase-swing';

export interface ArpeggioNoteEvent {
  readonly midi: number;
  readonly startBeat: number;
  readonly durationBeats: number;
  readonly velocity: number;
  readonly chordIndex: number;
}

const ARPEGGIO_PATTERNS = ['up', 'down', 'up-down', 'random'] as const;

/** Generate a seeded arpeggio phrase that follows the progression. */
export function generateArpeggio(
  seed: bigint | number,
  key: string,
  scale: ScaleMode,
  progression: readonly number[],
  settings: ArpeggioSettings,
): ArpeggioNoteEvent[] {
  if (typeof seed === 'number' && !Number.isSafeInteger(seed)) {
    throw new RangeError('Numeric seeds must be safe integers; use bigint for 64-bit seeds.');
  }
  if (progression.length < 1 || progression.length > 32
      || !ARPEGGIO_PATTERNS.includes(settings.pattern)
      || (settings.rate !== 'eighth' && settings.rate !== 'sixteenth')
      || (settings.octaves !== 1 && settings.octaves !== 2) || !decodePartPatch('arpeggio', settings)) {
    throw new RangeError('Arpeggio settings are outside the supported range.');
  }

  if (settings.generationEnabled === false) return [];
  const diatonicChords = getDiatonicChords(key, scale);
  const signedSeed = effectivePartSeed(seed, settings);
  const rhythmSettings = { ...settings, rhythm: settings.rhythm ?? 'full' as const,
    hitsPerPattern: settings.hitsPerPattern ?? (settings.rate === 'eighth' ? 8 : 16) };
  const pauses = partRhythmMask({ ...rhythmSettings, patternFlip: false });
  const pauseRandom = new JavaRandom(BigInt.asIntN(32, signedSeed + 30_004n));
  for (let index = 0; index < pauses.length; index++) if (pauseRandom.nextInt(100) < (settings.pauseChance ?? 0)) pauses[index] = 0;
  const exceptions = new JavaRandom(BigInt.asIntN(32, signedSeed + 30_002n));
  const timeline: Array<ArpeggioNoteEvent & { rhythm: number; duration: number; active: boolean }> = [];
  const events: ArpeggioNoteEvent[] = [];
  const [velocityMin, velocityMax] = velocityBounds(settings);
  const fill = partFillMask(progression.length, settings);

  progression.forEach((degree, chordIndex) => {
    const chord = diatonicChords.find((candidate) => candidate.degree === degree);
    if (!chord) {
      throw new RangeError(`Chord degree ${degree} is not available in ${key} ${scale}.`);
    }
    const rootPitchClass = getPitchClass(chord.root);
    if (rootPitchClass === undefined) {
      throw new RangeError(`Unknown chord root: ${chord.root}.`);
    }
    const third = chord.quality === 'major' ? 4 : 3;
    const fifth = chord.quality === 'diminished' ? 6 : 7;
    const ascending = expandedVoices(Array.from({ length: settings.octaves }, (_, octave) => [
      48 + rootPitchClass + octave * 12,
      48 + rootPitchClass + third + octave * 12,
      48 + rootPitchClass + fifth + octave * 12,
    ]).flat(), settings);
    const descending = [...ascending].reverse();
    const upDown = [...ascending, ...ascending.slice(1, -1).reverse()];
    const pitchRandom = new JavaRandom(BigInt.asIntN(32, signedSeed + 30_000n + BigInt(chordIndex)));
    const velocityRandom = new JavaRandom(BigInt.asIntN(32, signedSeed + 31_000n + BigInt(chordIndex)));

    const pattern = spannedPattern(rhythmSettings, chordIndex, true, pauses);
    const custom = spannedVelocities(rhythmSettings, chordIndex, true);
    const rate = 4 / pattern.length;
    const span = settings.chordSpan ?? 1;
    const sequence = settings.pattern === 'down' ? descending
      : settings.pattern === 'up-down' ? upDown : ascending;
    const basePitches = Array.from({ length: rhythmSettings.hitsPerPattern }, (_, index) =>
      sequence[settings.pattern === 'random' ? pitchRandom.nextInt(sequence.length) : index % sequence.length]);
    const expandedPitches = Array.from({ length: settings.patternRepeat ?? 1 }, () =>
      basePitches.flatMap(pitch => [pitch, ...Array(span - 1).fill(ascending[0])])).flat();
    const pitches = expandedPitches.slice((chordIndex % span) * pattern.length, (chordIndex % span + 1) * pattern.length);
    for (let step = 0; step < pattern.length; step++) {
      // Java arp turns excluded chords into rests after consuming pitch/dynamic randomness.
      const velocity = custom ? custom[step % custom.length] : velocityRandom.nextInt(velocityMax - velocityMin + 1) + velocityMin;
      const active = !!fill[chordIndex] && pattern[step] > 0 && velocity > 0;
      const split = exceptions.nextInt(100) < (settings.exceptionChance ?? 0) && active;
      const pitch = pitches[step];
      const duration = rate / (split ? 2 : 1);
      timeline.push({ rhythm: duration, duration: duration * span, active,
        midi: pitch,
        startBeat: chordIndex * 4 + step * rate,
        durationBeats: duration * span,
        velocity,
        chordIndex,
      });
      if (split) {
        const next = pitches[(step + 1) % pitches.length];
        const average = Math.trunc((pitch + next) / 2);
        const mode = scale === 'major' ? [0, 2, 4, 5, 7, 9, 11] : [0, 2, 3, 5, 7, 8, 10];
        const tonic = getPitchClass(key)!;
        const corrected = mode.includes((average - tonic + 120) % 12) ? average : average - 1;
        timeline.push({ midi: corrected, startBeat: chordIndex * 4 + step * rate + duration,
          durationBeats: duration * span, velocity: Math.max(0, velocity - 15), chordIndex,
          rhythm: duration, duration: duration * span, active: velocity > 15 });
      }
    }
  });

  swingNotes(timeline, rhythmSettings.hitsPerPattern % 2 === 0 ? settings.swingPercent ?? 50 : 50);
  let time = 0;
  for (const note of timeline) {
    if (note.active) events.push({ midi: note.midi, startBeat: time, durationBeats: note.duration, velocity: note.velocity, chordIndex: note.chordIndex });
    time += note.rhythm;
  }
  return events;
}
