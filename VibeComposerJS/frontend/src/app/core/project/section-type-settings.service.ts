import { inject, Injectable } from '@angular/core';
import type { ArrangedPart } from '../music/part-role';
import { UiPreferencesService } from '../ui/ui-preferences.service';
import type { SectionType } from './arrangement.model';

@Injectable({ providedIn: 'root' })
export class SectionTypeSettingsService {
  private readonly preferences = inject(UiPreferencesService);
  readonly chances = this.preferences.sectionTypeChances;

  setChance(sectionType: SectionType, part: ArrangedPart, chance: number): void {
    this.preferences.setSectionTypeChance(sectionType, part, chance);
  }

  reset(): void { this.preferences.resetSectionTypeChances(); }
}
