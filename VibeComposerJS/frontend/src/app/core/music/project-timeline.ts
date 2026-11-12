import type { CompositionProject } from '../project/project.model';
import { resolvedTrackPhrase, type PhraseSource } from './phrase';
import type { PhraseNote } from './phrase-note';
import { noteTimelineBounds } from './phrase-bounds';
import { samePhraseInputs } from './phrase-inputs';
import { beatToSeconds, secondsToBeat, projectTempoMap, sectionChordDurations, type TempoPoint } from './section-context';

export interface TimelineBounds { readonly startBeat: number; readonly endBeat: number; }
export interface TimelineSlot extends TimelineBounds { readonly measure: number; }
export interface TimelineSection extends TimelineBounds {
  readonly sectionId: string;
  readonly startMeasure: number;
  readonly slots: readonly TimelineSlot[];
}
export interface TimelineNote extends PhraseNote { readonly phraseSource?: PhraseSource; readonly sectionId?: string; }
export type TimelineNotes = Readonly<Record<string, readonly TimelineNote[]>>;

/** Immutable song coordinates. Bar 01 stays at zero; elapsed time starts at the first pickup. */
export interface ResolvedProjectTimeline {
  readonly sections: readonly TimelineSection[];
  readonly arrangementBounds: TimelineBounds;
  readonly soundingBounds: TimelineBounds;
  readonly notesByTrack: TimelineNotes;
  readonly tempoMap: readonly TempoPoint[];
  readonly durationSeconds: number;
  beatToSeconds(beat: number): number;
  secondsToBeat(seconds: number): number;
  elapsedSeconds(beat: number): number;
}

const timelines = new WeakMap<CompositionProject, ResolvedProjectTimeline>();
// Retain only the most recent input for reuse across immutable mix-only updates.
let previous: { project: CompositionProject; timeline: ResolvedProjectTimeline } | undefined;

export function resolveProjectTimeline(project: CompositionProject): ResolvedProjectTimeline {
  const cached = timelines.get(project);
  if (cached) { previous = { project, timeline: cached }; return cached; }
  if (previous && samePhraseInputs(previous.project, project)) {
    const timeline = previous.timeline;
    timelines.set(project, timeline);
    previous = { project, timeline };
    return timeline;
  }
  let beat = 0, measure = 1;
  const sections = project.arrangement.map(section => {
    const startBeat = beat, startMeasure = measure;
    const durations = sectionChordDurations(project, section);
    let sectionBeat = 0;
    const slots = Array.from({ length: section.measures }, (_, index) => {
      const slotStart = startBeat + sectionBeat;
      sectionBeat += durations[index % durations.length];
      return Object.freeze({ startBeat: slotStart, endBeat: startBeat + sectionBeat, measure: measure++ });
    });
    beat = startBeat + sectionBeat;
    return Object.freeze({ sectionId: section.id, startBeat, endBeat: beat, startMeasure, slots: Object.freeze(slots) });
  });
  const tempoMap = Object.freeze(projectTempoMap(project).map(point => Object.freeze(point)));
  const notesByTrack = Object.freeze(Object.fromEntries(project.tracks.map(track => [track.id,
    resolvedTrackPhrase(project, track)])));
  const timeline = createTimeline(Object.freeze(sections), Object.freeze({ startBeat: 0, endBeat: beat }),
    tempoMap, notesByTrack);
  timelines.set(project, timeline);
  previous = { project, timeline };
  return timeline;
}

/** Draft playback derives its own bounds without changing the committed timeline or cache. */
export function withTimelineNotes(timeline: ResolvedProjectTimeline, notes: TimelineNotes): ResolvedProjectTimeline {
  const notesByTrack = Object.freeze(Object.fromEntries(Object.entries(notes).map(([id, values]) =>
    [id, Object.freeze(values.map(note => Object.freeze({ ...note })))])));
  return createTimeline(timeline.sections, timeline.arrangementBounds, timeline.tempoMap, notesByTrack);
}

function createTimeline(sections: readonly TimelineSection[], arrangementBounds: TimelineBounds,
  tempoMap: readonly TempoPoint[], notesByTrack: TimelineNotes): ResolvedProjectTimeline {
  // Accumulate track bounds without allocating a flattened copy of every note.
  const soundingBounds = Object.freeze(Object.values(notesByTrack).reduce((bounds, notes) => {
    const track = noteTimelineBounds(notes, arrangementBounds.endBeat);
    return { startBeat: Math.min(bounds.startBeat, track.startBeat), endBeat: Math.max(bounds.endBeat, track.endBeat) };
  }, { ...arrangementBounds }));
  const startSeconds = beatToSeconds(tempoMap, soundingBounds.startBeat);
  return Object.freeze({ sections, arrangementBounds, soundingBounds, notesByTrack, tempoMap,
    durationSeconds: beatToSeconds(tempoMap, soundingBounds.endBeat) - startSeconds,
    beatToSeconds: (beat: number) => beatToSeconds(tempoMap, beat),
    secondsToBeat: (seconds: number) => secondsToBeat(tempoMap, seconds),
    elapsedSeconds: (beat: number) => beatToSeconds(tempoMap, beat) - startSeconds,
  });
}
