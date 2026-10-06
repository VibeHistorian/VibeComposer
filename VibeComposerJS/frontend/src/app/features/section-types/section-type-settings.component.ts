import { ChangeDetectionStrategy, Component, inject, output } from '@angular/core';
import type { ArrangedPart, SectionType } from '../../core/project/project.model';
import { ARRANGED_PARTS, SECTION_TYPES } from '../../core/project/project.model';
import { SectionTypeSettingsService } from '../../core/project/section-type-settings.service';

const PART_NAMES: Readonly<Record<ArrangedPart, string>> = {
  bass: 'Bass', chords: 'Chords', arpeggio: 'Arpeggio', drums: 'Drums',
};

@Component({
  selector: 'vc-section-type-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './section-type-settings.component.html',
  styleUrl: './section-type-settings.component.css',
})
export class SectionTypeSettingsComponent {
  readonly closed = output<void>();
  readonly settings = inject(SectionTypeSettingsService);
  readonly sectionTypes = SECTION_TYPES;
  readonly parts = ARRANGED_PARTS;

  sectionLabel(type: SectionType): string { return type.replaceAll('_', ' '); }
  partName(part: ArrangedPart): string { return PART_NAMES[part]; }
  chance(type: SectionType, part: ArrangedPart): number { return this.settings.chances()[type][part]; }

  updateChance(type: SectionType, part: ArrangedPart, event: Event): void {
    this.settings.setChance(type, part, Number((event.target as HTMLInputElement).value));
  }
}
