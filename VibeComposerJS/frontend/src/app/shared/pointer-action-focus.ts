import { SPACE_ACTIONS } from './keyboard-shortcuts';

const ACTION = SPACE_ACTIONS + ', [role="combobox"]:not(input, textarea, select)';
const COMPOSITE = '[role="menu"], [role="menubar"], [role="listbox"], [role="tree"]';
const DIALOG = '[role="dialog"], [role="alertdialog"]';

/** Release completed pointer actions so global Space works without changing keyboard navigation. */
export function installPointerActionFocus(document: Document): () => void {
  const window = document.defaultView!;
  let pointer = false;
  let timer = 0;
  let frame = 0;
  let watchedTrigger: HTMLElement | null = null;
  const expanded = new MutationObserver(() => {
    if (document.activeElement === watchedTrigger) release();
    else stopWatching();
  });

  function stopWatching(): void {
    expanded.disconnect();
    watchedTrigger = null;
  }

  function release(previousDialog?: Element | null): void {
    stopWatching();
    const focused = document.activeElement;
    if (!(focused instanceof HTMLElement) || focused.isContentEditable
      || !focused.matches(ACTION) || focused.closest(COMPOSITE)) return;
    const dialog = focused.closest(DIALOG);
    if (previousDialog !== undefined && dialog && dialog !== previousDialog) return;
    if (focused.getAttribute('aria-expanded') === 'true') {
      watchedTrigger = focused;
      expanded.observe(focused, { attributes: true, attributeFilter: ['aria-expanded'] });
      return;
    }
    focused.blur();
  }

  function cancel(): void {
    window.clearTimeout(timer);
    window.cancelAnimationFrame(frame);
    stopWatching();
  }

  const onPointer = () => { pointer = true; };
  const onKey = () => { pointer = false; cancel(); };
  const afterAction = (event: MouseEvent) => {
    if (!pointer || (event.type === 'click' && event.detail === 0)) return;
    cancel();
    const focused = document.activeElement;
    // A menu opened from a dialog lives in a separate overlay.
    const previousDialog = focused?.closest(DIALOG)
      ?? (focused?.closest(COMPOSITE) ? Array.from(document.querySelectorAll(DIALOG)).at(-1) ?? null : null);
    // Capture stopped clicks too, then wait for widget handlers and their rendered ARIA state.
    timer = window.setTimeout(() => {
      frame = window.requestAnimationFrame(() => release(previousDialog));
    });
  };
  const actions = ['click', 'auxclick', 'contextmenu'] as const;
  document.addEventListener('pointerdown', onPointer, true);
  document.addEventListener('keydown', onKey, true);
  for (const action of actions) document.addEventListener(action, afterAction, true);
  return () => {
    cancel();
    document.removeEventListener('pointerdown', onPointer, true);
    document.removeEventListener('keydown', onKey, true);
    for (const action of actions) document.removeEventListener(action, afterAction, true);
  };
}
