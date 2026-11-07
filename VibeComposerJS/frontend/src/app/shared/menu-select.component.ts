import { ChangeDetectionStrategy, Component, TemplateRef, computed, effect, inject, input, linkedSignal, output, viewChild } from '@angular/core';
import { CdkMenuModule, CdkMenuTrigger } from '@angular/cdk/menu';
import { UiPreferencesService } from '../core/ui/ui-preferences.service';
import type { SelectOption } from './select.component';

export interface SelectMenuAction {
  readonly id: string;
  readonly label: string;
  readonly disabled?: boolean;
}

export interface SelectMenuGroup {
  readonly id: string;
  readonly label: string;
  readonly values: readonly string[];
}

/** A value selector with menu semantics and feature-owned families. */
@Component({
  selector: 'vc-menu-select', exportAs: 'vcMenuSelect',
  imports: [CdkMenuModule], hostDirectives: [CdkMenuTrigger],
  templateUrl: './menu-select.component.html',
  styleUrls: ['./select.component.css', './menu-select.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'button', '[attr.tabindex]': 'disabled() ? -1 : 0',
    '[attr.aria-disabled]': 'disabled()', '[attr.value]': 'selectedValue()',
    '(wheel)': 'onWheel($event)', '(contextmenu)': '$event.preventDefault(); resetDefault($event)', '(pointerdown)': 'resetPointer($event)',
  },
})
export class MenuSelectComponent {
  private readonly preferences = inject(UiPreferencesService);
  private readonly trigger = inject(CdkMenuTrigger);
  readonly menu = viewChild.required<TemplateRef<unknown>>('root');
  readonly options = input<readonly (string | number | SelectOption)[] | undefined>([]);
  readonly groups = input.required<readonly SelectMenuGroup[]>();
  readonly actions = input<readonly SelectMenuAction[]>([]);
  readonly action = output<string>();
  readonly value = input<unknown>();
  readonly disabled = input(false);
  readonly mixed = input(false);
  readonly displayLabel = input<string>();
  readonly defaultValue = input<string | number | undefined>(undefined, { alias: 'selectDefault' });
  readonly valueChange = output<string>();
  readonly choices = computed(() => (this.options() ?? []).map(option => typeof option === 'object'
    ? { ...option, value: String(option.value) } : { value: String(option), label: String(option), disabled: false }));
  readonly boundValue = computed(() => this.mixed() ? '' : this.value() === undefined
    ? this.choices().find(option => !option.disabled)?.value ?? '' : String(this.value()));
  readonly selectedValue = linkedSignal(() => this.boundValue());
  readonly selectedLabel = computed(() => this.mixed() && this.selectedValue() === '' ? 'Mixed'
    : this.choices().find(option => option.value === this.selectedValue())?.label ?? '');
  readonly branches = computed(() => this.groups().map(group => ({ ...group,
    options: this.choices().filter(option => group.values.includes(option.value)),
  })).filter(group => group.options.length));
  readonly ungrouped = computed(() => this.choices().filter(option => !this.groups().some(group => group.values.includes(option.value))));
  constructor() {
    effect(() => {
      this.trigger.menuTemplateRef = this.disabled() ? null : this.menu();
      if (this.disabled()) this.trigger.close();
    });
  }
  runAction(id: string): void {
    if (!this.disabled() && this.actions().some(action => action.id === id && !action.disabled)) this.action.emit(id);
  }
  commit(value: string): void {
    if (this.choices().some(option => option.value === value && !option.disabled)) this.choose(value);
  }
  /** Restore the bound value after an action or a choice that opens a draft dialog. */
  restoreValue(): void {
    this.selectedValue.set(this.boundValue());
  }

  private choose(value: string): void {
    if (this.disabled() || value === this.selectedValue()) return;
    this.selectedValue.set(value);
    this.valueChange.emit(value);
  }

  resetPointer(event: MouseEvent): void {
    if (event.button !== 2) this.resetDefault(event);
  }

  resetDefault(event: MouseEvent): void {
    if (!this.preferences.matchesShortcut('controlReset', event)) return;
    event.preventDefault(); event.stopPropagation();
    if (this.disabled()) return;
    const option = this.choices().find(option => !option.disabled
      && (this.defaultValue() === undefined || option.value === String(this.defaultValue())));
    if (option) this.choose(option.value);
  }

  onWheel(event: WheelEvent): void {
    if (this.disabled() || !this.preferences.matchesShortcut('controlWheel', event) || event.deltaY === 0) return;
    const enabled = this.choices().filter(option => !option.disabled);
    if (!enabled.length) return;
    event.preventDefault();
    event.stopPropagation();
    const current = enabled.findIndex(option => option.value === this.selectedValue());
    const next = current < 0 ? 0 : (current + (event.deltaY > 0 ? 1 : -1) + enabled.length) % enabled.length;
    this.choose(enabled[next].value);
  }
}
