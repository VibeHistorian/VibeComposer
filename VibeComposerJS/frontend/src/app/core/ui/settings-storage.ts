import { inject, Injectable, signal } from '@angular/core';
import { decodeSectionTypeChances } from '../project/section-type-chances';
import { decodeTrackPresets } from '../project/track-presets';
import { BrowserDatabase } from '../storage/browser-database';
import { decodeUiPreferences, defaultUiPreferences } from './ui-preferences';

export const UI_PREFERENCES_KEY = 'vibecomposer.ui-preferences.v1';
export const TRACK_PRESETS_KEY = 'vibecomposer.track-presets';
const SECTION_CHANCES_KEY = 'vibecomposer.section-type-settings.v1';
type SettingsKey = typeof UI_PREFERENCES_KEY | typeof TRACK_PRESETS_KEY;

function readLegacy(key: string): { raw: string; value: unknown } | undefined {
  try {
    const raw = globalThis.localStorage?.getItem(key);
    if (raw != null) {
      try { return { raw, value: JSON.parse(raw) }; }
      catch { return { raw, value: null }; }
    }
  } catch { /* Leave unreadable legacy data in place. */ }
  return undefined;
}

@Injectable({ providedIn: 'root' })
export class SettingsStorage {
  private readonly database = inject(BrowserDatabase);
  private readonly records = {
    [UI_PREFERENCES_KEY]: signal<unknown>(undefined),
    [TRACK_PRESETS_KEY]: signal<unknown>(undefined),
  };
  private presetChanges?: BroadcastChannel;

  async initialize(): Promise<void> {
    await Promise.all([
      this.restore(UI_PREFERENCES_KEY, () => {
        const preferences = readLegacy(UI_PREFERENCES_KEY);
        const chances = readLegacy(SECTION_CHANCES_KEY);
        const ui = decodeUiPreferences(preferences?.value);
        const sectionTypeChances = decodeSectionTypeChances(chances?.value, true);
        return {
          value: sectionTypeChances ? { ...(ui ?? defaultUiPreferences()), sectionTypeChances } : ui,
          sources: [[UI_PREFERENCES_KEY, ui ? preferences?.raw : undefined],
            [SECTION_CHANCES_KEY, sectionTypeChances ? chances?.raw : undefined]],
        };
      }),
      this.restore(TRACK_PRESETS_KEY, () => {
        const legacy = readLegacy(TRACK_PRESETS_KEY);
        return { value: legacy?.value, sources: [[TRACK_PRESETS_KEY, legacy?.raw]] };
      }),
    ]);
    try {
      this.presetChanges = new BroadcastChannel('vibecomposer-track-presets');
      this.presetChanges.onmessage = () => {
        void this.database.read('settings', TRACK_PRESETS_KEY)
          .then(value => this.cache(TRACK_PRESETS_KEY, value)).catch(() => {});
      };
    } catch { /* Preset saves still work when cross-window updates are unavailable. */ }
  }

  ngOnDestroy(): void {
    this.presetChanges?.close();
    this.presetChanges = undefined;
  }

  read(key: SettingsKey): unknown { return this.records[key](); }

  async write(key: SettingsKey, value: object): Promise<void> {
    await this.database.write('settings', key, value);
    this.cache(key, value);
  }

  async updatePresets(change: (value: unknown) => object): Promise<void> {
    const value = await this.database.update('settings', TRACK_PRESETS_KEY,
      current => change(current === undefined ? this.read(TRACK_PRESETS_KEY) : current));
    this.cache(TRACK_PRESETS_KEY, value);
    this.presetChanges?.postMessage(null);
  }

  private cache(key: SettingsKey, value: unknown): void {
    this.records[key].set(value);
  }

  private async restore(key: SettingsKey, legacy: () => {
    value: unknown; sources: [string, string | undefined][];
  }): Promise<void> {
    let value: unknown;
    try { value = await this.database.read('settings', key); }
    catch { /* Try the legacy copy if IndexedDB is unavailable. */ }
    if (value === undefined) {
      const { value: oldValue, sources } = legacy();
      value = oldValue;
      const valid = key === TRACK_PRESETS_KEY ? decodeTrackPresets(value) : decodeUiPreferences(value);
      if (valid) {
        try {
          value = await this.database.update('settings', key, current =>
            current && typeof current === 'object' ? current : valid);
          for (const [oldKey, raw] of sources) {
            if (raw !== undefined && localStorage.getItem(oldKey) === raw) localStorage.removeItem(oldKey);
          }
        } catch { /* Keep the legacy data available until migration commits. */ }
      }
    }
    this.cache(key, value);
  }
}
