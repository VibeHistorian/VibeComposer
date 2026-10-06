# Feature areas

Keep translated music behavior in focused feature components, embedded where the single workspace needs it. Do not create separate Create, Arrange, Edit, or Mix routes.

- **Create behavior** — Primary chord setup sits above the grouped Tracks list. Role-specific generation settings appear in the inspector, with role-group selection applying the chosen generator controls to every track of that type.
- **Arrange behavior** — The arrangement and dense all-track score share the center canvas. Selecting a section exposes its type, length, chord overrides, track inclusion and chance, and section actions in the inspector.
- **Edit behavior** — Selecting a score note opens its track's piano roll or drum grid in the center canvas. Apply saves the phrase as one history action and returns to arrangement; Cancel discards the draft.
- **Mix behavior** — An icon opens the mixer popup. It edits each track's instrument, channel, level, pan, mute, and solo values used by playback and MIDI export.

## Angular ownership

- Keep app-wide project/session services and the Wails bridge in `core/`.
- Keep music-domain behavior with the feature component that owns the interaction, even when that component is embedded in the workspace canvas.
- Promote presentation primitives to `shared/` only when they have a real use across features. See the shared UI README for the visual conventions and promotion rule.

## Visuals

- Use [the split-parts moodboard](../../../UI_design_moodboard_split_parts.png) as layout direction.
- Use actual translated parameters and the shared theme; the legacy Java layout is a reference for capability breadth, not a screen to reproduce.
