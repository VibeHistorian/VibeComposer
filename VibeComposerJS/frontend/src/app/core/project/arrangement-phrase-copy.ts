import type { CompositionProject } from './project.model';
import type { ArrangedPart } from '../music/part-role';
import { layOutTrackPhrase } from '../music/phrase';
import { shouldGenerateTrackInSection } from '../music/arrangement-generator';
import { sectionDurationBeats, sectionTranspose } from '../music/section-context';

export interface ArrangementPhraseSource { readonly sectionId: string; readonly trackId?: string; readonly role?: ArrangedPart; }
export interface ArrangementPhraseTarget { readonly sectionId: string; readonly trackId: string; }
export interface ArrangementPhraseCopy { readonly source: ArrangementPhraseSource; readonly target: ArrangementPhraseTarget; }

/** Freeze section-owned sounding material into one section-relative manual replacement. */
export function copyArrangementPhrase(project: CompositionProject, { source, target }: ArrangementPhraseCopy): CompositionProject {
  const section = project.arrangement.find(section => section.id === source.sectionId);
  const destination = project.arrangement.find(section => section.id === target.sectionId);
  const destinationTrack = project.tracks.find(track => track.id === target.trackId);
  if (!section || !destination || !destinationTrack
    || source.sectionId === target.sectionId && source.trackId === target.trackId
    || !source.trackId && !source.role) return project;
  const tracks = project.tracks.filter(track => (source.trackId ? track.id === source.trackId : track.role === source.role)
    && shouldGenerateTrackInSection(section, track));
  const startBeat = project.arrangement.slice(0, project.arrangement.indexOf(section))
    .reduce((sum, section) => sum + sectionDurationBeats(project, section), 0);
  // Store pitches relative to the destination section's transpose.
  const untransposed = { ...project, transposeSemitones: 0 };
  const transpose = sectionTranspose(destination, destinationTrack.role);
  const notes = tracks.flatMap(track => layOutTrackPhrase(untransposed, track, false, true)
    .filter(note => note.sectionId === section.id)
    .map((note, index) => ({ id: 'copy-' + track.id + '-' + index, midi: Math.max(0, Math.min(127, note.midi - transpose)),
      startBeat: note.startBeat - startBeat, durationBeats: note.durationBeats, velocity: note.velocity })));
  if (!notes.length) return project;
  return { ...project, arrangement: project.arrangement.map(section => section.id === target.sectionId
    ? { ...section, trackPresence: { ...section.trackPresence, [target.trackId]: true },
      trackPhrases: { ...section.trackPhrases, [target.trackId]: notes } } : section) };
}
