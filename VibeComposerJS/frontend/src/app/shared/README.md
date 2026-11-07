# Shared UI

Keep reusable presentation controls and UI helpers here when multiple features have a concrete need for them. Domain UI lives with its feature even when several features embed it; see the [feature ownership map and workspace conventions](../features/README.md).

| Primitive | Concrete consumers |
| --- | --- |
| `compact-knob.component.*` | Part settings, harmony, track policies, workspace, transport and preferences |
| `compact-toggle.component.*` | Part settings, harmony, track policies and workspace |
| `control-heading.component.*` | Knobs, toggles and the part editor; shared labels, reset and inheritance presentation |
| `select.component.*`, `menu-select.component.*` | Flat and categorized value selectors; shared option rendering, surface/popup styles, wheel stepping and right-click reset |
| `context-menu.component.*` | Track-name actions and variation additions (so far); reusable menus with dynamic nested choices |
| `keyboard-shortcuts.ts` | Shared text-input/event helpers for global and phrase keyboard routing |

Promote a new primitive here only after identifying real consumers across features. Keep music-specific settings descriptors, pattern controls, chord controls and score helpers in their owning features. Shared UI must not import feature components or own project commands.

Wrap a trigger in `<vc-context-menu [items]="items()" (action)="handle($event)">…</vc-context-menu>` for a right-click menu. Items have stable `id`, `label`, optional `disabled` and recursive `children`. Child menus resolve their paths against the current input, so choices can change while open. CDK owns hover, keyboard navigation, dismissal and viewport placement. Leaf actions emit their ID and the original viewport coordinates (`x`, `y`); feature owners can use these coordinates for an anchored follow-up dialog. The menu never runs project commands itself. For a button trigger, export the wrapper as `#menu="vcContextMenu"` and bind the button’s `[cdkMenuTriggerFor]="menu.menu()"` (import `CdkMenuModule` in the consumer). This reuses the same menu template, keyboard navigation and placement.

Use semantic tokens from `src/styles.css`, separate component templates/styles, and single-line CSS section comments as required by the [translation rules](../../../../TRANSLATION_RULES.md). Preserve existing accessibility, reset and gesture behavior when reusing controls.

Use `<vc-select [options]="choices" [value]="value" (valueChange)="choose($event)"></vc-select>` for choices. Options may be strings, numbers, or `{ value, label, disabled? }` objects; `[mixed]` adds a disabled Mixed entry. `[selectDefault]` sets the right-click default; omitting it resets to the first enabled option. Wheel input wraps through enabled options. The default binding leaves Ctrl/Command zoom untouched. Wheel, reset, fine adjustment and editing bindings are UI preferences in `core/ui/shortcuts.ts`, matched through `UiPreferencesService`; Settings > Shortcuts records and restores them. Keep shortcut metadata and defaults in that catalog when adding an interaction. Each changed gesture emits one string value through `valueChange`; disabled controls and no-op resets emit none.

The custom element host uses Angular ARIA's `Combobox` directive and an explicit-selection `Listbox` in a CDK overlay. Arrow keys and typeahead browse; Enter, Space and option clicks commit; Escape cancels. Selection is remembered in a linked signal that follows bound input changes, with a separate popup draft so browsing never publishes project edits. Labels, IDs, ARIA attributes and disabled state stay on the host. Export the component as `#choice="vcSelect"` and call `choice.restoreValue()` when a choice opens an uncommitted draft or runs a one-shot action.

Popup geometry accounts for the space above and below the trigger, caps the list height to the available viewport, and repositions after ARIA's deferred listbox renders. Long lists scroll inside the panel. The DOM popup does not depend on native select or tooltip popup lifetimes; ordinary `title` tooltips remain available.

Features own option data, change commands, dimensions and contextual variants; common surface, focus and popup rules live in the component CSS. Use computed option lists for dynamic labels such as drum mapping, fonts and instrument pools. Feature action-button CSS targets `button`; select styling targets `vc-select`. Disabled select styles use `[aria-disabled="true"]`.

Use `<vc-menu-select [options]="choices" [groups]="families" [value]="value" (valueChange)="choose($event)" />` for categorized value choices. Feature-owned groups contain stable IDs, labels and string value IDs; empty families disappear, and ungrouped values remain available at the root. The trigger is a menu button with checked radio leaves; CDK owns keyboard navigation, typeahead, focus return and dismissal. Browsing never changes the value. The control shares select surface styling and preserves Mixed, disabled options, string outputs, right-click defaults and wheel order from the flat options array. Track instrument/percussion and strum selectors are the initial consumers.

Menu selectors also accept `[actions]="commands"` and emit command IDs through `(action)`. Commands render as menu items, never as checked values, and are excluded from wheel stepping and reset. Features own any file picker or dialog invoked by a command. Scale families live with Harmony UI; fill and rhythm families live with part settings.
