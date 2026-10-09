import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import type { ArrangedPart, TrackGenerationPolicy, TrackGenerationPolicyPatch } from '../core/project/project.model';
import { TRACK_POLICY_CONTROLS, type PolicyControl } from '../core/music/track-generation';

@Component({
  selector: 'vc-track-generation-policy',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './track-generation-policy.component.html',
  styleUrl: './track-generation-policy.component.css',
})
export class TrackGenerationPolicyComponent {
  readonly role = input<ArrangedPart>('arpeggio');
  readonly policy = input.required<TrackGenerationPolicy>();
  readonly values = computed(() => this.policy() as unknown as Record<string, string | number | boolean>);
  readonly expanded = input(false);
  readonly disabled = input(false);
  readonly policyChanged = output<TrackGenerationPolicyPatch>();
  readonly generationRequested = output<number>();
  readonly capacity = input(16);
  readonly generateCount = signal(1);
  readonly controls = computed(() => TRACK_POLICY_CONTROLS[this.role()]);

  changeCount(event: Event): void {
    const element = event.target as HTMLInputElement;
    const count = Number(element.value);
    if (Number.isInteger(count) && count >= 1 && count <= 16) this.generateCount.set(count);
    element.value = String(this.generateCount());
  }

  inactive(control: PolicyControl): boolean {
    return this.disabled() || (control.key === 'fixedHits' && this.values()['randomHits'] === true)
      || (control.key === 'sameSeed' && this.values()['rerollSeeds'] === false)
      || (control.key === 'powerOfTwo' && this.values()['randomHits'] === false)
      || (control.key === 'shiftChance' && this.values()['patterns'] === false)
      || (['voices', 'voicesChance'].includes(control.key) && this.values()['voicesMode'] === 'NONE');
  }

  change(control: PolicyControl, event: Event): void {
    if (this.inactive(control)) return;
    const element = event.target as HTMLInputElement;
    const value = control.options ? element.value : control.minimum !== undefined ? Number(element.value) : element.checked;
    this.policyChanged.emit({ [control.key]: value });
    // Invalid commits may leave the parent value unchanged; restore the visible canonical value.
    element.value = String(this.values()[control.key]);
  }
}
