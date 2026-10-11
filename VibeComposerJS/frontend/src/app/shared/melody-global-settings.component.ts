import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import type { MelodyGlobalSettings } from '../core/project/project.model';
import { CompactKnobComponent } from './compact-knob.component';
import { CompactToggleComponent } from './compact-toggle.component';

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
}
