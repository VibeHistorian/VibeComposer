import { Injectable, signal } from '@angular/core';
import { KEYS, getDiatonicChords } from '../music/harmony';
import type {
  ArpeggioSettings, ArrangedPart, ArrangementSection, BassSettings, ChordSettings, CompositionTrack, DrumSettings,
  MixChannelSettings, PhraseNote, SectionType,
} from './project.model';
import type { CompositionProject } from './project.model';
import {
  ARRANGED_PARTS, DEFAULT_ARPEGGIO_SETTINGS, DEFAULT_ARRANGEMENT, DEFAULT_BASS_SETTINGS, DEFAULT_CHORD_SETTINGS,
  DEFAULT_DRUM_SETTINGS, DEFAULT_MIX, DEFAULT_PROJECT, DEFAULT_SECTION_PART_CHANCES, DEFAULT_TRACKS, SECTION_TYPES,
} from './project.model';

const STORAGE_KEY = 'vibecomposer.project.v11';
const MAX_HISTORY = 100;
const MAX_ARRANGEMENT_SECTIONS = 32;
const MAX_ARRANGEMENT_MEASURES = 128;
const BASS_RHYTHMS = ['alternating', 'full', 'half', 'tresillo', 'sparse'] as const;
const CHORD_RHYTHMS = ['full', 'half', 'tresillo', 'sparse', 'single'] as const;
const CHORD_VOICINGS = ['close', 'open'] as const;
const ARPEGGIO_PATTERNS = ['up', 'down', 'up-down', 'random'] as const;
const ARPEGGIO_RATES = ['eighth', 'sixteenth'] as const;
const DRUM_GROOVES = ['rock', 'four-on-floor', 'half-time', 'sparse'] as const;

@Injectable({ providedIn: 'root' })
export class ProjectService {
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

  updateRoleGeneratorSettings(role: ArrangedPart, patch: Partial<BassSettings | ChordSettings | ArpeggioSettings | DrumSettings>): void {
    const current = this.state();
    if (!current.tracks.some((track) => track.role === role)) return;
    if (role === 'bass') {
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
    const source = current.tracks.find((track) => track.role === role);
    if (!source) return undefined;
    const nextNumber = current.tracks.reduce((maximum, track) => {
      const match = new RegExp(`^track-${role}-(\\d+)$`).exec(track.id);
      return match ? Math.max(maximum, Number(match[1])) : maximum;
    }, 0) + 1;
    const id = `track-${role}-${nextNumber}`;
    const channel = role === 'drums' ? 10 : this.nextAvailableChannel(current.tracks);
    const track = { ...source, id, name: `${role[0].toUpperCase()}${nextNumber}`, midiChannel: channel,
      mix: { ...source.mix, muted: false, solo: false },
      editedPhrase: source.editedPhrase?.map((note) => ({ ...note })) } as CompositionTrack;
    const arrangement = current.arrangement.map((section) => ({
      ...section,
      trackParts: section.trackParts ? { ...section.trackParts, [id]: section.trackParts[source.id] ?? section.parts[role] } : undefined,
      trackPartChances: section.trackPartChances ? { ...section.trackPartChances, [id]: section.trackPartChances[source.id] ?? section.partChances[role] } : undefined,
      trackPresence: section.trackPresence ? { ...section.trackPresence } : undefined,
    }));
    this.commit({ ...current, tracks: [...current.tracks, track], arrangement });
    return id;
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
      generatorSettings: { ...source.generatorSettings },
      editedPhrase: source.editedPhrase?.map((note) => ({ ...note })) } as CompositionTrack;
    const tracks = [...current.tracks];
    tracks.splice(sourceIndex + 1, 0, duplicate);
    this.commit({ ...current, tracks, arrangement: current.arrangement.map((section) => ({
      ...section,
      trackParts: section.trackParts ? { ...section.trackParts, [id]: section.trackParts[source.id] ?? section.parts[source.role] } : undefined,
      trackPartChances: section.trackPartChances ? { ...section.trackPartChances, [id]: section.trackPartChances[source.id] ?? section.partChances[source.role] } : undefined,
      trackPresence: section.trackPresence ? { ...section.trackPresence } : undefined,
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
    tracks[index] = { ...track, ...patch, name: patch.name?.trim() ?? track.name,
      mix: patch.mix ? { ...track.mix, ...patch.mix } : track.mix };
    const isPrimary = current.tracks.find((candidate) => candidate.role === track.role)?.id === track.id;
    const mix = patch.mix && isPrimary ? { ...current.mix, [track.role]: tracks[index].mix } : current.mix;
    this.commit({ ...current, tracks, mix });
  }

  updateTrackGeneratorSettings(trackId: string, patch: Partial<BassSettings | ChordSettings | ArpeggioSettings | DrumSettings>): void {
    const current = this.state();
    const index = current.tracks.findIndex((track) => track.id === trackId);
    if (index < 0) return;
    const tracks = [...current.tracks];
    const track = tracks[index];
    const generatorSettings = { ...track.generatorSettings, ...patch };
    const validSettings = track.role === 'bass' ? this.decodeBass(generatorSettings) !== undefined
      : track.role === 'chords' ? this.decodeChords(generatorSettings) !== undefined
        : track.role === 'arpeggio' ? this.decodeArpeggio(generatorSettings) !== undefined
          : this.decodeDrums(generatorSettings) !== undefined;
    if (!validSettings) return;
    tracks[index] = { ...track, generatorSettings } as CompositionTrack;
    const isPrimary = current.tracks.find((candidate) => candidate.role === track.role)?.id === track.id;
    const legacySettings = isPrimary ? tracks[index].generatorSettings : undefined;
    const next = track.role === 'bass' && legacySettings ? { ...current, bass: legacySettings as BassSettings, tracks }
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
    if (!track || current.tracks.filter((candidate) => candidate.role === track.role).length <= 1) return;
    const arrangement = current.arrangement.map((section) => {
      const trackParts = section.trackParts ? { ...section.trackParts } : undefined;
      const trackPartChances = section.trackPartChances ? { ...section.trackPartChances } : undefined;
      const trackPresence = section.trackPresence ? { ...section.trackPresence } : undefined;
      if (trackParts) delete trackParts[trackId];
      if (trackPartChances) delete trackPartChances[trackId];
      if (trackPresence) delete trackPresence[trackId];
      return { ...section, trackParts, trackPartChances, trackPresence };
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
      parts: { bass: true, chords: true, arpeggio: true, drums: true },
      partChances: { ...DEFAULT_SECTION_PART_CHANCES[type] },
      trackParts: Object.fromEntries(project.tracks.map((track) => [track.id, true])),
      trackPartChances: Object.fromEntries(project.tracks.map((track) => [track.id, DEFAULT_SECTION_PART_CHANCES[type][track.role]])),
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
      parts: { ...sections[index].parts }, partChances: { ...sections[index].partChances },
      trackParts: sections[index].trackParts ? { ...sections[index].trackParts } : undefined,
      trackPartChances: sections[index].trackPartChances ? { ...sections[index].trackPartChances } : undefined,
      trackPresence: sections[index].trackPresence ? { ...sections[index].trackPresence } : undefined,
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

  setSectionPart(index: number, part: ArrangedPart, included: boolean): void {
    const sections = this.state().arrangement;
    if (!Number.isInteger(index) || index < 0 || index >= sections.length || !ARRANGED_PARTS.includes(part)) {
      return;
    }
    const next = [...sections];
    const trackParts = { ...sections[index].trackParts };
    for (const track of this.state().tracks.filter((candidate) => candidate.role === part)) {
      trackParts[track.id] = included;
    }
    next[index] = { ...sections[index], parts: { ...sections[index].parts, [part]: included }, trackParts };
    this.commit({ ...this.state(), arrangement: next });
  }

  setSectionPartChance(index: number, part: ArrangedPart, chancePercent: number): void {
    const sections = this.state().arrangement;
    if (!Number.isInteger(index) || index < 0 || index >= sections.length || !ARRANGED_PARTS.includes(part)
        || !Number.isInteger(chancePercent) || chancePercent < 0 || chancePercent > 100) {
      return;
    }
    const next = [...sections];
    const trackPartChances = { ...sections[index].trackPartChances };
    const trackPresence = { ...sections[index].trackPresence };
    for (const track of this.state().tracks.filter((candidate) => candidate.role === part)) {
      trackPartChances[track.id] = chancePercent;
      delete trackPresence[track.id];
    }
    next[index] = {
      ...sections[index],
      partChances: { ...sections[index].partChances, [part]: chancePercent },
      trackPartChances,
      trackPresence,
    };
    this.commit({ ...this.state(), arrangement: next });
  }

  setSectionTrackPresence(index: number, trackId: string, present: boolean): void {
    const project = this.state();
    const section = project.arrangement[index];
    if (!section || !project.tracks.some((candidate) => candidate.id === trackId)) return;
    const trackPresence = { ...section.trackPresence, [trackId]: present };
    this.commit({ ...project, arrangement: project.arrangement.map((candidate, candidateIndex) => candidateIndex === index
      ? { ...section, trackPresence } : candidate) });
  }

  setSectionTrackChance(index: number, trackId: string, chancePercent: number): void {
    const project = this.state();
    const section = project.arrangement[index];
    const track = project.tracks.find((candidate) => candidate.id === trackId);
    if (!section || !track || !Number.isInteger(chancePercent) || chancePercent < 0 || chancePercent > 100) return;
    const trackPartChances = { ...section.trackPartChances, [trackId]: chancePercent };
    const trackPresence = { ...section.trackPresence };
    delete trackPresence[trackId];
    const sameRoleTracks = project.tracks.filter((candidate) => candidate.role === track.role);
    const sameChance = sameRoleTracks.every((candidate) => (trackPartChances[candidate.id] ?? section.partChances[track.role]) === chancePercent);
    const partChances = { ...section.partChances, [track.role]: sameChance ? chancePercent : section.partChances[track.role] };
    this.commit({ ...project, arrangement: project.arrangement.map((candidate, candidateIndex) => candidateIndex === index
      ? { ...section, partChances, trackPartChances, trackPresence } : candidate) });
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
    patch: { generatorSettings?: BassSettings | ChordSettings | ArpeggioSettings | DrumSettings;
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
    try {
      const raw = globalThis.sessionStorage?.getItem(STORAGE_KEY);
      if (raw) {
        const decoded = this.decodeProject(JSON.parse(raw));
        if (decoded) return decoded;
      }
    } catch {
      // Storage can be unavailable or contain invalid JSON.
    }
    return this.copyDefaultProject();
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
    if (!validBase) {
      return undefined;
    }

    const bass = this.decodeBass(project.bass);
    const chords = this.decodeChords(project.chords);
    const arpeggio = this.decodeArpeggio(project.arpeggio);
    const drums = this.decodeDrums(project.drums);
    const arrangement = this.decodeArrangement(project.arrangement);
    const editedPhrases = this.decodeEditedPhrases(project.editedPhrases);
    const mix = this.decodeMix(project.mix);
    const tracks = this.decodeTracks(project.tracks);
    if (!bass || !chords || !arpeggio || !drums || !arrangement || !editedPhrases || !mix || !tracks) {
      return undefined;
    }
    const trackIds = new Set(tracks.map((track) => track.id));
    if (arrangement.some((section) => [...Object.keys(section.trackParts ?? {}), ...Object.keys(section.trackPartChances ?? {}),
      ...Object.keys(section.trackPresence ?? {})]
      .some((trackId) => !trackIds.has(trackId)))) return undefined;
    return {
      schemaVersion: 11,
      name: project.name!,
      key: project.key!,
      scale: project.scale!,
      transposeSemitones: project.transposeSemitones ?? 0,
      tempoBpm: project.tempoBpm!,
      seed: project.seed!,
      progression: [...project.progression!],
      bass,
      chords,
      arpeggio,
      drums,
      editedPhrases,
      mix,
      tracks,
      arrangement,
    };
  }

  private decodeMix(value: unknown): Record<ArrangedPart, MixChannelSettings> | undefined {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return undefined;
    }
    const mix = value as Partial<Record<ArrangedPart, unknown>>;
    if (Object.keys(mix).length !== ARRANGED_PARTS.length
        || ARRANGED_PARTS.some((part) => !mix[part] || typeof mix[part] !== 'object')) {
      return undefined;
    }
    const decoded = {} as Record<ArrangedPart, MixChannelSettings>;
    for (const part of ARRANGED_PARTS) {
      const settings = mix[part] as Partial<MixChannelSettings>;
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
    if (!Array.isArray(value) || value.length === 0 || value.length > 64) return undefined;
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
      if (!mix || editedPhrase === null) return undefined;
      let decoded: CompositionTrack | undefined;
      switch (track.role) {
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
          if (generatorSettings) decoded = { id: track.id, role: 'drums', name: track.name.trim(), midiChannel: track.midiChannel!, mix, generatorSettings, editedPhrase };
          break;
        }
      }
      if (!decoded) return undefined;
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

  private decodeArrangement(value: unknown): ArrangementSection[] | undefined {
    if (!Array.isArray(value) || value.length < 1 || value.length > MAX_ARRANGEMENT_SECTIONS) {
      return undefined;
    }
    const sections: ArrangementSection[] = [];
    const ids = new Set<string>();
    let measureCount = 0;
    for (const candidate of value) {
      if (!candidate || typeof candidate !== 'object') {
        return undefined;
      }
      const section = candidate as Partial<ArrangementSection>;
      const parts = section.parts as Partial<Record<ArrangedPart, unknown>> | undefined;
      const sectionType = section.type as SectionType;
      const chances = section.partChances as Partial<Record<ArrangedPart, unknown>> | undefined;
      const trackParts = section.trackParts as Record<string, unknown> | undefined;
      const trackPartChances = section.trackPartChances as Record<string, unknown> | undefined;
      const trackPresence = section.trackPresence as Record<string, unknown> | undefined;
      const chordDegrees = section.chordDegrees;
      if (typeof section.id !== 'string' || section.id.length === 0 || section.id.length > 80 || ids.has(section.id)
          || !SECTION_TYPES.includes(sectionType)
          || !Number.isInteger(section.measures) || (section.measures ?? 0) < 1 || (section.measures ?? 33) > 32
          || (chordDegrees !== undefined && (!Array.isArray(chordDegrees) || chordDegrees.length !== section.measures
            || chordDegrees.some((degree) => !Number.isInteger(degree) || degree < 1 || degree > 7)))
          || !parts || ARRANGED_PARTS.some((part) => typeof parts[part] !== 'boolean')
          || (chances !== undefined && ARRANGED_PARTS.some((part) => !Number.isInteger(chances[part])
            || (chances[part] as number) < 0 || (chances[part] as number) > 100))
          || (trackParts !== undefined && (!trackParts || typeof trackParts !== 'object' || Array.isArray(trackParts)
            || Object.entries(trackParts).some(([id, included]) => !/^[a-zA-Z0-9_-]{1,64}$/.test(id) || typeof included !== 'boolean')))
          || (trackPartChances !== undefined && (!trackPartChances || typeof trackPartChances !== 'object' || Array.isArray(trackPartChances)
            || Object.entries(trackPartChances).some(([id, chance]) => !/^[a-zA-Z0-9_-]{1,64}$/.test(id)
              || !Number.isInteger(chance) || (chance as number) < 0 || (chance as number) > 100)))
          || (trackPresence !== undefined && (!trackPresence || typeof trackPresence !== 'object' || Array.isArray(trackPresence)
            || Object.entries(trackPresence).some(([id, present]) => !/^[a-zA-Z0-9_-]{1,64}$/.test(id) || typeof present !== 'boolean')))) {
        return undefined;
      }
      ids.add(section.id);
      measureCount += section.measures!;
      if (measureCount > MAX_ARRANGEMENT_MEASURES) {
        return undefined;
      }
      sections.push({
        id: section.id,
        type: sectionType,
        measures: section.measures!,
        chordDegrees: chordDegrees === undefined ? undefined : [...chordDegrees],
        parts: {
          bass: parts.bass as boolean, chords: parts.chords as boolean,
          arpeggio: parts.arpeggio as boolean, drums: parts.drums as boolean,
        },
        partChances: {
          bass: (chances?.bass as number | undefined) ?? DEFAULT_SECTION_PART_CHANCES[sectionType].bass,
          chords: (chances?.chords as number | undefined) ?? DEFAULT_SECTION_PART_CHANCES[sectionType].chords,
          arpeggio: (chances?.arpeggio as number | undefined) ?? DEFAULT_SECTION_PART_CHANCES[sectionType].arpeggio,
          drums: (chances?.drums as number | undefined) ?? DEFAULT_SECTION_PART_CHANCES[sectionType].drums,
        },
        trackParts: trackParts ? { ...trackParts } as Record<string, boolean> : undefined,
        trackPartChances: trackPartChances ? { ...trackPartChances } as Record<string, number> : undefined,
        trackPresence: trackPresence ? { ...trackPresence } as Record<string, boolean> : undefined,
      });
    }
    return sections;
  }

  private decodeBass(value: unknown): BassSettings | undefined {
    if (!value || typeof value !== 'object') {
      return undefined;
    }
    const bass = value as Partial<BassSettings>;
    if (!BASS_RHYTHMS.includes(bass.rhythm as typeof BASS_RHYTHMS[number])
        || !Number.isInteger(bass.noteVariation) || (bass.noteVariation ?? -1) < 0 || (bass.noteVariation ?? 101) > 100
        || (bass.octaveInterval !== undefined && typeof bass.octaveInterval !== 'boolean')) {
      return undefined;
    }
    return { rhythm: bass.rhythm!, noteVariation: bass.noteVariation!, octaveInterval: bass.octaveInterval ?? false };
  }

  private decodeChords(value: unknown): ChordSettings | undefined {
    if (!value || typeof value !== 'object') {
      return undefined;
    }
    const chords = value as Partial<ChordSettings>;
    if (!CHORD_RHYTHMS.includes(chords.rhythm as typeof CHORD_RHYTHMS[number])
        || !CHORD_VOICINGS.includes(chords.voicing as typeof CHORD_VOICINGS[number])
        || !Number.isInteger(chords.noteLengthPercent)
        || (chords.noteLengthPercent ?? 0) < 25 || (chords.noteLengthPercent ?? 126) > 125) {
      return undefined;
    }
    return {
      rhythm: chords.rhythm!,
      voicing: chords.voicing!,
      noteLengthPercent: chords.noteLengthPercent!,
    };
  }

  private decodeArpeggio(value: unknown): ArpeggioSettings | undefined {
    if (!value || typeof value !== 'object') {
      return undefined;
    }
    const arpeggio = value as Partial<ArpeggioSettings>;
    if (!ARPEGGIO_PATTERNS.includes(arpeggio.pattern as typeof ARPEGGIO_PATTERNS[number])
        || !ARPEGGIO_RATES.includes(arpeggio.rate as typeof ARPEGGIO_RATES[number])
        || (arpeggio.octaves !== 1 && arpeggio.octaves !== 2)) {
      return undefined;
    }
    return { pattern: arpeggio.pattern!, rate: arpeggio.rate!, octaves: arpeggio.octaves! };
  }

  private decodeDrums(value: unknown): DrumSettings | undefined {
    if (!value || typeof value !== 'object') {
      return undefined;
    }
    const drums = value as Partial<DrumSettings>;
    if (!DRUM_GROOVES.includes(drums.groove as typeof DRUM_GROOVES[number])
        || !Number.isInteger(drums.swingPercent)
        || (drums.swingPercent ?? 49) < 50 || (drums.swingPercent ?? 76) > 75) {
      return undefined;
    }
    return { groove: drums.groove!, swingPercent: drums.swingPercent! };
  }

  private copyDefaultProject(): CompositionProject {
    return {
      ...DEFAULT_PROJECT,
      progression: [...DEFAULT_PROJECT.progression],
      bass: { ...DEFAULT_BASS_SETTINGS },
      chords: { ...DEFAULT_CHORD_SETTINGS },
      arpeggio: { ...DEFAULT_ARPEGGIO_SETTINGS },
      drums: { ...DEFAULT_DRUM_SETTINGS },
      arrangement: this.copyDefaultArrangement(),
      mix: this.copyDefaultMix(),
      tracks: DEFAULT_TRACKS.map((track) => ({ ...track, mix: { ...track.mix }, generatorSettings: { ...track.generatorSettings } } as CompositionTrack)),
    };
  }

  private copyDefaultArrangement(): ArrangementSection[] {
    return DEFAULT_ARRANGEMENT.map((section) => ({
      ...section,
      chordDegrees: section.chordDegrees ? [...section.chordDegrees] : undefined,
      parts: { ...section.parts },
      partChances: { ...section.partChances },
    }));
  }

  private copyDefaultMix(): Record<ArrangedPart, MixChannelSettings> {
    return {
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
      && this.decodeBass(project.bass) !== undefined
      && this.decodeChords(project.chords) !== undefined
      && this.decodeArpeggio(project.arpeggio) !== undefined
      && this.decodeDrums(project.drums) !== undefined
      && this.decodeArrangement(project.arrangement) !== undefined
      && this.decodeEditedPhrases(project.editedPhrases) !== undefined
      && this.decodeMix(project.mix) !== undefined
      && this.decodeTracks(project.tracks) !== undefined;
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
