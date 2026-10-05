# Shared UI

Place reusable presentation components and UI helpers here when multiple features have a concrete need for them. Examples include buttons, sliders, selects, segmented controls, menus, popovers, dialogs, tooltips, and generic inspector layout. Keep generator and music-domain behavior with the feature that owns it.

## Visual conventions

- Use a persistent project header for project actions and a compact key/tempo summary. Use the shared bottom dock for playback and loop controls.
- Keep navigation focused on Create, Arrange, Edit, and Mix. Give each workspace a clear heading and one primary action.
- Let the main canvas occupy the available space. Use a contextual inspector for settings belonging to the selected part, section, note, or track.
- Show common controls directly; group advanced generation, variation, and project settings under clear expandable headings. Preserve dense grids when they support direct musical editing, as in piano rolls and drum sequencers.
- Use neutral surfaces and restrained borders to establish hierarchy. Assign stable colors to musical roles and use them consistently in part lists and arrangement views; don't color unrelated controls just to distinguish them.
- Prefer readable labels for important actions. Use icons for familiar secondary actions with accessible names and tooltips.
- Keep layouts usable at narrower window sizes by allowing navigation and inspector panels to collapse while preserving access to the active canvas and transport.

Shared components should define consistent appearance and interaction, not absorb music-domain rules. If a control only makes sense for one feature, keep it in that feature even if it uses shared primitives.
