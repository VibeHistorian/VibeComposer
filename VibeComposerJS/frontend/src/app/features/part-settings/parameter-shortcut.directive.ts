import { Directive, ElementRef, OnDestroy, inject, input, output } from '@angular/core';
import { UiPreferencesService } from '../../core/ui/ui-preferences.service';

/** Capture before knobs, dropdowns and text fields handle their own mouse actions. */
@Directive({ selector: '[vcParameterShortcut]' })
export class ParameterShortcutDirective implements OnDestroy {
  readonly enabled = input(true, { alias: 'vcParameterShortcut' });
  readonly section = input(false);
  readonly parameterShortcut = output<{ key: string; combined: boolean }>();
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly preferences = inject(UiPreferencesService);
  private consumed: { key: string; button: number; seen: Set<string> } | null = null;
  private readonly events = ['pointerdown', 'mousedown', 'contextmenu', 'click', 'auxclick'] as const;
  private readonly handle = (event: Event): void => {
    const mouse = event as MouseEvent;
    if (event.type === 'pointerdown') this.consumed = null;
    if (!this.enabled() || !(event.target instanceof Element) || event.target.closest('[disabled]')) return;
    const target = event.target.closest<HTMLElement>('[data-parameter-key]');
    if (!target || !this.element.contains(target)) return;
    const key = target.dataset['parameterKey']!;
    const consumed = this.consumed?.key === key && this.consumed.button === mouse.button && !this.consumed.seen.has(event.type);
    if (!consumed && !this.preferences.matchesShortcut(this.section() ? 'controlUnfreeze' : 'controlFreeze', mouse)) return;
    event.preventDefault(); event.stopImmediatePropagation();
    if (consumed) { this.consumed!.seen.add(event.type); return; }
    if (event.type === 'mousedown') return;
    this.consumed = { key, button: mouse.button, seen: new Set([event.type]) };
    this.parameterShortcut.emit({ key, combined: target.dataset['parameterCombined'] === 'true' });
  };
  constructor() {
    for (const type of this.events) this.element.addEventListener(type, this.handle, true);
  }
  ngOnDestroy(): void {
    for (const type of this.events) this.element.removeEventListener(type, this.handle, true);
  }
}
