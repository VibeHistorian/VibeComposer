import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import type { CommonPartSettings, PartSettingsPatch } from '../../core/music/part-settings.model';
import type { PartSettingsScope } from '../../core/project/part-settings-scope';
import { ProjectService } from '../../core/project/project.service';
import { resolvePartTrack } from '../../core/music/part-settings';
import { DEFAULT_DURATION_CHANCES, DEFAULT_DURATIONS, durationError, paddedDurations } from '../../core/music/durations';
import { validateDurationDraft } from '../../core/music/duration-validation';

@Component({
  selector: 'vc-durations-dialog',
  templateUrl: './durations-dialog.component.html',
  styleUrl: './durations-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DurationsDialogComponent {
  readonly data = inject<{ scope: PartSettingsScope; flipped?: boolean }>(DIALOG_DATA);
  readonly dialog = inject(DialogRef);
  readonly projects = inject(ProjectService);
  private readonly initialProject = this.projects.project();
  private readonly section = 'sectionId' in this.data.scope ? this.initialProject.arrangement.find(section => section.id === (this.data.scope as { sectionId: string }).sectionId) : undefined;
  readonly role = 'role' in this.data.scope ? this.data.scope.role : this.initialProject.tracks.find(track => track.id === (this.data.scope as { trackId: string }).trackId)!.role;
  private readonly tracks = this.initialProject.tracks.filter(track => track.role === this.role && (!('trackId' in this.data.scope) || track.id === this.data.scope.trackId));
  private readonly initial = resolvePartTrack(this.tracks[0], this.section).generatorSettings as CommonPartSettings;
  readonly trackCount = this.tracks.length;
  readonly texts = signal((this.initial.customDurations ?? DEFAULT_DURATIONS).map(row => row.join(', ')));
  readonly chances = signal([...(this.initial.customDurationChances ?? DEFAULT_DURATION_CHANCES)]);
  readonly weighted = signal(this.initial.customDurationRandomWeighting ?? false);
  readonly flipped = signal(this.data.flipped ?? this.initial.patternFlip ?? false);
  readonly grid = signal(0.25);
  readonly tool = signal<'note' | 'rest'>('note');
  readonly rows = computed(() => this.texts().map(text => text.trim() ? text.split(',').map(value => value.trim() ? Number(value) : NaN) : []));
  readonly patch = computed<PartSettingsPatch>(() => ({ rhythm: 'durations', customDurations: this.rows(),
    customDurationChances: this.chances(), customDurationRandomWeighting: this.weighted(), patternFlip: this.flipped() }));
  readonly error = computed(() => durationError({ ...this.initial, ...this.patch() }));
  readonly stale = computed(() => this.projects.project() !== this.initialProject);
  readonly checked = signal<{ draft: string; error: string | null } | null>(null);
  readonly checking = signal(false);
  readonly fingerprint = computed(() => JSON.stringify(this.patch()));
  readonly validationMessage = computed(() => this.checked()?.draft === this.fingerprint() ? this.checked()?.error : null);
  readonly canSave = computed(() => !this.stale() && !this.error() && !this.checking()
    && this.checked()?.draft === this.fingerprint() && this.checked()?.error === null);
  readonly steps = computed(() => Array.from({ length: Math.round(4 / this.grid()) }, (_, index) => index));
  readonly displayRows = computed(() => this.rows().map(row => {
    if (!row.length || row.some(value => !Number.isFinite(value) || value === 0) || row.reduce((sum, value) => sum + Math.abs(value), 0) > 4.0000001) return [];
    let start = 0;
    return paddedDurations(row, this.flipped()).map(value => {
      const slot = { start, duration: Math.abs(value), rest: value < 0 }; start += slot.duration; return slot;
    });
  }));
  private gesture: { pointer: number; row: number; start: number; end: number; original: readonly number[] } | null = null;

  editRow(row: number, text: string): void { this.texts.update(rows => rows.map((value, index) => index === row ? text : value)); }
  editChance(row: number, value: string): void { this.chances.update(chances => chances.map((chance, index) => index === row ? Number(value) : chance)); }
  private cell(event: PointerEvent): number {
    const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
    return Math.max(0, Math.min(this.steps().length - 1, Math.floor((event.clientX - bounds.left) / bounds.width * this.steps().length)));
  }
  start(event: PointerEvent, row: number): void {
    if (event.button !== 0) return;
    event.preventDefault();
    const values = this.rows()[row];
    if (values.some(value => !Number.isFinite(value) || value === 0) || values.reduce((sum, value) => sum + Math.abs(value), 0) > 4.0000001) return;
    const cell = this.cell(event);
    this.gesture = { pointer: event.pointerId, row, start: cell, end: cell, original: values };
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    this.paint();
  }
  move(event: PointerEvent): void {
    if (!this.gesture || this.gesture.pointer !== event.pointerId) return;
    const cell = this.cell(event);
    if (cell !== this.gesture.end) { this.gesture.end = cell; this.paint(); }
  }
  finish(): void { this.gesture = null; }
  cancel(): void {
    if (this.gesture) this.editRow(this.gesture.row, this.gesture.original.join(', '));
    this.gesture = null;
  }
  private paint(): void {
    const gesture = this.gesture!;
    const left = Math.min(gesture.start, gesture.end) * this.grid(), right = (Math.max(gesture.start, gesture.end) + 1) * this.grid();
    const before: number[] = [], after: number[] = [];
    let time = 0;
    for (const value of paddedDurations(gesture.original)) {
      const end = time + Math.abs(value), sign = Math.sign(value);
      if (time < left) before.push(sign * (Math.min(left, end) - time));
      if (end > right) after.push(sign * (end - Math.max(time, right)));
      time = end;
    }
    const sign = (this.tool() === 'note') !== this.flipped() ? 1 : -1;
    this.editRow(gesture.row, [...before, sign * (right - left), ...after].map(value => Math.round(value * 960) / 960).join(', '));
  }
  validate(): void {
    if (this.error() || this.stale() || this.checking()) return;
    this.checking.set(true);
    const draft = this.fingerprint(), patch = this.patch();
    // Give the popup a render before running the bounded generator checks.
    setTimeout(() => {
      try { this.checked.set({ draft, error: validateDurationDraft(this.initialProject, this.data.scope, patch) }); }
      catch (error) { this.checked.set({ draft, error: error instanceof Error ? error.message : 'Cannot generate these durations.' }); }
      finally { this.checking.set(false); }
    }, 0);
  }
  save(): void {
    if (!this.canSave()) return;
    this.projects.updatePartSettings(this.data.scope, this.patch());
    this.dialog.close();
  }
}
