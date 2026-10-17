import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { DialogRef } from '@angular/cdk/dialog';
import { ProjectService } from '../../core/project/project.service';
import { SectionTypeSettingsComponent } from '../section-types/section-type-settings.component';
import { COLOR_ADJUSTMENTS, FONT_AREAS, FONT_CHOICES, type FontArea } from '../../core/ui/appearance';
import { CompactKnobComponent } from '../../shared/compact-knob.component';
import { ARRANGED_PARTS, type ArrangedPart } from '../../core/project/project.model';
import { THEME_COLORS, type ThemeColor } from '../../core/ui/theme';

@Component({
  selector: 'vc-settings-dialog',
  imports: [SectionTypeSettingsComponent, CompactKnobComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './settings-dialog.component.html',
  styleUrl: './settings-dialog.component.css',
})
export class SettingsDialogComponent {
  readonly preferences = inject(UiPreferencesService);
  readonly transferMessage = signal('');
  readonly dialog = inject(DialogRef);
  readonly projects = inject(ProjectService);
  readonly fontAreas = FONT_AREAS;
  readonly fontChoices = FONT_CHOICES;
  readonly roles = ARRANGED_PARTS;
  readonly themeColors = THEME_COLORS;
  readonly colorAdjustments = COLOR_ADJUSTMENTS;
  changeThemeColor(key: ThemeColor, event: Event): void { this.preferences.setThemeColor(key, (event.target as HTMLInputElement).value); }
  readonly roleNames: Record<ArrangedPart, string> = { melody: 'Melody', bass: 'Bass', chords: 'Chords', arpeggio: 'Arps', drums: 'Drums' };
  changeFont(area: FontArea, event: Event): void { this.preferences.setFont(area, (event.target as HTMLSelectElement).value); }
  changeRoleColor(role: ArrangedPart, event: Event): void { this.preferences.setRoleColor(role, (event.target as HTMLInputElement).value); }
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
    { id: 'appearance', label: 'Appearance' },
    { id: 'behavior', label: 'Behavior' },
    { id: 'section-types', label: 'Section Types' },
    { id: 'project-history', label: 'Project History' },
  ] as const;
  readonly selectedCategory = signal<typeof this.categories[number]['id']>('appearance');
  readonly preview = signal(false);

  constructor() {
    effect(onCleanup => {
      const backdrop = this.dialog.overlayRef.backdropElement;
      backdrop?.classList.toggle('appearance-preview', this.preview() && this.selectedCategory() === 'appearance');
      onCleanup(() => backdrop?.classList.remove('appearance-preview'));
    });
  }
}
