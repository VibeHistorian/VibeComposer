import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
import { combinedControlKeys } from '../../core/ui/ui-preferences';
import { Dialog, DialogModule } from '@angular/cdk/dialog';
import { PatternSequencerComponent } from './pattern-sequencer.component';
import { MelodyDurationsDialogComponent } from './melody-durations-dialog.component';
import { melodyDurationPreview } from '../../core/music/melody-durations';
import { validateMelodyDurationDraft } from '../../core/music/melody-duration-validation';
import type { MelodySettings } from '../../core/project/project.model';
import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import type { ArrangedPart, ChordRhythm, PartSettingsScope } from '../../core/project/project.model';
import { PART_CONTROLS, type PartControl, type PartSettingValue } from '../../core/music/part-settings';
import { chordRhythmMask } from '../../core/music/rhythm-pattern';
import { DEFAULT_CUSTOM_VELOCITIES } from '../../core/music/velocity-pattern';
import { CompactKnobComponent } from '../../shared/compact-knob.component';
import { CompactToggleComponent } from '../../shared/compact-toggle.component';
import { WheelSelectDirective } from '../../shared/wheel-select.directive';
import { ControlHeadingComponent, type ControlInheritance } from '../../shared/control-heading.component';
import { PART_CONTROL_GROUPS, partControlGroup } from './part-control-groups';
import { ProjectService } from '../../core/project/project.service';

@Component({
  selector: 'vc-part-settings-editor',
  imports: [DialogModule, CompactKnobComponent, CompactToggleComponent, ControlHeadingComponent, WheelSelectDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-control-role]': 'role()', '[class.quick-layout]': '!showGroupTitles()', '[class.placement-editing]': 'preferences.editingQuickSettings()' },
  templateUrl: './part-settings-editor.component.html',
  styleUrl: './part-settings-editor.component.css',
})
export class PartSettingsEditorComponent {
  code(control: PartControl): string {
    const codes: Readonly<Record<string, string>> = {
      fillFlip: 'FILL↔', patternFlip: 'PAT↔', useCustomVelocities: 'VGRID',
      stretchEnabled: 'VOICE', fillPauses: 'HOLD', patternFlexible: 'FLEX', octaveInterval: 'OCT±',
      transpose: 'TRANS', velocityMin: 'VEL↓', velocityMax: 'VEL↑', noteLengthMultiplier: 'LEN', noteLengthPercent: 'LEN',
      feedbackDuration: 'D.TIM', feedbackVol: 'D.VEL', feedbackCount: 'DELAY',
      maxBlockChange: 'B.MAX', blockJump: 'B.JMP', hitsPerPattern: 'HITS', euclideanPulses: 'PULSE',
      patternShift: 'SHIFT', chordSpan: 'SPAN', chordNotesStretch: 'VOICE', patternRepeat: 'REPT',
      noteVariation: 'VAR', pauseChance: 'PAUSE', exceptionChance: 'SPLIT', swingPercent: 'SWING',
      strumPauseChance: 'S.PAU', transitionChance: 'TR.CH', transitionSplit: 'TR.AT',
      offset: 'OFFS', accents: 'ACCNT',
    };
    return codes[control.key] ?? control.label.toUpperCase().slice(0, 5);
  }
  readonly preferences = inject(UiPreferencesService);
  readonly projects = inject(ProjectService);
  private readonly dialogs = inject(Dialog);
  readonly scope = input.required<PartSettingsScope>();
  openSequencer(velocities = false): void {
    if (this.role() === 'melody') {
      this.dialogs.open(MelodyDurationsDialogComponent, { ariaLabel: 'Custom melody durations', data: { scope: this.scope() } });
      return;
    }
    this.dialogs.open(PatternSequencerComponent, { ariaLabel: this.role() === 'drums' ? 'Drum pattern sequencer' : 'Pattern sequencer',
      data: { scope: this.scope(), role: this.role(), velocities, melodyHits: this.melodyHits() } });
  }
  readonly emptyAllowedValues: readonly number[] = [];
  readonly role = input.required<ArrangedPart>();
  readonly values = input.required<Readonly<Record<string, PartSettingValue | null>>>();
  readonly overriddenKeys = input<readonly string[]>([]);
  readonly local = input(false);
  readonly advanced = input(true);
  readonly inspectorOnly = input(false);
  readonly showGroupTitles = input(true);
  readonly melodyHits = input(16);
  readonly settingsChanged = output<{ readonly key: string; readonly value: PartSettingValue }>();
  readonly inheritRequested = output<string | readonly string[]>();
  private textEditValues: Readonly<Record<string, PartSettingValue | null>> | null = null;
  readonly controls = computed(() => PART_CONTROLS[this.role()].filter((control) =>
    !['patternFlip', 'fillFlip', 'customPattern', 'customMelodyDurations', 'customDurationChances', 'customDurationRandomWeighting'].includes(control.key) &&
    (this.inspectorOnly() ? !this.preferences.quickSettings()[this.role()].includes(control.key)
      : this.advanced() || this.preferences.quickSettings()[this.role()].includes(control.key)) &&
    (this.preferences.editingQuickSettings() || (
    (this.role() !== 'melody' || this.values()['rhythm'] !== 'custom'
      || !['speed', 'doubledRhythmChance', 'pauseChance', 'fillPauses', 'splitChance', 'leadChordsChance', 'startNoteChance'].includes(control.key)) &&
    (this.role() !== 'melody' || !this.projects.project().melodyGlobal?.contextualMelody
      || !['fillPauses', 'patternFlexible', 'doubledRhythmChance', 'maxNoteExceptions', 'noteExceptionChance'].includes(control.key)) &&
    (this.values()['rhythm'] !== 'melody' || !['hitsPerPattern', 'patternShift', 'chordSpan', 'patternRepeat',
      ...(this.melodyHits() > 32 ? ['customVelocities', 'useCustomVelocities'] : [])].includes(control.key)) &&
    (control.key !== 'arpPatternCustom' || this.values()['pattern'] === 'custom' || this.values()['pattern'] === null) &&
    (!(this.role() === 'bass' && this.values()['rhythm'] === 'bass1')
      || !['hitsPerPattern', 'chordSpan', 'patternShift', 'patternFlip', 'euclideanPulses', 'customPattern', 'useCustomVelocities', 'customVelocities', 'patternJoinMode'].includes(control.key))
    &&
    (control.key !== 'euclideanPulses' || this.values()['rhythm'] === 'euclid' || this.values()['rhythm'] === null)
    && (control.key !== 'customPattern' || this.values()['rhythm'] !== 'melody' || this.role() === 'drums')
    && (control.key !== 'customVelocities' || this.values()['useCustomVelocities'] === true)
    && (!['velocityMin', 'velocityMax'].includes(control.key) || this.values()['useCustomVelocities'] !== true
      || (this.values()['rhythm'] === 'melody' && this.melodyHits() > 32)
      || (this.role() === 'bass' && this.values()['rhythm'] === 'bass1'))))));
  readonly controlGroups = computed(() => PART_CONTROL_GROUPS.map(group => ({
    id: group.id, label: group.label,
    controls: this.controls().filter(control => control.key !== 'patternSeed' && partControlGroup(control.key) === group.id),
  })).filter(group => group.controls.length > 0));
  readonly seedControl = computed(() => this.controls().find(control => control.key === 'patternSeed'));
  readonly velocitySlots = computed(() => {
    const values = this.values();
    if (values['hitsPerPattern'] === null || values['customVelocities'] === null) return null;
    return (values['customVelocities'] as readonly number[] ?? DEFAULT_CUSTOM_VELOCITIES).slice(0, values['rhythm'] === 'melody' ? this.melodyHits() : values['hitsPerPattern'] as number ?? 8);
  });
  readonly rhythmPreview = computed(() => {
    if (this.role() === 'melody' || this.values()['rhythm'] === 'melody' || (this.role() === 'bass' && this.values()['rhythm'] === 'bass1')) return undefined;
    const values = this.values();
    const keys = ['rhythm', 'hitsPerPattern', 'patternShift', 'patternFlip',
      ...(values['rhythm'] === 'euclid' ? ['euclideanPulses'] : []),
      ...(values['rhythm'] === 'custom' ? ['customPattern'] : [])];
    if (keys.some((key) => values[key] === null)) return null;
    return chordRhythmMask({ rhythm: values['rhythm'] as ChordRhythm,
      hitsPerPattern: values['hitsPerPattern'] as number | undefined,
      patternShift: values['patternShift'] as number | undefined,
      patternFlip: values['patternFlip'] as boolean | undefined,
      euclideanPulses: values['euclideanPulses'] as number | undefined,
      customPattern: values['customPattern'] as readonly number[] | undefined });
  });
  readonly melodyPreview = computed(() => {
    if (this.role() !== 'melody' || this.values()['rhythm'] !== 'custom') return undefined;
    if (this.values()['customMelodyDurations'] === null || this.values()['patternFlip'] === null) return null;
    return melodyDurationPreview(this.values() as unknown as MelodySettings);
  });
  readonly rhythmPreviewLabel = computed(() => {
    const pattern = this.rhythmPreview();
    return pattern ? `${pattern.filter((slot) => slot > 0).length} enabled slots in the ${pattern.length}-cell base rhythm. Span and Repeat change its placement; Pause and Fill can suppress notes; zero velocity silences individual hits.`
      : 'Mixed rhythm settings. Select an individual track to see its rhythm.';
  });

  inheritanceFor(control: PartControl): ControlInheritance {
    return this.local() ? combinedControlKeys(control.key, this.role()).some(key => this.overriddenKeys().includes(key)) ? 'custom' : 'inherited' : 'global';
  }

  resetControl(control: PartControl): void {
    this.inheritRequested.emit(combinedControlKeys(control.key, this.role()));
  }

  flipControl(control: PartControl): PartControl | undefined {
    if (control.key === 'rhythm' && this.role() === 'melody' && this.values()['rhythm'] !== 'custom') return undefined;
    if (control.key === 'rhythm' && this.role() === 'bass' && this.values()['rhythm'] === 'bass1'
        && !this.preferences.editingQuickSettings()) return undefined;
    const key = combinedControlKeys(control.key)[1];
    return key ? PART_CONTROLS[this.role()].find(control => control.key === key) : undefined;
  }

  toggleFlip(control: PartControl): void {
    const value = this.values()[control.key] !== true;
    if (this.role() === 'melody' && control.key === 'patternFlip'
      && validateMelodyDurationDraft(this.projects.project(), this.scope(), { patternFlip: value })) {
      this.dialogs.open(MelodyDurationsDialogComponent, { ariaLabel: 'Custom melody durations', data: { scope: this.scope(), flipped: value } });
      return;
    }
    this.settingsChanged.emit({ key: control.key, value });
  }

  numericDefault(control: PartControl): number {
    return typeof control.defaultValue === 'number' ? control.defaultValue : control.minimum!;
  }

  choiceDefault(control: PartControl): string | number {
    return typeof control.defaultValue === 'string' || typeof control.defaultValue === 'number'
      ? control.defaultValue : control.options![0];
  }

  numericValue(control: PartControl): number {
    return (this.values()[control.key] as number | null)
      ?? (typeof control.defaultValue === 'number' ? control.defaultValue : control.minimum!);
  }

  minimum(control: PartControl): number {
    return control.key === 'velocityMax' && typeof this.values()['velocityMin'] === 'number'
      ? this.values()['velocityMin'] as number : control.minimum!;
  }

  maximum(control: PartControl): number {
    return control.key === 'velocityMin' && typeof this.values()['velocityMax'] === 'number'
      ? this.values()['velocityMax'] as number : control.maximum!;
  }

  choose(control: PartControl, event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    if (value === '') return;
    if (this.role() === 'melody' && control.key === 'rhythm' && value === 'custom'
      && validateMelodyDurationDraft(this.projects.project(), this.scope(), { rhythm: 'custom' })) {
      (event.target as HTMLSelectElement).value = String(this.values()[control.key] ?? 'melody1');
      this.openSequencer();
      return;
    }
    this.settingsChanged.emit({ key: control.key, value: typeof control.options?.[0] === 'number' ? Number(value) : value });
  }

  textValue(control: PartControl): string {
    const value = this.values()[control.key];
    return value === null ? '' : Array.isArray(value) ? value.join(', ') : String(value ?? control.defaultValue ?? '');
  }

  startTextEdit(): void { this.textEditValues = this.values(); }

  cancelTextEdit(control: PartControl, event: Event): void {
    const element = event.target as HTMLInputElement;
    element.value = this.textValue(control);
    element.setCustomValidity('');
    this.textEditValues = null;
  }

  commitText(control: PartControl, event: Event): void {
    const element = event.target as HTMLInputElement;
    if (this.textEditValues && this.textEditValues !== this.values()) { this.cancelTextEdit(control, event); return; }
    const pieces = control.kind === 'integers' ? (control.allowEmpty && !element.value.trim() ? [] : element.value.split(',').map((piece) => piece.trim())) : [element.value.trim()];
    const valid = pieces.length >= (control.allowEmpty ? 0 : 1) && pieces.length <= 32 && pieces.every((piece) => /^-?\d+$/.test(piece)
      && Number.isSafeInteger(Number(piece)) && Number(piece) >= control.minimum! && Number(piece) <= control.maximum!);
    if (!valid) {
      element.setCustomValidity(control.kind === 'integers' ? `Enter ${control.allowEmpty ? '0' : '1'}–32 comma-separated integers from ${control.minimum} to ${control.maximum}.`
        : `Enter an integer from ${control.minimum} to ${control.maximum}.`);
      element.reportValidity(); return;
    }
    element.setCustomValidity('');
    this.textEditValues = null;
    this.settingsChanged.emit({ key: control.key, value: control.kind === 'integers' ? pieces.map(Number) : Number(pieces[0]) });
  }

}
