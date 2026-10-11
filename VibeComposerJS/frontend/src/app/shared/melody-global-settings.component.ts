import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import type { MelodyGlobalSettings } from '../core/project/project.model';
import { CompactKnobComponent } from './compact-knob.component';
import { CompactToggleComponent } from './compact-toggle.component';
import { DEFAULT_BLOCK_CHANGE_PREFERENCE, DEFAULT_BLOCK_TYPE_WEIGHTS, MELODY_BLOCK_FAMILIES } from '../core/music/melody-catalogue';
import { decodeMelodyGlobal } from '../core/music/melody-global-settings';

@Component({
  selector: 'vc-melody-global-settings',
  imports: [CompactKnobComponent, CompactToggleComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './melody-global-settings.component.html',
  styleUrl: './melody-global-settings.component.css',
})
export class MelodyGlobalSettingsComponent {
  readonly values = input<MelodyGlobalSettings>({});
  readonly settingsChanged = output<Partial<MelodyGlobalSettings>>();
  readonly blockFamilies = MELODY_BLOCK_FAMILIES;
  readonly defaultWeights = DEFAULT_BLOCK_TYPE_WEIGHTS;
  readonly blockPreferenceError = signal('');

  changeBlockWeight(index: number, value: number): void {
    const weights = [...(this.values().blockTypeWeights ?? this.defaultWeights)];
    weights[index] = value;
    this.settingsChanged.emit({ blockTypeWeights: weights });
  }

  changeBlockPreference(event: Event): void {
    const input = event.target as HTMLInputElement, text = input.value.trim();
    const preference = text === '' ? [] : /^\d+(?:\s*,\s*\d+)*$/.test(text) ? text.split(',').map(value => Number(value.trim())) : undefined;
    if (!preference || !decodeMelodyGlobal({ blockChangePreference: preference })) {
      this.blockPreferenceError.set('Enter every change 0–7 once, or leave empty for uniform selection.');
      input.value = (this.values().blockChangePreference ?? []).join(', ');
      return;
    }
    this.blockPreferenceError.set(''); input.value = preference.join(', ');
    this.settingsChanged.emit({ blockChangePreference: preference });
  }

  useBlockPreference(preferred: boolean): void {
    this.blockPreferenceError.set('');
    this.settingsChanged.emit({ blockChangePreference: preferred ? [...DEFAULT_BLOCK_CHANGE_PREFERENCE] : [] });
  }

  changePatternEffect(event: Event): void {
    const value = Number((event.target as HTMLSelectElement).value);
    if (value === 0 || value === 1 || value === 2) this.settingsChanged.emit({ patternEffect: value });
  }

  changeTargetDirection(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    if (value === 'ANY' || value === 'ASC' || value === 'DESC') this.settingsChanged.emit({ targetDirection: value });
  }
}
