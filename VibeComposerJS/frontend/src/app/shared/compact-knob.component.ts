import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { ControlHeadingComponent, type ControlInheritance } from './control-heading.component';
import { panLabel, volumeDecibels } from '../core/audio/mix-values';

@Component({
  selector: 'vc-compact-knob',
  imports: [ControlHeadingComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './compact-knob.component.html',
  styleUrl: './compact-knob.component.css',
})
export class CompactKnobComponent {
  readonly value = input.required<number>();
  readonly minimum = input.required<number>();
  readonly maximum = input.required<number>();
  readonly step = input(1);
  readonly allowedValues = input<readonly number[]>([]);
  readonly label = input.required<string>();
  readonly unit = input('%');
  readonly mixed = input(false);
  readonly showValueBelow = input(false);
  readonly mini = input(false);
  readonly valueFormat = input<'number' | 'pan' | 'decibels'>('number');
  readonly inheritance = input<ControlInheritance>('global');
  readonly inheritRequested = output<void>();
  readonly valueCommit = output<number>();
  readonly valuePreview = output<number>();
  readonly preview = signal<number | null>(null);
  readonly shownValue = computed(() => this.preview() ?? this.value());
  readonly formattedValue = computed(() => this.valueFormat() === 'pan' ? panLabel(this.shownValue())
    : this.valueFormat() === 'decibels' ? volumeDecibels(this.shownValue()) : this.shownValue() + this.unit());
  readonly accessibleValue = computed(() => this.formattedValue() + (this.valueFormat() === 'decibels' ? ' dB' : ''));
  private drag: { pointerId: number; y: number; value: number; minimum: number; maximum: number; step: number;
    allowedValues: readonly number[] } | null = null;
  readonly sliderMinimum = computed(() => this.allowedValues().length ? 0 : this.minimum());
  readonly sliderMaximum = computed(() => this.allowedValues().length ? this.allowedValues().length - 1 : this.maximum());
  readonly sliderStep = computed(() => this.allowedValues().length ? 1 : this.step());
  readonly sliderValue = computed(() => this.allowedValues().length
    ? this.allowedValues().indexOf(this.snap(this.value())) : this.value());
  readonly angle = computed(() => {
    const span = this.maximum() - this.minimum();
    const progress = span > 0 ? (this.shownValue() - this.minimum()) / span : 0;
    return `${Math.max(0, Math.min(1, progress)) * 270}deg`;
  });

  previewValue(event: Event): void {
    this.setPreview(this.fromSlider(Number((event.target as HTMLInputElement).value)));
  }

  commitValue(event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = this.fromSlider(Number(input.value));
    if (Number.isFinite(value)) this.valueCommit.emit(value);
    this.preview.set(null);
    // A rejected commit may leave the bound value unchanged; restore the native slider as well.
    input.value = String(this.sliderValue());
  }

  tooltip(): string {
    if (this.mixed() && this.preview() === null) return `${this.label()}: mixed values. Adjust to choose a shared value.`;
    return `${this.label()}: ${this.accessibleValue()}. Drag up/down or scroll to adjust; Shift for finer changes. Arrow keys also work.`;
  }

  private setPreview(value: number): void {
    this.preview.set(value);
    this.valuePreview.emit(value);
  }

  private snap(value: number): number {
    if (this.allowedValues().length) return this.allowedValues().reduce((closest, candidate) =>
      Math.abs(candidate - value) < Math.abs(closest - value) ? candidate : closest);
    return Math.max(this.minimum(), Math.min(this.maximum(),
      this.minimum() + Math.round((value - this.minimum()) / this.step()) * this.step()));
  }

  private fromSlider(value: number): number {
    return this.allowedValues().length ? this.allowedValues()[value] ?? this.value() : value;
  }

  onWheel(event: WheelEvent): void {
    if (event.ctrlKey || event.metaKey || event.deltaY === 0 || this.drag) return;
    event.preventDefault(); event.stopPropagation();
    const increment = event.shiftKey ? this.step() : Math.max(this.step(), Math.floor((this.maximum() - this.minimum()) / 20 / this.step()) * this.step());
    const direction = event.deltaY < 0 ? 1 : -1;
    const allowed = this.allowedValues();
    const value = allowed.length ? allowed[Math.max(0, Math.min(allowed.length - 1,
      allowed.indexOf(this.snap(this.value())) + direction))] : this.snap(this.value() + direction * increment);
    if (value !== this.value()) this.valueCommit.emit(value);
  }

  startDrag(event: PointerEvent): void {
    if (event.button !== 0 || this.drag) return;
    event.preventDefault(); event.stopPropagation();
    const control = event.currentTarget as HTMLInputElement;
    control.focus(); control.setPointerCapture(event.pointerId);
    this.drag = { pointerId: event.pointerId, y: event.clientY, value: this.value(),
      minimum: this.minimum(), maximum: this.maximum(), step: this.step(), allowedValues: this.allowedValues() };
  }

  moveDrag(event: PointerEvent): void {
    if (event.pointerId !== this.drag?.pointerId) return;
    if (!this.dragStillValid()) { this.cancelDrag(); return; }
    const sensitivity = (this.maximum() - this.minimum()) / 150 / (event.shiftKey ? 5 : 1);
    this.setPreview(this.snap(this.drag.value + (this.drag.y - event.clientY) * sensitivity));
  }

  finishDrag(event: PointerEvent): void {
    if (event.pointerId !== this.drag?.pointerId) return;
    const value = this.preview();
    const valid = this.dragStillValid();
    this.drag = null;
    this.preview.set(null);
    if (valid && value !== null && value !== this.value()) this.valueCommit.emit(value);
    else this.valuePreview.emit(this.value());
  }

  private dragStillValid(): boolean {
    return !!this.drag && this.drag.value === this.value() && this.drag.minimum === this.minimum()
      && this.drag.maximum === this.maximum() && this.drag.step === this.step()
      && this.drag.allowedValues === this.allowedValues();
  }

  cancelDrag(): void {
    if (!this.drag) return;
    this.drag = null; this.preview.set(null);
    this.valuePreview.emit(this.value());
  }

  ngOnDestroy(): void { this.cancelDrag(); }
}
