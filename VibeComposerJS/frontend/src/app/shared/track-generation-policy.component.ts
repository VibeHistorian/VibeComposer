import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import type { ArrangedPart, InstrumentGenerationPolicy, TrackGenerationPolicy, TrackGenerationPolicyPatch } from '../core/project/project.model';
import { DEFAULT_INSTRUMENT_POLICIES, instrumentPoolOptions } from '../core/music/instrument-generation';
import { TRACK_POLICY_CONTROLS, DEFAULT_TRACK_POLICIES, automaticPolicyDefault, type PolicyControl } from '../core/music/track-generation';
import { CompactToggleComponent } from './compact-toggle.component';
import { WheelSelectDirective } from './wheel-select.directive';
import { CompactKnobComponent } from './compact-knob.component';

@Component({
  selector: 'vc-track-generation-policy',
  imports: [CompactToggleComponent, CompactKnobComponent, WheelSelectDirective],
  host: { '[attr.data-control-role]': 'role()' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './track-generation-policy.component.html',
  styleUrl: './track-generation-policy.component.css',
})
export class TrackGenerationPolicyComponent {
  readonly role = input<ArrangedPart>('arpeggio');
  readonly policy = input.required<TrackGenerationPolicy>();
  readonly values = computed(() => ({ onCompose: false, onRegenerate: false,
    targetsOnCompose: automaticPolicyDefault('targetsOnCompose'), patternsOnCompose: automaticPolicyDefault('patternsOnCompose'),
    ...DEFAULT_TRACK_POLICIES[this.role()], ...this.policy() }) as unknown as Record<string, string | number | boolean>);
  readonly expanded = input(false);
  readonly disabled = input(false);
  readonly policyChanged = output<TrackGenerationPolicyPatch>();
  readonly instrumentPolicy = input<InstrumentGenerationPolicy>(DEFAULT_INSTRUMENT_POLICIES.arpeggio);
  readonly instrumentPolicyChanged = output<Partial<InstrumentGenerationPolicy>>();
  readonly instrumentPools = computed(() => instrumentPoolOptions(this.role()));
  readonly poolLabels = { AUTO: 'Chord / Pluck', ALL: 'All', MELODY: 'Melody', BASS: 'Bass', CHORD: 'Chord',
    PLUCK: 'Pluck', LONG_PAD: 'Long pad', DRUM: 'Percussion' };
  readonly controls = computed(() => TRACK_POLICY_CONTROLS[this.role()]);

  readonly defaultInstrumentPolicies = DEFAULT_INSTRUMENT_POLICIES;
  defaultValue(control: PolicyControl): string | number { return (DEFAULT_TRACK_POLICIES[this.role()] as unknown as Record<string, string | number>)[control.key]; }

  numericValue(control: PolicyControl): number { return Number(this.values()[control.key]); }

  code(control: PolicyControl): string {
    const codes: Readonly<Record<string, string>> = {
      fixedHits: 'HITS', maxSplit: 'TR.CH', lengthMin: 'LEN↓', lengthMax: 'LEN↑',
      maxRepeat: 'REPT', shiftChance: 'SHIFT', voices: 'VOICE', voicesChance: 'V.CH',
      velocityMin: 'VEL↓', velocityMax: 'VEL↑', struminess: 'STRUM',
      maxStrumPause: 'S.PAU', expandChance: 'EXP', maxSwing: 'SWING',
    };
    return codes[control.key] ?? control.label.toUpperCase().slice(0, 5);
  }

  changeNumber(control: PolicyControl, value: number): void {
    if (!this.inactive(control)) this.policyChanged.emit({ [control.key]: value });
  }

  changeSustain(value: number): void {
    if (!this.disabled()) this.instrumentPolicyChanged.emit({ sustainChance: value });
  }

  toggle(control: PolicyControl): void {
    if (!this.inactive(control)) this.policyChanged.emit({ [control.key]: !this.values()[control.key] });
  }

  toggleInstrument(key: 'randomize' | 'sameInstrument'): void {
    if (!this.disabled()) this.instrumentPolicyChanged.emit({ [key]: !this.instrumentPolicy()[key] });
  }

  inactive(control: PolicyControl): boolean {
    return this.disabled() || (control.key === 'fixedHits' && this.values()['randomHits'] === true)
      || (['struminess', 'maxStrumPause'].includes(control.key) && this.values()['strum'] === false)
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
