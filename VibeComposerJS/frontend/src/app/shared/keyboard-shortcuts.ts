/** Leave native editing shortcuts to form controls and inherited contenteditable regions.
 * Range controls (including knobs) use application history instead of native text history.
 */
export function isEditingText(event: KeyboardEvent): boolean {
  return event.composedPath().some(target => target instanceof HTMLElement
    && (target.isContentEditable || target.matches('input:not([type="range"]), textarea, select, [role="combobox"], [role="listbox"]')));
}
