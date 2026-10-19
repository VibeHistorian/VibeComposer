# Shared UI

Keep reusable presentation controls and UI helpers here when multiple features have a concrete need for them. Domain UI lives with its feature even when several features embed it; see the [feature ownership map and workspace conventions](../features/README.md).

| Primitive | Concrete consumers |
| --- | --- |
| `compact-knob.component.*` | Part settings, harmony, track policies, workspace, transport and preferences |
| `compact-toggle.component.*` | Part settings, harmony, track policies and workspace |
| `control-heading.component.*` | Knobs, toggles and the part editor; shared labels, reset and inheritance presentation |
| `wheel-select.directive.ts` | Part settings, section context, track policies, workspace and transport selects |
| `keyboard-shortcuts.ts` | App shortcuts and phrase editing; shared text-input/event helpers |

Promote a new primitive here only after identifying real consumers across features. Keep music-specific settings descriptors, pattern controls, chord controls and score helpers in their owning features. Shared UI must not import feature components or own project commands.

Use semantic tokens from `src/styles.css`, separate component templates/styles, and single-line CSS section comments as required by the [translation rules](../../../../TRANSLATION_RULES.md). Preserve existing accessibility, reset and gesture behavior when reusing controls.
