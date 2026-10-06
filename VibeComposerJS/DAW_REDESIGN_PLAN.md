# VibeComposerJS DAW Workspace Redesign Plan

## Goal

Consolidate the translated app into one editing workspace. Remove Create, Arrange, Edit, and Mix as route pages and remove the collapsible mode-controls section. Keep generation, arrangement, note editing, and mixing close to the song canvas, with a context-aware inspector for detailed settings.

The split-parts moodboard is the primary layout reference: grouped tracks at left, arrangement and dense score in the center, a contextual inspector at right, and persistent transport at the bottom. The legacy Java screenshot is a reference for the breadth and grouping of generation controls, not a request to reproduce its control wall. Keep the current dark theme and role colors.

References: [split-parts UI moodboard](frontend/UI_design_moodboard_split_parts.png), [legacy Java VibeComposer 2.6](frontend/LEGACY_java_vibecomposer26.png), and [translation rules](TRANSLATION_RULES.md).

## Workspace behavior

- Use a single workspace route and persistent shell. The arrangement overview and dense all-track score remain in the center canvas, with grouped tracks on the left, contextual inspector on the right, and transport along the bottom.
- Remove route navigation and page-level mode controls for Create, Arrange, Edit, and Mix. These names may remain as labels for grouped controls or canvas tools where useful, but they do not select separate pages or replace the song canvas.
- Arrange all sections and edit section/track inclusion directly in the arrangement canvas. Provide context-aware section and track actions in the inspector and canvas context menus. Every arrangement operation must be reachable without navigating away from the canvas.
- Clicking a note in the arrangement or score opens that track's piano roll or drum grid in the center canvas. Applying the edits commits them and returns to the arrangement view. Keep note properties in the inspector while editing.
- Open the mixer from a mixer-icon button. The mixer is a popup over the workspace and edits the same per-track mix state used by playback and MIDI export.
- Keep transport mounted at the bottom. Make key, transpose, tempo, and mode editable in the transport area. Retain playback, loop, position, and undo/redo access.

## Create controls and track organization

- Place primary chord setup in a compact, dense panel above the Tracks list. It owns the progression and the primary chord-generation controls needed to establish harmony.
- Put detailed chord-generation controls and other context-specific generation settings in the inspector. Selecting a role group shows generation settings that affect all tracks in that group; selecting an individual track shows that track's local generation and instrument settings.
- Group instrument setups by implemented type in the left Tracks panel: Bass, Chords, Arpeggio, and Drums. Each group can contain multiple independently configured tracks, can scroll as the list grows, and has group-level selection and add-track actions.
- Keep global project attributes (key, transpose, tempo, and mode) editable in the transport. Do not duplicate global controls in a route page.
- Keep quick track actions near each row. Put full parameter sets in the inspector rather than expanding every track into a form.

## Canvas and editing

- Keep arrangement sections and the dense all-track score visible together in the center canvas during normal workspace use. Share the ruler, section boundaries, and playhead between them.
- The score uses consistent role colors and velocity shading. Track selection can emphasize a track while leaving other notes as context.
- Editing is an in-canvas state entered by selecting a note. Use a piano roll for pitched parts and a drum grid for percussion. Provide an explicit Apply/Done action that commits one coherent edit and returns to arrangement view; provide Cancel to discard the active edit session.
- Keep precise note properties in the inspector while the editor is open. Velocity editing remains available and aligned to the selected note.
- Responsive behavior may collapse either side panel, but always provide a clear way to restore it and retain the central song canvas.

## Project and track state

- Keep stable track IDs and independent generator settings, edited phrases, General MIDI instrument programs, MIDI channels, and mix state for each track. Role groups are organizational and never replace the track entity.
- Use a discriminated union so each track role has its matching generator settings. Start new projects with one track per implemented role, and support adding, duplicating, reordering, and removing tracks.
- Store section inclusion and chance overrides by track ID, with role defaults where no track-specific override exists. Keep section chord overrides and project key, transpose, tempo, seed, progression, and name.
- Generation, phrase editing, instrument assignment, mixing, undo history, and MIDI export must target track IDs when an operation applies to one track. Group-level generation controls update all tracks in that role group.
- Persist per-track settings and phrase edits in project JSON and session storage. Treat `schemaVersion` as informational during buildout; validate the project structure without version-gated loading or compatibility migrations.
- Keep generation logic framework-independent and seeded random streams explicit as required by [translation rules](TRANSLATION_RULES.md). Keep audio and MIDI processing asynchronous and outside Angular rendering work.

## Delivery sequence

1. **Single workspace shell:** Remove the four feature routes and mode-controls drawer. Keep one route with the track browser, persistent canvas, inspector, and transport.
2. **Workspace selection model:** Add selected track-group, track, section, and note contexts. Show group-wide generation controls for a selected role group and local controls for a selected track.
3. **Creation and arrangement controls:** Move primary chord setup above Tracks, expose detailed generation in the inspector, and make section and arrangement actions available directly from the canvas and its contextual inspector.
4. **In-canvas note editing:** Open the piano roll or drum grid from a clicked canvas note. Support Apply and Cancel, commit edits as one history action, and return to arrangement on Apply.
5. **Mixer and transport:** Replace the Mix page with an icon-triggered mixer popup. Make key, transpose, tempo, and mode editable in the persistent transport.
6. **Responsive polish:** Preserve the central canvas and clear panel access at smaller widths. Keep controls keyboard-accessible and use shared theme tokens.

## Completion criteria

- The app has no Create, Arrange, Edit, or Mix route pages and no collapsible mode-controls drawer.
- The arrangement and dense score remain together in the center canvas, with grouped multi-track instrument types on the left, contextual inspector on the right, and persistent transport at the bottom.
- Primary chord settings appear above Tracks; detailed generation settings appear in the inspector according to group or track selection.
- Key, transpose, tempo, and mode can be changed in the transport.
- Arrangement actions are available directly through the canvas and context-aware inspector.
- Clicking an arrangement/score note opens the correct piano roll or drum grid; Apply commits and returns to arrangement view.
- A mixer icon opens a popup with per-track instrument/channel and mix controls.
- Multiple tracks of a type retain independent settings, notes, instrument, MIDI channel, and mix state; group generation settings can apply to all tracks of that type.
- Track state persists, undo groups gestures into coherent actions, and MIDI export preserves separate track settings.

## Design guardrails

- Keep legacy melody generation and its controls out of scope until separately translated.
- Treat the moodboard as a layout direction; use actual translated generator parameters and current theme tokens.
- Keep full parameter sets in the inspector and the song canvas as the visual anchor. Avoid rebuilding the legacy Java control wall as one large form.
- Keep the all-notes score useful for arrangement context, and use the selected track's piano roll or drum grid for precise edits.
- Use semantic theme variables from `frontend/src/styles.css`; add shared tokens there when a reusable value is missing.
