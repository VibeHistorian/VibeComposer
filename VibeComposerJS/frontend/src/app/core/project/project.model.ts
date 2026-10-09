import type { ScaleMode } from '../music/harmony';
import type { ChordSpanFill } from '../music/chord-span-fill';
import type { BassRhythm, RhythmPattern } from '../music/rhythm-patterns';
export type { BassRhythm } from '../music/rhythm-patterns';

export type ChordRhythm = RhythmPattern;
export type ChordVoicing = 'close' | 'open';
export type ArpeggioPattern = 'up' | 'down' | 'up-down' | 'random';
export type ArpeggioRate = 'eighth' | 'sixteenth';
export type DrumGroove = 'rock' | 'four-on-floor' | 'half-time' | 'sparse';
export type SectionType =
  | 'INTRO' | 'VERSE1' | 'VERSE2' | 'VERSE3' | 'CHORUS1' | 'CHORUS2' | 'HALF_CHORUS'
  | 'BREAKDOWN' | 'CHILL' | 'BUILDUP1' | 'BUILDUP2' | 'CHORUS3' | 'CLIMAX' | 'OUTRO';
export type ArrangedPart = 'melody' | 'bass' | 'chords' | 'arpeggio' | 'drums';

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
  readonly rolePartOverrides?: Readonly<Partial<{ [R in ArrangedPart]: Partial<PartSettingsByRole[R]> }>>;
  readonly trackPartOverrides?: Readonly<Record<string, PartSettingsPatch>>;
}

export interface CommonPartSettings {
  /** Which progression chord slots produce notes, independent of section presence. */
  readonly chordSpanFill?: ChordSpanFill;
  readonly fillFlip?: boolean;
  readonly transpose?: number;
  readonly noteLengthMultiplier?: number;
  readonly velocityMin?: number;
  readonly velocityMax?: number;
}

/** Minimum supported current block-generation inputs; advanced options are staged separately. */
export interface MelodySettings extends CommonPartSettings {
  readonly algorithm: 'block';
  readonly speed?: number;
  readonly fillPauses?: boolean;
  readonly chordNoteChoices?: readonly number[];
  readonly melodyPatternOffsets?: readonly number[];
  readonly maxBlockChange?: number;
  readonly blockJump?: number;
  readonly patternFlexible?: boolean;
  readonly pauseChance?: number;
  readonly swingPercent?: number;
  readonly accents?: number;
  /** Java int part seed; zero follows the project's exact long seed narrowed at generation. */
  readonly patternSeed?: number;
}

export interface BassSettings extends CommonPartSettings {
  readonly rhythm: BassRhythm;
  /** Chance, in percent, of choosing a chord tone instead of its root. */
  readonly noteVariation: number;
  /** Add a quieter note one octave above each generated bass note. */
  readonly octaveInterval: boolean;
}

export interface ChordSettings extends CommonPartSettings {
  readonly rhythm: ChordRhythm;
  /** Grid subdivisions per four-beat chord; only enabled cells produce notes. */
  readonly hitsPerPattern?: number;
  readonly patternShift?: number;
  readonly patternFlip?: boolean;
  /** Requested Euclidean sounded slots; capped by hitsPerPattern when generating. */
  readonly euclideanPulses?: number;
  /** Unshifted 32-cell binary grid. Hidden cells survive changes to Hits. */
  readonly customPattern?: readonly number[];
  /** Explicit counterpart of Java's non-null customVelocities; disabling retains the grid. */
  readonly useCustomVelocities?: boolean;
  /** 32 audible-subdivision velocities, independent of rhythm Shift/Flip; zero is silent. */
  readonly customVelocities?: readonly number[];
  readonly voicing: ChordVoicing;
  readonly noteLengthPercent: number;
}

export interface ArpeggioSettings extends CommonPartSettings {
  readonly pattern: ArpeggioPattern;
  readonly rate: ArpeggioRate;
  readonly octaves: 1 | 2;
}

export interface DrumSettings extends CommonPartSettings {
  readonly groove: DrumGroove;
  readonly swingPercent: number;
}

export interface PartSettingsByRole {
  readonly melody: MelodySettings;
  readonly bass: BassSettings;
  readonly chords: ChordSettings;
  readonly arpeggio: ArpeggioSettings;
  readonly drums: DrumSettings;
}
export type PartSettingsPatch = Partial<MelodySettings | BassSettings | ChordSettings | ArpeggioSettings | DrumSettings>;
export type PartSettingsScope =
  | { readonly kind: 'global-role'; readonly role: ArrangedPart }
  | { readonly kind: 'global-track'; readonly trackId: string }
  | { readonly kind: 'section-role'; readonly sectionId: string; readonly role: ArrangedPart }
  | { readonly kind: 'section-track'; readonly sectionId: string; readonly trackId: string };

export type LocalPartSettingsScope = Extract<PartSettingsScope, { readonly sectionId: string }>;
export type PartWorkflowResult = 'changed' | 'unchanged' | 'invalid';

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
  | (TrackBase & { readonly role: 'melody'; readonly generatorSettings: MelodySettings })
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
  readonly melody: MelodySettings;
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

/** Explicit Java group correspondence, independent of track IDs and seed derivation. */
export const PART_TYPES: Readonly<Record<ArrangedPart, number>> = { melody: 0, bass: 1, chords: 2, arpeggio: 3, drums: 4 };
export const ARRANGED_PARTS: readonly ArrangedPart[] = ['melody', 'bass', 'chords', 'arpeggio', 'drums'];
/** All five roles have active phrase consumers. */
export const PART_GENERATION_AVAILABLE: Readonly<Record<ArrangedPart, boolean>> = {
  melody: true, bass: true, chords: true, arpeggio: true, drums: true,
};

export function tracksInRoleOrder<T extends { readonly role: ArrangedPart }>(tracks: readonly T[]): T[] {
  return [...tracks].sort((left, right) => PART_TYPES[left.role] - PART_TYPES[right.role]);
}

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
  melody: DEFAULT_MELODY_SETTINGS,
  bass: DEFAULT_BASS_SETTINGS,
  chords: DEFAULT_CHORD_SETTINGS,
  arpeggio: DEFAULT_ARPEGGIO_SETTINGS,
  drums: DEFAULT_DRUM_SETTINGS,
  arrangement: DEFAULT_ARRANGEMENT,
  editedPhrases: {},
  mix: DEFAULT_MIX,
  tracks: DEFAULT_TRACKS,
};
