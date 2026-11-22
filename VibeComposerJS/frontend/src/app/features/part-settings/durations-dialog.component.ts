import { LiveRecordingComponent } from '../recording/live-recording.component';
import { recordedSegments, type RecordedSpan } from '../../core/audio/live-recording';
import { SelectComponent } from '../../shared/select.component';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import type { CommonPartSettings, PartSettingsPatch } from '../../core/music/part-settings.model';
import type { PartSettingsScope } from '../../core/project/part-settings-scope';
import { ProjectService } from '../../core/project/project.service';
import { resolvePartTrack } from '../../core/music/part-settings';
import { DEFAULT_DURATION_CHANCES, DEFAULT_DURATIONS, durationError, isDurationRow, joinDurationPauses, paddedDurations } from '../../core/music/durations';
import { validateDurationDraft } from '../../core/music/duration-validation';

const TICKS_PER_BEAT = 960;

interface DurationStroke {
  row: number;
  start: number;
  end: number;
  original: readonly number[];
}

@Component({
  imports: [LiveRecordingComponent, SelectComponent],
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
  private readonly initial = resolvePartTrack(this.tracks[0], this.section, this.initialProject.melodyGlobal).generatorSettings as CommonPartSettings;
  readonly trackCount = this.tracks.length;
  readonly texts = signal((this.initial.customDurations ?? DEFAULT_DURATIONS).map(row => joinDurationPauses(row).join(', ')));
  readonly chances = signal([...(this.initial.customDurationChances ?? DEFAULT_DURATION_CHANCES)]);
  readonly weighted = signal(this.initial.customDurationRandomWeighting ?? false);
  readonly flipped = signal(this.data.flipped ?? this.initial.patternFlip ?? false);
  readonly recordingLaneOptions = Array.from({ length: 10 }, (_, value) => ({ value, label: String(value + 1) }));
  readonly recordingLane = signal(0);
  readonly recordingBeat = signal<number | null>(null);
  private recordingBaseline: readonly number[] = [];
  private recordingIntervals: { start: number; end: number; sounded: boolean }[] = [];
  beginRecording(): void {
    this.recordingBaseline = [...this.rows()[this.recordingLane()]];
    this.recordingIntervals = [{ start: 0, end: 0, sounded: false }];
  }
  recordCursor(event: { beat: number; held: boolean }): void {
    if (this.stale()) return;
    this.recordingBeat.set(event.beat % 4);
    const current = this.recordingIntervals.at(-1);
    if (!current) return;
    current.end = event.beat;
    if (current.sounded !== event.held) this.recordingIntervals.push({ start: event.beat, end: event.beat, sounded: event.held });
    this.editRow(this.recordingLane(), this.recordingBaseline.join(', '));
    for (const [index, interval] of this.recordingIntervals.entries()) {
      // Silence only replaces complete cells; note attacks get at least one cell.
      const end = interval.sounded ? interval.end : Math.floor((interval.end + 1e-7) / this.grid()) * this.grid();
      const previous = this.recordingIntervals[index - 1];
      const start = interval.sounded ? interval.start : Math.max(Math.round(interval.start / this.grid()) * this.grid(),
        previous?.sounded ? (Math.floor(previous.start / this.grid()) + 1) * this.grid() : 0);
      if (!interval.sounded && end <= start) continue;
      this.writeRecordingSpan({ ...interval, start, end, midi: 60, velocity: 90 }, interval.sounded);
    }
  }
  private writeRecordingSpan(span: RecordedSpan, sounded: boolean): void {
    const lane = this.recordingLane(), grid = this.grid();
    for (const segment of recordedSegments(span, 4, grid)) {
      this.paint({ row: lane, start: Math.round(segment.start / grid),
        end: Math.round(segment.end / grid) - 1, original: this.rows()[lane] }, sounded);
    }
  }
  recordSpan(span: RecordedSpan): void {
    if (this.stale()) return;
    this.writeRecordingSpan(span, true);
  }
  readonly grid = signal(0.25);
  private readonly enteredRows = computed(() => this.texts().map(text => text.trim() ? text.split(',').map(value => value.trim() ? Number(value) : NaN) : []));
  readonly rows = computed(() => this.enteredRows().map(row => isDurationRow(row) ? joinDurationPauses(row) : row));
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
  readonly displayedNotes = computed(() => this.rows().map(row => {
    if (!row.length || row.some(value => !Number.isFinite(value) || value === 0) || row.reduce((sum, value) => sum + Math.abs(value), 0) > 4.0000001) return [];
    let start = 0;
    return paddedDurations(row, this.flipped()).flatMap(value => {
      const duration = Math.abs(value);
      const note = { start, duration };
      start += duration;
      return value > 0 ? [note] : [];
    });
  }));
  private gesture: (DurationStroke & { pointer: number; sounded: boolean }) | null = null;

  editRow(row: number, text: string): void { this.texts.update(rows => rows.map((value, index) => index === row ? text : value)); }
  finishTextEdit(row: number): void {
    const joined = this.rows()[row];
    if (joined.length < this.enteredRows()[row].length) this.editRow(row, joined.join(', '));
  }
  editChance(row: number, value: string): void { this.chances.update(chances => chances.map((chance, index) => index === row ? Number(value) : chance)); }
  private cell(event: PointerEvent): number {
    const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
    return Math.max(0, Math.min(this.steps().length - 1, Math.floor((event.clientX - bounds.left) / bounds.width * this.steps().length)));
  }
  start(event: PointerEvent, row: number): void {
    if (this.gesture || (event.button !== 0 && event.button !== 2)) return;
    event.preventDefault();
    const values = this.rows()[row];
    if (values.some(value => !Number.isFinite(value) || value === 0) || values.reduce((sum, value) => sum + Math.abs(value), 0) > 4.0000001) return;
    const cell = this.cell(event);
    const gesture = { pointer: event.pointerId, row, start: cell, end: cell, original: values, sounded: event.button === 0 };
    this.gesture = gesture;
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    this.paint(gesture, gesture.sounded);
  }
  move(event: PointerEvent): void {
    if (!this.gesture || this.gesture.pointer !== event.pointerId) return;
    const cell = this.cell(event);
    if (cell !== this.gesture.end) {
      this.gesture.end = cell;
      this.paint(this.gesture, this.gesture.sounded);
    }
  }
  finish(event: PointerEvent): void {
    if (this.gesture?.pointer === event.pointerId) this.gesture = null;
  }
  cancel(event: PointerEvent): void {
    if (this.gesture?.pointer !== event.pointerId) return;
    this.editRow(this.gesture.row, this.gesture.original.join(', '));
    this.gesture = null;
  }
  private paint(gesture: DurationStroke, sounded: boolean): void {
    const left = Math.round(Math.min(gesture.start, gesture.end) * this.grid() * TICKS_PER_BEAT);
    const right = Math.round((Math.max(gesture.start, gesture.end) + 1) * this.grid() * TICKS_PER_BEAT);
    const before: number[] = [], after: number[] = [];
    let elapsed = 0, start = 0;
    // Round boundaries before cutting so adjacent slots share an edge and no zero-length fragments survive.
    const original = gesture.original.length ? paddedDurations(gesture.original) : [this.flipped() ? 4 : -4];
    for (const value of original) {
      elapsed += Math.abs(value);
      const end = Math.round(elapsed * TICKS_PER_BEAT);
      const beforeTicks = Math.min(left, end) - start;
      const afterTicks = end - Math.max(start, right);
      if (beforeTicks > 0) before.push(Math.sign(value) * beforeTicks);
      if (afterTicks > 0) after.push(Math.sign(value) * afterTicks);
      start = end;
    }
    const sign = sounded !== this.flipped() ? 1 : -1;
    const joined = joinDurationPauses([...before, sign * (right - left), ...after]);
    this.editRow(gesture.row, joined.map(ticks => ticks / TICKS_PER_BEAT).join(', '));
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
