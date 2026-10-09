import type { DrumSettings } from '../project/project.model';
import { JavaRandom } from './java-random';
import { partFillMask } from './chord-span-fill';
import { partRhythmMask } from './rhythm-pattern';
import { partVelocityPattern } from './velocity-pattern';
import { decodePartPatch, velocityBounds } from './part-settings';
import { isDrumPitch } from './drum-instruments';
import { swingNotes } from './phrase-swing';
import { effectivePartSeed } from './part-processing';

export interface DrumHitEvent {
  readonly midi: number;
  readonly startBeat: number;
  readonly durationBeats: number;
  readonly velocity: number;
  readonly barIndex: number;
  readonly step: number;
}

/** Single-pitch DrumPhraseGenerator path, including spans, pauses and split hits. */
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
  if (settings.generationEnabled === false) return [];
  const seedValue = effectivePartSeed(seed, settings);
  const patternRandom = new JavaRandom(BigInt.asIntN(32, seedValue + 40_000n));
  const pattern = partRhythmMask({ ...rhythmSettings, patternFlip: false }).map(slot => {
    let blank = patternRandom.nextInt(100) < (settings.pauseChance ?? 0) || slot < 1;
    if (settings.patternFlip) blank = !blank;
    // Preserve Java's random stream while keeping this track's selected pitch.
    if (!blank && pitch === 42) patternRandom.nextInt(100);
    return blank ? 0 : 1;
  });
  const custom = partVelocityPattern(rhythmSettings);
  const [minimum, maximum] = velocityBounds(settings);
  // Java's first drum has orderOffset 1; velocity pattern uses part seed + 40000 + orderOffset.
  const random = new JavaRandom(BigInt.asIntN(32, seedValue + 40_001n));
  const velocities = custom ?? pattern.map(() => random.nextInt(maximum - minimum + 1) + minimum);
  const fill = partFillMask(barCount, settings);
  const span = settings.chordSpan ?? 1;
  const stepDuration = 4 * span / pattern.length;
  const exceptions = new JavaRandom(BigInt.asIntN(32, seedValue + 40_001n));
  const notes: Array<{ midi: number; barIndex: number; step: number; velocity: number; rhythm: number; duration: number }> = [];
  for (let group = 0; group < barCount; group += span) {
    const total = Math.min(span, barCount - group) * 4;
    for (let time = 0, step = 0; time + 0.01 < total; step++, time += stepDuration) {
      const slot = step % pattern.length;
      const barIndex = group + Math.floor((time + 0.01) / 4);
      const duration = Math.min(stepDuration, total - time);
      const midi = fill[barIndex] && pattern[slot] > 0 && velocities[slot] > 0 && duration >= 0.05 ? pitch : -1;
      const split = exceptions.nextInt(100) < (settings.exceptionChance ?? 0);
      notes.push({ midi, barIndex, step, velocity: velocities[slot], rhythm: duration / (split ? 2 : 1),
        duration: duration / (split ? 2 : 1) * 0.5 * 0.95 });
      if (split) notes.push({ midi, barIndex, step, velocity: Math.trunc(velocities[slot] * 0.8),
        rhythm: duration / 2, duration: duration / 2 * 0.5 * 0.95 });
    }
  }
  // Java suppresses swing for odd Hits and swings rests alongside sounded notes.
  swingNotes(notes, pattern.length % 2 === 0 ? settings.swingPercent : 50);
  const events: DrumHitEvent[] = [];
  let time = 0;
  for (const note of notes) {
    if (note.midi >= 0 && note.velocity > 0) events.push({ midi: note.midi, barIndex: note.barIndex, step: note.step,
      startBeat: time, durationBeats: note.duration, velocity: note.velocity });
    time += note.rhythm;
  }
  return events;
}
