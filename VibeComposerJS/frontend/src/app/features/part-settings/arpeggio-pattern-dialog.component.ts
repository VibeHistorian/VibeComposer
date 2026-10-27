import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ProjectService } from '../../core/project/project.service';
import type { PartSettingsScope } from '../../core/project/part-settings-scope';
import { ARPEGGIO_PATTERNS, MAX_ARPEGGIO_PATTERN_LENGTH, type ArpeggioPattern } from '../../core/music/arpeggio-pattern';
import { arpeggioPreviews } from '../../core/music/arpeggio-preview';
import { SelectComponent } from '../../shared/select.component';

@Component({
  selector: 'vc-arpeggio-pattern-dialog',
  imports: [SelectComponent],
  templateUrl: './arpeggio-pattern-dialog.component.html',
  styleUrl: './arpeggio-pattern-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ArpeggioPatternDialogComponent {
  readonly data = inject<{ scope: PartSettingsScope }>(DIALOG_DATA);
  readonly dialog = inject(DialogRef);
  readonly projects = inject(ProjectService);
  private readonly initialProject = this.projects.project();
  readonly previews = arpeggioPreviews(this.initialProject, this.data.scope);
  readonly mixed = this.previews.some(row => JSON.stringify(row.values) !== JSON.stringify(this.previews[0].values));
  readonly tracks = this.previews.map(row => ({ value: row.trackId, label: row.name }));
  readonly selectedTrack = signal(this.mixed ? '' : this.previews[0]?.trackId ?? '');
  readonly patterns = [{ value: '', label: 'Choose direction', disabled: true }, ...ARPEGGIO_PATTERNS.map(value => ({ value, label: value }))];
  readonly sourcePattern = signal('');
  readonly text = signal(this.mixed ? '' : this.previews[0]?.values.join(', ') ?? '');
  readonly values = computed(() => this.text().trim() ? this.text().split(',').map(value => value.trim() ? Number(value) : NaN) : []);
  readonly error = computed(() => {
    const values = this.values();
    return !values.length || values.length > MAX_ARPEGGIO_PATTERN_LENGTH || values.some(value => !Number.isInteger(value) || value < -12 || value > 12)
      ? `Use 1–${MAX_ARPEGGIO_PATTERN_LENGTH} steps with whole numbers from −12 to 12.` : null;
  });
  readonly stale = computed(() => this.projects.project() !== this.initialProject);
  readonly drawable = computed(() => this.values().length > 0 && this.values().every(value => Number.isInteger(value) && value >= -12 && value <= 12));
  readonly maximumSteps = MAX_ARPEGGIO_PATTERN_LENGTH;
  pointY(value: number): number { return 20 + (12 - value) / 24 * 72; }
  readonly points = computed(() => !this.drawable() ? '' : this.values().map((value, index, values) => `${(index + .5) / values.length * 100},${this.pointY(value)}`).join(' '));
  private gesture: { pointer: number; original: string } | null = null;
  start(event: PointerEvent): void {
    if (event.button !== 0 || !this.drawable() || !this.selectedTrack()) return;
    event.preventDefault();
    this.gesture = { pointer: event.pointerId, original: this.text() };
    (event.currentTarget as Element).setPointerCapture(event.pointerId);
    this.move(event);
  }
  move(event: PointerEvent): void {
    if (this.gesture?.pointer !== event.pointerId) return;
    const bounds = (event.currentTarget as Element).getBoundingClientRect();
    const index = Math.max(0, Math.min(this.values().length - 1, Math.floor((event.clientX - bounds.left) / bounds.width * this.values().length)));
    const value = Math.max(-12, Math.min(12, Math.round(12 - ((event.clientY - bounds.top) / bounds.height * 100 - 20) / 72 * 24)));
    this.editStep(index, String(value));
  }
  finish(): void { this.gesture = null; }
  cancelGesture(): void {
    if (this.gesture) this.text.set(this.gesture.original);
    this.gesture = null;
  }
  selectTrack(trackId: string): void {
    this.selectedTrack.set(trackId);
    this.sourcePattern.set('');
    this.text.set(this.previews.find(row => row.trackId === trackId)?.values.join(', ') ?? '');
  }
  choosePattern(pattern: string): void {
    if (!this.selectedTrack()) return;
    this.sourcePattern.set(pattern);
    const row = arpeggioPreviews(this.initialProject, this.data.scope, pattern as ArpeggioPattern)
      .find(row => row.trackId === this.selectedTrack());
    this.text.set(row?.values.join(', ') ?? '');
  }
  editStep(index: number, text: string): void {
    const pieces = this.text().split(',').map(piece => piece.trim());
    pieces[index] = text.trim() || 'invalid';
    this.text.set(pieces.join(', '));
  }
  resize(text: string): void {
    const length = Number(text);
    if (!Number.isInteger(length) || length < 1 || length > MAX_ARPEGGIO_PATTERN_LENGTH) return;
    const values = this.values();
    this.text.set(Array.from({ length }, (_, index) => values[index % values.length] ?? 0).join(', '));
  }
  save(): void {
    if (this.error() || this.stale() || !this.selectedTrack()) return;
    this.projects.updatePartSettings(this.data.scope, { pattern: 'custom', arpPatternCustom: [...this.values()], arpPatternRotate: 0 });
    this.dialog.close();
  }
}
