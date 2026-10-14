import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
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
  readonly preferences = inject(UiPreferencesService);
  readonly transferMessage = signal('');
  readonly dialog = inject(DialogRef);
  readonly projects = inject(ProjectService);
  exportPreferences(): void {
    const url = URL.createObjectURL(new Blob([this.preferences.exportJson()], { type: 'application/json' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = 'vibecomposer-ui-preferences.json';
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 0);
    this.transferMessage.set('UI preferences exported.');
  }
  async importPreferences(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    try {
      this.transferMessage.set(this.preferences.importJson(await file.text())
        ? 'UI preferences imported.' : 'Invalid UI preferences file. Choose an exported UI preferences JSON.');
    } catch { this.transferMessage.set('Could not read the UI preferences file.'); }
    input.value = '';
  }
  readonly categories = [
    { id: 'section-types', label: 'Section Types' },
    { id: 'project-history', label: 'Project History' },
  ] as const;
  readonly selectedCategory = signal<typeof this.categories[number]['id']>('section-types');
}
