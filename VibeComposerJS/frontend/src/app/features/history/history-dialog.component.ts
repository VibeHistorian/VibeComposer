import { TooltipDirective } from '../../shared/tooltip.directive';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { DialogRef } from '@angular/cdk/dialog';
import { ProjectService } from '../../core/project/project.service';

@Component({
  selector: 'vc-history-dialog',
  imports: [TooltipDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './history-dialog.component.html',
  styleUrl: './history-dialog.component.css',
})
export class HistoryDialogComponent {
  readonly dialog = inject(DialogRef);
  readonly projects = inject(ProjectService);
  readonly entries = computed(() => [...this.projects.historyEntries()].reverse());
}
