import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
import { registerTooltip } from '../../shared/tooltip.directive';
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

  constructor() {
    const preferences = inject(UiPreferencesService);
    const explainer = computed(() => preferences.preferences().settingsExplainersAsTooltips ? this.description() : null);
    registerTooltip(explainer, { preferOnFocus: true });
  }
}
