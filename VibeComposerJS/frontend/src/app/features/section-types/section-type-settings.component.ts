import { CompactKnobComponent } from '../../shared/compact-knob.component';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { type ArrangedPart, ARRANGED_PARTS } from '../../core/music/part-role';
import { type SectionType, SECTION_TYPES } from '../../core/project/arrangement.model';
import { DEFAULT_SECTION_TYPE_CHANCES, SectionTypeSettingsService } from '../../core/project/section-type-settings.service';

const PART_NAMES: Readonly<Record<ArrangedPart, string>> = {
  melody: 'Melody', bass: 'Bass', chords: 'Chords', arpeggio: 'Arps', drums: 'Drums',
};

@Component({
  selector: 'vc-section-type-settings',
  imports: [CompactKnobComponent],
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
  defaultChance(type: SectionType, part: ArrangedPart): number { return DEFAULT_SECTION_TYPE_CHANCES[type][part]; }
  chance(type: SectionType, part: ArrangedPart): number { return this.settings.chances()[type][part]; }

}
