import { LiveRecordingComponent } from '../recording/live-recording.component';
import { recordedSegments, recordingCell, type RecordedSpan } from '../../core/audio/live-recording';
import { SelectComponent } from '../../shared/select.component';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { combinedControlKeys } from '../../core/ui/ui-preferences';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import type { ArrangedPart } from '../../core/music/part-role';
import type { ChordRhythm, PartSettingsPatch } from '../../core/music/part-settings.model';
import type { PartSettingsScope } from '../../core/project/part-settings-scope';
import { ProjectService } from '../../core/project/project.service';
import { resolvePartTrack, settingsValues } from '../../core/music/part-settings';
import { chordRhythmMask } from '../../core/music/rhythm-pattern';
import { isMelodyGrid, MIN_MELODY_GRID_ATTACKS } from '../../core/music/melody-grid';
import { DEFAULT_CUSTOM_VELOCITIES } from '../../core/music/velocity-pattern';
import { MidiOutputService } from '../../core/audio/midi-output.service';

export interface PatternSequencerData { scope: PartSettingsScope; role: ArrangedPart; melodyHits: number }

interface GridStroke {
  id: string;
  last: number;
  project: ReturnType<ProjectService['project']>;
}

interface VelocityAdjustment { index: number; value: number; startY: number }

type GridGesture = GridStroke & (
  | { kind: 'hits'; pointer: number; sounded: number; adjustment?: VelocityAdjustment }
  | { kind: 'velocity-adjust'; pointer: number; adjustment: VelocityAdjustment }
  | { kind: 'velocity'; pointer: number | null; value: number }
);

@Component({
  imports: [LiveRecordingComponent, SelectComponent],
  selector: 'vc-pattern-sequencer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pattern-sequencer.component.html',
  styleUrl: './pattern-sequencer.component.css',
  host: {
    '(document:keyup.control)': 'finishHover()',
    '(keydown.escape)': 'cancelGesture($event)',
    '(window:blur)': 'cancelGesture()',
  },
})
export class PatternSequencerComponent {
  readonly data = inject<PatternSequencerData>(DIALOG_DATA);
  readonly dialog = inject(DialogRef);
  readonly projects = inject(ProjectService);
  private readonly midi = inject(MidiOutputService);
  readonly recordingCell = recordingCell;
  readonly recordingTrack = signal('');
  readonly recordingBeat = signal<number | null>(null);
  readonly recordingOptions = computed(() => this.rows().filter(row => row.supported).sort((left, right) => Number(right.active) - Number(left.active)).map(row => ({ value: row.id, label: row.name })));
  private recordingProject: ReturnType<ProjectService['project']> | null = null;
  private recordingId = '';
  private recordedHits = false;
  beginRecording(): void {
    this.recordedHits = false; this.cancel(); this.recordingProject = this.projects.project();
    this.recordingId = this.recordingTrack() || this.recordingOptions()[0]?.value || '';
    const row = this.rows().find(row => row.id === this.recordingId);
    if (row) this.draft.set({ id: row.id, pattern: [...row.pattern] });
  }
  recordCursor(event: { beat: number; held: boolean }): void {
    this.recordingBeat.set(event.beat % 4);
    if (this.recordingProject !== this.projects.project() || !event.held) return;
    const row = this.rows().find(row => row.id === this.recordingId);
    if (!row || !this.draft()?.pattern) return;
    this.recordedHits = true;
    const pattern = [...this.draft()!.pattern!]; pattern[recordingCell(event.beat, 4, row.hits)] = 1;
    this.draft.set({ id: row.id, pattern });
  }
  recordSpan(span: RecordedSpan): void {
    this.recordedHits = true;
    if (this.recordingProject !== this.projects.project()) return;
    const row = this.rows().find(row => row.id === this.recordingId);
    if (!row || !this.draft()?.pattern) return;
    const pattern = [...this.draft()!.pattern!];
    for (const segment of recordedSegments(span, 4, 4 / row.hits)) {
      for (let i = Math.round(segment.start * row.hits / 4); i < Math.round(segment.end * row.hits / 4); i++) pattern[i] = 1;
    }
    this.draft.set({ id: row.id, pattern });
  }
  endRecording(): void {
    const draft = this.draft(), valid = this.recordingProject === this.projects.project();
    this.recordingBeat.set(null); this.recordingProject = null; this.cancel();
    if (valid && this.recordedHits && draft?.pattern) this.storePattern(draft.id, draft.pattern);
  }
  readonly error = signal<string | null>(null);
  readonly minimumHits = this.data.role === 'melody' ? MIN_MELODY_GRID_ATTACKS : 1;
  readonly draft = signal<{ id: string; pattern?: number[]; velocities?: number[] } | null>(null);
  private gesture: GridGesture | null = null;
  readonly rows = computed(() => {
    const project = this.projects.project();
    const scope = this.data.scope;
    const section = 'sectionId' in scope ? project.arrangement.find(s => s.id === scope.sectionId) : undefined;
    const draft = this.draft();
    return project.tracks.filter(track => this.data.role === 'drums' ? track.role === 'drums'
      : 'trackId' in scope ? track.id === scope.trackId : track.role === this.data.role).map(track => {
      const effective = resolvePartTrack(track, section, project.melodyGlobal);
      const values = settingsValues(effective.generatorSettings, effective.role);
      const supported = track.role === 'melody' ? isMelodyGrid(values['rhythm'])
        : values['rhythm'] !== 'melody' && !(track.role === 'bass' && values['rhythm'] === 'bass1');
      const hits = values['rhythm'] === 'melody' ? this.data.melodyHits : Number(values['hitsPerPattern'] ?? 8);
      const pattern = draft?.id === track.id && draft.pattern ? draft.pattern : supported ? chordRhythmMask({
        rhythm: values['rhythm'] as ChordRhythm, hitsPerPattern: hits, patternShift: Number(values['patternShift'] ?? 0),
        patternFlip: values['patternFlip'] === true, euclideanPulses: Number(values['euclideanPulses'] ?? 4),
        customPattern: values['customPattern'] as readonly number[] | undefined }) : [];
      const velocities = draft?.id === track.id && draft.velocities ? draft.velocities
        : values['customVelocities'] as readonly number[] ?? DEFAULT_CUSTOM_VELOCITIES;
      const velocitySupported = track.role !== 'melody' && hits <= 32 && !(track.role === 'bass' && values['rhythm'] === 'bass1');
      return { id: track.id, name: track.name, instrument: effective.role === 'drums' ? this.midi.drumLabel(effective.generatorSettings.pitch, this.projects.project().drumSlots) : effective.role,
        values, supported, hits, pattern, velocities, velocitySupported,
        velocityEditing: velocitySupported && values['useCustomVelocities'] === true,
        slots: supported ? pattern : Array.from({ length: Math.min(32, hits) }, () => 1),
        active: 'trackId' in scope && track.id === scope.trackId };
    });
  });
  readonly velocityEditing = computed(() => this.rows().some(row => row.velocityEditing));
  readonly canMultiplyHits = computed(() => {
    const scope = this.data.scope;
    const rows = this.rows().filter(row => !('trackId' in scope) || row.id === scope.trackId);
    return this.data.role === 'drums' && rows.length > 0 && rows.every(row => row.supported);
  });
  multiplyHits(value: string): void {
    if (value && this.canMultiplyHits()) {
      this.cancel();
      this.projects.multiplyDrumHits(this.data.scope, Number(value));
    }
  }
  readonly steps = computed(() => Array.from({ length: Math.max(0, ...this.rows().map(row => Math.min(32, row.hits))) }, (_, i) => i));
  readonly scopeLabel = computed(() => {
    const scope = this.data.scope;
    return 'sectionId' in scope ? this.projects.project().arrangement.find(s => s.id === scope.sectionId)?.type ?? 'Section' : 'Global patterns';
  });

  patch(id: string, patch: PartSettingsPatch): void {
    const scope = this.data.scope;
    if (this.data.role === 'melody') this.projects.dismissGenerationError();
    this.projects.updatePartSettings('sectionId' in scope ? { kind: 'section-track', sectionId: scope.sectionId, trackId: id }
      : { kind: 'global-track', trackId: id }, patch);
    if (this.data.role === 'melody') this.error.set(this.projects.generationError());
  }

  changeHits(id: string, event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.value && input.checkValidity()) this.patch(id, { hitsPerPattern: input.valueAsNumber });
    input.value = String(this.rows().find(row => row.id === id)!.hits);
  }

  changeVelocityMode(id: string, event: Event): void {
    this.cancelGesture();
    this.patch(id, { useCustomVelocities: (event.target as HTMLInputElement).checked });
  }

  toggle(id: string, index: number, shift: boolean): void {
    const row = this.rows().find(row => row.id === id);
    if (!row?.supported || (row.velocityEditing && !shift) || this.recordingProject) return;
    const pattern = [...row.pattern]; pattern[index] = 1 - pattern[index];
    this.storePattern(id, pattern);
  }

  private storePattern(id: string, pattern: number[], velocities?: number[]): void {
    const row = this.rows().find(row => row.id === id)!;
    // Preserve hidden cells for an existing custom grid; bake other rhythms in audible coordinates.
    const custom = [...(row.values['customPattern'] as readonly number[] ?? Array(32).fill(1))];
    const shift = row.values['rhythm'] === 'custom' ? Number(row.values['patternShift'] ?? 0) : 0;
    const flip = row.values['rhythm'] === 'custom' && row.values['patternFlip'] === true;
    pattern.forEach((value, i) => custom[(i - shift + 32) % 32] = flip ? 1 - value : value);
    this.patch(id, { rhythm: 'custom', customPattern: custom,
      ...(velocities?.some((value, index) => value !== row.velocities[index]) ? { customVelocities: velocities } : {}),
      ...(row.values['rhythm'] === 'custom' ? {} : { patternShift: 0, patternFlip: false }) });
  }

  start(event: PointerEvent, id: string, index: number): void {
    this.finishHover();
    if (this.gesture || this.recordingProject) return;
    const row = this.rows().find(row => row.id === id)!;
    const cell = event.currentTarget as HTMLElement;
    const stroke = { pointer: event.pointerId, id, last: index, project: this.projects.project() };
    if (event.button === 0 && row.supported && (!row.velocityEditing || event.shiftKey)) {
      const sounded = 1 - row.pattern[index];
      const adjustment = row.velocityEditing && sounded === 1
        ? { index, value: this.velocityAt(event, cell), startY: event.clientY } : undefined;
      this.gesture = { ...stroke, kind: 'hits', sounded, adjustment };
      const pattern = [...row.pattern];
      pattern[index] = sounded;
      const velocities = adjustment ? [...row.velocities] : undefined;
      if (velocities && adjustment) velocities[index] = adjustment.value;
      this.draft.set({ id, pattern, velocities });
    } else if (row.velocityEditing && row.slots[index] > 0 && (event.button === 1 || (event.button === 0 && !event.shiftKey))) {
      const value = event.button === 0 ? row.velocities[index] : this.velocityAt(event, cell);
      const velocities = [...row.velocities];
      velocities[index] = value;
      this.gesture = event.button === 0
        ? { ...stroke, kind: 'velocity-adjust', adjustment: { index, value, startY: event.clientY } }
        : { ...stroke, kind: 'velocity', value };
      this.draft.set({ id, velocities });
    } else {
      if (row.velocityEditing && event.button === 1) event.preventDefault();
      return;
    }
    event.preventDefault();
    cell.focus({ preventScroll: true });
    (cell.parentElement ?? cell).setPointerCapture(event.pointerId);
  }

  move(event: PointerEvent, id: string): void {
    const gesture = this.gesture;
    const hover = event.ctrlKey && event.buttons === 0 && !this.recordingProject;
    if (gesture?.pointer === null && !hover) { this.finishHover(); return; }
    if (gesture ? gesture.id !== id || (gesture.pointer !== null && gesture.pointer !== event.pointerId) : !hover) return;
    if (gesture && gesture.project !== this.projects.project()) { this.cancel(); return; }
    if (gesture?.kind === 'velocity-adjust' || gesture?.kind === 'hits') {
      const adjustment = gesture.adjustment;
      if (adjustment) {
        const draft = this.draft()!;
        const value = this.clampVelocity(adjustment.value + (adjustment.startY - event.clientY) * 127 / 150);
        if (draft.velocities![adjustment.index] !== value) {
          const velocities = [...draft.velocities!];
          velocities[adjustment.index] = value;
          this.draft.set({ ...draft, velocities });
        }
      }
      if (gesture.kind === 'velocity-adjust') return;
    }
    const grid = event.currentTarget as HTMLElement;
    const cell = grid.ownerDocument.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('[data-step]');
    if (!cell || !grid.contains(cell)) return;
    const index = Number(cell.dataset['step']);
    if (gesture?.kind === 'hits') {
      if (gesture.last === index) return;
      const pattern = [...this.draft()!.pattern!];
      for (let i = Math.min(gesture.last, index); i <= Math.max(gesture.last, index); i++) pattern[i] = gesture.sounded;
      gesture.last = index;
      this.draft.set({ ...this.draft()!, pattern });
      return;
    }
    const row = this.rows().find(row => row.id === id)!;
    if (!row.velocityEditing) return;
    const value = this.velocityAt(event, cell);
    const velocities = [...row.velocities];
    if (!gesture) {
      if (row.slots[index] === 0) return;
      velocities[index] = value;
      this.gesture = { kind: 'velocity', pointer: null, project: this.projects.project(), id, last: index, value };
    } else {
      const distance = index - gesture.last;
      const direction = Math.sign(distance);
      for (let step = 1; step < Math.abs(distance); step++) {
        const intermediate = gesture.last + step * direction;
        if (row.slots[intermediate] > 0) {
          velocities[intermediate] = Math.round(gesture.value + (value - gesture.value) * step / Math.abs(distance));
        }
      }
      if (row.slots[index] > 0) velocities[index] = value;
      gesture.last = index;
      gesture.value = value;
    }
    this.draft.set({ id, velocities });
  }

  finish(event: PointerEvent): void {
    if (this.gesture?.pointer === event.pointerId) this.commitGesture();
  }

  finishHover(): void {
    if (this.gesture?.pointer === null) this.commitGesture();
  }

  private commitGesture(): void {
    const gesture = this.gesture, draft = this.draft();
    this.cancel();
    if (!gesture || gesture.project !== this.projects.project()) return;
    if (draft?.pattern) this.storePattern(gesture.id, draft.pattern, draft.velocities);
    else if (draft?.velocities) this.storeVelocities(gesture.id, draft.velocities);
  }

  private velocityAt(event: PointerEvent, cell: HTMLElement): number {
    const bounds = cell.getBoundingClientRect();
    return this.clampVelocity(127 * (bounds.bottom - event.clientY) / bounds.height);
  }

  private clampVelocity(value: number): number {
    return Math.max(0, Math.min(127, Math.round(value)));
  }

  private storeVelocities(id: string, velocities: number[]): void {
    const row = this.rows().find(row => row.id === id);
    if (row?.velocityEditing && velocities.some((value, index) => value !== row.velocities[index])) {
      this.patch(id, { customVelocities: velocities });
    }
  }

  cancel(): void { this.gesture = null; this.draft.set(null); }

  cancelGesture(event?: Event): void {
    if (!this.gesture) return;
    this.cancel();
    event?.stopPropagation();
  }

  wheelVelocity(id: string, index: number, event: WheelEvent): void {
    if (event.ctrlKey || event.metaKey || event.deltaY === 0 || this.gesture || this.recordingProject) return;
    const row = this.rows().find(row => row.id === id)!;
    if (!row.velocityEditing || row.slots[index] === 0) return;
    event.preventDefault(); event.stopPropagation();
    const velocities = [...row.velocities];
    velocities[index] = this.clampVelocity(velocities[index] - Math.sign(event.deltaY) * (event.shiftKey ? 1 : 6));
    this.storeVelocities(id, velocities);
  }

  resetVelocity(id: string, index: number): void {
    this.finishHover();
    if (this.gesture || this.recordingProject) return;
    const row = this.rows().find(row => row.id === id)!;
    if (!row.velocityEditing || row.slots[index] === 0) return;
    const velocities = [...row.velocities];
    velocities[index] = Math.floor((Number(row.values['velocityMin']) + Number(row.values['velocityMax'])) / 2);
    this.storeVelocities(id, velocities);
  }

  velocityKey(event: KeyboardEvent, id: string, index: number): void {
    const row = this.rows().find(row => row.id === id)!;
    if (!row.velocityEditing) return;
    if (event.shiftKey && (event.key === ' ' || event.key === 'Enter')) {
      event.preventDefault();
      if (!event.repeat && !this.gesture) this.toggle(id, index, true);
      return;
    }
    if (this.gesture || this.recordingProject || row.slots[index] === 0) return;
    const current = row.velocities[index];
    const values: Record<string, number> = {
      ArrowUp: current + 1, ArrowRight: current + 1, ArrowDown: current - 1, ArrowLeft: current - 1,
      PageUp: current + 6, PageDown: current - 6, Home: 0, End: 127,
    };
    if (!Object.hasOwn(values, event.key)) return;
    event.preventDefault();
    const velocities = [...row.velocities];
    velocities[index] = this.clampVelocity(values[event.key]);
    this.storeVelocities(id, velocities);
  }

  inherit(id: string, field: string): void {
    const scope = this.data.scope;
    if (!('sectionId' in scope)) return;
    if (this.data.role === 'melody') this.projects.dismissGenerationError();
    this.projects.resetPartSettings({ kind: 'section-track', sectionId: scope.sectionId, trackId: id },
      combinedControlKeys(field, this.data.role === 'melody' ? 'melody' : undefined));
    if (this.data.role === 'melody') this.error.set(this.projects.generationError());
  }
}
