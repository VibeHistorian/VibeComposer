import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import type { ArrangedPart } from '../core/project/project.model';
import { PART_CONTROLS, type PartControl } from '../core/music/part-settings';
import { CompactKnobComponent } from './compact-knob.component';

@Component({
  selector: 'vc-part-settings-editor',
  imports: [CompactKnobComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
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
  readonly controls = computed(() => PART_CONTROLS[this.role()]);

  numericValue(control: PartControl): number {
    return (this.values()[control.key] as number | null) ?? control.defaultValue ?? control.minimum!;
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
