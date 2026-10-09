import { Directive, ElementRef, inject } from '@angular/core';

/** Native selects share the same wheel behavior and existing change handlers. */
@Directive({ selector: 'select', host: { '(wheel)': 'onWheel($event)' } })
export class WheelSelectDirective {
  private readonly element = inject<ElementRef<HTMLSelectElement>>(ElementRef);

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
