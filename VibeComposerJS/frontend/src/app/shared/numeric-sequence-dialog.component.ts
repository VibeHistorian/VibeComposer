import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { SelectComponent } from './select.component';

export interface NumericSequenceRange {
  readonly minimum: number;
  readonly maximum: number;
}

export function sequencePoints(values: readonly number[], range: NumericSequenceRange, top = 0, height = 100): string {
  return values.map((value, index) =>
    `${(index + .5) / values.length * 100},${sequenceY(value, range, top, height)}`).join(' ');
}

export function sequenceY(value: number, { minimum, maximum }: NumericSequenceRange, top: number, height: number): number {
  const position = maximum === minimum ? .5 : (maximum - value) / (maximum - minimum);
  return top + position * height;
}

export interface NumericSequenceEditor extends NumericSequenceRange {
  readonly title: string;
  readonly description: string;
  readonly minimumSteps: number;
  readonly maximumSteps: number;
  readonly rows: readonly { id: string; name: string; values: readonly number[] }[];
  readonly presets?: readonly { value: string; label: string; load: (sourceId: string) => readonly number[] }[];
  readonly randomize?: (sourceId: string, values: readonly number[], seed: number) => readonly number[];
  readonly validate?: (values: readonly number[]) => string | undefined;
  readonly reorderable?: boolean;
  readonly sourceLabel?: string;
  readonly sourceNoun?: string;
  readonly emptyLabel?: string;
  readonly saveHint?: string;
  readonly presetLabel?: string;
  readonly saveLabel?: string;
}

export interface NumericSequenceDialogData {
  readonly editor: NumericSequenceEditor;
  readonly stale: () => boolean;
  readonly save: (values: readonly number[]) => string | undefined;
}

@Component({
  selector: 'vc-numeric-sequence-dialog',
  imports: [SelectComponent],
  templateUrl: './numeric-sequence-dialog.component.html',
  styleUrl: './numeric-sequence-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NumericSequenceDialogComponent {
  private readonly data = inject<NumericSequenceDialogData>(DIALOG_DATA);
  readonly editor = this.data.editor;
  readonly dialog = inject(DialogRef);
  readonly previews = this.editor.rows;
  private readonly initialValues = this.previews[0]?.values ?? [];
  readonly mixed = this.previews.some(({ values }) => values.length !== this.initialValues.length
    || values.some((value, index) => value !== this.initialValues[index]));
  readonly sources = this.previews.map(row => ({ value: row.id, label: row.name }));
  readonly sourceLabel = this.editor.sourceLabel ?? 'Starting sequence';
  readonly selectedSource = signal(this.mixed ? '' : this.previews[0]?.id ?? '');
  readonly presetLabel = this.editor.presetLabel ?? 'Load preset';
  readonly presets = [{ value: '', label: this.presetLabel, disabled: true }, ...(this.editor.presets ?? [])];
  readonly sourcePreset = signal('');
  readonly values = signal<readonly number[]>(this.mixed ? [] : this.initialValues);
  readonly error = computed(() => {
    const { minimum, maximum, minimumSteps, maximumSteps } = this.editor;
    const length = this.values().length;
    return length < minimumSteps || length > maximumSteps || !this.validValues()
      ? `Use ${minimumSteps}–${maximumSteps} steps with whole numbers from ${minimum} to ${maximum}.`
      : this.editor.validate?.(this.values()) ?? null;
  });
  readonly stale = computed(this.data.stale);
  readonly saveError = signal<string | undefined>(undefined);
  private readonly validValues = computed(() => this.values().every(value =>
    Number.isInteger(value) && value >= this.editor.minimum && value <= this.editor.maximum));
  readonly drawable = computed(() => this.values().length > 0 && this.validValues());
  pointY(value: number): number { return sequenceY(value, this.editor, 20, 72); }
  readonly points = computed(() => this.drawable() ? sequencePoints(this.values(), this.editor, 20, 72) : '');
  private gesture: { pointer: number; original: readonly number[]; preset: string } | null = null;
  start(event: PointerEvent): void {
    if (event.button !== 0 || this.gesture || !this.drawable() || !this.selectedSource()) return;
    event.preventDefault();
    this.gesture = { pointer: event.pointerId, original: this.values(), preset: this.sourcePreset() };
    (event.currentTarget as Element).setPointerCapture(event.pointerId);
    this.move(event);
  }
  move(event: PointerEvent): void {
    if (this.gesture?.pointer !== event.pointerId) return;
    const bounds = (event.currentTarget as Element).getBoundingClientRect();
    const index = Math.max(0, Math.min(this.values().length - 1, Math.floor((event.clientX - bounds.left) / bounds.width * this.values().length)));
    const { minimum, maximum } = this.editor;
    const value = maximum - ((event.clientY - bounds.top) / bounds.height * 100 - 20) / 72 * (maximum - minimum);
    this.editStep(index, Math.max(minimum, Math.min(maximum, Math.round(value))));
  }
  finish(event: PointerEvent): void {
    if (this.gesture?.pointer === event.pointerId) this.gesture = null;
  }
  cancelGesture(event: PointerEvent): void {
    if (this.gesture?.pointer !== event.pointerId) return;
    this.setDraft(this.gesture.original, this.gesture.preset);
    this.gesture = null;
  }
  selectSource(sourceId: string): void {
    this.selectedSource.set(sourceId);
    this.setDraft(this.previews.find(row => row.id === sourceId)?.values ?? []);
  }
  choosePreset(value: string): void {
    const preset = this.editor.presets?.find(preset => preset.value === value);
    if (preset && this.selectedSource()) this.setDraft(preset.load(this.selectedSource()), value);
  }
  randomize(): void {
    const randomize = this.editor.randomize;
    if (!randomize || !this.selectedSource()) return;
    const seed = new Int32Array(1);
    crypto.getRandomValues(seed);
    this.setDraft(randomize(this.selectedSource(), this.values(), seed[0]));
  }
  editStep(index: number, value: number): void {
    const values = [...this.values()];
    values[index] = value === 0 ? 0 : value;
    this.setDraft(values);
  }
  moveStep(index: number, direction: -1 | 1): void {
    const to = index + direction;
    const values = [...this.values()];
    if (to < 0 || to >= values.length) return;
    [values[index], values[to]] = [values[to], values[index]];
    this.setDraft(values);
  }
  resize(input: HTMLInputElement): void {
    const length = input.valueAsNumber;
    const values = this.values();
    if (!Number.isInteger(length) || length < this.editor.minimumSteps || length > this.editor.maximumSteps) {
      input.value = String(values.length);
      return;
    }
    this.setDraft(Array.from({ length }, (_, index) => values[index % values.length] ?? 0));
  }
  private setDraft(values: readonly number[], preset = ''): void {
    this.values.set(values);
    this.sourcePreset.set(preset);
    this.saveError.set(undefined);
  }
  save(): void {
    if (this.error() || this.stale() || !this.selectedSource()) return;
    const error = this.data.save(this.values());
    if (error) {
      this.saveError.set(error);
      return;
    }
    this.dialog.close();
  }
}
