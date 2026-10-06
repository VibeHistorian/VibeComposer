import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import type { ArrangedPart, SectionType } from '../../core/project/project.model';
import { SECTION_TYPES } from '../../core/project/project.model';
import { ProjectService } from '../../core/project/project.service';

@Component({
  selector: 'vc-arrange-workspace',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './arrange-workspace.component.html',
  styleUrl: './arrange-workspace.component.css',
})
export class ArrangeWorkspaceComponent {
  readonly projects = inject(ProjectService);
  readonly project = this.projects.project;
  readonly sectionTypes = SECTION_TYPES;
  readonly newSectionType = signal<SectionType>('VERSE1');
  readonly selectedId = signal(this.project().arrangement[0].id);
  readonly selectedSection = computed(() => this.project().arrangement.find((section) => section.id === this.selectedId())
    ?? this.project().arrangement[0]);
  readonly selectedIndex = computed(() => this.project().arrangement.findIndex((section) => section.id === this.selectedSection().id));
  readonly totalMeasures = computed(() => this.project().arrangement.reduce((sum, section) => sum + section.measures, 0));
  readonly partOptions: ReadonlyArray<{ key: ArrangedPart; label: string; color: string }> = [
    { key: 'bass', label: 'Bass', color: 'bass' },
    { key: 'chords', label: 'Chords', color: 'chords' },
    { key: 'arpeggio', label: 'Arpeggio', color: 'arpeggio' },
    { key: 'drums', label: 'Drums', color: 'drums' },
  ];

  selectSection(id: string): void {
    this.selectedId.set(id);
  }

  updateNewSectionType(event: Event): void {
    const type = (event.target as HTMLSelectElement).value as SectionType;
    if (this.sectionTypes.includes(type)) {
      this.newSectionType.set(type);
    }
  }

  addSection(): void {
    this.projects.addSection(this.newSectionType(), this.selectedIndex());
    const added = this.project().arrangement[this.selectedIndex() + 1];
    if (added) this.selectedId.set(added.id);
  }

  duplicateSelected(): void {
    this.projects.duplicateSection(this.selectedIndex());
    const duplicate = this.project().arrangement[this.selectedIndex() + 1];
    if (duplicate) this.selectedId.set(duplicate.id);
  }

  removeSelected(): void {
    const index = this.selectedIndex();
    const section = this.selectedSection();
    this.projects.removeSection(index);
    const next = this.project().arrangement[Math.min(index, this.project().arrangement.length - 1)];
    if (next?.id !== section.id) this.selectedId.set(next.id);
  }

  moveSelected(offset: -1 | 1): void {
    const index = this.selectedIndex();
    this.projects.moveSection(index, offset);
  }

  updateMeasures(event: Event): void {
    const measures = Number((event.target as HTMLInputElement).value);
    if (Number.isInteger(measures) && measures >= 1 && measures <= 32) {
      this.projects.updateSection(this.selectedIndex(), { measures });
    }
  }

  togglePart(part: ArrangedPart, event: Event): void {
    this.projects.setSectionPart(this.selectedIndex(), part, (event.target as HTMLInputElement).checked);
  }

  sectionLabel(type: SectionType): string {
    return type.replaceAll('_', ' ');
  }

  sectionWidth(measures: number): number {
    return measures / this.totalMeasures() * 100;
  }

  sectionStart(index: number): number {
    return this.project().arrangement.slice(0, index).reduce((sum, section) => sum + section.measures, 0) + 1;
  }

  endMeasure(index: number, measures: number): number {
    return this.sectionStart(index) + measures - 1;
  }
}
