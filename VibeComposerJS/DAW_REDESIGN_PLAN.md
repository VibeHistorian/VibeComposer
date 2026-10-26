# VibeComposerJS workspace contracts

The initial workspace redesign is delivered. Its original sequence and completion criteria are archived in [__work_history/daw-workspace-record.md](__work_history/daw-workspace-record.md). Remaining translation work lives in [CONTEXTUAL_CONTROLS_PLAN.md](CONTEXTUAL_CONTROLS_PLAN.md), with Java lookups in [CONTROL_PLACEMENT_MAP.md](CONTROL_PLACEMENT_MAP.md).

## Layout and interaction

- Keep one workspace route: grouped Tracks at left, arrangement and dense score in the center, contextual inspector at right, persistent transport at the bottom. Do not add Create/Arrange/Edit/Mix route pages or a mode-controls drawer.
- Part settings sit between arrangement and score and start expanded. The inspector supplies complete musical settings for the same scope. Reroll policies live only in expanded Tracks headers; instrument/channel selectors remain in track rows.
- Global and section role groups expose a `?` toggle for individual track quick settings. This view replaces the shared quick controls with numbered track rows and thin separators, using each track's global or section scope. Numbers sit vertically centered to the left of the controls in a one-character-wide column, wrapping multi-digit orders without adding row height. It keeps the panel above the score even on shorter windows and allows more panel height while retaining a usable score.
- Section headers select section ranges; cell and track selection set an independent settings target. Left-click selects, middle-click on arrangement track buttons toggles presence. Preserve keyboard/device alternatives, mixed values and scope breadcrumbs.
- Keep harmony Chordlets in transport alongside global key, transpose, tempo, mode, exact seed and playback. Keep the current theme, role colors and the canvas as the visual anchor.
- Selecting notes or Edit notes opens the relevant in-canvas piano roll/drum grid, including empty tracks. Apply commits one coherent edit and returns to arrangement; Cancel discards the draft. Mix controls live in the Tracks quick shortcuts; MIDI export is available in the main menu.
- Target desktop and landscape layouts at widths of at least 700 CSS pixels; portrait mobile compatibility and viewport tests below 700px are out of scope. Side panels may collapse on smaller supported windows but must remain recoverable; preserve the central canvas, useful note density, ruler, section boundaries and playhead.

## State and implementation

- Role ordering is Melody, Bass, Chords, Arpeggio, Drums. Tracks have stable independent IDs, generated/manual phrases, instruments, channels and mix state. Any role and the whole project may be empty.
- Resolve musical settings as track base → section role patch → section track patch. Keep section-range selection, presence, reroll policy, instrument identity and manual ownership separate. Mutate through immutable historical commands; commit a drag/paint gesture once.
- Persist settings, policies, overrides, exact seed strings and manual phrases in JSON/session state. Validate values/references without schema-version gates or compatibility migrations during buildout.
- Keep seeded generation framework-independent, Wails thin, and audio/MIDI scheduling asynchronous outside Angular rendering. Playback, previews, score and MIDI export share resolved generated output.
- Use semantic tokens from `frontend/src/styles.css`, separate component templates/styles and the [translation rules](TRANSLATION_RULES.md). Exclude the older melody skeleton and its exclusive controls.

Visual references: [split-parts moodboard](frontend/UI_design_moodboard_split_parts.png) and [legacy Java screenshot](frontend/LEGACY_java_vibecomposer26.png). The Java UI is a functional reference, not a layout to reproduce.
