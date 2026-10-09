import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import type { ArpeggioGenerationPolicy } from '../core/project/project.model';
import { ARPEGGIO_POLICY_CONTROLS, type PolicyControl } from '../core/music/track-generation';

@Component({
  selector: 'vc-track-generation-policy',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './track-generation-policy.component.html',
  styleUrl: './track-generation-policy.component.css',
})
export class TrackGenerationPolicyComponent {
  readonly policy = input.required<ArpeggioGenerationPolicy>();
  readonly advanced = input(false);
  readonly disabled = input(false);
  readonly policyChanged = output<Partial<ArpeggioGenerationPolicy>>();
  readonly controls = computed(() => ARPEGGIO_POLICY_CONTROLS.filter(control => this.advanced() || !control.advanced));

  inactive(control: PolicyControl): boolean {
    return this.disabled() || (control.key === 'fixedHits' && this.policy().randomHits)
      || (control.key === 'powerOfTwo' && !this.policy().randomHits)
      || (control.key === 'shiftChance' && !this.policy().patterns)
      || (['voices', 'voicesChance'].includes(control.key) && this.policy().voicesMode === 'NONE');
  }

  change(control: PolicyControl, event: Event): void {
    if (this.inactive(control)) return;
    const element = event.target as HTMLInputElement;
    const value = control.options ? element.value : control.minimum !== undefined ? Number(element.value) : element.checked;
    this.policyChanged.emit({ [control.key]: value });
    // Invalid commits may leave the parent value unchanged; restore the visible canonical value.
    element.value = String(this.policy()[control.key]);
  }
}
