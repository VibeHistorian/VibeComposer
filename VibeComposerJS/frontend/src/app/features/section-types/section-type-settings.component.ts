import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { type ArrangedPart, ARRANGED_PARTS } from '../../core/music/part-role';
import { type SectionType, SECTION_TYPES } from '../../core/project/arrangement.model';
import { SectionTypeSettingsService } from '../../core/project/section-type-settings.service';

const PART_NAMES: Readonly<Record<ArrangedPart, string>> = {
  melody: 'Melody', bass: 'Bass', chords: 'Chords', arpeggio: 'Arps', drums: 'Drums',
};

@Component({
  selector: 'vc-section-type-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './section-type-settings.component.html',
  styleUrls: ['./section-type-settings.component.css', '../settings/settings-rows.css'],
})
export class SectionTypeSettingsComponent {
  readonly settings = inject(SectionTypeSettingsService);
  readonly sectionTypes = SECTION_TYPES;
  readonly parts = ARRANGED_PARTS;

  sectionLabel(type: SectionType): string { return type.replaceAll('_', ' '); }
  partName(part: ArrangedPart): string { return PART_NAMES[part]; }
  readonly rows = computed(() => {
    const chances = this.settings.chances();
    return this.sectionTypes.map(type => {
      const total = this.parts.reduce((sum, part) => sum + chances[type][part], 0);
      // Reserve the 10% floor, then share the remaining width proportionally.
      const shares: Partial<Record<ArrangedPart, number>> = {};
      let flexible = [...this.parts], available = 100;
      while (flexible.length) {
        const weight = flexible.reduce((sum, part) => sum + chances[type][part], 0);
        const belowMinimum = weight === 0 ? [] : flexible.filter(part => chances[type][part] / weight * available < 10);
        if (!belowMinimum.length) {
          for (const part of flexible) shares[part] = weight === 0 ? available / flexible.length : chances[type][part] / weight * available;
          break;
        }
        for (const part of belowMinimum) shares[part] = 10;
        available -= belowMinimum.length * 10;
        flexible = flexible.filter(part => !belowMinimum.includes(part));
      }
      const roles = this.parts.map(part => {
        const chance = chances[type][part];
        return { part, chance, share: shares[part]!,
          brightness: chance === 0 ? 0.25 : 0.5 + (chance - 1) / 99 * 0.5 };
      });
      return { type, roles, empty: total === 0,
        description: this.sectionLabel(type) + ': ' + roles.map(role => this.partName(role.part) + ' ' + role.chance + '% chance').join(', ') };
    });
  });

  updateChance(type: SectionType, part: ArrangedPart, event: Event): void {
    this.settings.setChance(type, part, Number((event.target as HTMLInputElement).value));
  }
}
