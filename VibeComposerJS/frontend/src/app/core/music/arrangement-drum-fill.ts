import type { ArrangementSection } from '../project/arrangement.model';
import type { CompositionTrack } from '../project/track.model';
import { sectionEventEligibility, PRE_DROP_CUT_KEYS, type SectionEventWindow } from './arrangement-boundary-events';
import { arpEndFillWindow } from './arrangement-end-fill';
import { partRhythmMask } from './rhythm-pattern';
import type { RhythmSettings } from './part-settings.model';

export const DRUM_FILL_KEYS = [...PRE_DROP_CUT_KEYS, 'pitch', 'ghosts', 'velocityMin', 'velocityMax'] as const;

/** Snare-only retriggers. Preserve anchors; authored/dependent grids need other adapters. */
export function drumEndFillEligibility(track: CompositionTrack, section: ArrangementSection): 'manual' | 'overridden' | 'incompatible' | undefined {
  if (track.role !== 'drums') return 'incompatible';
  if (DRUM_FILL_KEYS.some(key => Object.hasOwn(section.rolePartOverrides?.drums ?? {}, key)
    || Object.hasOwn(section.trackPartOverrides?.[track.id] ?? {}, key))) return 'overridden';
  const window = arpEndFillWindow(section), status = sectionEventEligibility(track, section, window, ['drums']);
  if (status) return status;
  const s = track.generatorSettings;
  if (![38, 40].includes(s.pitch) || s.ghosts || (s.velocityMin ?? 69) < 2
    || (s.pauseChance ?? 0) !== 0 || (s.exceptionChance ?? 0) !== 0 || (s.patternRepeat ?? 1) !== 1
    || (s.patternShift ?? 0) !== 0 || s.patternFlip || s.fillFlip || (s.chordSpanFill ?? 'ALL') !== 'ALL'
    || (s.swingPercent ?? 50) !== 50) return 'incompatible';
  const hits = s.hitsPerPattern ?? 4;
  if (hits > 8) return 'incompatible';
  const mask = partRhythmMask({ ...s, rhythm: (s.rhythm ?? 'full') as RhythmSettings['rhythm'], hitsPerPattern: hits });
  const durations = section.customDurations ?? [4];
  const lastSlot = durations[(section.measures - 1) % durations.length] * (section.context?.beatDurationMultiplier ?? 1);
  const starts = mask.flatMap((value, i) => value > 0 ? [window!.endBeat - lastSlot + i * lastSlot / hits] : []);
  return starts.some((start, i) => start >= window!.startBeat
    && Math.min(starts[i + 1] ?? window!.endBeat, window!.endBeat) - start >= .25 - 1e-9) ? undefined : 'incompatible';
}

export function resolveDrumEndFill(track: CompositionTrack, section: ArrangementSection): (SectionEventWindow & { readonly limit: number }) | undefined {
  const choice = section.variations?.choices.find(c => c.trackId === track.id), window = arpEndFillWindow(section);
  return choice?.kind === 'drum-end-fill' && choice.enabled && choice.amount === 1 && section.trackPresence[track.id]
    && window && drumEndFillEligibility(track, section) === undefined ? { ...window, limit: choice.parameters?.fillLimit ?? 4 } : undefined;
}
