import { Injectable, signal } from '@angular/core';
import { KEYS, getDiatonicChords } from '../music/harmony';
import type { BassSettings } from './project.model';
import type { CompositionProject } from './project.model';
import { DEFAULT_BASS_SETTINGS, DEFAULT_PROJECT } from './project.model';

const STORAGE_KEY = 'vibecomposer.project.v2';
const LEGACY_STORAGE_KEY = 'vibecomposer.project.v1';
const MAX_HISTORY = 100;
const BASS_RHYTHMS = ['alternating', 'full', 'half', 'tresillo', 'sparse'] as const;

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
    for (const storageKey of [STORAGE_KEY, LEGACY_STORAGE_KEY]) {
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

    const project = value as Omit<Partial<CompositionProject>, 'schemaVersion' | 'bass'>
      & { schemaVersion?: number; bass?: unknown };
    const validBase = (project.schemaVersion === 1 || project.schemaVersion === 2)
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
    if (!bass) {
      return undefined;
    }
    return {
      schemaVersion: 2,
      name: project.name!,
      key: project.key!,
      scale: project.scale!,
      tempoBpm: project.tempoBpm!,
      seed: project.seed!,
      progression: [...project.progression!],
      bass,
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

  private copyDefaultProject(): CompositionProject {
    return {
      ...DEFAULT_PROJECT,
      progression: [...DEFAULT_PROJECT.progression],
      bass: { ...DEFAULT_BASS_SETTINGS },
    };
  }

  private isProject(value: unknown): value is CompositionProject {
    if (!value || typeof value !== 'object') {
      return false;
    }
    const project = value as Partial<CompositionProject>;
    return project.schemaVersion === 2
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
      && this.decodeBass(project.bass) !== undefined;
  }

  private persist(project: CompositionProject): void {
    try {
      globalThis.sessionStorage?.setItem(STORAGE_KEY, JSON.stringify(project));
    } catch {
      // Keep the project usable if storage is disabled or full.
    }
  }
}
