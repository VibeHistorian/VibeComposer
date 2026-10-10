import type { DrumSettings } from '../project/project.model';
import { JavaRandom } from './java-random';
import { DRUM_INSTRUMENTS } from './drum-instruments';
import { partRhythmMask } from './rhythm-pattern';

export interface DrumRerollContext {
  readonly remaining?: readonly DrumSettings[];
  readonly onlyAdd?: boolean;
  readonly totalTracks?: number;
}
const isKick = (pitch: number) => pitch === 35 || pitch === 36;
const isSnare = (pitch: number) => pitch === 38 || pitch === 40;

/** Audited DrumGUI.createRandomDrumPanels pitch branches, using an explicit Random stream. */
export function chooseDrumPitches(random: JavaRandom, count: number, remaining: readonly number[] = [], onlyAdd = false): number[] {
  if (!Number.isInteger(count) || count < 0 || count > 64) throw new RangeError('Invalid drum count.');
  const pool = DRUM_INSTRUMENTS.map(instrument => instrument.midi);
  const pitches = Array.from({ length: count }, () => pool[random.nextInt(127) % pool.length]).sort((a, b) => a - b);
  if (!count) return pitches;
  const kicks = remaining.filter(isKick).length, snares = remaining.filter(isSnare).length;
  let index = 0;
  if (!onlyAdd && count >= 3) {
    if (!kicks) { pitches[index++] = 35; pitches[index++] = 36; }
    else if (kicks === 1) pitches[index++] = 36;
    if (!snares) pitches[index++] = random.nextBoolean() ? 38 : 40;
  } else if (onlyAdd) {
    let allowed = [...pool];
    if (snares >= 1) {
      // Preserve Java's two sequential remove(int index) calls, including the shifted second index.
      allowed.splice(3, 1); allowed.splice(4, 1);
    } else if (kicks >= 1) allowed = [38, 40];
    if (kicks >= 2 && snares >= 1) { allowed.splice(0, 1); allowed.splice(0, 1); }
    else if (!kicks) allowed = [35, 36];
    pitches[0] = allowed[random.nextInt(allowed.length)];
  }
  if (!onlyAdd) {
    if (pitches.filter(isKick).length > 1) for (let slot = index; slot < count; slot++) {
      if (isKick(pitches[slot])) pitches[slot] = slot % 2 === 0 ? 46 : 60;
    }
    if (pitches.filter(isSnare).length > 1) for (let slot = index; slot < count; slot++) {
      if (isSnare(pitches[slot])) pitches[slot] = slot % 2 === 0 ? 42 : 44;
    }
    pitches.sort((a, b) => a - b);
  }
  return pitches;
}

/** InstPanel.applyPauseChance/growPattern, stored as an unshifted CUSTOM grid after editing. */
export function bakeDrumPattern(random: JavaRandom, settings: DrumSettings): DrumSettings {
  const hits = settings.hitsPerPattern ?? 8, shift = settings.patternShift ?? 0;
  const grid = partRhythmMask({ ...settings, hitsPerPattern: 32, patternShift: 0, patternFlip: false });
  let rhythm = settings.rhythm;
  const edit = (slot: number, value: number) => {
    grid[slot] = value;
    for (let repeated = slot + hits; repeated < 32 && shift + repeated < 32; repeated += hits) grid[repeated] = value;
    rhythm = 'custom';
  };
  const pause = settings.pauseChance ?? 0;
  let available = grid.slice(0, hits).filter(value => value > 0).length;
  if (pause > 0) for (let slot = 0; slot < hits && available >= 2; slot++) {
    if (random.nextInt(100) < pause && grid[slot] > 0) { edit(slot, 0); available--; }
  }
  if (rhythm !== 'full') {
    const empty = grid.slice(0, hits).flatMap((value, slot) => value < 1 ? [slot] : []);
    if (empty.length && random.nextInt(100) < 5) edit(empty[random.nextInt(empty.length)], 1);
  }
  return { ...settings, pauseChance: 0, rhythm,
    ...(rhythm === 'custom' ? { customPattern: grid } : {}) };
}
