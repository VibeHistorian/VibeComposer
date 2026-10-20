import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { combinedControlKeys } from '../../core/ui/ui-preferences';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import type { ArrangedPart } from '../../core/music/part-role';
import type { ChordRhythm, PartSettingsPatch } from '../../core/music/part-settings.model';
import type { PartSettingsScope } from '../../core/project/part-settings-scope';
import { ProjectService } from '../../core/project/project.service';
import { resolvePartTrack, settingsValues } from '../../core/music/part-settings';
import { chordRhythmMask } from '../../core/music/rhythm-pattern';
import { DEFAULT_CUSTOM_VELOCITIES } from '../../core/music/velocity-pattern';
import { MidiOutputService } from '../../core/audio/midi-output.service';

export interface PatternSequencerData { scope: PartSettingsScope; role: ArrangedPart; velocities: boolean; melodyHits: number }

@Component({
  selector: 'vc-pattern-sequencer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pattern-sequencer.component.html',
  styleUrl: './pattern-sequencer.component.css',
})
export class PatternSequencerComponent {
  readonly data = inject<PatternSequencerData>(DIALOG_DATA);
  readonly dialog = inject(DialogRef);
  readonly projects = inject(ProjectService);
  private readonly midi = inject(MidiOutputService);
  readonly showVelocities = signal(this.data.velocities);
  readonly draft = signal<{ id: string; pattern?: number[]; velocities?: number[] } | null>(null);
  private gesture: { pointer: number; id: string; last: number; sounded: number; project: ReturnType<ProjectService['project']> } | null = null;
  private velocityProject: ReturnType<ProjectService['project']> | null = null;
  readonly rows = computed(() => {
    const project = this.projects.project();
    const scope = this.data.scope;
    const section = 'sectionId' in scope ? project.arrangement.find(s => s.id === scope.sectionId) : undefined;
    const draft = this.draft();
    return project.tracks.filter(track => this.data.role === 'drums' ? track.role === 'drums'
      : 'trackId' in scope ? track.id === scope.trackId : track.role === this.data.role).map(track => {
      const effective = resolvePartTrack(track, section);
      const values = settingsValues(effective.generatorSettings, effective.role);
      const supported = track.role !== 'melody' && values['rhythm'] !== 'melody' && !(track.role === 'bass' && values['rhythm'] === 'bass1');
      const hits = values['rhythm'] === 'melody' || track.role === 'melody' ? this.data.melodyHits : Number(values['hitsPerPattern'] ?? 8);
      const pattern = draft?.id === track.id && draft.pattern ? draft.pattern : supported ? chordRhythmMask({
        rhythm: values['rhythm'] as ChordRhythm, hitsPerPattern: hits, patternShift: Number(values['patternShift'] ?? 0),
        patternFlip: values['patternFlip'] === true, euclideanPulses: Number(values['euclideanPulses'] ?? 4),
        customPattern: values['customPattern'] as readonly number[] | undefined }) : [];
      const velocities = draft?.id === track.id && draft.velocities ? draft.velocities
        : [...(values['customVelocities'] as readonly number[] ?? DEFAULT_CUSTOM_VELOCITIES)];
      return { id: track.id, name: track.name, instrument: effective.role === 'drums' ? this.midi.drumLabel(effective.generatorSettings.pitch) : effective.role,
        values, supported, hits, pattern, velocities: velocities.slice(0, Math.min(32, hits)), velocitySupported: hits <= 32 && !(track.role === 'bass' && values['rhythm'] === 'bass1'),
        active: 'trackId' in scope && track.id === scope.trackId };
    });
  });
  readonly canMultiplyHits = computed(() => {
    const scope = this.data.scope;
    const rows = this.rows().filter(row => !('trackId' in scope) || row.id === scope.trackId);
    return this.data.role === 'drums' && rows.length > 0 && rows.every(row => row.supported);
  });
  multiplyHits(event: Event): void {
    const select = event.target as HTMLSelectElement;
    if (select.value && this.canMultiplyHits()) {
      this.cancel();
      this.projects.multiplyDrumHits(this.data.scope, Number(select.value));
    }
    select.value = '';
  }
  readonly steps = computed(() => Array.from({ length: Math.max(0, ...this.rows().map(row => Math.min(32, row.hits))) }, (_, i) => i));
  readonly scopeLabel = computed(() => {
    const scope = this.data.scope;
    return 'sectionId' in scope ? this.projects.project().arrangement.find(s => s.id === scope.sectionId)?.type ?? 'Section' : 'Global patterns';
  });

  patch(id: string, patch: PartSettingsPatch): void {
    const scope = this.data.scope;
    this.projects.updatePartSettings('sectionId' in scope ? { kind: 'section-track', sectionId: scope.sectionId, trackId: id }
      : { kind: 'global-track', trackId: id }, patch);
  }

  toggle(id: string, index: number): void {
    const row = this.rows().find(row => row.id === id);
    if (!row?.supported) return;
    const pattern = [...row.pattern]; pattern[index] = 1 - pattern[index];
    this.storePattern(id, pattern);
  }

  private storePattern(id: string, pattern: number[]): void {
    const row = this.rows().find(row => row.id === id)!;
    // Preserve hidden cells for an existing custom grid; bake other rhythms in audible coordinates.
    const custom = [...(row.values['customPattern'] as readonly number[] ?? Array(32).fill(1))];
    const shift = row.values['rhythm'] === 'custom' ? Number(row.values['patternShift'] ?? 0) : 0;
    const flip = row.values['rhythm'] === 'custom' && row.values['patternFlip'] === true;
    pattern.forEach((value, i) => custom[(i - shift + 32) % 32] = flip ? 1 - value : value);
    this.patch(id, { rhythm: 'custom', customPattern: custom,
      ...(row.values['rhythm'] === 'custom' ? {} : { patternShift: 0, patternFlip: false }) });
  }

  start(event: PointerEvent, id: string, index: number): void {
    if (event.button !== 0 || this.gesture) return;
    const row = this.rows().find(row => row.id === id)!;
    event.preventDefault(); (event.target as HTMLElement).focus();
    this.gesture = { pointer: event.pointerId, id, last: index, sounded: 1 - row.pattern[index], project: this.projects.project() };
    const pattern = [...row.pattern]; pattern[index] = this.gesture.sounded;
    this.draft.set({ id, pattern });
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
  }

  move(event: PointerEvent): void {
    const gesture = this.gesture;
    if (!gesture || gesture.pointer !== event.pointerId) return;
    if (gesture.project !== this.projects.project()) { this.cancel(); return; }
    const grid = event.currentTarget as HTMLElement;
    const cell = grid.ownerDocument.elementFromPoint(event.clientX, event.clientY)?.closest<HTMLElement>('[data-step]');
    if (!cell || !grid.contains(cell)) return;
    const index = Number(cell.dataset['step']);
    const pattern = [...this.draft()!.pattern!];
    for (let i = Math.min(gesture.last, index); i <= Math.max(gesture.last, index); i++) pattern[i] = gesture.sounded;
    gesture.last = index; this.draft.set({ id: gesture.id, pattern });
  }

  finish(event: PointerEvent): void {
    const gesture = this.gesture, draft = this.draft();
    if (!gesture || gesture.pointer !== event.pointerId) return;
    this.cancel();
    if (draft?.pattern && gesture.project === this.projects.project()) this.storePattern(gesture.id, draft.pattern);
  }

  previewVelocity(id: string, index: number, event: Event): void {
    if (this.velocityProject && this.velocityProject !== this.projects.project()) { this.cancel(); return; }
    this.velocityProject ??= this.projects.project();
    const row = this.rows().find(row => row.id === id)!;
    const velocities = [...(this.draft()?.id === id ? this.draft()?.velocities : null)
      ?? row.values['customVelocities'] as readonly number[] ?? DEFAULT_CUSTOM_VELOCITIES];
    velocities[index] = Math.max(0, Math.min(127, Number((event.target as HTMLInputElement).value)));
    this.draft.set({ id, velocities });
  }

  commitVelocity(id: string, index: number, event: Event): void {
    if (this.velocityProject && this.velocityProject !== this.projects.project()) { this.cancel(); return; }
    this.previewVelocity(id, index, event);
    const velocities = this.draft()?.velocities;
    this.cancel();
    if (velocities) this.patch(id, { useCustomVelocities: true, customVelocities: velocities });
  }

  cancel(): void { this.gesture = null; this.velocityProject = null; this.draft.set(null); }

  wheelVelocity(id: string, index: number, event: WheelEvent): void {
    if (event.ctrlKey || event.metaKey || event.deltaY === 0 || this.velocityProject) return;
    event.preventDefault(); event.stopPropagation();
    const row = this.rows().find(row => row.id === id)!;
    const grid = [...(row.values['customVelocities'] as readonly number[] ?? DEFAULT_CUSTOM_VELOCITIES)];
    grid[index] = Math.max(0, Math.min(127, grid[index] + (event.deltaY < 0 ? 1 : -1) * (event.shiftKey ? 1 : 6)));
    this.patch(id, { useCustomVelocities: true, customVelocities: grid });
  }

  inherit(id: string, field: string): void {
    const scope = this.data.scope;
    if ('sectionId' in scope) this.projects.resetPartSettings({ kind: 'section-track', sectionId: scope.sectionId, trackId: id }, combinedControlKeys(field));
  }
}
