import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import type { MelodyGlobalSettings } from '../../core/music/part-settings.model';
import { CompactKnobComponent } from '../../shared/compact-knob.component';
import { CompactToggleComponent } from '../../shared/compact-toggle.component';
import { DEFAULT_BLOCK_CHANGE_PREFERENCE, DEFAULT_BLOCK_TYPE_WEIGHTS, MELODY_BLOCK_FAMILIES } from '../../core/music/melody-catalogue';
import { SelectComponent } from '../../shared/select.component';
import { DEFAULT_MELODY_GLOBAL, decodeMelodyGlobal } from '../../core/music/melody-global-settings';

@Component({
  selector: 'vc-melody-global-settings',
  imports: [CompactKnobComponent, CompactToggleComponent, SelectComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './melody-global-settings.component.html',
  styleUrl: './melody-global-settings.component.css',
})
export class MelodyGlobalSettingsComponent {
  readonly contextualNumbers = [
    { key: 'rangeMin', label: 'Melody Low MIDI', min: 0, max: 127, fallback: 48 },
    { key: 'rangeMax', label: 'Melody High MIDI', min: 0, max: 127, fallback: 72 },
    { key: 'tension', label: 'Melody Tension', min: 0, max: 100, fallback: 35 },
    { key: 'phraseBars', label: 'Phrase Bars', min: 1, max: 16, fallback: 2 },
    { key: 'beatsPerBar', label: 'Beats per Bar', min: 1, max: 12, fallback: 4 },
  ] as const;
  showAlgorithm(contextual: boolean): boolean {
    return !!this.values().contextualMelody === contextual;
  }
  numeric(key: 'rangeMin' | 'rangeMax' | 'tension' | 'phraseBars' | 'beatsPerBar'): number {
    return this.values()[key] ?? DEFAULT_MELODY_GLOBAL[key];
  }
  changeContextual(key: keyof MelodyGlobalSettings, value: unknown): void {
    const patch = { [key]: value };
    if (decodeMelodyGlobal({ ...DEFAULT_MELODY_GLOBAL, ...this.values(), ...patch })) this.settingsChanged.emit(patch);
  }
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

  changeTargetMode(event: Event): void {
    const value = Number((event.target as HTMLSelectElement).value);
    if (value === 0 || value === 1 || value === 2) this.settingsChanged.emit({ blockTargetMode: value });
  }
}
