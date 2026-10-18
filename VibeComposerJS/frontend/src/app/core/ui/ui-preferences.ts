import { PART_CONTROLS } from '../music/part-settings';
import { decodeAppearance, defaultAppearance, type AppearancePreferences } from './appearance';
import { ARRANGED_PARTS, type ArrangedPart } from '../project/project.model';
import type { DrumMappingPreference } from '../music/drum-instruments';

export type QuickSettings = Readonly<Record<ArrangedPart, readonly string[]>>;

/** Selector and inversion share placement and inheritance in the musical editor. */
export function combinedControlKeys(key: string): readonly string[] {
  if (key === 'rhythm' || key === 'patternFlip') return ['rhythm', 'patternFlip'];
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
  readonly quickSettings: QuickSettings;
  readonly appearance: AppearancePreferences;
}

export function defaultQuickSettings(): QuickSettings {
  return {
    melody: [
      'chordSpanFill', 'fillFlip', 'speed', 'fillPauses', 'chordNoteChoices', 'melodyPatternOffsets',
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
  return { kind: 'vibecomposer-ui-preferences', schemaVersion: 1, compactHistory: true, syncScoreMix: false, scoreChordGrid: false, restartPlaybackOnCompose: true, limitInstrumentsToPool: false, drumMapping: 'auto', quickSettings: defaultQuickSettings(), appearance: defaultAppearance() };
}

/** Decode atomically, accepting only UI data and retaining the code's control order. */
export function decodeUiPreferences(value: unknown): UiPreferences | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const data = value as Record<string, unknown>;
  if (data['kind'] !== 'vibecomposer-ui-preferences' || typeof data['compactHistory'] !== 'boolean'
      || !data['quickSettings'] || typeof data['quickSettings'] !== 'object' || Array.isArray(data['quickSettings'])) return null;
  const settings = data['quickSettings'] as Record<string, unknown>;
  if (data['syncScoreMix'] !== undefined && typeof data['syncScoreMix'] !== 'boolean') return null;
  if (data['scoreChordGrid'] !== undefined && typeof data['scoreChordGrid'] !== 'boolean') return null;
  if (data['restartPlaybackOnCompose'] !== undefined && typeof data['restartPlaybackOnCompose'] !== 'boolean') return null;
  if (data['limitInstrumentsToPool'] !== undefined && typeof data['limitInstrumentsToPool'] !== 'boolean') return null;
  if (data['drumMapping'] !== undefined && !['auto', 'general-midi', 'chromatic'].includes(data['drumMapping'] as string)) return null;
  const appearance = data['appearance'] === undefined ? defaultAppearance() : decodeAppearance(data['appearance']);
  if (!appearance) return null;
  const quickSettings = { ...defaultQuickSettings() };
  for (const role of ARRANGED_PARTS) {
    const keys = settings[role];
    if (!Array.isArray(keys) || keys.some(key => typeof key !== 'string' || !PART_CONTROLS[role].some(control => control.key === key))) return null;
    quickSettings[role] = PART_CONTROLS[role].filter(control => combinedControlKeys(control.key).some(key => keys.includes(key))).map(control => control.key);
  }
  return { kind: 'vibecomposer-ui-preferences', schemaVersion: 1, compactHistory: data['compactHistory'], syncScoreMix: data['syncScoreMix'] === true, scoreChordGrid: data['scoreChordGrid'] === true, restartPlaybackOnCompose: data['restartPlaybackOnCompose'] !== false, limitInstrumentsToPool: data['limitInstrumentsToPool'] === true, drumMapping: data['drumMapping'] as DrumMappingPreference ?? 'auto', quickSettings, appearance };
}
