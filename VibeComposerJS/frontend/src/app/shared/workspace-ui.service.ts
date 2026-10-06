import { Injectable, effect, inject, signal } from '@angular/core';
import type { ArrangedPart } from '../core/project/project.model';
import { ProjectService } from '../core/project/project.service';

@Injectable({ providedIn: 'root' })
export class WorkspaceUiService {
  private readonly projects = inject(ProjectService);
  readonly selectedTrackId = signal('track-bass-1');
  readonly selectedRole = signal<ArrangedPart | null>(null);
  readonly editingNoteId = signal<string | null>(null);

  constructor() {
    effect(() => {
      const tracks = this.projects.project().tracks;
      if (tracks.length && !tracks.some((track) => track.id === this.selectedTrackId())) {
        this.selectedTrackId.set(tracks[0].id);
      }
    });
  }

  selectTrack(trackId: string): void {
    this.selectedTrackId.set(trackId);
    this.selectedRole.set(null);
  }

  selectRole(role: ArrangedPart): void {
    this.selectedRole.set(role);
    const firstTrack = this.projects.project().tracks.find((track) => track.role === role);
    if (firstTrack) this.selectedTrackId.set(firstTrack.id);
  }
}
