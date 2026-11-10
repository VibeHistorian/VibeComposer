# Shortcut activation contexts

`shortcuts.ts` owns binding definitions, activation context metadata and conflict comparison. Settings calls `shortcutsConflict` for every other action, regardless of its display group or page. Existing event handlers remain responsible for dispatching actions; keep their targeting rules and this metadata in sync when changing interactions.

Every action must have an entry in `SHORTCUT_CONTEXTS`. The exhaustive TypeScript record rejects missing entries when adding an action. Each entry contains one or more alternative contexts:

- Constraints within a context combine with AND.
- Values within an axis combine with OR.
- Contexts within an action combine with OR.
- An omitted axis is unrestricted, so a global context overlaps every narrower context.

Two contexts overlap unless at least one shared axis has disjoint values. Two actions compete if any pair of their contexts overlaps. Equal bindings conflict only for competing actions. Settings groups, action names and the assigned modifiers do not establish context boundaries.

For example, parameter lock and section unfreeze share the knob/dropdown surfaces, but require different scope values (`global` versus `section`). Their contexts cannot overlap, so the same binding is valid. Snapped and free placement both accept empty phrase-grid space; snapping is the action's result, not an activation precondition. Those actions must use different bindings.

Keep axes independent. Selection state is separate from note geometry: an action for all note bodies overlaps both selected and unselected note bodies. Use alternative contexts when an action accepts different combinations, rather than widening each axis and accidentally including unsupported combinations.

Only declare click/drag phase separation where the event handler actually supports both operations on the same gesture. Box selection and click deletion share a handler that distinguishes movement on release. Seeking and placement claim the pointer on press, so they leave phase unrestricted and cannot share with an overlapping drag operation.

Held modifiers have an effect axis. Independent effects such as time snapping and pitch retention may compose, while two competing section-selection modifiers share the same effect and context. This is distinct from a command's output: never give competing commands different effect values merely to bypass a conflict.

`scripts/keyboard-shortcuts.test.cjs` verifies context intersection, wildcard and alternative semantics, symmetry, cross-page global conflicts and all shared default bindings. `scripts/ui-preferences-browser.test.cjs` exercises context-aware recording in Settings, including shared global/section bindings and rejected placement/global-history conflicts.
