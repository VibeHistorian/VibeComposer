import type { CompositionTrack } from '../project/track.model';
import type { ArrangementVariation, VariationKind } from './arrangement-variation.model';
import type { CommonPartSettings, PartSettingsPatch, RhythmSettings } from './part-settings.model';
import { STATIC_RHYTHMS, type StaticRhythm } from './rhythm-patterns';
import { partRhythmMask } from './rhythm-pattern';

const GRID_KEYS = ['rhythm', 'hitsPerPattern', 'euclideanPulses', 'patternShift', 'patternFlip', 'chordSpan',
  'customPattern', 'customDurations', 'pauseChance', 'exceptionChance', 'useCustomVelocities', 'customVelocities'] as const;

export function rhythmVariationProtectedKeys(kind: VariationKind): readonly string[] {
  if (kind === 'arp-density') return GRID_KEYS;
  if (kind === 'drum-density') return [...GRID_KEYS, 'ghosts'];
  if (kind === 'drum-ghosts') return [...GRID_KEYS, 'ghosts', 'velocityMin', 'velocityMax'];
  if (kind === 'chord-transition') return [...GRID_KEYS, 'transitionChance', 'transitionSplit', 'patternJoinMode', 'strum'];
  return [];
}

/** Ordinary span-one grids only: the operation must not reinterpret an authored rhythm. */
function simpleGrid(track: CompositionTrack): (RhythmSettings & CommonPartSettings) | undefined {
  const s = track.generatorSettings;
  if (track.role === 'melody' || track.role === 'bass') return undefined;
  const rhythm = s.rhythm ?? 'full';
  if (rhythm !== 'euclid' && !STATIC_RHYTHMS.includes(rhythm as StaticRhythm)) return undefined;
  if (s.customPattern || s.customDurations?.length || s.useCustomVelocities || s.patternFlip
    || (s.chordSpan ?? 1) !== 1 || (s.pauseChance ?? 0) !== 0 || (s.exceptionChance ?? 0) !== 0) return undefined;
  return { ...s, rhythm, hitsPerPattern: s.hitsPerPattern ?? (track.role === 'drums' ? 4 : 8) } as RhythmSettings & CommonPartSettings;
}

function sounded(settings: RhythmSettings): number { return partRhythmMask(settings).filter(value => value > 0).length; }

/** Euclid changes sounded pulses on the same grid; static patterns choose a meaningful adjacent density. */
function densityPatch(grid: RhythmSettings, amount: -1 | 1, maximum: number): PartSettingsPatch {
  const hits = grid.hitsPerPattern ?? 8;
  if (grid.rhythm === 'euclid') {
    const pulses = Math.min(grid.euclideanPulses ?? 4, hits), next = pulses + amount;
    return next >= 1 && next <= hits && (amount < 0 || next <= maximum) ? { euclideanPulses: next } : {};
  }
  const count = sounded(grid);
  const steps = amount > 0 ? [1, 2, 4, 8, 16, 32] : [32, 16, 8, 4, 2, 1];
  const next = steps.find(candidate => (amount > 0 ? candidate > hits && candidate <= maximum : candidate < hits)
    && (amount > 0 ? sounded({ ...grid, hitsPerPattern: candidate }) > count
      : sounded({ ...grid, hitsPerPattern: candidate }) >= 1 && sounded({ ...grid, hitsPerPattern: candidate }) < count));
  return next === undefined ? {} : { hitsPerPattern: next };
}

export function rhythmVariationPatch(track: CompositionTrack, choice: ArrangementVariation): PartSettingsPatch {
  const grid = simpleGrid(track);
  if (!grid) return {};
  const amount = choice.amount;
  switch (choice.kind) {
    case 'arp-density': return track.role === 'arpeggio' && grid.rhythm !== 'full' ? densityPatch(grid, amount, 32) : {};
    case 'drum-density': {
      if (track.role !== 'drums' || track.generatorSettings.ghosts || ![42, 44, 46, 53].includes(track.generatorSettings.pitch)) return {};
      return densityPatch(grid, amount, track.generatorSettings.pitch === 42 ? 16 : 8);
    }
    case 'drum-ghosts': {
      if (track.role !== 'drums' || ![38, 40].includes(track.generatorSettings.pitch) || (grid.hitsPerPattern ?? 4) > 8
        || (track.generatorSettings.velocityMin ?? 69) < 2) return {};
      const mask = partRhythmMask(grid);
      return mask.some(value => value > 0) && mask.some(value => value === 0) ? { ghosts: amount > 0 } : {};
    }
    case 'chord-transition': {
      if (track.role !== 'chords' || (track.generatorSettings.patternJoinMode ?? 'NOJOIN') !== 'NOJOIN'
        || (track.generatorSettings.strum ?? 0) !== 0) return {};
      const split = track.generatorSettings.transitionSplit ?? 625;
      if (split < 500 || split > 875) return {};
      const mask = partRhythmMask(grid);
      if (!mask.some((value, i) => value > 0 && i / mask.length < split / 1000)
        || !mask.some((value, i) => value > 0 && i / mask.length >= split / 1000)) return {};
      const current = track.generatorSettings.transitionChance ?? 0;
      if (amount > 0 && current >= 75 || amount < 0 && current <= 0) return {};
      return { transitionChance: amount > 0 ? Math.min(75, current + 25) : Math.max(0, current - 25) };
    }
    default: return {};
  }
}
