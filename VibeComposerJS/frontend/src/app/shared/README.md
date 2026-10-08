# Shared UI

Place reusable presentation components and UI helpers here when multiple features have a concrete need for them. Examples include buttons, sliders, selects, segmented controls, menus, popovers, dialogs, tooltips, and generic inspector layout. Keep generator and music-domain behavior with its feature component.

## Workspace conventions

- Use one persistent workspace with grouped Tracks at left, arrangement and dense score in the center, a selection-aware inspector at right, and transport along the bottom.
- Do not add route navigation or a mode-controls drawer for Create, Arrange, Edit, or Mix.
- The transport owns playback, position, loop, key, mode, transpose, and tempo controls. Playback scheduling stays in `core/audio`, outside Angular rendering.
- The mixer opens as a popup from a button with an accessible icon label.
- Track-group selection shows shared generation settings; individual track selection shows local generator and instrument settings; section selection shows arrangement controls.
- Arrangement headers exclusively select section ranges. Cell bodies select section-role settings; track buttons select section-track settings. Middle-click or I on a focused track button toggles presence without changing selection; the local inspector also provides an Include checkbox.
- `WorkspaceUiService.settingsTarget` identifies the active settings scope independently from the header-selected range used for score focus and looping. Global track/group selections also preserve that range. CLEAR or Escape clears both contexts.
- The Part settings panel between arrangement and score starts expanded and follows the settings target. At the first selection checkpoint it displays existing values; local musical editing remains read-only until the section-patch generation pipeline is implemented. The inspector's Edit global track settings action explicitly leaves section scope.
- Clicking a score note opens the in-canvas piano roll or drum grid. Apply commits the draft phrase as one user action and returns to arrangement; Cancel discards it.
- Keep common controls directly available and group detailed settings under clear labels. Preserve the density of piano rolls and drum grids.
- Use stable role colors for tracks and arrangement blocks, with neutral surfaces for other controls. Reuse the shared theme tokens.
- At narrower widths, let the track browser or inspector scroll and collapse while keeping a clear path to the song canvas and transport.

Shared components define consistent appearance and interaction. They should not absorb music-domain rules that belong to a feature.
