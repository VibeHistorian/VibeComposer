import { Injectable, signal } from '@angular/core';
import { KEYS, getDiatonicChords } from '../music/harmony';
import type { CompositionProject } from './project.model';
import { DEFAULT_PROJECT } from './project.model';

const STORAGE_KEY = 'vibecomposer.project.v1';
const MAX_HISTORY = 100;

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
    try {
      const raw = globalThis.sessionStorage?.getItem(STORAGE_KEY);
      if (!raw) {
        return { ...DEFAULT_PROJECT, progression: [...DEFAULT_PROJECT.progression] };
      }

      const value: unknown = JSON.parse(raw);
      if (this.isProject(value)) {
        return value;
      }
    } catch {
      // Storage can be unavailable in restricted browser contexts.
    }
    return { ...DEFAULT_PROJECT, progression: [...DEFAULT_PROJECT.progression] };
  }

  private isProject(value: unknown): value is CompositionProject {
    if (!value || typeof value !== 'object') {
      return false;
    }
    const project = value as Partial<CompositionProject>;
    return project.schemaVersion === 1
      && typeof project.name === 'string'
      && typeof project.key === 'string' && KEYS.includes(project.key)
      && (project.scale === 'major' || project.scale === 'natural-minor')
      && Number.isInteger(project.tempoBpm) && (project.tempoBpm ?? 0) >= 40 && (project.tempoBpm ?? 0) <= 240
      && typeof project.seed === 'string' && /^-?\d+$/.test(project.seed)
      && BigInt(project.seed) >= -(1n << 63n) && BigInt(project.seed) <= (1n << 63n) - 1n
      && Array.isArray(project.progression)
      && project.progression.length > 0
      && project.progression.length <= 32
      && project.progression.every((degree) => Number.isInteger(degree) && degree >= 1 && degree <= 7);
  }

  private persist(project: CompositionProject): void {
    try {
      globalThis.sessionStorage?.setItem(STORAGE_KEY, JSON.stringify(project));
    } catch {
      // Keep the project usable if storage is disabled or full.
    }
  }
}
