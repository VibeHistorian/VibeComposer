import type { SynthOperator, SynthPatchBank } from './tiny-synth-engine';
import { CHIPTUNE_REFERENCE, CHIPTUNE_WAVES } from './chiptune-reference';

const tone = (w: string, v = .2, a = .001, d = .2, s = 0, r = .015): SynthOperator =>
  ({ w, v, a, h: 0, d, s, r });

// Xylophone's sampled C4 body emphasizes 4/6/9/11, with quieter higher partials.
// Keep them on one carrier so a fourth operator can modulate their brightness.
const xylophoneOvertones = Array<number>(22).fill(0);
for (const [harmonic, amplitude] of [[4, .24], [6, .22], [9, .11], [11, .095],
  [14, .026], [16, .034], [19, .011], [21, .028]]) xylophoneOvertones[harmonic] = amplitude;
const xylophoneTuning = 2 ** (-10 / 1200);
const XYLOPHONE: readonly SynthOperator[] = [
  { ...tone('sine', .25, .001, 1.2, 0, .18), t: xylophoneTuning },
  { ...tone('wXylophoneOvertones', .25, .001, 1.2, 0, .18), t: xylophoneTuning },
  // Brief FM roughens the overtone carrier's attack, then fades within ~150ms.
  { ...tone('sine', .16, .001, .04, 0, .025), g: 2, t: 1 },
  // ~3.4Hz shimmer affects the overtones, while the fundamental stays steady.
  // Matching decay/release constants retain relative modulation depth through note-off.
  { ...tone('sine', .2, .001, 1.2, 0, .18), g: 12, t: 0, f: 3.36, relativeAmplitude: true },
];

/** Sampled GM slots are deliberately synthesized; the stationary chip loops use
 * compact Fourier waves and envelopes measured from chiptune_soundfont_4.0.sf2.
 * Every slot is explicit, with at most four operators. No stock GM patches leak in. */
function melodic(program: number): readonly SynthOperator[] {
  if (CHIPTUNE_REFERENCE[program]) return CHIPTUNE_REFERENCE[program];
  if (program <= 4) return [tone('triangle', .24, .001, .4), { ...tone('square', .035, .001, .06), t: 2 }];
  if (program === 13) return XYLOPHONE;
  if ([11, 14, 114].includes(program)) return [tone('sine', .25, .001, .35, 0, .08),
    { ...tone('sine', .08, .001, .13, 0, .04), t: program === 14 ? 2.76 : 3 }];
  if (program === 20) return [tone('square', .16, .015, .1, 1)];
  if (program === 24 || program === 25 || program === 45) return [tone('triangle', .25, .001, .18),
    { ...tone('square', .05, .001, .045), t: 2 }];
  if (program === 38) return [tone('sawtooth', .18, .001, .25), { ...tone('sine', .12, .001, .3), t: .5 }];
  if (program === 39 || program === 63) return [tone('square', .16, .01, .22, .55),
    { ...tone('sawtooth', .09, .01, .22, .5), f: 1.2 }];
  if (program === 47 || program === 116 || program === 117 || program === 118) return [
    { ...tone('sine', .3, .001, .12, 0, .04), p: .4, q: .035 }, tone('n0', .035, .001, .025)];
  if (program === 55) return [tone('sawtooth', .12, .001, .12),
    { ...tone('square', .1, .001, .16), t: 2 }, { ...tone('triangle', .15, .001, .2), t: .5 }];
  if (program === 56) return [tone('sawtooth', .19, .025, .1, .8), { ...tone('square', .035, .02, .1, .8), t: 2 }];
  if (program === 71) return [tone('square', .17, .015, .1, .8)];
  if (program === 73) return [tone('sine', .24, .02, .1, .9), tone('n0', .008, .01, .04)];
  if (program === 84) return [tone('square', .15, .001, .1, .8), { ...tone('sawtooth', .06, .001, .1, .8), t: 2 }];
  if (program === 96) return [tone('triangle', .17, .001, .25), { ...tone('sine', .1, .001, .4), t: 3 }];
  if (program === 101) return [tone('square', .1, .3, .2, .8, .25), { ...tone('sine', .1, .1, .3, .8, .25), f: 2 }];
  if (program === 115) return [{ ...tone('square', .18, .001, .018), t: 2 }];
  if (program === 119) return [tone('n0', .16, .3, .06, 0, .04)];
  if (program === 120 || program === 121) return [tone('n0', .12, .001, .055), tone('triangle', .08, .001, .06)];
  if (program === 122 || program === 126) return [tone('n0', .14, .5, .5, .7, .3)];
  if (program === 123) return [{ ...tone('sine', .14, .01, .1, 1), t: 2 }, { ...tone('sine', 2, .01, .1, 1), g: 1, t: 1.5 }];
  if (program === 124) return [{ ...tone('square', .12, .001, .1, 1), t: 2 }, { ...tone('sine', .8, .001, .1, 1), g: 11, t: .04 }];
  if (program === 125) return [tone('n0', .18, .03, .1, 1), { ...tone('sine', .8, .001, .1, 1), g: 11, t: .03 }];
  if (program === 127) return [tone('n0', .3, .001, .045), { ...tone('square', .08, .001, .025), p: .1, q: .02 }];
  throw new Error(`Missing chiptune GM patch ${program}`);
}

function drum(note: number): readonly SynthOperator[] {
  if (note <= 36) return [{ ...tone('sine', .55, 0, .07), t: 0, f: 150, p: .32, q: .025 }, tone('n0', .06, 0, .012)];
  if ([38, 40].includes(note)) return [tone('n0', .32, 0, .045), { ...tone('triangle', .18, 0, .035), t: 0, f: 180, p: .6, q: .02 }];
  if (note === 39) return [tone('n0', .3, 0, .06)];
  if ([41, 43, 45, 47, 48, 50, 64, 65, 66].includes(note)) return [
    { ...tone('triangle', .32, 0, .09), t: 0, f: 75 + (note - 41) * 8, p: .65, q: .025 }];
  if ([42, 44, 46, 49, 51, 52, 53, 55, 57, 59].includes(note)) return [
    { ...tone('n0', .18, 0, [42, 44].includes(note) ? .018 : note === 46 ? .09 : .22), t: 3 },
    { ...tone('square', .025, 0, .03), t: 0, f: 6300 }];
  if ([54, 58, 69, 70, 73, 74, 78, 79].includes(note)) return [{ ...tone('n0', .22, 0, .035), t: 2 }];
  if ([56, 67, 68, 75, 76, 77].includes(note)) return [
    { ...tone('square', .2, 0, .025), t: 0, f: 400 + (note - 56) * 50 },
    { ...tone('sine', .06, 0, .015), t: 0, f: 1200 }];
  if ([80, 81].includes(note)) return [{ ...tone('sine', .22, 0, note === 80 ? .04 : .3), t: 0, f: 2200 }];
  if ([71, 72].includes(note)) return [{ ...tone('sine', .24, 0, .1), t: 0, f: 1800, p: .7, q: .06 }];
  return [{ ...tone('sine', .3, 0, .065), t: 0, f: 240 + (note - 60) * 30, p: .75, q: .03 }];
}

export const CHIPTUNE_PATCHES: SynthPatchBank = {
  melodic: Array.from({ length: 128 }, (_, program) => melodic(program)),
  drums: Array.from({ length: 47 }, (_, index) => drum(index + 35)),
  waves: { ...CHIPTUNE_WAVES, wXylophoneOvertones: { real: Array<number>(22).fill(0), imag: xylophoneOvertones } },
};
