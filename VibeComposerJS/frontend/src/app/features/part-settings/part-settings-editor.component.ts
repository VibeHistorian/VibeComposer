import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
import { NgTemplateOutlet } from '@angular/common';
import { combinedControlKeys } from '../../core/ui/ui-preferences';
import { Dialog, DialogModule } from '@angular/cdk/dialog';
import { PatternSequencerComponent } from './pattern-sequencer.component';
import { DurationsDialogComponent } from './durations-dialog.component';
import { durationPreview } from '../../core/music/durations';
import { validateDurationDraft } from '../../core/music/duration-validation';
import type { CommonPartSettings, ChordRhythm } from '../../core/music/part-settings.model';
import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import type { ArrangedPart } from '../../core/music/part-role';
import type { PartSettingsScope } from '../../core/project/part-settings-scope';
import { PART_CONTROLS, type PartControl, type PartSettingValue } from '../../core/music/part-settings';
import { chordRhythmMask } from '../../core/music/rhythm-pattern';
import { DEFAULT_CUSTOM_VELOCITIES } from '../../core/music/velocity-pattern';
import { CompactKnobComponent } from '../../shared/compact-knob.component';
import { CompactToggleComponent } from '../../shared/compact-toggle.component';
import { SelectComponent } from '../../shared/select.component';
import { ControlHeadingComponent, type ControlInheritance } from '../../shared/control-heading.component';
import { PART_CONTROL_GROUPS, partControlGroup } from './part-control-groups';
import { ProjectService } from '../../core/project/project.service';

@Component({
  selector: 'vc-part-settings-editor',
  imports: [NgTemplateOutlet, DialogModule, CompactKnobComponent, CompactToggleComponent, ControlHeadingComponent, SelectComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-control-role]': 'role()', '[class.quick-layout]': '!showGroupTitles()', '[class.placement-editing]': 'preferences.editingQuickSettings()' },
  templateUrl: './part-settings-editor.component.html',
  styleUrl: './part-settings-editor.component.css',
})
export class PartSettingsEditorComponent {
  readonly delayOptions = [0, 1, 2, 3, 4, 5];
  readonly shiftOptions = [0, 1, 2, 3, 4, 5, 6, 7, 8];
  isQuick(key: string): boolean {
    return combinedControlKeys(key, this.role()).some(key => this.preferences.quickSettings()[this.role()].includes(key));
  }
  members(control: PartControl): readonly PartControl[] {
    const keys = control.key === 'rhythm' ? ['hitsPerPattern', 'patternShift']
      : control.key === 'feedbackCount' ? ['feedbackCount', 'feedbackDuration']
      : control.key === 'velocityMin' ? ['velocityMin', 'velocityMax']
      : control.key === 'rangeMin' ? ['rangeMin', 'rangeMax']
      : control.key === 'ending' ? ['ending', 'style', 'form'] : [];
    return keys.flatMap(key => this.controls().filter(control => control.key === key));
  }
  title(control: PartControl): string {
    if (control.key === 'rhythm' && ['hitsPerPattern', 'patternShift'].every(key => this.members(control).some(member => member.key === key))) {
      return 'PATTERN / # / SHIFT';
    }
    return control.key === 'velocityMin' ? 'VEL↓↑' : control.key === 'rangeMin' ? 'MIDI↓↑' : control.key === 'ending' ? 'SENTENCE'
      : control.kind === 'number' ? this.code(control) : control.label;
  }
  chooseNumber(control: PartControl, selection: string): void {
    const value = selection;
    if (value !== '') this.settingsChanged.emit({ key: control.key, value: Number(value) });
  }
  code(control: PartControl): string {
    const codes: Readonly<Record<string, string>> = {
      fillFlip: 'FILL↔', patternFlip: 'PAT↔', useCustomVelocities: 'VGRID',
      stretchEnabled: 'VOICE', fillPauses: 'HOLD', patternFlexible: 'FLEX', octaveInterval: 'OCT±',
      transpose: 'TRANS', velocityMin: 'VEL↓', velocityMax: 'VEL↑', noteLengthMultiplier: 'LEN', noteLengthPercent: 'LEN',
      rangeMin: 'MIDI↓', rangeMax: 'MIDI↑', tension: 'TENSE',
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
    if (!velocities && (this.role() === 'melody' || this.usesDurations())) {
      this.openDurations();
      return;
    }
    this.dialogs.open(PatternSequencerComponent, { ariaLabel: this.role() === 'drums' ? 'Drum pattern sequencer' : 'Pattern sequencer',
      data: { scope: this.scope(), role: this.role(), velocities, melodyHits: this.melodyHits() } });
  }
  private openDurations(flipped?: boolean): void {
    this.dialogs.open(DurationsDialogComponent, { ariaLabel: `Custom ${this.role()} durations`,
      data: { scope: this.scope(), flipped } });
  }
  readonly usesDurations = computed(() => this.values()['rhythm'] === 'durations');
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
    !['patternFlip', 'fillFlip', 'customPattern', 'customDurations', 'customDurationChances', 'customDurationRandomWeighting'].includes(control.key) &&
    (this.inspectorOnly() ? !this.isQuick(control.key)
      : this.advanced() || this.isQuick(control.key)) &&
    (this.preferences.editingQuickSettings() || (
    (!['rangeMin', 'rangeMax', 'tension', 'ending', 'style', 'form'].includes(control.key) || this.role() !== 'melody' || (this.projects.project().melodyGlobal?.contextualMelody ?? true)) &&
    (!this.usesDurations() || this.role() !== 'melody'
      || !['speed', 'pauseChance', 'fillPauses', 'splitChance', 'startNoteChance'].includes(control.key)) &&
    (this.role() !== 'melody' || !(this.projects.project().melodyGlobal?.contextualMelody ?? true)
      || !['fillPauses', 'fillPausesPerChord', 'patternFlexible', 'tonicNoteTarget', 'modeNoteTarget', 'chordNoteTarget', 'emphasizeKey'].includes(control.key)) &&
    (!this.usesDurations() || this.role() === 'melody' || !['patternShift', 'chordSpan', 'patternRepeat', 'patternJoinMode', 'euclideanPulses', 'useCustomVelocities', 'customVelocities', 'pauseChance', 'exceptionChance', ...(this.role() === 'bass' ? ['hitsPerPattern'] : [])].includes(control.key)) &&
    (this.values()['rhythm'] !== 'melody' || !['hitsPerPattern', 'patternShift', 'chordSpan', 'patternRepeat',
      ...(this.melodyHits() > 32 ? ['customVelocities', 'useCustomVelocities'] : [])].includes(control.key)) &&
    (control.key !== 'arpPatternCustom' || this.values()['pattern'] === 'custom' || this.values()['pattern'] === null) &&
    (!(this.role() === 'bass' && this.values()['rhythm'] === 'bass1')
      || !['hitsPerPattern', 'chordSpan', 'patternShift', 'patternFlip', 'euclideanPulses', 'customPattern', 'useCustomVelocities', 'customVelocities', 'patternJoinMode'].includes(control.key))
    &&
    (control.key !== 'euclideanPulses' || this.values()['rhythm'] === 'euclid' || this.values()['rhythm'] === null)
    && (control.key !== 'customPattern' || this.values()['rhythm'] !== 'melody' || this.role() === 'drums')
    && (control.key !== 'customVelocities' || this.values()['useCustomVelocities'] === true)
    && (!['velocityMin', 'velocityMax'].includes(control.key) || this.values()['useCustomVelocities'] !== true || this.usesDurations()
      || (this.values()['rhythm'] === 'melody' && this.melodyHits() > 32)
      || (this.role() === 'bass' && this.values()['rhythm'] === 'bass1'))))));
  readonly controlGroups = computed(() => PART_CONTROL_GROUPS.map(group => ({
    id: group.id, label: group.label,
    controls: this.controls().filter(control => !['patternSeed', 'feedbackDuration', 'velocityMax', 'rangeMax', 'style', 'form'].includes(control.key)
      && (!['hitsPerPattern', 'patternShift'].includes(control.key) || !this.controls().some(control => control.key === 'rhythm'))
      && partControlGroup(control.key) === group.id),
  })).filter(group => group.controls.length > 0));
  readonly seedControl = computed(() => this.controls().find(control => control.key === 'patternSeed'));
  readonly velocitySlots = computed(() => {
    const values = this.values();
    if (values['hitsPerPattern'] === null || values['customVelocities'] === null) return null;
    return (values['customVelocities'] as readonly number[] ?? DEFAULT_CUSTOM_VELOCITIES).slice(0, values['rhythm'] === 'melody' ? this.melodyHits() : values['hitsPerPattern'] as number ?? 8);
  });
  readonly rhythmPreview = computed(() => {
    if (this.usesDurations() || this.role() === 'melody' || this.values()['rhythm'] === 'melody' || (this.role() === 'bass' && this.values()['rhythm'] === 'bass1')) return undefined;
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
  readonly durationPreview = computed(() => {
    if (!this.usesDurations()) return undefined;
    if (['customDurations', 'patternFlip', 'customDurationChances', 'customDurationRandomWeighting']
      .some(key => this.values()[key] === null)) return null;
    return durationPreview(this.values() as unknown as CommonPartSettings);
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
    if (control.key === 'rhythm' && this.role() === 'melody' && !this.usesDurations()) return undefined;
    if (control.key === 'rhythm' && this.role() === 'bass' && this.values()['rhythm'] === 'bass1'
        && !this.preferences.editingQuickSettings()) return undefined;
    const key = control.key === 'rhythm' ? 'patternFlip' : control.key === 'chordSpanFill' ? 'fillFlip' : undefined;
    return key ? PART_CONTROLS[this.role()].find(control => control.key === key) : undefined;
  }

  toggleFlip(control: PartControl): void {
    const value = this.values()[control.key] !== true;
    if (this.usesDurations() && control.key === 'patternFlip'
      && validateDurationDraft(this.projects.project(), this.scope(), { patternFlip: value })) {
      this.openDurations(value);
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

  choose(control: PartControl, selection: string, select?: SelectComponent): void {
    const value = selection;
    if (value === '') return;
    if (control.key === 'rhythm' && value === 'durations'
      && validateDurationDraft(this.projects.project(), this.scope(), { rhythm: 'durations' })) {
      select?.restoreValue();
      this.openDurations();
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
