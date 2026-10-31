import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { SECTION_TYPES, type ArrangementSection, type SectionMusicalContext } from '../../core/project/arrangement.model';
import { ProjectService } from '../../core/project/project.service';
import { ARRANGED_PARTS, type ArrangedPart } from '../../core/music/part-role';
import { MAX_VOLUME_PERCENT } from '../../core/audio/mix-values';
import { KEYS } from '../../core/music/harmony';
import { SCALE_MODES, SCALE_DEFINITIONS } from '../../core/music/scales';
import { sectionChordDurations, sectionHasChordOverride } from '../../core/music/section-context';
import { ChordletEditorComponent } from '../harmony/chordlet-editor.component';
import { CompactKnobComponent } from '../../shared/compact-knob.component';
import { SelectComponent } from '../../shared/select.component';

@Component({
  selector: 'vc-section-context-editor', imports: [ChordletEditorComponent, SelectComponent, CompactKnobComponent],
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
  readonly allSections = input(false);
  readonly sectionTypes = SECTION_TYPES;
  mixedType(): boolean { return this.mixed(section => section.type); }
  mixedMeasures(): boolean { return this.mixed(section => section.measures); }
  readonly arrangementFlags = ['useMelodyHarmony', 'swapMelody'] as const;
  flagMixed(key: 'useMelodyHarmony' | 'swapMelody'): boolean { return this.mixed(section => section[key] ?? false); }
  setFlag(key: 'useMelodyHarmony' | 'swapMelody', event: Event): void {
    const value = (event.target as HTMLInputElement).checked;
    this.apply(section => ({ ...section, [key]: value }));
  }
  setAllType(value: string): void {
    this.projects.editAllSectionMusic(section => ({ ...section, type: value as ArrangementSection['type'] }));
  }
  setAllMeasures(event: Event): void {
    const measures = Number((event.target as HTMLInputElement).value);
    this.projects.editAllSectionMusic(section => ({ ...section, measures }));
  }
  presenceMixed(trackId: string): boolean { return this.mixed(section => section.trackPresence?.[trackId] ?? false); }
  setPresence(trackId: string, event: Event): void {
    const present = (event.target as HTMLInputElement).checked;
    this.projects.editAllSectionMusic(section => ({ ...section, trackPresence: { ...section.trackPresence, [trackId]: present } }));
  }
  mixed(read: (section: ArrangementSection) => unknown): boolean {
    return this.allSections() && this.projects.project().arrangement.some(section => JSON.stringify(read(section)) !== JSON.stringify(read(this.section())));
  }
  contextMixed(key: keyof SectionMusicalContext): boolean { return this.mixed(section => section.context?.[key]); }
  velocityMixed(role: ArrangedPart): boolean { return this.mixed(section => section.context?.velocityPercent?.[role] ?? 100); }
  durationValues(section: ArrangementSection): readonly number[] {
    return section.customDurations ?? sectionChordDurations(this.projects.project(), section).map(value => value / (section.context?.beatDurationMultiplier ?? 1));
  }
  durationMixed(index: number): boolean { return this.mixed(section => this.durationValues(section)[index]); }
  apply(change: (section: ArrangementSection) => ArrangementSection): void {
    if (this.allSections()) {
      this.projects.editAllSectionMusic(change);
      return;
    }
    // Consecutive events can arrive before Angular refreshes the section input.
    const current = this.projects.project().arrangement.find(section => section.id === this.section().id);
    if (!current) return;
    const next = change(current);
    this.projects.setSectionMusicalState(current.id, {
      context: next.context, customChords: next.customChords, chordDegrees: next.chordDegrees, customDurations: next.customDurations });
  }
  resetTiming(): void { this.apply(section => ({ ...section, context: section.context?.velocityPercent ? { velocityPercent: section.context.velocityPercent } : undefined })); }
  readonly keys = KEYS;
  readonly hasChordOverride = computed(() => sectionHasChordOverride(this.projects.project(), this.section()));
  readonly maximumVelocity = MAX_VOLUME_PERCENT;
  readonly roles = ARRANGED_PARTS;
  readonly roleLabels: Record<ArrangedPart, string> = { melody: 'Melody', bass: 'Bass', chords: 'Chords', arpeggio: 'Arps', drums: 'Drums' };
  setVelocity(role: ArrangedPart, value: number): void {
    this.apply(section => {
      const velocityPercent = { ...section.context?.velocityPercent };
      if (value === 100) delete velocityPercent[role]; else velocityPercent[role] = value;
      const context = { ...section.context };
      if (Object.keys(velocityPercent).length) context.velocityPercent = velocityPercent;
      else delete context.velocityPercent;
      return { ...section, context: Object.keys(context).length ? context : undefined };
    });
  }
  readonly durations = computed(() => this.allSections() ? Array.from({ length: Math.max(...this.projects.project().arrangement.map(section => this.durationValues(section).length)) }, (_, index) => this.durationValues(this.section())[index] ?? 4) : this.durationValues(this.section()));
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
    this.apply(section => {
      const context = { ...section.context };
      if (value === '') delete context[key]; else Object.assign(context, { [key]: numeric ? Number(value) : value });
      return { ...section, context: Object.keys(context).length ? context : undefined };
    });
  }
  reset(key: keyof SectionMusicalContext): void { this.choose(key, ''); }
  setDuration(index: number, event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    this.apply(section => {
      const durations = [...this.durationValues(section)];
      while (durations.length <= index) durations.push(4);
      durations[index] = value;
      return { ...section, customDurations: durations };
    });
  }
  clearDurations(): void { this.apply(section => ({ ...section, customDurations: undefined })); }
  clearHarmony(): void { this.apply(section => ({ ...section, customChords: undefined, chordDegrees: undefined })); }
}
