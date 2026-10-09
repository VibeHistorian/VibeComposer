import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import type {
  ArrangedPart, ArrangementSection, SectionType, CompositionTrack, CompositionProject, PartSettingsScope,
} from '../core/project/project.model';
import { ARRANGED_PARTS, PART_GENERATION_AVAILABLE, SECTION_TYPES, tracksInRoleOrder } from '../core/project/project.model';
import { AudioPlaybackService } from '../core/audio/audio-playback.service';
import { shouldGenerateTrackInSection } from '../core/music/arrangement-generator';
import { DRUM_INSTRUMENTS, drumInstrumentName } from '../core/music/drum-instruments';
import { layOutTrackPhrase, phraseForTrack } from '../core/music/phrase';
import { generateDiatonicProgression } from '../core/music/progression-generator';
import { getDiatonicChords, KEYS } from '../core/music/harmony';
import { ProjectService } from '../core/project/project.service';
import { WorkspaceUiService } from './workspace-ui.service';
import { ArrangementOverviewComponent } from './arrangement-overview.component';
import { ScoreCanvasComponent } from './score-canvas.component';
import type { ScoreCanvasNote, ScoreCanvasTrack, ScoreSectionFocus, ScoreViewport } from './score-canvas.component';
import { EditWorkspaceComponent } from '../features/edit/edit-workspace.component';
import { MixWorkspaceComponent } from '../features/mix/mix-workspace.component';
import { PartSettingsEditorComponent } from './part-settings-editor.component';
import { PartScopeActionsComponent } from './part-scope-actions.component';
import { CompactKnobComponent } from './compact-knob.component';
import { WheelSelectDirective } from './wheel-select.directive';
import { resolvePartTrack, settingsValues, partValuesEqual, type PartSettingValue } from '../core/music/part-settings';

type TrackRow = CompositionTrack & {
  readonly color: ArrangedPart;
  readonly noteCount: number;
};

interface TimelineSection {
  readonly section: ArrangementSection;
  readonly startBeat: number;
  readonly startMeasure: number;
  readonly widthPercent: number;
}

type ScoreNote = ScoreCanvasNote;

const ROLE_NAMES: Readonly<Record<ArrangedPart, string>> = {
  melody: 'Melody', bass: 'Bass', chords: 'Chords', arpeggio: 'Arpeggio', drums: 'Drums',
};

const INSTRUMENTS: ReadonlyArray<{ program: number; name: string }> = [
  { program: 0, name: 'Acoustic Piano' }, { program: 4, name: 'Electric Piano' },
  { program: 10, name: 'Music Box' }, { program: 11, name: 'Vibraphone' },
  { program: 24, name: 'Nylon Guitar' }, { program: 25, name: 'Steel Guitar' },
  { program: 32, name: 'Acoustic Bass' }, { program: 33, name: 'Electric Bass' },
  { program: 38, name: 'Synth Bass' }, { program: 40, name: 'Violin' },
  { program: 48, name: 'Strings' }, { program: 56, name: 'Trumpet' },
  { program: 65, name: 'Alto Sax' }, { program: 73, name: 'Flute' },
  { program: 80, name: 'Square Lead' }, { program: 88, name: 'Warm Pad' },
];

@Component({
  selector: 'vc-workspace-canvas',
  imports: [ArrangementOverviewComponent, PartSettingsEditorComponent, PartScopeActionsComponent, EditWorkspaceComponent, MixWorkspaceComponent, ScoreCanvasComponent, CompactKnobComponent, WheelSelectDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './workspace-canvas.component.html',
  styleUrl: './workspace-canvas.component.css',
})
export class WorkspaceCanvasComponent {
  readonly projects = inject(ProjectService);
  readonly playback = inject(AudioPlaybackService);
  readonly workspaceUi = inject(WorkspaceUiService);
  readonly project = this.projects.project;
  readonly instruments = INSTRUMENTS;
  readonly drumInstruments = DRUM_INSTRUMENTS;
  readonly keys = KEYS;
  readonly sectionTypes = SECTION_TYPES;
  readonly midiChannels = Array.from({ length: 16 }, (_, index) => index + 1);
  readonly roles = ARRANGED_PARTS.map((role) => ({ role, name: ROLE_NAMES[role] }));
  readonly generationAvailable = PART_GENERATION_AVAILABLE;
  readonly selectedTrackId = this.workspaceUi.selectedTrackId;
  readonly selectedRole = this.workspaceUi.selectedRole;
  readonly editingNoteId = this.workspaceUi.editingNoteId;
  readonly selectedSectionId = this.workspaceUi.selectedSectionId;
  readonly selectedSectionIds = this.workspaceUi.selectedSectionIds;
  readonly settingsTarget = this.workspaceUi.settingsTarget;
  readonly partPanelExpanded = signal(true);
  readonly localPartTarget = computed(() => {
    const target = this.settingsTarget();
    return target.kind === 'section-role' || target.kind === 'section-track' ? target : null;
  });
  readonly contextTracks = computed(() => {
    const target = this.settingsTarget();
    if ('trackId' in target) return this.project().tracks.filter((track) => track.id === target.trackId);
    if ('role' in target) return this.project().tracks.filter((track) => track.role === target.role);
    return [];
  });
  readonly contextLabel = computed(() => {
    const target = this.settingsTarget();
    const section = 'sectionId' in target ? this.project().arrangement.find((item) => item.id === target.sectionId) : null;
    const sectionLabel = section ? `${this.project().arrangement.indexOf(section) + 1} · ${this.sectionLabel(section.type)}` : 'Global';
    if ('role' in target) return `${sectionLabel} · ${this.roleName(target.role)}`;
    if ('trackId' in target) return `${sectionLabel} · ${this.contextTracks()[0]?.name ?? 'Track'}`;
    return sectionLabel;
  });
  readonly partScope = computed<PartSettingsScope | null>(() => {
    const target = this.settingsTarget();
    return target.kind === 'section' ? null : target;
  });
  readonly editableScopes = computed(() => {
    const scope = this.partScope();
    if (!scope || !this.contextTracks().length || !this.partGenerationAvailable()) return [];
    return [{ scope, key: JSON.stringify(scope) }];
  });
  readonly partRole = computed(() => {
    const target = this.settingsTarget();
    return 'role' in target ? target.role : this.contextTracks()[0]?.role ?? 'bass';
  });
  readonly partGenerationAvailable = computed(() => this.generationAvailable[this.partRole()]);
  readonly contextSection = computed(() => {
    const target = this.localPartTarget();
    return target ? this.project().arrangement.find((section) => section.id === target.sectionId) : undefined;
  });
  readonly effectiveContextTracks = computed(() => this.contextTracks().map((track) => resolvePartTrack(track, this.contextSection())));
  readonly partValues = computed(() => {
    const values = this.effectiveContextTracks().map((track) => settingsValues(track.generatorSettings, track.role));
    return Object.fromEntries(Object.keys(values[0] ?? {}).map((key) => [key,
      values.every((value) => partValuesEqual(value[key], values[0][key])) ? values[0][key] : null]));
  });
  readonly overriddenKeys = computed(() => {
    const target = this.localPartTarget();
    const section = this.contextSection();
    if (!target || !section) return [];
    return Object.keys(target.kind === 'section-role' ? section.rolePartOverrides?.[target.role] ?? {}
      : section.trackPartOverrides?.[target.trackId] ?? {});
  });
  readonly inheritanceLabel = computed(() => {
    const target = this.localPartTarget();
    if (!target) return 'Global part settings';
    if (this.overriddenKeys().length) return target.kind === 'section-role' ? 'Customized cell' : 'Customized track part';
    return target.kind === 'section-track' && Object.keys(this.contextSection()?.rolePartOverrides?.[this.partRole()] ?? {}).length
      ? 'Inherited from cell settings' : 'Inherited from global tracks';
  });
  readonly trackExceptionCount = computed(() => {
    const target = this.localPartTarget();
    return target?.kind === 'section-role' ? this.contextTracks().filter((track) =>
      Object.keys(this.contextSection()?.trackPartOverrides?.[track.id] ?? {}).length).length : 0;
  });
  readonly manualTrackNames = computed(() => this.contextTracks().filter((track) => track.editedPhrase !== undefined).map((track) => track.name).join(', '));
  readonly partDestinations = computed(() => this.project().arrangement.filter((section) =>
    this.selectedSectionIds().includes(section.id) && section.id !== this.localPartTarget()?.sectionId));
  readonly partCopyAvailability = computed(() => {
    const scope = this.localPartTarget();
    const ids = this.partDestinations().map((section) => section.id);
    return { overrides: !!scope && this.projects.partSettingsCopyWouldChange(scope, ids, 'overrides'),
      effective: !!scope && this.projects.partSettingsCopyWouldChange(scope, ids, 'effective') };
  });
  private readonly workflowFeedback = signal<{ scope: string; destinations: string; project: CompositionProject; message: string } | null>(null);
  readonly partWorkflowMessage = computed(() => {
    const feedback = this.workflowFeedback();
    return feedback?.scope === JSON.stringify(this.localPartTarget()) && feedback.project === this.project()
      && feedback.destinations === JSON.stringify(this.partDestinations().map((section) => section.id)) ? feedback.message : null;
  });
  private readonly editFailure = signal<{ scope: string; message: string } | null>(null);
  readonly partEditError = computed(() => this.editFailure()?.scope === JSON.stringify(this.partScope()) ? this.editFailure()?.message : null);
  readonly editing = signal(false);
  readonly mixerOpen = signal(false);
  readonly newSectionType = signal<SectionType>('VERSE1');
  readonly hiddenTracks = signal(new Set<string>());
  readonly collapsedGroups = signal(new Set<ArrangedPart>());
  readonly scoreSectionFocus = computed<ScoreSectionFocus>(() => {
    this.selectedSectionIds();
    // A changed selection must refit even when its bounds equal the full arrangement.
    return { ...(this.workspaceUi.sectionRange() ?? { startBeat: 0, endBeat: this.totalBeats() }) };
  });
  readonly scoreViewport = signal<ScoreViewport>({ scale: 1, offsetPercent: 0, width: 0, laneHeight: 40, verticalOffset: 0, scoreHeight: 0 });

  readonly totalMeasures = computed(() => this.project().arrangement.reduce((sum, section) => sum + section.measures, 0));
  readonly totalBeats = computed(() => this.totalMeasures() * 4);
  readonly playheadPercent = computed(() => this.totalBeats() > 0
    ? Math.min(100, Math.max(0, this.playback.beat() / this.totalBeats() * 100)) : 0);
  readonly timelineSections = computed<TimelineSection[]>(() => {
    let beat = 0;
    let measure = 1;
    const measures = this.totalMeasures();
    return this.project().arrangement.map((section) => {
      const item = { section, startBeat: beat, startMeasure: measure, widthPercent: section.measures / measures * 100 };
      beat += section.measures * 4;
      measure += section.measures;
      return item;
    });
  });
  readonly tracks = computed<TrackRow[]>(() => this.project().tracks.map((track) => ({
    ...track,
    color: track.role,
    noteCount: phraseForTrack(this.project(), track).length,
  })));
  readonly arrangedTracks = computed(() => tracksInRoleOrder(this.tracks()));
  readonly scoreTracks = computed(() => {
    const tracks = this.arrangedTracks();
    const drums = tracks.filter(track => track.role === 'drums');
    return [...tracks.filter(track => track.role !== 'drums'), ...(drums.length ? [{
      id: 'role:drums', name: 'Drums', role: 'drums' as const, color: 'drums' as const,
      trackIds: drums.map(track => track.id),
    }] : [])];
  });
  readonly selectedTrack = computed<TrackRow>(() => this.tracks().find((track) => track.id === this.selectedTrackId()) ?? this.tracks()[0]);
  readonly selectedPart = computed(() => this.selectedTrack().role);
  readonly hasChannelCollision = computed(() => this.tracks().some((track) => track.id !== this.selectedTrack().id
    && !(track.role === 'drums' && this.selectedTrack().role === 'drums')
    && track.midiChannel === this.selectedTrack().midiChannel));
  readonly selectedSection = computed(() => {
    const target = this.settingsTarget();
    return target.kind === 'section' ? this.project().arrangement.find((section) => section.id === target.sectionId) ?? null : null;
  });
  readonly selectedSectionIndex = computed(() => this.project().arrangement.findIndex((section) => section.id === this.selectedSectionId()));
  readonly chords = computed(() => getDiatonicChords(this.project().key, this.project().scale));
  readonly sectionChordDegrees = computed(() => {
    const section = this.selectedSection();
    return section ? Array.from({ length: section.measures }, (_, measure) =>
      section.chordDegrees?.[measure] ?? this.project().progression[measure % this.project().progression.length]) : [];
  });
  readonly scoreNotes = computed<ScoreNote[]>(() => {
    const phrases = this.tracks().map(track => ({ track, notes: layOutTrackPhrase(this.project(), track) }));
    const drumPitches = phrases.filter(item => item.track.role === 'drums').flatMap(item => item.notes.map(note => note.midi));
    const drumMin = drumPitches.reduce((minimum, pitch) => Math.min(minimum, pitch), Infinity);
    const drumMax = drumPitches.reduce((maximum, pitch) => Math.max(maximum, pitch), -Infinity);
    return phrases.flatMap(({ track, notes }) => {
      if (notes.length === 0) return [];
      const minPitch = track.role === 'drums' ? drumMin : Math.min(...notes.map((note) => note.midi));
      const maxPitch = track.role === 'drums' ? drumMax : Math.max(...notes.map((note) => note.midi));
      const pitchSpan = Math.max(12, maxPitch - minPitch);
      return notes.map((note) => ({
        ...note,
        part: track.id,
        trackName: track.name,
        color: track.role,
        topPercent: (maxPitch - note.midi) / pitchSpan * 86 + 3,
        opacity: 0.72 + note.velocity / 127 * 0.28,
      }));
    });
  });
  tracksFor(role: ArrangedPart): TrackRow[] { return this.tracks().filter((track) => track.role === role); }

  isGroupCollapsed(role: ArrangedPart): boolean { return this.collapsedGroups().has(role); }

  toggleGroupCollapsed(role: ArrangedPart): void {
    const next = new Set(this.collapsedGroups());
    if (next.has(role)) next.delete(role);
    else next.add(role);
    this.collapsedGroups.set(next);
  }

  selectTrack(trackId: string): void {
    if (this.editing()) return;
    if (!this.project().tracks.some((track) => track.id === trackId)) return;
    this.workspaceUi.selectTrack(trackId);
  }

  selectRole(role: ArrangedPart): void {
    if (this.editing()) return;
    this.workspaceUi.selectRole(role);
  }

  selectSection(section: ArrangementSection, trackId: string, modifiers: { ctrlKey?: boolean; metaKey?: boolean; shiftKey?: boolean } = {}): void {
    this.workspaceUi.selectSection(section.id, modifiers);
  }

  selectOverviewSection(sectionId: string, trackId: string, modifiers: { ctrlKey?: boolean; metaKey?: boolean; shiftKey?: boolean } = {}): void {
    const section = this.project().arrangement.find((item) => item.id === sectionId);
    if (section) this.selectSection(section, trackId, modifiers);
  }

  toggleArrangementTrack(sectionId: string, trackId: string, present: boolean): void {
    const index = this.project().arrangement.findIndex((section) => section.id === sectionId);
    if (index >= 0) this.projects.setSectionTrackPresence(index, trackId, present);
  }

  selectCell(sectionId: string, role: ArrangedPart): void {
    if (!this.editing()) this.workspaceUi.selectCell(sectionId, role);
  }

  selectSectionTrack(sectionId: string, trackId: string): void {
    if (!this.editing()) this.workspaceUi.selectSectionTrack(sectionId, trackId);
  }

  openInspector(): void {
    const inspector = document.getElementById('part-inspector');
    inspector?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    inspector?.focus({ preventScroll: true });
  }

  toggleContextTrackPresence(track: CompositionTrack): void {
    const target = this.localPartTarget();
    if (!target) return;
    const section = this.project().arrangement.find((item) => item.id === target.sectionId);
    if (section) this.toggleArrangementTrack(section.id, track.id, !shouldGenerateTrackInSection(section, track));
  }

  contextTrackPresent(track: CompositionTrack): boolean {
    const target = this.localPartTarget();
    const section = target ? this.project().arrangement.find((item) => item.id === target.sectionId) : null;
    return section ? shouldGenerateTrackInSection(section, track) : true;
  }

  editPartSettings(scope: PartSettingsScope, change: { key: string; value: PartSettingValue }): void {
    // The emitted scope belongs to this editor instance; never redirect a delayed gesture into a new selection.
    const before = this.project();
    this.projects.updatePartSettings(scope, { [change.key]: change.value });
    this.editFailure.set(this.project() === before && !partValuesEqual(this.partValues()[change.key], change.value)
      ? { scope: JSON.stringify(scope), message: 'This value conflicts with another track or cell setting. Keep each velocity minimum at or below its maximum.' } : null);
  }

  resetPartSettings(scope: PartSettingsScope, field?: string): void {
    const before = this.project();
    const hadOverride = field ? this.overriddenKeys().includes(field) : this.overriddenKeys().length > 0;
    this.projects.resetPartSettings(scope, field);
    this.editFailure.set(this.project() === before && hadOverride ? { scope: JSON.stringify(scope),
      message: 'Reset would conflict with another velocity setting. Reset both bounds together or adjust the other bound first.' } : null);
  }

  randomizeArrangementPresence(): void {
    this.projects.randomizeArrangementPresence();
  }

  runPartWorkflow(action: 'freeze' | 'overrides' | 'effective' | 'reset-cell'): void {
    const scope = this.localPartTarget();
    if (!scope || !this.partGenerationAvailable() || !this.contextTracks().length) return;
    const destinations = this.partDestinations().map((section) => section.id);
    const result = action === 'freeze' ? this.projects.freezePartSettings(scope)
      : action === 'reset-cell' ? scope.kind === 'section-role' ? this.projects.resetCellPartSettings(scope) : 'invalid'
      : this.projects.applyPartSettingsToSections(scope, destinations, action);
    const message = result === 'invalid'
      ? 'No settings changed. A destination or inherited velocity range conflicts with this action.'
      : result === 'unchanged' ? 'Settings already match; no changes were needed.'
      : action === 'freeze' ? 'Effective settings frozen for the current track(s) in this section.'
      : action === 'reset-cell' ? 'Cell and individual track overrides reset to global inheritance.'
      : `${action === 'overrides' ? 'Overrides' : 'Effective values'} applied to ${destinations.length} selected section(s).`;
    this.editFailure.set(null);
    this.workflowFeedback.set(result === 'unchanged' ? null : {
      scope: JSON.stringify(scope), destinations: JSON.stringify(destinations), project: this.project(), message,
    });
  }

  dismissPartWorkflowFeedback(): void { this.workflowFeedback.set(null); }

  openTrackEditor(trackId: string): void {
    if (this.editing() || !this.project().tracks.some((track) => track.id === trackId)) return;
    this.selectTrack(trackId);
    this.editingNoteId.set(null);
    this.editing.set(true);
  }

  openNoteEditor(note: ScoreNote): void {
    if (this.editing() || !this.project().tracks.some((track) => track.id === note.part)) return;
    this.openTrackEditor(note.part);
    this.editingNoteId.set(note.id);
  }

  closeEditor(): void {
    this.editing.set(false);
    this.editingNoteId.set(null);
  }

  openMixer(): void { this.mixerOpen.set(true); }
  closeMixer(): void { this.mixerOpen.set(false); }

  changeChord(index: number, event: Event): void {
    this.projects.setChordDegree(index, Number(this.inputValue(event)));
  }

  addChord(): void { this.projects.addChord(); }
  removeChord(index: number): void { this.projects.removeChord(index); }

  generateProgression(): void {
    this.projects.setProgression(generateDiatonicProgression(BigInt(this.project().seed), this.project().progression.length));
  }

  updateNewSectionType(event: Event): void {
    const value = this.inputValue(event) as SectionType;
    if (SECTION_TYPES.includes(value)) this.newSectionType.set(value);
  }

  addSection(): void {
    if (this.project().arrangement.length >= 32 || this.totalMeasures() + 4 > 128) return;
    const index = this.selectedSectionIndex() < 0 ? this.project().arrangement.length - 1 : this.selectedSectionIndex();
    this.projects.addSection(this.newSectionType(), index);
    const added = this.project().arrangement[index + 1];
    if (added) {
      this.workspaceUi.setSectionSelection(added.id);
    }
  }

  duplicateSection(): void {
    const index = this.selectedSectionIndex();
    if (index < 0 || this.project().arrangement.length >= 32
        || this.totalMeasures() + this.project().arrangement[index].measures > 128) return;
    this.projects.duplicateSection(index);
    this.workspaceUi.setSectionSelection(this.project().arrangement[index + 1]?.id ?? null);
  }

  removeSection(): void {
    const index = this.selectedSectionIndex();
    if (index < 0) return;
    this.projects.removeSection(index);
    this.workspaceUi.setSectionSelection(this.project().arrangement[Math.min(index, this.project().arrangement.length - 1)]?.id ?? null);
  }

  moveSection(offset: -1 | 1): void {
    const index = this.selectedSectionIndex();
    if (index < 0) return;
    this.projects.moveSection(index, offset);
  }

  updateSectionType(event: Event): void {
    const type = this.inputValue(event) as SectionType;
    if (SECTION_TYPES.includes(type) && this.selectedSectionIndex() >= 0) this.projects.updateSection(this.selectedSectionIndex(), { type });
  }

  updateSectionMeasures(event: Event): void {
    const measures = Number(this.inputValue(event));
    const section = this.selectedSection();
    if (Number.isInteger(measures) && measures >= 1 && measures <= 32 && section
        && this.totalMeasures() - section.measures + measures <= 128 && this.selectedSectionIndex() >= 0) {
      this.projects.updateSection(this.selectedSectionIndex(), { measures });
    }
  }

  maxSectionMeasures(): number {
    const section = this.selectedSection();
    return section ? Math.min(32, 128 - (this.totalMeasures() - section.measures)) : 32;
  }

  updateSectionChord(measure: number, event: Event): void {
    this.projects.setSectionChordDegree(this.selectedSectionIndex(), measure, Number(this.inputValue(event)));
  }

  clearSectionProgression(): void { this.projects.clearSectionChordOverrides(this.selectedSectionIndex()); }

  trackPresent(section: ArrangementSection, track: TrackRow): boolean {
    return shouldGenerateTrackInSection(section, track);
  }

  toggleSectionTrack(section: ArrangementSection, track: TrackRow, event: Event): void {
    const index = this.project().arrangement.findIndex((item) => item.id === section.id);
    this.projects.setSectionTrackPresence(index, track.id, (event.target as HTMLInputElement).checked);
  }

  randomizeSectionPresence(section: ArrangementSection): void {
    this.projects.randomizeArrangementPresence(section.id);
  }

  updateKey(event: Event): void {
    const key = this.inputValue(event);
    if (KEYS.includes(key)) this.projects.updateSettings({ key });
  }

  updateScale(event: Event): void {
    const scale = this.inputValue(event);
    if (scale === 'major' || scale === 'natural-minor') this.projects.updateSettings({ scale });
  }

  updateTempo(event: Event): void {
    const tempo = Number(this.inputValue(event));
    if (Number.isInteger(tempo) && tempo >= 40 && tempo <= 240) this.projects.updateSettings({ tempoBpm: tempo });
  }

  updateTranspose(event: Event): void {
    const transpose = Number(this.inputValue(event));
    if (Number.isInteger(transpose) && transpose >= -24 && transpose <= 24) this.projects.updateSettings({ transposeSemitones: transpose });
  }

  updateSeed(event: Event): void {
    const input = event.target as HTMLInputElement;
    const seed = input.value.trim();
    if (/^-?\d+$/.test(seed)) {
      const value = BigInt(seed);
      if (value >= -(1n << 63n) && value <= (1n << 63n) - 1n) {
        this.projects.updateSettings({ seed: value.toString() });
        return;
      }
    }
    input.value = this.project().seed;
  }

  addTrack(role: ArrangedPart): void {
    const trackId = this.projects.addTrack(role);
    if (trackId) this.selectTrack(trackId);
  }

  duplicateTrack(trackId: string): void {
    const duplicateId = this.projects.duplicateTrack(trackId);
    if (duplicateId) this.selectTrack(duplicateId);
  }

  removeTrack(trackId: string): void {
    const current = this.tracks();
    const index = current.findIndex((track) => track.id === trackId);
    if (index < 0 || current.length <= 1) return;
    this.projects.removeTrack(trackId);
    if (this.selectedTrackId() === trackId) this.selectTrack(this.project().tracks[Math.max(0, index - 1)].id);
  }

  canRemoveTrack(trackId: string): boolean {
    const track = this.project().tracks.find((candidate) => candidate.id === trackId);
    return !!track && this.tracks().length > 1;
  }

  canMoveTrack(trackId: string, offset: -1 | 1): boolean {
    const index = this.project().tracks.findIndex((track) => track.id === trackId);
    return index + offset >= 0 && index + offset < this.project().tracks.length;
  }

  instrumentName(track: TrackRow): string {
    return track.role === 'drums' ? drumInstrumentName(track.generatorSettings.pitch) : this.instruments.find((item) => item.program === track.mix.program)?.name ?? `GM ${track.mix.program + 1}`;
  }

  drumPitch(track: CompositionTrack): number | undefined {
    return track.role === 'drums' ? track.generatorSettings.pitch : undefined;
  }

  updateDrumPitch(event: Event): void {
    if (this.selectedPart() === 'drums') this.projects.updateTrackGeneratorSettings(this.selectedTrackId(), { pitch: Number(this.inputValue(event)) });
  }

  reorderTrack(trackId: string, offset: -1 | 1): void {
    this.projects.reorderTrack(trackId, offset);
  }

  toggleVisibility(trackId: string): void {
    const next = new Set(this.hiddenTracks());
    if (next.has(trackId)) next.delete(trackId);
    else next.add(trackId);
    this.hiddenTracks.set(next);
  }

  isVisible(trackId: string): boolean {
    return !this.hiddenTracks().has(trackId);
  }

  isScoreTrackVisible(track: ScoreCanvasTrack): boolean {
    return (track.trackIds ?? [track.id]).some(id => this.isVisible(id));
  }

  isScoreTrackSelected(track: ScoreCanvasTrack): boolean {
    return (track.trackIds ?? [track.id]).includes(this.selectedTrackId());
  }

  toggleMix(trackId: string, setting: 'muted' | 'solo'): void {
    const track = this.project().tracks.find((candidate) => candidate.id === trackId);
    if (!track) return;
    this.projects.updateTrack(trackId, { mix: { [setting]: !track.mix[setting] } });
    this.playback.updateMix(this.project());
  }

  updateTrackMix(trackId: string, setting: 'panPercent' | 'volumePercent', value: number): void {
    this.projects.updateTrack(trackId, { mix: { [setting]: value } });
    this.playback.updateMix(this.project());
  }

  previewTrackMix(trackId: string, setting: 'panPercent' | 'volumePercent', value: number): void {
    const project = this.project();
    this.playback.updateMix({ ...project, tracks: project.tracks.map((track) => track.id === trackId
      ? { ...track, mix: { ...track.mix, [setting]: value } } : track) });
  }

  sectionLabel(type: string): string { return type.replaceAll('_', ' '); }
  roleName(role: ArrangedPart): string { return ROLE_NAMES[role]; }

  updateTrackName(event: Event): void {
    this.projects.updateTrack(this.selectedTrackId(), { name: this.inputValue(event) });
  }
  restoreTrackPhrase(): void {
    if (!this.generationAvailable[this.selectedTrack().role]) return;
    this.projects.clearTrackPhrase(this.selectedTrackId());
  }
  updateChannel(event: Event): void {
    const channel = Number(this.inputValue(event));
    if (Number.isInteger(channel)) this.projects.updateTrack(this.selectedTrackId(), { midiChannel: channel });
  }
  updateInstrument(event: Event): void {
    if (this.selectedPart() === 'drums') return;
    const program = Number(this.inputValue(event));
    if (this.instruments.some((instrument) => instrument.program === program)) {
      this.projects.updateTrack(this.selectedTrackId(), { mix: { program } });
      this.playback.updateMix(this.project());
    }
  }
  updateVolume(event: Event): void {
    const volumePercent = Number(this.inputValue(event));
    if (Number.isInteger(volumePercent) && volumePercent >= 0 && volumePercent <= 100) {
      this.projects.updateTrack(this.selectedTrackId(), { mix: { volumePercent } });
      this.playback.updateMix(this.project());
    }
  }
  updatePan(event: Event): void {
    const panPercent = Number(this.inputValue(event));
    if (Number.isInteger(panPercent) && panPercent >= -100 && panPercent <= 100) {
      this.projects.updateTrack(this.selectedTrackId(), { mix: { panPercent } });
      this.playback.updateMix(this.project());
    }
  }
  panName(value: number): string {
    if (value === 0) return 'Center';
    return `${Math.abs(value)}% ${value < 0 ? 'Left' : 'Right'}`;
  }

  private inputValue(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }
}
