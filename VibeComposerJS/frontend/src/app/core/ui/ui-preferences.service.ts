import { computed, Injectable, signal } from '@angular/core';
import type { ArrangedPart } from '../project/project.model';
import { ARRANGED_PARTS } from '../project/project.model';
import { defaultAppearance, FONT_CHOICES, isRoleColor, roleColorTokens, type FontArea } from './appearance';
import { PART_CONTROLS } from '../music/part-settings';
import { THEME_COLORS, themeColorTokens, type ThemeColor } from './theme';
import { combinedControlKeys, decodeUiPreferences, defaultQuickSettings, defaultUiPreferences, type QuickSettings, type UiPreferences } from './ui-preferences';

const STORAGE_KEY = 'vibecomposer.ui-preferences.v1';

@Injectable({ providedIn: 'root' })
export class UiPreferencesService {
  private readonly state = signal(this.load());
  private readonly draft = signal<QuickSettings | null>(null);
  readonly preferences = this.state.asReadonly();
  readonly compactHistory = computed(() => this.state().compactHistory);
  readonly appearance = computed(() => this.state().appearance);
  readonly editingQuickSettings = computed(() => this.draft() !== null);
  readonly quickSettings = computed(() => this.draft() ?? this.state().quickSettings);

  constructor() { this.applyAppearance(this.state()); }

  setFont(area: FontArea, font: string): void {
    if (!FONT_CHOICES.some(choice => choice.value === font)) return;
    const appearance = this.appearance();
    this.commit({ ...this.state(), appearance: { ...appearance, fonts: { ...appearance.fonts, [area]: font } } });
  }
  setRoleColor(role: ArrangedPart, color: string): void {
    if (!isRoleColor(color)) return;
    const appearance = this.appearance();
    this.commit({ ...this.state(), appearance: { ...appearance, roleColors: { ...appearance.roleColors, [role]: color.toLowerCase() } } });
  }
  resetAppearance(): void { this.commit({ ...this.state(), appearance: defaultAppearance() }); }

  setThemeColor(key: ThemeColor, color: string): void {
    if (!THEME_COLORS.some(choice => choice.key === key) || !isRoleColor(color)) return;
    const appearance = this.appearance();
    this.commit({ ...this.state(), appearance: { ...appearance, themeColors: { ...appearance.themeColors, [key]: color.toLowerCase() } } });
  }

  private applyAppearance(preferences: UiPreferences): void {
    const style = globalThis.document?.documentElement?.style;
    if (!style) return;
    for (const [name, color] of Object.entries(themeColorTokens(preferences.appearance.themeColors))) style.setProperty(name, color);
    for (const [area, font] of Object.entries(preferences.appearance.fonts)) style.setProperty(`--font-${area}`, font);
    for (const role of ARRANGED_PARTS) {
      for (const [name, color] of Object.entries(roleColorTokens(role, preferences.appearance.roleColors[role]))) style.setProperty(name, color);
    }
  }

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
    this.applyAppearance(preferences);
    this.state.set(preferences);
    try { globalThis.localStorage?.setItem(STORAGE_KEY, JSON.stringify(preferences)); }
    catch { /* Keep preferences usable in memory when storage is unavailable. */ }
  }
}
