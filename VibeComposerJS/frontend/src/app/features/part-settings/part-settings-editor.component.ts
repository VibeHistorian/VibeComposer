import { trackPhraseSource } from '../../core/music/phrase';
import { MenuSelectComponent, type SelectMenuGroup } from '../../shared/menu-select.component';
import { ParameterShortcutDirective } from './parameter-shortcut.directive';
import { inspectArrangementVariation } from '../../core/music/arrangement-variation-inspection';
import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
import { NgTemplateOutlet } from '@angular/common';
import { combinedControlKeys } from '../../core/ui/ui-preferences';
import { DEFAULT_MELODY_GLOBAL } from '../../core/music/melody-global-settings';
import { Dialog, DialogModule } from '@angular/cdk/dialog';
import { CdkConnectedOverlay, type ConnectedPosition } from '@angular/cdk/overlay';
import { PatternSequencerComponent } from './pattern-sequencer.component';
import { DurationsDialogComponent } from './durations-dialog.component';
import { NumericSequenceDialogComponent } from './numeric-sequence-dialog.component';
import { NumericSequencePreviewComponent } from './numeric-sequence-preview.component';
import { partSequenceEditor, partSequencePatch, partSequenceRange, partSequenceRows, type PartSequenceKey } from './part-sequence-editor';
import { durationPreview } from '../../core/music/durations';
import { validateDurationDraft } from '../../core/music/duration-validation';
import type { CommonPartSettings, ChordRhythm, PatternJoinMode } from '../../core/music/part-settings.model';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import type { PartSettingsScope } from '../../core/project/part-settings-scope';
import { PART_CONTROLS, MANUAL_PARAMETER_TARGETS, type PartControl, type PartSettingValue, partValuesEqual } from '../../core/music/part-settings';
import { chordRhythmMask } from '../../core/music/rhythm-pattern';
import { isMelodyGrid, melodyGridSteps, MIN_MELODY_GRID_ATTACKS } from '../../core/music/melody-grid';
import { DEFAULT_CUSTOM_VELOCITIES } from '../../core/music/velocity-pattern';
import { CompactKnobComponent } from '../../shared/compact-knob.component';
import { CompactToggleComponent } from '../../shared/compact-toggle.component';
import { SelectComponent } from '../../shared/select.component';
import { ControlHeadingComponent, type ControlInheritance } from '../../shared/control-heading.component';
import { PART_CONTROL_GROUPS, partControlGroup } from './part-control-groups';
import { ProjectService } from '../../core/project/project.service';
import { partEditorContext } from './part-editor-context';
import { samePhraseInputs } from '../../core/music/phrase-inputs';
import { sectionGenerationHarmony } from '../../core/music/project-harmony';

@Component({
  selector: 'vc-part-settings-editor',
  imports: [MenuSelectComponent, ParameterShortcutDirective, NgTemplateOutlet, DialogModule, CompactKnobComponent, CompactToggleComponent, ControlHeadingComponent, SelectComponent, NumericSequencePreviewComponent, CdkConnectedOverlay],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-control-role]': 'role()', '[style.--variation-role-color]': '"var(--role-" + role() + ")"', '[class.quick-layout]': '!showGroupTitles()', '[class.placement-editing]': 'preferences.editingQuickSettings()' },
  templateUrl: './part-settings-editor.component.html',
  styleUrl: './part-settings-editor.component.css',
})
export class PartSettingsEditorComponent {
  readonly rhythmGroups: readonly SelectMenuGroup[] = [
    { id: 'static', label: 'Static patterns', values: ['full', 'half', 'tresillo', 'sparse', 'single', 'one-six'] },
  ];
  readonly fillGroups: readonly SelectMenuGroup[] = [
    { id: 'alternating', label: 'Alternating', values: ['ODD', 'EVEN'] },
    { id: 'position', label: 'Single position', values: ['F1', 'F2', 'F3', 'F4'] },
    { id: 'pairs', label: 'Adjacent pairs', values: ['F12', 'F23', 'F34'] },
    { id: 'halves', label: 'Halves', values: ['HALF1', 'HALF2'] },
  ];
  readonly fillOptions = [
    { value: 'ALL', label: 'ALL' },
    { value: 'ODD', label: 'ODD' }, { value: 'EVEN', label: 'EVEN' },
    { value: 'F1', label: 'POS1' }, { value: 'F2', label: 'POS2' },
    { value: 'F3', label: 'POS3' }, { value: 'F4', label: 'POS4' },
    { value: 'F12', label: 'POS12' }, { value: 'F23', label: 'POS23' }, { value: 'F34', label: 'POS34' },
    { value: 'HALF1', label: 'HALF1' }, { value: 'HALF2', label: 'HALF2' },
  ];
  readonly strumGroups: readonly SelectMenuGroup[] = [
    { id: 'arp', label: 'Arpeggiated', values: ['ARP_U', 'ARP_D'] },
    { id: 'random', label: 'Random', values: ['RAND_U', 'RAND', 'RAND_D', 'RAND_WU'] },
    { id: 'human', label: 'Humanized', values: ['HUMAN_U', 'HUMAN', 'HUMAN_D'] },
  ];
  readonly strumDisplayLabels: Readonly<Record<string, string>> = {
    ARP_U: 'Arp.Up', ARP_D: 'Arp.Down',
    RAND_U: 'Rand.Up', RAND: 'Rand.Any', RAND_D: 'Rand.Down', RAND_WU: 'Rand.Spaced',
    HUMAN_U: 'Human.Up', HUMAN: 'Human.Any', HUMAN_D: 'Human.Down',
  };
  readonly strumOptions = [
    { value: 'ARP_U', label: 'Arpeggiated up' }, { value: 'ARP_D', label: 'Arpeggiated down' },
    { value: 'RAND_U', label: 'Random up' }, { value: 'RAND', label: 'Random unordered' },
    { value: 'RAND_D', label: 'Random down' }, { value: 'RAND_WU', label: 'Random spaced up' },
    { value: 'HUMAN_U', label: 'Humanized up' }, { value: 'HUMAN', label: 'Humanized unordered' }, { value: 'HUMAN_D', label: 'Humanized down' },
  ];
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
      : control.key === 'rhythmicReuse' ? combinedControlKeys(control.key, this.role())
      : control.key === 'ending' ? ['ending', 'style', 'form'] : [];
    return keys.flatMap(key => this.controls().filter(control => control.key === key));
  }
  title(control: PartControl): string {
    if (control.key === 'rhythm' && ['hitsPerPattern', 'patternShift'].every(key => this.members(control).some(member => member.key === key))) {
      return 'PATTERN / # / SHIFT';
    }
    if (control.key === 'rhythmicReuse') return 'REUSE / BARS / VAR LOC';
    return control.key === 'velocityMin' ? 'VEL↓↑' : control.key === 'rangeMin' ? 'MIDI↓↑' : control.key === 'ending' ? 'SENTENCE'
      : control.kind === 'number' ? this.code(control) : control.label;
  }
  chooseNumber(control: PartControl, selection: string, select: Pick<SelectComponent, 'restoreValue'>): void {
    const value = selection;
    if (value !== '') this.settingsChanged.emit({ key: control.key, value: Number(value) });
    if (this.role() === 'melody') select.restoreValue();
  }
  code(control: PartControl): string {
    const codes: Readonly<Record<string, string>> = {
      fillFlip: 'FILL↔', patternFlip: 'PAT↔', useCustomVelocities: 'VGRID',
      stretchEnabled: 'VOICE', fillPauses: 'HOLD', patternFlexible: 'FLEX', octaveInterval: 'OCT±',
      transpose: 'TRANS', velocityMin: 'VEL↓', velocityMax: 'VEL↑', noteLengthMultiplier: 'LEN', noteLengthPercent: 'LEN',
      rangeMin: 'MIDI↓', rangeMax: 'MIDI↑', tension: 'TENSE',
      feedbackDuration: 'D.TIM', feedbackVol: 'D.VEL', feedbackCount: 'DELAY',
      maxDirectionChanges: 'DIR ⤒', newBlocksChance: 'BLOCK*', tonicNoteTarget: 'TONIC%', chordNoteTarget: 'CHORD%',
      maxBlockChange: 'BLOCK⤒', blockJump: 'BLOCK↕', hitsPerPattern: 'HITS', euclideanPulses: 'PULSE',
      patternShift: 'SHIFT', chordSpan: 'SPAN', chordNotesStretch: 'VOICE', patternRepeat: 'REPEAT',
      noteVariation: 'VAR', pauseChance: 'PAUSE', exceptionChance: 'SPLIT', swingPercent: 'SWING',
      syncopation: 'SYNC', rhythmicReuse: 'REUSE', pitchReuse: 'P.REUSE', articulationContrast: 'ARTIC', velocityContrast: 'VEL CT',
      entryPlacement: 'ENTRY', rhythmicGrouping: 'GROUP', motifLength: 'MOTIF', variationLocation: 'VAR LOC',
      strumPauseChance: 'STRUM⏸︎', transitionChance: 'TRAN%', transitionSplit: 'TRAN⌖',
      offset: 'OFFS', accents: 'ACCENT',
    };
    const manualTarget = MANUAL_PARAMETER_TARGETS[control.key];
    const label = codes[manualTarget ?? control.key] ?? control.label.toUpperCase().slice(0, 6);
    return manualTarget ? `m${label}` : label;
  }
  readonly preferences = inject(UiPreferencesService);
  readonly projects = inject(ProjectService);
  private readonly dialogs = inject(Dialog);
  readonly scope = input.required<PartSettingsScope>();
  private readonly context = computed(() => partEditorContext(this.projects.project(), this.scope()));
  openSequence(key: PartSequenceKey): void {
    const scope = this.scope();
    const editor = partSequenceEditor(this.projects.project(), scope, key);
    this.dialogs.open(NumericSequenceDialogComponent, { ariaLabel: editor.title, data: { scope, editor } });
  }
  private readonly arpeggioPreview = computed(() => {
    if (this.role() !== 'arpeggio') return null;
    const rows = partSequenceRows(this.projects.project(), this.scope(), 'pattern');
    if (!rows.length || rows.some(row => !partValuesEqual(row.values, rows[0].values))) return null;
    return rows[0].values;
  });
  sequenceKey(control: PartControl): PartSequenceKey | undefined {
    const { key } = control;
    return key === 'chordNoteChoices' || key === 'melodyPatternOffsets' || key === 'arpContour' ? key : undefined;
  }
  sequenceValues(key: PartSequenceKey): readonly number[] | null {
    return key === 'pattern' ? this.arpeggioPreview() : this.values()[key] as readonly number[] | null;
  }
  readonly sequenceRange = partSequenceRange;
  readonly sequenceHover = signal<{ key: PartSequenceKey; index: number | null } | null>(null, {
    equal: (a, b) => a?.key === b?.key && a?.index === b?.index,
  });
  readonly sequenceTooltipPositions: ConnectedPosition[] = [
    { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -6 },
    { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: 6 },
  ];
  hoverSequence(key: PartSequenceKey, event: MouseEvent): void {
    this.sequenceHover.set({ key, index: this.sequenceColumn(event, this.sequenceValues(key)?.length ?? 0) });
  }
  private sequenceColumn(event: MouseEvent, length: number): number | null {
    const preview = (event.currentTarget as HTMLElement).querySelector('vc-numeric-sequence-preview');
    const bounds = preview?.getBoundingClientRect();
    if (!bounds?.width || !length) return null;
    const column = Math.floor((event.clientX - bounds.left) / bounds.width * length);
    return Math.max(0, Math.min(length - 1, column));
  }
  scrollSequence(key: PartSequenceKey, event: WheelEvent): void {
    if (this.preferences.editingQuickSettings() || !event.deltaY || !this.preferences.matchesShortcut('controlWheel', event)) return;
    const values = this.sequenceValues(key);
    if (!values?.length) return;
    const index = this.sequenceColumn(event, values.length);
    if (index === null) return;
    if (this.sequenceHover()?.key === key) this.sequenceHover.set({ key, index });
    event.preventDefault();
    event.stopPropagation();
    const { minimum, maximum } = partSequenceRange(key);
    const value = values[index] - Math.sign(event.deltaY);
    if (value < minimum || value > maximum) return;
    const next = [...values];
    next[index] = value;
    this.projects.dismissGenerationError();
    this.projects.updatePartSettings(this.scope(), partSequencePatch(key, next), `sequence-wheel:${key}`);
  }
  openSequencer(velocities = false): void {
    if (!velocities && this.usesDurations()) {
      this.openDurations();
      return;
    }
    this.dialogs.open(PatternSequencerComponent, { ariaLabel: this.role() === 'drums' ? 'Drum pattern sequencer' : 'Pattern sequencer',
      data: { scope: this.scope(), role: this.role(), melodyHits: this.melodyHits(), velocities } });
  }
  private openDurations(flipped?: boolean): void {
    this.dialogs.open(DurationsDialogComponent, { ariaLabel: `Custom ${this.role()} durations`,
      data: { scope: this.scope(), flipped } });
  }
  readonly usesDurations = computed(() => this.values()['rhythm'] === 'durations');
  private readonly usesMelodyGrid = computed(() => this.role() === 'melody' && this.context().rows.some(row => isMelodyGrid(row['rhythm'])));
  readonly emptyAllowedValues: readonly number[] = [];
  readonly role = computed(() => this.context().role);
  readonly values = computed(() => this.context().values);
  readonly overriddenKeys = computed(() => this.context().overriddenKeys);
  readonly local = computed(() => 'sectionId' in this.scope());
  readonly advanced = input(true);
  readonly inspectorOnly = input(false);
  readonly showGroupTitles = input(true);
  private readonly phraseProject = computed(() => this.projects.project(), { equal: samePhraseInputs });
  readonly melodyHits = computed(() => {
    const project = this.phraseProject(), scope = this.scope();
    const section = 'sectionId' in scope ? project.arrangement.find(section => section.id === scope.sectionId) : undefined;
    return (section ? sectionGenerationHarmony(project, section, 'melody').degrees : project.progression).length * 16;
  });
  readonly settingsChanged = output<{ readonly key: string; readonly value: PartSettingValue }>();
  readonly inheritRequested = output<string | readonly string[]>();
  private textEditValues: Readonly<Record<string, PartSettingValue | null>> | null = null;
  readonly phraseModes = computed(() => {
    const project = this.projects.project();
    const { tracks, section } = this.context();
    return tracks
      .map(track => trackPhraseSource(track, section, project).endsWith('edited'));
  });
  private readonly reuseActivity = computed(() => {
    const { rows } = this.context();
    const manual = this.phraseModes();
    const active = rows.filter((row, index) =>
      !manual[index] && row['rhythm'] === 'melody1' && Number(row['rhythmicReuse']) > 0).length;
    return active === 0 ? 'none' : active < rows.length ? 'some' : 'all';
  });
  reuseMemberInactive(key: string): boolean {
    return (key === 'motifLength' || key === 'variationLocation') && this.reuseActivity() === 'none';
  }
  memberDescription(member: PartControl): string {
    const description = member.description ?? '';
    if (this.reuseMemberInactive(member.key)) return 'Increase Rhythmic Reuse to use this value.';
    if (member.key !== 'motifLength' && member.key !== 'variationLocation') return description;
    const phraseBars = this.projects.project().melodyGlobal?.phraseBars ?? DEFAULT_MELODY_GLOBAL.phraseBars;
    const bars = this.values()['motifLength'];
    const phraseHint = member.key === 'motifLength' && typeof bars === 'number' && bars >= phraseBars
      ? ' The motif uses the whole phrase at this length.' : '';
    const scopeHint = this.reuseActivity() === 'some' ? ' Used only by selected tracks with automatic rhythm and reuse enabled.' : '';
    return description + phraseHint + scopeHint;
  }
  readonly controls = computed(() => PART_CONTROLS[this.role()].filter((control) =>
    (this.preferences.editingQuickSettings() || (control.key in MANUAL_PARAMETER_TARGETS
      ? this.phraseModes().some(Boolean) && (control.key !== 'manualTranspose' || this.role() !== 'drums')
      : this.phraseModes().some(manual => !manual))) &&
    !['patternFlip', 'fillFlip', 'customPattern', 'customDurations', 'customDurationChances', 'customDurationRandomWeighting', 'arpPatternCustom'].includes(control.key) &&
    (this.inspectorOnly() ? !this.isQuick(control.key)
      : this.advanced() || this.isQuick(control.key)) &&
    (this.preferences.editingQuickSettings() || (
    (!['rangeMin', 'rangeMax', 'tension', 'ending', 'style', 'form', 'syncopation', 'rhythmicReuse', 'entryPlacement', 'rhythmicGrouping', 'motifLength', 'variationLocation', 'pitchReuse', 'articulationContrast', 'velocityContrast'].includes(control.key)
      || this.role() !== 'melody' || (this.projects.project().melodyGlobal?.contextualMelody ?? true)) &&
    (this.role() !== 'melody' || (
      (this.usesMelodyGrid() || !['hitsPerPattern', 'patternShift', 'euclideanPulses', 'patternJoinMode'].includes(control.key)) &&
      (!this.usesDurations() && !isMelodyGrid(this.values()['rhythm'])
        || !['speed', 'pauseChance', 'fillPauses', 'fillPausesPerChord', 'splitChance', 'startNoteChance', 'syncopation',
          'rhythmicReuse', 'entryPlacement', 'rhythmicGrouping', 'motifLength', 'variationLocation'].includes(control.key)) &&
      (!isMelodyGrid(this.values()['rhythm']) || control.key !== 'swingPercent')
    )) &&
    (this.role() !== 'melody' || !(this.projects.project().melodyGlobal?.contextualMelody ?? true)
       || !['accents', 'fillPauses', 'fillPausesPerChord', 'patternFlexible', 'tonicNoteTarget', 'modeNoteTarget', 'chordNoteTarget', 'emphasizeKey'].includes(control.key)) &&
    (!this.usesDurations() || this.role() === 'melody' || !['patternShift', 'chordSpan', 'patternRepeat', 'patternJoinMode', 'euclideanPulses', 'useCustomVelocities', 'customVelocities', 'pauseChance', 'exceptionChance', ...(this.role() === 'bass' ? ['hitsPerPattern'] : [])].includes(control.key)) &&
    (this.values()['rhythm'] !== 'melody' || !['hitsPerPattern', 'patternShift', 'chordSpan', 'patternRepeat',
      ...(this.melodyHits() > 32 ? ['customVelocities', 'useCustomVelocities'] : [])].includes(control.key)) &&
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
    controls: this.controls().filter(control => !['patternSeed', 'feedbackDuration', 'velocityMax', 'rangeMax', 'style', 'form', 'motifLength', 'variationLocation'].includes(control.key)
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
    if (this.usesDurations() || (this.role() === 'melody' && !this.usesMelodyGrid()) || this.values()['rhythm'] === 'melody'
      || (this.role() === 'bass' && this.values()['rhythm'] === 'bass1')) return undefined;
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
    if (pattern && this.role() === 'melody') {
      const join = this.values()['patternJoinMode'] as PatternJoinMode | null;
      return join === null ? 'Mixed Pattern Join settings. Edit the rhythm grid for each track.'
        : `${melodyGridSteps(pattern, join).filter(value => value > 0).length} attacks per chord after Pattern Join. Edit the rhythm grid.`;
    }
    return pattern ? `${pattern.filter((slot) => slot > 0).length} enabled slots in the ${pattern.length}-cell base rhythm. Span and Repeat change its placement; Pause and Fill can suppress notes; zero velocity silences individual hits.`
      : 'Mixed rhythm settings. Select an individual track to see its rhythm.';
  });

  readonly variationKeys = computed(() => {
    const scope = this.scope();
    if (!('sectionId' in scope)) return new Set<string>();
    const project = this.projects.project();
    const { section, tracks } = this.context();
    if (!section) return new Set<string>();
    const keys = new Set<string>();
    for (const track of tracks) {
      const choice = section.variations?.choices.find(choice => choice.trackId === track.id);
      if (!choice) continue;
      const inspection = inspectArrangementVariation(track, section, choice, project.melodyGlobal);
      if (inspection.status !== 'active') continue;
      for (const change of inspection.changes) {
        if (change.source === 'Variation' && !partValuesEqual(change.before, change.effective)) keys.add(change.parameter);
      }
    }
    return keys;
  });
  variationOnly(control: PartControl): boolean {
    return this.inheritanceFor(control) === 'inherited'
      && combinedControlKeys(control.key, this.role()).some(key => this.variationKeys().has(key));
  }
  inheritanceFor(control: PartControl): ControlInheritance {
    return this.local() ? combinedControlKeys(control.key, this.role()).some(key => this.overriddenKeys().includes(key)) ? 'custom' : 'inherited' : 'global';
  }

  parameterFrozen(key: string): boolean {
    if (this.local()) return this.overriddenKeys().includes(key);
    return this.context().tracks.some(track => track.parameterLocks?.includes(key));
  }

  categoryFreezeState(controls: readonly PartControl[]): 'none' | 'partial' | 'all' {
    const states = controls.flatMap(control => this.local()
      ? combinedControlKeys(control.key, this.role()).map(key => this.overriddenKeys().includes(key))
      : [this.lockState(control)]);
    return states.length && states.every(state => state === true) ? 'all'
      : states.some(state => state !== false) ? 'partial' : 'none';
  }

  lockState(control: PartControl): boolean | 'mixed' {
    if (this.local()) return false;
    const tracks = this.context().tracks;
    const keys = combinedControlKeys(control.key, this.role());
    const states = tracks.flatMap(track => keys.map(key => track.parameterLocks?.includes(key) ?? false));
    return states.length > 0 && states.every(Boolean) ? true : states.some(Boolean) ? 'mixed' : false;
  }
  parameterShortcut({ key, combined }: { key: string; combined: boolean }): void {
    const keys = combined ? combinedControlKeys(key, this.role()) : [key];
    if (this.local()) {
      const frozen = keys.filter(key => this.overriddenKeys().includes(key));
      if (frozen.length) this.inheritRequested.emit(frozen);
      return;
    }
    const scope = this.scope();
    const tracks = this.context().tracks;
    const locked = tracks.length > 0 && tracks.every(track => keys.every(key => track.parameterLocks?.includes(key)));
    this.projects.setParameterLocks(scope, keys, !locked);
  }

  toggleLock(control: PartControl): void {
    this.projects.setParameterLocks(this.scope(), combinedControlKeys(control.key, this.role()), this.lockState(control) !== true);
  }

  resetControl(control: PartControl): void {
    this.inheritRequested.emit(combinedControlKeys(control.key, this.role()));
  }

  flipControl(control: PartControl): PartControl | undefined {
    if (control.key === 'rhythm' && this.role() === 'melody' && !this.usesDurations() && !this.usesMelodyGrid()) return undefined;
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
    if (this.role() === 'melody' && control.key === 'hitsPerPattern') return MIN_MELODY_GRID_ATTACKS;
    return control.key === 'velocityMax' && typeof this.values()['velocityMin'] === 'number'
      ? this.values()['velocityMin'] as number : control.minimum!;
  }

  maximum(control: PartControl): number {
    return control.key === 'velocityMin' && typeof this.values()['velocityMax'] === 'number'
      ? this.values()['velocityMax'] as number : control.maximum!;
  }

  choose(control: PartControl, selection: string, select?: Pick<SelectComponent, 'restoreValue'>): void {
    const value = selection;
    if (value === '') return;
    if (control.key === 'rhythm' && value === 'durations'
      && validateDurationDraft(this.projects.project(), this.scope(), { rhythm: 'durations' })) {
      select?.restoreValue();
      this.openDurations();
      return;
    }
    this.settingsChanged.emit({ key: control.key, value: typeof control.options?.[0] === 'number' ? Number(value) : value });
    if (this.role() === 'melody') select?.restoreValue();
  }

  textValue(control: PartControl): string {
    const value = this.values()[control.key];
    return value === null ? '' : Array.isArray(value) ? value.join(', ') : String(value ?? control.defaultValue ?? '');
  }

  copyGlobalSeed(): void {
    // Narrow the exact global Java long to the part seed's Java int range.
    this.setSeed(Number(BigInt.asIntN(32, BigInt(this.projects.project().seed))));
  }

  seedMouseDown(event: MouseEvent): void {
    if (event.button !== 0) event.preventDefault();
  }

  seedMouseAction(event: MouseEvent): void {
    if (event.type !== 'contextmenu' && event.button !== 1) return;
    event.preventDefault();
    event.stopPropagation();
    const value = event.type === 'contextmenu' ? 0 : Math.floor(Math.random() * 2147483647) + 1;
    const element = event.target as HTMLInputElement;
    element.value = String(value);
    element.setCustomValidity('');
    this.setSeed(value);
  }

  private setSeed(value: number): void {
    this.textEditValues = null;
    this.settingsChanged.emit({ key: 'patternSeed', value });
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
