import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DialogRef } from '@angular/cdk/dialog';
import { ProjectService } from '../../core/project/project.service';
import { SectionTypeSettingsComponent } from '../section-types/section-type-settings.component';

@Component({
  selector: 'vc-settings-dialog',
  imports: [SectionTypeSettingsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './settings-dialog.component.html',
  styleUrl: './settings-dialog.component.css',
})
export class SettingsDialogComponent {
  readonly dialog = inject(DialogRef);
  readonly projects = inject(ProjectService);
  readonly categories = [
    { id: 'section-types', label: 'Section Types' },
    { id: 'project-history', label: 'Project History' },
  ] as const;
  readonly selectedCategory = signal<typeof this.categories[number]['id']>('section-types');
}
