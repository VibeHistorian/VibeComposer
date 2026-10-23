# Shared UI

Keep reusable presentation controls and UI helpers here when multiple features have a concrete need for them. Domain UI lives with its feature even when several features embed it; see the [feature ownership map and workspace conventions](../features/README.md).

| Primitive | Concrete consumers |
| --- | --- |
| `compact-knob.component.*` | Part settings, harmony, track policies, workspace, transport and preferences |
| `compact-toggle.component.*` | Part settings, harmony, track policies and workspace |
| `control-heading.component.*` | Knobs, toggles and the part editor; shared labels, reset and inheritance presentation |
| `select.component.*` | All select controls; shared option rendering, surface/popup styles, wheel stepping and right-click reset |
| `keyboard-shortcuts.ts` | App shortcuts and phrase editing; shared text-input/event helpers |

Promote a new primitive here only after identifying real consumers across features. Keep music-specific settings descriptors, pattern controls, chord controls and score helpers in their owning features. Shared UI must not import feature components or own project commands.

Use semantic tokens from `src/styles.css`, separate component templates/styles, and single-line CSS section comments as required by the [translation rules](../../../../TRANSLATION_RULES.md). Preserve existing accessibility, reset and gesture behavior when reusing controls.

Use `<select vcSelect [options]="choices" [value]="value" (change)="choose($event)"></select>` for choices. Options may be strings, numbers, or `{ value, label, disabled? }` objects; `[mixed]` adds a disabled Mixed entry. `[selectDefault]` sets the right-click default; omitting it resets to the first enabled option. Wheel input wraps through enabled options and leaves Ctrl/Command zoom untouched. Each changed gesture dispatches one ordinary bubbling change event; disabled controls and no-op resets dispatch none.

The component uses the native select as its host, preserving native keyboard behavior, labels, IDs, ARIA attributes, disabled state, DOM value access and change event targets. Features own option data, change commands, dimensions and contextual variants; common surface, focus and popup rules live in the component CSS. Use computed option lists for dynamic labels such as drum mapping, fonts and instrument pools.
