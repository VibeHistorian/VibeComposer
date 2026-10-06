# Shared UI

Place reusable presentation components and UI helpers here when multiple features have a concrete need for them. Examples include buttons, sliders, selects, segmented controls, menus, popovers, dialogs, tooltips, and generic inspector layout. Keep generator and music-domain behavior with the feature that owns it.

## Visual conventions

- Use a persistent project header for project actions and a compact key/tempo summary. Use the shared bottom dock for playback and loop controls.
- Keep the transport dock present across routes; it owns playback buttons and position display while `core/audio` schedules Web Audio events outside Angular rendering.
- Keep navigation focused on Create, Arrange, Edit, and Mix. Give each workspace a clear heading and one primary action.
- Keep the arrangement overview and all-part score mounted in the app shell as users switch among Create, Arrange, Edit, and Mix. The shared canvas owns track selection and preview visibility; each feature continues to own its editing tools.
- Some operations should always be available via header/footer, or as quick toggles (e.g. global volume, key, tempo, per-track/per-group/project-wide Solo/Mute) even if a dedicated view offers a full version (e.g. S/M buttons are always offered near tracks for convenience, while the dedicated Mix view offers per-track volume/pan/effects/etc.).
- Use the shared contextual inspector for the current track or arrangement selection, with feature workspaces providing the detailed controls until those controls move into the inspector.
- Show common controls directly; group advanced generation, variation, and project settings under clear expandable headings. Preserve dense grids when they support direct musical editing, as in piano rolls and drum sequencers.
- Use neutral surfaces and restrained borders to establish hierarchy. Assign stable colors to musical roles and use them consistently in part lists and arrangement views; don't color unrelated controls just to distinguish them.
- Prefer readable labels for important actions. Use icons for familiar secondary actions with accessible names and tooltips.
- Keep layouts usable at narrower window sizes by allowing navigation and inspector panels to collapse while preserving access to the active canvas and transport.

Shared components should define consistent appearance and interaction, not absorb music-domain rules. If a control only makes sense for one feature, keep it in that feature even if it uses shared primitives.
