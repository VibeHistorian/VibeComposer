import type { ArrangementSection } from '../project/arrangement.model';
import type { CompositionTrack } from '../project/track.model';
import type { PhraseNote } from './phrase-note';
import { accompanimentEventEligibility, PRE_DROP_CUT_KEYS, type SectionEventWindow } from './arrangement-boundary-events';

export const VELOCITY_ENVELOPE_KEYS = [...PRE_DROP_CUT_KEYS, 'velocityMin', 'velocityMax'] as const;

/** Sampled at note onsets; this is a velocity envelope, not continuous volume automation. */
export function velocityEnvelopeWindow(section: ArrangementSection): SectionEventWindow | undefined {
  const durations = section.customDurations ?? [4], multiplier = section.context?.beatDurationMultiplier ?? 1;
  const endBeat = Array.from({ length: section.measures }, (_, i) => durations[i % durations.length] * multiplier)
    .reduce((sum, value) => sum + value, 0);
  return endBeat > 4 ? { startBeat: endBeat - Math.min(8, endBeat / 2), endBeat } : undefined;
}

export function velocityEnvelopeEligibility(track: CompositionTrack, section: ArrangementSection,
  amount: -1 | 1): 'manual' | 'overridden' | 'incompatible' | undefined {
  if (VELOCITY_ENVELOPE_KEYS.some(key => Object.hasOwn(section.rolePartOverrides?.[track.role] ?? {}, key)
    || Object.hasOwn(section.trackPartOverrides?.[track.id] ?? {}, key))
    || Object.hasOwn(section.context?.velocityPercent ?? {}, track.role)) return 'overridden';
  const status = accompanimentEventEligibility(track, section, velocityEnvelopeWindow(section));
  if (status) return status;
  const low = track.generatorSettings.velocityMin ?? 69, high = track.generatorSettings.velocityMax ?? 89;
  // Reject ranges whose rounded velocities cannot move in the requested direction.
  return amount > 0 ? low >= 127 || high <= 3 ? 'incompatible' : undefined
    : high <= 2 ? 'incompatible' : undefined;
}

export function resolveVelocityEnvelope(track: CompositionTrack, section: ArrangementSection):
  (SectionEventWindow & { readonly amount: -1 | 1 }) | undefined {
  const choice = section.variations?.choices.find(c => c.trackId === track.id);
  const window = velocityEnvelopeWindow(section);
  return choice?.kind === 'accompaniment-velocity-ramp' && choice.enabled && section.trackPresence[track.id] && window
    && velocityEnvelopeEligibility(track, section, choice.amount) === undefined ? { ...window, amount: choice.amount } : undefined;
}

export function applyVelocityEnvelope<T extends PhraseNote>(notes: readonly T[], window: SectionEventWindow, amount: -1 | 1): T[] {
  return notes.map(note => {
    if (note.startBeat < window.startBeat || note.startBeat >= window.endBeat || note.velocity <= 0) return note;
    const progress = (note.startBeat - window.startBeat) / (window.endBeat - window.startBeat);
    const gain = 1 + (amount > 0 ? .15 : -.2) * progress;
    return { ...note, velocity: Math.max(1, Math.min(127, Math.round(note.velocity * gain))) };
  });
}
