import type { ArrangementSection } from '../project/arrangement.model';
import type { CompositionTrack } from '../project/track.model';
import type { PhraseNote } from './phrase-note';
import { accompanimentEventEligibility, type SectionEventWindow } from './arrangement-boundary-events';
import { JavaRandom } from './java-random';
import { partRhythmMask } from './rhythm-pattern';
import type { RhythmSettings } from './part-settings.model';

export function arpEndFillWindow(section: ArrangementSection): SectionEventWindow | undefined {
  const durations = section.customDurations ?? [4], multiplier = section.context?.beatDurationMultiplier ?? 1;
  const endBeat = Array.from({ length: section.measures }, (_, i) => durations[i % durations.length] * multiplier)
    .reduce((sum, value) => sum + value, 0);
  const lastSlot = durations[(section.measures - 1) % durations.length] * multiplier;
  return endBeat > 4 ? { startBeat: endBeat - Math.min(2, endBeat / 4, lastSlot), endBeat } : undefined;
}

export function arpEndFillEligibility(track: CompositionTrack, section: ArrangementSection): 'manual' | 'overridden' | 'incompatible' | undefined {
  if (track.role !== 'arpeggio' || (track.generatorSettings.patternRepeat ?? 1) !== 1) return 'incompatible';
  const window = arpEndFillWindow(section), status = accompanimentEventEligibility(track, section, window);
  if (status) return status;
  const s = track.generatorSettings;
  if ((s.pauseChance ?? 0) !== 0 || (s.exceptionChance ?? 0) !== 0 || (s.chordSpanFill ?? 'ALL') !== 'ALL' || s.fillFlip) return 'incompatible';
  const hits = s.hitsPerPattern ?? (s.rate === 'sixteenth' ? 16 : 8);
  const mask = partRhythmMask({ ...s, rhythm: (s.rhythm ?? 'full') as RhythmSettings['rhythm'], hitsPerPattern: hits });
  const durations = section.customDurations ?? [4];
  const lastSlot = durations[(section.measures - 1) % durations.length] * (section.context?.beatDurationMultiplier ?? 1);
  const starts = mask.flatMap((value, i) => value > 0 ? [window!.endBeat - lastSlot + i * lastSlot / hits] : []);
  return starts.some((start, i) => start >= window!.startBeat
    && Math.min(starts[i + 1] ?? window!.endBeat, window!.endBeat) - start >= .25 - 1e-9) ? undefined : 'incompatible';
}

export function resolveArpEndFill(track: CompositionTrack, section: ArrangementSection): SectionEventWindow | undefined {
  const choice = section.variations?.choices.find(c => c.trackId === track.id);
  return choice?.kind === 'arp-end-fill' && choice.enabled && choice.amount === 1 && section.trackPresence[track.id]
    && arpEndFillEligibility(track, section) === undefined ? arpEndFillWindow(section) : undefined;
}

function fillRandom(seed: bigint, identity: string): JavaRandom {
  let hash = 0n;
  for (const char of identity) hash = BigInt.asIntN(64, hash * 31n + BigInt(char.charCodeAt(0)));
  return new JavaRandom(BigInt.asIntN(64, seed ^ hash ^ 0x454e4446494c4cn));
}

/** Retrigger existing sounding pitches, after baseline humanization, without consuming its stream.
 * Onsets/metadata outside the window stay owned; skip collisions instead of shortening earlier notes.
 */
export function applyArpEndFill<T extends PhraseNote>(notes: readonly T[], window: SectionEventWindow,
  seed: bigint, identity: string): T[] {
  const ordered = notes.map((note, index) => ({ note, index })).sort((a, b) => a.note.startBeat - b.note.startBeat || a.index - b.index);
  const candidates = ordered.flatMap(({ note, index }, position) => {
    if (note.startBeat < window.startBeat || note.startBeat >= window.endBeat || note.velocity <= 0) return [];
    if (ordered.some(other => other.index !== index && Math.abs(other.note.startBeat - note.startBeat) < 1e-9)) return [];
    const next = ordered[position + 1]?.note.startBeat ?? window.endBeat;
    const gap = Math.min(1, next - note.startBeat, window.endBeat - note.startBeat);
    if (gap < .25 - 1e-9) return [];
    const startBeat = note.startBeat + gap / 2;
    if (ordered.some(other => other.index !== index && other.note.midi === note.midi
      && other.note.startBeat < startBeat && other.note.startBeat + other.note.durationBeats > startBeat)) return [];
    const random = fillRandom(seed, `${identity}/${note.id}/${position}`);
    return [{ index, note, startBeat, endBeat: Math.min(next, window.endBeat, startBeat + gap / 2),
      priority: random.nextDouble(), velocity: Math.max(1, Math.floor(note.velocity * (.7 + random.nextDouble() * .2))) }];
  }).sort((a, b) => b.priority - a.priority || a.index - b.index).slice(0, 4);
  const selected = new Map(candidates.map(candidate => [candidate.index, candidate]));
  return notes.flatMap((note, index) => {
    const hit = selected.get(index);
    if (!hit) return [note];
    return [{ ...note, durationBeats: Math.min(note.durationBeats, hit.startBeat - note.startBeat) },
      { ...note, id: `${note.id}-end-fill-${identity}-${index}`, startBeat: hit.startBeat,
        durationBeats: (hit.endBeat - hit.startBeat) * .8, velocity: hit.velocity }];
  });
}
