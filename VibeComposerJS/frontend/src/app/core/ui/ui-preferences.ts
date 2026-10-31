import { decodeShortcuts, defaultShortcuts, type ShortcutBindings } from './shortcuts';
import { PART_CONTROLS } from '../music/part-settings';
import { decodeAppearance, defaultAppearance, type AppearancePreferences } from './appearance';
import { ARRANGED_PARTS, type ArrangedPart } from '../music/part-role';
import type { DrumMappingPreference } from '../music/drum-instruments';
import { DEFAULT_EXPORT_NAME_FIELDS, EXPORT_NAME_FIELDS, type ExportNameField } from '../project/export-filename';

export type QuickSettings = Readonly<Record<ArrangedPart, readonly string[]>>;

/** Joined controls share placement and inheritance in the musical editor. */
export function combinedControlKeys(key: string, role?: ArrangedPart): readonly string[] {
  if (role === 'arpeggio' && ['pattern', 'arpPatternCustom'].includes(key)) return ['pattern', 'arpPatternCustom'];
  for (const keys of [['feedbackCount', 'feedbackDuration'], ['velocityMin', 'velocityMax'], ['rangeMin', 'rangeMax'], ['ending', 'style', 'form']]) {
    if (keys.includes(key)) return keys;
  }
  const gridKeys = role === 'melody' ? [] : ['hitsPerPattern', 'patternShift'];
  const durationKeys = ['rhythm', 'patternFlip', 'customDurations', 'customDurationChances', 'customDurationRandomWeighting'];
  if (role === 'melody' && durationKeys.includes(key)) return durationKeys;
  if ((role === 'bass' || role === 'arpeggio') && [...durationKeys, 'customPattern', ...gridKeys].includes(key)) return [...durationKeys, 'customPattern', ...gridKeys];
  if (['rhythm', 'patternFlip', 'customPattern', ...gridKeys].includes(key)) return ['rhythm', 'patternFlip', 'customPattern', ...gridKeys];
  if (key === 'chordSpanFill' || key === 'fillFlip') return ['chordSpanFill', 'fillFlip'];
  return [key];
}
export interface UiPreferences {
  readonly kind: 'vibecomposer-ui-preferences';
  readonly schemaVersion: number;
  readonly composeBpmMin: number;
  readonly composeBpmMax: number;
  readonly compactHistory: boolean;
  readonly syncScoreMix: boolean;
  readonly scoreChordGrid: boolean;
  readonly scoreGhostNotes: boolean;
  readonly scoreOutlineOtherTracks: boolean;
  readonly restartPlaybackOnCompose: boolean;
  readonly generateArrangementVariations: boolean;
  readonly rerollPresenceWithVariations: boolean;
  readonly limitInstrumentsToPool: boolean;
  readonly drumMapping: DrumMappingPreference;
  readonly discardPhraseNotesOnMidiDrop: boolean;
  readonly manualPhrasesFollowMode: boolean;
  readonly exportNameFields: readonly ExportNameField[];
  readonly quickSettings: QuickSettings;
  readonly appearance: AppearancePreferences;
  readonly shortcuts: ShortcutBindings;
}

export function defaultQuickSettings(): QuickSettings {
  return {
    melody: [
      'chordSpanFill', 'fillFlip', 'ending', 'style', 'form', 'rhythm', 'patternFlip', 'customDurations', 'customDurationChances', 'customDurationRandomWeighting', 'speed', 'fillPauses', 'chordNoteChoices', 'melodyPatternOffsets',
      'patternFlexible', 'pauseChance', 'swingPercent', 'transpose', 'velocityMin', 'velocityMax',
      'feedbackCount', 'feedbackDuration',
    ],
    bass: [
      'chordSpanFill', 'fillFlip', 'rhythm', 'hitsPerPattern', 'euclideanPulses', 'patternShift',
      'patternFlip', 'customPattern', 'customDurations', 'customDurationChances', 'customDurationRandomWeighting', 'chordSpan', 'noteVariation', 'octaveInterval', 'transpose',
      'velocityMin', 'velocityMax', 'noteLengthMultiplier',
    ],
    chords: [
      'chordSpanFill', 'fillFlip', 'rhythm', 'hitsPerPattern', 'patternShift', 'patternFlip',
      'customPattern', 'chordSpan', 'chordNotesStretch', 'stretchEnabled', 'strum', 'strumType',
      'strumPauseChance', 'voicing', 'noteLengthPercent', 'transpose',
    ],
    arpeggio: [
      'chordSpanFill', 'fillFlip', 'rhythm', 'hitsPerPattern', 'patternShift', 'patternFlip', 'customPattern',
      'customDurations', 'customDurationChances', 'customDurationRandomWeighting', 'chordSpan', 'patternRepeat', 'pauseChance', 'chordNotesStretch', 'stretchEnabled', 'pattern',
      'arpPatternRotate', 'arpPatternCustom', 'transpose',
    ],
    drums: [
      'chordSpanFill', 'fillFlip', 'rhythm', 'hitsPerPattern', 'euclideanPulses', 'patternShift',
      'patternFlip', 'customPattern', 'velocityMin', 'velocityMax', 'chordSpan', 'pauseChance',
      'swingPercent', 'ghosts',
    ],
  };
}

export function defaultUiPreferences(): UiPreferences {
  return { kind: 'vibecomposer-ui-preferences', schemaVersion: 1, composeBpmMin: 60, composeBpmMax: 100, compactHistory: true, syncScoreMix: false, scoreChordGrid: false, scoreGhostNotes: true, scoreOutlineOtherTracks: false, restartPlaybackOnCompose: true, generateArrangementVariations: false, rerollPresenceWithVariations: false, limitInstrumentsToPool: false, drumMapping: 'auto', discardPhraseNotesOnMidiDrop: false, manualPhrasesFollowMode: true, exportNameFields: DEFAULT_EXPORT_NAME_FIELDS, quickSettings: defaultQuickSettings(), appearance: defaultAppearance(), shortcuts: defaultShortcuts() };
}

/** Decode atomically, accepting only UI data and retaining the code's control order. */
export function decodeUiPreferences(value: unknown): UiPreferences | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const data = value as Record<string, unknown>;
  if (data['kind'] !== 'vibecomposer-ui-preferences' || typeof data['compactHistory'] !== 'boolean'
      || !data['quickSettings'] || typeof data['quickSettings'] !== 'object' || Array.isArray(data['quickSettings'])) return null;
  const composeBpmMin = data['composeBpmMin'] ?? 60, composeBpmMax = data['composeBpmMax'] ?? 100;
  if (typeof composeBpmMin !== 'number' || typeof composeBpmMax !== 'number' || !Number.isInteger(composeBpmMin) || !Number.isInteger(composeBpmMax) || composeBpmMin < 30 || composeBpmMax > 240 || composeBpmMin > composeBpmMax) return null;
  const settings = data['quickSettings'] as Record<string, unknown>;
  const fields = data['exportNameFields'];
  if (fields !== undefined && (!Array.isArray(fields) || fields.some(field => !EXPORT_NAME_FIELDS.some(choice => choice.id === field)))) return null;
  const exportNameFields = fields === undefined ? DEFAULT_EXPORT_NAME_FIELDS
    : EXPORT_NAME_FIELDS.filter(field => (fields as unknown[]).includes(field.id)).map(field => field.id);
  if (data['syncScoreMix'] !== undefined && typeof data['syncScoreMix'] !== 'boolean') return null;
  if (data['scoreChordGrid'] !== undefined && typeof data['scoreChordGrid'] !== 'boolean') return null;
  if (data['scoreOutlineOtherTracks'] !== undefined && typeof data['scoreOutlineOtherTracks'] !== 'boolean') return null;
  if (data['scoreGhostNotes'] !== undefined && typeof data['scoreGhostNotes'] !== 'boolean') return null;
  if (data['restartPlaybackOnCompose'] !== undefined && typeof data['restartPlaybackOnCompose'] !== 'boolean') return null;
  if (data['generateArrangementVariations'] !== undefined && typeof data['generateArrangementVariations'] !== 'boolean') return null;
  if (data['rerollPresenceWithVariations'] !== undefined && typeof data['rerollPresenceWithVariations'] !== 'boolean') return null;
  if (data['limitInstrumentsToPool'] !== undefined && typeof data['limitInstrumentsToPool'] !== 'boolean') return null;
  if (data['drumMapping'] !== undefined && !['auto', 'general-midi', 'chromatic'].includes(data['drumMapping'] as string)) return null;
  if (data['discardPhraseNotesOnMidiDrop'] !== undefined && typeof data['discardPhraseNotesOnMidiDrop'] !== 'boolean') return null;
  if (data['manualPhrasesFollowMode'] !== undefined && typeof data['manualPhrasesFollowMode'] !== 'boolean') return null;
  const appearance = data['appearance'] === undefined ? defaultAppearance() : decodeAppearance(data['appearance']);
  if (!appearance) return null;
  const shortcuts = decodeShortcuts(data['shortcuts']);
  if (!shortcuts) return null;
  const quickSettings = { ...defaultQuickSettings() };
  for (const role of ARRANGED_PARTS) {
    const keys = settings[role];
    if (!Array.isArray(keys) || keys.some(key => typeof key !== 'string' || !PART_CONTROLS[role].some(control => control.key === key))) return null;
    quickSettings[role] = PART_CONTROLS[role].filter(control => combinedControlKeys(control.key, role).some(key => keys.includes(key))).map(control => control.key);
  }
  return { kind: 'vibecomposer-ui-preferences', schemaVersion: 1, composeBpmMin, composeBpmMax, compactHistory: data['compactHistory'], syncScoreMix: data['syncScoreMix'] === true, scoreChordGrid: data['scoreChordGrid'] === true, scoreGhostNotes: data['scoreGhostNotes'] !== false, scoreOutlineOtherTracks: data['scoreOutlineOtherTracks'] === true, restartPlaybackOnCompose: data['restartPlaybackOnCompose'] !== false, generateArrangementVariations: data['generateArrangementVariations'] === true, rerollPresenceWithVariations: data['rerollPresenceWithVariations'] === true, limitInstrumentsToPool: data['limitInstrumentsToPool'] === true, drumMapping: data['drumMapping'] as DrumMappingPreference ?? 'auto', discardPhraseNotesOnMidiDrop: data['discardPhraseNotesOnMidiDrop'] === true, manualPhrasesFollowMode: data['manualPhrasesFollowMode'] !== false, exportNameFields, quickSettings, appearance, shortcuts };
}
