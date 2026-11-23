import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ProjectService } from '../../core/project/project.service';
import type { PartSettingsScope } from '../../core/project/part-settings-scope';
import { partValuesEqual } from '../../core/music/part-settings';
import type { PartSettingsPatch } from '../../core/music/part-settings.model';
import { SelectComponent } from '../../shared/select.component';

export interface NumericSequenceRange {
  readonly minimum: number;
  readonly maximum: number;
}

export function sequencePoints(values: readonly number[], range: NumericSequenceRange, top = 0, height = 100): string {
  return values.map((value, index) =>
    `${(index + .5) / values.length * 100},${sequenceY(value, range, top, height)}`).join(' ');
}

function sequenceY(value: number, { minimum, maximum }: NumericSequenceRange, top: number, height: number): number {
  return top + (maximum - value) / (maximum - minimum) * height;
}

export interface NumericSequenceEditor extends NumericSequenceRange {
  readonly title: string;
  readonly description: string;
  readonly minimumSteps: number;
  readonly maximumSteps: number;
  readonly rows: readonly { trackId: string; name: string; values: readonly number[] }[];
  readonly presets: readonly { value: string; label: string; load: (trackId: string) => readonly number[] }[];
  readonly randomize: (trackId: string, values: readonly number[], seed: number) => readonly number[];
  readonly patch: (values: readonly number[]) => PartSettingsPatch;
  readonly emptyLabel?: string;
  readonly saveHint?: string;
  readonly presetLabel?: string;
  readonly saveLabel?: string;
}

@Component({
  selector: 'vc-numeric-sequence-dialog',
  imports: [SelectComponent],
  templateUrl: './numeric-sequence-dialog.component.html',
  styleUrl: './numeric-sequence-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NumericSequenceDialogComponent {
  readonly data = inject<{ scope: PartSettingsScope; editor: NumericSequenceEditor }>(DIALOG_DATA);
  readonly editor = this.data.editor;
  readonly dialog = inject(DialogRef);
  private readonly projects = inject(ProjectService);
  private readonly initialProject = this.projects.project();
  readonly previews = this.editor.rows;
  readonly mixed = this.previews.some(row => !partValuesEqual(row.values, this.previews[0].values));
  readonly tracks = this.previews.map(row => ({ value: row.trackId, label: row.name }));
  readonly selectedTrack = signal(this.mixed ? '' : this.previews[0]?.trackId ?? '');
  readonly presetLabel = this.editor.presetLabel ?? 'Load preset';
  readonly presets = [{ value: '', label: this.presetLabel, disabled: true }, ...this.editor.presets];
  readonly sourcePreset = signal('');
  readonly values = signal<readonly number[]>(this.mixed ? [] : this.previews[0]?.values ?? []);
  readonly error = computed(() => {
    const { minimum, maximum, minimumSteps, maximumSteps } = this.editor;
    const length = this.values().length;
    return length < minimumSteps || length > maximumSteps || !this.validValues()
      ? `Use ${minimumSteps}–${maximumSteps} steps with whole numbers from ${minimum} to ${maximum}.` : null;
  });
  readonly stale = computed(() => this.projects.project() !== this.initialProject);
  readonly saveError = signal<string | undefined>(undefined);
  private readonly validValues = computed(() => this.values().every(value =>
    Number.isInteger(value) && value >= this.editor.minimum && value <= this.editor.maximum));
  readonly drawable = computed(() => this.values().length > 0 && this.validValues());
  pointY(value: number): number { return sequenceY(value, this.editor, 20, 72); }
  readonly points = computed(() => this.drawable() ? sequencePoints(this.values(), this.editor, 20, 72) : '');
  private gesture: { pointer: number; original: readonly number[]; preset: string } | null = null;
  start(event: PointerEvent): void {
    if (event.button !== 0 || this.gesture || !this.drawable() || !this.selectedTrack()) return;
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
  selectTrack(trackId: string): void {
    this.selectedTrack.set(trackId);
    this.setDraft(this.previews.find(row => row.trackId === trackId)?.values ?? []);
  }
  choosePreset(value: string): void {
    const preset = this.editor.presets.find(preset => preset.value === value);
    if (preset && this.selectedTrack()) this.setDraft(preset.load(this.selectedTrack()), value);
  }
  randomize(): void {
    if (!this.selectedTrack()) return;
    const seed = new Int32Array(1);
    crypto.getRandomValues(seed);
    this.setDraft(this.editor.randomize(this.selectedTrack(), this.values(), seed[0]));
  }
  editStep(index: number, value: number): void {
    const values = [...this.values()];
    values[index] = value === 0 ? 0 : value;
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
    if (this.error() || this.stale() || !this.selectedTrack()) return;
    this.projects.dismissGenerationError();
    this.projects.updatePartSettings(this.data.scope, this.editor.patch(this.values()));
    const error = this.projects.generationError();
    if (error) {
      this.saveError.set(error);
      return;
    }
    this.dialog.close();
  }
}
