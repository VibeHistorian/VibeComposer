import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { ControlHeadingComponent, type ControlInheritance } from './control-heading.component';
import { normalizeVolumePercent, panLabel, volumeDecibels } from '../core/audio/mix-values';

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
  readonly defaultValue = input.required<number>();
  readonly step = input(1);
  readonly allowedValues = input<readonly number[]>([]);
  readonly label = input.required<string>();
  readonly unit = input('%');
  readonly mixed = input(false);
  readonly disabled = input(false);
  readonly showValueBelow = input(false);
  readonly mini = input(false);
  readonly hoverLabelOnly = input(false);
  readonly showLabel = input(true);
  readonly dense = input(false);
  readonly composeEnabled = input<boolean | null>(null);
  readonly composeToggle = output<void>();
  readonly shortLabel = input<string | null>(null);
  readonly compact = input(false);
  readonly description = input('');
  readonly caption = input<string | null>(null);
  readonly valueFormat = input<'number' | 'pan' | 'decibels'>('number');
  readonly inheritance = input<ControlInheritance>('global');
  readonly inheritRequested = output<void>();
  readonly valueCommit = output<number>();
  readonly valuePreview = output<number>();
  readonly preview = signal<number | null>(null);
  readonly shownValue = computed(() => this.normalize(this.preview() ?? this.value()));
  readonly atDefault = computed(() => (!this.mixed() || this.preview() !== null)
    && this.shownValue() === this.normalize(this.defaultValue()));
  readonly formattedValue = computed(() => this.valueFormat() === 'pan' ? panLabel(this.shownValue())
    : this.valueFormat() === 'decibels' ? volumeDecibels(this.shownValue()) : this.shownValue() + this.unit());
  readonly accessibleValue = computed(() => this.formattedValue() + (this.valueFormat() === 'decibels' ? ' dB' : ''));
  readonly mixAccent = computed(() => {
    if (!this.mini() || !['pan', 'decibels'].includes(this.valueFormat())) return null;
    const neutral = this.valueFormat() === 'pan' ? 0 : 100;
    const value = this.shownValue();
    const extent = value < neutral ? neutral - this.minimum() : this.maximum() - neutral;
    const deviation = extent > 0 ? Math.min(1, Math.abs(value - neutral) / extent) : 0;
    // A visible initial jump makes even tiny mix edits readable; each endpoint reaches full accent.
    const strength = deviation === 0 ? 0 : 25 + 75 * Math.sqrt(deviation);
    const accent = this.valueFormat() === 'decibels' && value > neutral
      ? 'rgb(from var(--accent-primary) calc(255 - r) calc(255 - g) calc(255 - b))'
      : 'var(--accent-primary)';
    return `color-mix(in srgb, ${accent} ${strength}%, var(--text-muted))`;
  });
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
    if (this.disabled()) return;
    this.setPreview(this.fromSlider(Number((event.target as HTMLInputElement).value)));
  }

  commitValue(event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = this.fromSlider(Number(input.value));
    if (!this.disabled() && Number.isFinite(value)) this.valueCommit.emit(value);
    this.preview.set(null);
    // A rejected commit may leave the bound value unchanged; restore the native slider as well.
    input.value = String(this.sliderValue());
  }

  resetDefault(event: MouseEvent): void {
    event.preventDefault(); event.stopPropagation();
    if (this.disabled()) return;
    this.cancelDrag(); this.preview.set(null);
    const value = this.snap(this.defaultValue());
    if (this.mixed() || value !== this.value()) this.valueCommit.emit(value);
  }

  tooltip(): string {
    if (this.mixed() && this.preview() === null) return `${this.label()}: mixed values. Adjust to choose a shared value.`;
    return `${this.label()}: ${this.accessibleValue()}. ${this.description()} Drag up/down or scroll to adjust; Shift for finer changes. Arrow keys also work. Right-click to reset to ${this.defaultValue()}${this.unit()}.`;
  }

  private setPreview(value: number): void {
    this.preview.set(value);
    this.valuePreview.emit(value);
  }

  private normalize(value: number): number {
    return this.valueFormat() === 'decibels' ? normalizeVolumePercent(value) : value;
  }

  private snap(value: number): number {
    if (this.allowedValues().length) return this.allowedValues().reduce((closest, candidate) =>
      Math.abs(candidate - value) < Math.abs(closest - value) ? candidate : closest);
    return this.normalize(Math.max(this.minimum(), Math.min(this.maximum(),
      this.minimum() + Math.round((value - this.minimum()) / this.step()) * this.step())));
  }

  private fromSlider(value: number): number {
    return this.allowedValues().length ? this.allowedValues()[value] ?? this.value() : this.normalize(value);
  }

  onWheel(event: WheelEvent): void {
    if (this.disabled() || event.ctrlKey || event.metaKey || event.deltaY === 0 || this.drag) return;
    event.preventDefault(); event.stopPropagation();
    const increment = this.valueFormat() === 'decibels' ? event.shiftKey ? 1 : 10
      : event.shiftKey ? this.step() : Math.max(this.step(), Math.floor((this.maximum() - this.minimum()) / 20 / this.step()) * this.step());
    const direction = event.deltaY < 0 ? 1 : -1;
    const allowed = this.allowedValues();
    const value = allowed.length ? allowed[Math.max(0, Math.min(allowed.length - 1,
      allowed.indexOf(this.snap(this.value())) + direction))] : this.valueFormat() === 'decibels'
      ? this.normalize(Math.max(this.minimum(), Math.min(this.maximum(), this.shownValue() + direction * increment)))
      : this.snap(this.value() + direction * increment);
    if (value !== this.value()) this.valueCommit.emit(value);
  }

  startDrag(event: PointerEvent): void {
    if (this.disabled() || event.button !== 0 || this.drag) return;
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
    return !this.disabled() && !!this.drag && this.drag.value === this.value() && this.drag.minimum === this.minimum()
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
