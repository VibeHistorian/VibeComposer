import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';

@Component({
  selector: 'vc-compact-knob',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './compact-knob.component.html',
  styleUrl: './compact-knob.component.css',
})
export class CompactKnobComponent {
  readonly value = input.required<number>();
  readonly minimum = input.required<number>();
  readonly maximum = input.required<number>();
  readonly step = input(1);
  readonly code = input.required<string>();
  readonly label = input.required<string>();
  readonly unit = input('%');
  readonly mixed = input(false);
  readonly valueCommit = output<number>();
  readonly preview = signal<number | null>(null);
  readonly shownValue = computed(() => this.preview() ?? this.value());
  readonly angle = computed(() => {
    const span = this.maximum() - this.minimum();
    const progress = span > 0 ? (this.shownValue() - this.minimum()) / span : 0;
    return `${Math.max(0, Math.min(1, progress)) * 270}deg`;
  });

  previewValue(event: Event): void {
    this.preview.set(Number((event.target as HTMLInputElement).value));
  }

  commitValue(event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = Number(input.value);
    if (Number.isFinite(value)) this.valueCommit.emit(value);
    this.preview.set(null);
    // A rejected commit may leave the bound value unchanged; restore the native slider as well.
    input.value = String(this.value());
  }

  tooltip(): string {
    if (this.mixed() && this.preview() === null) return `${this.label()}: mixed values. Adjust to choose a shared value.`;
    return `${this.label()}: ${this.shownValue()}${this.unit()}. Use arrow keys to adjust.`;
  }
}
