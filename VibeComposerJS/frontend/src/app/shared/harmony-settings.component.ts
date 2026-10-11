import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import type { ArrangementSection } from '../core/project/project.model';
import { ProjectService } from '../core/project/project.service';
import { DEFAULT_HARMONY_SETTINGS } from '../core/music/harmony-voicing';
import { projectMelodyHarmony } from '../core/music/project-harmony';
import { getDiatonicChords, KEYS } from '../core/music/harmony';
import { CompactKnobComponent } from './compact-knob.component';
import { CompactToggleComponent } from './compact-toggle.component';

@Component({
  selector: 'vc-harmony-settings',
  imports: [CompactKnobComponent, CompactToggleComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './harmony-settings.component.html',
  styleUrl: './harmony-settings.component.css',
})
export class HarmonySettingsComponent {
  readonly projects = inject(ProjectService);
  readonly section = input<ArrangementSection>();
  readonly values = computed(() => ({ ...DEFAULT_HARMONY_SETTINGS, ...this.projects.project().harmonySettings }));
  readonly inference = computed(() => projectMelodyHarmony(this.projects.project()));
  readonly source = computed(() => this.projects.project().tracks.find(track => track.id === this.inference().sourceTrackId));
  readonly alternateNames = computed(() => {
    const project = this.projects.project(), harmony = this.inference().harmony;
    const diatonic = getDiatonicChords(project.key, project.scale);
    return harmony.degrees.map((degree,index) => {
      const custom = harmony.customChords?.[index];
      return custom ? `${KEYS[custom.root]}${custom.spice}${custom.inversion ? '.' + custom.inversion : ''}`
        : diatonic[degree - 1].symbol;
    }).join(' · ');
  });
}
