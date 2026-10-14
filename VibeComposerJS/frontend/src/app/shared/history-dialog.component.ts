import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DialogRef } from '@angular/cdk/dialog';
import { ProjectService } from '../core/project/project.service';
import { WorkspaceUiService } from './workspace-ui.service';

@Component({
  selector: 'vc-history-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './history-dialog.component.html',
  styleUrl: './history-dialog.component.css',
})
export class HistoryDialogComponent {
  readonly dialog = inject(DialogRef);
  readonly projects = inject(ProjectService);
  readonly workspaceUi = inject(WorkspaceUiService);
}
