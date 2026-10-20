import { JavaRandom } from './java-random';
import { chordPitches, getPitchClass, progressionChords, scaleNotes, type ProgressionChord, type ScaleMode } from './harmony';
import type { MelodyGlobalSettings } from './part-settings.model';
import { DEFAULT_MELODY_GLOBAL } from './melody-global-settings';

export function contextualRange(seed: bigint): { rangeMin: number; rangeMax: number } {
  const random = new JavaRandom(seed ^ 0x72616e67654cn), span = [12,14,16,19,24][random.nextInt(5)];
  const center = 55 + random.nextInt(10), rangeMin = center - Math.trunc(span / 2);
  return { rangeMin, rangeMax: rangeMin + span };
}

/** ContextualMelodyRandomizer.targets, in normalized pre-transposition coordinates. */
export function contextualTargets(seed: number, degrees: readonly number[], key: string, scale: ScaleMode,
  settings: MelodyGlobalSettings, custom?: readonly (ProgressionChord | null)[]): number[] {
  const options = { ...DEFAULT_MELODY_GLOBAL, ...settings }, tonic = getPitchClass(key)!;
  const random = new JavaRandom(BigInt(seed) ^ 0x746172676574n), kind = random.nextInt(4), notes = scaleNotes(scale);
  const center = (options.rangeMin + options.rangeMax) / 2, width = (options.rangeMax - options.rangeMin) / 3;
  const direction = options.targetDirection === 'ASC' ? 1 : options.targetDirection === 'DESC' ? -1 : 0;
  let previous = -1;
  return progressionChords(key,scale,degrees,custom).map((chord,index) => {
    const classes = new Set(chordPitches(chord,0).map(p => (p - tonic + 120) % 12));
    const position = degrees.length === 1 ? 0 : index / (degrees.length - 1);
    const contour = direction ? direction * (2 * position - 1) : kind < 2 ? (kind === 0 ? 1 : -1) * (2 * position - 1)
      : (kind === 2 ? 1 : -1) * Math.sin(position * Math.PI);
    const desired = center + contour * width;
    const close = options.ending === 'CLOSED' && index === degrees.length - 1 && classes.has(0);
    let bestPitch = -1, bestDegree = 0, bestScore = -Infinity;
    notes.forEach((pc,degree) => {
      if (close && pc !== 0) return;
      for (let pitch = options.rangeMin; pitch <= options.rangeMax; pitch++) {
        if (pitch % 12 !== pc) continue;
        let score = (classes.has(pc) ? 8 : 0) - Math.abs(pitch - desired) * 0.2 + random.nextDouble() * 1.5;
        if (previous >= 0) {
          score -= Math.abs(pitch - previous) * 0.16;
          score += direction * Math.sign(pitch - previous) * 0.15;
          if (pitch % 12 === previous % 12) score -= 0.6;
        }
        if (score > bestScore) { bestScore = score; bestPitch = pitch; bestDegree = degree; }
      }
    });
    previous = bestPitch;
    return bestDegree;
  });
}
