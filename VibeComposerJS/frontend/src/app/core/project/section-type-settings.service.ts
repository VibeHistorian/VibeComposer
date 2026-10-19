import { Injectable, signal } from '@angular/core';
import type { ArrangedPart, SectionType } from './project.model';
import { ARRANGED_PARTS, SECTION_TYPES } from './project.model';
import { DEFAULT_SECTION_TYPE_CHANCES, type SectionTypeChanceSettings } from './section-type-chances';

export { DEFAULT_SECTION_TYPE_CHANCES, type SectionTypeChanceSettings } from './section-type-chances';

const STORAGE_KEY = 'vibecomposer.section-type-settings.v1';

@Injectable({ providedIn: 'root' })
export class SectionTypeSettingsService {
  private readonly state = signal<SectionTypeChanceSettings>(this.load());
  readonly chances = this.state.asReadonly();

  setChance(sectionType: SectionType, part: ArrangedPart, chance: number): void {
    if (!SECTION_TYPES.includes(sectionType) || !ARRANGED_PARTS.includes(part)
        || !Number.isInteger(chance) || chance < 0 || chance > 100) return;
    const next = {
      ...this.state(),
      [sectionType]: { ...this.state()[sectionType], [part]: chance },
    } as SectionTypeChanceSettings;
    this.state.set(next);
    this.persist(next);
  }

  reset(): void {
    const defaults = this.copyDefaults();
    this.state.set(defaults);
    this.persist(defaults);
  }

  private load(): SectionTypeChanceSettings {
    try {
      const raw = globalThis.localStorage?.getItem(STORAGE_KEY);
      if (raw) {
        const value = JSON.parse(raw) as Record<string, Record<string, unknown>>;
        const decoded = this.copyDefaults();
        for (const sectionType of SECTION_TYPES) {
          for (const part of ARRANGED_PARTS) {
            const chance = value?.[sectionType]?.[part];
            if (Number.isInteger(chance) && (chance as number) >= 0 && (chance as number) <= 100) {
              (decoded[sectionType] as Record<ArrangedPart, number>)[part] = chance as number;
            }
          }
        }
        return decoded;
      }
    } catch {
      // Use defaults when settings storage is unavailable or malformed.
    }
    return this.copyDefaults();
  }

  private copyDefaults(): SectionTypeChanceSettings {
    const defaults = Object.fromEntries(SECTION_TYPES.map((type) => [type, { ...DEFAULT_SECTION_TYPE_CHANCES[type] }])) as SectionTypeChanceSettings;
    return defaults;
  }

  private persist(settings: SectionTypeChanceSettings): void {
    try {
      globalThis.localStorage?.setItem(STORAGE_KEY, JSON.stringify(settings));
    } catch {
      // Keep settings usable for the current session if storage is unavailable.
    }
  }
}
