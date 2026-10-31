import type { ArrangementSection } from '../project/arrangement.model';
import type { CompositionTrack } from '../project/track.model';
import type { PhraseNote } from './phrase-note';
import { applyPhraseRangeEdits } from './phrase-range-edit';
import { STATIC_RHYTHMS } from './rhythm-patterns';

export interface SectionEventWindow { readonly startBeat: number; readonly endBeat: number; }

export const PRE_DROP_CUT_KEYS = ['rhythm', 'hitsPerPattern', 'euclideanPulses', 'patternShift', 'patternFlip',
  'customPattern', 'customDurations', 'useCustomVelocities', 'customVelocities', 'chordSpan', 'chordSpanFill', 'fillFlip',
  'patternRepeat', 'pauseChance', 'exceptionChance', 'swingPercent', 'offset', 'feedbackCount',
  'feedbackDuration', 'feedbackVol', 'noteLengthMultiplier', 'noteLengthPercent', 'pattern', 'arpContour',
  'arpContourChordMode', 'arpPatternCustom', 'useOctaveAdjustments', 'patternJoinMode', 'strum', 'strumType', 'strumPauseChance'] as const;

/** A final-beat gate, reduced for short/nonuniform final slots. Never repeat per progression cycle. */
export function preDropCutWindow(section: ArrangementSection): SectionEventWindow | undefined {
  const durations = section.customDurations ?? [4], multiplier = section.context?.beatDurationMultiplier ?? 1;
  const endBeat = Array.from({ length: section.measures }, (_, i) => durations[i % durations.length] * multiplier)
    .reduce((sum, value) => sum + value, 0);
  if (endBeat <= 4) return undefined;
  const lastSlot = durations[(section.measures - 1) % durations.length] * multiplier;
  return { startBeat: endBeat - Math.min(1, endBeat / 8, lastSlot / 2), endBeat };
}

export function preDropCutBlockedKeys(track: CompositionTrack, section: ArrangementSection): readonly string[] {
  return PRE_DROP_CUT_KEYS.filter(key => Object.hasOwn(section.rolePartOverrides?.[track.role] ?? {}, key)
    || Object.hasOwn(section.trackPartOverrides?.[track.id] ?? {}, key));
}

/** Manual windows block the entire event, including silence and notes sustaining into its gate. */
export function preDropCutEligibility(track: CompositionTrack, section: ArrangementSection): 'manual' | 'overridden' | 'incompatible' | undefined {
  if (section.trackPhrases?.[track.id] !== undefined || track.editedPhrase !== undefined && !section.generatedTrackIds?.includes(track.id)) return 'manual';
  if (preDropCutBlockedKeys(track, section).length) return 'overridden';
  const window = preDropCutWindow(section);
  if (!window || track.role !== 'chords' && track.role !== 'arpeggio') return 'incompatible';
  const s = { ...track.generatorSettings, ...section.rolePartOverrides?.[track.role], ...section.trackPartOverrides?.[track.id] };
  if (![...STATIC_RHYTHMS, 'euclid'].includes(s.rhythm ?? 'full') || s.customPattern || s.customDurations?.length
    || s.useCustomVelocities || (s.chordSpan ?? 1) !== 1 || (s.offset ?? 0) !== 0 || (s.feedbackCount ?? 0) !== 0
    || track.role === 'arpeggio' && (track.generatorSettings.pattern === 'custom' || track.generatorSettings.arpContour?.length)) return 'incompatible';
  const edits = section.generatedTrackIds?.includes(track.id) ? undefined : track.phraseEdits;
  // Half-beat guard covers the maximum output timing+duration humanization of manual notes.
  if (edits?.some(edit => edit.startBeat < window.endBeat + .5 && Math.min(edit.endBeat, window.endBeat) > window.startBeat - .5)) return 'manual';
  const notes = applyPhraseRangeEdits([], edits, window.endBeat, { startBeat: -durationsFirst(section), endBeat: window.endBeat + durationsLast(section) });
  if (notes.some(note => note.startBeat < window.endBeat + .5 && note.startBeat + note.durationBeats > window.startBeat - .5)) return 'manual';
  return undefined;
}

const durationsFirst = (section: ArrangementSection) => (section.customDurations?.[0] ?? 4) * (section.context?.beatDurationMultiplier ?? 1);
const durationsLast = (section: ArrangementSection) => (section.customDurations?.[(section.measures - 1) % section.customDurations.length] ?? 4)
  * (section.context?.beatDurationMultiplier ?? 1);

export function resolvePreDropCut(track: CompositionTrack, section: ArrangementSection): SectionEventWindow | undefined {
  const choice = section.variations?.choices.find(c => c.trackId === track.id);
  return choice?.kind === 'pre-drop-cut' && choice.enabled && choice.amount === -1 && section.trackPresence[track.id]
    && preDropCutEligibility(track, section) === undefined ? preDropCutWindow(section) : undefined;
}

/** Shared output gate. Keep onsets outside [start,end), stop crossing sustains without resuming them. */
export function applySectionCut<T extends PhraseNote>(notes: readonly T[], window: SectionEventWindow): T[] {
  return notes.flatMap(note => {
    if (note.startBeat >= window.startBeat && note.startBeat < window.endBeat) return [];
    if (note.startBeat < window.startBeat && note.startBeat + note.durationBeats > window.startBeat)
      return [{ ...note, durationBeats: window.startBeat - note.startBeat }];
    return [note];
  });
}
