# Refactor Session Notes

## Useful files

- `REFACTOR_MAP.md` is the module plan and progress checklist. Update it as modules are completed.
- `src/main/java/org/vibehistorian/vibecomposer/VibeComposerGUI.java` is the legacy UI owner. Search for a feature's fields and methods with `rg -n "Melody|melody" <file>` and inspect focused line ranges; avoid repeatedly dumping the whole class.
- `VibeComposerGUI_MigrationBackup.java` is a read-only reference. Do not edit it.
- `MelodyGUI.java` is the first extracted UI module. It owns melody controls and melody-specific UI setup, initial panel creation, panel randomization, and seed randomization. Its `Context` interface receives shared window operations so this class does not depend on `VibeComposerGUI`.
- `ChordGUI.java` owns chord UI state, chord settings construction, the chord tab, progression controls, custom chord editing and generation, and randomized chord panel creation. Cross-instrument workflows stay in `VibeComposerGUI` and read chord controls through `ChordGUI`; its `Context` interface supplies shared window operations.
- `ArpGUI.java` owns arp UI state, settings and tab construction, and randomized arp panel creation. Its `Context` interface supplies shared window operations.
- `ArrangementGUI.java` owns arrangement UI state, controls and table initialization, action dispatch, and popup/model helpers. Its `Context` supplies shared window operations and delegates cross-instrument or table-editing work that still belongs to `VibeComposerGUI`.
- `SwingUtils.java` holds shared Swing helpers. Put generally useful UI helpers here instead of making a feature module call back into the main window.
- `ExtraSettingsGUI` owns settings popup state and construction, including its Generation tab. `GenerationGUI` owns the randomization and macro controls in the main window, built by `initRandomButtons` and `initMacroParams`, plus panel transpose/sidechain/swing operations and BPM/instrument randomization. `VibeComposerCoreGUI` may still be a skeleton; inspect it before relying on it. `ScoreGUI` owns score UI state, tab setup, and popup behavior.
- `SoloMuteController` applies panel solo/mute state to sequencer tracks and handles the exclude-not-soloed panel action. Generated result updates belong to their feature GUIs: `ChordGUI`, `MelodyGUI`, `ArpGUI`, and `ArrangementGUI`.
- `PlaybackController` owns MIDI play, pause, stop, seek, saved-position, and queued-event cleanup workflows. Its context supplies only the MIDI CC thread and the few values read from settings and generation state.
- `MidiDeviceController` owns the active MIDI output device and synthesizer, SoundFont loading, sequencer output connections, endpoint cleanup, and direct MIDI message/note output. Its context supplies the selected mode and device, SoundFont path, playback stop, and sequence-read error presentation.
- `MidiCcController` owns live MIDI CC polling and control-message calculation. Its context supplies instrument/global control values, sequencer status, and MIDI message output.
- Shared state extracted in phase 2.3 has focused owners: `GUIConstants` for immutable presentation values, `UITheme` for display preferences, `GUIAssets` for image caches, `PlaybackState` for playback runtime, `ApplicationSessionState` for active application/session services, and `SoloMuteState` for cross-instrument solo/mute state.
- Phase 2.4 is complete. Arrangement and score popup ownership, panel preset and arrangement rendering operations, solo/mute actions, knob/combo controls, instrument-panel lifecycle actions, visual-pattern operations, score popup/playhead callbacks, value-popup/range-slider regeneration, score-canvas actions, and MIDI editor operations now use focused contexts or callbacks. No active `VibeComposerGUI` references remain in shared `Components`, `Panels`, or `Popups`; 2.4.17 also removed direct main-window references from `ArrangementGUI`.
- Phase 2.5 is complete. Playback, panel lifecycle, arrangement, solo/mute, generated-result, MIDI device/export/CC, generation randomization, and custom chord generation workflows now live with focused controllers or feature GUIs; `VibeComposerGUI` remains the window composition root and cross-feature coordinator.
- Phase 2.6 is complete. Generator and model classes use supplied part data and focused callbacks for ordering, arrangement maps, sequence-track assignments, MIDI input actions, and config-version defaults. Arrangement part providers are passed only to map operations; `Section` retains no provider or panel snapshot. The compile check passes; remaining direct main-window references are in `SwingUtils`.
- Phase 3.8 moved shared control-value reads and restores into `UIComponentState`; `UndoManager` no longer calls back into `VibeComposerGUI`.
- Phase 2.4.7 removes `VibeComposerGUI`, `ShowPanelBig`, and `ScoreGUI` lookups from `SoloMuter`. Its solo/mute toggle events use a focused context supplied when the window creates global, group, and instrument controls; the window continues to coordinate cross-group updates and score refreshes.
- Phase 2.4.8 removes the main window's `ActionListener` from instrument-panel constructors and replaces the `RandomizePart` command path with a focused callback. The composition root installs it, and `VibeComposerGUI` retains the randomization and auto-regeneration behavior.
- Cross-cutting models and components include `GUIConfig.java`, `Panels/MelodyPanel.java`, and `Components/MelodyMidiDropPane.java`.

## Conventions and boundaries

- Migrate one module at a time. Move its fields and cohesive UI methods, then update references across the source tree with `rg`.
- Keep obsolete members in `VibeComposerGUI` during the migration by prefixing their names with `__`; remove them only after the full migration is ready for cleanup.
- Melody state is currently exposed as static fields on `MelodyGUI` to support existing application-wide callers. Use `MelodyGUI.fieldName` at call sites so ownership stays visible.
- Arrangement state is exposed as static fields on `ArrangementGUI` while callers across the application are being migrated. Keep new arrangement references on `ArrangementGUI`.
- Keep workflows that coordinate multiple instrument types in `VibeComposerGUI`; pass only the shared operations a module needs through its context interface.
- Update component and popup callers when a moved field changes owner. Search the whole `src/main/java` tree, excluding the migration backup when checking active references.
- The first extraction phase is complete. The next phase plan is recorded in `REFACTOR_MAP.md` under **Ownership and Shared State**. Treat static feature fields and broad context APIs as migration scaffolding to reduce gradually; do not add new global state or pass the main window as a general-purpose context.

## Effective commands

Run from the project root (`D:\Production\IdeaProjects\VibeComposer\midimasterpiece`):

Prefer simple file operations through IDEA MCP.

```powershell
rg -n "Melody|melody" src/main/java/org/vibehistorian/vibecomposer/VibeComposerGUI.java
Get-Content src/main/java/org/vibehistorian/vibecomposer/VibeComposerGUI.java | Select-Object -Skip 1700 -First 100
```

`rg -n` is effective for locating scattered references; `Get-Content` with `Select-Object` is useful for reviewing a bounded source section. `mvn -DskipTests compile` successfully checked the MelodyGUI migration without running tests.

# Compilation
mvn -DskipTests compile
