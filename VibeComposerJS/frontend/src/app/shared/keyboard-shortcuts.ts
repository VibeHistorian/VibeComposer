/** Leave native editing shortcuts to form controls and inherited contenteditable regions.
 * Range controls (including knobs) use application history instead of native text history.
 */
export function isEditingText(event: KeyboardEvent): boolean {
  return event.composedPath().some(target => target instanceof HTMLElement
    && (target.isContentEditable || target.matches('input:not([type="range"]), textarea, select, [role="combobox"], [role="listbox"]')));
}

/** Preserve Space activation on controls, including controls inside shadow roots. */
export function usesSpaceKey(event: KeyboardEvent): boolean {
  return isEditingText(event) || event.composedPath().some(target => target instanceof HTMLElement
    && target.matches('button, a[href], summary, [role="button"], [role="checkbox"], [role="radio"], [role="switch"], [role="menuitem"], [role="tab"], [role="treeitem"]'));
}
