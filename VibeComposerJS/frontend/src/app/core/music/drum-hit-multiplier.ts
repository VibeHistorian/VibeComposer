import type { DrumSettings } from '../project/project.model';
import { rotateLeft } from './arpeggio-pattern';

export const DRUM_HIT_MULTIPLIERS = [0.5, 0.75, 1.5, 2] as const;

/** DrumGUI's immediate transform; GUI bounds clamp truncated hit counts to 1–32. */
export function multiplyDrumHits(settings: DrumSettings, multiplier: number): Partial<DrumSettings> {
  if (!(DRUM_HIT_MULTIPLIERS as readonly number[]).includes(multiplier) || settings.rhythm === 'melody') {
    throw new RangeError('Unsupported drum hit multiplier.');
  }
  const oldHits = settings.hitsPerPattern ?? 4;
  const patch: Partial<DrumSettings> = { hitsPerPattern: Math.max(1, Math.min(32, Math.trunc(oldHits * multiplier))) };
  if (multiplier !== 0.5 && settings.rhythm === 'custom') {
    // Java repeats the old unflipped visible grid, then clears Shift; flip and velocities retain ownership.
    const visible = rotateLeft((settings.customPattern ?? Array<number>(32).fill(1)).slice(0, oldHits), -(settings.patternShift ?? 0));
    return { ...patch, patternShift: 0, customPattern: Array.from({ length: 32 }, (_, index) => visible[index % visible.length]) };
  }
  return patch;
}
