import { computed, Injectable, signal } from '@angular/core';
import type { ArrangedPart } from '../project/project.model';
import { PART_CONTROLS } from '../music/part-settings';
import { combinedControlKeys, decodeUiPreferences, defaultQuickSettings, defaultUiPreferences, type QuickSettings, type UiPreferences } from './ui-preferences';

const STORAGE_KEY = 'vibecomposer.ui-preferences.v1';

@Injectable({ providedIn: 'root' })
export class UiPreferencesService {
  private readonly state = signal(this.load());
  private readonly draft = signal<QuickSettings | null>(null);
  readonly preferences = this.state.asReadonly();
  readonly compactHistory = computed(() => this.state().compactHistory);
  readonly editingQuickSettings = computed(() => this.draft() !== null);
  readonly quickSettings = computed(() => this.draft() ?? this.state().quickSettings);

  beginQuickSettings(): void { this.draft.set({ ...this.state().quickSettings }); }
  saveQuickSettings(): void {
    const quickSettings = this.draft();
    if (quickSettings) this.commit({ ...this.state(), quickSettings });
    this.draft.set(null);
  }
  resetQuickSettings(role: ArrangedPart): void {
    if (this.draft()) this.draft.update(draft => ({ ...draft!, [role]: defaultQuickSettings()[role] }));
  }
  moveControl(role: ArrangedPart, key: string): void {
    const draft = this.draft();
    if (!draft || !PART_CONTROLS[role].some(control => control.key === key)) return;
    const keys = new Set(draft[role]);
    const pairedKeys = combinedControlKeys(key);
    const remove = pairedKeys.some(key => keys.has(key));
    for (const pairedKey of pairedKeys) {
      if (remove) keys.delete(pairedKey); else keys.add(pairedKey);
    }
    this.draft.set({ ...draft, [role]: PART_CONTROLS[role].filter(control => keys.has(control.key)).map(control => control.key) });
  }
  toggleCompactHistory(): void { this.commit({ ...this.state(), compactHistory: !this.compactHistory() }); }
  exportJson(): string { return JSON.stringify(this.state(), null, 2); }
  importJson(json: string): boolean {
    try {
      const preferences = decodeUiPreferences(JSON.parse(json));
      if (!preferences) return false;
      this.commit(preferences);
      this.draft.set(null);
      return true;
    } catch { return false; }
  }
  private load(): UiPreferences {
    try {
      const raw = globalThis.localStorage?.getItem(STORAGE_KEY);
      if (raw) return decodeUiPreferences(JSON.parse(raw)) ?? defaultUiPreferences();
    } catch { /* Storage may be unavailable. */ }
    return defaultUiPreferences();
  }
  private commit(preferences: UiPreferences): void {
    this.state.set(preferences);
    try { globalThis.localStorage?.setItem(STORAGE_KEY, JSON.stringify(preferences)); }
    catch { /* Keep preferences usable in memory when storage is unavailable. */ }
  }
}
