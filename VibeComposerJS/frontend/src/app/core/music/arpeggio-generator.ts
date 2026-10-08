import type { ArpeggioSettings } from '../project/project.model';
import { getDiatonicChords, getPitchClass } from './harmony';
import type { ScaleMode } from './harmony';
import { JavaRandom } from './java-random';
import { velocityBounds } from './part-settings';

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
      || (settings.octaves !== 1 && settings.octaves !== 2)) {
    throw new RangeError('Arpeggio settings are outside the supported range.');
  }

  const diatonicChords = getDiatonicChords(key, scale);
  const signedSeed = BigInt.asIntN(32, BigInt(seed));
  const rate = settings.rate === 'eighth' ? 0.5 : 0.25;
  const notesPerChord = Math.round(4 / rate);
  const events: ArpeggioNoteEvent[] = [];
  const [velocityMin, velocityMax] = velocityBounds(settings);

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
    const ascending = Array.from({ length: settings.octaves }, (_, octave) => [
      48 + rootPitchClass + octave * 12,
      48 + rootPitchClass + third + octave * 12,
      48 + rootPitchClass + fifth + octave * 12,
    ]).flat();
    const descending = [...ascending].reverse();
    const upDown = [...ascending, ...ascending.slice(1, -1).reverse()];
    const pitchRandom = new JavaRandom(BigInt.asIntN(32, signedSeed + 30_000n + BigInt(chordIndex)));
    const velocityRandom = new JavaRandom(BigInt.asIntN(32, signedSeed + 31_000n + BigInt(chordIndex)));

    for (let step = 0; step < notesPerChord; step++) {
      const sequence = settings.pattern === 'down' ? descending
        : settings.pattern === 'up-down' ? upDown : ascending;
      const pitchIndex = settings.pattern === 'random'
        ? pitchRandom.nextInt(ascending.length)
        : step % sequence.length;
      events.push({
        midi: settings.pattern === 'random' ? ascending[pitchIndex] : sequence[pitchIndex],
        startBeat: chordIndex * 4 + step * rate,
        durationBeats: rate,
        velocity: velocityRandom.nextInt(velocityMax - velocityMin + 1) + velocityMin,
        chordIndex,
      });
    }
  });

  return events;
}
