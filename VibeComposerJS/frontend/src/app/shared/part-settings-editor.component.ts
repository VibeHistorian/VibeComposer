import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import type { ArrangedPart, ChordRhythm } from '../core/project/project.model';
import { PART_CONTROLS, type PartControl, type PartSettingValue, partValuesEqual } from '../core/music/part-settings';
import { chordRhythmMask } from '../core/music/rhythm-pattern';
import { DEFAULT_CUSTOM_VELOCITIES } from '../core/music/velocity-pattern';
import { CompactKnobComponent } from './compact-knob.component';
import { WheelSelectDirective } from './wheel-select.directive';
import { ControlHeadingComponent, type ControlInheritance } from './control-heading.component';

@Component({
  selector: 'vc-part-settings-editor',
  imports: [CompactKnobComponent, ControlHeadingComponent, WheelSelectDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-control-role]': 'role()' },
  templateUrl: './part-settings-editor.component.html',
  styleUrl: './part-settings-editor.component.css',
})
export class PartSettingsEditorComponent {
  readonly emptyAllowedValues: readonly number[] = [];
  readonly role = input.required<ArrangedPart>();
  readonly values = input.required<Readonly<Record<string, PartSettingValue | null>>>();
  readonly overriddenKeys = input<readonly string[]>([]);
  readonly local = input(false);
  readonly advanced = input(true);
  readonly settingsChanged = output<{ readonly key: string; readonly value: PartSettingValue }>();
  readonly inheritRequested = output<string>();
  private textEditValues: Readonly<Record<string, PartSettingValue | null>> | null = null;
  readonly controls = computed(() => PART_CONTROLS[this.role()].filter((control) =>
    (!control.advanced || this.advanced()) &&
    (!(this.role() === 'bass' && this.values()['rhythm'] === 'alternating')
      || !['hitsPerPattern', 'chordSpan', 'patternShift', 'patternFlip', 'euclideanPulses', 'customPattern', 'useCustomVelocities', 'customVelocities', 'patternJoinMode'].includes(control.key))
    &&
    (control.key !== 'euclideanPulses' || this.values()['rhythm'] === 'euclid' || this.values()['rhythm'] === null)
    && (control.key !== 'customPattern' || this.values()['rhythm'] === 'custom')
    && (control.key !== 'customVelocities' || this.values()['useCustomVelocities'] === true)
    && (!['velocityMin', 'velocityMax'].includes(control.key) || this.values()['useCustomVelocities'] !== true
      || (this.role() === 'bass' && this.values()['rhythm'] === 'alternating'))));
  readonly velocityDraft = signal<readonly number[] | null>(null);
  private velocityEdit: { index: number; values: Readonly<Record<string, PartSettingValue | null>> } | null = null;
  readonly velocitySlots = computed(() => {
    const draft = this.velocityDraft();
    const values = this.values();
    if (values['hitsPerPattern'] === null || values['customVelocities'] === null) return null;
    const grid = (this.velocityEdit?.values === values ? draft : null)
      ?? values['customVelocities'] as readonly number[] | undefined ?? DEFAULT_CUSTOM_VELOCITIES;
    return grid.slice(0, values['hitsPerPattern'] as number | undefined ?? 8);
  });
  readonly patternDraft = signal<readonly number[] | null>(null);
  private painting: { pointerId: number; values: Readonly<Record<string, PartSettingValue | null>>; sounded: number; lastIndex: number } | null = null;
  readonly rhythmPreview = computed(() => {
    const draft = this.patternDraft();
    if (this.role() === 'melody' || (this.role() === 'bass' && this.values()['rhythm'] === 'alternating')) return undefined;
    const values = this.values();
    const keys = ['rhythm', 'hitsPerPattern', 'patternShift', 'patternFlip',
      ...(values['rhythm'] === 'euclid' ? ['euclideanPulses'] : []),
      ...(values['rhythm'] === 'custom' ? ['customPattern'] : [])];
    if (keys.some((key) => values[key] === null)) return null;
    return chordRhythmMask({ rhythm: values['rhythm'] as ChordRhythm,
      hitsPerPattern: values['hitsPerPattern'] as number | undefined,
      patternShift: values['patternShift'] as number | undefined,
      patternFlip: values['patternFlip'] as boolean | undefined,
      euclideanPulses: values['euclideanPulses'] as number | undefined,
      customPattern: (this.painting?.values === values ? draft : null)
        ?? values['customPattern'] as readonly number[] | undefined });
  });
  readonly rhythmPreviewLabel = computed(() => {
    const pattern = this.rhythmPreview();
    return pattern ? `${pattern.filter((slot) => slot > 0).length} enabled slots in the ${pattern.length}-cell base rhythm. Span and Repeat change its placement; Pause and Fill can suppress notes; zero velocity silences individual hits.`
      : 'Mixed rhythm settings. Select an individual track to see its rhythm.';
  });

  inheritanceFor(control: PartControl): ControlInheritance {
    return this.local() ? this.overriddenKeys().includes(control.key) ? 'custom' : 'inherited' : 'global';
  }

  numericValue(control: PartControl): number {
    return (this.values()[control.key] as number | null)
      ?? (typeof control.defaultValue === 'number' ? control.defaultValue : control.minimum!);
  }

  minimum(control: PartControl): number {
    return control.key === 'velocityMax' && typeof this.values()['velocityMin'] === 'number'
      ? this.values()['velocityMin'] as number : control.minimum!;
  }

  maximum(control: PartControl): number {
    return control.key === 'velocityMin' && typeof this.values()['velocityMax'] === 'number'
      ? this.values()['velocityMax'] as number : control.maximum!;
  }

  choose(control: PartControl, event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    if (value === '') return;
    this.settingsChanged.emit({ key: control.key, value: typeof control.options?.[0] === 'number' ? Number(value) : value });
  }

  textValue(control: PartControl): string {
    const value = this.values()[control.key];
    return value === null ? '' : Array.isArray(value) ? value.join(', ') : String(value ?? control.defaultValue ?? '');
  }

  startTextEdit(): void { this.textEditValues = this.values(); }

  cancelTextEdit(control: PartControl, event: Event): void {
    const element = event.target as HTMLInputElement;
    element.value = this.textValue(control);
    element.setCustomValidity('');
    this.textEditValues = null;
  }

  commitText(control: PartControl, event: Event): void {
    const element = event.target as HTMLInputElement;
    if (this.textEditValues && this.textEditValues !== this.values()) { this.cancelTextEdit(control, event); return; }
    const pieces = control.kind === 'integers' ? element.value.split(',').map((piece) => piece.trim()) : [element.value.trim()];
    const valid = pieces.length >= 1 && pieces.length <= 32 && pieces.every((piece) => /^-?\d+$/.test(piece)
      && Number.isSafeInteger(Number(piece)) && Number(piece) >= control.minimum! && Number(piece) <= control.maximum!);
    if (!valid) {
      element.setCustomValidity(control.kind === 'integers' ? `Enter 1–32 comma-separated integers from ${control.minimum} to ${control.maximum}.`
        : `Enter an integer from ${control.minimum} to ${control.maximum}.`);
      element.reportValidity(); return;
    }
    element.setCustomValidity('');
    this.textEditValues = null;
    this.settingsChanged.emit({ key: control.key, value: control.kind === 'integers' ? pieces.map(Number) : Number(pieces[0]) });
  }

  useFullGrid(): void {
    this.settingsChanged.emit({ key: 'customPattern', value: Array(32).fill(1) });
  }

  toggleSlot(index: number): void {
    const pattern = this.rhythmPreview();
    if (!pattern || this.values()['rhythm'] !== 'custom') return;
    this.settingsChanged.emit({ key: 'customPattern', value: this.paintSlot(index, 1 - pattern[index]) });
  }

  /** Paint in audible coordinates, undoing flip and the full-list rotation when storing. */
  private paintSlot(index: number, sounded: number): number[] {
    const draft = this.painting?.values === this.values() ? this.patternDraft() : null;
    const pattern = [...(draft ?? this.values()['customPattern'] as readonly number[] ?? Array(32).fill(1))];
    const shift = this.values()['patternShift'] as number ?? 0;
    pattern[(index - shift + 32) % 32] = this.values()['patternFlip'] ? 1 - sounded : sounded;
    return pattern;
  }

  startPainting(event: PointerEvent, index: number): void {
    if (event.button !== 0 || this.painting) return;
    const pattern = this.rhythmPreview();
    if (!pattern) return;
    event.preventDefault();
    (event.target as HTMLElement).focus();
    this.painting = { pointerId: event.pointerId, values: this.values(), sounded: 1 - pattern[index], lastIndex: index };
    this.patternDraft.set(this.paintSlot(index, this.painting.sounded));
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
  }

  movePainting(event: PointerEvent): void {
    if (event.pointerId !== this.painting?.pointerId) return;
    if (this.painting.values !== this.values()) { this.cancelPainting(); return; }
    const grid = event.currentTarget as HTMLElement;
    const slot = grid.ownerDocument.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('[data-rhythm-slot]');
    if (slot && grid.contains(slot)) {
      const index = Number(slot.dataset['rhythmSlot']);
      for (let current = Math.min(this.painting.lastIndex, index); current <= Math.max(this.painting.lastIndex, index); current++) {
        this.patternDraft.set(this.paintSlot(current, this.painting.sounded));
      }
      this.painting.lastIndex = index;
    }
  }

  finishPainting(event: PointerEvent): void {
    if (event.pointerId !== this.painting?.pointerId) return;
    const draft = this.patternDraft();
    const unchangedContext = this.painting.values === this.values();
    this.cancelPainting();
    if (draft && unchangedContext && !partValuesEqual(draft, this.values()['customPattern'])) {
      this.settingsChanged.emit({ key: 'customPattern', value: draft });
    }
  }

  cancelPainting(): void {
    this.painting = null;
    this.patternDraft.set(null);
  }

  slotClick(event: MouseEvent, index: number): void {
    // Pointer gestures commit on release; native keyboard and assistive clicks commit here.
    if (event.detail === 0) this.toggleSlot(index);
  }

  useDefaultVelocities(): void {
    this.settingsChanged.emit({ key: 'customVelocities', value: [...DEFAULT_CUSTOM_VELOCITIES] });
  }

  previewVelocity(index: number, event: Event): void {
    if (!this.velocitySlots()) return;
    const value = Number((event.target as HTMLInputElement).value);
    if (!Number.isInteger(value) || value < 0 || value > 127) return;
    if (this.velocityEdit && (this.velocityEdit.values !== this.values() || this.velocityEdit.index !== index)) {
      this.cancelVelocity(); return;
    }
    this.velocityEdit ??= { index, values: this.values() };
    const grid = [...(this.velocityDraft() ?? this.values()['customVelocities'] as readonly number[] ?? DEFAULT_CUSTOM_VELOCITIES)];
    grid[index] = value;
    this.velocityDraft.set(grid);
  }

  commitVelocity(index: number, event: Event): void {
    const unchangedContext = !this.velocityEdit || this.velocityEdit.values === this.values();
    if (unchangedContext) this.previewVelocity(index, event);
    const draft = this.velocityDraft();
    this.cancelVelocity();
    if (unchangedContext && draft && !partValuesEqual(draft, this.values()['customVelocities'])) {
      this.settingsChanged.emit({ key: 'customVelocities', value: draft });
    }
  }

  cancelVelocity(): void {
    this.velocityEdit = null;
    this.velocityDraft.set(null);
  }

  wheelVelocity(index: number, event: WheelEvent): void {
    const slots = this.velocitySlots();
    if (!slots || this.velocityEdit || event.ctrlKey || event.metaKey || event.deltaY === 0) return;
    event.preventDefault(); event.stopPropagation();
    const grid = [...(this.values()['customVelocities'] as readonly number[] | undefined ?? DEFAULT_CUSTOM_VELOCITIES)];
    const value = Math.max(0, Math.min(127, slots[index] + (event.deltaY < 0 ? 1 : -1) * (event.shiftKey ? 1 : 6)));
    if (value === slots[index]) return;
    grid[index] = value;
    this.settingsChanged.emit({ key: 'customVelocities', value: grid });
  }
}
