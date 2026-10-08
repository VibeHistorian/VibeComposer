import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import type { ArrangedPart, ChordRhythm } from '../core/project/project.model';
import { PART_CONTROLS, type PartControl } from '../core/music/part-settings';
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
  readonly values = input.required<Readonly<Record<string, string | number | boolean | null>>>();
  readonly overriddenKeys = input<readonly string[]>([]);
  readonly local = input(false);
  readonly settingsChanged = output<{ readonly key: string; readonly value: string | number | boolean }>();
  readonly inheritRequested = output<string>();
  readonly controls = computed(() => PART_CONTROLS[this.role()].filter((control) =>
    control.key !== 'euclideanPulses' || this.values()['rhythm'] === 'euclid' || this.values()['rhythm'] === null));
  readonly rhythmPreview = computed(() => {
    if (this.role() !== 'chords') return undefined;
    const values = this.values();
    const keys = ['rhythm', 'hitsPerPattern', 'patternShift', 'patternFlip',
      ...(values['rhythm'] === 'euclid' ? ['euclideanPulses'] : [])];
    if (keys.some((key) => values[key] === null)) return null;
    return chordRhythmMask({ rhythm: values['rhythm'] as ChordRhythm,
      hitsPerPattern: values['hitsPerPattern'] as number | undefined,
      patternShift: values['patternShift'] as number | undefined,
      patternFlip: values['patternFlip'] as boolean | undefined,
      euclideanPulses: values['euclideanPulses'] as number | undefined });
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
}
