# Shared UI

Place reusable presentation components and UI helpers here when multiple features have a concrete need for them. Examples include buttons, sliders, selects, segmented controls, menus, popovers, dialogs, tooltips, and generic inspector layout. Keep generator and music-domain behavior with its feature component.

## Workspace conventions

- Use one persistent workspace with grouped Tracks at left, arrangement and dense score in the center, a selection-aware inspector at right, and transport along the bottom.
- Do not add route navigation or a mode-controls drawer for Create, Arrange, Edit, or Mix.
- The transport owns playback, position, loop, key, mode, transpose, and tempo controls. Playback scheduling stays in `core/audio`, outside Angular rendering.
- The mixer opens as a popup from a button with an accessible icon label.
- Track-group selection shows shared musical settings; individual track selection shows local musical settings; section selection shows arrangement controls. Instrument/channel selectors remain in track rows, and reroll policies remain in expanded group headers.
- Arrangement headers exclusively select section ranges. Cell bodies select section-role settings; track buttons select section-track settings. Middle-click or I on a focused track button toggles presence without changing selection; the local inspector also provides an Include checkbox.
- `WorkspaceUiService.settingsTarget` identifies the active settings scope independently from the header-selected range used for score focus and looping. Global track/group selections also preserve that range. CLEAR or Escape clears both contexts.
- The Part settings panel between arrangement and score starts expanded and follows the settings target. It and the inspector share `PartSettingsEditorComponent`, with typed control descriptors and effective mixed values. Commands target global track/role settings or sparse section role/track patches. The inspector's Edit global track settings action explicitly leaves section scope.
- Section part settings resolve track base → section role patch → section track patch through the framework-independent `core/music/part-settings.ts` module. Per-field and scoped resets remove only the selected layer's patches. Manual track phrases remain replacements; the panels identify when they mask generator settings.
- Header-selected sections expose scoped Chordlets/durations and sparse timing/key context in `SectionContextEditorComponent`. Section swing supersedes applicable part swing; section BPM/key/scale inherit from the project. Arrangement/score ranges, playback and MIDI use shared variable-duration boundaries and tempo maps from `core/music/section-context.ts`. See the P5f implementation record for normalized timing and manual-note ownership.
- The score shares one harmonic pitch axis with a condensed keyboard and a short percussion section with named pitch rows. Header role filters control visibility; optional Solo/Mute sync makes those roles audible in one history action. Drums disappear when filtered out; velocities appear only during editing.
- Clicking a score note zooms its phrase in the same score renderer while arrangement and part settings remain mounted. The header provides Apply, Local Apply and Cancel, and the existing inspector shows note properties. Harmonic and drum notes share move/resize interactions. Apply or middle-clicking empty space saves the global track phrase; Local Apply saves a section-relative track phrase and preserves material outside the edited phrase. Both commit once and return to the previous score viewport; Cancel discards the draft.
- Keep common controls directly available and group detailed settings under clear labels. Preserve the density of piano rolls and drum grids.
- Use stable role colors for tracks and arrangement blocks, with neutral surfaces for other controls. Reuse the shared theme tokens.
- At narrower widths, let the track browser or inspector scroll and collapse while keeping a clear path to the song canvas and transport.

Shared components define consistent appearance and interaction. They should not absorb music-domain rules that belong to a feature.
