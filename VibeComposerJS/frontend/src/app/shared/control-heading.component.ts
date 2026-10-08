import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

export type ControlInheritance = 'global' | 'inherited' | 'custom';
let nextControlId = 0;

@Component({
  selector: 'vc-control-heading',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './control-heading.component.html',
  styleUrl: './control-heading.component.css',
})
export class ControlHeadingComponent {
  readonly controlId = `part-control-${nextControlId++}`;
  readonly label = input.required<string>();
  readonly inheritance = input<ControlInheritance>('global');
  readonly inheritRequested = output<void>();
  readonly statusLabel = computed(() => this.inheritance() === 'custom'
    ? `Reset ${this.label()} to inherited`
    : `${this.label()}: ${this.inheritance() === 'inherited' ? 'inherited' : 'global setting'}`);
}
