import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import type { ArrangedPart } from '../../core/music/part-role';
import { ProjectService } from '../../core/project/project.service';
import { decodeTrackPresets, type TrackPreset } from '../../core/project/track-presets';
import { formatCompactJson } from '../../core/project/project-json';
import { SelectComponent } from '../../shared/select.component';
import { BUILTIN_TRACK_PRESETS } from '../../core/project/builtin-track-presets';

import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
import { readTrackPresetLibrary, TRACK_PRESET_STORAGE_KEY } from '../../core/ui/track-preset-storage';

@Component({
  selector: 'vc-track-preset-dialog',
  imports: [SelectComponent],
  templateUrl: './track-preset-dialog.component.html',
  styleUrl: './track-preset-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TrackPresetDialogComponent {
  readonly data = inject<{ role: ArrangedPart; name: string }>(DIALOG_DATA);
  readonly dialog = inject(DialogRef);
  readonly projects = inject(ProjectService);
  readonly preferences = inject(UiPreferencesService);
  readonly tracks = computed(() => this.projects.project().tracks.filter(track => track.role === this.data.role));
  readonly selected = signal(new Set(this.tracks().map(track => track.id)));
  readonly name = signal('');
  readonly presetName = signal('');
  readonly replace = this.preferences.replacePresetTracks;
  readonly message = signal('');
  readonly library = signal<TrackPreset[]>(this.readLibrary());
  readonly presets = computed(() => this.library().filter(preset => preset.role === this.data.role));
  readonly builtins = BUILTIN_TRACK_PRESETS.filter(preset => preset.role === this.data.role);
  readonly isBuiltin = computed(() => this.presetName().startsWith('builtin:'));
  readonly options = computed(() => [{ value: '', label: 'Choose a preset' },
    ...this.presets().map(preset => ({ value: `user:${preset.name}`, label: `${preset.name} (${preset.tracks.length})` })),
    ...this.builtins.map(preset => ({ value: `builtin:${preset.name}`, label: `Built-in · ${preset.name} (${preset.tracks.length})` }))]);
  readonly chosen = computed(() => this.isBuiltin()
    ? this.builtins.find(preset => `builtin:${preset.name}` === this.presetName())
    : this.presets().find(preset => `user:${preset.name}` === this.presetName()));
  readonly canSave = computed(() => !!this.name().trim() && this.name().trim().length <= 80 && this.tracks().some(track => this.selected().has(track.id)));

  toggle(id: string, checked: boolean): void {
    this.selected.update(current => { const next = new Set(current); checked ? next.add(id) : next.delete(id); return next; });
  }
  private readLibrary(): TrackPreset[] {
    const presets = readTrackPresetLibrary();
    if (presets) return presets;
    this.message.set('Could not read saved presets. Import a valid library to recover it.');
    return [];
  }
  private store(presets: TrackPreset[]): boolean {
    if (!decodeTrackPresets(presets)) { this.message.set('Preset library is invalid or exceeds 256 presets.'); return false; }
    try { localStorage.setItem(TRACK_PRESET_STORAGE_KEY, JSON.stringify(presets)); }
    catch { this.message.set('Could not save presets in browser storage.'); return false; }
    this.library.set(presets);
    return true;
  }
  save(): void {
    if (!this.canSave()) return;
    const name = this.name().trim();
    if (this.presets().some(preset => preset.name === name)) { this.message.set('This name already exists. Choose a different name.'); return; }
    const preset: TrackPreset = { name, role: this.data.role, tracks: structuredClone(this.tracks().filter(track => this.selected().has(track.id))) };
    if (this.store([...this.library(), preset])) { this.presetName.set(`user:${name}`); this.name.set(''); this.message.set('Preset saved.'); }
  }
  load(): void {
    const preset = this.chosen();
    if (!preset) return;
    const result = this.projects.loadTrackPreset(this.data.role, preset.tracks, this.replace());
    this.message.set(result === 'invalid' ? 'Cannot load preset. The project supports up to 64 tracks.' : 'Preset loaded. Undo restores the previous tracks.');
  }
  remove(): void {
    const preset = this.chosen();
    if (preset && !this.isBuiltin() && this.store(this.library().filter(item => item !== preset))) { this.presetName.set(''); this.message.set('Preset deleted.'); }
  }
  async importFile(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    try {
      if (file.size > 10 * 1024 * 1024) throw new Error('Preset file exceeds 10 MB.');
      const imported = decodeTrackPresets(JSON.parse(await file.text()));
      if (!imported) throw new Error('Invalid preset JSON. Import an exported preset library.');
      // Keep existing presets on name conflicts, so imports cannot silently overwrite them.
      const additions = imported.filter(preset => !this.library().some(existing => existing.role === preset.role && existing.name === preset.name));
      if (this.store([...this.library(), ...additions])) this.message.set(`Imported ${additions.length} presets. Existing names were kept.`);
    } catch (error) { this.message.set(error instanceof Error ? error.message : 'Could not import presets.'); }
    finally { input.value = ''; }
  }
  export(): void {
    const url = URL.createObjectURL(new Blob([formatCompactJson(this.library())], { type: 'application/json' }));
    const link = document.createElement('a');
    link.href = url; link.download = 'vibecomposer-track-presets.json'; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
