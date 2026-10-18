import type { ScaleMode } from '../music/harmony';
import type { ChordSpanFill } from '../music/chord-span-fill';
import type { BassRhythm, RhythmPattern } from '../music/rhythm-patterns';
import type { StrumType } from '../music/chord-strum';
export type { BassRhythm } from '../music/rhythm-patterns';

export type ChordRhythm = RhythmPattern;
export type ChordVoicing = 'close' | 'open';
export type { ArpeggioPattern } from '../music/arpeggio-pattern';
import type { ArpeggioPattern } from '../music/arpeggio-pattern';
export type ArpeggioRate = 'eighth' | 'sixteenth';
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
  /** Sparse section context; omission inherits project/part values. */
  readonly context?: SectionMusicalContext;
  readonly customChords?: readonly (import('../music/harmony').ProgressionChord | null)[];
  readonly customDurations?: readonly number[];
  /** Realized harmony selections; explicit section chords take precedence. */
  readonly useMelodyHarmony?: boolean;
  readonly swapMelody?: boolean;
  readonly id: string;
  readonly type: SectionType;
  readonly measures: number;
  /** Optional one-chord-per-bar override; absent sections follow the Create progression. */
  readonly chordDegrees?: readonly number[];
  /** Materialized arrangement state; chance settings live with section type preferences. */
  readonly trackPresence: Readonly<Record<string, boolean>>;
  readonly rolePartOverrides?: Readonly<Partial<{ [R in ArrangedPart]: Partial<PartSettingsByRole[R]> }>>;
  /** Manual phrase for this section and independent track, in section-relative beats. */
  readonly trackPhrases?: Readonly<Record<string, readonly PhraseNote[]>>;
  /** Use generated notes here even when the global track has a manual phrase. */
  readonly generatedTrackIds?: readonly string[];
  readonly trackPartOverrides?: Readonly<Record<string, PartSettingsPatch>>;
}

export interface SectionMusicalContext {
  readonly tempoBpm?: number;
  readonly swingPercent?: number;
  readonly key?: string;
  readonly scale?: ScaleMode;
  readonly keyChange?: number;
  /** Direct transposition or an ending ii–V cadence in the selected section key. */
  readonly keyChangeType?: 'direct' | 'two-five-one';
  readonly beatDurationMultiplier?: 0.5 | 1 | 2;
}

export interface CommonPartSettings {
  readonly patternSeed?: number;
  /** Thousandths of a beat, matching Java's noteMultiplier units at normal stretch. */
  readonly offset?: number;
  readonly feedbackCount?: number;
  readonly feedbackDuration?: number;
  readonly feedbackVol?: number;
  readonly chordSpan?: number;
  readonly patternRepeat?: number;
  readonly pauseChance?: number;
  readonly exceptionChance?: number;
  readonly swingPercent?: number;
  readonly chordNotesStretch?: number;
  readonly stretchEnabled?: boolean;
  readonly hitsPerPattern?: number;
  readonly patternShift?: number;
  readonly patternFlip?: boolean;
  readonly euclideanPulses?: number;
  readonly customPattern?: readonly number[];
  readonly useCustomVelocities?: boolean;
  readonly customVelocities?: readonly number[];
  /** Which progression chord slots produce notes, independent of section presence. */
  readonly chordSpanFill?: ChordSpanFill;
  readonly fillFlip?: boolean;
  readonly transpose?: number;
  readonly noteLengthMultiplier?: number;
  readonly velocityMin?: number;
  readonly velocityMax?: number;
}

/** Current block-generation part inputs; Java GUIConfig options have a separate global scope. */
export interface MelodySettings extends CommonPartSettings {
  readonly algorithm: 'block';
  readonly speed?: number;
  readonly fillPauses?: boolean;
  readonly chordNoteChoices?: readonly number[];
  readonly melodyPatternOffsets?: readonly number[];
  readonly maxBlockChange?: number;
  readonly blockJump?: number;
  readonly patternFlexible?: boolean;
  readonly doubledRhythmChance?: number;
  readonly maxNoteExceptions?: number;
  readonly noteExceptionChance?: number;
  readonly splitChance?: number;
  readonly leadChordsChance?: number;
  readonly startNoteChance?: number;
  readonly pauseChance?: number;
  readonly swingPercent?: number;
  readonly accents?: number;
  /** Java int part seed; zero follows the project's exact long seed narrowed at generation. */
  readonly patternSeed?: number;
}

/** GUIConfig musical inputs shared by every Melody track and section. */
export interface MelodyGlobalSettings {
  readonly contextualMelody?: boolean;
  readonly rangeMin?: number;
  readonly rangeMax?: number;
  readonly rangeRandomizeOnCompose?: boolean;
  readonly tension?: number;
  readonly ending?: 'OPEN' | 'CLOSED' | 'LOOP';
  readonly form?: 'STATEMENT_VARIATION' | 'QUESTION_ANSWER' | 'SENTENCE' | 'CONTINUOUS';
  readonly style?: 'TONAL' | 'MODAL' | 'JAZZ';
  readonly chromaticApproaches?: boolean;
  readonly phraseBars?: number;
  readonly beatsPerBar?: number;
  /** 0 chord-note index, 1 chord root + scale-degree offset, 2 MIDI 60 + scale-degree offset. */
  readonly blockTargetMode?: number;
  /** Requested tonic/mode note percentages before pauses; these are best-effort pitch repairs. */
  readonly tonicNoteTarget?: number;
  readonly modeNoteTarget?: number;
  readonly chordNoteTarget?: number;
  /** 0 off, 1 semitone above chord tones, 2 also below and two semitones above. */
  readonly replaceAvoidNotes?: number;
  /** Assign longer block durations to tonic/mode-important pitches before pitch exceptions. */
  readonly emphasizeKey?: boolean;
  /** Invert the first Melody's published onset grid for dependent parts; Melody notes stay unchanged. */
  readonly patternFlip?: boolean;
  readonly singleNoteExceptions?: boolean;
  readonly maxDirectionChanges?: number;
  readonly fillPausesPerChord?: boolean;
  /** 0 repeats rhythm, 1 repeats notes, 2 repeats rhythm and notes. */
  readonly patternEffect?: 0 | 1 | 2;
  readonly arpySurprises?: boolean;
  readonly targetNoteVariation?: number;
  readonly targetDirection?: 'ANY' | 'ASC' | 'DESC';
  readonly useDirectionsFromProgression?: boolean;
  /** Copy the first stored Melody track's outline fields during preparation. */
  readonly forceFirstOutline?: boolean;
  readonly newBlocksChance?: number;
  /** Weights in BlockType enum order: Scaley, Neighbory, Arpy, Chordy, Wavy, Interval, Note. */
  readonly blockTypeWeights?: readonly number[];
  /** Empty selects uniformly; otherwise a permutation of absolute changes 0–7. */
  readonly blockChangePreference?: readonly number[];
}

export interface BassSettings extends CommonPartSettings {
  readonly rhythm: BassRhythm;
  /** Omission preserves the translated subdivision path; Java's part default is EXPAND. */
  readonly patternJoinMode?: 'NOJOIN' | 'EXPAND' | 'JOIN';
  /** Chance, in percent, of choosing a chord tone instead of its root. */
  readonly noteVariation: number;
  /** Add a quieter note one octave above each generated bass note. */
  readonly octaveInterval: boolean;
}

/** Shared grid shape; Bass also permits bass1, and old Arpeggio projects may omit rhythm. */
export interface RhythmSettings {
  readonly rhythm: RhythmPattern;
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
}

export interface ChordSettings extends CommonPartSettings, RhythmSettings {
  readonly patternJoinMode?: 'NOJOIN' | 'EXPAND' | 'JOIN';
  readonly transitionChance?: number;
  /** Thousandths of the current chord duration; 625 starts transitions at 62.5%. */
  readonly transitionSplit?: number;
  readonly strum?: number;
  readonly strumType?: StrumType;
  readonly strumPauseChance?: number;
  readonly voicing: ChordVoicing;
  readonly noteLengthPercent: number;
}

export interface ArpeggioSettings extends CommonPartSettings {
  readonly rhythm?: RhythmPattern;
  readonly pattern: ArpeggioPattern;
  readonly rate: ArpeggioRate;
  readonly octaves: 1 | 2;
  readonly arpPatternRotate?: number;
  readonly arpPatternCustom?: readonly number[];
  readonly arpContour?: readonly number[];
  readonly arpContourChordMode?: boolean;
  readonly useOctaveAdjustments?: boolean;
  readonly correctMelodyNotes?: boolean;
}

export interface DrumSettings extends CommonPartSettings, RhythmSettings {
  /** Track identity, edited globally; section musical patches cannot change the instrument. */
  readonly pitch: number;
  readonly swingPercent: number;
  readonly ghosts?: boolean;
  /** Seeded timing variation in ten-thousandths of a beat. */
  readonly humanize?: number;
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
  /** Linear gain percentage: 100 is unity; maximum is +6 dB (about 199.526%). */
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
  readonly rerollLocked?: boolean;
  readonly instrumentLocked?: boolean;
  readonly instrumentPool?: Exclude<InstrumentPool, 'AUTO'>;
}

export type InstrumentPool = 'AUTO' | 'ALL' | 'MELODY' | 'BASS' | 'CHORD' | 'PLUCK' | 'LONG_PAD' | 'DRUM';
export interface InstrumentGenerationPolicy {
  readonly randomize: boolean;
  readonly pool: InstrumentPool;
  readonly sameInstrument?: boolean;
  readonly sustainChance?: number;
}

/** Rules for choosing stored part values, separate from phrase generation inputs. */
export interface ArpeggioGenerationPolicy {
  readonly onCompose?: boolean;
  readonly fixedHits: number;
  readonly randomHits: boolean;
  readonly sameHits: boolean;
  readonly powerOfTwo: boolean;
  readonly fills: boolean;
  readonly patterns: boolean;
  readonly transpose: boolean;
  readonly randomSpan: boolean;
  readonly maxSplit: number;
  readonly lengthMin: number;
  readonly lengthMax: number;
  readonly maxRepeat: number;
  readonly shiftChance: number;
  readonly voicesMode: 'NONE' | 'FIXED' | 'AT_MOST';
  readonly voices: number;
  readonly voicesChance: number;
  readonly velocityMin: number;
  readonly velocityMax: number;
}

export interface ChordGenerationPolicy {
  readonly onCompose?: boolean;
  readonly strum?: boolean;
  readonly struminess?: number;
  readonly split?: boolean;
  readonly maxSplit?: number;
  readonly maxStrumPause?: number;
  readonly expandChance?: number;
  readonly fills: boolean;
  readonly patterns: boolean;
  readonly transpose: boolean;
  readonly varyLength: boolean;
  readonly delay: boolean;
  readonly shiftChance: number;
  readonly voicesMode: 'NONE' | 'FIXED' | 'AT_MOST';
  readonly voices: number;
  readonly voicesChance: number;
  readonly velocityMin: number;
  readonly velocityMax: number;
}

export interface DrumGenerationPolicy {
  readonly dynamicChance?: number;
  readonly overrandomizeChance?: number;
  readonly melodyChance?: number;
  readonly onCompose?: boolean;
  readonly patterns: boolean;
  readonly fills: boolean;
  readonly maxSwing: number;
  readonly randomOffset: boolean;
  readonly hitsMultiplier: '1' | '1/2' | '3/4' | '3/2' | '2';
  readonly shiftChance: number;
}

export interface MelodyGenerationPolicy {
  readonly onCompose?: boolean;
  readonly onRegenerate?: boolean;
  readonly targetsOnCompose?: boolean;
  readonly patternsOnCompose?: boolean;
  readonly rerollSeeds: boolean;
  readonly sameSeed: boolean;
  readonly patterns: boolean;
}

/** Bass uses its audited creation branches with an optional compose reroll. */
export interface TrackGenerationPolicies {
  readonly bass: { readonly onCompose?: boolean };
  readonly chords: ChordGenerationPolicy;
  readonly arpeggio: ArpeggioGenerationPolicy;
  readonly drums: DrumGenerationPolicy;
  readonly melody: MelodyGenerationPolicy;
}
export type TrackGenerationPolicy = TrackGenerationPolicies[ArrangedPart];
export type TrackGenerationPolicyPatch = Partial<ChordGenerationPolicy & ArpeggioGenerationPolicy & DrumGenerationPolicy & MelodyGenerationPolicy>;

export type CompositionTrack =
  | (TrackBase & { readonly role: 'melody'; readonly generatorSettings: MelodySettings })
  | (TrackBase & { readonly role: 'bass'; readonly generatorSettings: BassSettings })
  | (TrackBase & { readonly role: 'chords'; readonly generatorSettings: ChordSettings })
  | (TrackBase & { readonly role: 'arpeggio'; readonly generatorSettings: ArpeggioSettings })
  | (TrackBase & { readonly role: 'drums'; readonly generatorSettings: DrumSettings });

export interface CompositionProject {
  readonly harmonySettings?: import('../music/harmony-voicing').HarmonySettings;
  readonly melodyGlobal?: MelodyGlobalSettings;
  readonly schemaVersion: 12;
  readonly name: string;
  readonly key: string;
  readonly scale: ScaleMode;
  readonly transposeSemitones?: number;
  readonly tempoBpm: number;
  readonly randomizeTempoOnCompose?: boolean;
  readonly randomizeTransposeOnCompose?: boolean;
  /** Decimal text preserves Java long seeds when JSON is exported or persisted. */
  readonly seed: string;
  /** One-based diatonic chord degrees in the selected key and scale. */
  readonly progression: readonly number[];
  /** Explicit Chordlet edits; null entries continue to follow the diatonic mode. */
  readonly progressionChords?: readonly (import('../music/harmony').ProgressionChord | null)[];
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
  readonly trackGenerationPolicies?: Readonly<Partial<TrackGenerationPolicies>>;
  readonly instrumentGenerationPolicies?: Readonly<Partial<Record<ArrangedPart, InstrumentGenerationPolicy>>>;
  readonly trackRerollCounts?: Readonly<Partial<Record<ArrangedPart, number>>>;
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
