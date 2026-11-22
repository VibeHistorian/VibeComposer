import type { CommonPartSettings, PatternJoinMode } from './part-settings.model';
import { partRhythmMask } from './rhythm-pattern';
import { RHYTHM_PATTERNS, type GridRhythm } from './rhythm-patterns';

export const MIN_MELODY_GRID_ATTACKS = 2;
const MELODY_GRID_ATTACK_ERROR = 'A Melody pattern needs at least two attacks per chord after Join. Add hits or change the pattern, shift, flip or Join mode.';

export function isMelodyGrid(rhythm: unknown): rhythm is GridRhythm {
  return RHYTHM_PATTERNS.includes(rhythm as GridRhythm);
}

/** Signed lengths in grid steps. Joining stays within the current chord. */
export function melodyGridSteps(mask: readonly number[], join: PatternJoinMode = 'NOJOIN'): number[] {
  const steps: number[] = [];
  for (let index = 0; index < mask.length;) {
    const active = mask[index] === 1;
    let end = index + 1;
    const absorbed = !active || join === 'EXPAND' ? 0 : join === 'JOIN' ? 1 : undefined;
    while (end < mask.length && mask[end] === absorbed) end++;
    steps.push((end - index) * (active ? 1 : -1));
    index = end;
  }
  return steps;
}

export function melodyGrid(settings: CommonPartSettings & { rhythm?: unknown; patternJoinMode?: PatternJoinMode }): number[] | undefined {
  if (!isMelodyGrid(settings.rhythm)) return undefined;
  const steps = melodyGridSteps(partRhythmMask({ ...settings, rhythm: settings.rhythm }), settings.patternJoinMode);
  if (steps.filter(value => value > 0).length < MIN_MELODY_GRID_ATTACKS) throw new RangeError(MELODY_GRID_ATTACK_ERROR);
  return steps;
}

/** Balance forced block lengths across the chord, with two to four pitches per block. */
export function melodyGridBlocks(steps: readonly number[], hits: number): number[][] {
  const attacks = steps.filter(value => value > 0).length;
  const count = Math.ceil(attacks / 4);
  const blocks: number[][] = Array.from({ length: count }, () => []);
  let block = 0, sounded = 0;
  for (const value of steps) {
    if (value > 0 && sounded >= Math.ceil((block + 1) * attacks / count)) block++;
    blocks[block].push(value / hits * 4);
    if (value > 0) sounded++;
  }
  return blocks;
}
