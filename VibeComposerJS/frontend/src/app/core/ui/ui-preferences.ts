import { PART_CONTROLS } from '../music/part-settings';
import { decodeAppearance, defaultAppearance, type AppearancePreferences } from './appearance';
import { ARRANGED_PARTS, type ArrangedPart } from '../project/project.model';

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
  readonly quickSettings: QuickSettings;
  readonly appearance: AppearancePreferences;
}

export function defaultQuickSettings(): QuickSettings {
  return Object.fromEntries(ARRANGED_PARTS.map(role => [role,
    PART_CONTROLS[role].filter(control => !control.advanced).map(control => control.key),
  ])) as unknown as QuickSettings;
}

export function defaultUiPreferences(): UiPreferences {
  return { kind: 'vibecomposer-ui-preferences', schemaVersion: 1, compactHistory: true, quickSettings: defaultQuickSettings(), appearance: defaultAppearance() };
}

/** Decode atomically, accepting only UI data and retaining the code's control order. */
export function decodeUiPreferences(value: unknown): UiPreferences | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const data = value as Record<string, unknown>;
  if (data['kind'] !== 'vibecomposer-ui-preferences' || typeof data['compactHistory'] !== 'boolean'
      || !data['quickSettings'] || typeof data['quickSettings'] !== 'object' || Array.isArray(data['quickSettings'])) return null;
  const settings = data['quickSettings'] as Record<string, unknown>;
  const appearance = data['appearance'] === undefined ? defaultAppearance() : decodeAppearance(data['appearance']);
  if (!appearance) return null;
  const quickSettings = { ...defaultQuickSettings() };
  for (const role of ARRANGED_PARTS) {
    const keys = settings[role];
    if (!Array.isArray(keys) || keys.some(key => typeof key !== 'string' || !PART_CONTROLS[role].some(control => control.key === key))) return null;
    quickSettings[role] = PART_CONTROLS[role].filter(control => combinedControlKeys(control.key).some(key => keys.includes(key))).map(control => control.key);
  }
  return { kind: 'vibecomposer-ui-preferences', schemaVersion: 1, compactHistory: data['compactHistory'], quickSettings, appearance };
}
