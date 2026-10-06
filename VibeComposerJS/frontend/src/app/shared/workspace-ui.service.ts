import { Injectable, effect, inject, signal } from '@angular/core';
import { ProjectService } from '../core/project/project.service';

@Injectable({ providedIn: 'root' })
export class WorkspaceUiService {
  private readonly projects = inject(ProjectService);
  private readonly expanded = signal(false);
  readonly selectedTrackId = signal('track-bass-1');
  readonly toolsOpen = this.expanded.asReadonly();

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
  }

  openTools(): void {
    this.expanded.set(true);
  }

  toggleTools(): void {
    this.expanded.update((open) => !open);
  }
}
