import type { CompositionProject, CompositionTrack, ArrangementSection, ArrangedPart, MixChannelSettings } from './project.model';
import { DEFAULT_PROJECT, DEFAULT_TRACKS, DEFAULT_MELODY_SETTINGS, DEFAULT_BASS_SETTINGS,
  DEFAULT_CHORD_SETTINGS, DEFAULT_ARPEGGIO_SETTINGS, DEFAULT_DRUM_SETTINGS, DEFAULT_ARRANGEMENT, DEFAULT_MIX } from './project.model';
import { generateTrackPresence } from '../music/arrangement-generator';
import type { SectionTypeChanceSettings } from './section-type-chances';

export function copyDefaultProject(chances: SectionTypeChanceSettings, random: () => number): CompositionProject {
  const tracks = DEFAULT_TRACKS.map((track) => ({ ...track, mix: { ...track.mix }, generatorSettings: { ...track.generatorSettings } } as CompositionTrack));
  return {
    ...DEFAULT_PROJECT,
    progression: [...DEFAULT_PROJECT.progression],
    melody: { ...DEFAULT_MELODY_SETTINGS },
    bass: { ...DEFAULT_BASS_SETTINGS },
    chords: { ...DEFAULT_CHORD_SETTINGS },
    arpeggio: { ...DEFAULT_ARPEGGIO_SETTINGS },
    drums: { ...DEFAULT_DRUM_SETTINGS },
    arrangement: copyDefaultArrangement(tracks, chances, random),
    mix: copyDefaultMix(),
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

function copyDefaultMix(): Record<ArrangedPart, MixChannelSettings> {
  return {
    melody: { ...DEFAULT_MIX.melody },
    bass: { ...DEFAULT_MIX.bass },
    chords: { ...DEFAULT_MIX.chords },
    arpeggio: { ...DEFAULT_MIX.arpeggio },
    drums: { ...DEFAULT_MIX.drums },
  };
}
