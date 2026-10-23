import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import type { ArrangementSection, SectionMusicalContext } from '../../core/project/arrangement.model';
import { ProjectService } from '../../core/project/project.service';
import { KEYS } from '../../core/music/harmony';
import { SCALE_MODES, SCALE_DEFINITIONS } from '../../core/music/scales';
import { sectionChordDurations, sectionDurationBeats, sectionHasChordOverride } from '../../core/music/section-context';
import { ChordletEditorComponent } from '../harmony/chordlet-editor.component';
import { SelectComponent } from '../../shared/select.component';

@Component({
  selector: 'vc-section-context-editor', imports: [ChordletEditorComponent, SelectComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './section-context-editor.component.html', styleUrl: './section-context-editor.component.css',
})
export class SectionContextEditorComponent {
  readonly keyOptions = computed(() => [{ value: '', label: 'Project key' }, ...this.keys.map(key => ({ value: key, label: key }))]);
  readonly scaleOptions = computed(() => [{ value: '', label: 'Project scale' }, ...this.scales.map(scale => ({ value: scale, label: this.scaleDefinitions[scale].label }))]);

  readonly scales = SCALE_MODES;
  readonly scaleDefinitions = SCALE_DEFINITIONS;
  readonly projects = inject(ProjectService);
  readonly section = input.required<ArrangementSection>();
  readonly keys = KEYS;
  readonly hasChordOverride = computed(() => sectionHasChordOverride(this.projects.project(), this.section()));
  readonly duration = computed(() => sectionDurationBeats(this.projects.project(), this.section()));
  readonly durations = computed(() => this.section().customDurations
    ?? sectionChordDurations(this.projects.project(), this.section()).map(value => value / (this.section().context?.beatDurationMultiplier ?? 1)));
  readonly numericFields = [
    { key: 'tempoBpm', label: 'BPM', min: 30, max: 300, step: 1 },
    { key: 'swingPercent', label: 'Swing %', min: 0, max: 100, step: 1 },
    { key: 'keyChange', label: 'Key change (semitones)', min: -24, max: 24, step: 1 },
  ] as const;
  inherited(key: string): string {
    return key === 'tempoBpm' ? String(this.projects.project().tempoBpm) : key === 'swingPercent' ? 'Part swing' : '0';
  }
  update(key: keyof SectionMusicalContext, event: Event, numeric = false): void {
    this.choose(key, (event.target as HTMLInputElement).value, numeric);
  }
  choose(key: keyof SectionMusicalContext, value: string, numeric = false): void {
    this.projects.updateSectionContext(this.section().id, { [key]: value === '' ? null : numeric ? Number(value) : value });
  }
  reset(key: keyof SectionMusicalContext): void { this.projects.updateSectionContext(this.section().id, { [key]: null }); }
  setDuration(index: number, event: Event): void {
    const durations = [...this.durations()]; durations[index] = Number((event.target as HTMLInputElement).value);
    this.projects.setSectionMusicalState(this.section().id, { customDurations: durations });
  }
  clearDurations(): void { this.projects.setSectionMusicalState(this.section().id, { customDurations: undefined }); }
  clearHarmony(): void { this.projects.setSectionMusicalState(this.section().id, { customChords: undefined, chordDegrees: undefined }); }
}
