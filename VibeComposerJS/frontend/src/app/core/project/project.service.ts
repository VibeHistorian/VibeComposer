import { Injectable, signal } from '@angular/core';
import { KEYS, getDiatonicChords } from '../music/harmony';
import type { ArpeggioSettings, BassSettings, ChordSettings, DrumSettings } from './project.model';
import type { CompositionProject } from './project.model';
import {
  DEFAULT_ARPEGGIO_SETTINGS, DEFAULT_BASS_SETTINGS, DEFAULT_CHORD_SETTINGS,
  DEFAULT_DRUM_SETTINGS, DEFAULT_PROJECT,
} from './project.model';

const STORAGE_KEY = 'vibecomposer.project.v4';
const PREVIOUS_STORAGE_KEYS = ['vibecomposer.project.v3', 'vibecomposer.project.v2'];
const LEGACY_STORAGE_KEY = 'vibecomposer.project.v1';
const MAX_HISTORY = 100;
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

  updateSettings(patch: Partial<Pick<CompositionProject, 'name' | 'key' | 'scale' | 'tempoBpm' | 'seed'>>): void {
    this.commit({ ...this.state(), ...patch });
  }

  updateBassSettings(patch: Partial<BassSettings>): void {
    this.commit({ ...this.state(), bass: { ...this.state().bass, ...patch } });
  }

  updateChordSettings(patch: Partial<ChordSettings>): void {
    this.commit({ ...this.state(), chords: { ...this.state().chords, ...patch } });
  }

  updateArpeggioSettings(patch: Partial<ArpeggioSettings>): void {
    this.commit({ ...this.state(), arpeggio: { ...this.state().arpeggio, ...patch } });
  }

  updateDrumSettings(patch: Partial<DrumSettings>): void {
    this.commit({ ...this.state(), drums: { ...this.state().drums, ...patch } });
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

  private loadProject(): CompositionProject {
    for (const storageKey of [STORAGE_KEY, ...PREVIOUS_STORAGE_KEYS, LEGACY_STORAGE_KEY]) {
      try {
        const raw = globalThis.sessionStorage?.getItem(storageKey);
        if (raw) {
          const decoded = this.decodeProject(JSON.parse(raw));
          if (decoded) {
            if (storageKey !== STORAGE_KEY) {
              this.persist(decoded);
            }
            return decoded;
          }
        }
      } catch {
        // Storage can be unavailable or contain invalid JSON.
      }
    }
    return this.copyDefaultProject();
  }

  private decodeProject(value: unknown): CompositionProject | undefined {
    if (!value || typeof value !== 'object') {
      return undefined;
    }

    const project = value as Omit<Partial<CompositionProject>, 'schemaVersion' | 'bass' | 'chords' | 'arpeggio' | 'drums'>
      & { schemaVersion?: number; bass?: unknown; chords?: unknown; arpeggio?: unknown; drums?: unknown };
    const validBase = (project.schemaVersion === 1 || project.schemaVersion === 2
      || project.schemaVersion === 3 || project.schemaVersion === 4)
      && typeof project.name === 'string'
      && typeof project.key === 'string' && KEYS.includes(project.key)
      && (project.scale === 'major' || project.scale === 'natural-minor')
      && Number.isInteger(project.tempoBpm) && (project.tempoBpm ?? 0) >= 40 && (project.tempoBpm ?? 0) <= 240
      && typeof project.seed === 'string' && /^-?\d+$/.test(project.seed)
      && BigInt(project.seed) >= -(1n << 63n) && BigInt(project.seed) <= (1n << 63n) - 1n
      && Array.isArray(project.progression)
      && project.progression.length > 0 && project.progression.length <= 32
      && project.progression.every((degree) => Number.isInteger(degree) && degree >= 1 && degree <= 7);
    if (!validBase) {
      return undefined;
    }

    const bass = project.schemaVersion === 1 ? DEFAULT_BASS_SETTINGS : this.decodeBass(project.bass);
    const chords = project.schemaVersion === 3 || project.schemaVersion === 4
      ? this.decodeChords(project.chords) : DEFAULT_CHORD_SETTINGS;
    const arpeggio = project.schemaVersion === 4 ? this.decodeArpeggio(project.arpeggio) : DEFAULT_ARPEGGIO_SETTINGS;
    const drums = project.schemaVersion === 4 ? this.decodeDrums(project.drums) : DEFAULT_DRUM_SETTINGS;
    if (!bass || !chords || !arpeggio || !drums) {
      return undefined;
    }
    return {
      schemaVersion: 4,
      name: project.name!,
      key: project.key!,
      scale: project.scale!,
      tempoBpm: project.tempoBpm!,
      seed: project.seed!,
      progression: [...project.progression!],
      bass,
      chords,
      arpeggio,
      drums,
    };
  }

  private decodeBass(value: unknown): BassSettings | undefined {
    if (!value || typeof value !== 'object') {
      return undefined;
    }
    const bass = value as Partial<BassSettings>;
    if (!BASS_RHYTHMS.includes(bass.rhythm as typeof BASS_RHYTHMS[number])
        || !Number.isInteger(bass.noteVariation) || (bass.noteVariation ?? -1) < 0 || (bass.noteVariation ?? 101) > 100) {
      return undefined;
    }
    return { rhythm: bass.rhythm!, noteVariation: bass.noteVariation! };
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
    };
  }

  private isProject(value: unknown): value is CompositionProject {
    if (!value || typeof value !== 'object') {
      return false;
    }
    const project = value as Partial<CompositionProject>;
    return project.schemaVersion === 4
      && typeof project.name === 'string'
      && typeof project.key === 'string' && KEYS.includes(project.key)
      && (project.scale === 'major' || project.scale === 'natural-minor')
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
      && this.decodeDrums(project.drums) !== undefined;
  }

  private persist(project: CompositionProject): void {
    try {
      globalThis.sessionStorage?.setItem(STORAGE_KEY, JSON.stringify(project));
    } catch {
      // Keep the project usable if storage is disabled or full.
    }
  }
}
