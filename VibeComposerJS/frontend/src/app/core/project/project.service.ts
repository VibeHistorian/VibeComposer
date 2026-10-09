import { inject, Injectable, signal } from '@angular/core';
import { KEYS, getDiatonicChords } from '../music/harmony';
import { generateTrackPresence, legacyTrackPresence } from '../music/arrangement-generator';
import type {
  ArpeggioSettings, ArrangedPart, ArrangementSection, BassSettings, ChordSettings, CompositionTrack, DrumSettings,
  MixChannelSettings, PhraseNote, SectionType, PartSettingsPatch, PartSettingsScope, MelodySettings,
  LocalPartSettingsScope, PartWorkflowResult,
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
import { DEFAULT_TRACK_POLICIES, decodeTrackGenerationPolicy, rerollRoleSettings, validTrackGenerationMetadata } from '../music/track-generation';

const STORAGE_KEY = 'vibecomposer.project.v12';
const LEGACY_STORAGE_KEY = 'vibecomposer.project.v11';
const MAX_HISTORY = 100;
const MAX_ARRANGEMENT_SECTIONS = 32;
const MAX_ARRANGEMENT_MEASURES = 128;
const CHORD_VOICINGS = ['close', 'open'] as const;
const ARPEGGIO_PATTERNS = ['up', 'down', 'up-down', 'random'] as const;
const ARPEGGIO_RATES = ['eighth', 'sixteenth'] as const;

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly sectionTypeSettings = inject(SectionTypeSettingsService);
  private readonly state = signal<CompositionProject>(this.loadProject());
  private readonly past: CompositionProject[] = [];
  private readonly future: CompositionProject[] = [];
  private readonly undoAvailable = signal(false);
  private readonly redoAvailable = signal(false);

  readonly project = this.state.asReadonly();
  readonly canUndo = this.undoAvailable.asReadonly();
  readonly canRedo = this.redoAvailable.asReadonly();

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
    return this.commitPartWorkflow({ ...current, trackGenerationPolicies: { ...current.trackGenerationPolicies, [role]: policy } });
  }

  setTrackRerollLock(trackId: string, locked: boolean): void {
    if (typeof locked !== 'boolean') return;
    const current = this.state();
    const track = current.tracks.find(item => item.id === trackId);
    if (!track || (track.rerollLocked ?? false) === locked) return;
    this.commit({ ...current, tracks: current.tracks.map(item => item.id === trackId ? { ...item, rerollLocked: locked } : item) });
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
        mix: { ...source.mix, muted: false, solo: false }, rerollLocked: false, editedPhrase: undefined } as CompositionTrack;
      created.push(track); tracks.push(track);
    }
    const policy = current.trackGenerationPolicies?.[role] ?? DEFAULT_TRACK_POLICIES[role];
    const seed = this.trackRerollSeed(current, role, count);
    const members = tracks.filter(track => track.role === role);
    const settings = rerollRoleSettings(seed, role, policy, created, created.map(track => members.indexOf(track) + 1));
    const generated = created.map((track, index) => ({ ...track, generatorSettings: settings[index] } as CompositionTrack));
    const replacements = new Map(generated.map(track => [track.id, track]));
    const primary = replacements.get(`track-${role}-1`);
    const presenceRandom = new JavaRandom(BigInt.asIntN(64, seed ^ 0x50524553454e4345n));
    return this.commitPartWorkflow({ ...current, tracks: tracks.map(track => replacements.get(track.id) ?? track),
      [role]: primary?.generatorSettings ?? current[role],
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
    const current = this.state();
    const roleTracks = current.tracks.filter(track => track.role === role);
    const sources = roleTracks.filter(track => !track.rerollLocked && (trackId === undefined || track.id === trackId));
    if (!sources.length) return 'unchanged';
    const policy = current.trackGenerationPolicies?.[role] ?? DEFAULT_TRACK_POLICIES[role];
    const count = current.trackRerollCounts?.[role] ?? 0;
    if (count >= 2147483647) return 'invalid';
    const seed = this.trackRerollSeed(current, role, count);
    const settings = rerollRoleSettings(seed, role, policy, sources, sources.map(track => roleTracks.indexOf(track) + 1));
    if (sources.every((track, index) => partPatchesEqual(track.generatorSettings, settings[index]))) return 'unchanged';
    const replacements = new Map(sources.map((track, index) => [track.id, { ...track, generatorSettings: settings[index] } as CompositionTrack]));
    const primary = replacements.get('track-' + role + '-1');
    const next = { ...current, tracks: current.tracks.map(track => replacements.get(track.id) ?? track),
      [role]: primary?.generatorSettings ?? current[role],
      trackRerollCounts: { ...current.trackRerollCounts, [role]: count + 1 } };
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
    this.commit({ ...current, arrangement });
  }

  resetPartSettings(scope: PartSettingsScope, field?: string): void {
    if (scope.kind !== 'section-role' && scope.kind !== 'section-track') return;
    const current = this.state();
    const arrangement = current.arrangement.map((section) => {
      if (section.id !== scope.sectionId) return section;
      const key = scope.kind === 'section-role' ? scope.role : scope.trackId;
      const map: Record<string, PartSettingsPatch> = { ...(scope.kind === 'section-role' ? section.rolePartOverrides : section.trackPartOverrides) };
      const values: Record<string, unknown> = { ...map[key] };
      if (field) delete values[field];
      else for (const name of Object.keys(values)) delete values[name];
      if (Object.keys(values).length) (map as Record<string, PartSettingsPatch>)[key] = values as PartSettingsPatch;
      else delete (map as Record<string, PartSettingsPatch>)[key];
      return scope.kind === 'section-role' ? { ...section, rolePartOverrides: map as NonNullable<ArrangementSection['rolePartOverrides']> }
        : { ...section, trackPartOverrides: map };
    });
    this.commit({ ...current, arrangement });
  }

  /** Snapshot each current member independently; mixed cells never pick a representative track. */
  freezePartSettings(scope: LocalPartSettingsScope): PartWorkflowResult {
    return this.copyLocalPartSettings(scope, [scope.sectionId], 'effective', true);
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

  private commitPartWorkflow(next: CompositionProject): PartWorkflowResult {
    // Validate the complete candidate before committing: a conflict in one destination rejects the entire batch.
    if (!this.isProject(next)) return 'invalid';
    const before = this.state();
    this.commit(next);
    return before === this.state() ? 'unchanged' : 'changed';
  }

  importProjectJson(json: string): boolean {
    try {
      const project = this.decodeProject(JSON.parse(json));
      if (!project) {
        return false;
      }
      this.commit(project);
      return true;
    } catch {
      return false;
    }
  }

  updateSettings(patch: Partial<Pick<CompositionProject, 'name' | 'key' | 'scale' | 'transposeSemitones' | 'tempoBpm' | 'seed'>>): void {
    if (patch.transposeSemitones !== undefined
        && (!Number.isInteger(patch.transposeSemitones) || patch.transposeSemitones < -24 || patch.transposeSemitones > 24)) return;
    this.commit({ ...this.state(), ...patch });
  }

  updateRoleGeneratorSettings(role: ArrangedPart, patch: PartSettingsPatch): void {
    const current = this.state();
    if (role === 'drums' && 'pitch' in patch) return;
    if (!current.tracks.some((track) => track.role === role)) return;
    if (role === 'melody') {
      const melody = this.decodeMelody({ ...current.melody, ...patch });
      if (!melody) return;
      this.commit({ ...current, melody, tracks: current.tracks.map((track) => track.role === role
        ? { ...track, generatorSettings: melody } : track) });
    } else if (role === 'bass') {
      const bass = { ...current.bass, ...patch } as BassSettings;
      if (!this.decodeBass(bass)) return;
      this.commit({ ...current, bass, tracks: current.tracks.map((track) => track.role === role
        ? { ...track, generatorSettings: { ...track.generatorSettings, ...patch } as BassSettings } : track) });
    } else if (role === 'chords') {
      const chords = { ...current.chords, ...patch } as ChordSettings;
      if (!this.decodeChords(chords)) return;
      this.commit({ ...current, chords, tracks: current.tracks.map((track) => track.role === role
        ? { ...track, generatorSettings: { ...track.generatorSettings, ...patch } as ChordSettings } : track) });
    } else if (role === 'arpeggio') {
      const arpeggio = { ...current.arpeggio, ...patch } as ArpeggioSettings;
      if (!this.decodeArpeggio(arpeggio)) return;
      this.commit({ ...current, arpeggio, tracks: current.tracks.map((track) => track.role === role
        ? { ...track, generatorSettings: { ...track.generatorSettings, ...patch } as ArpeggioSettings } : track) });
    } else {
      const drums = { ...current.drums, ...patch } as DrumSettings;
      if (!this.decodeDrums(drums)) return;
      this.commit({ ...current, drums, tracks: current.tracks.map((track) => track.role === role
        ? { ...track, generatorSettings: { ...track.generatorSettings, ...patch } as DrumSettings } : track) });
    }
  }

  updateBassSettings(patch: Partial<BassSettings>): void {
    const current = this.state();
    const bass = { ...current.bass, ...patch };
    this.commit({ ...current, bass, tracks: this.updatePrimaryTrack(current.tracks, 'bass', { generatorSettings: bass }) });
  }

  updateChordSettings(patch: Partial<ChordSettings>): void {
    const current = this.state();
    const chords = { ...current.chords, ...patch };
    this.commit({ ...current, chords, tracks: this.updatePrimaryTrack(current.tracks, 'chords', { generatorSettings: chords }) });
  }

  updateArpeggioSettings(patch: Partial<ArpeggioSettings>): void {
    const current = this.state();
    const arpeggio = { ...current.arpeggio, ...patch };
    this.commit({ ...current, arpeggio, tracks: this.updatePrimaryTrack(current.tracks, 'arpeggio', { generatorSettings: arpeggio }) });
  }

  updateDrumSettings(patch: Partial<DrumSettings>): void {
    const current = this.state();
    const drums = { ...current.drums, ...patch };
    this.commit({ ...current, drums, tracks: this.updatePrimaryTrack(current.tracks, 'drums', { generatorSettings: drums }) });
  }

  updateMixSettings(part: ArrangedPart, patch: Partial<MixChannelSettings>): void {
    if (!ARRANGED_PARTS.includes(part)) {
      return;
    }
    const current = this.state();
    const mix = { ...current.mix, [part]: { ...current.mix[part], ...patch } };
    this.commit({ ...current, mix, tracks: this.updatePrimaryTrack(current.tracks, part, { mix: mix[part] }) });
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
        || (patch.mix?.volumePercent !== undefined && (!Number.isInteger(patch.mix.volumePercent) || patch.mix.volumePercent < 0 || patch.mix.volumePercent > 100))
        || (patch.mix?.panPercent !== undefined && (!Number.isInteger(patch.mix.panPercent) || patch.mix.panPercent < -100 || patch.mix.panPercent > 100))
        || (patch.mix?.muted !== undefined && typeof patch.mix.muted !== 'boolean')
        || (patch.mix?.solo !== undefined && typeof patch.mix.solo !== 'boolean')) return;
    const tracks = [...current.tracks];
    const track = tracks[index];
    if (track.role === 'drums' && ((patch.midiChannel !== undefined && patch.midiChannel !== 10)
      || patch.mix?.program !== undefined)) return;
    tracks[index] = { ...track, ...patch, name: patch.name?.trim() ?? track.name,
      mix: patch.mix ? { ...track.mix, ...patch.mix } : track.mix };
    const isPrimary = current.tracks.find((candidate) => candidate.role === track.role)?.id === track.id;
    const mix = patch.mix && isPrimary ? { ...current.mix, [track.role]: tracks[index].mix } : current.mix;
    this.commit({ ...current, tracks, mix });
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
    this.commit(next);
  }

  updateTrackPhrase(trackId: string, notes: readonly PhraseNote[]): void {
    const current = this.state();
    const index = current.tracks.findIndex((track) => track.id === trackId);
    if (index < 0 || !this.isPhrase(notes)) return;
    const tracks = [...current.tracks];
    tracks[index] = { ...tracks[index], editedPhrase: notes.map((note) => ({ ...note })) } as CompositionTrack;
    const isPrimary = current.tracks.find((candidate) => candidate.role === tracks[index].role)?.id === trackId;
    const editedPhrases = isPrimary ? { ...current.editedPhrases, [tracks[index].role]: tracks[index].editedPhrase } : current.editedPhrases;
    this.commit({ ...current, tracks, editedPhrases });
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
      return { ...section, trackPresence, trackPartOverrides };
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
      chordDegrees: sections[index].chordDegrees ? [...sections[index].chordDegrees] : undefined,
      trackPresence: { ...sections[index].trackPresence },
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
    if (patch.measures !== undefined) {
      const progression = this.state().progression;
      const chordDegrees = Array.from({ length: patch.measures }, (_, measure) =>
        current.chordDegrees?.[measure] ?? progression[measure % progression.length]);
      updated.chordDegrees = this.matchesMainProgression(chordDegrees) ? undefined : chordDegrees;
    }
    next[index] = updated;
    this.commit({ ...this.state(), arrangement: next });
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
    if (!section?.chordDegrees) return;
    const next = [...project.arrangement];
    next[sectionIndex] = { ...section, chordDegrees: undefined };
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
    this.commit({ ...this.state(), progression });
  }

  setProgression(progression: readonly number[]): void {
    const chords = getDiatonicChords(this.state().key, this.state().scale);
    if (progression.length < 1 || progression.length > 32
        || progression.some((degree) => !chords.some((chord) => chord.degree === degree))) {
      return;
    }
    this.commit({ ...this.state(), progression: [...progression] });
  }

  addChord(): void {
    if (this.state().progression.length >= 32) {
      return;
    }
    const progression = [...this.state().progression, 1];
    this.commit({ ...this.state(), progression });
  }

  removeChord(index: number): void {
    const progression = this.state().progression.filter((_, chordIndex) => chordIndex !== index);
    if (progression.length > 0 && progression.length !== this.state().progression.length) {
      this.commit({ ...this.state(), progression });
    }
  }

  resetProgression(): void {
    this.commit({ ...this.state(), progression: [...DEFAULT_PROJECT.progression] });
  }

  undo(): void {
    const previous = this.past.pop();
    if (!previous) {
      return;
    }
    this.future.push(this.state());
    this.state.set(previous);
    this.persist(previous);
    this.refreshHistoryAvailability();
  }

  redo(): void {
    const next = this.future.pop();
    if (!next) {
      return;
    }
    this.past.push(this.state());
    this.state.set(next);
    this.persist(next);
    this.refreshHistoryAvailability();
  }

  private commit(next: CompositionProject): void {
    const current = this.state();
    if (!this.isProject(next) || JSON.stringify(current) === JSON.stringify(next)) {
      return;
    }

    this.past.push(current);
    if (this.past.length > MAX_HISTORY) {
      this.past.shift();
    }
    this.future.length = 0;
    this.state.set(next);
    this.persist(next);
    this.refreshHistoryAvailability();
  }

  private refreshHistoryAvailability(): void {
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
      return { ...track, ...patch } as CompositionTrack;
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
        const decoded = this.decodeProject(JSON.parse(raw));
        if (decoded) {
          if (key !== STORAGE_KEY) this.persist(decoded);
          return decoded;
        }
      } catch {
        // Storage can be unavailable or contain invalid JSON.
      }
    }
    const initial = this.copyDefaultProject();
    this.persist(initial);
    return initial;
  }

  private decodeProject(value: unknown): CompositionProject | undefined {
    if (!value || typeof value !== 'object') {
      return undefined;
    }

    const project = value as Partial<CompositionProject> & { bass?: unknown; chords?: unknown; arpeggio?: unknown; drums?: unknown; tracks?: unknown };
    const validBase = typeof project.name === 'string'
      && typeof project.key === 'string' && KEYS.includes(project.key)
      && (project.scale === 'major' || project.scale === 'natural-minor')
      && (project.transposeSemitones === undefined || (Number.isInteger(project.transposeSemitones) && project.transposeSemitones >= -24 && project.transposeSemitones <= 24))
      && Number.isInteger(project.tempoBpm) && (project.tempoBpm ?? 0) >= 40 && (project.tempoBpm ?? 0) <= 240
      && typeof project.seed === 'string' && /^-?\d+$/.test(project.seed)
      && BigInt(project.seed) >= -(1n << 63n) && BigInt(project.seed) <= (1n << 63n) - 1n
      && Array.isArray(project.progression)
      && project.progression.length > 0 && project.progression.length <= 32
      && project.progression.every((degree) => Number.isInteger(degree) && degree >= 1 && degree <= 7);
    if (!validBase || !validTrackGenerationMetadata(project.trackGenerationPolicies, project.trackRerollCounts)) {
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
      seed: project.seed!,
      progression: [...project.progression!],
      melody,
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
          || !Number.isInteger(settings.volumePercent) || (settings.volumePercent ?? -1) < 0 || (settings.volumePercent ?? 101) > 100
          || !Number.isInteger(settings.panPercent) || (settings.panPercent ?? -101) < -100 || (settings.panPercent ?? 101) > 100
          || typeof settings.muted !== 'boolean' || typeof settings.solo !== 'boolean') {
        return undefined;
      }
      decoded[part] = {
        program: settings.program!, volumePercent: settings.volumePercent!, panPercent: settings.panPercent!,
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
      const editedPhrase = track.editedPhrase === undefined ? undefined
        : this.isPhrase(track.editedPhrase) ? track.editedPhrase.map((note) => ({ ...note })) : null;
      if (!mix || editedPhrase === null || (track.rerollLocked !== undefined && typeof track.rerollLocked !== 'boolean')) return undefined;
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
      if (track.rerollLocked !== undefined) decoded = { ...decoded, rerollLocked: track.rerollLocked };
      ids.add(decoded.id);
      tracks.push(decoded);
    }
    return tracks;
  }

  private decodeTrackMix(value: unknown): MixChannelSettings | undefined {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined;
    const settings = value as Partial<MixChannelSettings>;
    if (!Number.isInteger(settings.program) || (settings.program ?? -1) < 0 || (settings.program ?? 128) > 127
        || !Number.isInteger(settings.volumePercent) || (settings.volumePercent ?? -1) < 0 || (settings.volumePercent ?? 101) > 100
        || !Number.isInteger(settings.panPercent) || (settings.panPercent ?? -101) < -100 || (settings.panPercent ?? 101) > 100
        || typeof settings.muted !== 'boolean' || typeof settings.solo !== 'boolean') return undefined;
    return { program: settings.program!, volumePercent: settings.volumePercent!, panPercent: settings.panPercent!, muted: settings.muted, solo: settings.solo };
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

  private isPhrase(value: unknown): value is readonly PhraseNote[] {
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
          || note.startBeat < 0 || note.startBeat >= 128
          || typeof note.durationBeats !== 'number' || !Number.isFinite(note.durationBeats)
          || note.durationBeats <= 0 || note.durationBeats > 128 - note.startBeat
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
      if (typeof section.id !== 'string' || section.id.length === 0 || section.id.length > 80 || ids.has(section.id)
          || !SECTION_TYPES.includes(sectionType)
          || !Number.isInteger(section.measures) || (section.measures ?? 0) < 1 || (section.measures ?? 33) > 32
          || (chordDegrees !== undefined && (!Array.isArray(chordDegrees) || chordDegrees.length !== section.measures
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
        chordDegrees: chordDegrees === undefined ? undefined : [...chordDegrees],
        trackPresence: presence,
        rolePartOverrides: rolePartOverrides as NonNullable<ArrangementSection['rolePartOverrides']>,
        trackPartOverrides,
      });
    }
    return sections;
  }

  private decodeMelody(value: unknown): MelodySettings | undefined {
    if (!value || typeof value !== 'object' || Array.isArray(value)
        || (value as MelodySettings).algorithm !== 'block') return undefined;
    const { algorithm, ...fields } = value as MelodySettings;
    const decoded = decodePartPatch('melody', fields);
    return decoded && validVelocityRange(decoded, 'melody') ? { algorithm, ...decoded } : undefined;
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
      && (project.scale === 'major' || project.scale === 'natural-minor')
      && (project.transposeSemitones === undefined || (Number.isInteger(project.transposeSemitones) && project.transposeSemitones >= -24 && project.transposeSemitones <= 24))
      && Number.isInteger(project.tempoBpm) && (project.tempoBpm ?? 0) >= 40 && (project.tempoBpm ?? 0) <= 240
      && typeof project.seed === 'string' && /^-?\d+$/.test(project.seed)
      && BigInt(project.seed) >= -(1n << 63n) && BigInt(project.seed) <= (1n << 63n) - 1n
      && Array.isArray(project.progression)
      && project.progression.length > 0
      && project.progression.length <= 32
      && project.progression.every((degree) => Number.isInteger(degree) && degree >= 1 && degree <= 7)
      && this.decodeMelody(project.melody) !== undefined
      && this.decodeBass(project.bass) !== undefined
      && this.decodeChords(project.chords) !== undefined
      && this.decodeArpeggio(project.arpeggio) !== undefined
      && this.decodeDrums(project.drums) !== undefined
      && tracks !== undefined
      && this.decodeArrangement(project.arrangement, tracks, BigInt(project.seed!)) !== undefined
      && this.decodeEditedPhrases(project.editedPhrases) !== undefined
      && this.decodeMix(project.mix) !== undefined
      && validTrackGenerationMetadata(project.trackGenerationPolicies, project.trackRerollCounts);
  }

  private persist(project: CompositionProject): void {
    try {
      globalThis.sessionStorage?.setItem(STORAGE_KEY, JSON.stringify(project));
    } catch {
      // Keep the project usable if storage is disabled or full.
    }
  }

  private matchesMainProgression(chordDegrees: readonly number[]): boolean {
    const progression = this.state().progression;
    return chordDegrees.every((degree, index) => degree === progression[index % progression.length]);
  }
}
