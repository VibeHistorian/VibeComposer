import type { DrumGenerationPolicy } from './generation-policy.model';
import type { JavaRandom } from './java-random';
import type { DrumSettings } from './part-settings.model';

export type DrumJob = 'pulse' | 'answer' | 'time' | 'reply' | 'accent';

interface DrumProfile {
  readonly job: DrumJob;
  readonly substitute?: DrumJob;
  /** Relative low, mid, and high energy. Decay is an approximate audible tail in ms. */
  readonly bands: readonly [number, number, number];
  readonly decay: number;
  readonly panLimit: number;
}

const kick: DrumProfile = { job: 'pulse', bands: [1, .25, .12], decay: 170, panLimit: 0 };
const snare: DrumProfile = { job: 'answer', bands: [.15, .8, .65], decay: 140, panLimit: 10 };
const stick: DrumProfile = { job: 'answer', substitute: 'accent', bands: [.04, .85, .4], decay: 60, panLimit: 24 };
const lowTom: DrumProfile = { job: 'reply', substitute: 'pulse', bands: [.75, .58, .13], decay: 220, panLimit: 24 };
const tom: DrumProfile = { job: 'reply', substitute: 'answer', bands: [.36, .8, .23], decay: 155, panLimit: 44 };
const closedHat: DrumProfile = { job: 'time', bands: [0, .12, .85], decay: 65, panLimit: 44 };
const openHat: DrumProfile = { job: 'accent', bands: [0, .18, .9], decay: 420, panLimit: 44 };
const ride: DrumProfile = { job: 'accent', substitute: 'time', bands: [0, .24, .8], decay: 620, panLimit: 48 };
const crash: DrumProfile = { job: 'accent', bands: [.05, .44, 1], decay: 900, panLimit: 52 };
const shaker: DrumProfile = { job: 'time', substitute: 'reply', bands: [0, .19, .68], decay: 105, panLimit: 60 };
const hand: DrumProfile = { job: 'reply', substitute: 'answer', bands: [.14, .79, .28], decay: 85, panLimit: 51 };
const lowHand: DrumProfile = { job: 'reply', substitute: 'pulse', bands: [.52, .72, .2], decay: 140, panLimit: 33 };
const block: DrumProfile = { job: 'reply', substitute: 'time', bands: [.02, .6, .52], decay: 48, panLimit: 60 };
const metal: DrumProfile = { job: 'reply', substitute: 'accent', bands: [.05, .65, .72], decay: 230, panLimit: 53 };
const color: DrumProfile = { job: 'accent', bands: [0, .47, .72], decay: 440, panLimit: 56 };
const unknown: DrumProfile = { job: 'reply', bands: [.38, .45, .38], decay: 145, panLimit: 24 };

const PROFILES: Readonly<Record<number, DrumProfile>> = {
  35: kick, 36: kick, 37: stick, 38: snare, 39: { ...snare, substitute: 'accent', decay: 100, panLimit: 17 }, 40: snare,
  41: lowTom, 42: closedHat, 43: lowTom, 44: closedHat, 45: lowTom, 46: openHat, 47: tom, 48: tom,
  49: crash, 50: tom, 51: ride, 52: crash, 53: { ...ride, decay: 370 }, 54: { ...shaker, substitute: 'accent' },
  55: { ...crash, decay: 380 }, 56: metal, 57: crash, 58: color, 59: ride,
  60: hand, 61: lowHand, 62: lowHand, 63: hand, 64: lowHand, 65: hand, 66: lowHand,
  67: metal, 68: metal, 69: shaker, 70: shaker, 71: block, 72: color, 73: block, 74: color,
  75: block, 76: block, 77: block, 78: hand, 79: color, 80: metal, 81: color, 82: shaker,
};

export function drumProfile(pitch: number): DrumProfile { return PROFILES[pitch] ?? unknown; }

const GROOVES = [
  { grid: 16, pulse: [0, 8], answer: [4, 12] },
  { grid: 16, pulse: [0, 6, 10], answer: [4, 12] },
  { grid: 16, pulse: [0, 7], answer: [5, 12] },
  { grid: 16, pulse: [0, 10], answer: [6, 14] },
  { grid: 16, pulse: [2, 8], answer: [4, 13] },
  { grid: 16, pulse: [0, 5, 11], answer: [8, 14] },
  { grid: 12, pulse: [0, 9], answer: [4, 10] },
  { grid: 12, pulse: [0, 6], answer: [3, 8] },
  { grid: 8, pulse: [0, 5], answer: [2, 6] },
  { grid: 8, pulse: [0, 4], answer: [3, 7] },
  { grid: 16, pulse: [3, 9, 15], answer: [0, 7] },
  { grid: 12, pulse: [1, 4, 9], answer: [7, 11] },
  { grid: 16, pulse: [0, 5, 13], answer: [3, 10, 15] },
] as const;
export type DrumGroove = typeof GROOVES[number];

export function chooseDrumGroove(random: JavaRandom, experiment: number): DrumGroove {
  const standard = GROOVES.length - 3;
  return random.nextDouble() < experiment ** 2 * .6
    ? GROOVES[standard + random.nextInt(3)] : GROOVES[random.nextInt(standard)];
}

const HITS_MULTIPLIERS = { '1': 1, '1/2': .5, '3/4': .75, '3/2': 1.5, '2': 2 } as const;
const VELOCITIES: Readonly<Record<DrumJob, readonly [number, number]>> = {
  pulse: [81, 103], answer: [69, 96], time: [46, 72], reply: [52, 79], accent: [55, 81],
};

export function kitPattern(random: JavaRandom, policy: DrumGenerationPolicy, source: DrumSettings, job: DrumJob,
  groove: DrumGroove, swing: number, offset: number, load: number, accentSpan: number): DrumSettings {
  const anchor = job === 'pulse' || job === 'answer';
  const span = job === 'accent' ? accentSpan : 1;
  const [minimum, maximum] = VELOCITIES[job];
  const next: DrumSettings = { ...source, swingPercent: swing,
    ...(policy.patterns || policy.randomOffset ? { offset: anchor ? 0 : offset } : {}),
    ...(policy.fills ? { chordSpanFill: 'ALL', fillFlip: false } as const : {}) };
  if (!policy.patterns) {
    if (policy.fills && !anchor && job !== 'time' && random.nextInt(3) === 0) {
      return { ...next, chordSpanFill: random.nextBoolean() ? 'EVEN' : 'F12' };
    }
    return next;
  }
  const steps = Math.max(1, Math.min(32, Math.round(groove.grid * span * HITS_MULTIPLIERS[policy.hitsMultiplier])));
  const grid = Array<number>(32).fill(0), velocities = Array<number>(32).fill(minimum);
  const put = (step: number, velocity: number) => {
    velocities[step] = grid[step] ? Math.max(velocities[step], velocity) : velocity;
    grid[step] = 1;
  };
  const attack = (step: number, main: boolean) => put(step, main ? maximum - random.nextInt(10)
    : minimum + random.nextInt(Math.max(1, maximum - minimum - 9)));
  const roundStep = (step: number) => Math.min(steps - 1, Math.round(step * steps / groove.grid / span));
  const shifted = random.nextInt(100) < policy.shiftChance;
  const pulses = Math.max(1, Math.min(steps, Math.round(load * span)));
  if (anchor) {
    const positions: readonly number[] = job === 'pulse' ? groove.pulse : groove.answer;
    positions.slice(0, pulses).forEach((position, index) => attack(roundStep(position), index === 0));
    if (pulses > positions.length) attack(random.nextInt(steps), false);
  } else if (job === 'time') {
    const phase = shifted ? random.nextInt(Math.max(1, Math.ceil(steps / pulses))) : 0;
    for (let index = 0; index < pulses; index++) {
      attack((Math.floor(index * steps / pulses) + phase) % steps, index % 3 === 0);
    }
  } else {
    const slots = Array.from({ length: steps }, (_, step) => step);
    for (let index = 0; index < pulses; index++) {
      const pick = shifted || job === 'accent' ? random.nextInt(slots.length) : Math.floor(slots.length * (index % 2 ? .72 : .31));
      attack(slots.splice(pick, 1)[0], index === 0);
    }
  }
  if (job !== 'accent' && steps > 2 && random.nextInt(100) < (policy.dynamicChance ?? 50)) {
    const rests = grid.slice(0, steps).flatMap((value, step) => value === 0 ? [step] : []);
    if (rests.length) put(rests[random.nextInt(rests.length)], Math.round(minimum * .55));
  }
  const alternateBars = policy.fills && span === 1 && (job === 'accent' || job === 'reply') && random.nextInt(4) === 0;
  const generated = { ...next, ...(alternateBars ? { chordSpanFill: 'F12' } as const : {}),
    chordSpan: span, hitsPerPattern: steps, patternShift: 0, patternFlip: false,
    rhythm: 'custom', customPattern: grid, customVelocities: velocities, useCustomVelocities: true,
    velocityMin: minimum, velocityMax: maximum, ghosts: false, pauseChance: 0, exceptionChance: 0,
    feedbackCount: 0, patternSeed: 0 } as const;
  if ((job === 'time' || job === 'reply') && random.nextInt(100) < (policy.melodyChance ?? 30)) {
    return { ...generated, rhythm: 'melody', chordSpan: 1, useCustomVelocities: false,
      pauseChance: Math.max(35, 81 - Math.round(load * 6)) };
  }
  return generated;
}
