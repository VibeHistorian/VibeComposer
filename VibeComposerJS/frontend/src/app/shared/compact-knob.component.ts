import { UiPreferencesService } from '../core/ui/ui-preferences.service';
import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input, output, signal } from '@angular/core';
import { CdkConnectedOverlay, type ConnectedPosition } from '@angular/cdk/overlay';
import { ControlHeadingComponent, type ControlInheritance } from './control-heading.component';
import { normalizeVolumePercent, panLabel, volumeDecibels } from '../core/audio/mix-values';

const TIME_SLIDER_STEPS = 1000;
const TIME_SCALE_MS = 10;

@Component({
  selector: 'vc-compact-knob',
  imports: [ControlHeadingComponent, CdkConnectedOverlay],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './compact-knob.component.html',
  styleUrl: './compact-knob.component.css',
})
export class CompactKnobComponent {
  private readonly preferences = inject(UiPreferencesService);
  readonly element = inject(ElementRef<HTMLElement>);
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
  readonly showValueInline = input(false);
  readonly valueToggle = input<{ readonly pressed: boolean; readonly label: string; readonly tooltip: string; readonly disabled?: boolean } | null>(null);
  readonly valueToggleChange = output<boolean>();
  readonly mini = input(false);
  readonly pill = input(false);
  readonly letter = input<string | null>(null);
  readonly logarithmicTime = input(false);
  readonly defaultDisplay = input<{ readonly active: boolean; readonly text: string; readonly varied: boolean } | null>(null);
  readonly defaultRequested = output<void>();
  readonly hoverLabelOnly = input(false);
  readonly showLabel = input(true);
  readonly dense = input(false);
  readonly composeEnabled = input<boolean | null>(null);
  readonly composeToggle = output<void>();
  readonly shortLabel = input<string | null>(null);
  readonly compact = input(false);
  readonly tooltipEnabled = input(true);
  readonly description = input('');
  readonly caption = input<string | null>(null);
  readonly valueFormat = input<'number' | 'pan' | 'decibels'>('number');
  readonly inheritance = input<ControlInheritance>('global');
  readonly inheritRequested = output<void>();
  readonly valueCommit = output<number>();
  readonly valuePreview = output<number>();
  readonly preview = signal<number | null>(null);
  readonly shownValue = computed(() => this.normalize(this.preview() ?? this.value()));
  readonly atDefault = computed(() => {
    const display = this.defaultDisplay(), preview = this.preview();
    if (display && preview === null) return display.active;
    return !display?.varied && (!this.mixed() || preview !== null) && this.shownValue() === this.normalize(this.defaultValue());
  });
  readonly formattedValue = computed(() => this.valueFormat() === 'pan' ? panLabel(this.shownValue())
    : this.valueFormat() === 'decibels' ? volumeDecibels(this.shownValue())
    : (this.letter() ? Number(this.shownValue().toFixed(2)) : this.shownValue()) + this.unit());
  readonly letterValue = computed(() => this.atDefault() ? this.defaultDisplay()?.text ?? this.formattedValue() : this.formattedValue());
  readonly tooltipHovered = signal(false);
  readonly tooltipFocused = signal(false);
  readonly instantTooltip = computed(() => this.tooltipEnabled() && (this.tooltipHovered() || this.tooltipFocused() || this.preview() !== null));
  readonly tooltipPositions: ConnectedPosition[] = [
    { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -4 },
    { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: 4 },
  ];
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
  readonly sliderMaximum = computed(() => this.allowedValues().length ? this.allowedValues().length - 1
    : this.logarithmicTime() ? TIME_SLIDER_STEPS : this.maximum());
  readonly sliderStep = computed(() => this.allowedValues().length || this.logarithmicTime() ? 1 : this.step());
  readonly sliderValue = computed(() => this.allowedValues().length
    ? this.allowedValues().indexOf(this.snap(this.value())) : this.toSlider(this.value()));
  private readonly progress = computed(() => {
    if (this.logarithmicTime()) return this.toSlider(this.shownValue()) / TIME_SLIDER_STEPS;
    const span = this.maximum() - this.minimum();
    const progress = span > 0 ? (this.shownValue() - this.minimum()) / span : 0;
    return Math.max(0, Math.min(1, progress));
  });
  readonly angle = computed(() => `${this.progress() * 270}deg`);
  readonly fill = computed(() => `${this.progress() * 100}%`);

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

  resetAux(event: MouseEvent): void {
    if (event.button !== 2) this.resetDefault(event);
  }

  resetDefault(event: MouseEvent): void {
    if (!this.preferences.matchesShortcut('controlReset', event)) return;
    event.preventDefault(); event.stopPropagation();
    if (this.disabled()) return;
    this.cancelDrag(); this.preview.set(null);
    const display = this.defaultDisplay();
    if (display) {
      if (!display.active) this.defaultRequested.emit();
      return;
    }
    const value = this.snap(this.defaultValue());
    if (this.mixed() || value !== this.value()) this.valueCommit.emit(value);
  }

  tooltip(): string {
    if (this.mixed() && this.preview() === null) return `${this.label()}: mixed values. Adjust to choose a shared value.`;
    return `${this.label()}: ${this.accessibleValue()}. ${this.description()} Interactions: Settings > Shortcuts.`;
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
    if (this.allowedValues().length) return this.allowedValues()[value] ?? this.value();
    if (!this.logarithmicTime()) return this.normalize(value);
    const position = Math.max(0, Math.min(TIME_SLIDER_STEPS, Math.round(value)));
    if (position === this.toSlider(this.defaultValue())) return this.defaultValue();
    return Number((TIME_SCALE_MS * Math.expm1(position / TIME_SLIDER_STEPS * Math.log1p(this.maximum() / TIME_SCALE_MS))).toFixed(2));
  }

  private toSlider(value: number): number {
    return this.logarithmicTime() ? Math.round(TIME_SLIDER_STEPS * Math.log1p(value / TIME_SCALE_MS)
      / Math.log1p(this.maximum() / TIME_SCALE_MS)) : value;
  }

  private changedValue(value: number): boolean {
    const display = this.defaultDisplay();
    return value !== this.value() || !!(display?.active && display.varied);
  }

  onWheel(event: WheelEvent): void {
    if (this.disabled() || (!this.preferences.matchesShortcut('controlWheel', event) && !this.preferences.matchesShortcut('controlFineWheel', event)) || event.deltaY === 0 || this.drag) return;
    event.preventDefault(); event.stopPropagation();
    const direction = event.deltaY < 0 ? 1 : -1;
    if (this.logarithmicTime()) {
      const increment = this.preferences.matchesShortcut('controlFineWheel', event) ? 1 : 25;
      const value = this.fromSlider(this.sliderValue() + direction * increment);
      if (this.changedValue(value)) this.valueCommit.emit(value);
      return;
    }
    const increment = this.valueFormat() === 'decibels' ? this.preferences.matchesShortcut('controlFineWheel', event) ? 1 : 10
      : this.preferences.matchesShortcut('controlFineWheel', event) ? this.step() : Math.max(this.step(), Math.floor((this.maximum() - this.minimum()) / 20 / this.step()) * this.step());
    const allowed = this.allowedValues();
    const value = allowed.length ? allowed[Math.max(0, Math.min(allowed.length - 1,
      allowed.indexOf(this.snap(this.value())) + direction))] : this.valueFormat() === 'decibels'
      ? this.normalize(Math.max(this.minimum(), Math.min(this.maximum(), this.shownValue() + direction * increment)))
      : this.snap(this.value() + direction * increment);
    if (this.changedValue(value)) this.valueCommit.emit(value);
  }

  startDrag(event: PointerEvent): void {
    if (event.button === 0 && this.preferences.matchesShortcut('controlReset', event)) { this.resetDefault(event); return; }
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
    const span = this.logarithmicTime() ? TIME_SLIDER_STEPS : this.maximum() - this.minimum();
    const sensitivity = span / 150 / (this.preferences.matchesModifiers('fineDrag', event) ? 5 : 1);
    const change = (this.drag.y - event.clientY) * sensitivity;
    const value = this.logarithmicTime() ? this.fromSlider(this.toSlider(this.drag.value) + change) : this.snap(this.drag.value + change);
    this.setPreview(value);
  }

  finishDrag(event: PointerEvent): void {
    if (event.pointerId !== this.drag?.pointerId) return;
    const value = this.preview();
    const valid = this.dragStillValid();
    this.drag = null;
    this.preview.set(null);
    if (valid && value !== null && this.changedValue(value)) this.valueCommit.emit(value);
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
