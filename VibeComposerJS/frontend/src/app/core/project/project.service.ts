import { UiPreferencesService } from '../ui/ui-preferences.service';
import { isScaleMode } from '../music/scales';
import { normalizeVolumePercent, validVolumePercent } from '../audio/mix-values';
import { computed, inject, Injectable, signal } from '@angular/core';
import { historyContent, preserveAudition, projectDiff } from './project-history';
import { KEYS, getDiatonicChords, progressionChords, validProgressionChords, type ProgressionChord } from '../music/harmony';
import { generateTrackPresence, legacyTrackPresence } from '../music/arrangement-generator';
import type {
  ArpeggioSettings, ArrangedPart, ArrangementSection, BassSettings, ChordSettings, CompositionTrack, DrumSettings,
  MixChannelSettings, PhraseNote, SectionType, PartSettingsPatch, PartSettingsScope, MelodySettings,
  LocalPartSettingsScope, PartWorkflowResult, InstrumentGenerationPolicy,
  ArpeggioGenerationPolicy, TrackGenerationPolicyPatch, TrackGenerationPolicies,
} from './project.model';
import type { CompositionProject } from './project.model';
import {
  ARRANGED_PARTS, DEFAULT_ARPEGGIO_SETTINGS, DEFAULT_ARRANGEMENT, DEFAULT_BASS_SETTINGS, DEFAULT_CHORD_SETTINGS,
  DEFAULT_DRUM_SETTINGS, DEFAULT_MELODY_SETTINGS, DEFAULT_MIX, DEFAULT_PROJECT, DEFAULT_TRACKS, SECTION_TYPES,
} from './project.model';
import { DEFAULT_SECTION_TYPE_CHANCES, SectionTypeSettingsService } from './section-type-settings.service';
import { copyPartSettings, decodeCommonPartSettings, decodePartPatch, resolvePartTrack, settingsValues, partPatchesEqual, partValuesEqual, type PartSettingValue, PART_CONTROLS, validVelocityRange } from '../music/part-settings';
import { BASS_RHYTHMS, RHYTHM_PATTERNS } from '../music/rhythm-patterns';
import { isDrumPitch } from '../music/drum-instruments';
import { JavaRandom } from '../music/java-random';
import { ARPEGGIO_PATTERNS } from '../music/arpeggio-pattern';
import { DRUM_HIT_MULTIPLIERS, multiplyDrumHits } from '../music/drum-hit-multiplier';
import { chooseMelodyTargets, forceMelodyOutline } from '../music/melody-targets';
import { decodeMelodyGlobal } from '../music/melody-global-settings';
import { decodeHarmonySettings, DEFAULT_HARMONY_SETTINGS, type HarmonySettings } from '../music/harmony-voicing';
import { generateHarmony } from '../music/contextual-harmony';
import { contextualRange } from '../music/contextual-randomizer';
import { DEFAULT_MELODY_GLOBAL } from '../music/melody-global-settings';
import type { MelodyGlobalSettings } from './project.model';
import { DEFAULT_TRACK_POLICIES, chooseMelodyPattern, decodeTrackGenerationPolicy, rerollRoleSettings, validTrackGenerationMetadata } from '../music/track-generation';
import { DEFAULT_INSTRUMENT_POLICIES, INSTRUMENT_POOLS, decodeInstrumentPolicy, validInstrumentPolicies, rerollInstruments } from '../music/instrument-generation';
import { sectionDegrees, sectionProject, sectionValuesEqual, validSectionHarmony } from '../music/section-context';
import { baseGenerationHarmony } from '../music/project-harmony';
import type { SectionMusicalContext } from './project.model';

const STORAGE_KEY = 'vibecomposer.project.v12';
const LEGACY_STORAGE_KEY = 'vibecomposer.project.v11';
const MAX_HISTORY = 100;
const COMPACT_HISTORY_WINDOW_MS = 1000;
const MAX_ARRANGEMENT_SECTIONS = 32;
const MAX_ARRANGEMENT_MEASURES = 128;
const CHORD_VOICINGS = ['close', 'open'] as const;
const ARPEGGIO_RATES = ['eighth', 'sixteenth'] as const;

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
    const decoded = decodeMelodyGlobal(patch, true);
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
    const keys = ['tempoBpm','swingPercent','key','scale','keyChange','keyChangeType','beatDurationMultiplier'];
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
    return JSON.stringify(this.state(), null, 2);
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
    const primary = replacements.get(`track-${role}-1`);
    return this.commitPartWorkflow({ ...current, tracks: current.tracks.map(track => replacements.get(track.id) ?? track),
      [role]: primary?.generatorSettings ?? current[role],
      mix: primary && role !== 'drums' ? { ...current.mix, [role]: primary.mix } : current.mix,
      trackRerollCounts: { ...current.trackRerollCounts, [role]: count + 1 } });
  }

  /** Reroll existing global track bases. Local overrides, identity and saved manual notes remain owned. */
  rerollArpeggioTracks(trackId?: string): PartWorkflowResult { return this.rerollRoleTracks('arpeggio', trackId); }

  /** Set a role's total, protecting locks and deleting highest numbered members first. */
  setRoleTrackCount(role: ArrangedPart, requested: number): PartWorkflowResult {
    if (!ARRANGED_PARTS.includes(role) || !Number.isInteger(requested) || requested < 0 || requested > 64) return 'invalid';
    const current = this.state();
    const members = current.tracks.filter(track => track.role === role);
    const target = Math.max(requested, members.filter(track => track.rerollLocked).length);
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
    const source = tracks.find(track => track.role === role) ?? {
      ...DEFAULT_TRACKS.find(track => track.role === role)!, generatorSettings: current[role], mix: current.mix[role],
    } as CompositionTrack;
    const created: CompositionTrack[] = [];
    for (let index = 0; index < amount; index++) {
      const number = Math.max(0, ...tracks.filter(track => track.role === role)
        .map(track => Number(track.id.match(/(\d+)$/)?.[1] ?? 0))) + 1;
      const id = `track-${role}-${number}`;
      const track = { ...source, id, name: `${role[0].toUpperCase()}${number}`,
        midiChannel: role === 'drums' ? 10 : this.nextAvailableChannel(tracks),
        generatorSettings: copyPartSettings(source.generatorSettings),
        mix: { ...source.mix, muted: false, solo: false }, rerollLocked: false, instrumentLocked: false, editedPhrase: undefined } as CompositionTrack;
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
    const primary = replacements.get(`track-${role}-1`);
    const presenceRandom = new JavaRandom(BigInt.asIntN(64, seed ^ 0x50524553454e4345n));
    return this.commitPartWorkflow({ ...current, tracks: tracks.map(track => replacements.get(track.id) ?? track),
      [role]: primary?.generatorSettings ?? current[role],
      mix: primary && role !== 'drums' ? { ...current.mix, [role]: primary.mix } : current.mix,
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
          if (track.editedPhrase !== undefined) generatedTrackIds.add(track.id);
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
    const primary = replacements.get('track-' + role + '-1');
    const next = { ...current, tracks: current.tracks.map(track => replacements.get(track.id) ?? track),
      [role]: primary?.generatorSettings ?? current[role],
      mix: primary && role !== 'drums' ? { ...current.mix, [role]: primary.mix } : current.mix,
      trackRerollCounts: { ...current.trackRerollCounts, [role]: count + 1 } };
    return next;
  }

  /** User-triggered events only. Live reload, playback and ordinary edits never run policies. */
  generateComposition(event: 'compose' | 'regenerate'): PartWorkflowResult {
    if (event !== 'compose' && event !== 'regenerate') return 'invalid';
    const current = this.state();
    let next = current;
    if (event === 'compose') {
      const seed = this.automaticSeed() || BigInt(current.seed) === 0n
        ? this.randomCompositionSeed() : current.seed;
      const random = new JavaRandom(BigInt(seed));
      // Separate stream from track rerolls; draw both values even when a star is disabled.
      const transpose = random.nextInt(12) - 6;
      const tempo = random.nextInt(41) + 60;
      next = { ...current, seed,
        transposeSemitones: current.randomizeTransposeOnCompose === false ? current.transposeSemitones : transpose,
        tempoBpm: current.randomizeTempoOnCompose === false ? current.tempoBpm : tempo };
      if (next.melodyGlobal?.contextualMelody && next.melodyGlobal.rangeRandomizeOnCompose
        && next.tracks.some(track => track.role === 'melody' && !track.rerollLocked && !track.editedPhrase)) {
        next = { ...next, melodyGlobal: { ...next.melodyGlobal, ...contextualRange(BigInt(seed)) } };
      }
      for (const role of ARRANGED_PARTS) {
        if ((current.trackGenerationPolicies?.[role] ?? DEFAULT_TRACK_POLICIES[role]).onCompose === false) continue;
        const candidate = this.buildRoleReroll(next, role);
        if (!candidate) return 'invalid';
        next = candidate;
      }
    }
    const policy = current.trackGenerationPolicies?.melody ?? DEFAULT_TRACK_POLICIES.melody;
    if (event === 'compose' || policy.onRegenerate) {
      const count = current.trackRerollCounts?.melody ?? 0;
      const sources = next.tracks.filter(track => track.role === 'melody' && !track.rerollLocked);
      if (sources.length && ((policy.patternsOnCompose ?? true) || (policy.targetsOnCompose ?? true)
        || event === 'regenerate' && policy.rerollSeeds || !next.melodyGlobal?.contextualMelody && next.melodyGlobal?.forceFirstOutline)) {
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
            next.key, next.scale, next.progressionChords);
          if (partPatchesEqual(track.generatorSettings, settings)) return track;
          return { ...track, generatorSettings: settings };
        });
        if (!next.melodyGlobal?.contextualMelody && next.melodyGlobal?.forceFirstOutline) {
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
        if (changed) next = { ...next, tracks, melody: tracks.find(track => track.id === 'track-melody-1')?.generatorSettings as MelodySettings ?? next.melody,
          trackRerollCounts: { ...next.trackRerollCounts, melody: count + 1 } };
      }
    }
    return next === current ? 'unchanged' : this.commitPartWorkflow(next);
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
    const primary = tracks.find(track => track.id === 'track-drums-1');
    return this.commitPartWorkflow({ ...current, tracks, drums: primary?.generatorSettings as DrumSettings ?? current.drums });
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
    const role = members[0].role, primary = replacements.get(`track-${role}-1`);
    return { ...current, tracks: current.tracks.map(track => replacements.get(track.id) ?? track),
      [role]: primary?.generatorSettings ?? current[role] };
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
    if (!this.isProject(next)) return 'invalid';
    const before = this.state();
    this.commit(next, controlKey);
    return before === this.state() ? 'unchanged' : 'changed';
  }

  importProjectJson(json: string): boolean {
    let value: unknown;
    try {
      value = JSON.parse(json);
      const project = this.decodeProject(value);
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

  private randomCompositionSeed(): string {
    const values = new Int32Array(1);
    globalThis.crypto.getRandomValues(values);
    return String(values[0] || 1);
  }

  updateSettings(patch: Partial<Pick<CompositionProject, 'name' | 'key' | 'scale' | 'transposeSemitones' | 'tempoBpm' | 'seed' | 'randomizeTempoOnCompose' | 'randomizeTransposeOnCompose'>>): void {
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
    this.commit({ ...this.state(), ...patch }, this.controlHistoryKey(['project'], patch));
  }

  updateRoleGeneratorSettings(role: ArrangedPart, patch: PartSettingsPatch): void {
    const current = this.state();
    const controlKey = this.controlHistoryKey(['part', 'global-role', role], patch);
    if (role === 'drums' && 'pitch' in patch) return;
    if (!current.tracks.some((track) => track.role === role)) return;
    if (role === 'melody') {
      const melody = this.decodeMelody({ ...current.melody, ...patch });
      if (!melody) return;
      const replacements = new Map(current.tracks.filter(track => track.role === 'melody')
        .map(track => [track.id, this.decodeMelody({ ...track.generatorSettings, ...patch })]));
      if ([...replacements.values()].some(settings => !settings)) return;
      this.commit({ ...current, melody, tracks: current.tracks.map((track) => track.role === role
        ? { ...track, generatorSettings: replacements.get(track.id)! } : track) }, controlKey);
    } else if (role === 'bass') {
      const bass = { ...current.bass, ...patch } as BassSettings;
      if (!this.decodeBass(bass)) return;
      this.commit({ ...current, bass, tracks: current.tracks.map((track) => track.role === role
        ? { ...track, generatorSettings: { ...track.generatorSettings, ...patch } as BassSettings } : track) }, controlKey);
    } else if (role === 'chords') {
      const chords = { ...current.chords, ...patch } as ChordSettings;
      if (!this.decodeChords(chords)) return;
      this.commit({ ...current, chords, tracks: current.tracks.map((track) => track.role === role
        ? { ...track, generatorSettings: { ...track.generatorSettings, ...patch } as ChordSettings } : track) }, controlKey);
    } else if (role === 'arpeggio') {
      const arpeggio = { ...current.arpeggio, ...patch } as ArpeggioSettings;
      if (!this.decodeArpeggio(arpeggio)) return;
      this.commit({ ...current, arpeggio, tracks: current.tracks.map((track) => track.role === role
        ? { ...track, generatorSettings: { ...track.generatorSettings, ...patch } as ArpeggioSettings } : track) }, controlKey);
    } else {
      const drums = { ...current.drums, ...patch } as DrumSettings;
      if (!this.decodeDrums(drums)) return;
      this.commit({ ...current, drums, tracks: current.tracks.map((track) => track.role === role
        ? { ...track, generatorSettings: { ...track.generatorSettings, ...patch } as DrumSettings } : track) }, controlKey);
    }
  }

  updateBassSettings(patch: Partial<BassSettings>): void {
    const current = this.state();
    const bass = { ...current.bass, ...patch };
    this.commit({ ...current, bass, tracks: this.updatePrimaryTrack(current.tracks, 'bass', { generatorSettings: bass }) },
      this.controlHistoryKey(['primary-part', 'bass'], patch));
  }

  updateChordSettings(patch: Partial<ChordSettings>): void {
    const current = this.state();
    const chords = { ...current.chords, ...patch };
    this.commit({ ...current, chords, tracks: this.updatePrimaryTrack(current.tracks, 'chords', { generatorSettings: chords }) },
      this.controlHistoryKey(['primary-part', 'chords'], patch));
  }

  updateArpeggioSettings(patch: Partial<ArpeggioSettings>): void {
    const current = this.state();
    const arpeggio = { ...current.arpeggio, ...patch };
    this.commit({ ...current, arpeggio, tracks: this.updatePrimaryTrack(current.tracks, 'arpeggio', { generatorSettings: arpeggio }) },
      this.controlHistoryKey(['primary-part', 'arpeggio'], patch));
  }

  updateDrumSettings(patch: Partial<DrumSettings>): void {
    const current = this.state();
    const drums = { ...current.drums, ...patch };
    this.commit({ ...current, drums, tracks: this.updatePrimaryTrack(current.tracks, 'drums', { generatorSettings: drums }) },
      this.controlHistoryKey(['primary-part', 'drums'], patch));
  }

  updateMixSettings(part: ArrangedPart, patch: Partial<MixChannelSettings>): void {
    if (!ARRANGED_PARTS.includes(part)) {
      return;
    }
    const current = this.state();
    const mix = { ...current.mix, [part]: { ...current.mix[part], ...patch,
      ...(patch.solo === true ? { muted: false } : patch.muted === true ? { solo: false } : {}) } };
    this.commit({ ...current, mix, tracks: this.updatePrimaryTrack(current.tracks, part, { mix: mix[part] }) },
      this.controlHistoryKey(['primary-mix', part], patch));
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
    const mix = { ...current.mix };
    for (const role of ARRANGED_PARTS) {
      const primary = tracks.find(track => track.role === role);
      if (primary) mix[role] = primary.mix;
    }
    this.commit({ ...current, tracks, mix });
  }

  updateEditedPhrase(part: ArrangedPart, notes: readonly PhraseNote[]): void {
    if (!ARRANGED_PARTS.includes(part) || !this.isPhrase(notes)) {
      return;
    }
    const current = this.state();
    const editedPhrase = notes.map((note) => ({ ...note }));
    this.commit({
      ...current,
      editedPhrases: { ...current.editedPhrases, [part]: editedPhrase },
      tracks: this.updatePrimaryTrack(current.tracks, part, { editedPhrase }),
    });
  }

  clearEditedPhrase(part: ArrangedPart): void {
    if (!this.state().editedPhrases[part]) {
      return;
    }
    const current = this.state();
    const editedPhrases = { ...current.editedPhrases };
    delete editedPhrases[part];
    this.commit({ ...current, editedPhrases, tracks: this.updatePrimaryTrack(current.tracks, part, { editedPhrase: undefined }) });
  }

  addTrack(role: ArrangedPart): string | undefined {
    const current = this.state();
    if (!ARRANGED_PARTS.includes(role) || current.tracks.length >= 64) return undefined;
    const source = current.tracks.find((track) => track.role === role) ?? {
      ...DEFAULT_TRACKS.find((track) => track.role === role)!,
      generatorSettings: current[role], mix: current.mix[role],
    } as CompositionTrack;
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
      editedPhrase: role === 'drums' ? undefined : source.editedPhrase?.map((note) => ({ ...note })) } as CompositionTrack;
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
      editedPhrase: source.editedPhrase?.map((note) => ({ ...note })) } as CompositionTrack;
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
    const isPrimary = current.tracks.find((candidate) => candidate.role === track.role)?.id === track.id;
    const mix = patch.mix && isPrimary ? { ...current.mix, [track.role]: tracks[index].mix } : current.mix;
    this.commit({ ...current, tracks, mix }, Object.keys(patch).length === 1
      ? this.controlHistoryKey(['track', trackId, ...(patch.mix ? ['mix'] : [])], patch.mix ?? patch) : undefined);
  }

  updateTrackGeneratorSettings(trackId: string, patch: PartSettingsPatch): void {
    const current = this.state();
    const index = current.tracks.findIndex((track) => track.id === trackId);
    if (index < 0) return;
    const tracks = [...current.tracks];
    const track = tracks[index];
    const generatorSettings = { ...track.generatorSettings, ...patch };
    const validSettings = track.role === 'melody' ? this.decodeMelody(generatorSettings) !== undefined
      : track.role === 'bass' ? this.decodeBass(generatorSettings) !== undefined
      : track.role === 'chords' ? this.decodeChords(generatorSettings) !== undefined
        : track.role === 'arpeggio' ? this.decodeArpeggio(generatorSettings) !== undefined
          : this.decodeDrums(generatorSettings) !== undefined;
    if (!validSettings) return;
    tracks[index] = { ...track, generatorSettings } as CompositionTrack;
    const isPrimary = current.tracks.find((candidate) => candidate.role === track.role)?.id === track.id;
    const legacySettings = isPrimary ? tracks[index].generatorSettings : undefined;
    const next = track.role === 'melody' && legacySettings ? { ...current, melody: legacySettings as MelodySettings, tracks }
      : track.role === 'bass' && legacySettings ? { ...current, bass: legacySettings as BassSettings, tracks }
      : track.role === 'chords' && legacySettings ? { ...current, chords: legacySettings as ChordSettings, tracks }
        : track.role === 'arpeggio' && legacySettings ? { ...current, arpeggio: legacySettings as ArpeggioSettings, tracks }
          : track.role === 'drums' && legacySettings ? { ...current, drums: legacySettings as DrumSettings, tracks }
            : { ...current, tracks };
    this.commit(next, this.controlHistoryKey(['part', 'global-track', trackId], patch));
  }

  syncScoreMix(visibleTrackIds: readonly string[]): void {
    const current = this.state();
    const visible = new Set(visibleTrackIds);
    const tracks = current.tracks.map(track => ({ ...track, mix: { ...track.mix, solo: false, muted: !visible.has(track.id) } }));
    if (tracks.some((track, index) => track.mix.muted !== current.tracks[index].mix.muted || current.tracks[index].mix.solo)) this.commit({ ...current, tracks });
  }

  updateSectionTrackPhrase(sectionId: string, trackId: string, notes: readonly PhraseNote[]): boolean {
    const current = this.state();
    if (!current.tracks.some(track => track.id === trackId) || !current.arrangement.some(section => section.id === sectionId) || !this.isPhrase(notes, 2048)) return false;
    this.commit({ ...current, arrangement: current.arrangement.map(section => section.id === sectionId
      ? { ...section, trackPhrases: { ...section.trackPhrases, [trackId]: notes.map(note => ({ ...note })) } } : section) });
    return true;
  }

  /** Apply a multi-track phrase draft atomically, including mixed global and section destinations. */
  applyPhraseEdits(edits: readonly { trackId: string; sectionId?: string; notes: readonly PhraseNote[] | null }[]): boolean {
    const current = this.state();
    if (!edits.length || edits.some(edit => !current.tracks.some(track => track.id === edit.trackId)
      || edit.sectionId !== undefined && !current.arrangement.some(section => section.id === edit.sectionId)
      || edit.notes !== null && !this.isPhrase(edit.notes, edit.sectionId ? 2048 : undefined))) return false;
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
        tracks = tracks.map(track => track.id === edit.trackId
          ? { ...track, editedPhrase: edit.notes?.map(note => ({ ...note })) } as CompositionTrack : track);
        const track = tracks.find(track => track.id === edit.trackId)!;
        if (tracks.find(candidate => candidate.role === track.role)?.id === track.id) {
          if (edit.notes === null) delete editedPhrases[track.role];
          else editedPhrases[track.role] = track.editedPhrase;
        }
      }
    }
    const next = { ...current, tracks, arrangement, editedPhrases };
    if (!this.isProject(next)) return false;
    this.commit(next);
    return true;
  }

  updateTrackPhrase(trackId: string, notes: readonly PhraseNote[]): boolean {
    const current = this.state();
    const index = current.tracks.findIndex((track) => track.id === trackId);
    if (index < 0 || !this.isPhrase(notes)) return false;
    const tracks = [...current.tracks];
    tracks[index] = { ...tracks[index], editedPhrase: notes.map((note) => ({ ...note })) } as CompositionTrack;
    const isPrimary = current.tracks.find((candidate) => candidate.role === tracks[index].role)?.id === trackId;
    const editedPhrases = isPrimary ? { ...current.editedPhrases, [tracks[index].role]: tracks[index].editedPhrase } : current.editedPhrases;
    this.commit({ ...current, tracks, editedPhrases });
    return true;
  }

  clearTrackPhrase(trackId: string): void {
    const current = this.state();
    const track = current.tracks.find((candidate) => candidate.id === trackId);
    if (!track?.editedPhrase) return;
    const tracks = current.tracks.map((candidate) => candidate.id === trackId
      ? { ...candidate, editedPhrase: undefined } as CompositionTrack : candidate);
    const isPrimary = current.tracks.find((candidate) => candidate.role === track.role)?.id === trackId;
    const editedPhrases = { ...current.editedPhrases };
    if (isPrimary) delete editedPhrases[track.role];
    this.commit({ ...current, tracks, editedPhrases });
  }

  reorderTrack(trackId: string, offset: -1 | 1): void {
    const current = this.state();
    const index = current.tracks.findIndex((track) => track.id === trackId);
    const target = index + offset;
    if (index < 0 || target < 0 || target >= current.tracks.length) return;
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
    const sections = this.state().arrangement;
    if (sections.length >= MAX_ARRANGEMENT_SECTIONS || !SECTION_TYPES.includes(type)) {
      return;
    }
    const insertAt = Math.max(0, Math.min(sections.length, afterIndex + 1));
    const nextId = sections.reduce((maximum, section) => {
      const match = /^section-(\d+)$/.exec(section.id);
      return match ? Math.max(maximum, Number(match[1])) : maximum;
    }, 0) + 1;
    const next = [...sections];
    const project = this.state();
    next.splice(insertAt, 0, {
      id: `section-${nextId}`,
      type,
      measures: 4,
      trackPresence: generateTrackPresence(project.tracks, this.sectionTypeSettings.chances()[type]),
    });
    this.commit({ ...this.state(), arrangement: next });
  }

  duplicateSection(index: number): void {
    const sections = this.state().arrangement;
    if (!Number.isInteger(index) || index < 0 || index >= sections.length
        || sections.length >= MAX_ARRANGEMENT_SECTIONS) {
      return;
    }
    const nextId = sections.reduce((maximum, section) => {
      const match = /^section-(\d+)$/.exec(section.id);
      return match ? Math.max(maximum, Number(match[1])) : maximum;
    }, 0) + 1;
    const next = [...sections];
    next.splice(index + 1, 0, {
      ...sections[index], id: `section-${nextId}`,
      context: sections[index].context ? { ...sections[index].context } : undefined,
      customChords: sections[index].customChords?.map(chord => chord ? { ...chord } : null),
      customDurations: sections[index].customDurations ? [...sections[index].customDurations] : undefined,
      chordDegrees: sections[index].chordDegrees ? [...sections[index].chordDegrees] : undefined,
      trackPresence: { ...sections[index].trackPresence },
      ...(sections[index].generatedTrackIds ? { generatedTrackIds: [...sections[index].generatedTrackIds] } : {}),
      ...(sections[index].trackPhrases !== undefined ? { trackPhrases: Object.fromEntries(Object.entries(sections[index].trackPhrases).map(([id, notes]) => [id, notes.map(note => ({ ...note }))])) } : {}),
      rolePartOverrides: Object.fromEntries(Object.entries(sections[index].rolePartOverrides ?? {}).map(([role, patch]) => [role, copyPartSettings(patch)])),
      trackPartOverrides: Object.fromEntries(Object.entries(sections[index].trackPartOverrides ?? {}).map(([id, patch]) => [id, copyPartSettings(patch)])),
    });
    this.commit({ ...this.state(), arrangement: next });
  }

  removeSection(index: number): void {
    const sections = this.state().arrangement;
    if (!Number.isInteger(index) || index < 0 || index >= sections.length || sections.length <= 1) {
      return;
    }
    this.commit({ ...this.state(), arrangement: sections.filter((_, sectionIndex) => sectionIndex !== index) });
  }

  moveSection(index: number, offset: -1 | 1): void {
    const sections = this.state().arrangement;
    const target = index + offset;
    if (!Number.isInteger(index) || index < 0 || target < 0 || target >= sections.length) {
      return;
    }
    const next = [...sections];
    [next[index], next[target]] = [next[target], next[index]];
    this.commit({ ...this.state(), arrangement: next });
  }

  updateSection(index: number, patch: Partial<Pick<ArrangementSection, 'type' | 'measures'>>): void {
    const sections = this.state().arrangement;
    if (!Number.isInteger(index) || index < 0 || index >= sections.length) {
      return;
    }
    const next = [...sections];
    const current = sections[index];
    const updated = { ...current, ...patch };
    if (patch.measures !== undefined && !current.customChords && !current.customDurations) {
      const progression = this.state().progression;
      const chordDegrees = Array.from({ length: patch.measures }, (_, measure) =>
        current.chordDegrees?.[measure] ?? progression[measure % progression.length]);
      updated.chordDegrees = this.matchesMainProgression(chordDegrees) ? undefined : chordDegrees;
    }
    next[index] = updated;
    this.commit({ ...this.state(), arrangement: next }, this.controlHistoryKey(['section', current.id], patch));
  }

  setSectionChordDegree(sectionIndex: number, measureIndex: number, degree: number): void {
    const project = this.state();
    const section = project.arrangement[sectionIndex];
    if (!section || !Number.isInteger(measureIndex) || measureIndex < 0 || measureIndex >= section.measures
        || !getDiatonicChords(project.key, project.scale).some((chord) => chord.degree === degree)) {
      return;
    }
    const chordDegrees = Array.from({ length: section.measures }, (_, measure) =>
      section.chordDegrees?.[measure] ?? project.progression[measure % project.progression.length]);
    chordDegrees[measureIndex] = degree;
    const next = [...project.arrangement];
    next[sectionIndex] = { ...section, chordDegrees: this.matchesMainProgression(chordDegrees) ? undefined : chordDegrees };
    this.commit({ ...project, arrangement: next });
  }

  clearSectionChordOverrides(sectionIndex: number): void {
    const project = this.state();
    const section = project.arrangement[sectionIndex];
    if (!section || !section.chordDegrees && !section.customChords) return;
    const next = [...project.arrangement];
    next[sectionIndex] = { ...section, chordDegrees: undefined, customChords: undefined };
    this.commit({ ...project, arrangement: next });
  }

  setSectionTrackPresence(index: number, trackId: string, present: boolean): void {
    const project = this.state();
    const section = project.arrangement[index];
    if (!section || typeof present !== 'boolean' || !project.tracks.some((candidate) => candidate.id === trackId)) return;
    const trackPresence = { ...section.trackPresence, [trackId]: present };
    this.commit({ ...project, arrangement: project.arrangement.map((candidate, candidateIndex) => candidateIndex === index
      ? { ...section, trackPresence } : candidate) });
  }

  randomizeArrangementPresence(sectionId?: string): void {
    const project = this.state();
    const settings = this.sectionTypeSettings.chances();
    const arrangement = project.arrangement.map((section) => sectionId === undefined || section.id === sectionId
      ? { ...section, trackPresence: generateTrackPresence(project.tracks, settings[section.type]) }
      : section);
    if (sectionId !== undefined && !project.arrangement.some((section) => section.id === sectionId)) return;
    this.commit({ ...project, arrangement });
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
    if (!this.isProject(next) || JSON.stringify(current) === JSON.stringify(next)) {
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
      mix?: MixChannelSettings; editedPhrase?: readonly PhraseNote[] },
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
        const decoded = this.decodeProject(value);
        if (decoded) {
          if (key !== STORAGE_KEY) this.persist(decoded);
          return decoded;
        }
        this.warnProjectLoadFailure(`session storage (${key})`, value);
      } catch (error) {
        console.warn(`Could not load project from session storage (${key}).`, error);
      }
    }
    const initial = this.copyDefaultProject();
    this.persist(initial);
    return initial;
  }

  /** Only gather diagnostics after rejection; normal loading keeps its existing acceptance rules. */
  private warnProjectLoadFailure(source: string, value: unknown): void {
    const issues: string[] = [];
    const object = (raw: unknown, path: string, required: readonly string[], allowed: readonly string[]): Record<string, unknown> | undefined => {
      if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
        issues.push(`${path}: expected an object`);
        return undefined;
      }
      const fields = raw as Record<string, unknown>;
      for (const key of required) if (fields[key] === undefined) issues.push(`${path}.${key}: missing required property`);
      for (const key of Object.keys(fields)) if (!allowed.includes(key)) issues.push(`${path}.${key}: unknown property encountered`);
      return fields;
    };
    const check = (path: string, valid: boolean, expected: string) => {
      if (!valid) issues.push(`${path}: ${expected}`);
    };
    const settings = (role: ArrangedPart, raw: unknown, path: string, patch = false) => {
      const required = patch ? [] : role === 'melody' ? ['algorithm'] : role === 'bass' ? ['rhythm', 'noteVariation']
        : role === 'chords' ? ['rhythm', 'voicing', 'noteLengthPercent'] : role === 'arpeggio' ? ['pattern', 'rate', 'octaves']
        : ['pitch', 'rhythm', 'swingPercent'];
      const allowed = PART_CONTROLS[role].map(control => control.key as string);
      if (role === 'arpeggio') allowed.push('rate');
      if (!patch && role === 'melody') allowed.push('algorithm');
      if (!patch && role === 'drums') allowed.push('pitch');
      const fields = object(raw, path, required, allowed);
      if (!fields) return;
      for (const [key, field] of Object.entries(fields)) {
        if (!allowed.includes(key)) continue;
        if (key === 'algorithm') check(`${path}.${key}`, field === 'block', 'expected "block"');
        else if (key === 'pitch') check(`${path}.${key}`, isDrumPitch(field), 'expected a supported percussion MIDI pitch');
        else check(`${path}.${key}`, !!decodePartPatch(role, { [key]: field }), 'invalid value for this generator setting');
      }
      check(path, validVelocityRange(fields as PartSettingsPatch, role), 'velocityMin must not exceed velocityMax');
    };
    const mix = (raw: unknown, path: string) => {
      const keys = ['program', 'volumePercent', 'panPercent', 'muted', 'solo'];
      const fields = object(raw, path, keys, keys);
      if (!fields) return;
      check(`${path}.program`, Number.isInteger(fields['program']) && (fields['program'] as number) >= 0 && (fields['program'] as number) <= 127, 'expected an integer from 0 to 127');
      check(`${path}.volumePercent`, validVolumePercent(fields['volumePercent']), 'invalid volume percentage');
      check(`${path}.panPercent`, Number.isInteger(fields['panPercent']) && Math.abs(fields['panPercent'] as number) <= 100, 'expected an integer from -100 to 100');
      for (const key of ['muted', 'solo']) check(`${path}.${key}`, typeof fields[key] === 'boolean', 'expected a boolean');
    };
    const required = ['name', 'key', 'scale', 'tempoBpm', 'seed', 'progression', 'bass', 'chords', 'arpeggio', 'drums', 'editedPhrases', 'mix', 'tracks', 'arrangement'];
    const project = object(value, 'project', required, [...required, 'schemaVersion', 'transposeSemitones', 'progressionChords', 'melody',
      'melodyGlobal', 'harmonySettings', 'trackGenerationPolicies', 'trackRerollCounts', 'instrumentGenerationPolicies']);
    if (project) {
      check('project.name', typeof project['name'] === 'string', 'expected a string');
      check('project.key', KEYS.includes(project['key'] as string), `expected one of ${KEYS.join(', ')}`);
      check('project.scale', isScaleMode(project['scale']), 'expected a supported scale/mode');
      check('project.tempoBpm', Number.isInteger(project['tempoBpm']) && (project['tempoBpm'] as number) >= 30 && (project['tempoBpm'] as number) <= 240, 'expected an integer from 30 to 240');
      const seed = project['seed'];
      check('project.seed', typeof seed === 'string' && /^-?\d+$/.test(seed) && BigInt(seed) >= -(1n << 63n) && BigInt(seed) <= (1n << 63n) - 1n, 'expected a decimal string within the signed 64-bit seed range');
      const progression = project['progression'];
      check('project.progression', Array.isArray(progression) && progression.length > 0 && progression.length <= 32 && progression.every(degree => Number.isInteger(degree) && degree >= 1 && degree <= 7), 'expected 1–32 chord degrees, each an integer from 1 to 7');
      if (project['transposeSemitones'] !== undefined) check('project.transposeSemitones', Number.isInteger(project['transposeSemitones']) && Math.abs(project['transposeSemitones'] as number) <= 24, 'expected an integer from -24 to 24');
      if (Array.isArray(progression)) check('project.progressionChords', !!validProgressionChords(project['progressionChords'], progression.length), 'invalid chords or length does not match progression');
      for (const role of ARRANGED_PARTS) if (role !== 'melody' || project[role] !== undefined) settings(role, project[role], `project.${role}`);
      const channels = object(project['mix'], 'project.mix', ARRANGED_PARTS.filter(role => role !== 'melody'), ARRANGED_PARTS);
      if (channels) for (const role of ARRANGED_PARTS) if (channels[role] !== undefined) mix(channels[role], `project.mix.${role}`);
      const tracks = project['tracks'];
      check('project.tracks', Array.isArray(tracks) && tracks.length <= 64, 'expected an array of at most 64 tracks');
      if (Array.isArray(tracks)) tracks.forEach((raw, index) => {
        const path = `project.tracks[${index}]`;
        const keys = ['id', 'role', 'name', 'midiChannel', 'mix', 'generatorSettings'];
        const track = object(raw, path, keys, [...keys, 'editedPhrase', 'rerollLocked', 'instrumentLocked', 'instrumentPool', 'orderOffset']);
        if (!track) return;
        mix(track['mix'], `${path}.mix`);
        const role = track['role'] as ArrangedPart;
        check(`${path}.role`, ARRANGED_PARTS.includes(role), `expected one of ${ARRANGED_PARTS.join(', ')}`);
        check(`${path}.id`, typeof track['id'] === 'string' && /^[a-zA-Z0-9_-]{1,64}$/.test(track['id']), 'expected 1–64 letters, digits, underscores, or hyphens');
        check(`${path}.name`, typeof track['name'] === 'string' && !!track['name'].trim() && track['name'].length <= 48, 'expected a nonempty name of at most 48 characters');
        check(`${path}.midiChannel`, Number.isInteger(track['midiChannel']) && (track['midiChannel'] as number) >= 1 && (track['midiChannel'] as number) <= 16 && (role !== 'drums' || track['midiChannel'] === 10), 'expected channel 1–16 (10 for drums)');
        if (ARRANGED_PARTS.includes(role)) settings(role, track['generatorSettings'], `${path}.generatorSettings`);
        check(path, !!this.decodeTracks([raw]), 'invalid track identity, name, channel, locks, instrument pool, settings, or phrase');
        check(`${path}.id`, !tracks.slice(0, index).some(previous => previous?.id === track['id']), 'duplicate track ID');
      });
      const phrases = object(project['editedPhrases'], 'project.editedPhrases', [], ARRANGED_PARTS);
      if (phrases) for (const [role, notes] of Object.entries(phrases)) check(`project.editedPhrases.${role}`, this.isPhrase(notes), 'invalid phrase: check note IDs, MIDI pitches, beat bounds, durations, and velocities');
      for (const [key, valid] of [
        ['trackGenerationPolicies', validTrackGenerationMetadata(project['trackGenerationPolicies'], undefined)],
        ['trackRerollCounts', validTrackGenerationMetadata(undefined, project['trackRerollCounts'])],
        ['instrumentGenerationPolicies', validInstrumentPolicies(project['instrumentGenerationPolicies'])],
        ['melodyGlobal', project['melodyGlobal'] === undefined || !!decodeMelodyGlobal(project['melodyGlobal'])],
        ['harmonySettings', project['harmonySettings'] === undefined || !!decodeHarmonySettings(project['harmonySettings'])],
      ] as const) check(`project.${key}`, valid, 'invalid settings, property names, or values');
      for (const key of ['trackGenerationPolicies', 'trackRerollCounts', 'instrumentGenerationPolicies']) {
        if (project[key] === undefined) continue;
        const policies = object(project[key], `project.${key}`, [], ARRANGED_PARTS);
        if (!policies || key === 'trackRerollCounts') continue;
        for (const [role, policy] of Object.entries(policies)) {
          if (!ARRANGED_PARTS.includes(role as ArrangedPart)) continue;
          const defaults = key === 'trackGenerationPolicies' ? DEFAULT_TRACK_POLICIES[role as ArrangedPart] : DEFAULT_INSTRUMENT_POLICIES[role as ArrangedPart];
          const keys = Object.keys(defaults);
          const validPolicy = (candidate: unknown) => key === 'trackGenerationPolicies'
            ? !!decodeTrackGenerationPolicy(role as ArrangedPart, candidate) : !!decodeInstrumentPolicy(role as ArrangedPart, candidate);
          const requiredKeys = keys.filter(field => !validPolicy({ ...defaults, [field]: undefined }));
          const fields = object(policy, `project.${key}.${role}`, requiredKeys, keys);
          if (!fields) continue;
          for (const field of keys) {
            const candidate = { ...defaults, [field]: fields[field] };
            check(`project.${key}.${role}.${field}`, validPolicy(candidate), 'invalid policy value');
          }
        }
      }
      const arrangement = project['arrangement'];
      check('project.arrangement', Array.isArray(arrangement) && arrangement.length >= 1 && arrangement.length <= MAX_ARRANGEMENT_SECTIONS, `expected 1–${MAX_ARRANGEMENT_SECTIONS} sections`);
      if (Array.isArray(arrangement)) arrangement.forEach((raw, index) => {
        const path = `project.arrangement[${index}]`;
        const section = object(raw, path, ['id', 'type', 'measures'], ['id', 'type', 'measures', 'context', 'customChords', 'customDurations', 'chordDegrees',
          'useMelodyHarmony', 'swapMelody', 'trackPresence', 'rolePartOverrides', 'trackPartOverrides', 'trackPhrases', 'generatedTrackIds', 'parts', 'partChances', 'trackParts', 'trackPartChances']);
        if (!section) return;
        check(`${path}.type`, SECTION_TYPES.includes(section['type'] as SectionType), `expected one of ${SECTION_TYPES.join(', ')}`);
        check(`${path}.measures`, Number.isInteger(section['measures']) && (section['measures'] as number) >= 1 && (section['measures'] as number) <= 32, 'expected an integer from 1 to 32');
        for (const [role, patch] of Object.entries(section['rolePartOverrides'] ?? {})) if (ARRANGED_PARTS.includes(role as ArrangedPart)) settings(role as ArrangedPart, patch, `${path}.rolePartOverrides.${role}`, true);
        const decodedTracks = this.decodeTracks(tracks);
        if (decodedTracks) {
          for (const [id, patch] of Object.entries(section['trackPartOverrides'] ?? {})) {
            const track = decodedTracks.find(track => track.id === id);
            if (track) settings(track.role, patch, `${path}.trackPartOverrides.${id}`, true);
          }
          check(path, !!this.decodeArrangement([raw], decodedTracks, 0n), 'invalid section identity, type, measures, harmony, track references, overrides, or phrases');
        }
        check(`${path}.id`, !arrangement.slice(0, index).some(previous => previous?.id === section['id']), 'duplicate section ID');
      });
      if (Array.isArray(arrangement)) check('project.arrangement', arrangement.reduce((total, section) => total + (section?.measures ?? 0), 0) <= MAX_ARRANGEMENT_MEASURES, `total length must not exceed ${MAX_ARRANGEMENT_MEASURES} measures`);
    }
    console.warn(`Could not load project from ${source}.`, issues.length ? issues : ['Project structure or values failed validation.']);
  }

  private decodeProject(value: unknown): CompositionProject | undefined {
    if (!value || typeof value !== 'object') {
      return undefined;
    }

    const project = value as Partial<CompositionProject> & { bass?: unknown; chords?: unknown; arpeggio?: unknown; drums?: unknown; tracks?: unknown };
    const validBase = typeof project.name === 'string'
      && typeof project.key === 'string' && KEYS.includes(project.key)
      && (isScaleMode(project.scale))
      && (project.transposeSemitones === undefined || (Number.isInteger(project.transposeSemitones) && project.transposeSemitones >= -24 && project.transposeSemitones <= 24))
      && (project.randomizeTempoOnCompose === undefined || typeof project.randomizeTempoOnCompose === 'boolean')
      && (project.randomizeTransposeOnCompose === undefined || typeof project.randomizeTransposeOnCompose === 'boolean')
      && Number.isInteger(project.tempoBpm) && (project.tempoBpm ?? 0) >= 30 && (project.tempoBpm ?? 0) <= 240
      && typeof project.seed === 'string' && /^-?\d+$/.test(project.seed)
      && BigInt(project.seed) >= -(1n << 63n) && BigInt(project.seed) <= (1n << 63n) - 1n
      && Array.isArray(project.progression)
      && project.progression.length > 0 && project.progression.length <= 32
      && project.progression.every((degree) => Number.isInteger(degree) && degree >= 1 && degree <= 7)
      && validProgressionChords(project.progressionChords, project.progression.length);
    if (!validBase || !validTrackGenerationMetadata(project.trackGenerationPolicies, project.trackRerollCounts)
      || !validInstrumentPolicies(project.instrumentGenerationPolicies)
      || (project.melodyGlobal !== undefined && !decodeMelodyGlobal(project.melodyGlobal))
      || (project.harmonySettings !== undefined && !decodeHarmonySettings(project.harmonySettings))) {
      return undefined;
    }

    const melody = this.decodeMelody(project.melody === undefined ? DEFAULT_MELODY_SETTINGS : project.melody);
    const bass = this.decodeBass(project.bass);
    const chords = this.decodeChords(project.chords);
    const arpeggio = this.decodeArpeggio(project.arpeggio);
    const drums = this.decodeDrums(project.drums);
    const editedPhrases = this.decodeEditedPhrases(project.editedPhrases);
    const mix = this.decodeMix(project.mix);
    const tracks = this.decodeTracks(project.tracks);
    const arrangement = tracks ? this.decodeArrangement(project.arrangement, tracks, BigInt(project.seed!)) : undefined;
    if (!melody || !bass || !chords || !arpeggio || !drums || !arrangement || !editedPhrases || !mix || !tracks) {
      return undefined;
    }
    const trackIds = new Set(tracks.map((track) => track.id));
    if (arrangement.some((section) => Object.keys(section.trackPresence).some((trackId) => !trackIds.has(trackId)))) return undefined;
    return {
      schemaVersion: 12,
      name: project.name!,
      key: project.key!,
      scale: project.scale!,
      transposeSemitones: project.transposeSemitones ?? 0,
      tempoBpm: project.tempoBpm!,
      ...(project.randomizeTempoOnCompose !== undefined ? { randomizeTempoOnCompose: project.randomizeTempoOnCompose } : {}),
      ...(project.randomizeTransposeOnCompose !== undefined ? { randomizeTransposeOnCompose: project.randomizeTransposeOnCompose } : {}),
      seed: project.seed!,
      progression: [...project.progression!],
      ...(project.progressionChords ? { progressionChords: project.progressionChords.map(chord => chord ? { ...chord } : null) } : {}),
      melody,
      ...(project.harmonySettings !== undefined ? { harmonySettings: decodeHarmonySettings(project.harmonySettings)! } : {}),
      ...(project.melodyGlobal !== undefined ? { melodyGlobal: decodeMelodyGlobal(project.melodyGlobal)! } : {}),
      bass,
      chords,
      arpeggio,
      drums,
      editedPhrases,
      mix,
      tracks,
      arrangement,
      ...(project.trackGenerationPolicies !== undefined ? { trackGenerationPolicies: Object.fromEntries(
        Object.entries(project.trackGenerationPolicies).map(([role, policy]) => [role, decodeTrackGenerationPolicy(role as ArrangedPart, policy)!])
      ) as Partial<TrackGenerationPolicies> } : {}),
      ...(project.trackRerollCounts !== undefined ? { trackRerollCounts: { ...project.trackRerollCounts } } : {}),
      ...(project.instrumentGenerationPolicies !== undefined ? { instrumentGenerationPolicies: Object.fromEntries(
        Object.entries(project.instrumentGenerationPolicies).map(([role, policy]) => [role, decodeInstrumentPolicy(role as ArrangedPart, policy)!])
      ) } : {}),
    };
  }

  private decodeMix(value: unknown): Record<ArrangedPart, MixChannelSettings> | undefined {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return undefined;
    }
    const mix = value as Partial<Record<ArrangedPart, unknown>>;
    if (Object.keys(mix).some((part) => !ARRANGED_PARTS.includes(part as ArrangedPart))
        || ARRANGED_PARTS.some((part) => part === 'melody' && mix[part] === undefined
          ? false : !mix[part] || typeof mix[part] !== 'object')) {
      return undefined;
    }
    const decoded = {} as Record<ArrangedPart, MixChannelSettings>;
    for (const part of ARRANGED_PARTS) {
      const settings = (part === 'melody' && mix[part] === undefined ? DEFAULT_MIX.melody : mix[part]) as Partial<MixChannelSettings>;
      if (!Number.isInteger(settings.program) || (settings.program ?? -1) < 0 || (settings.program ?? 128) > 127
          || !validVolumePercent(settings.volumePercent)
          || !Number.isInteger(settings.panPercent) || (settings.panPercent ?? -101) < -100 || (settings.panPercent ?? 101) > 100
          || typeof settings.muted !== 'boolean' || typeof settings.solo !== 'boolean') {
        return undefined;
      }
      decoded[part] = {
        program: settings.program!, volumePercent: normalizeVolumePercent(settings.volumePercent!), panPercent: settings.panPercent!,
        muted: settings.muted, solo: settings.solo,
      };
    }
    return decoded;
  }

  private decodeTracks(value: unknown): CompositionTrack[] | undefined {
    if (!Array.isArray(value) || value.length > 64) return undefined;
    const tracks: CompositionTrack[] = [];
    const ids = new Set<string>();
    for (const raw of value) {
      if (!raw || typeof raw !== 'object') return undefined;
      const track = raw as Partial<CompositionTrack> & { generatorSettings?: unknown; mix?: unknown; editedPhrase?: unknown };
      if (typeof track.id !== 'string' || !/^[a-zA-Z0-9_-]{1,64}$/.test(track.id) || ids.has(track.id)
          || typeof track.name !== 'string' || !track.name.trim() || track.name.length > 48
          || !Number.isInteger(track.midiChannel) || (track.midiChannel ?? 0) < 1 || (track.midiChannel ?? 17) > 16) return undefined;
      const mix = this.decodeTrackMix(track.mix);
      if (track.orderOffset !== undefined && (!Number.isInteger(track.orderOffset)
        || track.orderOffset < -2147483648 || track.orderOffset > 2147483647)) return undefined;
      const editedPhrase = track.editedPhrase === undefined ? undefined
        : this.isPhrase(track.editedPhrase) ? track.editedPhrase.map((note) => ({ ...note })) : null;
      if (!mix || editedPhrase === null || (track.rerollLocked !== undefined && typeof track.rerollLocked !== 'boolean')
        || (track.instrumentLocked !== undefined && typeof track.instrumentLocked !== 'boolean')) return undefined;
      if (track.instrumentPool !== undefined && (typeof track.instrumentPool !== 'string' || track.role === 'drums' || track.instrumentPool === 'DRUM'
        || !Object.hasOwn(INSTRUMENT_POOLS, track.instrumentPool)
        || !INSTRUMENT_POOLS[track.instrumentPool]?.includes(mix.program))) return undefined;
      let decoded: CompositionTrack | undefined;
      switch (track.role) {
        case 'melody': {
          const generatorSettings = this.decodeMelody(track.generatorSettings);
          if (generatorSettings) decoded = { id: track.id, role: 'melody', name: track.name.trim(), midiChannel: track.midiChannel!, mix, generatorSettings, editedPhrase };
          break;
        }
        case 'bass': {
          const generatorSettings = this.decodeBass(track.generatorSettings);
          if (generatorSettings) decoded = { id: track.id, role: 'bass', name: track.name.trim(), midiChannel: track.midiChannel!, mix, generatorSettings, editedPhrase };
          break;
        }
        case 'chords': {
          const generatorSettings = this.decodeChords(track.generatorSettings);
          if (generatorSettings) decoded = { id: track.id, role: 'chords', name: track.name.trim(), midiChannel: track.midiChannel!, mix, generatorSettings, editedPhrase };
          break;
        }
        case 'arpeggio': {
          const generatorSettings = this.decodeArpeggio(track.generatorSettings);
          if (generatorSettings) decoded = { id: track.id, role: 'arpeggio', name: track.name.trim(), midiChannel: track.midiChannel!, mix, generatorSettings, editedPhrase };
          break;
        }
        case 'drums': {
          const generatorSettings = this.decodeDrums(track.generatorSettings);
          if (generatorSettings && track.midiChannel === 10) decoded = { id: track.id, role: 'drums', name: track.name.trim(), midiChannel: 10, mix, generatorSettings, editedPhrase };
          break;
        }
      }
      if (!decoded) return undefined;
      if (track.orderOffset !== undefined) decoded = { ...decoded, orderOffset: track.orderOffset };
      if (track.rerollLocked !== undefined) decoded = { ...decoded, rerollLocked: track.rerollLocked };
      if (track.instrumentLocked !== undefined) decoded = { ...decoded, instrumentLocked: track.instrumentLocked };
      if (track.instrumentPool !== undefined) decoded = { ...decoded, instrumentPool: track.instrumentPool };
      ids.add(decoded.id);
      tracks.push(decoded);
    }
    return tracks;
  }

  private decodeTrackMix(value: unknown): MixChannelSettings | undefined {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
    const settings = value as Partial<MixChannelSettings>;
    if (!Number.isInteger(settings.program) || (settings.program ?? -1) < 0 || (settings.program ?? 128) > 127
        || !validVolumePercent(settings.volumePercent)
        || !Number.isInteger(settings.panPercent) || (settings.panPercent ?? -101) < -100 || (settings.panPercent ?? 101) > 100
        || typeof settings.muted !== 'boolean' || typeof settings.solo !== 'boolean') return undefined;
    return { program: settings.program!, volumePercent: normalizeVolumePercent(settings.volumePercent!), panPercent: settings.panPercent!, muted: settings.muted, solo: settings.solo };
  }

  private decodeEditedPhrases(value: unknown): Partial<Record<ArrangedPart, PhraseNote[]>> | undefined {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return undefined;
    }
    const phrases = value as Partial<Record<ArrangedPart, unknown>>;
    if (Object.keys(phrases).some((part) => !ARRANGED_PARTS.includes(part as ArrangedPart))) {
      return undefined;
    }
    const decoded: Partial<Record<ArrangedPart, PhraseNote[]>> = {};
    for (const part of ARRANGED_PARTS) {
      const notes = phrases[part];
      if (notes === undefined) {
        continue;
      }
      if (!Array.isArray(notes) || !this.isPhrase(notes)) {
        return undefined;
      }
      decoded[part] = notes.map((note) => ({ ...note }));
    }
    return decoded;
  }

  private isPhrase(value: unknown, maximumBeat = 128): value is readonly PhraseNote[] {
    if (!Array.isArray(value) || value.length > 2048) {
      return false;
    }
    const ids = new Set<string>();
    for (const candidate of value) {
      if (!candidate || typeof candidate !== 'object') {
        return false;
      }
      const note = candidate as Partial<PhraseNote>;
      if (typeof note.id !== 'string' || note.id.length < 1 || note.id.length > 120 || ids.has(note.id)
          || !Number.isInteger(note.midi) || (note.midi ?? -1) < 0 || (note.midi ?? 128) > 127
          || typeof note.startBeat !== 'number' || !Number.isFinite(note.startBeat)
          || note.startBeat < 0 || note.startBeat >= maximumBeat
          || typeof note.durationBeats !== 'number' || !Number.isFinite(note.durationBeats)
          || note.durationBeats <= 0 || note.durationBeats > maximumBeat - note.startBeat
          || !Number.isInteger(note.velocity) || (note.velocity ?? 0) < 1 || (note.velocity ?? 128) > 127) {
        return false;
      }
      ids.add(note.id);
    }
    return true;
  }

  private decodeArrangement(value: unknown, tracks: readonly CompositionTrack[], projectSeed: bigint): ArrangementSection[] | undefined {
    if (!Array.isArray(value) || value.length < 1 || value.length > MAX_ARRANGEMENT_SECTIONS) {
      return undefined;
    }
    const sections: ArrangementSection[] = [];
    const ids = new Set<string>();
    const trackIds = new Set(tracks.map((track) => track.id));
    let measureCount = 0;
    for (const candidate of value) {
      if (!candidate || typeof candidate !== 'object') {
        return undefined;
      }
      const section = candidate as Partial<ArrangementSection> & {
        parts?: Partial<Record<ArrangedPart, unknown>>;
        partChances?: Partial<Record<ArrangedPart, unknown>>;
        trackParts?: Record<string, unknown>;
        trackPartChances?: Record<string, unknown>;
      };
      const sectionType = section.type as SectionType;
      const parts = section.parts;
      const chances = section.partChances;
      const trackParts = section.trackParts;
      const trackPartChances = section.trackPartChances;
      const trackPresence = section.trackPresence as Record<string, unknown> | undefined;
      const chordDegrees = section.chordDegrees;
      if ((section.useMelodyHarmony !== undefined && typeof section.useMelodyHarmony !== 'boolean')
        || (section.swapMelody !== undefined && typeof section.swapMelody !== 'boolean')) return undefined;
      if (typeof section.id !== 'string' || section.id.length === 0 || section.id.length > 80 || ids.has(section.id)
          || !SECTION_TYPES.includes(sectionType)
          || !Number.isInteger(section.measures) || (section.measures ?? 0) < 1 || (section.measures ?? 33) > 32
          || !validSectionHarmony(section as ArrangementSection)
          || (chordDegrees !== undefined && (!Array.isArray(chordDegrees) || chordDegrees.length < 1 || chordDegrees.length > 32
            || chordDegrees.some((degree) => !Number.isInteger(degree) || degree < 1 || degree > 7)))
          || (parts !== undefined && (!parts || typeof parts !== 'object' || Array.isArray(parts)
            || ARRANGED_PARTS.some((part) => part === 'melody' && parts[part] === undefined ? false : typeof parts[part] !== 'boolean')))
          || (chances !== undefined && (!chances || typeof chances !== 'object' || Array.isArray(chances)
            || ARRANGED_PARTS.some((part) => part === 'melody' && chances[part] === undefined ? false : !Number.isInteger(chances[part])
              || (chances[part] as number) < 0 || (chances[part] as number) > 100)))
          || (trackParts !== undefined && (!trackParts || typeof trackParts !== 'object' || Array.isArray(trackParts)
            || Object.entries(trackParts).some(([id, included]) => !/^[a-zA-Z0-9_-]{1,64}$/.test(id) || typeof included !== 'boolean')))
          || (trackPartChances !== undefined && (!trackPartChances || typeof trackPartChances !== 'object' || Array.isArray(trackPartChances)
            || Object.entries(trackPartChances).some(([id, chance]) => !/^[a-zA-Z0-9_-]{1,64}$/.test(id)
              || !Number.isInteger(chance) || (chance as number) < 0 || (chance as number) > 100)))
          || (trackPresence !== undefined && (!trackPresence || typeof trackPresence !== 'object' || Array.isArray(trackPresence)
            || Object.entries(trackPresence).some(([id, present]) => !/^[a-zA-Z0-9_-]{1,64}$/.test(id) || typeof present !== 'boolean')))) {
        return undefined;
      }
      if ([...Object.keys(trackParts ?? {}), ...Object.keys(trackPartChances ?? {}), ...Object.keys(trackPresence ?? {})]
        .some((trackId) => !trackIds.has(trackId))) return undefined;
      const rolePartOverrides: Partial<Record<ArrangedPart, PartSettingsPatch>> = {};
      const trackPartOverrides: Record<string, PartSettingsPatch> = {};
      for (const [raw, decoded, byTrack] of [[section.rolePartOverrides, rolePartOverrides, false],
        [section.trackPartOverrides, trackPartOverrides, true]] as const) {
        if (raw === undefined) continue;
        if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return undefined;
        for (const [key, patch] of Object.entries(raw)) {
          const role = byTrack ? tracks.find((track) => track.id === key)?.role : key as ArrangedPart;
          if (!role || !ARRANGED_PARTS.includes(role)) return undefined;
          const result = decodePartPatch(role, patch);
          if (!result) return undefined;
          (decoded as Record<string, PartSettingsPatch>)[key] = result;
        }
      }
      const trackPhrases: Record<string, readonly PhraseNote[]> = {};
      if (section.generatedTrackIds !== undefined && (!Array.isArray(section.generatedTrackIds)
        || section.generatedTrackIds.some(id => typeof id !== 'string' || !trackIds.has(id))
        || new Set(section.generatedTrackIds).size !== section.generatedTrackIds.length)) return undefined;
      if (section.trackPhrases !== undefined) {
        if (!section.trackPhrases || typeof section.trackPhrases !== 'object' || Array.isArray(section.trackPhrases)) return undefined;
        for (const [trackId, notes] of Object.entries(section.trackPhrases)) {
          if (!trackIds.has(trackId) || !this.isPhrase(notes, 2048)) return undefined;
          trackPhrases[trackId] = notes.map(note => ({ ...note }));
        }
      }
      const effectiveSection = { rolePartOverrides, trackPartOverrides } as ArrangementSection;
      if (tracks.some((track) => !validVelocityRange(resolvePartTrack(track, effectiveSection).generatorSettings, track.role))) return undefined;
      ids.add(section.id);
      measureCount += section.measures!;
      if (measureCount > MAX_ARRANGEMENT_MEASURES) {
        return undefined;
      }
      const hasLegacyChanceData = parts !== undefined || chances !== undefined
        || trackParts !== undefined || trackPartChances !== undefined;
      const generatedPresence = hasLegacyChanceData ? {} : generateTrackPresence(tracks, this.sectionTypeSettings.chances()[sectionType]);
      const presence: Record<string, boolean> = {};
      for (const track of tracks) {
        const explicit = trackPresence?.[track.id];
        if (typeof explicit === 'boolean') {
          presence[track.id] = explicit;
        } else if (hasLegacyChanceData) {
          const included = trackParts?.[track.id] ?? parts?.[track.role] ?? true;
          const chance = trackPartChances?.[track.id] ?? chances?.[track.role]
            ?? DEFAULT_SECTION_TYPE_CHANCES[sectionType][track.role];
          presence[track.id] = legacyTrackPresence(projectSeed, track, included as boolean, chance as number);
        } else {
          presence[track.id] = generatedPresence[track.id];
        }
      }
      sections.push({
        id: section.id,
        type: sectionType,
        measures: section.measures!,
        ...(section.context ? { context: { ...section.context } } : {}),
        ...(section.customChords ? { customChords: section.customChords.map(chord => chord ? { ...chord } : null) } : {}),
        ...(section.customDurations ? { customDurations: [...section.customDurations] } : {}),
        chordDegrees: chordDegrees === undefined ? undefined : [...chordDegrees],
        ...(section.useMelodyHarmony !== undefined ? { useMelodyHarmony: section.useMelodyHarmony } : {}),
        ...(section.swapMelody !== undefined ? { swapMelody: section.swapMelody } : {}),
        trackPresence: presence,
        rolePartOverrides: rolePartOverrides as NonNullable<ArrangementSection['rolePartOverrides']>,
        trackPartOverrides,
        ...(section.trackPhrases !== undefined ? { trackPhrases } : {}),
        ...(section.generatedTrackIds !== undefined ? { generatedTrackIds: [...section.generatedTrackIds] } : {}),
      });
    }
    return sections;
  }

  private decodeMelody(value: unknown): MelodySettings | undefined {
    if (!value || typeof value !== 'object' || Array.isArray(value)
        || (value as MelodySettings).algorithm !== 'block') return undefined;
    const { algorithm, ...fields } = value as MelodySettings;
    const decoded = decodePartPatch('melody', fields);
    return decoded && validVelocityRange(decoded, 'melody') ? { algorithm, ...decoded } as MelodySettings : undefined;
  }

  private decodeBass(value: unknown): BassSettings | undefined {
    const common = decodeCommonPartSettings('bass', value);
    if (!common) return undefined;
    if (!value || typeof value !== 'object') {
      return undefined;
    }
    const bass = value as Partial<BassSettings>;
    if (!BASS_RHYTHMS.includes(bass.rhythm as typeof BASS_RHYTHMS[number])
        || !Number.isInteger(bass.noteVariation) || (bass.noteVariation ?? -1) < 0 || (bass.noteVariation ?? 101) > 100
        || (bass.octaveInterval !== undefined && typeof bass.octaveInterval !== 'boolean')) {
      return undefined;
    }
    return { ...common, rhythm: bass.rhythm!, noteVariation: bass.noteVariation!, octaveInterval: bass.octaveInterval ?? false };
  }

  private decodeChords(value: unknown): ChordSettings | undefined {
    const common = decodeCommonPartSettings('chords', value);
    if (!common) return undefined;
    if (!value || typeof value !== 'object') {
      return undefined;
    }
    const chords = value as Partial<ChordSettings>;
    if (!RHYTHM_PATTERNS.includes(chords.rhythm as typeof RHYTHM_PATTERNS[number])
        || !CHORD_VOICINGS.includes(chords.voicing as typeof CHORD_VOICINGS[number])
        || !Number.isInteger(chords.noteLengthPercent)
        || (chords.noteLengthPercent ?? 0) < 25 || (chords.noteLengthPercent ?? 201) > 200) {
      return undefined;
    }
    return {
      ...common,
      rhythm: chords.rhythm!,
      voicing: chords.voicing!,
      noteLengthPercent: chords.noteLengthPercent!,
    };
  }

  private decodeArpeggio(value: unknown): ArpeggioSettings | undefined {
    const common = decodeCommonPartSettings('arpeggio', value);
    if (!common) return undefined;
    if (!value || typeof value !== 'object') {
      return undefined;
    }
    const arpeggio = value as Partial<ArpeggioSettings>;
    if (!ARPEGGIO_PATTERNS.includes(arpeggio.pattern as typeof ARPEGGIO_PATTERNS[number])
        || !ARPEGGIO_RATES.includes(arpeggio.rate as typeof ARPEGGIO_RATES[number])
        || (arpeggio.octaves !== 1 && arpeggio.octaves !== 2)) {
      return undefined;
    }
    return { ...common, pattern: arpeggio.pattern!, rate: arpeggio.rate!, octaves: arpeggio.octaves! };
  }

  private decodeDrums(value: unknown): DrumSettings | undefined {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
    const { pitch, ...fields } = value as DrumSettings;
    const decoded = decodePartPatch('drums', fields);
    return isDrumPitch(pitch) && decoded && fields.rhythm && fields.swingPercent !== undefined
      && validVelocityRange(decoded, 'drums') ? { pitch, ...decoded } as DrumSettings : undefined;
  }

  private copyDefaultProject(): CompositionProject {
    const tracks = DEFAULT_TRACKS.map((track) => ({ ...track, mix: { ...track.mix }, generatorSettings: { ...track.generatorSettings } } as CompositionTrack));
    return {
      ...DEFAULT_PROJECT,
      progression: [...DEFAULT_PROJECT.progression],
      melody: { ...DEFAULT_MELODY_SETTINGS },
      bass: { ...DEFAULT_BASS_SETTINGS },
      chords: { ...DEFAULT_CHORD_SETTINGS },
      arpeggio: { ...DEFAULT_ARPEGGIO_SETTINGS },
      drums: { ...DEFAULT_DRUM_SETTINGS },
      arrangement: this.copyDefaultArrangement(tracks),
      mix: this.copyDefaultMix(),
      tracks,
    };
  }

  private copyDefaultArrangement(tracks: readonly CompositionTrack[]): ArrangementSection[] {
    const chances = this.sectionTypeSettings.chances();
    return DEFAULT_ARRANGEMENT.map((section) => ({
      ...section,
      chordDegrees: section.chordDegrees ? [...section.chordDegrees] : undefined,
      trackPresence: generateTrackPresence(tracks, chances[section.type]),
    }));
  }

  private copyDefaultMix(): Record<ArrangedPart, MixChannelSettings> {
    return {
      melody: { ...DEFAULT_MIX.melody },
      bass: { ...DEFAULT_MIX.bass },
      chords: { ...DEFAULT_MIX.chords },
      arpeggio: { ...DEFAULT_MIX.arpeggio },
      drums: { ...DEFAULT_MIX.drums },
    };
  }

  private isProject(value: unknown): value is CompositionProject {
    if (!value || typeof value !== 'object') {
      return false;
    }
    const project = value as Partial<CompositionProject>;
    const tracks = this.decodeTracks(project.tracks);
    return typeof project.name === 'string'
      && typeof project.key === 'string' && KEYS.includes(project.key)
      && (isScaleMode(project.scale))
      && (project.transposeSemitones === undefined || (Number.isInteger(project.transposeSemitones) && project.transposeSemitones >= -24 && project.transposeSemitones <= 24))
      && (project.randomizeTempoOnCompose === undefined || typeof project.randomizeTempoOnCompose === 'boolean')
      && (project.randomizeTransposeOnCompose === undefined || typeof project.randomizeTransposeOnCompose === 'boolean')
      && Number.isInteger(project.tempoBpm) && (project.tempoBpm ?? 0) >= 30 && (project.tempoBpm ?? 0) <= 240
      && typeof project.seed === 'string' && /^-?\d+$/.test(project.seed)
      && BigInt(project.seed) >= -(1n << 63n) && BigInt(project.seed) <= (1n << 63n) - 1n
      && Array.isArray(project.progression)
      && project.progression.length > 0
      && project.progression.length <= 32
      && project.progression.every((degree) => Number.isInteger(degree) && degree >= 1 && degree <= 7)
      && validProgressionChords(project.progressionChords, project.progression.length)
      && this.decodeMelody(project.melody) !== undefined
      && this.decodeBass(project.bass) !== undefined
      && this.decodeChords(project.chords) !== undefined
      && this.decodeArpeggio(project.arpeggio) !== undefined
      && this.decodeDrums(project.drums) !== undefined
      && tracks !== undefined
      && this.decodeArrangement(project.arrangement, tracks, BigInt(project.seed!)) !== undefined
      && this.decodeEditedPhrases(project.editedPhrases) !== undefined
      && this.decodeMix(project.mix) !== undefined
      && validTrackGenerationMetadata(project.trackGenerationPolicies, project.trackRerollCounts)
      && validInstrumentPolicies(project.instrumentGenerationPolicies)
      && (project.harmonySettings === undefined || decodeHarmonySettings(project.harmonySettings) !== undefined)
      && (project.melodyGlobal === undefined || decodeMelodyGlobal(project.melodyGlobal) !== undefined);
  }

  private persist(project: CompositionProject): void {
    try {
      globalThis.sessionStorage?.setItem(STORAGE_KEY, JSON.stringify(project));
    } catch {
      // Keep the project usable if storage is disabled or full.
    }
  }

  private matchesMainProgression(chordDegrees: readonly number[]): boolean {
    if (this.state().progressionChords?.some(Boolean)) return false;
    const progression = this.state().progression;
    return chordDegrees.every((degree, index) => degree === progression[index % progression.length]);
  }
}
