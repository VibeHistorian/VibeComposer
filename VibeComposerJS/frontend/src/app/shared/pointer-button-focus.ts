/** Pointer-operated actions should not own the next Space press. Keyboard navigation
 * retains focus, while composite widgets keep their own focus and key handling. */
export function installPointerButtonFocus(document: Document): () => void {
  let pointer = false;
  const action = (target: EventTarget | null): target is HTMLElement => target instanceof HTMLElement
    && target.matches('button, input[type="button"], input[type="submit"], input[type="reset"], input[type="checkbox"], input[type="radio"], [role="button"], [role="checkbox"], [role="radio"], [role="switch"], [role="tab"]')
    && !target.closest('[role="menu"], [role="menubar"], [role="listbox"], [role="combobox"], [role="tree"], [aria-haspopup]');
  const release = () => {
    const focused = document.activeElement;
    if (pointer && action(focused)) focused.blur();
  };
  const onPointer = () => { pointer = true; };
  const onKey = () => { pointer = false; };
  document.addEventListener('pointerdown', onPointer, true);
  document.addEventListener('keydown', onKey, true);
  document.addEventListener('focusin', release, true);
  // Also release actions which were already focused when clicked.
  document.addEventListener('click', release);
  return () => {
    document.removeEventListener('pointerdown', onPointer, true);
    document.removeEventListener('keydown', onKey, true);
    document.removeEventListener('focusin', release, true);
    document.removeEventListener('click', release);
  };
}
