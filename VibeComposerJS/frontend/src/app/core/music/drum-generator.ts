import type { DrumSettings } from '../project/project.model';
import { JavaRandom } from './java-random';
import { partFillMask } from './chord-span-fill';
import { partRhythmMask } from './rhythm-pattern';
import { partVelocityPattern } from './velocity-pattern';
import { decodePartPatch, velocityBounds } from './part-settings';
import { isDrumPitch } from './drum-instruments';
import { swingNotes } from './phrase-swing';

export interface DrumHitEvent {
  readonly midi: number;
  readonly startBeat: number;
  readonly durationBeats: number;
  readonly velocity: number;
  readonly barIndex: number;
  readonly step: number;
}

/** Supported DrumPhraseGenerator path: one pitch, span 1, no ghosts, pauses or exceptions. */
export function generateDrumPart(seed: bigint | number, barCount: number, settings: DrumSettings): DrumHitEvent[] {
  if (typeof seed === 'number' && !Number.isSafeInteger(seed)) {
    throw new RangeError('Numeric seeds must be safe integers; use bigint for 64-bit seeds.');
  }
  const { pitch, ...fields } = settings;
  if (!Number.isInteger(barCount) || barCount < 1 || barCount > 32 || !isDrumPitch(pitch)
    || !decodePartPatch('drums', fields) || !fields.rhythm || fields.swingPercent === undefined) {
    throw new RangeError('Drum generation settings are outside the supported range.');
  }
  const rhythmSettings = { ...settings, hitsPerPattern: settings.hitsPerPattern ?? 4 };
  const pattern = partRhythmMask(rhythmSettings);
  const custom = partVelocityPattern(rhythmSettings);
  const [minimum, maximum] = velocityBounds(settings);
  // Java's first drum has orderOffset 1; velocity pattern uses part seed + 40000 + orderOffset.
  const random = new JavaRandom(BigInt.asIntN(32, BigInt(seed) + 40_001n));
  const velocities = custom ?? pattern.map(() => random.nextInt(maximum - minimum + 1) + minimum);
  const fill = partFillMask(barCount, settings);
  const stepDuration = 4 / pattern.length;
  const notes = Array.from({ length: barCount }, (_, barIndex) => pattern.map((slot, step) => ({
    midi: fill[barIndex] && slot > 0 && velocities[step] > 0 ? pitch : -1,
    barIndex, step, velocity: velocities[step], rhythm: stepDuration, duration: stepDuration * 0.5 * 0.95,
  }))).flat();
  // Java suppresses swing for odd Hits and swings rests alongside sounded notes.
  swingNotes(notes, pattern.length % 2 === 0 ? settings.swingPercent : 50);
  const events: DrumHitEvent[] = [];
  let time = 0;
  for (const note of notes) {
    if (note.midi >= 0) events.push({ midi: note.midi, barIndex: note.barIndex, step: note.step,
      startBeat: time, durationBeats: note.duration, velocity: note.velocity });
    time += note.rhythm;
  }
  return events;
}
