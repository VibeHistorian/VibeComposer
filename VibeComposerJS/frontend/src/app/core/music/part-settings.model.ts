import type { ChordSpanFill } from './chord-span-fill';
import type { BassRhythm, RhythmPattern } from './rhythm-patterns';
import type { StrumType } from './chord-strum';
import type { ArpeggioPattern } from './arpeggio-pattern';

export type ChordRhythm = RhythmPattern;

export type ChordVoicing = 'close' | 'open';

export type ArpeggioRate = 'eighth' | 'sixteenth';

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
  readonly rhythm?: 'melody1' | 'custom';
  readonly customMelodyDurations?: import('./melody-durations').MelodyDurations;
  readonly customDurationChances?: readonly number[];
  readonly customDurationRandomWeighting?: boolean;
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
