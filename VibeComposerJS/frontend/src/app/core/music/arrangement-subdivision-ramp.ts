import type { ArrangementSection } from '../project/arrangement.model';
import type { CompositionTrack } from '../project/track.model';
import type { PhraseNote } from './phrase-note';
import { accompanimentEventEligibility, type SectionEventWindow } from './arrangement-boundary-events';

/** One section-ending window, independent of progression repetitions. */
export function arpSubdivisionWindow(section: ArrangementSection): SectionEventWindow | undefined {
  const durations = section.customDurations ?? [4], multiplier = section.context?.beatDurationMultiplier ?? 1;
  const endBeat = Array.from({ length: section.measures }, (_, i) => durations[i % durations.length] * multiplier)
    .reduce((sum, duration) => sum + duration, 0);
  return endBeat > 4 ? { startBeat: endBeat - Math.min(8, endBeat / 2), endBeat } : undefined;
}

export function arpSubdivisionEligibility(track: CompositionTrack, section: ArrangementSection): 'manual' | 'overridden' | 'incompatible' | undefined {
  if (track.role !== 'arpeggio') return 'incompatible';
  if (Object.hasOwn(section.rolePartOverrides?.[track.role] ?? {}, 'rate') || Object.hasOwn(section.trackPartOverrides?.[track.id] ?? {}, 'rate')) return 'overridden';
  const status = accompanimentEventEligibility(track, section, arpSubdivisionWindow(section));
  if (status) return status;
  const s = track.generatorSettings;
  const hits = s.hitsPerPattern ?? (s.rate === 'sixteenth' ? 16 : 8);
  if ((s.rhythm ?? 'full') !== 'full' || ![4, 8].includes(hits)
    || (s.patternRepeat ?? 1) !== 1 || (s.pauseChance ?? 0) !== 0 || (s.exceptionChance ?? 0) !== 0
    || (s.chordSpanFill ?? 'ALL') !== 'ALL' || s.fillFlip || s.patternFlip || s.useOctaveAdjustments
    || s.stretchEnabled || (s.patternShift ?? 0) !== 0 || (s.swingPercent ?? 50) !== 50) return 'incompatible';
  // Never create more than eight attacks per beat, even in short/custom slots.
  const durations = section.customDurations ?? [4], multiplier = section.context?.beatDurationMultiplier ?? 1;
  return durations.some(duration => duration * multiplier < 2) ? 'incompatible' : undefined;
}

export function resolveArpSubdivision(track: CompositionTrack, section: ArrangementSection): (SectionEventWindow & { readonly limit: number }) | undefined {
  const choice = section.variations?.choices.find(choice => choice.trackId === track.id);
  const window = arpSubdivisionWindow(section);
  return choice?.kind === 'arp-subdivision-ramp' && choice.enabled && choice.amount === 1 && section.trackPresence[track.id]
    && window && arpSubdivisionEligibility(track, section) === undefined ? { ...window, limit: choice.parameters?.subdivisionLimit ?? 64 } : undefined;
}

/** Subdivide actual sounding gaps using the source pitch. No phrase or output random draws.
 * Four-hit grids stage 8 then 16; eight-hit grids keep 8 then stage 16.
 * Existing attacks retain identity/timing/velocity; only their crossing sustain is shortened.
 */
export function applyArpSubdivision<T extends PhraseNote>(notes: readonly T[], window: SectionEventWindow,
  slots: readonly SectionEventWindow[], hits: number, identity: string, neighbors: readonly T[] = [], limit = 64): T[] {
  const ordered = notes.map((note, index) => ({ note, index })).sort((a, b) => a.note.startBeat - b.note.startBeat || a.index - b.index);
  const midpoint = (window.startBeat + window.endBeat) / 2;
  const additions = new Map<number, T[]>();
  let count = 0;
  for (const { note, index } of ordered) {
    if (count >= limit || note.startBeat < window.startBeat || note.startBeat >= window.endBeat || note.velocity <= 0) continue;
    if (ordered.some(other => other.index !== index && Math.abs(other.note.startBeat - note.startBeat) < 1e-9)) continue;
    const slot = slots.find(slot => note.startBeat >= slot.startBeat && note.startBeat < slot.endBeat);
    if (!slot) continue;
    const factor = (note.startBeat < midpoint ? 8 : 16) / hits;
    if (factor <= 1) continue;
    const baseGap = (slot.endBeat - slot.startBeat) / hits;
    const next = ordered.find(other => other.note.startBeat > note.startBeat + 1e-9)?.note.startBeat ?? window.endBeat;
    const end = Math.min(note.startBeat + baseGap, next, slot.endBeat, window.endBeat,
      note.startBeat < midpoint ? midpoint : window.endBeat);
    const step = baseGap / factor;
    if (step < .125 - 1e-9) continue;
    const added: T[] = [];
    for (let subdivision = 1; subdivision < factor && count < limit; subdivision++) {
      const startBeat = note.startBeat + subdivision * step;
      if (startBeat + .125 > end + 1e-9) break;
      if (ordered.some(other => other.index !== index && other.note.midi === note.midi
        && other.note.startBeat < startBeat && other.note.startBeat + other.note.durationBeats > startBeat)) continue;
      if (neighbors.some(other => other.midi === note.midi && other.startBeat <= startBeat
        && other.startBeat + other.durationBeats > startBeat)) continue;
      added.push({ ...note, id: `${note.id}-subdivision-${identity}-${index}-${subdivision}`, startBeat,
        durationBeats: Math.min(step, end - startBeat) * .8, velocity: Math.max(1, Math.floor(note.velocity * .8)) });
      count++;
    }
    if (added.length) additions.set(index, added);
  }
  return notes.flatMap((note, index) => {
    const added = additions.get(index);
    return !added ? [note] : [{ ...note, durationBeats: Math.min(note.durationBeats, added[0].startBeat - note.startBeat) }, ...added];
  });
}
