export interface ModifierState {
  readonly ctrlKey: boolean;
  readonly metaKey: boolean;
  readonly shiftKey: boolean;
  readonly altKey: boolean;
  readonly active: boolean;
}

/** One observer per app; pointer events recover modifiers held while focus was elsewhere. */
export class ModifierObserver {
  private state: ModifierState = { ctrlKey: false, metaKey: false, shiftKey: false, altKey: false, active: true };
  private readonly listeners = new Set<(state: ModifierState) => void>();
  private readonly update = (event: Event): void => {
    const keys = event as KeyboardEvent | PointerEvent;
    this.publish({ ctrlKey: !!keys.ctrlKey, metaKey: !!keys.metaKey,
      shiftKey: !!keys.shiftKey, altKey: !!keys.altKey, active: true });
  };
  private readonly clear = (): void => {
    this.publish({ ctrlKey: false, metaKey: false, shiftKey: false, altKey: false, active: false });
  };
  private readonly visibility = (): void => { if (this.document.hidden) this.clear(); };

  constructor(private readonly document: Document) {
    for (const type of ['keydown', 'keyup', 'pointerdown', 'pointermove']) document.addEventListener(type, this.update, { capture: true, passive: true });
    document.addEventListener('visibilitychange', this.visibility);
    document.defaultView?.addEventListener('blur', this.clear);
  }

  subscribe(listener: (state: ModifierState) => void): () => void {
    this.listeners.add(listener);
    listener(this.state);
    return () => { this.listeners.delete(listener); };
  }

  private publish(next: ModifierState): void {
    const current = this.state;
    if (current.ctrlKey === next.ctrlKey && current.metaKey === next.metaKey
      && current.shiftKey === next.shiftKey && current.altKey === next.altKey && current.active === next.active) return;
    this.state = next;
    for (const listener of this.listeners) listener(next);
  }

  destroy(): void {
    for (const type of ['keydown', 'keyup', 'pointerdown', 'pointermove']) this.document.removeEventListener(type, this.update, { capture: true });
    this.document.removeEventListener('visibilitychange', this.visibility);
    this.document.defaultView?.removeEventListener('blur', this.clear);
    this.listeners.clear();
  }
}
