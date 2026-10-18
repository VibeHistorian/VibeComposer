import { UiPreferencesService } from '../core/ui/ui-preferences.service';
import { isScaleMode } from '../core/music/scales';
import { NgTemplateOutlet } from '@angular/common';
import { SectionContextEditorComponent } from './section-context-editor.component';
import { sectionDurationBeats } from '../core/music/section-context';
import { MAX_VOLUME_PERCENT, validVolumePercent } from '../core/audio/mix-values';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import type {
  ArrangedPart, ArrangementSection, SectionType, CompositionTrack, CompositionProject, PartSettingsScope,
  TrackGenerationPolicyPatch, InstrumentGenerationPolicy,
} from '../core/project/project.model';
import { ARRANGED_PARTS, PART_GENERATION_AVAILABLE, SECTION_TYPES, tracksInRoleOrder, DEFAULT_MIX, DEFAULT_TRACKS } from '../core/project/project.model';
import { AudioPlaybackService } from '../core/audio/audio-playback.service';
import { shouldGenerateTrackInSection } from '../core/music/arrangement-generator';
import { DRUM_INSTRUMENTS, drumInstrumentName } from '../core/music/drum-instruments';
import { GM_INSTRUMENTS } from '../core/music/gm-instruments';
import { layOutTrackPhrase, phraseForTrack, trackPhraseSource } from '../core/music/phrase';
import { getDiatonicChords } from '../core/music/harmony';
import { sectionGenerationHarmony } from '../core/music/project-harmony';


import { ProjectService } from '../core/project/project.service';
import { WorkspaceUiService } from './workspace-ui.service';
import type { SettingsSelection } from './workspace-ui.service';
import { ArrangementOverviewComponent } from './arrangement-overview.component';
import { ScoreCanvasComponent } from './score-canvas.component';
import type { ScoreCanvasNote, ScoreCanvasTrack, ScoreSectionFocus, ScoreViewport } from './score-canvas.component';
import { EditWorkspaceComponent } from '../features/edit/edit-workspace.component';
import { PartSettingsEditorComponent } from './part-settings-editor.component';
import { MelodyGlobalSettingsComponent } from './melody-global-settings.component';
import { HarmonySettingsComponent } from './harmony-settings.component';
import { PartScopeActionsComponent } from './part-scope-actions.component';
import { CompactKnobComponent } from './compact-knob.component';
import { CompactToggleComponent } from './compact-toggle.component';
import { MixButtonsComponent } from './mix-buttons.component';
import { WheelSelectDirective } from './wheel-select.directive';
import { TrackGenerationPolicyComponent } from './track-generation-policy.component';
import { DEFAULT_TRACK_POLICIES } from '../core/music/track-generation';
import { DEFAULT_INSTRUMENT_POLICIES, INSTRUMENT_POOLS } from '../core/music/instrument-generation';
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
  melody: 'Melody', bass: 'Bass', chords: 'Chords', arpeggio: 'Arps', drums: 'Drums',
};


@Component({
  selector: 'vc-workspace-canvas',
  imports: [NgTemplateOutlet, SectionContextEditorComponent, ArrangementOverviewComponent, PartSettingsEditorComponent, MelodyGlobalSettingsComponent, HarmonySettingsComponent, PartScopeActionsComponent, EditWorkspaceComponent, ScoreCanvasComponent, CompactKnobComponent, CompactToggleComponent, MixButtonsComponent, WheelSelectDirective, TrackGenerationPolicyComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(window:resize)': 'updatePartSettingsLayout()' },
  templateUrl: './workspace-canvas.component.html',
  styleUrl: './workspace-canvas.component.css',
})
export class WorkspaceCanvasComponent {
  readonly partSettingsInInspector = signal(this.isPartSettingsHeightLimited());
  updatePartSettingsLayout(): void {
    this.partSettingsInInspector.set(this.isPartSettingsHeightLimited());
  }
  private isPartSettingsHeightLimited(): boolean {
    return typeof window !== 'undefined' && window.matchMedia(
      '(max-height: 865px), (max-width: 1400px) and (max-height: 1005px)',
    ).matches;
  }
  readonly toggleBoolean = (value: boolean) => !value;
  private emptyMiddlePress: { pointerId: number; eligible: boolean; released: boolean } | null = null;
  private isEmptyMiddleTarget(event: MouseEvent): boolean {
    const target = event.target;
    return target instanceof Element && !target.closest('button, input, select, textarea, a, canvas, vc-score-canvas, vc-compact-knob, vc-compact-toggle, vc-mix-buttons');
  }
  beginEmptyMiddleClick(event: PointerEvent): void {
    if (event.button !== 1) return;
    this.emptyMiddlePress = { pointerId: event.pointerId, eligible: this.editing() && this.isEmptyMiddleTarget(event), released: false };
  }
  endEmptyMiddleClick(event: PointerEvent): void {
    if (this.emptyMiddlePress?.pointerId !== event.pointerId) return;
    this.emptyMiddlePress.eligible &&= this.isEmptyMiddleTarget(event);
    this.emptyMiddlePress.released = true;
  }
  cancelEmptyMiddleClick(): void { this.emptyMiddlePress = null; }
  readonly defaultMix = DEFAULT_MIX;
  defaultChannel(track: CompositionTrack): number { return DEFAULT_TRACKS.find(item => item.role === track.role)!.midiChannel; }
  readonly maximumVolume = MAX_VOLUME_PERCENT;
  readonly preferences = inject(UiPreferencesService);
  readonly projects = inject(ProjectService);
  readonly playback = inject(AudioPlaybackService);
  readonly workspaceUi = inject(WorkspaceUiService);
  readonly project = this.projects.project;
  readonly instruments = GM_INSTRUMENTS;
  readonly trackInstrumentOptions = computed(() => {
    const limited = this.preferences.limitInstrumentsToPool();
    return Object.fromEntries(this.project().tracks.filter(track => track.role !== 'drums').map(track => {
      const pool = this.instrumentPolicyFor(track.role).pool;
      const programs = pool === 'AUTO' ? [...INSTRUMENT_POOLS.CHORD, ...INSTRUMENT_POOLS.PLUCK] : INSTRUMENT_POOLS[pool];
      return [track.id, limited ? this.instruments.filter(instrument =>
        instrument.program === track.mix.program || programs.includes(instrument.program)) : this.instruments];
    }));
  });
  readonly drumInstruments = DRUM_INSTRUMENTS;
  readonly sectionTypes = SECTION_TYPES;
  readonly chords = computed(() => getDiatonicChords(this.project().key, this.project().scale));
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
  private readonly rerollFeedback = signal<{ project: CompositionProject; role: ArrangedPart; message: string } | null>(null);
  readonly rerollMessage = computed(() => this.rerollFeedback()?.project === this.project() ? this.rerollFeedback()?.message : null);

  policyFor(role: ArrangedPart) { return { ...DEFAULT_TRACK_POLICIES[role], ...this.project().trackGenerationPolicies?.[role] }; }
  instrumentPolicyFor(role: ArrangedPart) { return this.project().instrumentGenerationPolicies?.[role] ?? DEFAULT_INSTRUMENT_POLICIES[role]; }
  updateInstrumentPolicy(role: ArrangedPart, patch: Partial<InstrumentGenerationPolicy>): void {
    if (this.editing()) return;
    const result = this.projects.updateInstrumentPolicy(role, patch);
    this.rerollFeedback.set(result === 'invalid' ? { project: this.project(), role, message: 'Invalid instrument rule.' } : null);
  }
  canRerollRole(role: ArrangedPart): boolean { return this.project().tracks.some(track => track.role === role && !track.rerollLocked); }
  rerollMessageFor(role: ArrangedPart): string | null | undefined { return this.rerollFeedback()?.role === role ? this.rerollMessage() : null; }

  changeRoleCount(role: ArrangedPart, event: Event): void {
    if (this.editing()) return;
    const element = event.target as HTMLInputElement;
    const result = this.projects.setRoleTrackCount(role, element.value.trim() === '' ? NaN : Number(element.value));
    element.value = String(this.tracksFor(role).length);
    this.rerollFeedback.set(result === 'invalid' ? { project: this.project(), role,
      message: 'Cannot change the track count. Check capacity and local settings.' } : null);
  }

  updateRolePolicy(role: ArrangedPart, patch: TrackGenerationPolicyPatch): void {
    if (this.editing()) return;
    const result = this.projects.updateTrackGenerationPolicy(role, patch);
    this.rerollFeedback.set(result === 'invalid' ? { project: this.project(), role,
      message: 'Invalid reroll limits. Minimum values must not exceed maximum values.' } : null);
  }

  rerollRole(role: ArrangedPart, trackId?: string): void {
    if (this.editing()) return;
    const result = this.projects.rerollRoleTracks(role, trackId);
    this.rerollFeedback.set(result === 'changed' ? null : { project: this.project(), role, message: result === 'invalid'
      ? 'Reroll conflicts with local settings. Adjust the rules or local overrides.'
      : 'No track settings changed.' });
  }

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
  readonly contextLocationLabel = computed(() => {
    const target = this.settingsTarget();
    const section = 'sectionId' in target ? this.project().arrangement.find((item) => item.id === target.sectionId) : null;
    return section ? `${this.project().arrangement.indexOf(section) + 1} · ${this.sectionLabel(section.type)}` : 'Global';
  });
  readonly contextPartLabel = computed(() => {
    const target = this.settingsTarget();
    if ('role' in target) return this.roleName(target.role);
    if ('trackId' in target) return this.contextTracks()[0]?.name ?? 'Track';
    return '';
  });
  readonly contextLabel = computed(() => [this.contextLocationLabel(), this.contextPartLabel()].filter(Boolean).join(' · '));
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
  readonly partPanelLabel = computed(() => {
    const scope = this.partScope();
    if (!scope || !this.contextTracks().length) return 'Quick access settings';
    const location = scope.kind.startsWith('section-') ? 'Section' : 'Global';
    const part = 'role' in scope ? this.roleName(scope.role) : 'part';
    return `${location} ${part} settings`;
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
  readonly manualTrackNames = computed(() => {
    const section = this.contextSection();
    return this.contextTracks().filter(track => section?.trackPhrases?.[track.id] !== undefined
      || track.editedPhrase !== undefined && !section?.generatedTrackIds?.includes(track.id)).map(track => track.name).join(', ');
  });
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
  readonly editor = viewChild(EditWorkspaceComponent);
  readonly editSectionId = signal<string | null>(null);
  readonly editStartBeat = signal(0);
  readonly editPhraseOffset = signal(0);
  readonly editPhraseLength = signal(16);
  readonly displayedRoles = signal(new Set<ArrangedPart>(ARRANGED_PARTS));
  private readonly scoreMixSyncEffect = effect(() => {
    if (this.preferences.syncScoreMix()) untracked(() => this.synchronizeScoreMix());
  });
  toggleScoreRole(role: ArrangedPart): void {
    this.displayedRoles.update(roles => { const next = new Set(roles); if (next.has(role)) next.delete(role); else next.add(role); return next; });
    this.synchronizeScoreMix();
  }
  private synchronizeScoreMix(): void {
    if (this.preferences.syncScoreMix()) this.projects.syncScoreMix(this.project().tracks.filter(track => this.displayedRoles().has(track.role)).map(track => track.id));
  }
  readonly editing = signal(false);
  private selectionBeforeEditing: SettingsSelection | null = null;
  readonly hiddenTracks = signal(new Set<string>());
  readonly editingHiddenTracks = new Set<string>();
  readonly collapsedGroups = signal(new Set<ArrangedPart>(ARRANGED_PARTS));
  private readonly groupExpansionEffect = effect(() => {
    this.settingsTarget();
    const selected = this.selectedRole();
    this.collapsedGroups.set(new Set(ARRANGED_PARTS.filter(role => role !== selected)));
  });
  readonly scoreSectionFocus = computed<ScoreSectionFocus>(() => {
    if (this.editing()) return { startBeat: this.editStartBeat(), endBeat: this.editStartBeat() + this.editPhraseLength() };
    this.selectedSectionIds();
    // A changed selection must refit even when its bounds equal the full arrangement.
    return { ...(this.workspaceUi.sectionRange() ?? { startBeat: 0, endBeat: this.totalBeats() }) };
  });
  readonly scoreViewport = signal<ScoreViewport>({ scale: 1, offsetPercent: 0, width: 0, laneHeight: 40, verticalOffset: 0, scoreHeight: 0 });

  readonly totalMeasures = computed(() => this.project().arrangement.reduce((sum, section) => sum + section.measures, 0));
  readonly totalBeats = computed(() => this.project().arrangement.reduce((sum, section) => sum + sectionDurationBeats(this.project(), section), 0));
  readonly loopRange = computed(() => this.playback.loopEnabled()
    ? this.workspaceUi.sectionRange() ?? { startBeat: 0, endBeat: this.totalBeats() } : null);

  seek(beat: number): void {
    this.playback.seek(this.project(), beat);
  }
  readonly playheadPercent = computed(() => this.totalBeats() > 0
    ? Math.min(100, Math.max(0, this.playback.beat() / this.totalBeats() * 100)) : 0);
  readonly timelineSections = computed<TimelineSection[]>(() => {
    let beat = 0;
    let measure = 1;
    return this.project().arrangement.map((section) => {
      const item = { section, startBeat: beat, startMeasure: measure, widthPercent: sectionDurationBeats(this.project(), section) / this.totalBeats() * 100 };
      beat += sectionDurationBeats(this.project(), section);
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
  readonly scoreTracks = computed(() => this.arrangedTracks().filter(track => this.editing()
    ? this.selectedPart() === 'drums' ? track.role === 'drums' : track.id === this.selectedTrackId() : this.displayedRoles().has(track.role))
    .map(track => ({ ...track, drumPitch: track.role === 'drums' ? track.generatorSettings.pitch : undefined })));
  readonly canvasNotes = computed<ScoreNote[]>(() => {
    const editor = this.editor();
    if (!this.editing() || !editor) return this.scoreNotes().filter(note => this.displayedRoles().has(note.color));
    const section = this.project().arrangement.find(section => section.id === this.editSectionId());
    return editor.notes().map(note => {
      const track = this.project().tracks.find(track => track.id === note.trackId)!;
      const changed = editor.changedTracks().some(changed => changed.id === track.id);
      return { ...note, startBeat: note.startBeat + this.editStartBeat(), part: note.trackId,
        trackName: track.name, trackOrder: this.tracksFor(track.role).findIndex(candidate => candidate.id === track.id) + 1,
        phraseSource: changed ? section ? 'section-edited' : 'global-edited' : trackPhraseSource(track, section),
        color: track.role, topPercent: 0, opacity: 0.5 + note.velocity / 127 * 0.5 };
    });
  });
  applyOnEmptyMiddleClick(event: MouseEvent): void {
    if (event.button !== 1 || !this.editing()) return;
    const press = this.emptyMiddlePress;
    this.emptyMiddlePress = null;
    if (!press?.eligible || !press.released || !this.isEmptyMiddleTarget(event)) return;
    event.preventDefault();
    this.editor()?.smartExit();
  }
  canvasNoteChanged(note: ScoreNote | import('../core/project/project.model').PhraseNote): void {
    this.editor()?.replaceNote({ ...note, startBeat: note.startBeat - this.editStartBeat() });
  }
  canvasNoteAdded(position: { trackId?: string; midi: number; startBeat: number; durationBeats?: number; velocity?: number }): void {
    this.editor()?.addNoteAt({ ...position, startBeat: position.startBeat - this.editStartBeat() });
    const note = this.editor()?.selectedNote();
    if (note) void this.playback.audition(this.selectedTrack(), [note]);
  }
  canvasNotesChanged(change: { notes: readonly import('../core/project/project.model').PhraseNote[]; duplicate: boolean }): void {
    this.editor()?.replaceNotes(change.notes.map(note => ({ ...note, startBeat: note.startBeat - this.editStartBeat() })), change.duplicate);
  }
  auditionCanvasNotes(notes: readonly import('../core/project/project.model').PhraseNote[]): void {
    const editor = this.editor();
    if (!editor) return;
    for (const track of editor.editedTracks()) {
      const owned = notes.filter(note => editor.notes().some(draft => draft.id === note.id && draft.trackId === track.id));
      if (owned.length) void this.playback.audition(track, owned);
    }
  }
  canvasSplit(position: { id: string; beat: number }): void {
    this.editor()?.splitNotes({ ...position, beat: position.beat - this.editStartBeat() });
  }
  canvasDelete(id: string): void {
    const editor = this.editor();
    if (!editor) return;
    if (!editor.selectedNoteIds().includes(id)) editor.selectNote(id);
    editor.deleteSelected();
  }
  canvasNoteSelected(note: ScoreNote): void {
    if (this.editing()) this.editor()?.selectNote(note.id); else this.openNoteEditor(note);
  }
  selectNoteTrack(note: ScoreNote): void {
    if (this.editing()) return;
    const track = this.project().tracks.find(track => track.id === note.part);
    if (!track) return;
    const section = this.timelineSections().find((item, index, all) => note.startBeat >= item.startBeat
      && note.startBeat < (all[index + 1]?.startBeat ?? this.totalBeats()))?.section;
    if (section && (section.trackPhrases?.[track.id] !== undefined
        || track.editedPhrase !== undefined && !section.generatedTrackIds?.includes(track.id))) {
      this.openNoteEditor(note);
    } else if (section && (Object.keys(section.trackPartOverrides?.[track.id] ?? {}).length > 0
        || Object.keys(section.rolePartOverrides?.[track.role] ?? {}).length > 0)) {
      this.selectSectionTrack(section.id, track.id);
    } else {
      this.selectTrack(track.id);
    }
  }
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
  readonly sectionChordDegrees = computed(() => {
    const section = this.selectedSection();
    return section ? Array.from({ length: section.measures }, (_, measure) =>
      section.chordDegrees?.[measure] ?? this.project().progression[measure % this.project().progression.length]) : [];
  });
  readonly scoreNotes = computed<ScoreNote[]>(() => {
    const phrases = this.tracks().map(track => ({ track, notes: layOutTrackPhrase(this.project(), track, true) }));
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
        trackOrder: this.tracksFor(track.role).findIndex(candidate => candidate.id === track.id) + 1,
        color: track.role,
        topPercent: (maxPitch - note.midi) / pitchSpan * 86 + 3,
        opacity: 0.4 + note.velocity / 127 * 0.6,
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

  selectScoreTrack(track: ScoreCanvasTrack): void {
    const ids = track.trackIds ?? [track.id];
    this.selectTrack(ids.includes(this.selectedTrackId()) ? this.selectedTrackId() : ids[0]);
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

  toggleQuickSettingsEdit(): void {
    if (this.preferences.editingQuickSettings()) this.preferences.saveQuickSettings();
    else {
      this.partPanelExpanded.set(true);
      this.preferences.beginQuickSettings();
    }
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

  melodyRhythmHits(scope: PartSettingsScope): number {
    const section = 'sectionId' in scope ? this.project().arrangement.find(item => item.id === scope.sectionId) : undefined;
    return (section ? sectionGenerationHarmony(this.project(),section,'melody').degrees : this.project().progression).length * 16;
  }

  resetPartSettings(scope: PartSettingsScope, field?: string | readonly string[]): void {
    const before = this.project();
    const hadOverride = field ? (typeof field === 'string' ? [field] : field).some(key => this.overriddenKeys().includes(key)) : this.overriddenKeys().length > 0;
    this.projects.resetPartSettings(scope, field);
    this.editFailure.set(this.project() === before && hadOverride ? { scope: JSON.stringify(scope),
      message: 'Reset would conflict with another velocity setting. Reset both bounds together or adjust the other bound first.' } : null);
  }

  randomizeArrangementPresence(): void {
    this.projects.randomizeArrangementPresence();
  }

  readonly canCopyPartGlobals = computed(() => {
    const scope = this.localPartTarget();
    return !!scope && this.projects.partSettingsGlobalsWouldChange(scope);
  });

  runPartWorkflow(action: 'freeze' | 'overrides' | 'effective' | 'reset-cell' | 'globals'): void {
    const scope = this.localPartTarget();
    if (!scope || !this.partGenerationAvailable() || !this.contextTracks().length) return;
    const destinations = this.partDestinations().map((section) => section.id);
    const result = action === 'freeze' ? this.projects.freezePartSettings(scope)
      : action === 'globals' ? this.projects.copyPartSettingsToGlobals(scope)
      : action === 'reset-cell' ? scope.kind === 'section-role' ? this.projects.resetCellPartSettings(scope) : 'invalid'
      : this.projects.applyPartSettingsToSections(scope, destinations, action);
    const message = result === 'invalid'
      ? 'No settings changed. A destination or inherited velocity range conflicts with this action.'
      : result === 'unchanged' ? 'Settings already match; no changes were needed.'
      : action === 'freeze' ? 'Effective settings frozen for the current track(s) in this section.'
      : action === 'reset-cell' ? 'Cell and individual track overrides reset to global inheritance.'
      : action === 'globals' ? 'Effective values copied to each current track’s global settings. Section overrides retained.'
      : `${action === 'overrides' ? 'Overrides' : 'Effective values'} applied to ${destinations.length} selected section(s).`;
    this.editFailure.set(null);
    this.workflowFeedback.set(result === 'unchanged' ? null : {
      scope: JSON.stringify(scope), destinations: JSON.stringify(destinations), project: this.project(), message,
    });
  }

  dismissPartWorkflowFeedback(): void { this.workflowFeedback.set(null); }

  openTrackEditor(trackId: string): void {
    if (this.editing() || !this.project().tracks.some((track) => track.id === trackId)) return;
    this.selectionBeforeEditing = this.workspaceUi.captureSettingsSelection();
    const target = this.settingsTarget();
    const sectionId = 'sectionId' in target ? target.sectionId : this.selectedSectionId() ?? this.project().arrangement[0]?.id ?? null;
    const item = this.timelineSections().find(item => item.section.id === sectionId);
    this.editSectionId.set(sectionId);
    this.editPhraseOffset.set(0);
    this.editStartBeat.set(item?.startBeat ?? 0);
    this.editPhraseLength.set(Math.min(this.project().progression.length * 4, item ? sectionDurationBeats(this.project(), item.section) : this.totalBeats()));
    this.workspaceUi.selectedTrackId.set(trackId);
    this.editingNoteId.set(null);
    this.editing.set(true);
  }

  openNoteEditor(note: ScoreNote): void {
    if (this.editing() || !this.project().tracks.some((track) => track.id === note.part)) return;
    const item = this.timelineSections().find((item, index, all) => note.startBeat >= item.startBeat
      && note.startBeat < (all[index + 1]?.startBeat ?? this.totalBeats()));
    this.openTrackEditor(note.part);
    if (item) {
      const length = this.project().progression.length * 4;
      const offset = Math.floor((note.startBeat - item.startBeat) / length) * length;
      this.editSectionId.set(item.section.id);
      this.editPhraseOffset.set(offset);
      this.editStartBeat.set(item.startBeat + offset);
      this.editPhraseLength.set(Math.min(length, sectionDurationBeats(this.project(), item.section) - offset));
    }
    this.editingNoteId.set(note.id);
  }

  closeEditor(): void {
    if (this.selectionBeforeEditing) {
      this.workspaceUi.restoreSettingsSelection(this.selectionBeforeEditing);
      this.selectionBeforeEditing = null;
    }
    this.editing.set(false);
    this.editingNoteId.set(null);
  }


  addSection(type: SectionType): void {
    if (!SECTION_TYPES.includes(type) || this.project().arrangement.length >= 32 || this.totalMeasures() + 4 > 128) return;
    const index = this.selectedSectionIndex() < 0 ? this.project().arrangement.length - 1 : this.selectedSectionIndex();
    this.projects.addSection(type, index);
    const added = this.project().arrangement[index + 1];
    if (added) {
      this.workspaceUi.setSectionSelection(added.id);
    }
  }

  changeOverviewTracks(event: { sectionId: string; role?: ArrangedPart; trackId?: string; action: 'reroll' | 'reset' }): void {
    if (this.editing()) return;
    this.projects.changeOverviewTracks(event.trackId ? { kind: 'section-track', sectionId: event.sectionId, trackId: event.trackId }
      : event.role ? { kind: 'section-role', sectionId: event.sectionId, role: event.role }
      : { kind: 'section', sectionId: event.sectionId }, event.action);
  }

  duplicateSection(sectionId?: string): void {
    if (this.editing()) return;
    const index = sectionId === undefined ? this.selectedSectionIndex() : this.project().arrangement.findIndex(section => section.id === sectionId);
    if (index < 0 || this.project().arrangement.length >= 32
        || this.totalMeasures() + this.project().arrangement[index].measures > 128) return;
    this.projects.duplicateSection(index);
    this.workspaceUi.setSectionSelection(this.project().arrangement[index + 1]?.id ?? null);
  }

  removeSection(sectionId?: string): void {
    if (this.editing()) return;
    const index = sectionId === undefined ? this.selectedSectionIndex() : this.project().arrangement.findIndex(section => section.id === sectionId);
    if (index < 0 || this.project().arrangement.length <= 1) return;
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

  updateScale(event: Event): void {
    const scale = this.inputValue(event);
    if (isScaleMode(scale)) this.projects.updateSettings({ scale });
  }

  updateTempo(event: Event): void {
    const tempo = Number(this.inputValue(event));
    if (Number.isInteger(tempo) && tempo >= 30 && tempo <= 240) this.projects.updateSettings({ tempoBpm: tempo });
  }

  updateTranspose(event: Event): void {
    const transpose = Number(this.inputValue(event));
    if (Number.isInteger(transpose) && transpose >= -24 && transpose <= 24) this.projects.updateSettings({ transposeSemitones: transpose });
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

  updateDrumPitch(track: CompositionTrack, event: Event): void {
    if (!this.editing() && track.role === 'drums') this.projects.updateTrackGeneratorSettings(track.id, { pitch: Number(this.inputValue(event)) });
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

  roleTrackIds(role: ArrangedPart): string[] {
    return this.tracksFor(role).map(track => track.id);
  }

  soloSelection(event: MouseEvent, trackIds: readonly string[]): void {
    if (event.button !== 1 || this.editing()) return;
    event.preventDefault();
    event.stopPropagation();
    this.projects.toggleTrackMix(trackIds, 'solo', true);
    this.playback.updateMix(this.project());
  }

  preventSelectionScroll(event: MouseEvent): void {
    if (event.button === 1) event.preventDefault();
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
  updateChannel(track: CompositionTrack, event: Event): void {
    if (this.editing() || track.role === 'drums') return;
    const channel = Number(this.inputValue(event));
    if (Number.isInteger(channel)) this.projects.updateTrack(track.id, { midiChannel: channel });
  }
  updateInstrument(track: CompositionTrack, event: Event): void {
    if (this.editing() || track.role === 'drums') return;
    const program = Number(this.inputValue(event));
    if (this.instruments.some((instrument) => instrument.program === program)) {
      this.projects.updateTrack(track.id, { mix: { program } });
      this.playback.updateMix(this.project());
    }
  }
  updateVolume(event: Event): void {
    const volumePercent = Number(this.inputValue(event));
    if (validVolumePercent(volumePercent)) {
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
