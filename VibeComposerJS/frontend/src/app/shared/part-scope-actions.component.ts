import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

@Component({
  selector: 'vc-part-scope-actions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './part-scope-actions.component.html',
  styleUrl: './part-scope-actions.component.css',
})
export class PartScopeActionsComponent {
  readonly layout = input<'full' | 'header' | 'range'>('full');
  readonly group = input(false);
  readonly hasOverrides = input(false);
  readonly hasExceptions = input(false);
  readonly destinations = input('');
  readonly destinationCount = input(0);
  readonly message = input<string | null>(null);
  readonly freezeRequested = output<void>();
  readonly inheritRequested = output<void>();
  readonly resetCellRequested = output<void>();
  readonly applyRequested = output<'overrides' | 'effective'>();
}
