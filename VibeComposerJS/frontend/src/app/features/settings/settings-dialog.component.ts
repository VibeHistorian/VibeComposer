import { InstrumentSettingsService } from '../../core/audio/instrument-settings.service';
import { acceptsShortcut, SHORTCUTS, SHORTCUT_PAGES, shortcutFromEvent, shortcutLabel, sameShortcut, type ShortcutId } from '../../core/ui/shortcuts';
import { SettingsScopeIndicatorComponent, type SettingScope } from './settings-scope-indicator.component';
import { SelectComponent } from '../../shared/select.component';
import { MelodyGlobalSettingsComponent } from './melody-global-settings.component';
import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, signal } from '@angular/core';
import { EXPORT_NAME_FIELDS, exportFilename } from '../../core/project/export-filename';
import { downloadProjectFile } from '../../core/project/download-file';
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
  imports: [SettingsScopeIndicatorComponent, SelectComponent, SectionTypeSettingsComponent, CompactKnobComponent, MelodyGlobalSettingsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './settings-dialog.component.html',
  styleUrls: ['./settings-dialog.component.css', './settings-rows.css'],
})
export class SettingsDialogComponent {
  setHumanization(key: 'timingBeats' | 'durationBeats', value: number): void {
    this.projects.updateSettings({ humanization: { timingBeats: 0, durationBeats: 0, ...this.projects.project().humanization, [key]: value } });
  }
  setPadding(role: ArrangedPart, value: number): void {
    this.projects.updateSettings({ midiPadding: { ...this.projects.project().midiPadding, [role]: value } });
  }
  readonly preferences = inject(UiPreferencesService);
  readonly instruments = inject(InstrumentSettingsService);
  async loadSoundfont(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) await this.instruments.load(file);
  }
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
    downloadProjectFile(new Blob([this.preferences.exportJson()], { type: 'application/json' }),
      'vibecomposer-ui-preferences', 'json');
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
    { id: 'shortcuts', label: 'Shortcuts', children: SHORTCUT_PAGES.map(({ id, label }) => ({ id, label })) },
    { id: 'midi', label: 'MIDI', children: [] },
    { id: 'project', label: 'Project', children: [] },
  ] as const;
  readonly categoryScopes: Readonly<Record<string, readonly SettingScope[]>> = {
    appearance: ['ui'], score: ['ui'], behavior: ['ui', 'compose', 'sound'],
    harmony: ['compose', 'sound'], melody: ['compose', 'sound'], 'section-types': ['compose'],
    ...Object.fromEntries(SHORTCUT_PAGES.map(page => [page.id, ['ui'] as const])), midi: ['ui', 'sound'], project: ['ui'],
  };
  readonly selectedCategory = signal<typeof this.categories[number]['id'] | typeof this.categories[number]['children'][number]['id']>('appearance');
  readonly preview = signal(false);

  private captureUntil = 0;
  readonly shortcutPage = computed(() => {
    const page = SHORTCUT_PAGES.find(page => page.id === this.selectedCategory());
    return page ? { ...page, actions: page.groups.flatMap(group => SHORTCUTS.filter(action => action.group === group)) } : null;
  });
  readonly recording = signal<ShortcutId | null>(null);
  readonly shortcutMessage = signal('');
  shortcutText(id: ShortcutId): string { return this.preferences.shortcuts()[id].map(shortcutLabel).join(' or ') || 'Unassigned'; }
  recordShortcut(id: ShortcutId): void {
    this.recording.set(id);
    this.preferences.capturingShortcut.set(true);
    this.shortcutMessage.set(SHORTCUTS.find(action => action.id === id)!.defaults[0].kind === 'modifier'
      ? 'Hold your desired modifiers, then release one to record. Escape cancels.' : 'Press a key, click a mouse button or scroll, with optional Ctrl, Shift and Alt. Escape cancels.');
  }
  cancelRecording(): void {
    this.recording.set(null);
    this.preferences.capturingShortcut.set(false);
  }
  resetShortcuts(): void { this.cancelRecording(); this.preferences.resetShortcuts(); this.shortcutMessage.set('App defaults restored.'); }
  private captureShortcut = (event: KeyboardEvent | MouseEvent | WheelEvent): void => {
    const id = this.recording();
    if (!id) return;
    if ((event.target as HTMLElement)?.closest?.('[data-cancel-recording]')) return;
    event.preventDefault(); event.stopImmediatePropagation();
    if ('key' in event && event.key === 'Escape') { this.cancelRecording(); return; }
    const action = SHORTCUTS.find(action => action.id === id)!;
    const modifierOnly = action.defaults[0].kind === 'modifier';
    if (modifierOnly && event.type !== 'keyup') return;
    if (!modifierOnly && event.type === 'keyup') return;
    const binding = modifierOnly && 'key' in event && ['Control', 'Meta', 'Shift', 'Alt'].includes(event.key)
      ? { kind: 'modifier' as const, input: 'held', ctrl: event.ctrlKey || event.metaKey || event.key === 'Control' || event.key === 'Meta',
        shift: event.shiftKey || event.key === 'Shift', alt: event.altKey || event.key === 'Alt' } : shortcutFromEvent(event);
    if (!binding) return;
    if (!acceptsShortcut(action, binding)) {
      this.shortcutMessage.set(action.defaults[0].kind === 'wheel' ? 'Scroll the wheel with your desired modifiers.'
        : action.group === 'Phrase gestures' ? 'Click a mouse button with your desired modifiers.' : 'Use a key or mouse input supported by this action.');
      return;
    }
    const conflict = SHORTCUTS.find(other => other.id !== id && (other.group === action.group
      || (other.group === 'Section headers' && ['reroll', 'reset', 'clearSections'].includes(action.id))
      || (action.group === 'Section headers' && ['reroll', 'reset', 'clearSections'].includes(other.id))
      || (['Global', 'Phrase editor'].includes(other.group) && ['Global', 'Phrase editor'].includes(action.group)))
      && this.preferences.shortcuts()[other.id].some(value => sameShortcut(value, binding)));
    if (conflict) { this.shortcutMessage.set('Already used by ' + conflict.label + '. Clear or change that binding first.'); return; }
    this.captureUntil = performance.now() + 500;
    this.preferences.setShortcut(id, binding);
    this.cancelRecording();
    this.shortcutMessage.set(action.label + ': ' + shortcutLabel(binding));
  };

  constructor() {
    const document = globalThis.document;
    const capture = this.captureShortcut as EventListener;
    for (const type of ['keydown', 'keyup', 'pointerdown', 'wheel']) document.addEventListener(type, capture, { capture: true, passive: false });
    const suppress = (event: Event) => {
      if ((event.target as HTMLElement)?.closest?.('[data-cancel-recording]')) return;
      if (this.recording() || performance.now() < this.captureUntil) { event.preventDefault(); event.stopImmediatePropagation(); }
    };
    for (const type of ['click', 'auxclick', 'contextmenu']) document.addEventListener(type, suppress, true);
    inject(DestroyRef).onDestroy(() => {
      this.cancelRecording();
      for (const type of ['keydown', 'keyup', 'pointerdown', 'wheel']) document.removeEventListener(type, capture, true);
      for (const type of ['click', 'auxclick', 'contextmenu']) document.removeEventListener(type, suppress, true);
    });
    const timer = setInterval(() => this.previewTime.set(new Date()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
    effect(onCleanup => {
      const backdrop = this.dialog.overlayRef.backdropElement;
      backdrop?.classList.toggle('appearance-preview', this.preview() && this.selectedCategory() === 'appearance');
      onCleanup(() => backdrop?.classList.remove('appearance-preview'));
    });
  }
}
