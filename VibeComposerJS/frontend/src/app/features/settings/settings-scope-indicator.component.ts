import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type SettingScope = 'ui' | 'compose' | 'sound';

@Component({
  selector: 'vc-settings-scope-indicator',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-scope]': 'scope()' },
  templateUrl: './settings-scope-indicator.component.html',
  styleUrl: './settings-scope-indicator.component.css',
})
export class SettingsScopeIndicatorComponent {
  readonly scope = input.required<SettingScope>();
  readonly definitions = {
    ui: { symbol: '⚙', label: 'UI behavior / appearance', description: 'Changes the interface, appearance or editing workflow.' },
    compose: { symbol: '✧', label: 'Rerolling / composing', description: 'Used when composing, preparing or rerolling musical choices.' },
    sound: { symbol: '♫', label: 'Generation / track sound', description: 'Affects generated notes, voicing or the sound of tracks.' },
  };
}
