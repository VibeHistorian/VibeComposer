import * as arrangementCommands from './project-arrangement-commands';
import { loadTrackPreset } from './track-presets';
import { decodeMelody, decodeBass, decodeChords, decodeArpeggio, decodeDrums } from './project-part-decoder';
import { isPhrase, isPhraseRangeEdits } from './project-track-decoder';
import { appendPhraseRangeEdit, hasManualPhrase, type PhraseRangeEdit } from '../music/phrase-range-edit';
import { decodeProject, isProject } from './project-codec';
import { formatProjectJson } from './project-json';
import { encodeProjectStorage } from './project-storage';
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
import { copyPartSettings, decodePartPatch, resolvePartTrack, settingsValues, partPatchesEqual, partValuesEqual, type PartSettingValue, PART_CONTROLS } from '../music/part-settings';
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

  goToHistory(index: number): void {
    if (!Number.isInteger(index) || index < 0 || index > this.past.length + this.future.length) return;
    const current = this.state();
    const states = [...this.past, current, ...[...this.future].reverse()];
    const next = preserveAudition(states[index], current);
    this.past.splice(0, this.past.length, ...states.slice(0, index));
    this.future.splice(0, this.future.length, ...states.slice(index + 1).reverse());
    this.lastControlEdit = null;
    this.state.set(next);
    this.persist(next);
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

  updateSectionHarmony(sectionId: string, patch: Pick<ArrangementSection, 'useMelodyHarmony' | 'swapMelody'>): PartWorkflowResult {
    if (!patch || typeof patch !== 'object' || Array.isArray(patch)
      || Object.entries(patch).some(([key,value]) => !['useMelodyHarmony','swapMelody'].includes(key) || typeof value !== 'boolean')) return 'invalid';
    const current = this.state(), section = current.arrangement.find(section => section.id === sectionId);
    if (!section) return 'invalid';
    if (Object.entries(patch).every(([key,value]) => (section[key as 'useMelodyHarmony' | 'swapMelody'] ?? false) === value)) return 'unchanged';
    this.commit({ ...current, arrangement: current.arrangement.map(item => item.id === sectionId ? { ...item, ...patch } : item) });
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
    this.commit({ ...current, arrangement: current.arrangement.map(item => item.id === sectionId ? copied : item) });
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
      { remaining: current.tracks.filter(track => track.role === 'drums' && !sources.includes(track)).map(track => track.generatorSettings as DrumSettings) });
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
    return this.commitPartWorkflow({ ...current, tracks: current.tracks.filter(track => !removed.has(track.id)),
      arrangement: current.arrangement.map(section => ({ ...section,
        trackPresence: Object.fromEntries(Object.entries(section.trackPresence).filter(([id]) => !removed.has(id))),
        ...(section.generatedTrackIds ? { generatedTrackIds: section.generatedTrackIds.filter(id => !removed.has(id)) } : {}),
        ...(section.trackPhrases !== undefined ? { trackPhrases: Object.fromEntries(Object.entries(section.trackPhrases).filter(([id]) => !removed.has(id))) } : {}),
        ...(section.trackPartOverrides !== undefined ? { trackPartOverrides: Object.fromEntries(
          Object.entries(section.trackPartOverrides).filter(([id]) => !removed.has(id))) } : {}),
      })) });
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
    const drumContext = { onlyAdd: true, totalTracks: members.length,
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
        const sources = selected.filter(track => !track.rerollLocked).map(track => resolvePartTrack(track, section));
        if (!sources.length) continue;
        const count = counts[role] ?? 0;
        if (count >= 2147483647) return 'invalid';
        const roleTracks = current.tracks.filter(track => track.role === role);
        const drumContext = { totalTracks: roleTracks.length,
          remaining: roleTracks.filter(track => !sources.some(source => source.id === track.id))
            .map(track => resolvePartTrack(track, section).generatorSettings as DrumSettings) };
        const settings = rerollRoleSettings(this.trackRerollSeed(current, role, count), role,
          current.trackGenerationPolicies?.[role] ?? DEFAULT_TRACK_POLICIES[role], sources,
          sources.map(track => roleTracks.findIndex(item => item.id === track.id) + 1), drumContext);
        sources.forEach((track, index) => {
          trackPartOverrides[track.id] = copyPartSettings(settingsValues(settings[index], role)) as PartSettingsPatch;
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
    const drumContext = { totalTracks: roleTracks.length,
      remaining: roleTracks.filter(track => !sources.includes(track)).map(track => track.generatorSettings as DrumSettings) };
    const instrumentTracks = rerollInstruments(seed, role, current.instrumentGenerationPolicies?.[role] ?? DEFAULT_INSTRUMENT_POLICIES[role], sources, false, drumContext);
    const settings = rerollRoleSettings(seed, role, policy, instrumentTracks, sources.map(track => roleTracks.indexOf(track) + 1), drumContext);
    if (sources.every((track, index) => track === instrumentTracks[index] && partPatchesEqual(track.generatorSettings, settings[index]))) return current;
    const replacements = new Map(instrumentTracks.map((track, index) => [track.id, { ...track, generatorSettings: settings[index] } as CompositionTrack]));
    const next = { ...current, tracks: current.tracks.map(track => replacements.get(track.id) ?? track),
      trackRerollCounts: { ...current.trackRerollCounts, [role]: count + 1 } };
    return next;
  }

  /** User-triggered events only. Live reload, playback and ordinary edits never run policies. */
  generateComposition(event: 'compose' | 'regenerate'): PartWorkflowResult {
    this.generationFailure.set(null);
    try {
      return this.prepareComposition(event);
    } catch (error) {
      this.generationFailure.set(error instanceof Error ? error.message : 'The requested music could not be generated.');
      return 'invalid';
    }
  }

  private prepareComposition(event: 'compose' | 'regenerate'): PartWorkflowResult {
    if (event !== 'compose' && event !== 'regenerate') return 'invalid';
    const current = this.state();
    let next = current;
    if (event === 'compose') {
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
    }
    if (event === 'compose' && next.randomizeArrangementOnCompose) {
      // Independent stream preserves track and global-setting random draw order.
      const random = new JavaRandom(BigInt(next.seed));
      next = next.arrangementManual
        ? arrangementCommands.randomizeArrangementPresence(next, undefined,
          this.sectionTypeSettings.chances(), () => random.nextDouble())!
        : arrangementCommands.randomizeArrangement(next, this.sectionTypeSettings.chances());
    }
    const policy = current.trackGenerationPolicies?.melody ?? DEFAULT_TRACK_POLICIES.melody;
    if (event === 'compose' || policy.onRegenerate) {
      const count = current.trackRerollCounts?.melody ?? 0;
      const sources = next.tracks.filter(track => track.role === 'melody' && !track.rerollLocked);
      if (sources.length && ((policy.patternsOnCompose ?? true) || (policy.targetsOnCompose ?? true)
        || event === 'regenerate' && policy.rerollSeeds || !(next.melodyGlobal?.contextualMelody ?? true) && next.melodyGlobal?.forceFirstOutline)) {
        if (count >= 2147483647) return 'invalid';
        const seed = this.trackRerollSeed(next, 'melody', count);
        const random = new JavaRandom(seed);
        const sharedSeed = random.nextInt(); // randomizeMelodySeeds draws once even for independent seeds.
        let tracks = next.tracks.map(track => {
          if (track.role !== 'melody' || track.rerollLocked) return track;
          const settings = { ...track.generatorSettings };
          if (event === 'regenerate' && policy.rerollSeeds) settings.patternSeed = policy.sameSeed ? sharedSeed : random.nextInt();
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
    }
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
        : resolvePartTrack(item, { ...section, trackPartOverrides: undefined }).generatorSettings, role));
      for (const [field, value] of Object.entries(decoded)) {
        if (inherited.length && inherited.every((settings) => partValuesEqual(settings[field], value))) delete values[field];
        else values[field] = value as PartSettingValue;
      }
      if (partPatchesEqual(previous, values as PartSettingsPatch)) return section;
      const map: Record<string, PartSettingsPatch> = { ...overrides };
      if (Object.keys(values).length) map[key] = values as PartSettingsPatch;
      else delete map[key];
      return scope.kind === 'section-role'
        ? { ...section, rolePartOverrides: map as NonNullable<ArrangementSection['rolePartOverrides']> }
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
      return scope.kind === 'section-role' ? { ...section, rolePartOverrides: map as NonNullable<ArrangementSection['rolePartOverrides']> }
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
      const effective = resolvePartTrack(track, section);
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
      const values = settingsValues(resolvePartTrack(track, section).generatorSettings, track.role);
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
      patch: settingsValues(resolvePartTrack(track, source).generatorSettings, track.role) as PartSettingsPatch })) : [];
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
    if (!isProject(next, this.sectionTypeSettings.chances(), Math.random)) return 'invalid';
    const before = this.state();
    this.commit(next, controlKey);
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
      this.commit(project);
      this.automaticSeed.set(BigInt(project.seed) === 0n);
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

  updateSettings(patch: Partial<Pick<CompositionProject, 'humanization' | 'midiPadding' | 'name' | 'key' | 'scale' | 'transposeSemitones' | 'tempoBpm' | 'seed' | 'randomizeTempoOnCompose' | 'randomizeHarmonyOnCompose' | 'randomizeTransposeOnCompose' | 'randomizeArrangementOnCompose' | 'arrangementPreview' | 'arrangementManual'>>): void {
    if (patch.scale !== undefined && !isScaleMode(patch.scale)) return;
    if (patch.transposeSemitones !== undefined
        && (!Number.isInteger(patch.transposeSemitones) || patch.transposeSemitones < -24 || patch.transposeSemitones > 24)) return;
    if (patch.seed !== undefined) {
      if (!/^-?\d+$/.test(patch.seed) || BigInt(patch.seed) < -(1n << 63n) || BigInt(patch.seed) > (1n << 63n) - 1n) return;
      const automatic = BigInt(patch.seed) === 0n;
      this.automaticSeed.set(automatic);
      if (automatic) {
        const { seed, ...settings } = patch;
        if (Object.keys(settings).length) this.updateSettings(settings);
        return;
      }
    }
    const current = this.state();
    const next = { ...current, ...patch };
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

  addTrack(role: ArrangedPart): string | undefined {
    const current = this.state();
    if (!ARRANGED_PARTS.includes(role) || current.tracks.length >= 64) return undefined;
    const source = current.tracks.find((track) => track.role === role) ?? DEFAULT_TRACKS.find(track => track.role === role)!;
    const nextNumber = current.tracks.reduce((maximum, track) => {
      const match = new RegExp(`^track-${role}-(\\d+)$`).exec(track.id);
      return match ? Math.max(maximum, Number(match[1])) : maximum;
    }, 0) + 1;
    const id = `track-${role}-${nextNumber}`;
    const channel = role === 'drums' ? 10 : this.nextAvailableChannel(current.tracks);
    const track = { ...source, id, name: `${role[0].toUpperCase()}${nextNumber}`, midiChannel: channel,
      mix: { ...source.mix, muted: false, solo: false },
      generatorSettings: copyPartSettings(role === 'drums' ? DEFAULT_DRUM_SETTINGS : source.generatorSettings),
      rerollLocked: false,
      instrumentLocked: false,
      editedPhrase: role === 'drums' ? undefined : source.editedPhrase?.map((note) => ({ ...note })),
      phraseEdits: role === 'drums' ? undefined : source.phraseEdits?.map(edit => ({ ...edit, notes: edit.notes.map(note => ({ ...note })) })) } as CompositionTrack;
    const arrangement = current.arrangement.map((section) => ({
      ...section,
      trackPresence: { ...section.trackPresence, ...generateTrackPresence([track], this.sectionTypeSettings.chances()[section.type]) },
    }));
    this.commit({ ...current, tracks: [...current.tracks, track], arrangement });
    return this.state().tracks.some((candidate) => candidate.id === id) ? id : undefined;
  }

  duplicateTrack(trackId: string): string | undefined {
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
      editedPhrase: source.editedPhrase?.map((note) => ({ ...note })),
      phraseEdits: source.phraseEdits?.map(edit => ({ ...edit, notes: edit.notes.map(note => ({ ...note })) })) } as CompositionTrack;
    const tracks = [...current.tracks];
    tracks.splice(sourceIndex + 1, 0, duplicate);
    this.commit({ ...current, tracks, arrangement: current.arrangement.map((section) => ({
      ...section,
      trackPresence: { ...section.trackPresence, [id]: section.trackPresence[source.id] ?? false },
      ...(section.generatedTrackIds?.includes(source.id) ? { generatedTrackIds: [...section.generatedTrackIds, id] } : {}),
      ...(section.trackPhrases?.[source.id] !== undefined ? { trackPhrases: { ...section.trackPhrases, [id]: section.trackPhrases[source.id].map(note => ({ ...note })) } } : {}),
      trackPartOverrides: section.trackPartOverrides?.[source.id]
        ? { ...section.trackPartOverrides, [id]: copyPartSettings(section.trackPartOverrides[source.id]) } : section.trackPartOverrides,
    })) });
    return id;
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
          return { ...section, trackPhrases };
        });
      } else {
        tracks = tracks.map(track => track.id !== edit.trackId ? track : edit.range && edit.notes !== null
          ? { ...track, phraseEdits: appendPhraseRangeEdit(track.phraseEdits, { ...edit.range, notes: edit.notes }) } as CompositionTrack
          : { ...track, editedPhrase: edit.notes?.map(note => ({ ...note })), phraseEdits: undefined } as CompositionTrack);
        const track = tracks.find(track => track.id === edit.trackId)!;
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
      ? { ...candidate, editedPhrase: undefined, phraseEdits: undefined } as CompositionTrack : candidate);
    const isPrimary = current.tracks.find((candidate) => candidate.role === track.role)?.id === trackId;
    const editedPhrases = { ...current.editedPhrases };
    if (isPrimary) delete editedPhrases[track.role];
    this.commit({ ...current, tracks, editedPhrases });
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
      return { ...section, trackPresence, trackPartOverrides, ...(section.trackPhrases !== undefined ? { trackPhrases } : {}),
        ...(section.generatedTrackIds ? { generatedTrackIds: section.generatedTrackIds.filter(id => id !== trackId) } : {}) };
    });
    this.commit({ ...current, tracks: current.tracks.filter((candidate) => candidate.id !== trackId), arrangement });
  }

  addSection(type: SectionType = 'VERSE1', afterIndex = this.state().arrangement.length - 1): void {
    const next = arrangementCommands.addSection(this.state(), type, afterIndex, this.sectionTypeSettings.chances(), Math.random);
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

  /** Explicit toolbar action replaces the whole arrangement independently of Compose policy. */
  randomizeArrangement(): void {
    const seed = BigInt(Math.floor(Math.random() * 0x1000000000000));
    this.commit(arrangementCommands.randomizeArrangement(this.state(), this.sectionTypeSettings.chances(), seed));
  }

  randomizeArrangementPresence(sectionId?: string): void {
    const next = arrangementCommands.randomizeArrangementPresence(this.state(), sectionId, this.sectionTypeSettings.chances(), Math.random);
    if (next) this.commit(next);
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

  generateProgression(sectionId?: string): void {
    const project = this.state(), section = sectionId ? project.arrangement.find(s => s.id === sectionId) : undefined;
    if (sectionId && !section) return;
    const context = sectionProject(project, section);
    const length = section?.customChords?.length ?? section?.chordDegrees?.length ?? project.progression.length;
    const generated = generateHarmony(BigInt(project.seed), length, context.key, context.scale, project.harmonySettings, project.melodyGlobal);
    if (section) this.setSectionMusicalState(section.id, { chordDegrees: generated.degrees, customChords: generated.chords });
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
    this.persist(restored);
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
    this.persist(restored);
    this.refreshHistoryAvailability();
  }

  private commit(next: CompositionProject, controlKey?: string): void {
    const current = this.state();
    if (!isProject(next, this.sectionTypeSettings.chances(), Math.random) || JSON.stringify(current) === JSON.stringify(next)) {
      return;
    }
    if (historyContent(current) === historyContent(next)) {
      this.state.set(next);
      this.persist(next);
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
    this.persist(next);
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
          this.persist(decoded);
          return decoded;
        }
        this.warnProjectLoadFailure(`session storage (${key})`, value);
      } catch (error) {
        console.warn(`Could not load project from session storage (${key}).`, error);
      }
    }
    const initial = copyDefaultProject(this.sectionTypeSettings.chances(), Math.random);
    this.persist(initial);
    return initial;
  }

  private warnProjectLoadFailure(source: string, value: unknown): void {
    console.warn(`Could not load project from ${source}.`, projectLoadIssues(value, this.sectionTypeSettings.chances(), Math.random));
  }

  private persist(project: CompositionProject): void {
    try {
      globalThis.sessionStorage?.setItem(STORAGE_KEY, JSON.stringify(encodeProjectStorage(project)));
    } catch {
      // Keep the project usable if storage is disabled or full.
    }
  }
}
