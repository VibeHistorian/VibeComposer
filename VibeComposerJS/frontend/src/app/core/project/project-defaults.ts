import type { CompositionProject } from './project.model';
import type { CompositionTrack, MixChannelSettings } from './track.model';
import type { ArrangementSection, SectionType } from './arrangement.model';
import type { ArrangedPart } from '../music/part-role';
import type { MelodySettings, BassSettings, ChordSettings, ArpeggioSettings, DrumSettings } from '../music/part-settings.model';
import { generateContextualPresence } from '../music/arrangement-intent';
import type { SectionTypeChanceSettings } from './section-type-chances';

function defaultSection(id: string, type: SectionType, measures: number): ArrangementSection {
  return {
    id,
    type,
    measures,
    trackPresence: {},
  };
}

export const DEFAULT_ARRANGEMENT: readonly ArrangementSection[] = [
  defaultSection('section-1', 'INTRO', 2),
  defaultSection('section-2', 'VERSE1', 4),
  defaultSection('section-3', 'CHORUS1', 4),
  defaultSection('section-4', 'VERSE2', 4),
  defaultSection('section-5', 'CHORUS2', 4),
  defaultSection('section-6', 'OUTRO', 2),
];

export const DEFAULT_MELODY_SETTINGS: MelodySettings = { algorithm: 'block' };

export const DEFAULT_BASS_SETTINGS: BassSettings = {
  rhythm: 'bass1',
  noteVariation: 20,
  octaveInterval: false,
};

export const DEFAULT_CHORD_SETTINGS: ChordSettings = {
  rhythm: 'full',
  voicing: 'close',
  noteLengthPercent: 100,
};

export const DEFAULT_ARPEGGIO_SETTINGS: ArpeggioSettings = {
  pattern: 'up',
  rate: 'eighth',
  octaves: 1,
};

export const DEFAULT_DRUM_SETTINGS: DrumSettings = {
  pitch: 36,
  rhythm: 'full',
  hitsPerPattern: 4,
  swingPercent: 50,
};

export const DEFAULT_MIX: Readonly<Record<ArrangedPart, MixChannelSettings>> = {
  melody: { program: 73, volumePercent: 100, panPercent: 0, muted: false, solo: false },
  bass: { program: 33, volumePercent: 100, panPercent: 0, muted: false, solo: false },
  chords: { program: 0, volumePercent: 100, panPercent: 0, muted: false, solo: false },
  arpeggio: { program: 11, volumePercent: 100, panPercent: 0, muted: false, solo: false },
  drums: { program: 0, volumePercent: 100, panPercent: 0, muted: false, solo: false },
};

export const DEFAULT_TRACKS: readonly CompositionTrack[] = [
  { id: 'track-melody-1', role: 'melody', name: 'M1', midiChannel: 4, generatorSettings: DEFAULT_MELODY_SETTINGS, mix: DEFAULT_MIX.melody },
  { id: 'track-bass-1', role: 'bass', name: 'B1', midiChannel: 1, generatorSettings: DEFAULT_BASS_SETTINGS, mix: DEFAULT_MIX.bass },
  { id: 'track-chords-1', role: 'chords', name: 'C1', midiChannel: 2, generatorSettings: DEFAULT_CHORD_SETTINGS, mix: DEFAULT_MIX.chords },
  { id: 'track-arpeggio-1', role: 'arpeggio', name: 'A1', midiChannel: 3, generatorSettings: DEFAULT_ARPEGGIO_SETTINGS, mix: DEFAULT_MIX.arpeggio },
  { id: 'track-drums-1', role: 'drums', name: 'D1', midiChannel: 10, generatorSettings: DEFAULT_DRUM_SETTINGS, mix: DEFAULT_MIX.drums },
];

export const DEFAULT_PROJECT: CompositionProject = {
  schemaVersion: 12,
  name: 'Untitled composition',
  key: 'C',
  scale: 'major',
  transposeSemitones: 0,
  tempoBpm: 120,
  seed: '42',
  progression: [1, 5, 6, 4],
  arrangement: DEFAULT_ARRANGEMENT,
  editedPhrases: {},
  tracks: DEFAULT_TRACKS,
};

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
  const presence = generateContextualPresence(tracks, DEFAULT_ARRANGEMENT, chances, random);
  return DEFAULT_ARRANGEMENT.map((section, index) => ({
    ...section,
    chordDegrees: section.chordDegrees ? [...section.chordDegrees] : undefined,
    trackPresence: presence[index],
  }));
}
