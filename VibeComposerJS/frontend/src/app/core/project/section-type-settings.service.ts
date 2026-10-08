import { Injectable, signal } from '@angular/core';
import type { ArrangedPart, SectionType } from './project.model';
import { ARRANGED_PARTS, SECTION_TYPES } from './project.model';

export type SectionTypeChanceSettings = Readonly<Record<SectionType, Readonly<Record<ArrangedPart, number>>>>;

export const DEFAULT_SECTION_TYPE_CHANCES: SectionTypeChanceSettings = {
  // Melody probabilities follow Arrangement.defaultSections in the Java app.
  INTRO: { melody: 20, bass: 10, chords: 40, arpeggio: 25, drums: 20 },
  VERSE1: { melody: 40, bass: 60, chords: 30, arpeggio: 25, drums: 40 },
  VERSE2: { melody: 40, bass: 60, chords: 40, arpeggio: 50, drums: 50 },
  VERSE3: { melody: 50, bass: 80, chords: 40, arpeggio: 70, drums: 60 },
  CHORUS1: { melody: 50, bass: 90, chords: 50, arpeggio: 35, drums: 60 },
  CHORUS2: { melody: 65, bass: 100, chords: 60, arpeggio: 50, drums: 70 },
  HALF_CHORUS: { melody: 0, bass: 100, chords: 60, arpeggio: 50, drums: 80 },
  BREAKDOWN: { melody: 40, bass: 60, chords: 60, arpeggio: 25, drums: 40 },
  CHILL: { melody: 10, bass: 30, chords: 70, arpeggio: 70, drums: 10 },
  BUILDUP1: { melody: 40, bass: 40, chords: 10, arpeggio: 20, drums: 70 },
  BUILDUP2: { melody: 65, bass: 60, chords: 20, arpeggio: 40, drums: 90 },
  CHORUS3: { melody: 80, bass: 100, chords: 80, arpeggio: 80, drums: 85 },
  CLIMAX: { melody: 100, bass: 100, chords: 100, arpeggio: 100, drums: 100 },
  OUTRO: { melody: 50, bass: 70, chords: 60, arpeggio: 40, drums: 10 },
};

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
