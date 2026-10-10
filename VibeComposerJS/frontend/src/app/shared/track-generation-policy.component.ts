import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import type { ArrangedPart, InstrumentGenerationPolicy, TrackGenerationPolicy, TrackGenerationPolicyPatch } from '../core/project/project.model';
import { DEFAULT_INSTRUMENT_POLICIES, instrumentPoolOptions } from '../core/music/instrument-generation';
import { TRACK_POLICY_CONTROLS, automaticPolicyDefault, type PolicyControl } from '../core/music/track-generation';

@Component({
  selector: 'vc-track-generation-policy',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './track-generation-policy.component.html',
  styleUrl: './track-generation-policy.component.css',
})
export class TrackGenerationPolicyComponent {
  readonly role = input<ArrangedPart>('arpeggio');
  readonly policy = input.required<TrackGenerationPolicy>();
  readonly values = computed(() => ({ onCompose: false, onRegenerate: false,
    targetsOnCompose: automaticPolicyDefault('targetsOnCompose'), patternsOnCompose: automaticPolicyDefault('patternsOnCompose'),
    ...this.policy() }) as unknown as Record<string, string | number | boolean>);
  readonly expanded = input(false);
  readonly disabled = input(false);
  readonly policyChanged = output<TrackGenerationPolicyPatch>();
  readonly instrumentPolicy = input<InstrumentGenerationPolicy>(DEFAULT_INSTRUMENT_POLICIES.arpeggio);
  readonly instrumentPolicyChanged = output<Partial<InstrumentGenerationPolicy>>();
  readonly instrumentPools = computed(() => instrumentPoolOptions(this.role()));
  readonly poolLabels = { AUTO: 'Chord / Pluck', ALL: 'All', MELODY: 'Melody', BASS: 'Bass', CHORD: 'Chord',
    PLUCK: 'Pluck', LONG_PAD: 'Long pad', DRUM: 'Percussion' };
  readonly controls = computed(() => TRACK_POLICY_CONTROLS[this.role()]);

  inactive(control: PolicyControl): boolean {
    return this.disabled() || (control.key === 'fixedHits' && this.values()['randomHits'] === true)
      || (control.key === 'sameSeed' && this.values()['rerollSeeds'] === false)
      || (control.key === 'powerOfTwo' && this.values()['randomHits'] === false)
      || (control.key === 'shiftChance' && this.values()['patterns'] === false)
      || (['voices', 'voicesChance'].includes(control.key) && this.values()['voicesMode'] === 'NONE');
  }

  changeInstrument(key: keyof InstrumentGenerationPolicy, event: Event): void {
    if (this.disabled()) return;
    const element = event.target as HTMLInputElement;
    const value = key === 'pool' ? element.value : key === 'sustainChance'
      ? element.value.trim() === '' ? NaN : Number(element.value) : element.checked;
    this.instrumentPolicyChanged.emit({ [key]: value });
    element.value = String(this.instrumentPolicy()[key]);
    if (element.type === 'checkbox') element.checked = this.instrumentPolicy()[key] === true;
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
