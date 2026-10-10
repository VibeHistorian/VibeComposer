import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ControlHeadingComponent, type ControlInheritance } from './control-heading.component';

@Component({
  selector: 'vc-compact-toggle',
  imports: [ControlHeadingComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './compact-toggle.component.html',
  styleUrl: './compact-toggle.component.css',
})
export class CompactToggleComponent {
  readonly label = input.required<string>();
  readonly shortLabel = input<string | null>(null);
  readonly description = input('');
  readonly active = input(false);
  readonly mixed = input(false);
  readonly disabled = input(false);
  readonly showStatus = input(false);
  readonly inheritance = input<ControlInheritance>('global');
  readonly toggled = output<void>();
  readonly inheritRequested = output<void>();
}
