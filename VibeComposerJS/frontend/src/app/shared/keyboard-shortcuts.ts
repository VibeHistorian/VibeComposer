/** Leave native editing shortcuts to form controls and inherited contenteditable regions. */
export function isEditingText(event: KeyboardEvent): boolean {
  return event.composedPath().some(target => target instanceof HTMLElement
    && (target.isContentEditable || target.matches('input, textarea, select')));
}
