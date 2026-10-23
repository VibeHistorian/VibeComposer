import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input } from '@angular/core';

export interface SelectOption {
  readonly value: string | number;
  readonly label: string;
  readonly disabled?: boolean;
}

/** A native select host preserves labels, focus, keyboard input and ordinary change events. */
@Component({
  selector: 'select[vcSelect]',
  templateUrl: './select.component.html',
  styleUrl: './select.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(wheel)': 'onWheel($event)', '(contextmenu)': 'resetDefault($event)' },
})
export class SelectComponent {
  readonly options = input<readonly (string | number | SelectOption)[] | undefined>([]);
  // Match native value coercion, including values read from heterogeneous settings records.
  readonly value = input<unknown>();
  readonly mixed = input(false);
  readonly choices = computed(() => (this.options() ?? []).map(option => typeof option === 'object'
    ? { ...option, value: String(option.value) } : { value: String(option), label: String(option), disabled: false }));
  readonly selectedValue = computed(() => this.value() === undefined ? undefined : String(this.value()));
  private readonly element = inject<ElementRef<HTMLSelectElement>>(ElementRef);
  readonly defaultValue = input<string | number | undefined>(undefined, { alias: 'selectDefault' });

  resetDefault(event: MouseEvent): void {
    event.preventDefault(); event.stopPropagation();
    const select = this.element.nativeElement;
    if (select.disabled) return;
    const options = Array.from(select.options);
    const index = options.findIndex(option => !option.disabled
      && !(option.parentElement instanceof HTMLOptGroupElement && option.parentElement.disabled)
      && (this.defaultValue() === undefined || option.value === String(this.defaultValue())));
    if (index < 0 || index === select.selectedIndex) return;
    select.selectedIndex = index;
    select.dispatchEvent(new Event('change', { bubbles: true }));
  }

  onWheel(event: WheelEvent): void {
    const select = this.element.nativeElement;
    if (select.disabled || event.ctrlKey || event.metaKey || event.deltaY === 0) return;
    const options = Array.from(select.options);
    const enabled = options.map((option, index) => ({ option, index })).filter(({ option }) =>
      !option.disabled && !(option.parentElement instanceof HTMLOptGroupElement && option.parentElement.disabled));
    if (!enabled.length) return;
    event.preventDefault();
    event.stopPropagation();
    const current = enabled.findIndex(({ index }) => index === select.selectedIndex);
    const next = current < 0 ? 0 : (current + (event.deltaY > 0 ? 1 : -1) + enabled.length) % enabled.length;
    if (select.selectedIndex === enabled[next].index) return;
    select.selectedIndex = enabled[next].index;
    select.dispatchEvent(new Event('change', { bubbles: true }));
  }
}

