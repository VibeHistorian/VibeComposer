/** UI-only bindings; mouse inputs also describe the start of a drag. */
export interface Shortcut {
  readonly kind: 'key' | 'mouse' | 'wheel' | 'modifier';
  readonly input: string;
  readonly ctrl: boolean;
  readonly shift: boolean;
  readonly alt: boolean;
}
const binding = (kind: Shortcut['kind'], input: string, ctrl = false, shift = false, alt = false): Shortcut => ({ kind, input, ctrl, shift, alt });
const key = (input: string, ctrl = false, shift = false) => binding('key', input, ctrl, shift);
const mouse = (button: number, ctrl = false, shift = false, alt = false) => binding('mouse', String(button), ctrl, shift, alt);
const wheel = (ctrl = false, shift = false, alt = false) => binding('wheel', 'scroll', ctrl, shift, alt);
const modifier = (ctrl = false, shift = false, alt = false) => binding('modifier', 'held', ctrl, shift, alt);
export const SHORTCUTS = [
  { id: 'extendSections', group: 'Section selection', label: 'Extend / trim sections', description: 'Hold while selecting a section. Extends a contiguous selection or trims it at the chosen section.', defaults: [modifier(true)] },
  { id: 'rangeSections', group: 'Section selection', label: 'Select section range', description: 'Hold while selecting a section to select from the selection anchor.', defaults: [modifier(false, true)] },
  { id: 'freeGrid', group: 'Editing modifiers', label: 'Bypass time grid', description: 'Hold while moving, adding or splitting notes. Move drags also retain pitch.', defaults: [modifier(false, true)] },
  { id: 'addNote', group: 'Phrase gestures', label: 'Add / move note', description: 'Click empty space to add a note; drag a note to move it or its edges to trim.', defaults: [mouse(0)] },
  { id: 'controlWheel', group: 'Controls', label: 'Adjust value', description: 'Scroll over a knob or dropdown to change its value.', defaults: [wheel()] },
  { id: 'controlFineWheel', group: 'Controls', label: 'Fine adjustment', description: 'Scroll over a knob to change its value in smaller steps.', defaults: [wheel(false, true)] },
  { id: 'controlReset', group: 'Controls', label: 'Reset value', description: 'On a knob or dropdown, restore the default value.', defaults: [mouse(2)] },
  { id: 'fineDrag', group: 'Control modifiers', label: 'Fine knob dragging', description: 'Hold while dragging a knob for finer changes.', defaults: [modifier(false, true)] },
  { id: 'playback', group: 'Global', label: 'Play / pause', description: 'Toggle playback, including a phrase draft.', defaults: [key(' ')] },
  { id: 'undo', group: 'Global', label: 'Undo', description: 'Undo in the project or active phrase draft.', defaults: [key('z', true)] },
  { id: 'redo', group: 'Global', label: 'Redo', description: 'Redo in the project or active phrase draft.', defaults: [key('y', true), key('z', true, true)] },
  { id: 'overviewZoom', group: 'Arrangement', label: 'Zoom timeline', description: 'Scroll over the overview to zoom at the pointer.', defaults: [wheel(true)] },
  { id: 'presence', group: 'Arrangement', label: 'Toggle presence', description: 'On a track: toggle inclusion. On a role: include all or clear all tracks.', defaults: [mouse(1), key('i')] },
  { id: 'reroll', group: 'Arrangement', label: 'Reroll settings', description: 'Reroll the track, role cell or section under the pointer or keyboard focus.', defaults: [mouse(1, false, true)] },
  { id: 'reset', group: 'Arrangement', label: 'Reset settings and notes', description: 'Reset targeted settings and restore generated notes.', defaults: [mouse(2, false, true)] },
  { id: 'duplicateSection', group: 'Section headers', label: 'Duplicate section', description: 'On a section header, insert a copy.', defaults: [mouse(1)] },
  { id: 'removeSection', group: 'Section headers', label: 'Remove section', description: 'On a section header, remove that section.', defaults: [mouse(2)] },
  { id: 'clearSections', group: 'Arrangement', label: 'Clear section selection', description: 'While the overview has focus.', defaults: [key('escape')] },
  { id: 'zoomTime', group: 'Score', label: 'Zoom time', description: 'Scroll over the score to zoom horizontally at the pointer.', defaults: [wheel(true)] },
  { id: 'zoomPitch', group: 'Score', label: 'Zoom pitch', description: 'Scroll over pitched lanes to zoom vertically.', defaults: [wheel(false, false, true)] },
  { id: 'panTime', group: 'Score', label: 'Scroll horizontally', description: 'Scroll over the score to move along the timeline.', defaults: [wheel(false, true)] },
  { id: 'panPitch', group: 'Score', label: 'Scroll vertically', description: 'Scroll over the score to move through pitch lanes.', defaults: [wheel()] },
  { id: 'selectAllNotes', group: 'Phrase editor', label: 'Select all notes', description: 'Select all notes in the current draft.', defaults: [key('a', true)] },
  { id: 'transposeNotes', group: 'Phrase editor', label: 'Open transpose', description: 'Open transpose controls for selected notes.', defaults: [key('t', false, true)] },
  { id: 'deleteNotes', group: 'Phrase editor', label: 'Delete selected notes', description: 'Remove the current note selection.', defaults: [key('delete'), key('backspace')] },
  { id: 'seek', group: 'Phrase gestures', label: 'Set playback position', description: 'Click empty grid space to seek.', defaults: [mouse(0, false, true)] },
  { id: 'boxSelect', group: 'Phrase gestures', label: 'Box select / delete note', description: 'Drag a selection box; click a note to delete it.', defaults: [mouse(2)] },
  { id: 'boxExtend', group: 'Phrase gestures', label: 'Extend box selection', description: 'Add a box selection to the current selection.', defaults: [mouse(2, true)] },
  { id: 'splitNote', group: 'Phrase gestures', label: 'Split note', description: 'Click a note to split it at the pointer.', defaults: [mouse(2, false, false, true)] },
  { id: 'shapePitch', group: 'Phrase gestures', label: 'Duplicate / shape pitch', description: 'Drag selected notes to duplicate; elsewhere draw note pitches.', defaults: [mouse(0, true)] },
  { id: 'shapeVelocity', group: 'Phrase gestures', label: 'Shape velocities', description: 'Draw velocities across notes.', defaults: [mouse(1, true)] },
  { id: 'dragVelocity', group: 'Phrase gestures', label: 'Drag velocity / apply phrase', description: 'Drag notes to change velocity. Click empty space to exit and apply edits, or cancel an unchanged draft.', defaults: [mouse(1)] },
  { id: 'dragDuration', group: 'Phrase gestures', label: 'Drag note length', description: 'Drag notes to change their length.', defaults: [mouse(1, false, false, true)] },
  { id: 'dragPitch', group: 'Phrase gestures', label: 'Drag pitch', description: 'Drag notes to change pitch.', defaults: [mouse(1, false, true)] },
  { id: 'trimStart', group: 'Phrase gestures', label: 'Trim note start', description: 'Drag anywhere on a note to trim its start.', defaults: [mouse(0, false, false, true)] },
] as const;
/** Visible settings pages. Global and section-selection bindings remain available internally. */
export const SHORTCUT_PAGES = [
  { id: 'shortcuts-general', label: 'General', groups: ['Controls', 'Control modifiers'] },
  { id: 'shortcuts-arrangement', label: 'Arrangement', groups: ['Arrangement', 'Section headers'] },
  { id: 'shortcuts-score', label: 'Score', groups: ['Score'] },
  { id: 'shortcuts-phrase-editor', label: 'Phrase editor', groups: ['Phrase editor', 'Phrase gestures', 'Editing modifiers'] },
] as const;
export type ShortcutId = typeof SHORTCUTS[number]['id'];
export type ShortcutBindings = Readonly<Record<ShortcutId, readonly Shortcut[]>>;
export function defaultShortcuts(): ShortcutBindings {
  return Object.fromEntries(SHORTCUTS.map(action => [action.id, action.defaults.map(value => ({ ...value }))])) as unknown as ShortcutBindings;
}
export function shortcutFromEvent(event: KeyboardEvent | MouseEvent | WheelEvent): Shortcut | null {
  const modifiers = { ctrl: !!(event.ctrlKey || event.metaKey), shift: !!event.shiftKey, alt: !!event.altKey };
  if ('key' in event) {
    if (['Control', 'Shift', 'Alt', 'Meta', 'AltGraph'].includes(event.key) || event.isComposing) return null;
    return { kind: 'key', input: event.key.toLowerCase(), ...modifiers };
  }
  if ('deltaY' in event) return event.deltaY || event.deltaX ? { kind: 'wheel', input: 'scroll', ...modifiers } : null;
  return { kind: 'mouse', input: String(event.button), ...modifiers };
}
export function sameShortcut(a: Shortcut, b: Shortcut): boolean {
  return a.kind === b.kind && a.input === b.input && a.ctrl === b.ctrl && a.shift === b.shift && a.alt === b.alt;
}
export function shortcutLabel(value: Shortcut): string {
  const input = value.kind === 'modifier' ? '' : value.kind === 'mouse' ? ['Left mouse', 'Middle mouse', 'Right mouse'][Number(value.input)] ?? `Mouse ${Number(value.input) + 1}`
    : value.kind === 'wheel' ? 'Wheel' : value.input === ' ' ? 'Space' : value.input.length === 1 ? value.input.toUpperCase() : value.input;
  return [value.ctrl ? 'Ctrl/Cmd' : '', value.shift ? 'Shift' : '', value.alt ? 'Alt' : '', input].filter(Boolean).join(' + ');
}
export function acceptsShortcut(action: typeof SHORTCUTS[number], value: Shortcut): boolean {
  return action.defaults[0].kind === 'modifier' ? value.kind === 'modifier'
    : action.defaults[0].kind === 'wheel' ? value.kind === 'wheel'
    : action.group === 'Phrase gestures' || action.id === 'controlReset' ? value.kind === 'mouse'
    : action.group === 'Global' || action.group === 'Phrase editor' || action.id === 'clearSections' ? value.kind === 'key' : value.kind === 'key' || value.kind === 'mouse';
}
export function decodeShortcuts(value: unknown): ShortcutBindings | null {
  if (value === undefined) return defaultShortcuts();
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const result = { ...defaultShortcuts() };
  for (const action of SHORTCUTS) {
    const bindings = (value as Record<string, unknown>)[action.id];
    if (bindings === undefined) continue;
    if (!Array.isArray(bindings) || bindings.length > 4 || bindings.some(b => !b || typeof b !== 'object'
      || !['key', 'mouse', 'wheel', 'modifier'].includes(b.kind) || typeof b.input !== 'string' || !b.input.length
      || b.input.length > 40 || ['ctrl', 'shift', 'alt'].some(k => typeof b[k] !== 'boolean')
      || (b.kind === 'mouse' && !/^[0-4]$/.test(b.input)) || (b.kind === 'wheel' && b.input !== 'scroll')
      || (b.kind === 'modifier' && (b.input !== 'held' || !(b.ctrl || b.shift || b.alt)))
      || !acceptsShortcut(action, b))) return null;
    result[action.id] = bindings.map(({ kind, input, ctrl, shift, alt }) => ({ kind, input, ctrl, shift, alt }));
  }
  return result;
}
