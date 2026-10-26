import type { CompositionProject } from '../project/project.model';
import type { ArrangementSection } from '../project/arrangement.model';
import type { PhraseNote } from './phrase-note';
import { sectionChordDurations, sectionDurationBeats } from './section-context';

/** The first/last section slots define the adjacent editable measures. */
export function sectionPhraseBounds(project: CompositionProject, section: ArrangementSection): { startBeat: number; endBeat: number } {
  const durations = sectionChordDurations(project, section);
  return { startBeat: -durations[0], endBeat: sectionDurationBeats(project, section)
    + durations[(section.measures - 1) % durations.length] };
}

/** Song coordinates stay anchored at bar 01, even when a pickup precedes it. */
export function noteTimelineBounds(notes: readonly PhraseNote[], songEnd: number): { startBeat: number; endBeat: number } {
  return notes.reduce((bounds, note) => ({ startBeat: Math.min(bounds.startBeat, note.startBeat),
    endBeat: Math.max(bounds.endBeat, note.startBeat + note.durationBeats) }), { startBeat: 0, endBeat: songEnd });
}
