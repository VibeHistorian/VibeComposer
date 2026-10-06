# Feature areas

Organize the main experience into four workspaces. Each feature owns its screen behavior and music-domain logic; the app shell provides navigation and persistent project controls.

## Workspaces

- **Create** — Set the key, tempo, and chord progression, then generate in-scope parts such as bass, chords, arpeggios, and drums. Export the result as MIDI. Show each role as a concise part card or row. Put detailed generation and variation parameters in the inspector for the selected part.
- **Arrange** — Show song sections on a timeline. Selecting a section opens its length, type, part inclusion, and variation controls in the inspector. Let users add, duplicate, reorder, and edit sections from the timeline itself rather than repeating an Edit button in every cell.
- **Edit** — The piano roll edits generated bass, chord, and arpeggio phrases; the drum grid edits one progression bar at a time. Phrase edits are saved with the project, included in MIDI export, and can be restored to the seeded generator output.
- **Mix** — Each generated part has General MIDI instrument selection, level, pan, mute, and solo controls. These settings are saved with the project and written into MIDI export; browser preview and loop controls live in the shared transport dock.

These are workspaces, not a forced wizard: users can move between them at any time. Keep the central canvas focused on the current task, give each workspace one primary action, and avoid showing every domain's controls simultaneously. A chord picker should open in the context of the selected progression slot; offer scale-aware choices and a way to browse the full chord collection without making a large chord matrix the default view.

## Angular ownership

- Keep Create, Arrange, Edit, and Mix screens and their domain behavior in their respective feature areas. Split into smaller components when a concrete screen or interaction needs it.
- Keep the piano roll, drum grid, arrangement timeline, chord picker, and generation controls with the feature that owns their musical behavior.
- Put app-wide project/session services and the Wails bridge in `core/`.
- Promote presentation primitives to `shared/` only when they have a real use across features. See the shared UI README for the visual conventions and promotion rule.

-----------------------

## Visuals

- rough visual design available in frontend/UI_design_moodboard_split_parts.png
- the **real** layout will differ (using the actual parameters and features available from the desktop VibeComposer), but the new modern styling should be approximated
