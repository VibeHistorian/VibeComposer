# Feature areas

Organize the main experience into four workspaces. Each feature owns its screen behavior and music-domain logic; the app shell provides navigation and persistent project controls.

## Workspaces

- **Create** — Set the key, tempo, and chord progression, then generate or regenerate melody, bass, chords, arpeggios, and drums. Show each role as a concise part card or row. Put detailed generation and variation parameters in the inspector for the selected part.
- **Arrange** — Show song sections on a timeline. Selecting a section opens its length, type, part inclusion, and variation controls in the inspector. Let users add, duplicate, reorder, and edit sections from the timeline itself rather than repeating an Edit button in every cell.
- **Edit** — Provide the editor that matches the selected content: piano roll for pitched parts, step grid for drums, and score view where useful. Keep editing tools close to the canvas and selection-specific properties in the inspector.
- **Mix** — Group instruments and sound controls by part, with direct access to levels, mute/solo, and instrument selection. Keep playback and loop controls in the shared bottom transport dock.

These are workspaces, not a forced wizard: users can move between them at any time. Keep the central canvas focused on the current task, give each workspace one primary action, and avoid showing every domain's controls simultaneously. A chord picker should open in the context of the selected progression slot; offer scale-aware choices and a way to browse the full chord collection without making a large chord matrix the default view.

## Angular ownership

- Keep Create, Arrange, Edit, and Mix screens and their domain behavior in their respective feature areas. Split into smaller components when a concrete screen or interaction needs it.
- Keep the piano roll, drum grid, arrangement timeline, chord picker, and generation controls with the feature that owns their musical behavior.
- Put app-wide project/session services and the Wails bridge in `core/`.
- Promote presentation primitives to `shared/` only when they have a real use across features. See the shared UI README for the visual conventions and promotion rule.

-----------------------

## Visuals

- rough visual design available in frontend/UI_design_moodboard.png
- the **real** layout will differ (using the actual parameters and features available from the desktop VibeComposer), but the new modern styling should be approximated