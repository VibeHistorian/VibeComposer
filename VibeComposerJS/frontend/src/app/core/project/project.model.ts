import type { ScaleMode } from '../music/harmony';

export type BassRhythm = 'alternating' | 'full' | 'half' | 'tresillo' | 'sparse';
export type ChordRhythm = 'full' | 'half' | 'tresillo' | 'sparse' | 'single';
export type ChordVoicing = 'close' | 'open';
export type ArpeggioPattern = 'up' | 'down' | 'up-down' | 'random';
export type ArpeggioRate = 'eighth' | 'sixteenth';
export type DrumGroove = 'rock' | 'four-on-floor' | 'half-time' | 'sparse';
export type SectionType =
  | 'INTRO' | 'VERSE1' | 'VERSE2' | 'VERSE3' | 'CHORUS1' | 'CHORUS2' | 'HALF_CHORUS'
  | 'BREAKDOWN' | 'CHILL' | 'BUILDUP1' | 'BUILDUP2' | 'CHORUS3' | 'CLIMAX' | 'OUTRO';
export type ArrangedPart = 'bass' | 'chords' | 'arpeggio' | 'drums';

export interface PhraseNote {
  readonly id: string;
  readonly midi: number;
  readonly startBeat: number;
  readonly durationBeats: number;
  readonly velocity: number;
}

export interface ArrangementSection {
  readonly id: string;
  readonly type: SectionType;
  readonly measures: number;
  /** Optional one-chord-per-bar override; absent sections follow the Create progression. */
  readonly chordDegrees?: readonly number[];
  /** Materialized arrangement state; chance settings live with section type preferences. */
  readonly trackPresence: Readonly<Record<string, boolean>>;
}

export interface BassSettings {
  readonly rhythm: BassRhythm;
  /** Chance, in percent, of choosing a chord tone instead of its root. */
  readonly noteVariation: number;
  /** Add a quieter note one octave above each generated bass note. */
  readonly octaveInterval: boolean;
}

export interface ChordSettings {
  readonly rhythm: ChordRhythm;
  readonly voicing: ChordVoicing;
  readonly noteLengthPercent: number;
}

export interface ArpeggioSettings {
  readonly pattern: ArpeggioPattern;
  readonly rate: ArpeggioRate;
  readonly octaves: 1 | 2;
}

export interface DrumSettings {
  readonly groove: DrumGroove;
  readonly swingPercent: number;
}

export interface MixChannelSettings {
  /** General MIDI program number, ignored by the percussion channel. */
  readonly program: number;
  readonly volumePercent: number;
  /** Stereo position from -100 (left) to 100 (right). */
  readonly panPercent: number;
  readonly muted: boolean;
  readonly solo: boolean;
}

interface TrackBase {
  readonly id: string;
  readonly name: string;
  /** MIDI channels are stored one-based to match the channel numbers shown in the UI. */
  readonly midiChannel: number;
  readonly mix: MixChannelSettings;
  readonly editedPhrase?: readonly PhraseNote[];
}

export type CompositionTrack =
  | (TrackBase & { readonly role: 'bass'; readonly generatorSettings: BassSettings })
  | (TrackBase & { readonly role: 'chords'; readonly generatorSettings: ChordSettings })
  | (TrackBase & { readonly role: 'arpeggio'; readonly generatorSettings: ArpeggioSettings })
  | (TrackBase & { readonly role: 'drums'; readonly generatorSettings: DrumSettings });

export interface CompositionProject {
  readonly schemaVersion: 12;
  readonly name: string;
  readonly key: string;
  readonly scale: ScaleMode;
  readonly transposeSemitones?: number;
  readonly tempoBpm: number;
  /** Decimal text preserves Java long seeds when JSON is exported or persisted. */
  readonly seed: string;
  /** One-based diatonic chord degrees in the selected key and scale. */
  readonly progression: readonly number[];
  readonly bass: BassSettings;
  readonly chords: ChordSettings;
  readonly arpeggio: ArpeggioSettings;
  readonly drums: DrumSettings;
  readonly arrangement: readonly ArrangementSection[];
  /** Phrase-level note edits. Missing parts continue to use their seeded generator output. */
  readonly editedPhrases: Readonly<Partial<Record<ArrangedPart, readonly PhraseNote[]>>>;
  readonly mix: Readonly<Record<ArrangedPart, MixChannelSettings>>;
  /** Independent instrument tracks. Legacy role fields above remain for the existing workspace tools. */
  readonly tracks: readonly CompositionTrack[];
}

export const SECTION_TYPES: readonly SectionType[] = [
  'INTRO', 'VERSE1', 'VERSE2', 'VERSE3', 'CHORUS1', 'CHORUS2', 'HALF_CHORUS',
  'BREAKDOWN', 'CHILL', 'BUILDUP1', 'BUILDUP2', 'CHORUS3', 'CLIMAX', 'OUTRO',
];

export const ARRANGED_PARTS: readonly ArrangedPart[] = ['bass', 'chords', 'arpeggio', 'drums'];

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

export const DEFAULT_BASS_SETTINGS: BassSettings = {
  rhythm: 'alternating',
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
  groove: 'rock',
  swingPercent: 50,
};

export const DEFAULT_MIX: Readonly<Record<ArrangedPart, MixChannelSettings>> = {
  bass: { program: 33, volumePercent: 100, panPercent: 0, muted: false, solo: false },
  chords: { program: 0, volumePercent: 100, panPercent: 0, muted: false, solo: false },
  arpeggio: { program: 11, volumePercent: 100, panPercent: 0, muted: false, solo: false },
  drums: { program: 0, volumePercent: 100, panPercent: 0, muted: false, solo: false },
};

export const DEFAULT_TRACKS: readonly CompositionTrack[] = [
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
  bass: DEFAULT_BASS_SETTINGS,
  chords: DEFAULT_CHORD_SETTINGS,
  arpeggio: DEFAULT_ARPEGGIO_SETTINGS,
  drums: DEFAULT_DRUM_SETTINGS,
  arrangement: DEFAULT_ARRANGEMENT,
  editedPhrases: {},
  mix: DEFAULT_MIX,
  tracks: DEFAULT_TRACKS,
};
