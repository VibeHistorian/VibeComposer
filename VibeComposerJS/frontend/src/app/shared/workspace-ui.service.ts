import { Injectable, computed, effect, inject, signal } from '@angular/core';
import type { ArrangedPart } from '../core/project/project.model';
import { ProjectService } from '../core/project/project.service';

export type SettingsTarget =
  | { readonly kind: 'global-role'; readonly role: ArrangedPart }
  | { readonly kind: 'global-track'; readonly trackId: string }
  | { readonly kind: 'section'; readonly sectionId: string }
  | { readonly kind: 'section-role'; readonly sectionId: string; readonly role: ArrangedPart }
  | { readonly kind: 'section-track'; readonly sectionId: string; readonly trackId: string };

@Injectable({ providedIn: 'root' })
export class WorkspaceUiService {
  private readonly projects = inject(ProjectService);
  readonly selectedTrackId = signal('track-bass-1');
  private readonly target = signal<SettingsTarget>({ kind: 'global-track', trackId: 'track-bass-1' });
  readonly settingsTarget = computed<SettingsTarget>(() => {
    const target = this.target();
    const project = this.projects.project();
    const validSection = !('sectionId' in target) || project.arrangement.some((section) => section.id === target.sectionId);
    const validTrack = !('trackId' in target) || project.tracks.some((track) => track.id === target.trackId);
    if (validSection && validTrack) return target;
    return { kind: 'global-track', trackId: project.tracks[0]?.id ?? '' };
  });
  readonly selectedRole = computed(() => {
    const target = this.settingsTarget();
    return target.kind === 'global-role' ? target.role : null;
  });
  readonly editingNoteId = signal<string | null>(null);
  private readonly sectionSelection = signal<readonly string[]>([]);
  private readonly sectionAnchor = signal<string | null>(null);
  private readonly focusedSection = signal<string | null>(null);
  readonly selectedSectionIds = computed(() => {
    const sections = this.projects.project().arrangement;
    const selected = this.sectionSelection();
    const indices = sections.flatMap((section, index) => selected.includes(section.id) ? [index] : []);
    // Imports, reordering, undo, and deletion must also preserve a contiguous selection.
    return indices.length ? sections.slice(indices[0], indices.at(-1)! + 1).map((section) => section.id) : [];
  }, { equal: (left, right) => left.length === right.length && left.every((id, index) => id === right[index]) });
  readonly selectedSectionId = computed(() => {
    const selected = this.selectedSectionIds();
    const focused = this.focusedSection();
    return focused && selected.includes(focused) ? focused : selected.at(-1) ?? null;
  });
  readonly sectionRange = computed(() => {
    const selected = this.selectedSectionIds();
    if (!selected.length) return null;
    let beat = 0;
    let startBeat = 0;
    let endBeat = 0;
    for (const section of this.projects.project().arrangement) {
      if (section.id === selected[0]) startBeat = beat;
      beat += section.measures * 4;
      if (section.id === selected.at(-1)) endBeat = beat;
    }
    return { startBeat, endBeat };
  }, { equal: (left, right) => left?.startBeat === right?.startBeat && left?.endBeat === right?.endBeat });

  constructor() {
    effect(() => {
      const tracks = this.projects.project().tracks;
      if (tracks.length && !tracks.some((track) => track.id === this.selectedTrackId())) {
        this.selectedTrackId.set(tracks[0].id);
      }
    });
  }

  selectTrack(trackId: string): void {
    if (!this.projects.project().tracks.some((track) => track.id === trackId)) return;
    this.selectedTrackId.set(trackId);
    this.target.set({ kind: 'global-track', trackId });
  }

  selectRole(role: ArrangedPart): void {
    this.target.set({ kind: 'global-role', role });
    const firstTrack = this.projects.project().tracks.find((track) => track.role === role);
    if (firstTrack) this.selectedTrackId.set(firstTrack.id);
  }

  clearSectionSelection(): void {
    this.sectionSelection.set([]);
    this.sectionAnchor.set(null);
    this.focusedSection.set(null);
    if (this.settingsTarget().kind === 'section') this.selectTrack(this.selectedTrackId());
  }

  clearSelection(): void {
    this.clearSectionSelection();
    this.selectTrack(this.selectedTrackId());
  }

  selectCell(sectionId: string, role: ArrangedPart): void {
    if (!this.projects.project().arrangement.some((section) => section.id === sectionId)) return;
    this.target.set({ kind: 'section-role', sectionId, role });
  }

  selectSectionTrack(sectionId: string, trackId: string): void {
    const project = this.projects.project();
    if (!project.arrangement.some((section) => section.id === sectionId)
        || !project.tracks.some((track) => track.id === trackId)) return;
    this.selectedTrackId.set(trackId);
    this.target.set({ kind: 'section-track', sectionId, trackId });
  }

  setSectionSelection(sectionId: string | null): void {
    this.sectionSelection.set(sectionId ? [sectionId] : []);
    this.sectionAnchor.set(sectionId);
    this.focusedSection.set(sectionId);
    if (sectionId) this.target.set({ kind: 'section', sectionId });
    else this.selectTrack(this.selectedTrackId());
  }

  selectSection(sectionId: string, modifiers: { ctrlKey?: boolean; metaKey?: boolean; shiftKey?: boolean } = {}): void {
    const sections = this.projects.project().arrangement;
    const index = sections.findIndex((section) => section.id === sectionId);
    if (index < 0) return;
    const selected = this.selectedSectionIds();
    let next: readonly string[];
    if (modifiers.shiftKey && selected.length) {
      const anchor = sections.findIndex((section) => section.id === this.sectionAnchor());
      const start = anchor >= 0 ? anchor : sections.findIndex((section) => section.id === selected[0]);
      next = sections.slice(Math.min(start, index), Math.max(start, index) + 1).map((section) => section.id);
    } else if ((modifiers.ctrlKey || modifiers.metaKey) && selected.length) {
      const selectedIndex = selected.indexOf(sectionId);
      if (selectedIndex >= 0) {
        // Removing an interior section discards the entire right-hand remainder.
        next = selectedIndex === 0 ? selected.slice(1) : selected.slice(0, selectedIndex);
      } else {
        const first = sections.findIndex((section) => section.id === selected[0]);
        const last = sections.findIndex((section) => section.id === selected.at(-1));
        next = sections.slice(Math.min(first, index), Math.max(last, index) + 1).map((section) => section.id);
      }
    } else {
      next = selected.length === 1 && selected[0] === sectionId ? [] : [sectionId];
      this.sectionAnchor.set(next.length ? sectionId : null);
    }
    this.sectionSelection.set(next);
    this.focusedSection.set(next.includes(sectionId) ? sectionId : next.at(-1) ?? null);
    if (!next.includes(this.sectionAnchor() ?? '')) this.sectionAnchor.set(next[0] ?? null);
    if (next.length) this.target.set({ kind: 'section', sectionId: this.focusedSection()! });
    else this.selectTrack(this.selectedTrackId());
  }
}
