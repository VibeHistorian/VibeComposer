import { SelectComponent } from '../../shared/select.component';
import { MelodyGlobalSettingsComponent } from './melody-global-settings.component';
import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, signal } from '@angular/core';
import { EXPORT_NAME_FIELDS, exportFilename } from '../../core/project/export-filename';
import { DialogRef } from '@angular/cdk/dialog';
import { ProjectService } from '../../core/project/project.service';
import { SectionTypeSettingsComponent } from '../section-types/section-type-settings.component';
import { COLOR_ADJUSTMENTS, FONT_AREAS, FONT_CHOICES, type FontArea } from '../../core/ui/appearance';
import { CompactKnobComponent } from '../../shared/compact-knob.component';
import { ARRANGED_PARTS, type ArrangedPart } from '../../core/music/part-role';
import { THEME_COLORS, type ThemeColor } from '../../core/ui/theme';
import { HARMONY_GENERATIONS } from '../../core/music/contextual-harmony';
import { DEFAULT_HARMONY_SETTINGS, type HarmonySettings } from '../../core/music/harmony-voicing';
import { MidiOutputService } from '../../core/audio/midi-output.service';
import type { DrumMappingPreference } from '../../core/music/drum-instruments';

@Component({
  selector: 'vc-settings-dialog',
  imports: [SelectComponent, SectionTypeSettingsComponent, CompactKnobComponent, MelodyGlobalSettingsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './settings-dialog.component.html',
  styleUrl: './settings-dialog.component.css',
})
export class SettingsDialogComponent {
  readonly preferences = inject(UiPreferencesService);
  readonly midi = inject(MidiOutputService);
  changeDrumMapping(selection: string): void {
    this.preferences.setDrumMapping(selection as DrumMappingPreference);
  }
  readonly transferMessage = signal('');
  readonly dialog = inject(DialogRef);
  readonly projects = inject(ProjectService);
  readonly exportNameFields = EXPORT_NAME_FIELDS;
  private readonly previewTime = signal(new Date());
  readonly exportNamePreview = computed(() => exportFilename(this.projects.project(), this.preferences.exportNameFields(), this.previewTime()));
  readonly fontAreas = FONT_AREAS;
  readonly fontChoices = FONT_CHOICES;
  readonly roles = ARRANGED_PARTS;
  readonly themeColors = THEME_COLORS;
  readonly colorAdjustments = COLOR_ADJUSTMENTS;
  readonly harmonyGenerations = HARMONY_GENERATIONS;
  readonly harmonyValues = computed(() => ({ ...DEFAULT_HARMONY_SETTINGS, ...this.projects.project().harmonySettings }));
  changeHarmony(selection: string): void {
    this.projects.updateHarmonySettings({ generation: selection as HarmonySettings['generation'] });
  }
  changeThemeColor(key: ThemeColor, event: Event): void { this.preferences.setThemeColor(key, (event.target as HTMLInputElement).value); }
  readonly roleNames: Record<ArrangedPart, string> = { melody: 'Melody', bass: 'Bass', chords: 'Chords', arpeggio: 'Arps', drums: 'Drums' };
  changeFont(area: FontArea, selection: string): void { this.preferences.setFont(area, selection); }
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
    { id: 'appearance', label: 'Appearance', children: [
      { id: 'appearance', label: 'General' },
      { id: 'score', label: 'Score' },
    ] },
    { id: 'behavior', label: 'Behavior', children: [
      { id: 'behavior', label: 'General' },
      { id: 'harmony', label: 'Harmony' },
      { id: 'melody', label: 'Melody' },
      { id: 'section-types', label: 'Section Types' },
    ] },
    { id: 'midi', label: 'MIDI', children: [] },
    { id: 'project', label: 'Project', children: [] },
  ] as const;
  readonly selectedCategory = signal<typeof this.categories[number]['id'] | typeof this.categories[number]['children'][number]['id']>('appearance');
  readonly preview = signal(false);

  constructor() {
    const timer = setInterval(() => this.previewTime.set(new Date()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
    effect(onCleanup => {
      const backdrop = this.dialog.overlayRef.backdropElement;
      backdrop?.classList.toggle('appearance-preview', this.preview() && this.selectedCategory() === 'appearance');
      onCleanup(() => backdrop?.classList.remove('appearance-preview'));
    });
  }
}
