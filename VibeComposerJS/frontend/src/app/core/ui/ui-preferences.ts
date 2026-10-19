import { PART_CONTROLS } from '../music/part-settings';
import { decodeAppearance, defaultAppearance, type AppearancePreferences } from './appearance';
import { ARRANGED_PARTS, type ArrangedPart } from '../project/project.model';
import type { DrumMappingPreference } from '../music/drum-instruments';
import { DEFAULT_EXPORT_NAME_FIELDS, EXPORT_NAME_FIELDS, type ExportNameField } from '../project/export-filename';

export type QuickSettings = Readonly<Record<ArrangedPart, readonly string[]>>;

/** Joined controls share placement and inheritance in the musical editor. */
export function combinedControlKeys(key: string, role?: ArrangedPart): readonly string[] {
  const melodyKeys = ['rhythm', 'patternFlip', 'customMelodyDurations', 'customDurationChances', 'customDurationRandomWeighting'];
  if (role === 'melody' && melodyKeys.includes(key)) return melodyKeys;
  if (['rhythm', 'patternFlip', 'customPattern'].includes(key)) return ['rhythm', 'patternFlip', 'customPattern'];
  if (key === 'chordSpanFill' || key === 'fillFlip') return ['chordSpanFill', 'fillFlip'];
  return [key];
}
export interface UiPreferences {
  readonly kind: 'vibecomposer-ui-preferences';
  readonly schemaVersion: number;
  readonly compactHistory: boolean;
  readonly syncScoreMix: boolean;
  readonly scoreChordGrid: boolean;
  readonly restartPlaybackOnCompose: boolean;
  readonly limitInstrumentsToPool: boolean;
  readonly drumMapping: DrumMappingPreference;
  readonly discardPhraseNotesOnMidiDrop: boolean;
  readonly exportNameFields: readonly ExportNameField[];
  readonly quickSettings: QuickSettings;
  readonly appearance: AppearancePreferences;
}

export function defaultQuickSettings(): QuickSettings {
  return {
    melody: [
      'chordSpanFill', 'fillFlip', 'rhythm', 'patternFlip', 'customMelodyDurations', 'customDurationChances', 'customDurationRandomWeighting', 'speed', 'fillPauses', 'chordNoteChoices', 'melodyPatternOffsets',
      'patternFlexible', 'pauseChance', 'swingPercent', 'transpose', 'velocityMin', 'velocityMax',
      'feedbackCount', 'feedbackDuration',
    ],
    bass: [
      'chordSpanFill', 'fillFlip', 'rhythm', 'hitsPerPattern', 'euclideanPulses', 'patternShift',
      'patternFlip', 'customPattern', 'chordSpan', 'noteVariation', 'octaveInterval', 'transpose',
      'velocityMin', 'velocityMax', 'noteLengthMultiplier',
    ],
    chords: [
      'chordSpanFill', 'fillFlip', 'rhythm', 'hitsPerPattern', 'patternShift', 'patternFlip',
      'customPattern', 'chordSpan', 'chordNotesStretch', 'stretchEnabled', 'strum', 'strumType',
      'strumPauseChance', 'voicing', 'noteLengthPercent', 'transpose',
    ],
    arpeggio: [
      'chordSpanFill', 'fillFlip', 'rhythm', 'hitsPerPattern', 'patternFlip', 'customPattern',
      'chordSpan', 'patternRepeat', 'pauseChance', 'chordNotesStretch', 'stretchEnabled', 'pattern',
      'arpPatternRotate', 'transpose',
    ],
    drums: [
      'chordSpanFill', 'fillFlip', 'rhythm', 'hitsPerPattern', 'euclideanPulses', 'patternShift',
      'patternFlip', 'customPattern', 'velocityMin', 'velocityMax', 'chordSpan', 'pauseChance',
      'swingPercent', 'ghosts',
    ],
  };
}

export function defaultUiPreferences(): UiPreferences {
  return { kind: 'vibecomposer-ui-preferences', schemaVersion: 1, compactHistory: true, syncScoreMix: false, scoreChordGrid: false, restartPlaybackOnCompose: true, limitInstrumentsToPool: false, drumMapping: 'auto', discardPhraseNotesOnMidiDrop: false, exportNameFields: DEFAULT_EXPORT_NAME_FIELDS, quickSettings: defaultQuickSettings(), appearance: defaultAppearance() };
}

/** Decode atomically, accepting only UI data and retaining the code's control order. */
export function decodeUiPreferences(value: unknown): UiPreferences | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const data = value as Record<string, unknown>;
  if (data['kind'] !== 'vibecomposer-ui-preferences' || typeof data['compactHistory'] !== 'boolean'
      || !data['quickSettings'] || typeof data['quickSettings'] !== 'object' || Array.isArray(data['quickSettings'])) return null;
  const settings = data['quickSettings'] as Record<string, unknown>;
  const fields = data['exportNameFields'];
  if (fields !== undefined && (!Array.isArray(fields) || fields.some(field => !EXPORT_NAME_FIELDS.some(choice => choice.id === field)))) return null;
  const exportNameFields = fields === undefined ? DEFAULT_EXPORT_NAME_FIELDS
    : EXPORT_NAME_FIELDS.filter(field => (fields as unknown[]).includes(field.id)).map(field => field.id);
  if (data['syncScoreMix'] !== undefined && typeof data['syncScoreMix'] !== 'boolean') return null;
  if (data['scoreChordGrid'] !== undefined && typeof data['scoreChordGrid'] !== 'boolean') return null;
  if (data['restartPlaybackOnCompose'] !== undefined && typeof data['restartPlaybackOnCompose'] !== 'boolean') return null;
  if (data['limitInstrumentsToPool'] !== undefined && typeof data['limitInstrumentsToPool'] !== 'boolean') return null;
  if (data['drumMapping'] !== undefined && !['auto', 'general-midi', 'chromatic'].includes(data['drumMapping'] as string)) return null;
  if (data['discardPhraseNotesOnMidiDrop'] !== undefined && typeof data['discardPhraseNotesOnMidiDrop'] !== 'boolean') return null;
  const appearance = data['appearance'] === undefined ? defaultAppearance() : decodeAppearance(data['appearance']);
  if (!appearance) return null;
  const quickSettings = { ...defaultQuickSettings() };
  for (const role of ARRANGED_PARTS) {
    const keys = settings[role];
    if (!Array.isArray(keys) || keys.some(key => typeof key !== 'string' || !PART_CONTROLS[role].some(control => control.key === key))) return null;
    quickSettings[role] = PART_CONTROLS[role].filter(control => combinedControlKeys(control.key, role).some(key => keys.includes(key))).map(control => control.key);
  }
  return { kind: 'vibecomposer-ui-preferences', schemaVersion: 1, compactHistory: data['compactHistory'], syncScoreMix: data['syncScoreMix'] === true, scoreChordGrid: data['scoreChordGrid'] === true, restartPlaybackOnCompose: data['restartPlaybackOnCompose'] !== false, limitInstrumentsToPool: data['limitInstrumentsToPool'] === true, drumMapping: data['drumMapping'] as DrumMappingPreference ?? 'auto', discardPhraseNotesOnMidiDrop: data['discardPhraseNotesOnMidiDrop'] === true, exportNameFields, quickSettings, appearance };
}
