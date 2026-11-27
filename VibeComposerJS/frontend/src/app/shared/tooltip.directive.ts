import { booleanAttribute, ChangeDetectionStrategy, Component, DestroyRef, Directive, DOCUMENT, ElementRef, Injectable, computed, effect, inject, input, signal, type ComponentRef, type Signal } from '@angular/core';
import { Overlay, type ConnectedPosition, type FlexibleConnectedPositionStrategy, type OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';

const TOOLTIP_DELAY_MS = 650;
const TOOLTIP_LEAVE_MS = 120;
const POPUP_TRIGGER = ':is([aria-haspopup]:not([aria-haspopup="false"]), [role="combobox"])';
const OPEN_POPUP = `${POPUP_TRIGGER}[aria-expanded="true"]`;

export const TOOLTIP_POSITIONS: ConnectedPosition[] = [
  { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -4 },
  { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: 4 },
];

interface TooltipTarget {
  readonly element: HTMLElement;
  readonly text: Signal<string | null | undefined>;
  readonly instant?: Signal<boolean>;
  readonly preferOnFocus?: boolean;
}

@Component({
  selector: 'vc-tooltip-panel',
  template: '<div class="vc-tooltip" [class.vc-tooltip-detail]="!instant()" role="tooltip" tabindex="-1">{{ text() }}</div>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class TooltipPanelComponent {
  readonly text = input('');
  readonly instant = input(false);
}

@Injectable({ providedIn: 'root' })
class TooltipOverlay {
  private readonly overlay = inject(Overlay);
  private readonly targets = new WeakMap<Element, TooltipTarget>();
  private readonly active = signal<TooltipTarget | null>(null);
  private readonly candidate = computed(() => {
    const target = this.active();
    return target?.text() ? target : null;
  });
  private readonly ready = signal<TooltipTarget | null>(null);
  private hovered: TooltipTarget | null = null;
  private focused: TooltipTarget | null = null;
  private shown: TooltipTarget | null = null;
  private hoverSuppressed = false;
  private leaveTimer?: ReturnType<typeof setTimeout>;
  private position?: FlexibleConnectedPositionStrategy;
  private overlayRef?: OverlayRef;
  private panel?: ComponentRef<TooltipPanelComponent>;

  constructor() {
    const document = inject(DOCUMENT);
    const controller = new AbortController();
    const options = { capture: true, signal: controller.signal };
    document.addEventListener('pointerover', event => this.hover(event, event.target), options);
    document.addEventListener('pointerout', event => this.hover(event, event.relatedTarget), options);
    document.addEventListener('pointermove', event => {
      if (!this.hoverSuppressed || event.pointerType === 'touch') return;
      this.hoverSuppressed = false;
      this.hover(event, event.target);
    }, { ...options, passive: true });
    document.addEventListener('focusin', event => this.focus(event.target), options);
    document.addEventListener('focusout', event => this.focus(event.relatedTarget), options);
    document.addEventListener('pointerdown', event => this.press(event.target), options);
    document.addEventListener('click', event => this.press(event.target), options);
    document.addEventListener('contextmenu', event => this.press(event.target), options);
    document.addEventListener('scroll', event => {
      if (!this.insidePanel(event.target)) this.dismiss();
    }, options);
    document.addEventListener('keydown', event => {
      const activates = (event.key === 'Enter' || event.key === ' ') && event.target instanceof Element
        && event.target.matches('button, [role="button"], [role^="menuitem"], [role="combobox"], input[type="checkbox"]');
      const opens = (event.key === 'ArrowDown' || event.key === 'ArrowUp') && event.target instanceof Element
        && event.target.matches(POPUP_TRIGGER);
      if (event.key === 'Escape' || activates || opens) this.dismiss();
    }, options);
    document.defaultView?.addEventListener('blur', () => this.dismiss(), { signal: controller.signal });
    inject(DestroyRef).onDestroy(() => {
      controller.abort();
      clearTimeout(this.leaveTimer);
      this.overlayRef?.dispose();
    });

    // Keep the delay tied to the trigger, so live text updates never restart it.
    effect(onCleanup => {
      const target = this.candidate();
      this.ready.set(null);
      if (!target) return;
      if (target.instant?.()) {
        this.ready.set(target);
      } else {
        const timer = setTimeout(() => this.ready.set(target), TOOLTIP_DELAY_MS);
        onCleanup(() => clearTimeout(timer));
      }
    });
    effect(() => {
      const target = this.ready();
      if (target && target === this.candidate() && target.element.isConnected && !target.element.closest(OPEN_POPUP)) {
        this.show(target, target.text()!, target.instant?.() ?? false);
      } else {
        this.hide();
      }
    });
  }

  add(target: TooltipTarget): void { this.targets.set(target.element, target); }

  remove(target: TooltipTarget): void {
    this.targets.delete(target.element);
    if (this.hovered === target) this.hovered = null;
    if (this.focused === target) this.focused = null;
    if (this.active() === target) this.active.set(this.hovered ?? this.focused);
    if (this.shown === target) this.hide();
  }

  private find(target: EventTarget | null, keyboard = false): TooltipTarget | null {
    if (!(target instanceof Element) || target.closest(OPEN_POPUP)) return null;
    let closest: TooltipTarget | null = null;
    for (let node: Element | null = target; node; node = node.parentElement) {
      const tooltip = this.targets.get(node);
      if (!tooltip) continue;
      if (!keyboard || (tooltip.preferOnFocus && tooltip.text())) return tooltip;
      closest ??= tooltip;
    }
    return closest;
  }

  private insidePanel(target: EventTarget | null): boolean {
    return target instanceof Node && this.overlayRef?.overlayElement.contains(target) === true;
  }

  private hover(event: PointerEvent, target: EventTarget | null): void {
    if (this.hoverSuppressed || event.pointerType === 'touch') return;
    const next = this.insidePanel(target) ? this.shown : this.find(target);
    if (next === this.hovered) return;
    const previous = this.hovered;
    this.hovered = next;
    clearTimeout(this.leaveTimer);
    if (next) {
      this.active.set(next);
    } else if (previous === this.active()) {
      if (previous && previous === this.shown && !previous.instant?.() && this.focused !== previous) {
        // Allow crossing the gap from the trigger to a longer, readable tooltip.
        this.leaveTimer = setTimeout(() => this.active.set(this.focused), TOOLTIP_LEAVE_MS);
      } else {
        this.active.set(this.focused);
      }
    }
  }

  private focus(target: EventTarget | null): void {
    const next = target instanceof Element && target.matches(':focus-visible') ? this.find(target, true) : null;
    if (next === this.focused) return;
    const previous = this.focused;
    this.focused = next;
    clearTimeout(this.leaveTimer);
    if (next || this.active() === previous) this.active.set(next ?? this.hovered);
  }

  private press(target: EventTarget | null): void {
    // Sliders keep their value readout while a pointer gesture updates it.
    if (target instanceof Element && target.matches('input[type="range"]') && this.find(target) === this.active()) return;
    if (!this.insidePanel(target)) this.dismiss();
  }

  private dismiss(): void {
    clearTimeout(this.leaveTimer);
    // Removing a hovered panel can retarget the stationary pointer to another control.
    if (this.overlayRef?.overlayElement.matches(':hover')) this.hoverSuppressed = true;
    this.focused = null;
    this.active.set(null);
    this.hide();
  }

  private show(target: TooltipTarget, text: string, instant: boolean): void {
    if (!this.overlayRef) {
      this.position = this.overlay.position().flexibleConnectedTo(target.element)
        .withPositions(TOOLTIP_POSITIONS).withFlexibleDimensions(false).withPush(true).withViewportMargin(8);
      this.overlayRef = this.overlay.create({ positionStrategy: this.position });
    }
    this.position?.setOrigin(target.element);
    if (!this.panel) this.panel = this.overlayRef.attach(new ComponentPortal(TooltipPanelComponent));
    this.shown = target;
    this.overlayRef.overlayElement.classList.toggle('vc-tooltip-passive-pane', instant);
    this.panel.setInput('text', text);
    this.panel.setInput('instant', instant);
    this.panel.changeDetectorRef.detectChanges();
    this.overlayRef.updatePosition();
  }

  private hide(): void {
    this.shown = null;
    this.panel = undefined;
    this.overlayRef?.detach();
  }
}

/** Register projected controls and copy together when the row owns one explainer. */
export function registerTooltip(text: TooltipTarget['text'], options: Pick<TooltipTarget, 'instant' | 'preferOnFocus'> = {}): void {
  const element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  const overlay = inject(TooltipOverlay);
  const target = { element, text, ...options };
  overlay.add(target);
  inject(DestroyRef).onDestroy(() => overlay.remove(target));
}

@Directive({
  selector: '[vcTooltip]',
  host: { '[attr.aria-description]': 'text() || null' },
})
export class TooltipDirective {
  readonly text = input<string | null>(null, { alias: 'vcTooltip' });
  readonly instant = input(false, { alias: 'vcTooltipInstant', transform: booleanAttribute });

  constructor() { registerTooltip(this.text, { instant: this.instant }); }
}
