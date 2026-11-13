import { transferManualParameters } from '../music/manual-phrase-processing';
import { followHarmonyMode } from '../music/harmony-mode';
import { MAX_DRUM_SOUNDS } from '../music/drum-instruments';
import { projectDrumPitches, reconcileDrumKit } from './project-drum-kit';
import { copyArrangementPhrase, type ArrangementPhraseCopy } from './arrangement-phrase-copy';
import { samePhraseInputs } from '../music/phrase-inputs';
import type { TrackDuplicationOptions } from './track-duplication';
import * as arrangementCommands from './project-arrangement-commands';
import { fitVariationCapacity } from './arrangement-variation-capacity';
import { DEFAULT_VARIATION_POLICY, pruneVariations, validVariationPolicy } from '../music/arrangement-variations';
import type { VariationPolicy } from '../music/arrangement-variation.model';
import type { VariationDraft, VariationDraftOptions } from './arrangement-variation-draft';
import { loadTrackPreset } from './track-presets';
import { decodeMelody, decodeBass, decodeChords, decodeArpeggio, decodeDrums } from './project-part-decoder';
import { isPhrase, isPhraseRangeEdits } from './project-track-decoder';
import { appendPhraseRangeEdit, hasManualPhrase, type PhraseRangeEdit } from '../music/phrase-range-edit';
import { decodeProject, isProject } from './project-codec';
import { formatProjectJson } from './project-json';
import { encodeProjectStorage } from './project-storage';
import { ProjectPersistence } from './project-persistence';
import { copyDefaultProject, DEFAULT_DRUM_SETTINGS, DEFAULT_PROJECT, DEFAULT_TRACKS } from './project-defaults';
import { projectLoadIssues } from './project-load-diagnostics';
import { UiPreferencesService } from '../ui/ui-preferences.service';
import { isScaleMode } from '../music/scales';
import { followManualPhraseMode } from '../music/manual-phrase-mode';
import { normalizeVolumePercent, validVolumePercent } from '../audio/mix-values';
import { computed, inject, Injectable, signal } from '@angular/core';
import { historyContent, preserveAudition, projectDiff } from './project-history';
import { getDiatonicChords, progressionChords, validProgressionChords, type ProgressionChord } from '../music/harmony';
import { generateTrackPresence } from '../music/arrangement-generator';
import type { ArpeggioSettings, BassSettings, ChordSettings, DrumSettings, PartSettingsPatch, MelodySettings, MelodyGlobalSettings } from '../music/part-settings.model';
import { type ArrangedPart, ARRANGED_PARTS } from '../music/part-role';
import type { ArrangementSection, SectionType, SectionMusicalContext } from './arrangement.model';
import type { CompositionTrack, MixChannelSettings } from './track.model';
import type { PhraseNote } from '../music/phrase-note';
import type { PartSettingsScope, LocalPartSettingsScope, PartWorkflowResult } from './part-settings-scope';
import type { InstrumentGenerationPolicy, ArpeggioGenerationPolicy, TrackGenerationPolicyPatch } from '../music/generation-policy.model';
import type { CompositionProject } from './project.model';
import { SectionTypeSettingsService } from './section-type-settings.service';
import { copyPartSettings, decodePartPatch, resolvePartTrack, settingsValues, partPatchesEqual, partValuesEqual, type PartSettingValue, PART_CONTROLS, MANUAL_PARAMETER_TARGETS } from '../music/part-settings';
import { JavaRandom } from '../music/java-random';
import { DRUM_HIT_MULTIPLIERS, multiplyDrumHits } from '../music/drum-hit-multiplier';
import { chooseMelodyTargets, forceMelodyOutline } from '../music/melody-targets';
import { decodeMelodyGlobal } from '../music/melody-global-settings';
import { decodeHarmonySettings, DEFAULT_HARMONY_SETTINGS, type HarmonySettings } from '../music/harmony-voicing';
import { generateHarmony } from '../music/contextual-harmony';
import { contextualRange } from '../music/contextual-randomizer';
import { DEFAULT_MELODY_GLOBAL } from '../music/melody-global-settings';
import { DEFAULT_TRACK_POLICIES, chooseMelodyPattern, decodeTrackGenerationPolicy, rerollRoleSettings } from '../music/track-generation';
import { DEFAULT_INSTRUMENT_POLICIES, decodeInstrumentPolicy, rerollInstruments } from '../music/instrument-generation';
import { sectionDegrees, sectionProject, sectionValuesEqual, validSectionHarmony } from '../music/section-context';
import { baseGenerationHarmony } from '../music/project-harmony';
import { layOutTrackPhrase, phraseForTrack } from '../music/phrase';

const STORAGE_KEY = 'vibecomposer.project.v12';
const LEGACY_STORAGE_KEY = 'vibecomposer.project.v11';
const TABS_STORAGE_KEY = 'vibecomposer.project-tabs.v1';
interface ProjectTab {
  readonly id: string;
  readonly project: CompositionProject;
  readonly past: readonly CompositionProject[];
  readonly future: readonly CompositionProject[];
  readonly automaticSeed: boolean;
}
const MAX_HISTORY = 100;
const COMPACT_HISTORY_WINDOW_MS = 1000;

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly sectionTypeSettings = inject(SectionTypeSettingsService);
  private readonly state = signal<CompositionProject>(this.loadProject());
  private readonly past: CompositionProject[] = [];
  private readonly future: CompositionProject[] = [];
  private readonly undoAvailable = signal(false);
  private readonly redoAvailable = signal(false);
  private readonly uiPreferences = inject(UiPreferencesService);
  private lastControlEdit: { key: string; time: number } | null = null;
  private readonly historyRevision = signal(0);

  readonly project = this.state.asReadonly();
  private readonly generationFailure = signal<string | null>(null);
  readonly generationError = this.generationFailure.asReadonly();
  dismissGenerationError(): void { this.generationFailure.set(null); }
  private readonly automaticSeed = signal(BigInt(this.state().seed) === 0n);
  readonly seedInput = computed(() => this.automaticSeed() ? '0' : this.project().seed);
  readonly canUndo = this.undoAvailable.asReadonly();
  readonly canRedo = this.redoAvailable.asReadonly();
  readonly compactHistory = this.uiPreferences.compactHistory;
  readonly historyPosition = computed(() => { this.historyRevision(); return this.past.length; });
  readonly historyEntries = computed(() => {
    this.historyRevision();
    const states = [...this.past, this.state(), ...[...this.future].reverse()];
    return states.map((state, index) => ({ index,
      changes: index === 0 ? ['Starting point of retained history'] : projectDiff(states[index - 1], state),
    }));
  });

  private readonly tabs = signal<readonly ProjectTab[]>([]);
  readonly activeProjectTabId = signal('');
  readonly projectActivation = signal(0);
  readonly openProjects = computed(() => this.tabs().map(tab => ({ id: tab.id,
    name: tab.id === this.activeProjectTabId() ? this.project().name : tab.project.name })));
  private readonly persistence = new ProjectPersistence(() => this.writeWorkspace());

  /** Flush before an explicit host shutdown; browser lifecycle events also flush pending saves. */
  flushPersistence(): void { this.persistence.flush(); }

  ngOnDestroy(): void { this.persistence.destroy(); }

  constructor() {
    const restored: ProjectTab[] = [];
    let active = '';
    try {
      const raw = globalThis.localStorage?.getItem(TABS_STORAGE_KEY);
      if (raw) {
        const data = JSON.parse(raw);
        if (Array.isArray(data.tabs)) {
          const decode = (value: unknown) => decodeProject(value, this.sectionTypeSettings.chances(), Math.random);
          for (const tab of data.tabs) {
            if (!tab || typeof tab !== 'object') continue;
            const project = decode(tab.project);
            const past = Array.isArray(tab.past) ? tab.past.slice(-MAX_HISTORY).map(decode) : [];
            const future = Array.isArray(tab.future) ? tab.future.slice(-MAX_HISTORY).map(decode) : [];
            if (typeof tab.id === 'string' && project && past.every(Boolean) && future.every(Boolean)
                && !restored.some(item => item.id === tab.id)) {
              restored.push({ id: tab.id, project, past: past as CompositionProject[], future: future as CompositionProject[],
                automaticSeed: tab.automaticSeed === true });
            }
          }
          active = data.activeId;
        }
      }
    } catch { /* Keep the session project when workspace storage is unavailable. */ }
    if (!restored.length) restored.push(this.snapshotTab(globalThis.crypto.randomUUID()));
    this.tabs.set(restored);
    this.activateTab(restored.find(tab => tab.id === active) ?? restored[0]);
  }

  private snapshotTab(id = this.activeProjectTabId()): ProjectTab {
    return { id, project: this.state(), past: [...this.past], future: [...this.future], automaticSeed: this.automaticSeed() };
  }

  private activateTab(tab: ProjectTab): void {
    this.projectActivation.update(revision => revision + 1);
    this.activeProjectTabId.set(tab.id);
    this.past.splice(0, this.past.length, ...tab.past);
    this.future.splice(0, this.future.length, ...tab.future);
    this.lastControlEdit = null;
    this.automaticSeed.set(tab.automaticSeed);
    this.generationFailure.set(null);
    this.state.set(tab.project);
    this.refreshHistoryAvailability();
    this.persist();
  }

  switchProjectTab(id: string): void {
    if (id === this.activeProjectTabId()) return;
    const tab = this.tabs().find(tab => tab.id === id);
    if (tab) this.activateTab(tab);
  }

  duplicateProjectTab(id = this.activeProjectTabId()): void {
    this.copyProjectTab(id, 'copy');
  }

  checkpointProjectTab(): void {
    const now = new Date();
    const time = `${String(now.getHours()).padStart(2, '0')}_${String(now.getMinutes()).padStart(2, '0')}`;
    this.copyProjectTab(this.activeProjectTabId(), time);
  }

  private copyProjectTab(id: string, suffix: string): void {
    const source = id === this.activeProjectTabId() ? this.snapshotTab() : this.tabs().find(tab => tab.id === id);
    if (!source) return;
    const tab = structuredClone({ ...source, id: globalThis.crypto.randomUUID(),
      project: { ...source.project, name: `${source.project.name} (${suffix})` } });
    this.tabs.update(tabs => [...tabs, tab]);
    this.activateTab(tab);
  }

  removeProjectTab(id: string): void {
    const index = this.tabs().findIndex(tab => tab.id === id);
    if (index < 0) return;
    const remaining = this.tabs().filter(tab => tab.id !== id);
    if (!remaining.length) remaining.push({ id: globalThis.crypto.randomUUID(),
      project: copyDefaultProject(this.sectionTypeSettings.chances(), Math.random), past: [], future: [], automaticSeed: BigInt(DEFAULT_PROJECT.seed) === 0n });
    this.tabs.set(remaining);
    if (id === this.activeProjectTabId()) this.activateTab(remaining[Math.min(index, remaining.length - 1)]);
    else this.persist();
  }

  projectForTab(id: string): CompositionProject | undefined {
    return id === this.activeProjectTabId() ? this.project() : this.tabs().find(tab => tab.id === id)?.project;
  }

  renameProjectTab(id: string, value: string): void {
    const name = value.trim() || 'Untitled vibe';
    if (id === this.activeProjectTabId()) { this.updateSettings({ name }); return; }
    const tab = this.tabs().find(tab => tab.id === id);
    if (!tab || tab.project.name === name) return;
    this.tabs.update(tabs => tabs.map(item => item.id === id ? { ...item,
      project: { ...item.project, name }, past: [...item.past, item.project].slice(-MAX_HISTORY), future: [] } : item));
    this.persist();
  }

  goToHistory(index: number): void {
    if (!Number.isInteger(index) || index < 0 || index > this.past.length + this.future.length) return;
    const current = this.state();
    const states = [...this.past, current, ...[...this.future].reverse()];
    const next = preserveAudition(states[index], current);
    this.past.splice(0, this.past.length, ...states.slice(0, index));
    this.future.splice(0, this.future.length, ...states.slice(index + 1).reverse());
    this.lastControlEdit = null;
    this.state.set(next);
    this.persist();
    this.refreshHistoryAvailability();
  }

  toggleCompactHistory(): void {
    this.uiPreferences.toggleCompactHistory();
    this.lastControlEdit = null;
  }

  updateMelodyGlobal(patch: Partial<MelodyGlobalSettings>): PartWorkflowResult {
    const decoded = decodeMelodyGlobal(patch);
    if (!decoded) return 'invalid';
    const current = this.state(), melodyGlobal = { ...current.melodyGlobal, ...decoded };
    if (!decodeMelodyGlobal({ ...DEFAULT_MELODY_GLOBAL, ...melodyGlobal })) return 'invalid';
    if (Object.entries(decoded).every(([key, value]) =>
      partValuesEqual(current.melodyGlobal?.[key as keyof MelodyGlobalSettings], value))) return 'unchanged';
    this.commit({ ...current, melodyGlobal });
    return 'changed';
  }

  updateHarmonySettings(patch: HarmonySettings): PartWorkflowResult {
    const decoded = decodeHarmonySettings(patch);
    if (!decoded) return 'invalid';
    const current = this.state();
    const previous = { ...DEFAULT_HARMONY_SETTINGS, ...current.harmonySettings };
    if (Object.entries(decoded).every(([key,value]) => previous[key as keyof HarmonySettings] === value)) return 'unchanged';
    this.commit({ ...current, harmonySettings: { ...current.harmonySettings, ...decoded } }, this.controlHistoryKey(['harmony'], decoded));
    return 'changed';
  }

  updateSectionHarmony(sectionId: string, patch: Pick<ArrangementSection, 'useMelodyHarmony'>): PartWorkflowResult {
    if (!patch || typeof patch !== 'object' || Array.isArray(patch)
      || Object.entries(patch).some(([key,value]) => !['useMelodyHarmony'].includes(key) || typeof value !== 'boolean')) return 'invalid';
    const current = this.state(), section = current.arrangement.find(section => section.id === sectionId);
    if (!section) return 'invalid';
    if (Object.entries(patch).every(([key,value]) => (section[key as 'useMelodyHarmony'] ?? false) === value)) return 'unchanged';
    this.commit({ ...current, arrangement: current.arrangement.map(item => item.id === sectionId ? { ...item, ...patch } : item) });
    return 'changed';
  }

  editAllSectionMusic(change: (section: ArrangementSection) => ArrangementSection): PartWorkflowResult {
    const current = this.state();
    const next = { ...current, arrangement: current.arrangement.map(change) };
    if (!isProject(next, this.sectionTypeSettings.chances(), Math.random)) return 'invalid';
    if (JSON.stringify(current) === JSON.stringify(next)) return 'unchanged';
    this.commit(followHarmonyMode(current, next, this.uiPreferences.harmonyFollowsMode()));
    return 'changed';
  }

  updateSectionContext(sectionId: string, patch: Partial<{ [K in keyof SectionMusicalContext]: SectionMusicalContext[K] | null }>): PartWorkflowResult {
    const current = this.state(), section = current.arrangement.find(item => item.id === sectionId);
    if (!section || !patch || typeof patch !== 'object' || Array.isArray(patch)) return 'invalid';
    const context = { ...section.context } as Record<string, unknown>;
    const keys = ['velocityPercent','tempoBpm','swingPercent','key','scale','keyChange','keyChangeType','beatDurationMultiplier'];
    for (const [key, value] of Object.entries(patch)) {
      if (!keys.includes(key)) return 'invalid';
      if (value === null) delete context[key]; else context[key] = value;
    }
    return this.setSectionMusicalState(sectionId, { context: Object.keys(context).length ? context as SectionMusicalContext : undefined });
  }

  setSectionMusicalState(sectionId: string, patch: Pick<ArrangementSection, 'context' | 'customChords' | 'customDurations' | 'chordDegrees'>): PartWorkflowResult {
    const current = this.state(), section = current.arrangement.find(item => item.id === sectionId);
    if (!section || !patch || typeof patch !== 'object' || Array.isArray(patch)
      || Object.keys(patch).some(key => !['context','customChords','customDurations','chordDegrees'].includes(key))) return 'invalid';
    const next = { ...section, ...patch };
    if (!validSectionHarmony(next) || next.chordDegrees !== undefined && (!Array.isArray(next.chordDegrees)
      || !next.chordDegrees.length || next.chordDegrees.length > 32
      || next.chordDegrees.some(degree => !Number.isInteger(degree) || degree < 1 || degree > 7))) return 'invalid';
    if (Object.entries(patch).every(([key,value]) => sectionValuesEqual(section[key as keyof typeof patch], value))) return 'unchanged';
    const copied = { ...next, context: next.context ? { ...next.context } : undefined,
      chordDegrees: next.chordDegrees ? [...next.chordDegrees] : undefined,
      customChords: next.customChords?.map(chord => chord ? { ...chord } : null), customDurations: next.customDurations ? [...next.customDurations] : undefined };
    this.commit(followHarmonyMode(current, { ...current, arrangement: current.arrangement.map(item => item.id === sectionId ? copied : item) }, this.uiPreferences.harmonyFollowsMode()));
    return 'changed';
  }

  setSectionChord(sectionId: string, index: number, chord: ProgressionChord): void {
    const project = this.state(), section = project.arrangement.find(item => item.id === sectionId);
    if (!section) return;
    const degrees = sectionDegrees(project, section);
    const count = section.customDurations?.length ?? degrees.length;
    if (!Number.isInteger(index) || index < 0 || index >= count) return;
    const contextual = sectionProject(project, section), harmony = baseGenerationHarmony(project, section);
    const displayed = progressionChords(contextual.key, contextual.scale, harmony.degrees, harmony.customChords);
    const custom = Array.from({ length: count }, (_, i) => i === index ? chord : displayed[i]);
    this.setSectionMusicalState(sectionId, { chordDegrees: Array.from({ length: count }, (_, i) => degrees[i % degrees.length]), customChords: custom });
  }

  editSectionChordList(sectionId: string, action: 'add' | 'remove' | 'move', from = 0, to = 0): void {
    const project = this.state(), section = project.arrangement.find(item => item.id === sectionId);
    if (!section) return;
    const base = sectionDegrees(project, section), count = section.customDurations?.length ?? base.length;
    const degrees = Array.from({ length: count }, (_, i) => base[i % base.length]);
    const contextual = sectionProject(project, section), harmony = baseGenerationHarmony(project, section);
    const chords: (ProgressionChord | null)[] = progressionChords(contextual.key, contextual.scale, harmony.degrees, harmony.customChords);
    const durations = section.customDurations ? [...section.customDurations] : undefined;
    if (action === 'add') { if (count >= 32) return; degrees.push(1); chords.push(null); durations?.push(4); }
    else {
      if (!Number.isInteger(from) || from < 0 || from >= count) return;
      if (action === 'remove') { if (count <= 1) return; degrees.splice(from,1); chords.splice(from,1); durations?.splice(from,1); }
      else { if (!Number.isInteger(to) || to < 0 || to >= count || from === to) return;
        degrees.splice(to,0,degrees.splice(from,1)[0]); chords.splice(to,0,chords.splice(from,1)[0]);
        if (durations) durations.splice(to,0,durations.splice(from,1)[0]); }
    }
    this.setSectionMusicalState(sectionId, { chordDegrees: degrees, customChords: chords, customDurations: durations });
  }

  /** Only single-control edits can join a burst; scope and field identify the control. */
  private controlHistoryKey(target: readonly string[], patch: object): string | undefined {
    const keys = Object.keys(patch);
    return keys.length === 1 ? JSON.stringify([...target, keys[0]]) : undefined;
  }

  exportProjectJson(): string {
    return formatProjectJson(this.state());
  }

  updateArpeggioPolicy(patch: Partial<ArpeggioGenerationPolicy>): PartWorkflowResult {
    return this.updateTrackGenerationPolicy('arpeggio', patch);
  }

  updateTrackGenerationPolicy(role: ArrangedPart, patch: TrackGenerationPolicyPatch): PartWorkflowResult {
    if (!ARRANGED_PARTS.includes(role)) return 'invalid';
    const current = this.state();
    const previous = current.trackGenerationPolicies?.[role] ?? DEFAULT_TRACK_POLICIES[role];
    const policy = decodeTrackGenerationPolicy(role, { ...previous, ...patch });
    if (!policy) return 'invalid';
    if (Object.entries(policy).every(([key, value]) => value === (previous as unknown as Record<string, unknown>)[key])) return 'unchanged';
    return this.commitPartWorkflow({ ...current, trackGenerationPolicies: { ...current.trackGenerationPolicies, [role]: policy } },
      this.controlHistoryKey(['policy', role], patch));
  }

  setParameterLocks(scope: PartSettingsScope, keys: readonly string[], locked: boolean): void {
    if (scope.kind !== 'global-track' && scope.kind !== 'global-role') return;
    const current = this.state();
    const tracks = current.tracks.map(track => {
      if (scope.kind === 'global-track' ? track.id !== scope.trackId : track.role !== scope.role) return track;
      if (!keys.length || keys.some(key => !PART_CONTROLS[track.role].some(control => control.key === key))) return track;
      const parameterLocks = new Set(track.parameterLocks ?? []);
      for (const key of keys) locked ? parameterLocks.add(key) : parameterLocks.delete(key);
      if (parameterLocks.size === (track.parameterLocks?.length ?? 0) && [...parameterLocks].every(key => track.parameterLocks?.includes(key))) return track;
      return { ...track, parameterLocks: [...parameterLocks] };
    });
    if (tracks.some((track, index) => track !== current.tracks[index])) this.commit({ ...current, tracks });
  }

  private preserveParameterLocks(source: CompositionTrack, candidate: CompositionTrack): CompositionTrack {
    if (!source.parameterLocks?.length) return candidate;
    const settings = { ...candidate.generatorSettings } as unknown as Record<string, unknown>;
    const original = source.generatorSettings as unknown as Record<string, unknown>;
    for (const key of source.parameterLocks) {
      if (original[key] === undefined) delete settings[key];
      else settings[key] = original[key];
    }
    return { ...candidate, generatorSettings: settings } as unknown as CompositionTrack;
  }

  setTrackRerollLock(trackId: string, locked: boolean): void {
    if (typeof locked !== 'boolean') return;
    const current = this.state();
    const track = current.tracks.find(item => item.id === trackId);
    if (!track || (track.rerollLocked ?? false) === locked) return;
    this.commit({ ...current, tracks: current.tracks.map(item => item.id === trackId ? { ...item, rerollLocked: locked } : item) });
  }

  setTrackInstrumentLock(trackId: string, locked: boolean): void {
    if (typeof locked !== 'boolean') return;
    const current = this.state(), track = current.tracks.find(item => item.id === trackId);
    if (!track || (track.instrumentLocked ?? false) === locked) return;
    this.commit({ ...current, tracks: current.tracks.map(item => item.id === trackId ? { ...item, instrumentLocked: locked } : item) });
  }

  updateInstrumentPolicy(role: ArrangedPart, patch: Partial<InstrumentGenerationPolicy>): PartWorkflowResult {
    if (!ARRANGED_PARTS.includes(role)) return 'invalid';
    const current = this.state(), previous = current.instrumentGenerationPolicies?.[role] ?? DEFAULT_INSTRUMENT_POLICIES[role];
    const policy = decodeInstrumentPolicy(role, { ...previous, ...patch });
    if (!policy) return 'invalid';
    if (Object.entries(policy).every(([key, value]) => value === previous[key as keyof InstrumentGenerationPolicy])) return 'unchanged';
    return this.commitPartWorkflow({ ...current, instrumentGenerationPolicies: { ...current.instrumentGenerationPolicies, [role]: policy } },
      this.controlHistoryKey(['instrument-policy', role], patch));
  }

  /** Explicit instrument-only action ignores permission, but respects both locks. */
  rerollRoleInstruments(role: ArrangedPart, trackId?: string): PartWorkflowResult {
    if (!ARRANGED_PARTS.includes(role)) return 'invalid';
    const current = this.state(), count = current.trackRerollCounts?.[role] ?? 0;
    const sources = current.tracks.filter(track => track.role === role && !track.rerollLocked && !track.instrumentLocked
      && (trackId === undefined || track.id === trackId));
    if (!sources.length) return 'unchanged';
    if (count >= 2147483647) return 'invalid';
    const targets = rerollInstruments(this.trackRerollSeed(current, role, count), role,
      current.instrumentGenerationPolicies?.[role] ?? DEFAULT_INSTRUMENT_POLICIES[role], sources, true,
      { reservedPitches: projectDrumPitches(current, sources.map(track => track.id)), remaining: current.tracks.filter(track => track.role === 'drums' && !sources.includes(track)).map(track => track.generatorSettings as DrumSettings) });
    if (targets.every((track, index) => track === sources[index])) return 'unchanged';
    const replacements = new Map(targets.map(track => [track.id, track]));
    return this.commitPartWorkflow({ ...current, tracks: current.tracks.map(track => replacements.get(track.id) ?? track),
      trackRerollCounts: { ...current.trackRerollCounts, [role]: count + 1 } });
  }

  /** Reroll existing global track bases. Local overrides, identity and saved manual notes remain owned. */
  rerollArpeggioTracks(trackId?: string): PartWorkflowResult { return this.rerollRoleTracks('arpeggio', trackId); }

  /** Set a role's total, protecting locks and deleting highest numbered members first. */
  setRoleTrackCount(role: ArrangedPart, requested: number): PartWorkflowResult {
    if (!ARRANGED_PARTS.includes(role) || !Number.isInteger(requested) || requested < 0 || requested > 64) return 'invalid';
    const current = this.state();
    const members = current.tracks.filter(track => track.role === role);
    const minimum = current.tracks.some(track => track.role !== role) ? 0 : 1;
    const target = Math.max(requested, minimum, members.filter(track => track.rerollLocked).length);
    if (target === members.length) return 'unchanged';
    if (target > members.length) return this.generateRoleTracks(role, target - members.length);
    const removable = members.filter(track => !track.rerollLocked)
      .sort((a, b) => Number(b.id.match(/(\d+)$/)?.[1] ?? members.indexOf(b) + 1)
        - Number(a.id.match(/(\d+)$/)?.[1] ?? members.indexOf(a) + 1));
    const removed = new Set(removable.slice(0, members.length - target).map(track => track.id));
    return this.commitPartWorkflow(fitVariationCapacity({ ...current, tracks: current.tracks.filter(track => !removed.has(track.id)),
      arrangement: current.arrangement.map(section => ({ ...section,
        ...(section.variations ? { variations: pruneVariations(section.variations, removed) } : {}),
        trackPresence: Object.fromEntries(Object.entries(section.trackPresence).filter(([id]) => !removed.has(id))),
        ...(section.generatedTrackIds ? { generatedTrackIds: section.generatedTrackIds.filter(id => !removed.has(id)) } : {}),
        ...(section.trackPhrases !== undefined ? { trackPhrases: Object.fromEntries(Object.entries(section.trackPhrases).filter(([id]) => !removed.has(id))) } : {}),
        ...(section.trackPartOverrides !== undefined ? { trackPartOverrides: Object.fromEntries(
          Object.entries(section.trackPartOverrides).filter(([id]) => !removed.has(id))) } : {}),
      })) }));
  }

  /** Append explicitly generated tracks in one transaction; never replace existing members. */
  generateRoleTracks(role: ArrangedPart, amount: number): PartWorkflowResult {
    const current = this.state();
    if (!ARRANGED_PARTS.includes(role) || !Number.isInteger(amount) || amount < 1 || amount > 64
      || current.tracks.length + amount > 64) return 'invalid';
    const count = current.trackRerollCounts?.[role] ?? 0;
    if (count >= 2147483647) return 'invalid';
    const tracks = [...current.tracks];
    const source = tracks.find(track => track.role === role) ?? DEFAULT_TRACKS.find(track => track.role === role)!;
    const created: CompositionTrack[] = [];
    for (let index = 0; index < amount; index++) {
      const number = Math.max(0, ...tracks.filter(track => track.role === role)
        .map(track => Number(track.id.match(/(\d+)$/)?.[1] ?? 0))) + 1;
      const id = `track-${role}-${number}`;
      const track = { ...source, id, name: `${role[0].toUpperCase()}${number}`,
        midiChannel: role === 'drums' ? 10 : this.nextAvailableChannel(tracks),
        generatorSettings: copyPartSettings(source.generatorSettings),
        mix: { ...source.mix, muted: false, solo: false }, rerollLocked: false, instrumentLocked: false, editedPhrase: undefined, phraseEdits: undefined } as CompositionTrack;
      created.push(track); tracks.push(track);
    }
    const policy = current.trackGenerationPolicies?.[role] ?? DEFAULT_TRACK_POLICIES[role];
    const seed = this.trackRerollSeed(current, role, count);
    const members = tracks.filter(track => track.role === role);
    const drumContext = { onlyAdd: true, totalTracks: members.length, reservedPitches: projectDrumPitches(current),
      remaining: current.tracks.filter(track => track.role === 'drums').map(track => track.generatorSettings as DrumSettings) };
    const instrumentTracks = rerollInstruments(seed, role, current.instrumentGenerationPolicies?.[role] ?? DEFAULT_INSTRUMENT_POLICIES[role], created, false, drumContext);
    const settings = rerollRoleSettings(seed, role, policy, instrumentTracks, created.map(track => members.indexOf(track) + 1), drumContext);
    const generated = instrumentTracks.map((track, index) => ({ ...track, generatorSettings: settings[index] } as CompositionTrack));
    const replacements = new Map(generated.map(track => [track.id, track]));
    const presenceRandom = new JavaRandom(BigInt.asIntN(64, seed ^ 0x50524553454e4345n));
    return this.commitPartWorkflow({ ...current, tracks: tracks.map(track => replacements.get(track.id) ?? track),
      arrangement: current.arrangement.map(section => ({ ...section, trackPresence: { ...section.trackPresence,
        ...generateTrackPresence(generated, this.sectionTypeSettings.chances()[section.type], () => presenceRandom.nextDouble()) } })),
      trackRerollCounts: { ...current.trackRerollCounts, [role]: count + 1 } });
  }

  private trackRerollSeed(project: CompositionProject, role: ArrangedPart, count: number): bigint {
    const offset = { melody: 0n, bass: 100000n, chords: 200000n, arpeggio: 300000n, drums: 400000n }[role];
    return BigInt.asIntN(64, BigInt(project.seed) + offset + BigInt(count) * 0x9e3779b97f4a7c15n);
  }

  /** Reseed eligible tracks in one history transaction. */
  reseedRoleTracks(role: ArrangedPart): PartWorkflowResult {
    if (!ARRANGED_PARTS.includes(role)) return 'invalid';
    this.generationFailure.set(null);
    try {
      const current = this.state();
      const policy = current.trackGenerationPolicies?.[role] ?? DEFAULT_TRACK_POLICIES[role];
      if (!(policy.rerollSeeds ?? role === 'melody')) return 'unchanged';
      const count = current.trackRerollCounts?.[role] ?? 0;
      if (count >= 2147483647) return 'invalid';
      const random = new JavaRandom(this.trackRerollSeed(current, role, count));
      const sharedSeed = random.nextInt();
      let order = 0;
      const tracks = current.tracks.map(track => {
        if (track.role !== role) return track;
        order++;
        if (track.rerollLocked || hasManualPhrase(track)) return track;
        const settings = { ...track.generatorSettings, patternSeed: policy.sameSeed ? sharedSeed : random.nextInt() };
        if (track.role === 'melody') {
          const melodyPolicy = current.trackGenerationPolicies?.melody ?? DEFAULT_TRACK_POLICIES.melody;
          const seed = ((settings.patternSeed || Number(BigInt.asIntN(32, BigInt(current.seed)))) + order) | 0;
          if (melodyPolicy.patternsOnCompose ?? true) Object.assign(settings, { melodyPatternOffsets: chooseMelodyPattern(seed) });
          if (melodyPolicy.targetsOnCompose ?? true) Object.assign(settings, { chordNoteChoices: chooseMelodyTargets(seed, current.progression,
            current.melodyGlobal, current.key, current.scale, current.progressionChords, { ...track.generatorSettings, patternSeed: settings.patternSeed }) });
        }
        return { ...track, generatorSettings: settings } as CompositionTrack;
      });
      if (tracks.every((track, index) => track === current.tracks[index])) return 'unchanged';
      const next = { ...current, tracks, trackRerollCounts: { ...current.trackRerollCounts, [role]: count + 1 } };
      for (const track of next.tracks) {
        phraseForTrack(next, track);
        layOutTrackPhrase(next, track, true);
      }
      return this.commitPartWorkflow(next);
    } catch (error) {
      this.generationFailure.set(error instanceof Error ? error.message : 'The requested music could not be generated.');
      return 'invalid';
    }
  }

  rerollRoleTracks(role: ArrangedPart, trackId?: string): PartWorkflowResult {
    if (!ARRANGED_PARTS.includes(role)) return 'invalid';
    const candidate = this.buildRoleReroll(this.state(), role, trackId);
    return candidate ? candidate === this.state() ? 'unchanged' : this.commitPartWorkflow(candidate) : 'invalid';
  }

  /** Overview shortcuts are section-local and commit the entire gesture once. */
  changeOverviewTracks(scope: LocalPartSettingsScope | { kind: 'section'; sectionId: string },
    action: 'reroll' | 'reset'): PartWorkflowResult {
    const current = this.state(), section = current.arrangement.find(item => item.id === scope.sectionId);
    if (!section || !['reroll', 'reset'].includes(action)) return 'invalid';
    const members = current.tracks.filter(track => scope.kind === 'section' ||
      (scope.kind === 'section-role' ? track.role === scope.role : track.id === scope.trackId));
    if (!members.length) return 'unchanged';
    const rolePartOverrides = { ...section.rolePartOverrides };
    const trackPartOverrides = { ...section.trackPartOverrides };
    const trackPhrases = { ...section.trackPhrases };
    const generatedTrackIds = new Set(section.generatedTrackIds);
    const counts = { ...current.trackRerollCounts };
    for (const role of ARRANGED_PARTS) {
      const selected = members.filter(track => track.role === role);
      if (!selected.length) continue;
      if (action === 'reset') {
        // Move a shared cell override to untouched siblings before removing it.
        const shared = rolePartOverrides[role];
        if (shared) for (const sibling of current.tracks.filter(track => track.role === role && !selected.includes(track))) {
          trackPartOverrides[sibling.id] = copyPartSettings({ ...shared, ...trackPartOverrides[sibling.id] });
        }
        delete rolePartOverrides[role];
        for (const track of selected) {
          delete trackPartOverrides[track.id];
          delete trackPhrases[track.id];
          if (hasManualPhrase(track)) generatedTrackIds.add(track.id);
          else generatedTrackIds.delete(track.id);
        }
      } else {
        const sources = selected.filter(track => !track.rerollLocked).map(track => resolvePartTrack(track, section, current.melodyGlobal));
        if (!sources.length) continue;
        const count = counts[role] ?? 0;
        if (count >= 2147483647) return 'invalid';
        const roleTracks = current.tracks.filter(track => track.role === role);
        const drumContext = { totalTracks: roleTracks.length, reservedPitches: projectDrumPitches(current, sources.map(track => track.id)),
          remaining: roleTracks.filter(track => !sources.some(source => source.id === track.id))
            .map(track => resolvePartTrack(track, section, current.melodyGlobal).generatorSettings as DrumSettings) };
        const settings = rerollRoleSettings(this.trackRerollSeed(current, role, count), role,
          current.trackGenerationPolicies?.[role] ?? DEFAULT_TRACK_POLICIES[role], sources,
          sources.map(track => roleTracks.findIndex(item => item.id === track.id) + 1), drumContext);
        sources.forEach((track, index) => {
          const base = current.tracks.find(item => item.id === track.id)!;
          const inherited = settingsValues(resolvePartTrack(base, { ...section, trackPartOverrides: undefined },
            current.melodyGlobal).generatorSettings, role);
          // Keep unchanged controls inheriting instead of freezing the entire rerolled snapshot.
          const patch = copyPartSettings(Object.fromEntries(Object.entries(settingsValues(settings[index], role))
            .filter(([field, value]) => !partValuesEqual(value, inherited[field]))) as PartSettingsPatch);
          if (Object.keys(patch).length) trackPartOverrides[track.id] = patch;
          else delete trackPartOverrides[track.id];
        });
        counts[role] = count + 1;
      }
    }
    if (JSON.stringify(rolePartOverrides) === JSON.stringify({ ...section.rolePartOverrides })
      && JSON.stringify(trackPartOverrides) === JSON.stringify({ ...section.trackPartOverrides })
      && JSON.stringify(trackPhrases) === JSON.stringify({ ...section.trackPhrases })
      && JSON.stringify([...generatedTrackIds]) === JSON.stringify(section.generatedTrackIds ?? [])
      && JSON.stringify(counts) === JSON.stringify({ ...current.trackRerollCounts })) return 'unchanged';
    const nextSection = { ...section, rolePartOverrides, trackPartOverrides,
      ...(section.trackPhrases !== undefined ? { trackPhrases } : {}),
      ...(generatedTrackIds.size || section.generatedTrackIds !== undefined ? { generatedTrackIds: [...generatedTrackIds] } : {}) };
    return this.commitPartWorkflow({ ...current, trackRerollCounts: counts,
      arrangement: current.arrangement.map(item => item === section ? nextSection : item) });
  }

  private buildRoleReroll(current: CompositionProject, role: ArrangedPart, trackId?: string): CompositionProject | undefined {
    const roleTracks = current.tracks.filter(track => track.role === role);
    const sources = roleTracks.filter(track => !track.rerollLocked && (trackId === undefined || track.id === trackId));
    if (!sources.length) return current;
    const policy = current.trackGenerationPolicies?.[role] ?? DEFAULT_TRACK_POLICIES[role];
    const count = current.trackRerollCounts?.[role] ?? 0;
    if (count >= 2147483647) return undefined;
    const seed = this.trackRerollSeed(current, role, count);
    const drumContext = { totalTracks: roleTracks.length, reservedPitches: projectDrumPitches(current, sources.map(track => track.id)),
      remaining: roleTracks.filter(track => !sources.includes(track)).map(track => track.generatorSettings as DrumSettings) };
    const instrumentTracks = rerollInstruments(seed, role, current.instrumentGenerationPolicies?.[role] ?? DEFAULT_INSTRUMENT_POLICIES[role], sources, false, drumContext);
    const settings = rerollRoleSettings(seed, role, policy, instrumentTracks, sources.map(track => roleTracks.indexOf(track) + 1), drumContext);
    if (sources.every((track, index) => track === instrumentTracks[index] && partPatchesEqual(track.generatorSettings, settings[index]))) return current;
    const replacements = new Map(instrumentTracks.map((track, index) => [track.id, this.preserveParameterLocks(sources[index], { ...track, generatorSettings: settings[index] } as CompositionTrack)]));
    const next = { ...current, tracks: current.tracks.map(track => replacements.get(track.id) ?? track),
      trackRerollCounts: { ...current.trackRerollCounts, [role]: count + 1 } };
    return next;
  }

  /** Explicit Compose only. Live reload, playback and ordinary edits never run policies. */
  generateComposition(): PartWorkflowResult {
    this.generationFailure.set(null);
    try {
      return this.prepareComposition();
    } catch (error) {
      this.generationFailure.set(error instanceof Error ? error.message : 'The requested music could not be generated.');
      return 'invalid';
    }
  }

  private prepareComposition(): PartWorkflowResult {
    const current = this.state();
    let next = current;
    const seed = this.automaticSeed() || BigInt(current.seed) === 0n
      ? this.randomCompositionSeed() : current.seed;
    const random = new JavaRandom(BigInt(seed));
    // Separate stream from track rerolls; draw both values even when a star is disabled.
    const transpose = random.nextInt(12) - 6;
    const bounds = this.uiPreferences.preferences();
    const tempo = random.nextInt(bounds.composeBpmMax - bounds.composeBpmMin + 1) + bounds.composeBpmMin;
    next = { ...current, seed,
      transposeSemitones: current.randomizeTransposeOnCompose === false ? current.transposeSemitones : transpose,
      tempoBpm: current.randomizeTempoOnCompose === false ? current.tempoBpm : tempo };
    if (current.randomizeHarmonyOnCompose !== false) {
      // Harmony owns its seeded stream; toggling it never shifts other random draws.
      const generated = generateHarmony(BigInt(seed), current.progression.length, next.key, next.scale,
        next.harmonySettings, next.melodyGlobal);
      next = { ...next, progression: generated.degrees, progressionChords: generated.chords };
    }
    if ((next.melodyGlobal?.contextualMelody ?? true) && next.melodyGlobal?.rangeRandomizeOnCompose
      && next.tracks.some(track => track.role === 'melody' && !track.rerollLocked && !hasManualPhrase(track))) {
      next = { ...next, tracks: next.tracks.map((track, index) => track.role === 'melody' && !track.rerollLocked && !hasManualPhrase(track)
        ? { ...track, generatorSettings: { ...track.generatorSettings, ...contextualRange(BigInt(seed) + BigInt(index)) } } : track) };
    }
    for (const role of ARRANGED_PARTS) {
      if ((current.trackGenerationPolicies?.[role] ?? DEFAULT_TRACK_POLICIES[role]).onCompose === false) continue;
      const candidate = this.buildRoleReroll(next, role);
      if (!candidate) return 'invalid';
      next = candidate;
    }
    const policy = current.trackGenerationPolicies?.melody ?? DEFAULT_TRACK_POLICIES.melody;
    const count = current.trackRerollCounts?.melody ?? 0;
    const sources = next.tracks.filter(track => track.role === 'melody' && !track.rerollLocked);
    if (sources.length && ((policy.patternsOnCompose ?? true) || (policy.targetsOnCompose ?? true)
      || !(next.melodyGlobal?.contextualMelody ?? true) && next.melodyGlobal?.forceFirstOutline)) {
      if (count >= 2147483647) return 'invalid';
      let tracks = next.tracks.map(track => {
        if (track.role !== 'melody' || track.rerollLocked) return track;
        const settings = { ...track.generatorSettings };
        const order = next.tracks.filter(item => item.role === 'melody').indexOf(track) + 1;
        const patternSeed = ((settings.patternSeed || Number(BigInt.asIntN(32, BigInt(next.seed)))) + order) | 0;
        if (policy.patternsOnCompose ?? true) settings.melodyPatternOffsets = chooseMelodyPattern(patternSeed);
        if (policy.targetsOnCompose ?? true) settings.chordNoteChoices = chooseMelodyTargets(patternSeed, next.progression, next.melodyGlobal,
          next.key, next.scale, next.progressionChords, settings);
        if (partPatchesEqual(track.generatorSettings, settings)) return track;
        return { ...track, generatorSettings: settings };
      });
      if (!(next.melodyGlobal?.contextualMelody ?? true) && next.melodyGlobal?.forceFirstOutline) {
        const first = tracks.find(track => track.role === 'melody');
        if (first?.role === 'melody') tracks = tracks.map(track => {
          if (track.role !== 'melody' || track === first || track.rerollLocked) return track;
          const settings = forceMelodyOutline(first.generatorSettings, track.generatorSettings);
          if (partPatchesEqual(track.generatorSettings, settings)) return track;
          return { ...track, generatorSettings: settings };
        });
      }
      // Forced outline can erase intermediate per-track preparation changes.
      const changed = tracks.some((track, index) => !partPatchesEqual(track.generatorSettings, next.tracks[index].generatorSettings));
      if (changed) next = { ...next, tracks,
        trackRerollCounts: { ...next.trackRerollCounts, melody: count + 1 } };
    }
    if (next.randomizeArrangementOnCompose) {
      // Plan after track preparation; orchestration and variations own independent streams.
      const random = new JavaRandom(BigInt(next.seed));
      if (next.arrangementManual) {
        next = arrangementCommands.randomizeArrangementPresence(next, undefined,
          this.sectionTypeSettings.chances(), () => random.nextDouble())!;
        if (this.uiPreferences.generateArrangementVariations()) next = arrangementCommands.randomizeArrangementVariations(next,
          BigInt(next.seed), undefined, next.arrangementVariationPolicy ?? DEFAULT_VARIATION_POLICY, this.uiPreferences.variationRerolls())!;
      } else next = arrangementCommands.randomizeArrangement(next, this.sectionTypeSettings.chances(), BigInt(next.seed),
        this.uiPreferences.generateArrangementVariations(), this.uiPreferences.variationRerolls());
      next = arrangementCommands.randomizeArrangementBeatDuration(next, BigInt(next.seed));
    }
    next = { ...next, tracks: next.tracks.map(track => {
      const source = current.tracks.find(source => source.id === track.id);
      return source ? this.preserveParameterLocks(source, track) : track;
    }) };
    if (next === current) return 'unchanged';
    // Rendering also asks for the global phrase (note counts) and for section
    // layouts/dependencies. Resolve both before publishing any candidate state.
    // Successful layouts are cached and reused by the view and playback.
    for (const track of next.tracks) {
      phraseForTrack(next, track);
      layOutTrackPhrase(next, track, true);
    }
    return this.commitPartWorkflow(next);
  }

  updatePartSettings(scope: PartSettingsScope, patch: PartSettingsPatch): void {
    const current = this.state();
    const track = 'trackId' in scope ? current.tracks.find((candidate) => candidate.id === scope.trackId) : undefined;
    const role = 'role' in scope ? scope.role : track?.role;
    if (!role || !ARRANGED_PARTS.includes(role)) return;
    const decoded = decodePartPatch(role, patch, true);
    if (!decoded) return;
    if (scope.kind === 'global-role') { this.updateRoleGeneratorSettings(role, decoded); return; }
    if (scope.kind === 'global-track') { this.updateTrackGeneratorSettings(scope.trackId, decoded); return; }
    const arrangement = current.arrangement.map((section) => {
      if (section.id !== scope.sectionId) return section;
      const key = scope.kind === 'section-role' ? role : scope.trackId;
      const overrides = scope.kind === 'section-role' ? section.rolePartOverrides : section.trackPartOverrides;
      const previous = scope.kind === 'section-role' ? section.rolePartOverrides?.[role] : section.trackPartOverrides?.[scope.trackId];
      const values: Record<string, PartSettingValue> = { ...previous };
      // A manual edit equal to this layer's inherited value restores inheritance for that field.
      // Cell inheritance uses all track bases, ignoring their higher-priority section exceptions.
      // Track inheritance includes the cell patch but excludes its own patch.
      const members = scope.kind === 'section-role' ? current.tracks.filter((item) => item.role === role) : track ? [track] : [];
      const inherited = members.map((item) => settingsValues(scope.kind === 'section-role' ? item.generatorSettings
        : resolvePartTrack(item, { ...section, trackPartOverrides: undefined }, current.melodyGlobal).generatorSettings, role));
      for (const [field, value] of Object.entries(decoded)) {
        if (inherited.length && inherited.every((settings) => partValuesEqual(settings[field], value))) delete values[field];
        else values[field] = value as PartSettingValue;
      }
      let trackPartOverrides = section.trackPartOverrides;
      if (scope.kind === 'section-role') {
        // A group edit replaces member exceptions only for the edited fields.
        for (const member of members) {
          const previousTrack = trackPartOverrides?.[member.id];
          if (!previousTrack) continue;
          const nextTrack = { ...previousTrack };
          for (const field of Object.keys(decoded)) delete (nextTrack as Record<string, unknown>)[field];
          if (partPatchesEqual(previousTrack, nextTrack)) continue;
          const nextOverrides = { ...trackPartOverrides };
          if (Object.keys(nextTrack).length) nextOverrides[member.id] = nextTrack;
          else delete nextOverrides[member.id];
          trackPartOverrides = nextOverrides;
        }
      }
      if (partPatchesEqual(previous, values as PartSettingsPatch) && trackPartOverrides === section.trackPartOverrides) return section;
      const map: Record<string, PartSettingsPatch> = { ...overrides };
      if (Object.keys(values).length) map[key] = values as PartSettingsPatch;
      else delete map[key];
      return scope.kind === 'section-role'
        ? { ...section, rolePartOverrides: map as NonNullable<ArrangementSection['rolePartOverrides']>, trackPartOverrides }
        : { ...section, trackPartOverrides: map };
    });
    this.commit({ ...current, arrangement }, this.controlHistoryKey(
      ['part', scope.kind, scope.sectionId, scope.kind === 'section-role' ? scope.role : scope.trackId], decoded));
  }

  resetPartSettings(scope: PartSettingsScope, field?: string | readonly string[]): void {
    if (scope.kind !== 'section-role' && scope.kind !== 'section-track') return;
    const current = this.state();
    const arrangement = current.arrangement.map((section) => {
      if (section.id !== scope.sectionId) return section;
      const key = scope.kind === 'section-role' ? scope.role : scope.trackId;
      const map: Record<string, PartSettingsPatch> = { ...(scope.kind === 'section-role' ? section.rolePartOverrides : section.trackPartOverrides) };
      const values: Record<string, unknown> = { ...map[key] };
      if (field) for (const name of typeof field === 'string' ? [field] : field) delete values[name];
      else for (const name of Object.keys(values)) delete values[name];
      if (Object.keys(values).length) (map as Record<string, PartSettingsPatch>)[key] = values as PartSettingsPatch;
      else delete (map as Record<string, PartSettingsPatch>)[key];
      const trackPartOverrides = { ...section.trackPartOverrides };
      if (scope.kind === 'section-role') {
        for (const track of current.tracks.filter(track => track.role === scope.role)) {
          const patch = { ...trackPartOverrides[track.id] } as Record<string, unknown>;
          for (const name of field ? typeof field === 'string' ? [field] : field : Object.keys(patch)) delete patch[name];
          if (Object.keys(patch).length) trackPartOverrides[track.id] = patch as PartSettingsPatch;
          else delete trackPartOverrides[track.id];
        }
      }
      return scope.kind === 'section-role' ? { ...section, rolePartOverrides: map as NonNullable<ArrangementSection['rolePartOverrides']>, trackPartOverrides }
        : { ...section, trackPartOverrides: map };
    });
    this.commit({ ...current, arrangement });
  }

  /** Transform each member independently, including mixed cells, in one atomic action. */
  multiplyDrumHits(scope: PartSettingsScope, multiplier: number): PartWorkflowResult {
    if (!(DRUM_HIT_MULTIPLIERS as readonly number[]).includes(multiplier)) return 'invalid';
    const current = this.state();
    const members = current.tracks.filter(track => 'trackId' in scope ? track.id === scope.trackId : track.role === scope.role);
    const section = 'sectionId' in scope ? current.arrangement.find(item => item.id === scope.sectionId) : undefined;
    if (!members.length || members.some(track => track.role !== 'drums') || ('sectionId' in scope && !section)) return 'invalid';
    const patches = new Map<string, PartSettingsPatch>();
    for (const track of members) {
      const effective = resolvePartTrack(track, section, current.melodyGlobal);
      if (effective.role !== 'drums' || effective.generatorSettings.rhythm === 'melody') return 'invalid';
      const patch = multiplyDrumHits(effective.generatorSettings, multiplier);
      if (Object.entries(patch).some(([key, value]) => !partValuesEqual(settingsValues(effective.generatorSettings, 'drums')[key], value))) {
        patches.set(track.id, patch);
      }
    }
    if (!patches.size) return 'unchanged';
    if (section) {
      const trackPartOverrides = { ...section.trackPartOverrides };
      for (const [id, patch] of patches) trackPartOverrides[id] = copyPartSettings({ ...trackPartOverrides[id], ...patch });
      return this.commitPartWorkflow({ ...current, arrangement: current.arrangement.map(item => item.id === section.id
        ? { ...item, trackPartOverrides } : item) });
    }
    const tracks = current.tracks.map(track => patches.has(track.id)
      ? { ...track, generatorSettings: copyPartSettings({ ...track.generatorSettings, ...patches.get(track.id) }) } as CompositionTrack : track);
    return this.commitPartWorkflow({ ...current, tracks });
  }

  /** Snapshot each current member independently; mixed cells never pick a representative track. */
  freezePartSettings(scope: LocalPartSettingsScope): PartWorkflowResult {
    return this.copyLocalPartSettings(scope, [scope.sectionId], 'effective', true);
  }

  /** Promote each member's effective musical values independently, including track exceptions. */
  copyPartSettingsToGlobals(scope: LocalPartSettingsScope): PartWorkflowResult {
    const candidate = this.buildPartSettingsGlobals(scope);
    return candidate ? candidate === this.state() ? 'unchanged' : this.commitPartWorkflow(candidate) : 'invalid';
  }

  partSettingsGlobalsWouldChange(scope: LocalPartSettingsScope): boolean {
    const candidate = this.buildPartSettingsGlobals(scope);
    return !!candidate && candidate !== this.state();
  }

  private buildPartSettingsGlobals(scope: LocalPartSettingsScope): CompositionProject | undefined {
    const current = this.state(), section = current.arrangement.find(item => item.id === scope.sectionId);
    const members = current.tracks.filter(track => scope.kind === 'section-role' ? track.role === scope.role : track.id === scope.trackId);
    if (!section || !members.length) return undefined;
    const replacements = new Map(members.map(track => {
      const values = settingsValues(resolvePartTrack(track, section, current.melodyGlobal).generatorSettings, track.role);
      if (partPatchesEqual(settingsValues(track.generatorSettings, track.role), values)) return [track.id, track] as const;
      const settings = copyPartSettings({ ...track.generatorSettings, ...values });
      return [track.id, partPatchesEqual(track.generatorSettings, settings) ? track : { ...track, generatorSettings: settings } as CompositionTrack];
    }));
    if (members.every(track => replacements.get(track.id) === track)) return current;
    return { ...current, tracks: current.tracks.map(track => replacements.get(track.id) ?? track) };
  }

  /** Merge only the chosen musical layer, or snapshot effective values into each destination track. */
  applyPartSettingsToSections(scope: LocalPartSettingsScope, sectionIds: readonly string[],
    mode: 'overrides' | 'effective'): PartWorkflowResult {
    return this.copyLocalPartSettings(scope, sectionIds, mode, false);
  }

  /** Read-only settings preview. No generation, project decoding, persistence or history work. */
  partSettingsCopyWouldChange(scope: LocalPartSettingsScope, sectionIds: readonly string[],
    mode: 'overrides' | 'effective'): boolean {
    const candidate = this.buildLocalPartSettingsCopy(scope, sectionIds, mode, false);
    return !!candidate && candidate !== this.state();
  }

  /** Explicit whole-cell reset, including snapshots/exceptions for its current member tracks. */
  resetCellPartSettings(scope: Extract<LocalPartSettingsScope, { kind: 'section-role' }>): PartWorkflowResult {
    const current = this.state();
    const source = current.arrangement.find((section) => section.id === scope.sectionId);
    if (!source || !ARRANGED_PARTS.includes(scope.role)) return 'invalid';
    const tracks = current.tracks.filter((track) => track.role === scope.role);
    if (!Object.keys(source.rolePartOverrides?.[scope.role] ?? {}).length
      && !tracks.some((track) => Object.keys(source.trackPartOverrides?.[track.id] ?? {}).length)) return 'unchanged';
    const rolePartOverrides = { ...source.rolePartOverrides };
    const trackPartOverrides = { ...source.trackPartOverrides };
    delete rolePartOverrides[scope.role];
    for (const track of tracks) delete trackPartOverrides[track.id];
    return this.commitPartWorkflow({ ...current, arrangement: current.arrangement.map((section) => section === source
      ? { ...section, rolePartOverrides, trackPartOverrides } : section) });
  }

  private copyLocalPartSettings(scope: LocalPartSettingsScope, sectionIds: readonly string[],
    mode: 'overrides' | 'effective', includeSource: boolean): PartWorkflowResult {
    const candidate = this.buildLocalPartSettingsCopy(scope, sectionIds, mode, includeSource);
    return candidate ? candidate === this.state() ? 'unchanged' : this.commitPartWorkflow(candidate) : 'invalid';
  }

  private buildLocalPartSettingsCopy(scope: LocalPartSettingsScope, sectionIds: readonly string[],
    mode: 'overrides' | 'effective', includeSource: boolean): CompositionProject | undefined {
    const current = this.state();
    const source = current.arrangement.find((section) => section.id === scope.sectionId);
    const tracks = current.tracks.filter((track) => scope.kind === 'section-role'
      ? track.role === scope.role : track.id === scope.trackId);
    if (!source || !tracks.length || !PART_CONTROLS[tracks[0].role].length
      || (mode !== 'overrides' && mode !== 'effective') || !sectionIds.length
      || sectionIds.some((id) => !current.arrangement.some((section) => section.id === id))) return undefined;
    const destinations = new Set(sectionIds.filter((id) => includeSource || id !== source.id));
    const sourcePatch = scope.kind === 'section-role'
      ? source.rolePartOverrides?.[scope.role] : source.trackPartOverrides?.[scope.trackId];
    if (!destinations.size || (mode === 'overrides' && !Object.keys(sourcePatch ?? {}).length)) return current;
    // Copy existing validated values, including imported semitone values. This action doesn't select new knob values.
    const snapshots = mode === 'effective' ? tracks.map((track) => ({ id: track.id,
      patch: settingsValues(resolvePartTrack(track, source, current.melodyGlobal).generatorSettings, track.role) as PartSettingsPatch })) : [];
    let changed = false;
    const arrangement = current.arrangement.map((section) => {
      if (!destinations.has(section.id)) return section;
      if (mode === 'overrides' && scope.kind === 'section-role') {
        const previous = section.rolePartOverrides?.[scope.role];
        const patch = copyPartSettings({ ...previous, ...sourcePatch });
        if (partPatchesEqual(previous, patch)) return section;
        changed = true;
        return { ...section, rolePartOverrides: { ...section.rolePartOverrides, [scope.role]: patch } };
      }
      const trackPartOverrides = { ...section.trackPartOverrides };
      let sectionChanged = false;
      if (mode === 'overrides' && scope.kind === 'section-track') {
        const previous = trackPartOverrides[scope.trackId];
        const patch = copyPartSettings({ ...previous, ...sourcePatch });
        sectionChanged = !partPatchesEqual(previous, patch);
        trackPartOverrides[scope.trackId] = patch;
      } else {
        for (const snapshot of snapshots) {
          if (!partPatchesEqual(trackPartOverrides[snapshot.id], snapshot.patch)) sectionChanged = true;
          trackPartOverrides[snapshot.id] = copyPartSettings(snapshot.patch);
        }
      }
      if (!sectionChanged) return section;
      changed = true;
      return { ...section, trackPartOverrides };
    });
    return changed ? { ...current, arrangement } : current;
  }

  private commitPartWorkflow(next: CompositionProject, controlKey?: string): PartWorkflowResult {
    // Validate the complete candidate before committing: a conflict in one destination rejects the entire batch.
    if (!reconcileDrumKit(next, this.state())) {
      this.generationFailure.set(`A project supports at most ${MAX_DRUM_SOUNDS} distinct drum sounds. Choose a sound already in use or replace its final use.`);
      return 'invalid';
    }
    if (!isProject(next, this.sectionTypeSettings.chances(), Math.random)) return 'invalid';
    const before = this.state();
    this.commit(next, controlKey);
    if (this.generationFailure()) return 'invalid';
    return before === this.state() ? 'unchanged' : 'changed';
  }

  importProjectJson(json: string): boolean {
    let value: unknown;
    try {
      value = JSON.parse(json);
      const project = decodeProject(value, this.sectionTypeSettings.chances(), Math.random);
      if (!project) {
        this.warnProjectLoadFailure('imported JSON', value);
        return false;
      }
      // Reject unusable musical states before creating or replacing a tab.
      for (const track of project.tracks) {
        phraseForTrack(project, track);
        layOutTrackPhrase(project, track, true);
      }
      const tab: ProjectTab = { id: this.uiPreferences.replaceCurrentProjectTab()
        ? this.activeProjectTabId() : globalThis.crypto.randomUUID(), project, past: [], future: [], automaticSeed: BigInt(project.seed) === 0n };
      if (!this.uiPreferences.replaceCurrentProjectTab()) this.tabs.update(tabs => [...tabs, tab]);
      this.activateTab(tab);
      return true;
    } catch (error) {
      console.warn('Could not load project from imported JSON.', error);
      return false;
    }
  }

  randomizeSeed(): void {
    this.updateSettings({ seed: this.randomCompositionSeed() });
  }

  private randomCompositionSeed(): string {
    const values = new Int32Array(1);
    globalThis.crypto.getRandomValues(values);
    return String(values[0] || 1);
  }

  updateSettings(patch: Partial<Pick<CompositionProject, 'humanization' | 'midiPadding' | 'name' | 'key' | 'scale' | 'transposeSemitones' | 'tempoBpm' | 'seed' | 'randomizeTempoOnCompose' | 'randomizeHarmonyOnCompose' | 'randomizeTransposeOnCompose' | 'randomizeArrangementOnCompose' | 'arrangementPreview' | 'arrangementManual' | 'arrangementVariationPolicy'>>): void {
    if (patch.scale !== undefined && !isScaleMode(patch.scale)) return;
    if (patch.transposeSemitones !== undefined
        && (!Number.isInteger(patch.transposeSemitones) || patch.transposeSemitones < -24 || patch.transposeSemitones > 24)) return;
    if (patch.seed !== undefined) {
      if (!/^-?\d+$/.test(patch.seed) || BigInt(patch.seed) < -(1n << 63n) || BigInt(patch.seed) > (1n << 63n) - 1n) return;
      const automatic = BigInt(patch.seed) === 0n;
      this.automaticSeed.set(automatic);
      this.persist();
      if (automatic) {
        const { seed, ...settings } = patch;
        if (Object.keys(settings).length) this.updateSettings(settings);
        return;
      }
    }
    const current = this.state();
    const next = followHarmonyMode(current, { ...current, ...patch }, this.uiPreferences.harmonyFollowsMode());
    this.commit(this.uiPreferences.manualPhrasesFollowMode() ? followManualPhraseMode(current, next) : next,
      this.controlHistoryKey(['project'], patch));
  }

  updateRoleGeneratorSettings(role: ArrangedPart, patch: PartSettingsPatch): void {
    const current = this.state();
    const controlKey = this.controlHistoryKey(['part', 'global-role', role], patch);
    if (role === 'drums' && 'pitch' in patch) return;
    if (!current.tracks.some((track) => track.role === role)) return;
    const tracks = current.tracks.map(track => track.role === role
      ? { ...track, generatorSettings: { ...track.generatorSettings, ...patch } } as CompositionTrack : track);
    this.commit({ ...current, tracks }, controlKey);
  }

  /** Group/global audition gestures persist without entering editing history. */
  toggleTrackMix(trackIds: readonly string[], setting: 'muted' | 'solo', exclusive = false): void {
    const current = this.state();
    const ids = new Set(trackIds);
    const selected = current.tracks.filter(track => ids.has(track.id));
    if (!selected.length) return;
    const enabled = exclusive || !selected.some(track => track.mix[setting]);
    const opposite = setting === 'solo' ? 'muted' : 'solo';
    const tracks = current.tracks.map(track => {
      const mix = ids.has(track.id)
        ? { ...track.mix, [setting]: enabled, ...(enabled ? { [opposite]: false } : {}) }
        : exclusive && setting === 'solo' ? { ...track.mix, solo: false } : track.mix;
      return mix.muted === track.mix.muted && mix.solo === track.mix.solo ? track : { ...track, mix };
    });
    if (tracks.every((track, index) => track === current.tracks[index])) return;
    this.commit({ ...current, tracks });
  }

  updateEditedPhrase(part: ArrangedPart, notes: readonly PhraseNote[]): void {
    if (!ARRANGED_PARTS.includes(part) || !isPhrase(notes)) {
      return;
    }
    const current = this.state();
    const editedPhrase = notes.map((note) => ({ ...note }));
    this.commit({
      ...current,
      editedPhrases: { ...current.editedPhrases, [part]: editedPhrase },
      tracks: this.updatePrimaryTrack(current.tracks, part, { editedPhrase, phraseEdits: undefined }),
    });
  }

  clearEditedPhrase(part: ArrangedPart): void {
    if (!this.state().tracks.some(track => track.role === part && hasManualPhrase(track))) {
      return;
    }
    const current = this.state();
    const editedPhrases = { ...current.editedPhrases };
    delete editedPhrases[part];
    this.commit({ ...current, editedPhrases, tracks: this.updatePrimaryTrack(current.tracks, part, { editedPhrase: undefined, phraseEdits: undefined }) });
  }

  loadTrackPreset(role: ArrangedPart, tracks: unknown, replace: boolean): PartWorkflowResult {
    const candidate = loadTrackPreset(this.state(), role, tracks, replace,
      (added, section) => generateTrackPresence(added, this.sectionTypeSettings.chances()[section.type]));
    return candidate ? this.commitPartWorkflow(candidate) : 'invalid';
  }

  addTrack(role: ArrangedPart, scorePoint?: { sectionId: string; midi: number; scope?: 'section' | 'global' }): string | undefined {
    const current = this.state();
    if (!ARRANGED_PARTS.includes(role) || current.tracks.length >= 64) return undefined;
    if (scorePoint && (!current.arrangement.some(section => section.id === scorePoint.sectionId)
      || !Number.isInteger(scorePoint.midi) || scorePoint.midi < 0 || scorePoint.midi > 127
      || scorePoint.scope !== undefined && scorePoint.scope !== 'section' && scorePoint.scope !== 'global')) return undefined;
    const count = current.trackRerollCounts?.[role] ?? 0;
    if (scorePoint && count >= 2147483647) return undefined;
    const source = current.tracks.find((track) => track.role === role) ?? DEFAULT_TRACKS.find(track => track.role === role)!;
    const nextNumber = current.tracks.reduce((maximum, track) => {
      const match = new RegExp(`^track-${role}-(\\d+)$`).exec(track.id);
      return match ? Math.max(maximum, Number(match[1])) : maximum;
    }, 0) + 1;
    const id = `track-${role}-${nextNumber}`;
    const channel = role === 'drums' ? 10 : this.nextAvailableChannel(current.tracks);
    let track = { ...source, id, name: `${role[0].toUpperCase()}${nextNumber}`, midiChannel: channel,
      mix: { ...source.mix, muted: false, solo: false },
      generatorSettings: copyPartSettings(role === 'drums' ? DEFAULT_DRUM_SETTINGS : source.generatorSettings),
      rerollLocked: false,
      instrumentLocked: false,
      editedPhrase: role === 'drums' ? undefined : source.editedPhrase?.map((note) => ({ ...note })),
      phraseEdits: role === 'drums' ? undefined : source.phraseEdits?.map(edit => ({ ...edit, notes: edit.notes.map(note => ({ ...note })) })) } as CompositionTrack;
    if (scorePoint) {
      track = { ...track, editedPhrase: undefined, phraseEdits: undefined } as CompositionTrack;
      const members = [...current.tracks.filter(member => member.role === role), track];
      const seed = this.trackRerollSeed(current, role, count);
      const drumContext = { onlyAdd: true, totalTracks: members.length, reservedPitches: projectDrumPitches(current),
        remaining: current.tracks.filter(member => member.role === 'drums').map(member => member.generatorSettings as DrumSettings) };
      const instruments = rerollInstruments(seed, role, current.instrumentGenerationPolicies?.[role] ?? DEFAULT_INSTRUMENT_POLICIES[role], [track], false, drumContext);
      const settings = rerollRoleSettings(seed, role, current.trackGenerationPolicies?.[role] ?? DEFAULT_TRACK_POLICIES[role], instruments, [members.length], drumContext);
      track = { ...instruments[0], generatorSettings: { ...settings[0], ...(role === 'drums' ? {} : { transpose: 0 }) } } as CompositionTrack;
    }
    let arrangement = current.arrangement.map((section) => ({
      ...section,
      trackPresence: { ...section.trackPresence, ...(scorePoint ? { [id]: scorePoint.scope === 'global' || section.id === scorePoint.sectionId }
        : generateTrackPresence([track], this.sectionTypeSettings.chances()[section.type])) },
      ...(scorePoint && role !== 'drums' && section.id === scorePoint.sectionId && section.rolePartOverrides?.[role]?.transpose !== undefined
        ? { trackPartOverrides: { ...section.trackPartOverrides, [id]: { transpose: 0 } } } : {}),
    }));
    if (scorePoint && role !== 'drums') {
      const candidate = { ...current, tracks: [...current.tracks, track], arrangement };
      let pitches: number[];
      try {
        pitches = layOutTrackPhrase(candidate, track, false, true).filter(note => note.sectionId === scorePoint.sectionId)
          .map(note => note.midi).sort((a, b) => a - b);
      } catch (error) {
        this.generationFailure.set(error instanceof Error ? error.message : 'The requested music could not be generated.');
        return undefined;
      }
      // Preserve the role's contour and choose the octave nearest the clicked pitch range.
      const middle = pitches.length ? pitches[Math.floor(pitches.length / 2)] : role === 'bass' ? 36 : 60;
      const transpose = Math.max(-36, Math.min(36, (Math.floor(scorePoint.midi / 12) - Math.floor(middle / 12)) * 12));
      track = { ...track, generatorSettings: { ...track.generatorSettings, transpose } } as CompositionTrack;
      arrangement = arrangement.map(section => section.trackPartOverrides?.[id]
        ? { ...section, trackPartOverrides: { ...section.trackPartOverrides, [id]: { transpose } } } : section);
    }
    this.commit({ ...current, tracks: [...current.tracks, track], arrangement,
      ...(scorePoint ? { trackRerollCounts: { ...current.trackRerollCounts, [role]: count + 1 } } : {}) });
    return this.state().tracks.some((candidate) => candidate.id === id) ? id : undefined;
  }

  duplicateTrack(trackId: string, options?: TrackDuplicationOptions): string | undefined {
    const current = this.state();
    if (current.tracks.length >= 64) return undefined;
    const sourceIndex = current.tracks.findIndex((track) => track.id === trackId);
    if (sourceIndex < 0) return undefined;
    const source = current.tracks[sourceIndex];
    const id = this.nextTrackId(current.tracks, source.role);
    const channel = source.role === 'drums' ? 10 : this.nextAvailableChannel(current.tracks);
    const duplicate = { ...source, id, name: `${source.name} copy`, midiChannel: channel,
      mix: { ...source.mix, muted: false, solo: false },
      generatorSettings: copyPartSettings(source.generatorSettings),
      editedPhrase: !options || options.manualNotes ? source.editedPhrase?.map((note) => ({ ...note })) : undefined,
      phraseEdits: !options || options.manualNotes ? source.phraseEdits?.map(edit => ({ ...edit, notes: edit.notes.map(note => ({ ...note })) })) : undefined } as CompositionTrack;
    const tracks = [...current.tracks];
    tracks.splice(options ? tracks.length : sourceIndex + 1, 0, duplicate);
    this.commit({ ...current, tracks, arrangement: current.arrangement.map((section) => ({
      ...section,
      trackPresence: { ...section.trackPresence, [id]: (!options || options.presences) && (section.trackPresence[source.id] ?? false) },
      ...((!options || options.manualNotes) && section.generatedTrackIds?.includes(source.id) ? { generatedTrackIds: [...section.generatedTrackIds, id] } : {}),
      ...((!options || options.manualNotes) && section.trackPhrases?.[source.id] !== undefined ? { trackPhrases: { ...section.trackPhrases, [id]: section.trackPhrases[source.id].map(note => ({ ...note })) } } : {}),
      ...(options?.presences && options.variations && section.variations ? { variations: { ...section.variations,
        choices: [...section.variations.choices, ...section.variations.choices.filter(choice => choice.trackId === source.id)
          .map(choice => ({ ...structuredClone(choice), trackId: id }))] } } : {}),
      trackPartOverrides: (!options || options.presences && options.overrides) && section.trackPartOverrides?.[source.id]
        ? { ...section.trackPartOverrides, [id]: copyPartSettings(section.trackPartOverrides[source.id]) } : section.trackPartOverrides,
    })) });
    return this.state().tracks.some(track => track.id === id) ? id : undefined;
  }

  updateTrack(trackId: string, patch: { name?: string; midiChannel?: number; mix?: Partial<MixChannelSettings> }): void {
    const current = this.state();
    const index = current.tracks.findIndex((track) => track.id === trackId);
    if (index < 0 || (patch.name !== undefined && (!patch.name.trim() || patch.name.length > 48))
        || (patch.midiChannel !== undefined && (!Number.isInteger(patch.midiChannel) || patch.midiChannel < 1 || patch.midiChannel > 16))
        || (patch.mix?.program !== undefined && (!Number.isInteger(patch.mix.program) || patch.mix.program < 0 || patch.mix.program > 127))
        || (patch.mix?.volumePercent !== undefined && !validVolumePercent(patch.mix.volumePercent))
        || (patch.mix?.panPercent !== undefined && (!Number.isInteger(patch.mix.panPercent) || patch.mix.panPercent < -100 || patch.mix.panPercent > 100))
        || (patch.mix?.muted !== undefined && typeof patch.mix.muted !== 'boolean')
        || (patch.mix?.solo !== undefined && typeof patch.mix.solo !== 'boolean')) return;
    const tracks = [...current.tracks];
    const track = tracks[index];
    if (track.role === 'drums' && ((patch.midiChannel !== undefined && patch.midiChannel !== 10)
      || patch.mix?.program !== undefined)) return;
    tracks[index] = { ...track, ...patch, name: patch.name?.trim() ?? track.name,
      mix: patch.mix ? { ...track.mix, ...patch.mix,
        ...(patch.mix.volumePercent !== undefined ? { volumePercent: normalizeVolumePercent(patch.mix.volumePercent) } : {}),
        ...(patch.mix.solo === true ? { muted: false } : patch.mix.muted === true ? { solo: false } : {}) } : track.mix };
    if (patch.mix?.program !== undefined && patch.mix.program !== track.mix.program) {
      delete (tracks[index] as { instrumentPool?: string }).instrumentPool;
    }
    this.commit({ ...current, tracks }, Object.keys(patch).length === 1
      ? this.controlHistoryKey(['track', trackId, ...(patch.mix ? ['mix'] : [])], patch.mix ?? patch) : undefined);
  }

  updateTrackGeneratorSettings(trackId: string, patch: PartSettingsPatch): void {
    const current = this.state();
    const index = current.tracks.findIndex((track) => track.id === trackId);
    if (index < 0) return;
    const tracks = [...current.tracks];
    const track = tracks[index];
    const generatorSettings = { ...track.generatorSettings, ...patch };
    const validSettings = track.role === 'melody' ? decodeMelody(generatorSettings) !== undefined
      : track.role === 'bass' ? decodeBass(generatorSettings) !== undefined
      : track.role === 'chords' ? decodeChords(generatorSettings) !== undefined
        : track.role === 'arpeggio' ? decodeArpeggio(generatorSettings) !== undefined
          : decodeDrums(generatorSettings) !== undefined;
    if (!validSettings) return;
    tracks[index] = { ...track, generatorSettings } as CompositionTrack;
    this.commit({ ...current, tracks }, this.controlHistoryKey(['part', 'global-track', trackId], patch));
  }

  copyArrangementPhrase(request: ArrangementPhraseCopy): void {
    const current = this.state();
    const next = copyArrangementPhrase(current, request);
    if (next !== current) this.commit(next);
  }

  updateSectionTrackPhrase(sectionId: string, trackId: string, notes: readonly PhraseNote[]): boolean {
    const current = this.state();
    if (!current.tracks.some(track => track.id === trackId) || !current.arrangement.some(section => section.id === sectionId) || !isPhrase(notes, 2112, -64)) return false;
    this.commit({ ...current, arrangement: current.arrangement.map(section => section.id === sectionId
      ? { ...section, trackPhrases: { ...section.trackPhrases, [trackId]: notes.map(note => ({ ...note })) } } : section) });
    return true;
  }

  /** Apply a multi-track phrase draft atomically, including mixed global and section destinations. */
  applyPhraseEdits(edits: readonly { trackId: string; sectionId?: string; notes: readonly PhraseNote[] | null;
    range?: Omit<PhraseRangeEdit, 'notes'> }[]): boolean {
    const current = this.state();
    if (!edits.length || edits.some(edit => !current.tracks.some(track => track.id === edit.trackId)
      || edit.sectionId !== undefined && !current.arrangement.some(section => section.id === edit.sectionId)
      || edit.range !== undefined && (edit.sectionId !== undefined || edit.notes === null
        || !isPhraseRangeEdits([{ ...edit.range, notes: edit.notes }]))
      || edit.notes !== null && !edit.range && !isPhrase(edit.notes, edit.sectionId ? 2112 : undefined, edit.sectionId ? -64 : undefined))) return false;
    let tracks = [...current.tracks];
    let arrangement = [...current.arrangement];
    const editedPhrases = { ...current.editedPhrases };
    for (const edit of edits) {
      if (edit.sectionId) {
        arrangement = arrangement.map(section => {
          if (section.id !== edit.sectionId) return section;
          const trackPhrases = { ...section.trackPhrases };
          if (edit.notes === null) delete trackPhrases[edit.trackId];
          else trackPhrases[edit.trackId] = edit.notes.map(note => ({ ...note }));
          if (edit.notes === null) {
            const track = tracks.find(track => track.id === edit.trackId)!;
            const patch = { ...section.rolePartOverrides?.[track.role], ...section.trackPartOverrides?.[track.id] };
            return { ...section, trackPhrases, trackPartOverrides: { ...section.trackPartOverrides,
              [track.id]: transferManualParameters(patch, track.role, resolvePartTrack(track, section).generatorSettings) } };
          }
          return { ...section, trackPhrases };
        });
      } else {
        tracks = tracks.map(track => track.id !== edit.trackId ? track : edit.range && edit.notes !== null
          ? { ...track, phraseEdits: appendPhraseRangeEdit(track.phraseEdits, { ...edit.range, notes: edit.notes }) } as CompositionTrack
          : { ...track, editedPhrase: edit.notes?.map(note => ({ ...note })), phraseEdits: undefined,
              generatorSettings: edit.notes === null ? transferManualParameters(track.generatorSettings, track.role) : track.generatorSettings } as CompositionTrack);
        const track = tracks.find(track => track.id === edit.trackId)!;
        if (edit.notes === null) arrangement = [...this.transferRestoredSectionParameters(arrangement, current.tracks.find(source => source.id === edit.trackId)!)];
        if (tracks.find(candidate => candidate.role === track.role)?.id === track.id) {
          if (track.editedPhrase === undefined) delete editedPhrases[track.role];
          else editedPhrases[track.role] = track.editedPhrase;
        }
      }
    }
    const next = { ...current, tracks, arrangement, editedPhrases };
    if (!isProject(next, this.sectionTypeSettings.chances(), Math.random)) return false;
    this.commit(next);
    return true;
  }

  updateTrackPhrase(trackId: string, notes: readonly PhraseNote[]): boolean {
    const current = this.state();
    const index = current.tracks.findIndex((track) => track.id === trackId);
    if (index < 0 || !isPhrase(notes)) return false;
    const tracks = [...current.tracks];
    tracks[index] = { ...tracks[index], editedPhrase: notes.map((note) => ({ ...note })), phraseEdits: undefined } as CompositionTrack;
    const isPrimary = current.tracks.find((candidate) => candidate.role === tracks[index].role)?.id === trackId;
    const editedPhrases = isPrimary ? { ...current.editedPhrases, [tracks[index].role]: tracks[index].editedPhrase } : current.editedPhrases;
    this.commit({ ...current, tracks, editedPhrases });
    return true;
  }

  clearTrackPhrase(trackId: string): void {
    const current = this.state();
    const track = current.tracks.find((candidate) => candidate.id === trackId);
    if (!track || !hasManualPhrase(track)) return;
    const tracks = current.tracks.map((candidate) => candidate.id === trackId
      ? { ...candidate, editedPhrase: undefined, phraseEdits: undefined, generatorSettings: transferManualParameters(candidate.generatorSettings, candidate.role) } as CompositionTrack : candidate);
    const isPrimary = current.tracks.find((candidate) => candidate.role === track.role)?.id === trackId;
    const editedPhrases = { ...current.editedPhrases };
    if (isPrimary) delete editedPhrases[track.role];
    const arrangement = this.transferRestoredSectionParameters(current.arrangement, track);
    this.commit({ ...current, tracks, editedPhrases, arrangement });
  }

  private transferRestoredSectionParameters(arrangement: CompositionProject['arrangement'], track: CompositionTrack): CompositionProject['arrangement'] {
    return arrangement.map(section => {
      if (section.trackPhrases?.[track.id] !== undefined) return section;
      const patch = { ...section.rolePartOverrides?.[track.role], ...section.trackPartOverrides?.[track.id] };
      const effective = resolvePartTrack(track, section).generatorSettings;
      if (!Object.keys(effective).some(key => key in MANUAL_PARAMETER_TARGETS)) return section;
      const converted = transferManualParameters(effective, track.role) as unknown as Record<string, unknown>;
      const inherited = transferManualParameters(track.generatorSettings, track.role) as unknown as Record<string, unknown>;
      const next = Object.fromEntries(Object.entries(patch).filter(([key]) => !(key in MANUAL_PARAMETER_TARGETS)));
      for (const [manual, target] of Object.entries(MANUAL_PARAMETER_TARGETS)) {
        if ((effective as unknown as Record<string, unknown>)[manual] === undefined) continue;
        const destination = manual === 'manualLength' && track.role === 'chords' ? 'noteLengthPercent' : target;
        if (converted[destination] !== (next[destination] ?? inherited[destination])) next[destination] = converted[destination];
      }
      return { ...section, trackPartOverrides: { ...section.trackPartOverrides,
        [track.id]: next as PartSettingsPatch } };
    });
  }

  private trackMoveTargetIndex(trackId: string, offset: -1 | 1): number {
    const tracks = this.state().tracks;
    const track = tracks.find(candidate => candidate.id === trackId);
    if (!track) return -1;
    const members = tracks.filter(candidate => candidate.role === track.role);
    const target = members[members.indexOf(track) + offset];
    return target ? tracks.indexOf(target) : -1;
  }

  canMoveTrack(trackId: string, offset: -1 | 1): boolean {
    return this.trackMoveTargetIndex(trackId, offset) >= 0;
  }

  reorderTrack(trackId: string, offset: -1 | 1): void {
    const current = this.state();
    const index = current.tracks.findIndex((track) => track.id === trackId);
    const target = this.trackMoveTargetIndex(trackId, offset);
    if (target < 0) return;
    const tracks = [...current.tracks];
    [tracks[index], tracks[target]] = [tracks[target], tracks[index]];
    this.commit({ ...current, tracks });
  }

  removeTrack(trackId: string): void {
    const current = this.state();
    const track = current.tracks.find((candidate) => candidate.id === trackId);
    if (!track || current.tracks.length <= 1) return;
    const arrangement = current.arrangement.map((section) => {
      const trackPresence = { ...section.trackPresence };
      delete trackPresence[trackId];
      const trackPartOverrides = { ...section.trackPartOverrides };
      delete trackPartOverrides[trackId];
      const trackPhrases = { ...section.trackPhrases };
      delete trackPhrases[trackId];
      return { ...section, trackPresence, trackPartOverrides,
        ...(section.variations ? { variations: pruneVariations(section.variations, new Set([trackId])) } : {}),
        ...(section.trackPhrases !== undefined ? { trackPhrases } : {}),
        ...(section.generatedTrackIds ? { generatedTrackIds: section.generatedTrackIds.filter(id => id !== trackId) } : {}) };
    });
    this.commit(fitVariationCapacity({ ...current, tracks: current.tracks.filter((candidate) => candidate.id !== trackId), arrangement }));
  }

  addSection(type: SectionType = 'VERSE1', afterIndex = this.state().arrangement.length - 1): void {
    const next = arrangementCommands.addSection(this.state(), type, afterIndex, this.sectionTypeSettings.chances(), Math.random,
      this.uiPreferences.generateArrangementVariations(), this.uiPreferences.variationRerolls());
    if (next) this.commit(next);
  }

  duplicateSection(index: number): void {
    const next = arrangementCommands.duplicateSection(this.state(), index);
    if (next) this.commit(next);
  }

  removeSection(index: number): void {
    const next = arrangementCommands.removeSection(this.state(), index);
    if (next) this.commit(next);
  }

  moveSection(index: number, offset: -1 | 1): void {
    const next = arrangementCommands.moveSection(this.state(), index, offset);
    if (next) this.commit(next);
  }

  updateSection(index: number, patch: Partial<Pick<ArrangementSection, 'type' | 'measures'>>): void {
    const current = this.state();
    const next = arrangementCommands.updateSection(current, index, patch);
    if (next) this.commit(next, this.controlHistoryKey(['section', current.arrangement[index].id], patch));
  }

  setSectionChordDegree(sectionIndex: number, measureIndex: number, degree: number): void {
    const next = arrangementCommands.setSectionChordDegree(this.state(), sectionIndex, measureIndex, degree);
    if (next) this.commit(next);
  }

  clearSectionChordOverrides(sectionIndex: number): void {
    const next = arrangementCommands.clearSectionChordOverrides(this.state(), sectionIndex);
    if (next) this.commit(next);
  }

  toggleSectionRolePresence(sectionId: string, role: ArrangedPart): void {
    const next = arrangementCommands.toggleSectionRolePresence(this.state(), sectionId, role);
    if (next) this.commit(next);
  }

  setSectionTrackPresence(index: number, trackId: string, present: boolean): void {
    const next = arrangementCommands.setSectionTrackPresence(this.state(), index, trackId, present);
    if (next) this.commit(next);
  }

  clearTrackPresence(trackId: string, sectionId?: string): void {
    const current = this.state();
    if (!current.tracks.some(track => track.id === trackId)
      || sectionId !== undefined && !current.arrangement.some(section => section.id === sectionId)) return;
    this.commit({ ...current, arrangement: current.arrangement.map(section => sectionId === undefined || section.id === sectionId
      ? { ...section, trackPresence: { ...section.trackPresence, [trackId]: false } } : section) });
  }

  /** Explicit toolbar action replaces the whole arrangement independently of Compose policy. */
  randomizeArrangement(): void {
    const seed = BigInt(Math.floor(Math.random() * 0x1000000000000));
    this.commit(arrangementCommands.randomizeArrangement(this.state(), this.sectionTypeSettings.chances(), seed,
      this.uiPreferences.generateArrangementVariations(), this.uiPreferences.variationRerolls()));
  }

  randomizeArrangementPresence(sectionId?: string): void {
    const next = arrangementCommands.randomizeArrangementPresence(this.state(), sectionId, this.sectionTypeSettings.chances(), Math.random);
    if (next) this.commit(next);
  }

  randomizeArrangementVariations(sectionId?: string, policy?: VariationPolicy, seed?: bigint): PartWorkflowResult {
    const next = arrangementCommands.randomizeArrangementVariations(this.state(),
      seed ?? BigInt(Math.floor(Math.random() * 0x1000000000000)), sectionId, policy, this.uiPreferences.variationRerolls());
    return next ? this.commitPartWorkflow(next) : 'invalid';
  }

  setArrangementVariationEnabled(sectionId: string, trackId: string, enabled: boolean): PartWorkflowResult {
    const current = this.state(), section = current.arrangement.find(s => s.id === sectionId);
    if (typeof enabled !== 'boolean' || !section?.variations?.choices.some(c => c.trackId === trackId)) return 'invalid';
    return this.commitPartWorkflow({ ...current, arrangement: current.arrangement.map(s => s !== section ? s : {
      ...s, variations: { ...section.variations!, choices: section.variations!.choices.map(c => c.trackId === trackId ? { ...c, enabled } : c) },
    }) });
  }

  resetArrangementVariations(sectionId?: string): PartWorkflowResult {
    const current = this.state();
    if (sectionId !== undefined && !current.arrangement.some(s => s.id === sectionId)) return 'invalid';
    return this.commitPartWorkflow({ ...current, arrangement: current.arrangement.map(s => sectionId !== undefined && s.id !== sectionId
      ? s : { ...s, variations: undefined }) });
  }

  applyArrangementVariationDraft(expected: CompositionProject, draft: VariationDraft, options: VariationDraftOptions = {}): PartWorkflowResult {
    const current = this.state();
    if (current !== expected || !draft || typeof draft !== 'object' || Array.isArray(draft)
      || Object.keys(draft).some(id => !current.arrangement.some(section => section.id === id))) return 'invalid';
    if (!options || typeof options !== 'object' || Array.isArray(options)
      || Object.keys(options).some(key => key !== 'presence' && key !== 'policy')
      || options.policy !== undefined && !validVariationPolicy(options.policy, current.tracks.length)) return 'invalid';
    const presence = options.presence;
    if (presence !== undefined && (!presence || typeof presence !== 'object' || Array.isArray(presence)
      || Object.keys(presence).length !== current.arrangement.length
      || current.arrangement.some(section => {
        const flags = presence[section.id];
        return !flags || typeof flags !== 'object' || Array.isArray(flags)
          || Object.keys(flags).length !== current.tracks.length || current.tracks.some(track => typeof flags[track.id] !== 'boolean');
      }))) return 'invalid';
    return this.commitPartWorkflow({ ...current,
      ...(options.policy ? { arrangementVariationPolicy: structuredClone(options.policy) } : {}),
      arrangement: current.arrangement.map(section => ({ ...section,
        ...(Object.hasOwn(draft, section.id) ? { variations: draft[section.id] === undefined ? undefined : structuredClone(draft[section.id]) } : {}),
        ...(presence ? { trackPresence: { ...presence[section.id] } } : {}),
      })) });
  }

  setChordDegree(index: number, degree: number): void {
    const chords = getDiatonicChords(this.state().key, this.state().scale);
    if (!Number.isInteger(index) || index < 0 || index >= this.state().progression.length
        || !chords.some((chord) => chord.degree === degree)) {
      return;
    }

    const progression = [...this.state().progression];
    progression[index] = degree;
    this.commit({ ...this.state(), progression,
      progressionChords: this.state().progressionChords?.map((chord, i) => i === index ? null : chord) });
  }

  setProgressionChord(index: number, chord: ProgressionChord | null): void {
    const current = this.state();
    if (!Number.isInteger(index) || index < 0 || index >= current.progression.length) return;
    const custom = current.progression.map((_, i) => i === index ? chord : current.progressionChords?.[i] ?? null);
    if (!validProgressionChords(custom, current.progression.length)) return;
    this.commit({ ...current, progressionChords: custom.some(Boolean) ? custom.map(chord => chord ? { ...chord } : null) : undefined });
  }

  moveProgressionChord(from: number, to: number): void {
    const current = this.state();
    if (![from,to].every(index => Number.isInteger(index) && index >= 0 && index < current.progression.length) || from === to) return;
    const reorder = <T>(values: readonly T[]): T[] => { const next = [...values]; next.splice(to, 0, next.splice(from, 1)[0]); return next; };
    this.commit({ ...current, progression: reorder(current.progression),
      progressionChords: current.progressionChords ? reorder(current.progressionChords) : undefined });
  }

  setProgression(progression: readonly number[]): void {
    const chords = getDiatonicChords(this.state().key, this.state().scale);
    if (progression.length < 1 || progression.length > 32
        || progression.some((degree) => !chords.some((chord) => chord.degree === degree))) {
      return;
    }
    this.commit({ ...this.state(), progression: [...progression], progressionChords: undefined });
  }

  generateProgression(sectionId?: string, allSections = false, useProjectSeed = false): void {
    const project = this.state(), section = sectionId ? project.arrangement.find(s => s.id === sectionId) : undefined;
    if (sectionId && !section) return;
    const seed = BigInt(useProjectSeed ? project.seed : this.randomCompositionSeed());
    const context = sectionProject(project, section);
    const length = section?.customChords?.length ?? section?.chordDegrees?.length ?? project.progression.length;
    const generated = generateHarmony(seed, length, context.key, context.scale, project.harmonySettings, project.melodyGlobal);
    if (allSections) this.editAllSectionMusic(item => {
      const local = sectionProject(project, item);
      const count = item.customChords?.length ?? item.chordDegrees?.length ?? project.progression.length;
      const harmony = generateHarmony(seed, count, local.key, local.scale, project.harmonySettings, project.melodyGlobal);
      return { ...item, chordDegrees: harmony.degrees, customChords: harmony.chords };
    });
    else if (section) this.setSectionMusicalState(section.id, { chordDegrees: generated.degrees, customChords: generated.chords });
    else this.commit({ ...project, progression: generated.degrees, progressionChords: generated.chords });
  }

  addChord(): void {
    if (this.state().progression.length >= 32) {
      return;
    }
    const progression = [...this.state().progression, 1];
    this.commit({ ...this.state(), progression,
      progressionChords: this.state().progressionChords ? [...this.state().progressionChords!, null] : undefined });
  }

  removeChord(index: number): void {
    const progression = this.state().progression.filter((_, chordIndex) => chordIndex !== index);
    if (progression.length > 0 && progression.length !== this.state().progression.length) {
      this.commit({ ...this.state(), progression,
        progressionChords: this.state().progressionChords?.filter((_, chordIndex) => chordIndex !== index) });
    }
  }

  resetProgression(): void {
    this.commit({ ...this.state(), progression: [...DEFAULT_PROJECT.progression], progressionChords: undefined });
  }

  undo(): void {
    this.lastControlEdit = null;
    const previous = this.past.pop();
    if (!previous) {
      return;
    }
    this.future.push(this.state());
    const restored = preserveAudition(previous, this.state());
    this.state.set(restored);
    this.persist();
    this.refreshHistoryAvailability();
  }

  redo(): void {
    this.lastControlEdit = null;
    const next = this.future.pop();
    if (!next) {
      return;
    }
    this.past.push(this.state());
    const restored = preserveAudition(next, this.state());
    this.state.set(restored);
    this.persist();
    this.refreshHistoryAvailability();
  }

  private commit(next: CompositionProject, controlKey?: string): void {
    const current = this.state();
    const kit = reconcileDrumKit(next, current);
    if (!kit) {
      this.generationFailure.set(`A project supports at most ${MAX_DRUM_SOUNDS} distinct drum sounds. Replace an unused sound or choose a sound already in use.`);
      return;
    }
    next = kit;
    if (!isProject(next, this.sectionTypeSettings.chances(), Math.random) || JSON.stringify(current) === JSON.stringify(next)) {
      return;
    }
    // Exercise both the global preview and section/dependency pipeline before
    // publishing signals, changing history or persisting an unusable candidate.
    try {
      for (const track of samePhraseInputs(current, next) ? [] : next.tracks) {
        phraseForTrack(next, track);
        layOutTrackPhrase(next, track, true);
      }
    } catch (error) {
      this.generationFailure.set(error instanceof Error ? error.message : 'The requested music could not be generated.');
      return;
    }
    this.generationFailure.set(null);
    if (historyContent(current) === historyContent(next)) {
      this.state.set(next);
      this.persist();
      return;
    }

    const time = performance.now();
    const compact = this.compactHistory() && controlKey !== undefined
      && this.lastControlEdit?.key === controlKey && this.past.length > 0 && this.future.length === 0
      && time - this.lastControlEdit.time < COMPACT_HISTORY_WINDOW_MS;
    if (!compact) {
      this.past.push(current);
      if (this.past.length > MAX_HISTORY) this.past.shift();
    }
    this.lastControlEdit = this.compactHistory() && controlKey !== undefined ? { key: controlKey, time } : null;
    this.future.length = 0;
    this.state.set(next);
    this.persist();
    this.refreshHistoryAvailability();
  }

  private refreshHistoryAvailability(): void {
    this.historyRevision.update(revision => revision + 1);
    this.undoAvailable.set(this.past.length > 0);
    this.redoAvailable.set(this.future.length > 0);
  }

  private updatePrimaryTrack(
    tracks: readonly CompositionTrack[],
    role: ArrangedPart,
    patch: { generatorSettings?: MelodySettings | BassSettings | ChordSettings | ArpeggioSettings | DrumSettings;
      mix?: MixChannelSettings; editedPhrase?: readonly PhraseNote[]; phraseEdits?: readonly PhraseRangeEdit[] },
  ): CompositionTrack[] {
    let updated = false;
    return tracks.map((track) => {
      if (updated || track.role !== role) return track;
      updated = true;
      const next = { ...track, ...patch } as CompositionTrack;
      if (patch.mix && patch.mix.program !== track.mix.program) delete (next as { instrumentPool?: string }).instrumentPool;
      return next;
    });
  }

  private nextTrackId(tracks: readonly CompositionTrack[], role: ArrangedPart): string {
    const used = new Set(tracks.map((track) => track.id));
    let index = 1;
    while (used.has(`track-${role}-${index}`)) index++;
    return `track-${role}-${index}`;
  }

  private nextAvailableChannel(tracks: readonly CompositionTrack[]): number {
    const used = new Set(tracks.filter((track) => track.role !== 'drums').map((track) => track.midiChannel));
    for (let channel = 1; channel <= 16; channel++) if (channel !== 10 && !used.has(channel)) return channel;
    return 1;
  }

  private loadProject(): CompositionProject {
    for (const key of [STORAGE_KEY, LEGACY_STORAGE_KEY]) {
      try {
        const raw = globalThis.sessionStorage?.getItem(key);
        if (!raw) continue;
        const value: unknown = JSON.parse(raw);
        const decoded = decodeProject(value, this.sectionTypeSettings.chances(), Math.random);
        if (decoded) {
          return decoded;
        }
        this.warnProjectLoadFailure(`session storage (${key})`, value);
      } catch (error) {
        console.warn(`Could not load project from session storage (${key}).`, error);
      }
    }
    const initial = copyDefaultProject(this.sectionTypeSettings.chances(), Math.random);
    return initial;
  }

  private warnProjectLoadFailure(source: string, value: unknown): void {
    console.warn(`Could not load project from ${source}.`, projectLoadIssues(value, this.sectionTypeSettings.chances(), Math.random));
  }

  private persist(): void {
    // Tab snapshots must follow every edit immediately, even before a save runs.
    const current = this.snapshotTab();
    this.tabs.update(tabs => tabs.map(tab => tab.id === current.id ? current : tab));
    this.persistence.schedule();
  }

  private writeWorkspace(): void {
    try {
      globalThis.sessionStorage?.setItem(STORAGE_KEY, JSON.stringify(encodeProjectStorage(this.state())));
    } catch {
      // Keep the project usable if storage is disabled or full.
    }
    if (!this.tabs || !this.activeProjectTabId()) return;
    try {
      globalThis.localStorage?.setItem(TABS_STORAGE_KEY, JSON.stringify({ activeId: this.activeProjectTabId(),
        tabs: this.tabs().map(tab => ({ ...tab, project: encodeProjectStorage(tab.project),
          past: tab.past.map(encodeProjectStorage), future: tab.future.map(encodeProjectStorage) })) }));
    } catch { /* Tabs remain usable when browser storage is disabled or full. */ }
  }
}
