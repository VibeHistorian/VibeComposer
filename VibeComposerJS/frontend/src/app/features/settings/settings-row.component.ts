import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { SettingsScopeIndicatorComponent, type SettingScope } from './settings-scope-indicator.component';

/** Presentation only: projected controls retain their owner's bindings and commands. */
@Component({
  selector: '[vcSettingRow]',
  imports: [SettingsScopeIndicatorComponent],
  templateUrl: './settings-row.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsRowComponent {
  readonly label = input.required<string>();
  readonly description = input('');
  readonly descriptionId = input<string>();
  readonly scope = input<SettingScope>();
}
