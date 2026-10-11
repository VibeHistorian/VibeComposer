# Feature areas

Keep translated music behavior in focused feature components, embedded where the single workspace needs it. Do not create separate Create, Arrange, Edit, or Mix routes.

- **Create behavior** — Harmony Chordlets live in transport. Musical part settings appear in contextual editors; track-reroll policies live in expanded Tracks headers. Role-group musical edits apply to current members without replacing their identities.
- **Arrange behavior** — The arrangement and dense all-track score share the center canvas. Selecting a section exposes its type, length, chord overrides, track inclusion and chance, and section actions in the inspector.
- **Edit behavior** — Selecting a score note opens its track's piano roll or drum grid in the center canvas. Apply saves the phrase as one history action and returns to arrangement; Cancel discards the draft.
- **Mix behavior** — An icon opens the mixer popup. It edits each track's instrument, channel, level, pan, mute, and solo values used by playback and MIDI export.

## Angular ownership

Current layout and state contracts are defined in [the workspace guide](../../../../DAW_REDESIGN_PLAN.md). Follow [the active backlog](../../../../CONTEXTUAL_CONTROLS_PLAN.md) for remaining translation work; completed delivery evidence lives in [the work history](../../../../__work_history/README.md).

- Keep app-wide project/session services and the Wails bridge in `core/`.
- Keep music-domain behavior with the feature component that owns the interaction, even when that component is embedded in the workspace canvas.
- Promote presentation primitives to `shared/` only when they have a real use across features. See the shared UI README for the visual conventions and promotion rule.

## Visuals

- Use [the split-parts moodboard](../../../UI_design_moodboard_split_parts.png) as layout direction.
- Use actual translated parameters and the shared theme; the legacy Java layout is a reference for capability breadth, not a screen to reproduce.
