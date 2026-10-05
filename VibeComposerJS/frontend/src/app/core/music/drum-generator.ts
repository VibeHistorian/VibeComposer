import type { DrumSettings } from '../project/project.model';
import { JavaRandom } from './java-random';

export type DrumVoice = 'kick' | 'snare' | 'closed-hat';

export interface DrumHitEvent {
  readonly midi: number;
  readonly voice: DrumVoice;
  readonly startBeat: number;
  readonly durationBeats: number;
  readonly velocity: number;
  readonly barIndex: number;
  readonly step: number;
}

const SIXTEENTH_NOTE_BEATS = 0.25;
const GROOVES: Readonly<Record<DrumSettings['groove'], Readonly<Record<DrumVoice, readonly number[]>>>> = {
  rock: {
    kick: [0, 8],
    snare: [4, 12],
    'closed-hat': [0, 2, 4, 6, 8, 10, 12, 14],
  },
  'four-on-floor': {
    kick: [0, 4, 8, 12],
    snare: [4, 12],
    'closed-hat': [0, 2, 4, 6, 8, 10, 12, 14],
  },
  'half-time': {
    kick: [0, 8],
    snare: [8],
    'closed-hat': [0, 2, 4, 6, 8, 10, 12, 14],
  },
  sparse: {
    kick: [0],
    snare: [8],
    'closed-hat': [0, 4, 8, 12],
  },
};

/** Generate a seeded GM drum phrase, one four-beat bar per progression slot. */
export function generateDrumPart(
  seed: bigint | number,
  barCount: number,
  settings: DrumSettings,
): DrumHitEvent[] {
  if (typeof seed === 'number' && !Number.isSafeInteger(seed)) {
    throw new RangeError('Numeric seeds must be safe integers; use bigint for 64-bit seeds.');
  }
  if (!Number.isInteger(barCount) || barCount < 1 || barCount > 32
      || !Number.isInteger(settings.swingPercent)
      || settings.swingPercent < 50 || settings.swingPercent > 75) {
    throw new RangeError('Drum generation settings are outside the supported range.');
  }

  const signedSeed = BigInt.asIntN(32, BigInt(seed));
  const velocityRandom = new JavaRandom(BigInt.asIntN(32, signedSeed + 40_000n));
  const groove = GROOVES[settings.groove];
  if (!groove) {
    throw new RangeError(`Unknown drum groove: ${settings.groove}.`);
  }

  const voices: ReadonlyArray<{ voice: DrumVoice; midi: number; baseVelocity: number }> = [
    { voice: 'kick', midi: 36, baseVelocity: 96 },
    { voice: 'snare', midi: 38, baseVelocity: 88 },
    { voice: 'closed-hat', midi: 42, baseVelocity: 72 },
  ];
  const events: DrumHitEvent[] = [];
  for (let barIndex = 0; barIndex < barCount; barIndex++) {
    for (const drum of voices) {
      for (const step of groove[drum.voice]) {
        const swing = step % 4 === 2 ? (settings.swingPercent / 100 - 0.5) * 0.5 : 0;
        events.push({
          midi: drum.midi,
          voice: drum.voice,
          startBeat: barIndex * 4 + step * SIXTEENTH_NOTE_BEATS + swing,
          durationBeats: drum.voice === 'closed-hat' ? 0.1 : 0.18,
          velocity: Math.min(127, drum.baseVelocity + velocityRandom.nextInt(13) - 6),
          barIndex,
          step,
        });
      }
    }
  }
  return events.sort((left, right) => left.startBeat - right.startBeat || left.midi - right.midi);
}
