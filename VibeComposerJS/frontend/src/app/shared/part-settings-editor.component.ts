import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import type { ArrangedPart, ChordRhythm } from '../core/project/project.model';
import { PART_CONTROLS, type PartControl, type PartSettingValue, partValuesEqual } from '../core/music/part-settings';
import { chordRhythmMask } from '../core/music/rhythm-pattern';
import { CompactKnobComponent } from './compact-knob.component';
import { ControlHeadingComponent, type ControlInheritance } from './control-heading.component';

@Component({
  selector: 'vc-part-settings-editor',
  imports: [CompactKnobComponent, ControlHeadingComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-control-role]': 'role()' },
  templateUrl: './part-settings-editor.component.html',
  styleUrl: './part-settings-editor.component.css',
})
export class PartSettingsEditorComponent {
  readonly role = input.required<ArrangedPart>();
  readonly values = input.required<Readonly<Record<string, PartSettingValue | null>>>();
  readonly overriddenKeys = input<readonly string[]>([]);
  readonly local = input(false);
  readonly settingsChanged = output<{ readonly key: string; readonly value: PartSettingValue }>();
  readonly inheritRequested = output<string>();
  readonly controls = computed(() => PART_CONTROLS[this.role()].filter((control) =>
    (control.key !== 'euclideanPulses' || this.values()['rhythm'] === 'euclid' || this.values()['rhythm'] === null)
    && (control.key !== 'customPattern' || this.values()['rhythm'] === 'custom')));
  readonly patternDraft = signal<readonly number[] | null>(null);
  private painting: { pointerId: number; values: Readonly<Record<string, PartSettingValue | null>>; sounded: number; lastIndex: number } | null = null;
  readonly rhythmPreview = computed(() => {
    const draft = this.patternDraft();
    if (this.role() !== 'chords') return undefined;
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
    return pattern ? `${pattern.filter((slot) => slot > 0).length} sounded slots in ${pattern.length} subdivisions per chord. Fill can suppress whole chords.`
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
}
