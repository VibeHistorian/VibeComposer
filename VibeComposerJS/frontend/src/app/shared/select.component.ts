import { UiPreferencesService } from '../core/ui/ui-preferences.service';
import { afterRenderEffect, ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, input, linkedSignal, output, signal, viewChild } from '@angular/core';
import { Combobox, ComboboxPopup, ComboboxWidget } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { CdkConnectedOverlay, type ConnectedPosition } from '@angular/cdk/overlay';

export interface SelectOption {
  readonly value: string | number;
  readonly label: string;
  readonly disabled?: boolean;
}

/** Select-only ARIA combobox with a DOM listbox rather than a native select popup. */
@Component({
  selector: 'vc-select',
  exportAs: 'vcSelect',
  imports: [ComboboxPopup, ComboboxWidget, Listbox, Option, CdkConnectedOverlay],
  hostDirectives: [{ directive: Combobox, inputs: ['disabled'] }],
  templateUrl: './select.component.html',
  styleUrl: './select.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.value]': 'selectedValue()',
    '(window:resize)': 'measurePopup()',
    '(wheel)': 'onWheel($event)', '(contextmenu)': '$event.preventDefault(); resetDefault($event)', '(pointerdown)': 'resetPointer($event)',
  },
})
export class SelectComponent {
  private readonly preferences = inject(UiPreferencesService);
  readonly options = input<readonly (string | number | SelectOption)[] | undefined>([]);
  // Match native value coercion, including values read from heterogeneous settings records.
  readonly value = input<unknown>();
  readonly mixed = input(false);
  readonly displayLabel = input<string>();
  readonly choices = computed(() => (this.options() ?? []).map(option => typeof option === 'object'
    ? { ...option, value: String(option.value) } : { value: String(option), label: String(option), disabled: false }));
  readonly valueChange = output<string>();
  private readonly boundValue = computed(() => this.mixed() ? '' : this.value() === undefined
    ? this.choices().find(option => !option.disabled)?.value ?? '' : String(this.value()));
  readonly selectedValue = linkedSignal(() => this.boundValue());
  readonly selectedLabel = computed(() => this.mixed() && this.selectedValue() === '' ? 'Mixed'
    : this.choices().find(option => option.value === this.selectedValue())?.label ?? '');
  readonly draft = linkedSignal({ source: () => this.selectedValue(), computation: value => [value] });
  readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  readonly combobox = inject(Combobox);
  readonly panelMinWidth = signal(0);
  readonly panelMaxHeight = signal(320);
  private readonly above = signal(false);
  private readonly overlay = viewChild(CdkConnectedOverlay);
  private readonly listbox = viewChild<Listbox<string>>('listbox');
  readonly positions = computed<ConnectedPosition[]>(() => {
    const below: ConnectedPosition[] = ['start', 'end'].map(align => ({ originX: align as 'start' | 'end', originY: 'bottom', overlayX: align as 'start' | 'end', overlayY: 'top', offsetY: 3 }));
    const above: ConnectedPosition[] = ['start', 'end'].map(align => ({ originX: align as 'start' | 'end', originY: 'top', overlayX: align as 'start' | 'end', overlayY: 'bottom', offsetY: -3 }));
    return this.above() ? [...above, ...below] : [...below, ...above];
  });
  readonly defaultValue = input<string | number | undefined>(undefined, { alias: 'selectDefault' });

  constructor() {
    effect(() => {
      if (this.combobox.disabled()) this.close();
      else if (this.combobox.expanded()) this.measurePopup();
    });
    afterRenderEffect(() => {
      const listbox = this.listbox();
      if (!this.combobox.expanded() || !listbox) return;
      this.panelMaxHeight();
      // ARIA defers the listbox until after the overlay attaches; position with its real size.
      this.overlay()?.overlayRef.updatePosition();
      listbox.scrollActiveItemIntoView({ block: 'nearest' });
    });
  }

  measurePopup(): void {
    if (!this.combobox.expanded()) return;
    const trigger = this.element.nativeElement.getBoundingClientRect();
    const viewport = window.visualViewport;
    const top = viewport?.offsetTop ?? 0;
    const bottom = top + (viewport?.height ?? window.innerHeight);
    const roomAbove = Math.max(0, trigger.top - top - 11);
    const roomBelow = Math.max(0, bottom - trigger.bottom - 11);
    const height = Math.min(320, (this.choices().length + (this.mixed() ? 1 : 0)) * 27 + 8);
    const above = roomBelow < height && roomAbove > roomBelow;
    this.above.set(above);
    this.panelMinWidth.set(trigger.width);
    this.panelMaxHeight.set(Math.min(320, above ? roomAbove : roomBelow));
  }

  close(): void { this.combobox.expanded.set(false); }

  outsideClick(event: MouseEvent): void {
    if (!event.composedPath().includes(this.element.nativeElement)) this.close();
  }

  commit(event: Event): void {
    const activeId = this.listbox()?.activeDescendant();
    const option = event.type === 'click' && event.target instanceof Element
      ? event.target.closest('[role="option"]')
      : activeId ? this.element.nativeElement.ownerDocument.getElementById(activeId) : null;
    if (option?.getAttribute('aria-disabled') === 'true' || (event.type === 'click' && !option)) return;
    // Commit the activated option directly: listbox model outputs and template click
    // listeners need not run in the same order, particularly when the value is mixed.
    const value = option?.getAttribute('data-value') ?? this.draft()[0];
    if (value !== undefined) this.choose(value);
    this.close();
  }

  /** Restore the bound value after an action or a choice that opens a draft dialog. */
  restoreValue(): void {
    this.selectedValue.set(this.boundValue());
  }

  private choose(value: string): void {
    if (this.combobox.disabled() || value === this.selectedValue()) return;
    this.selectedValue.set(value);
    this.valueChange.emit(value);
  }

  resetPointer(event: MouseEvent): void {
    if (event.button !== 2) this.resetDefault(event);
  }

  resetDefault(event: MouseEvent): void {
    if (!this.preferences.matchesShortcut('controlReset', event)) return;
    event.preventDefault(); event.stopPropagation();
    if (this.combobox.disabled()) return;
    const option = this.choices().find(option => !option.disabled
      && (this.defaultValue() === undefined || option.value === String(this.defaultValue())));
    if (option) this.choose(option.value);
  }

  onWheel(event: WheelEvent): void {
    if (this.combobox.disabled() || !this.preferences.matchesShortcut('controlWheel', event) || event.deltaY === 0) return;
    const enabled = this.choices().filter(option => !option.disabled);
    if (!enabled.length) return;
    event.preventDefault();
    event.stopPropagation();
    const current = enabled.findIndex(option => option.value === this.selectedValue());
    const next = current < 0 ? 0 : (current + (event.deltaY > 0 ? 1 : -1) + enabled.length) % enabled.length;
    this.choose(enabled[next].value);
  }
}

