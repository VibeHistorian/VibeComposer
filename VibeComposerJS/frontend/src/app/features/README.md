# Feature areas

Keep translated music behavior in focused feature components, embedded where the single workspace needs it. Do not create separate Create, Arrange, Edit, or Mix routes.

- **Create behavior** — Harmony Chordlets live in transport. Musical part settings appear in contextual editors; track-reroll policies live in expanded Tracks headers. Role-group musical edits apply to current members without replacing their identities.
- **Arrange behavior** — The arrangement and dense all-track score share the center canvas. Selecting a section exposes its type, length, chord overrides, track inclusion and chance, and section actions in the inspector.
- **Edit behavior** — Selecting a score note zooms an editable phrase inside the shared score canvas, keeping arrangement and part settings visible. The workspace header supplies Apply, Local Apply and Cancel; note properties occupy the existing inspector. Harmonic and percussion notes share timing/pitch/duration controls. Apply commits a global track phrase; Local Apply stores a section-track phrase override. Drafts commit once, and Cancel discards them.
- **Mix behavior** — Tracks quick shortcuts edit each track's instrument, channel, level, pan, mute, and solo values used by playback and MIDI export. MIDI export is available in the main menu.

## Angular ownership

Current layout and state contracts are defined in [the workspace guide](../../../../DAW_REDESIGN_PLAN.md). Follow [the active backlog](../../../../CONTEXTUAL_CONTROLS_PLAN.md) for remaining translation work; completed delivery evidence lives in [the work history](../../../../__work_history/README.md).

- Keep app-wide project/session services and the Wails bridge in `core/`.
- Keep music-domain behavior with the feature component that owns the interaction, even when that component is embedded in the workspace canvas.
- Promote presentation primitives to `shared/` only when they have a real use across features. See the shared UI README for the visual conventions and promotion rule.

## Ownership map

Paths below are relative to `src/app/`. Feature folders group embedded interactions; they do not introduce routes or separate service instances. Import owners directly rather than adding forwarding modules at old paths.

| Owner | Files and responsibility |
| --- | --- |
| `features/workspace/` | `workspace-canvas.component.*`: persistent shell, Tracks, contextual inspector and composition of the embedded editors |
| `features/arrangement/` | `arrangement-overview.component.*`, `section-context-editor.component.*`, `arrangement-variations-dialog.component.*`: section selection, presence, musical context and variation drafts |
| `features/score/` | `score-canvas.component.*`, `score-chord-labels.directive.ts` and plain geometry/hit-testing/interaction helpers; see [score ownership](score/README.md) |
| `features/edit/` | `edit-workspace.component.*`: phrase drafts, note properties, Apply/Local Apply/Cancel and phrase import/export |
| `features/part-settings/` | Part editor, scope actions, control groups, pattern sequencer and shared Melody/Bass/Arpeggio duration dialog |
| `features/harmony/` | Chordlet editor and harmony settings, reused by transport and section/workspace editors |
| `features/tracks/` | Mix buttons and track generation policies embedded in Tracks and transport |
| `features/transport/` | `transport-dock.component.*`: playback and global musical controls |
| `features/history/` | `history-dialog.component.*`: project history navigation |
| `features/midi-import/` | `midi-import-dialog.component.*`: import choices; file/worker orchestration remains in `core/ui/` |
| `features/settings/`, `features/section-types/` | Preferences dialog, project-wide Melody settings (Behavior → Melody), global harmony generation and voicing (Behavior → Harmony), and section-type chance settings |
| `core/ui/workspace-ui.service.ts` | App-wide selection, settings targets and section ranges shared by workspace, editors, history and transport |
| `core/project/`, `core/music/`, `core/audio/` | Historical commands/persistence, framework-independent music and asynchronous playback/MIDI; see the [project-state guide](../core/project/README.md) |

Templates and styles stay beside their components. Fixture loaders and source-reading tests under `scripts/` use these same paths; see the [test guide](../../../scripts/README.md) for focused commands and production-build prerequisites.

## Compatibility

- Use actual translated parameters and the shared theme; the legacy Java layout is a reference for capability breadth, not a screen to reproduce.

## Workspace conventions

- Use one persistent workspace with grouped Tracks at left, arrangement and dense score in the center, a selection-aware inspector at right, and transport along the bottom.
- Do not add route navigation or a mode-controls drawer for Create, Arrange, Edit, or Mix.
- The transport owns playback, position, loop, key, mode, transpose, and tempo controls. Playback scheduling stays in `core/audio`, outside Angular rendering.
- Mix controls live in the Tracks quick shortcuts; MIDI export is available in the main menu.
- Track-group selection shows shared musical settings; individual track selection shows local musical settings; section selection shows arrangement controls. Instrument/channel selectors remain in track rows, and reroll policies remain in expanded group headers.
- Arrangement headers exclusively select section ranges. Cell bodies select section-role settings; track buttons select section-track settings. Middle-click or I on a focused role cell clears all tracks of that role in that section if any are present, otherwise includes all of them. On a track button it toggles only that track without changing selection; the local inspector also provides an Include checkbox.
- `WorkspaceUiService.settingsTarget` identifies the active settings scope independently from the header-selected range used for score focus and looping. Global track/group selections also preserve that range. CLEAR or Escape clears both contexts.
- The Part settings panel between arrangement and score starts expanded and follows the settings target. It and the inspector share `PartSettingsEditorComponent`, with typed control descriptors and effective mixed values. Commands target global track/role settings or sparse section role/track patches. The inspector's Edit global track settings action explicitly leaves section scope.
- Section part settings resolve track base → section role patch → section track patch through the framework-independent `core/music/part-settings.ts` module. Per-field and scoped resets remove only the selected layer's patches. Manual track phrases remain replacements; the panels identify when they mask generator settings.
- Header-selected sections expose scoped Chordlets/durations and sparse timing/key context in `SectionContextEditorComponent`. Section swing supersedes applicable part swing; section BPM/key/scale inherit from the project. Arrangement/score ranges, playback and MIDI use shared variable-duration boundaries and tempo maps from `core/music/section-context.ts`. See the P5f implementation record for normalized timing and manual-note ownership.
- The score shares one harmonic pitch axis with a condensed keyboard and a short percussion section with named pitch rows. Header role filters control visibility; optional Solo/Mute sync makes those roles audible in one history action. Drums disappear when filtered out; velocities appear only during editing.
- Clicking a score note zooms its phrase in the same score renderer while arrangement and part settings remain mounted. The header provides Apply, Local Apply and Cancel, and the existing inspector shows note properties. Harmonic and drum notes share move/resize interactions. Apply or middle-clicking empty space saves the global track phrase; Local Apply saves a section-relative track phrase and preserves material outside the edited phrase. Both commit once and return to the previous score viewport; Cancel discards the draft.
- Keep common controls directly available and group detailed settings under clear labels. Preserve the density of piano rolls and drum grids.
- Use stable role colors for tracks and arrangement blocks, with neutral surfaces for other controls. Reuse the shared theme tokens.
- Support desktop and landscape layouts at viewport widths of at least 700 CSS pixels. At smaller supported widths, let the track browser or inspector scroll and collapse while keeping a clear path to the song canvas and transport. Portrait mobile compatibility and browser viewport tests below 700px are out of scope.
