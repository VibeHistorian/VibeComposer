import type { CompositionProject, CompositionTrack, ArrangementSection } from './project.model';
import { DEFAULT_PROJECT, DEFAULT_TRACKS, DEFAULT_ARRANGEMENT } from './project.model';
import { generateTrackPresence } from '../music/arrangement-generator';
import type { SectionTypeChanceSettings } from './section-type-chances';

export function copyDefaultProject(chances: SectionTypeChanceSettings, random: () => number): CompositionProject {
  const tracks = DEFAULT_TRACKS.map((track) => ({ ...track, mix: { ...track.mix }, generatorSettings: { ...track.generatorSettings } } as CompositionTrack));
  return {
    ...DEFAULT_PROJECT,
    progression: [...DEFAULT_PROJECT.progression],
    arrangement: copyDefaultArrangement(tracks, chances, random),
    tracks,
  };
}

function copyDefaultArrangement(tracks: readonly CompositionTrack[], chances: SectionTypeChanceSettings, random: () => number): ArrangementSection[] {
  return DEFAULT_ARRANGEMENT.map((section) => ({
    ...section,
    chordDegrees: section.chordDegrees ? [...section.chordDegrees] : undefined,
    trackPresence: generateTrackPresence(tracks, chances[section.type], random),
  }));
}
