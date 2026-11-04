import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ProjectService } from '../../core/project/project.service';

@Component({
  selector: 'vc-duplicate-track-dialog',
  templateUrl: './duplicate-track-dialog.component.html',
  styleUrl: './duplicate-track-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DuplicateTrackDialogComponent {
  readonly data = inject<{ trackId: string }>(DIALOG_DATA);
  readonly dialog = inject(DialogRef);
  readonly projects = inject(ProjectService);
  readonly manualNotes = signal(true);
  readonly presences = signal(true);
  readonly overrides = signal(true);
  readonly variations = signal(true);
  readonly error = signal('');
  apply(): void {
    const id = this.projects.duplicateTrack(this.data.trackId, {
      manualNotes: this.manualNotes(), presences: this.presences(),
      overrides: this.presences() && this.overrides(), variations: this.presences() && this.variations(),
    });
    if (id) this.dialog.close(id);
    else this.error.set(this.projects.generationError() ?? 'Could not duplicate this track. Check the track limit and section variation capacity.');
  }
}
