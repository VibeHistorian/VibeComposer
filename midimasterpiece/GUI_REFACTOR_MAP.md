# Original prompt (do not modify): Create a plan to split VibeComposerGUI (careful: >10k lines, won't fit into context window), 
# currently owning a very large amount of organizable chunks of the GUI, into smaller GUI classes split logically (e.g. MelodyGUI, BassGUI, ChordGUI, ArpGUI, DrumGUI, ArrangementGUI, ScoreGUI, ExtraSettingsGUI, GenerationGUI, VibeComposerCoreGUI). 
# The VibeComposerGUI class owns a very large number of fields, both static and non-static, which are typically instantiated once via various init methods and then live for the lifetime of the application (effectively singletons).
# An overall broad goal for the refactoring is to try to eliminate cyclic dependencies, and to make it easier to later translate the application into a different language (e.g. C++) by having smaller, more focused, more independent classes.

# Checkpoint 1 - Proposed Strategy

1. Discovery & Mapping: Identify all fields and methods in VibeComposerGUI and map them to the target sub-classes. Since the file is too large for a single scan, I will use  grep  to find keywords related to each module (e.g., "Melody", "Bass", "Drum").
2. Skeleton Creation: Create the new classes (e.g., MelodyGUI.java, BassGUI.java) with the appropriate package structure. These will initially hold the fields and logic moved from the main class.
3. Incremental Migration: Instead of a "big bang" refactor, I will migrate one module at a time:
   • Move fields to the new class.
   • Update initialization logic in VibeComposerGUI to instantiate the new classes.
   • Replace direct access to moved fields in VibeComposerGUI with calls to the new objects (e.g., melodyGui.someMethod()).
4. Core Extraction: Move shared components, global state, and core utilities into VibeComposerCoreGUI.
5. Verification: Use the build system to ensure the project compiles after each major move.

# Checkpoint 2 - Action Plan

1. - 9. Creation of smaller module GUIs
10. Address VibeComposerCoreGUI:
    *   Identify the remaining "Core" logic (UI framework, shared components like `currentChords`, `midimessage`, `time`, `sequencer` etc.).

***************************************************************

## Mapping Progress
- [x] Melody
- [x] Bass
- [x] Chord
- [x] Arp
- [x] Drum
- [x] Arrangement
- [x] Score
- [x] ExtraSettings
- [x] Generation
- [x] Core cleanup


------------------------------------------------------------------
# Status:
- MelodyGUI migration: complete for melody UI state, settings construction, initial panel setup, melody panel randomization, and seed randomization.
- BassGUI migration: complete for bass panel state, bass tab construction, and random bass panel creation.
- ChordGUI migration: complete for chord UI state, settings construction, chord tab setup, progression controls, custom chord controls, and randomized chord panel creation. Cross-instrument workflows remain in VibeComposerGUI and access chord state through ChordGUI.
- ArpGUI migration: complete for arp UI state, settings and tab construction, and randomized arp panel creation. Shared window operations and cross-instrument access are supplied through its context or remain in VibeComposerGUI.
- DrumGUI migration: complete for drum UI state, generation settings and tab construction, and randomized drum panel creation. Shared window operations and cross-instrument workflows remain in VibeComposerGUI through DrumGUI.Context.
- ArrangementGUI migration: complete for arrangement state ownership, controls and table initialization, action dispatch, table rendering, variation-button creation and recoloring, popup/model helpers, section panel application and selection, actual-arrangement table interactions, and custom MIDI pattern copy-dragging. Instrument panel access, theme colors and icons, config pattern lookup, and MIDI editor creation are supplied through its context.
- ScoreGUI migration: complete for score UI state, score tab and display settings, score rendering initialization, and popup toggling. Playback and MIDI workflows remain in VibeComposerGUI and access score state through ScoreGUI.
- ExtraSettingsGUI migration: complete for extra settings state, settings window construction, and all popup panels, including Generation. Shared window actions are supplied through its context.
- GenerationGUI migration: complete for the main-window randomization and macro panels built by `initRandomButtons` and `initMacroParams`. Cross-instrument actions remain in VibeComposerGUI and are supplied through GenerationGUI.Context.
- Deprecated `__` migration scaffolding cleanup: complete. Removed legacy fields, obsolete method copies, and GUI compatibility sync methods from `VibeComposerGUI`; no active source files reference `__` members. Verified with `mvn -DskipTests compile`.
- Phase 2.1 instrument-control ownership: complete. Melody, bass, chord, arp, and drum GUIs now own their enabled checkbox, group filter slider, add/generate buttons, generation-count field, panel scroll pane, and panel list. Their contexts no longer receive indexed control arrays or instrument-indexed panel callbacks. `VibeComposerGUI` keeps the established 0–4 instrument order behind a typed module accessor, and remaining static callers use the corresponding typed panel-list or scroll-pane accessor. Verified with `mvn -DskipTests compile`.
- Phase 2.2 config transfer ownership: complete. Melody, bass, chord, arp, drum, arrangement, score, generation, and extra-settings modules now save and restore their own `GUIConfig` fields. Instrument modules also own enabled state and part serialization/restoration; the window supplies only the panel recreation callback. `VibeComposerGUI` retains version/session/BPM transfer and explicitly orders validation, transient-state preparation, module-control loading, panel recreation, and derived chord refresh. `GUIConfig` fields and XML shape are unchanged. Verified with `mvn -DskipTests compile`.
- COMPLETED

***************************************************************

# Next Phase Plan - Ownership and Shared State

## Assessment

The feature extraction, shared-state ownership, component decoupling, coordinator-workflow extraction, and generator/model dependency phases are complete. `VibeComposerGUI.java` is now about 4k lines. The earlier estimate of about 7.1k lines and roughly 300 component references below described an intermediate stage and is no longer current; a current source scan finds only two direct references outside `VibeComposerGUI`, both in `SwingUtils`.

Remaining size comes from a mixture of legitimate window composition and cohesive responsibilities that can still have clearer owners: global window-control construction, the detailed compose workflow, preset/config file and view operations, and residual instrument, section, and MIDI helpers. Further extraction should reduce what the window owns while preserving a clear composition root; line count alone is not an acceptance criterion.

`GUIConfig` remains a flat JAXB persistence object to preserve saved preset compatibility. `VibeComposerCoreGUI` is still an empty skeleton and should not become a catch-all for code removed from the window.

## Target boundaries

1. **Instrument GUI ownership:** each instrument GUI owns its enabled checkbox, group filter, add/generate buttons, generation count field, and panel collection. Cross-instrument code uses a small typed API when it truly needs to visit all instruments; feature modules no longer receive arrays and index into them.
2. **Config mapping ownership:** `GUIConfig` remains the persistence DTO and keeps its existing serialized field names. Each feature GUI implements focused `saveToConfig(GUIConfig)` and `loadFromConfig(GUIConfig)` operations for its own settings and parts. `VibeComposerGUI` coordinates the order of those calls and application-wide sequencing.
3. **Shared state ownership:** classify shared values before moving them: immutable constants, user preferences/theme, window metrics, playback runtime, active configuration/history, and application services have different lifetimes and owners. Extract focused owners for these concerns; do not replace `VibeComposerGUI` with one mutable `GlobalState` bag.
4. **Application access:** gradually replace component-level reads and callbacks through `VibeComposerGUI` static fields with narrow injected services or callbacks. Keep cross-feature workflows in a coordinator until a cohesive workflow has a clear owner.
5. **Composition root:** after the above boundaries settle, keep `VibeComposerGUI` responsible for window lifecycle, assembling modules, and coordinating workflows that span modules. Use `VibeComposerCoreGUI` only for actual shared UI composition and helpers.

## Work sequence

### 2.1 Replace instrument-indexed UI arrays

- Add the corresponding control fields to each instrument GUI and create the controls there.
- Replace the five array getters in each instrument `Context` with either no dependency or a narrow callback for the few operations that still belong to the window coordinator.
- Update shared operations such as enable/disable, counts, and configuration mapping to use typed module accessors. Preserve the existing instrument order at public boundaries during this pass to avoid mixing an enum migration into the ownership change.
- Remove the parent-owned arrays after all callers have moved. Keep the instrument modules’ panel lists instance-owned as part of the same boundary, then migrate remaining static callers in a follow-up sweep.

**Done when:** no instrument GUI receives a multi-instrument UI array or chooses its controls with `[0]` through `[4]`; each control has one owning instrument GUI.

### 2.2 Move config transfer beside the controls

- Extract melody, bass, chord, arp, drum, arrangement, score, generation, and extra-settings portions of the two bulk copy methods into their owning modules.
- Keep application-level ordering in `VibeComposerGUI`: validate version, prepare dependent state, load module settings, recreate panels, then run derived-state refreshes. Make ordering explicit because the current load path mixes control values, panel creation, and derived updates.
- Keep the flat `GUIConfig` JAXB model and XML names stable in this phase. Do not put Swing controls or GUI-module instances inside `GUIConfig`.
- Once module operations are complete, reduce the parent methods to orchestration and shared settings that have no feature owner.

**Done when:** each feature module can populate its own portion of a supplied config and restore its own UI from one, while existing preset files retain the same persistence shape.

### 2.3 Extract shared state by lifetime and behavior

Inventory each static field and assign it to one of these categories before changing references:

- **Immutable presentation constants:** colors, default dimensions, and table widths belong in focused constants/theme definitions; keep mutable active theme choices separate.
- **Window and theme preferences:** dark/light mode, monitor mode, dynamic palette, and window metrics belong to a UI preferences/theme owner.
- **Playback runtime:** sequencer, slider position and ranges, current time/section, pause bookkeeping, and MIDI event queues belong with playback control and presentation.
- **Application/session services:** active config/history, MIDI editor session, undo managers, and application callbacks need explicit app-level owners rather than module statics.
- **Feature state:** arrangement, score, and instrument data stays with the feature modules that own it.

Move one category at a time, migrate its callers, then remove its old static field. Prefer instance ownership and constructor/context injection. A temporary forwarding API may keep each step buildable, but it should have a tracked removal point.

### 2.4 Remove direct main-window coupling from shared components

- Start with high-fan-out families (`Components`, `Panels`, and `Popups`) and replace direct `VibeComposerGUI` lookups with the smallest needed dependency: a theme provider, playback access, panel actions, or popup/window services.
- Keep callbacks narrow and behavior-based; do not pass the whole `VibeComposerGUI` or a generic service locator into every component.
- Migrate constructors and callers in groups so each group compiles before continuing.

**Done when:** shared components can be constructed with the dependencies they use and do not need to find the main window through static access.

### 2.5 Re-home remaining coordinator workflows

After ownership and state seams are clear, inspect remaining large method groups in `VibeComposerGUI` and move cohesive workflows such as playback/MIDI lifecycle, instrument-panel management, and arrangement-wide actions to focused controllers or feature modules. Leave genuinely cross-feature decisions in the window coordinator.

### 2.6 Tackle VibeComposerGUI references in non-UI (model) classes

Generator and model classes may still be referencing VibeComposerGUI out of convenience - these should be decoupled while preserving the functionality (e.g. midi generation feeding back the sequencer track numbers which should be set/enabled for the main sequencer for solo/mute purposes).

## Phase completion criteria

- Instrument GUIs own their controls and panel state without indexed control arrays.
- Each GUI feature saves and restores its own config fields; `GUIConfig` keeps its existing persisted format.
- Mutable theme, playback, and session state have explicit owners and are not stored as unrelated static fields on `VibeComposerGUI`.
- Shared components use narrow dependencies instead of locating the main window globally.
- `VibeComposerGUI` is primarily the window composition root and cross-feature coordinator; `VibeComposerCoreGUI` has only a defined shared-UI responsibility.
- Compile after each work sequence and check representative preset load/save flows before removing any compatibility forwarding API.

## Recommended first implementation slice

Start with **2.1**, limited to the five shared instrument-control arrays. It is a concrete ownership leak repeated across all five instrument GUIs, can be migrated without changing the persisted config format, and establishes the typed access pattern needed by 2.2. Then move config mappings feature-by-feature, beginning with one module and retaining the same load/save behavior before broadening the change.

## Phase 2.3 Progress — Shared State by Lifetime and Behavior

### Static field inventory

The remaining static fields on `VibeComposerGUI` are classified here before ownership changes. This inventory excludes static methods and fields owned by the already extracted feature GUIs.

- **Immutable presentation constants:** `COMPOSE_COLOR`, `COMPOSE_COLOR_TEXT`, `COMPOSE_COLOR_TEXT_LIGHT`, `REGENERATE_COLOR_TEXT`, `REGENERATE_COLOR_TEXT_LIGHT`, `DEFAULT_WIDTH`, `DEFAULT_HEIGHT`, and `TABLE_COLUMN_MIN_WIDTH`. Moved to `GUIConstants`.
- **Window and theme preferences:** `panelColorHigh`, `panelColorLow`, `isBigMonitorMode`, `isDarkMode`, `isFullMode`, `darkModeUIColor`, `lightModeUIColor`, `toggledUIColor`, `toggledComposeColor`, `toggledRegenerateColor`, `scrollPaneDimension`, and `toggleableComponents`. Moved to `UITheme`; immutable colors and dimensions stay in `GUIConstants`.
- **Shared UI assets:** `SECTION_VARIATIONS_ICONS`, `SECTION_VAR_ICON_NAMES`, `SECTION_TRANSITION_ICONS`, `SECTION_TRANSITION_ICON_NAMES`, `LOCK_COMPONENT_ICONS`, and `LOCK_COMPONENT_ICON_NAMES`. Moved to `GUIAssets`, which loads and caches these resources.
- **Playback runtime:** `sequencer`, `midiEventsToRemove`, `currentMidi`, `currentSequenceMidi`, `partAndOrderLastNoteIndexes`, `loopBeat`, `sliderPanel`, `slider`, `sliderExtended`, `sliderMeasureStartTimes`, `sliderBeatStartTimes`, `currentTime`, `currentSectionIndex`, `sectionText`, `isDragging`, `pauseInfoResettable`, `pausedBpm`, `pausedSliderPosition`, `pausedMeasureCounter`, `startBpm`, `startSliderPosition`, `startBeatCounter`, `currentBeatMultiplier`, and `lastPlayedMs`. Moved to `PlaybackState`, including `getNextNoteIndex`.
- **Application/session services:** `defaultGuiPreset`, `currentMidiEditorPopup`, `currentMidiEditorSectionIndex`, `soundfont`, `melodyGen`, `guiConfig`, `configHistory`, `heavyBackgroundTasksInProgress`, `originalOut`, `originalErr`, `dummyOut`, `actionUndoManager`, `instrumentTabUndoManager`, and `dconsole`. Moved to `ApplicationSessionState`.
- **Feature or cross-feature state:** `scaleMode`, `loopBeatCount`, `mainBpm`, `randomSeed`, `lastRandomSeed`, and `regenerateWhenValuesChange` now belong to `GenerationGUI`, including `getCurrentSeed`; `globalSoloMuter`, `groupSoloMuters`, `needToRecalculateSoloMuters`, `needToRecalculateSoloMutersAfterSequenceGenerated`, `cpSm`, `apSm`, and `dpSm` belong to `SoloMuteState`.
- **Composition root and class metadata:** `instrumentTabPane`, `vibeComposerGUI`, and `constraints` remain with `VibeComposerGUI` for window composition and construction. `CURRENT_VERSION` is application metadata; `serialVersionUID` is serialization metadata.

### Completed slices

- **2.3.1 Immutable presentation constants:** added `GUIConstants` for shared compose/regenerate colors, default dimensions, and minimum table column width. Updated active callers and removed these fields from `VibeComposerGUI`; values and behavior are unchanged.
- **2.3.2 Theme and display preferences:** added `UITheme`, moved mutable palette and display settings with their palette helpers, and migrated active callers.
- **2.3.3 Shared image assets:** added `GUIAssets` for the section and lock image caches, source names, and loading.
- **2.3.4 Playback runtime:** added `PlaybackState` for sequencer, MIDI files, playhead display, timing markers, pause bookkeeping, and note indexing.
- **2.3.5 Application/session services:** added `ApplicationSessionState` for active config/history, preset/editor session, soundbank, undo managers, console, output streams, and background-task status.
- **2.3.6 Feature and cross-feature state:** moved macro and seed controls to `GenerationGUI`, and shared solo/mute state to `SoloMuteState`.
- **2.3 context ownership cleanup:** removed context accessors that only relayed theme, playback, session, generation, chord, or instrument-panel state owned by another module. Call sites now read that owner directly; contexts retain window operations and cross-feature workflows.

### Phase 2.3 status — complete

Every mutable `VibeComposerGUI` static field now has a focused owner or a defined composition-root role. Active callers have been migrated without forwarding fields on `VibeComposerGUI`. Owner fields remain static to preserve the application's existing single-window lifetime; replacing component-level static access with injected dependencies is the next boundary in phase 2.4.

## Phase 2.4 Progress — Shared Component Dependencies

- **2.4.1 Arrangement section selector:** replaced its lookups of `ArrangementGUI`, `VibeComposerGUI`, and their static state with callbacks for section selection, variation popup opening, and playback start adjustment.
- **2.4.2 Playhead and score popup ownership:** `PlayheadRangeSlider` receives its `JTabbedPane`, and `ShowScorePopup` receives its parent component through `ScoreGUI.Context`.
- **2.4.3 Arrangement popup panel data:** part inclusion, variation, and custom-section popups receive instrument panel lists from their caller. Removed the unused no-argument `DrumLoopPopup` path and a main-window location log from `TemporaryInfoPopup`.
- **2.4.4 Part preset management:** `PartManagerPanel` receives save, load, and count-recalculation operations through a focused context supplied by the instrument GUIs.
- **2.4.5 Arrangement rendering:** `CollectionCellRenderer` receives panel-list and absolute-order lookups from `ArrangementGUI.Context`.
- **2.4.6 Score playback actions:** `ShowPanelBig` receives slider and pause callbacks through `ScoreGUI.Context`; `ScoreGUI.pianoRoll()` is now instance-owned.
- **2.4.7 Solo/mute controls:** `SoloMuter` sends solo and mute toggle events through an injected context. The window coordinates group-wide state changes, sequence recalculation flags, and score refreshes; `SoloMuter` no longer looks up `VibeComposerGUI`, `ShowPanelBig`, or `ScoreGUI` statically. Instrument-panel construction supplies this context to single, group, and global controls.
- **2.4.8 Instrument-panel randomization:** removed the main-window `ActionListener` dependency from `InstPanel` and its subclasses, and replaced the `RandomizePart` command dispatch with a focused panel callback. The window installs the callback after construction and retains the existing randomize and auto-regenerate behavior.
- **2.4.9 Knob and combo controls:** `JKnob`, `ScrollComboBox2`, and `ScrollComboPanel` receive affected-panel and regeneration operations through `InstrumentControlContext`; instrument-panel construction supplies the context.
- **2.4.10 Instrument-panel lifecycle:** `InstPanel` receives copy, removal, panel-order, and peer-list operations through its focused `Context`. The window remains the lifecycle coordinator.
- **2.4.11 Visual pattern controls:** `VisualPatternPanel` receives panel-list and regeneration operations through `InstrumentControlContext`, supplied by its owning `InstPanel`.
- **2.4.12 Score popup and playhead controls:** `ShowScorePopup` receives its popup sizing mode and a close callback; score restoration stays in `ScoreGUI`. `PlayheadRangeSlider` receives score repaint and note-highlight operations.
- **2.4.13 Value popups and range sliders:** `KnobValuePopup` and the range-slider UI receive regeneration operations through `InstrumentControlContext`.
- **2.4.14 Score canvas actions:** `ShowAreaBig` delegates MIDI editor opening, panel selection and solo/mute changes through score actions. `ShowPanelBig` delegates score repainting instead of locating `ScoreGUI`.
- **2.4.15 MIDI editor actions:** `MidiEditPopup` and `MidiEditArea` receive panel, transpose, playback, chord-duration, regeneration, and arrangement refresh operations through a focused editor context.
- **2.4.16 Score canvas ownership:** `ShowAreaBig` and `ShowRulerBig` read changing score dimensions, timing, and controls from their owning `ShowPanelBig` instance.
- **2.4.17 ArrangementGUI main-window calls:** replaced direct `VibeComposerGUI` lookups for panel lists, panes, custom-section panel creation, absolute panel order, arrangement layout insertion, and variation-popup geometry with focused `ArrangementGUI.Context` operations.
- **2.4.18 MIDI editor chord durations:** moved user chord-duration parsing to `ChordGUI` and gave each `MidiEditArea` an explicit duration-provider context, removing its duration lookup dependency on `MidiEditPopup` and `VibeComposerGUI`.

### Phase 2.4 status — complete

No active `VibeComposerGUI` references remain in `Components`, `Panels`, or `Popups`, and `ArrangementGUI` no longer references the main window directly. Shared controls and editors receive the window operations they use through focused contexts or callbacks.

- Verification: `mvn -DskipTests compile` succeeds after 2.4.17. Tests were skipped.

## Phase 2.5 Progress — Re-home Coordinator Workflows

- **2.5.1 Playback transport:** added `PlaybackController` for MIDI play, pause, stop, seek, saved playhead position, sequencer position reset, and pending MIDI event cleanup. The window supplies the MIDI CC thread operation and focused reads for start-from-bar, current BPM, and generated chord availability. Playback buttons, score callbacks, slider actions, and compose/regenerate paths now call the controller directly; the matching transport methods were removed from `VibeComposerGUI`.
- **2.5.2 Instrument-panel workflows:** added `InstrumentPanelController` for panel creation and insertion, removal, restoration from saved parts, panel ordering, custom-section presentation, arrangement part-map refreshes, and add/generate/randomize panel dispatch. `VibeComposerGUI` supplies the panel factory and instrument-specific randomization operation plus focused layout/count/repaint operations; feature GUIs now use the controller through their existing panel contexts.
- **2.5.3 Arrangement workflows:** moved section recomposition, replacement from config history, compose-time custom-part cleanup and arrangement randomization, manual-arrangement override selection, and post-generation table/selection refresh into `ArrangementGUI`. The module requests only cross-feature panel randomization, panel counts, config-history access, count/solo refresh, and optional playback regeneration through its context.
- **2.5.4 Solo/mute track lifecycle:** moved sequencer track solo/mute reset and restoration around generation, plus the “Exclude Not Solo'd” panel action, into `SoloMuteController`. The composition root supplies only instrument panel lists and enabled state; sequencer access comes from `PlaybackState`.
- **2.5.5 Generated-result synchronization:** moved generated chord display/custom-chord updates to `ChordGUI`, melody target-note updates to `MelodyGUI`, random arp-pattern updates to `ArpGUI`, and generated-arrangement copying to `ArrangementGUI`. `VibeComposerGUI` now sequences those module operations before refreshing the score and open MIDI editor.
- **2.5.6 Solo/mute group actions:** moved group/global solo and mute toggling, panel-state reconciliation, readiness checks, and single-solo detection into `SoloMuteController`. The window delegates toggle events and supplies score refresh callbacks; the controller continues to own sequencer track application.
- **2.5.7 MIDI device and synthesizer lifecycle:** added `MidiDeviceController` to own the active MIDI device and synthesizer, SoundFont loading, sequencer output connection and cleanup, direct MIDI message/note output, and endpoint preparation for compose and WAV export. `VibeComposerGUI` keeps mode controls and generated-sequence orchestration, delegating endpoint operations through a focused context.
- **2.5.8 WAV sequence rendering:** added `MidiExportController` for rendering the current MIDI sequence into WAV audio, including event timing, CC setup, SoundFont preparation, and audio stream writing. `VibeComposerGUI` keeps the export button, background worker, output filename, and playback restoration.
- **2.5.9 MIDI control output:** added `MidiCcController` for the live MIDI CC polling thread, instrument/global control reads, MIDI CC value calculation, and message dispatch. Playback and WAV export call it through focused callbacks; the window supplies UI values and routes messages through `MidiDeviceController`.
- **2.5.10 Generation operations:** moved panel transpose randomization, sidechain pattern application, global swing application, BPM randomization, and instrument randomization into `GenerationGUI`. Its context now provides selected-tab and panel access plus regeneration operations; compose-time sidechaining and randomization dispatch directly to the module. Random transpose selection also retains at least one available choice for large panel groups.
- **2.5.11 Custom chord generation:** moved randomized user-chord generation and chord-selection filtering from `VibeComposerGUI` into `ChordGUI`. The window still refreshes the active config through the module context before generation.
- **Verification:** `mvn -DskipTests compile` succeeds after 2.5.11. Tests were skipped.

### Phase 2.5 status — complete

Playback transport, instrument-panel lifecycle, arrangement section actions, solo/mute actions and track application, feature-owned generated-result updates, MIDI device/synthesizer lifecycle, WAV rendering, live MIDI control output, generation operations, and custom chord generation are re-homed. `VibeComposerGUI` retains window composition, config serialization, the high-level compose sequence, and workflows that coordinate multiple features. Phase 2.6 can now address `VibeComposerGUI` references in generator and model classes.

## Phase 2.6 Progress — Generator and Model Dependencies

- `MidiGenerator` now derives absolute part order from the supplied `GUIConfig`. It reports generated sequence-track assignments through `SequenceTrackAssigner`; the composition root applies those assignments to instrument panels, while offline chord generation uses a no-op default.
- `InstPart.getAbsoluteOrder` now takes the peer parts it indexes. `Arrangement` and `Section` use supplied `InstPart` collections for panel-order mapping, presence generation, and map resizing instead of reading the main window. Section map operations receive the part provider only for the operation; `Section` retains no callback or panel snapshot.
- `PatternMap.checkMapBounds` accepts the active part provider. `GUIConfig` takes its default version from `Constants.APP_VERSION`, removing its dependency on the window class. The part-inclusion column names now belong to `Arrangement`, not its popup.
- `MidiHandler` receives a small input-action context for BPM changes and note playback. The logging helper now names its own logger; stale main-window imports were removed from table/icon helpers.
- **Verification:** `mvn -DskipTests compile` succeeds after 2.6. Tests were skipped. The active model/generator classes in this slice no longer reference `VibeComposerGUI`; the remaining direct references outside the window are in `SwingUtils` and `UndoManager`, which are UI helpers.

### Phase 2.6 status — complete

Generation, arrangement models, part models, persistence defaults, pattern-map bounds, and MIDI input dispatch now use supplied data or focused callbacks instead of looking up the main window. Sequence track assignments still reach instrument panels through the composition root, preserving solo/mute track bookkeeping.

--------------------------------------------------

### Phase 3 - functional refactoring, not just moving

Until now, most of the refactoring work was about moving GUI code from the god class to more relevant owners, and giving classes context so they don't have to back-reference the god class.
In this phase, the goal is to identify and address functionality/responsibilities which isn't really about coordinating and can be split out (already alluded to by some steps in 2.5 which created new non-GUI controllers), 
and functionality which can be simplified (e.g. less passing of parameters, or rewriting logic/event handling to do the same work in less code).
The app should still behave the same, but the internals of how that is achieved may be rewritten.

Phase slices should be focused, to make review of logical modifications easy to confirm. 

## Phase 3 Progress — Functional Simplification

- **3.1 Solo/mute reset API:** removed `resetButtons` parameters from group and all-track solo/mute reset operations. Every active call passed `true`, so the parameter described an unused alternate behavior; the operations now express the behavior they already performed without that dead choice.
- **3.2 Solo/mute toggle API:** removed the `recalculate` parameter from solo/mute toggle controls and their callbacks. Every active toggle recalculated sequencer solo/mute state and refreshed the score, so those steps are now unconditional parts of the toggle operation.
- **3.3 Playback seek clamping:** replaced the nested negative-time check in `PlaybackController.midiNavigate` with a zero lower bound via `Math.max`, preserving the accepted seek condition and resulting position.
- **3.4 Playback seek guard:** changed the boolean OR in the seek condition to short-circuit `||`. The duration check now runs only when the earlier sub-tick condition does not already allow the seek.
- **3.5 Playback position indexing:** consolidated the duplicate pause and start-position scans into one segment-index helper, keeping their 50 ms boundary and zero fallback unchanged.
- **3.6 Playback restart flow:** captured the sequencer's running state before stopping it and moved the shared non-replay stop ahead of the branch. The resume/reset branch still uses the pre-stop state.
- **3.7 Playback position reset:** consolidated the two identical navigation calls after the end-of-sequence check; the slider still resets to zero at its maximum before navigation.
- **3.8 GUI value state ownership:** moved supported control value reads and restores from `VibeComposerGUI` to `UIComponentState`. Preset save/load and undo history use the shared helper; tab selection history suppression now sits in `UndoManager`.
- **3.9 Instrument part workflow ownership:** moved locked-panel filtering for part saves and imported-part merging into `InstrumentPanelController`. The window retains XML file loading and presents the existing custom-section restriction message.
- **3.10 Instrument panel lookup ownership:** moved instrument panel list and scroll-pane access, plus custom-section-aware affected-panel selection, into `InstrumentPanelController`. The window's static accessors delegate to the controller for compatibility; the controller context supplies the instrument-owned list and pane directly.
- **3.11 Instrument enum foundation:** added `INST` with stable 0–4 indices and `fromIndex` conversion. `InstrumentPanelController` now accepts `INST` for panel creation, generation, access, save/restore, and randomization workflows. Instrument GUIs and arrangement workflows call the controller directly with enum values; window accessors retain integer entry points where older callers still need them.
- **3.12 Instrument-index migration:** replaced fixed five-item loops with `INST.values()` iteration in arrangement UI/model operations, section maps, solo/mute handling, score part controls, instrument-tab setup, pattern maps, and inclusion-map persistence. GUI config and section instrument dispatch, plus MIDI editor drum/chord checks, now map indices through `INST.fromIndex`. Integer keys remain at model and serialization boundaries.
- **3.13 Instrument-typed context APIs:** changed context callbacks for arrangement operations, instrument controls, solo/mute, MIDI editing and input, MIDI CC, score playback, visual pattern controls, and part presets to accept `INST`. Score and MIDI data, section maps, panel factories, and persistence convert to the enum's stable index only at their boundaries. MIDI CC still skips the drum group in its general panel loop.
- **3.14 Part-preset storage:** moved part-preset file discovery, XML part counting, JAXB serialization/deserialization, and preset path ownership into PartPresetStore. InstrumentPanelController retains panel-to-part conversion and imported-part merging; the window retains the custom-section restriction message and count refresh.
- **3.15 Action-command predicate cleanup:** cached the command once in `actionPerformedTask`, changed action checks from reference identity to string-value equality, and used short-circuit `&&` for the compose-time strum option. Independent checks, ordering, and post-action recalculation/regeneration behavior remain intact.
- **3.16 Melody compose-preparation ownership:** moved compose-time melody panel generation and melody seed, pattern, target-note, and forced-pattern preparation into `MelodyGUI`. The window calls the melody operations at their original points in `prepareUI`, preserving their order around timing and sidechain preparation. The prepared `lastRandomSeed` supplies the same seed value previously read through `getCurrentSeed()`.
- **Verification:** `mvn -DskipTests compile` succeeds after 3.14. Tests were skipped.
- **Verification:** `mvn -DskipTests compile` succeeds after 3.15 and 3.16. Tests were skipped; compose/regenerate scenarios were not manually exercised.

### Phase 3 status

Phase 3 has started. Continue with small behavior-preserving changes that simplify workflows or reduce unnecessary responsibilities and API surface. Keep each slice documented separately.

--------------------------------------------------

## Phase 4 — Remaining Window Responsibilities

Phase 4 reduces `VibeComposerGUI` further by assigning remaining cohesive work to focused owners. Keep the class responsible for `JFrame` lifecycle, assembling feature modules and their contexts, and sequencing cross-feature operations. A target near 2k lines is a useful direction, not a completion requirement; stop when ownership is clear and the remaining code is genuinely window composition or coordination.

### Candidate work sequence

- **4.1 Main-window controls and layout:** inspect construction and listener wiring in `init`, `initTitles`, `initSoloMutersAndTrackControl`, `initControlPanel`, and `initPlayPanel`. Move coherent groups of global controls, layout rules, and their local event handling into a focused main-window UI builder or controls class. Leave top-level window lifecycle and module assembly in `VibeComposerGUI`; do not use `VibeComposerCoreGUI` as a general-purpose destination.
- **4.2 Compose workflow:** move detailed compose preparation, parameter filling, UI state changes, generation dispatch, cleanup, and generated-result handling from `composeMidi` and its helper methods into a `ComposeCoordinator` or similarly focused workflow owner. Keep the operation order explicit because it spans feature GUIs, playback, solo/mute, MIDI devices, and arrangement state. `VibeComposerGUI` should retain short entry points and supply narrow window callbacks.
- **4.3 Preset and view workflows:** separate preset/config file operations, current-view snapshotting, and load/save presentation from window construction. Keep feature-owned config mappings in their current feature GUIs and preserve the existing `GUIConfig` XML shape. The window should retain only application-level restore ordering and callbacks that require the live window.
- **4.4 Residual instrument, section, and MIDI helpers:** assign remaining panel-to-part conversion and panel-order utilities to `InstrumentPanelController`; section/playhead calculations to arrangement or playback owners; and note audition/output helpers to the appropriate MIDI owner. Migrate callers from `VibeComposerGUI` forwarding/static methods as ownership moves, retaining compatibility methods only while they have active callers.
- **4.5 Review remaining dispatch and refresh code:** after the larger ownership moves, inspect action-command dispatch, tab/count refresh, background UI updates, and appearance switching. Extract only cohesive behavior with a clear owner; short cross-module sequencing and direct `JFrame` changes may remain in the window.

### Phase 4.1 status — complete

- Moved the shared header, global audio controls, solo/mute and track-history row, compose controls, and playback/save row from `VibeComposerGUI` into `MainWindowControls`. The window still assembles these panels in `init()` and supplies explicit callbacks for preset/history, arrangement, playback, MIDI-device, save, and export actions.
- Moved ownership of the corresponding controls and their colors into `MainWindowControls`; window workflows now access those controls through its focused getters. `GenerationGUI` and `PlaybackState` retain ownership of their established generation and playback state.
- Verification: `mvn -DskipTests compile` succeeds. Tests were skipped; the application was not manually launched.

### Phase 4.2 status — complete

- Added `ComposeCoordinator` to own compose/regenerate sequencing, seed and UI preparation, generator parameter setup, generation dispatch, config-history and seed-history updates, feature-generated results, and MIDI playback/slider setup.
- `VibeComposerGUI` retains the public compose entry point and supplies focused callbacks for config transfer, sequence-track assignment, selected tab, slider timing, repaint, and tab-count refresh.
- Verification: `mvn -DskipTests compile` succeeds. Tests were skipped; compose/regenerate scenarios were not manually exercised.

### Phase 4.3 status — complete

- Added `PresetViewController` for named preset loading and saving, config-file selection, saved MIDI/config sidecars, ordered current-view snapshot/restore values, and JAXB config/preset serialization. Existing preset naming, popup messages, file paths, and XML model classes remain unchanged.
- `VibeComposerGUI` retains feature config mapping and application-level restore order, including module settings, panel recreation, and display-mode updates. The config-file chooser delegates the parsed config back to the window for that same restore sequence.
- Verification: `mvn -DskipTests compile` succeeds. Tests were skipped; preset/config load and save flows were not manually exercised.

### Phase 4.4 status — complete

- Moved panel lookup by instrument/order and panel-to-part conversion into `InstrumentPanelController`. Arrangement map creation and custom-section panel snapshots now use those controller operations.
- Moved custom-section detection, section measure-start lookup, and section key-change lookup into `ArrangementGUI`. Moved playhead range updates into `PlaybackController` and connected arrangement section selection directly to those owners.
- Added `MidiAuditionController` for keyboard and MIDI-editor note selection, transposition, and audition dispatch through `MidiDeviceController`. MIDI handler and editor callbacks now call it directly.
- Removed the corresponding `VibeComposerGUI` forwarding methods for panel lookup, custom-section detection, regeneration eligibility, section/playhead helpers, and note audition. Remaining instrument-list and affected-panel accessors retain active callers.
- After assembling the main window, wired its `KnobPanel` controls to the shared regenerate-on-change context. Detached knobs retain their disabled regeneration behavior.
- Verification: `mvn -DskipTests compile` succeeds. Tests were skipped; keyboard, MIDI input, and editor audition were not manually exercised.

### Phase 4.5 status — complete

- Added `AppearanceController` for look-and-feel installation, shared Swing defaults, and recoloring controls owned across feature GUIs. `VibeComposerGUI` supplies the live window components and retains direct display-size and `JFrame` changes.
- Routed playhead slider and playback status-label updates from the polling thread through the Swing event queue. Pending slider updates coalesce to the latest sequencer position, while the polling cadence and distinction between score-refreshing and raw slider updates remain intact.
- Reviewed action-command dispatch and tab/count refresh. They remain in `VibeComposerGUI` because dispatch preserves cross-feature action ordering and the refresh is a short composition-root update spanning instrument and arrangement tabs.
- Verification: `mvn -DskipTests compile` succeeds. Tests were skipped; appearance switching and playback UI updates were not manually exercised.

### Phase 4 completion criteria

- `VibeComposerGUI` primarily owns the window lifecycle, module/context assembly, and short cross-feature sequencing calls.
- Compose, preset/view, and global-control work have focused owners with narrow dependencies; feature GUI config ownership and the persisted XML format remain intact.
- Callers use the owner of instrument, section, playback, and MIDI behavior rather than routing through compatibility methods on `VibeComposerGUI`.
- The `VibeComposerCoreGUI` class has a defined shared-UI responsibility before receiving code.
- Each slice preserves behavior and is reviewed independently. Any compile or runtime verification is recorded with the slice; a particular source-line target is not required for completion.

--------------------------------------------------

## Phase 5 — Context, Static Access, and Structural Smells

Phase 5 reviews the dependency seams left after feature extraction and workflow ownership. Its goal is to make lifetime and ownership explicit, shrink context APIs where callers currently receive more access than they need, and record broader structural smells as actionable candidates. Do not convert the existing static state into a new catch-all context or service locator.

### Initial audit

- At the initial audit, mutable controls in several feature GUIs were static. Phase 5.4 has since made `ScoreGUI`, `DrumGUI`, `ArpGUI`, `MelodyGUI`, `ChordGUI`, and `GenerationGUI` controls instance-owned, along with the shared instrument-panel controls. Substantial mutable UI and interaction state remains static in `ArrangementGUI` and `ExtraSettingsGUI`, so those owners still limit isolated window construction.
- Arrangement popup consumers have been migrated, while renderers and other callers still read `ArrangementGUI` static state. `MelodyPanel` and `MelodyMidiDropPane` now receive narrow callbacks for melody behavior, and imported phrase state belongs to the active `MelodyGUI` instance (5.4.11).
- Most reviewed `Context` interfaces are already small. The clearest review candidates were `ArrangementGUI.Context`, which mixed UI composition, popup access, playback, config history, and recomposition, and `InstrumentPanelController.Context`, which mixed panel creation, presentation, randomization, and refresh notifications. Phase 5.3 traces their current call sites before deciding whether any remaining operations need separate interfaces.
- The shared `PartManagerPanel.Context` is a focused four-operation preset boundary. Keep this as the shape to prefer: give a component only the operations it performs, and pass an owner or collaborator directly when it only needs that collaborator's API.
- `ApplicationSessionState` is a static holder for several unrelated lifetimes (active config/history, MIDI editor session, soundbank, generator, undo managers, console/streams, and background-task flag). The fields have an owner class, but the class still acts as a global access point. Separate app services from window/session/UI state as callers are migrated.
- Mutable interaction and view state also appears in component statics, including score/editor view controls in `ShowPanelBig` and interaction state in `ScrollComboPanel`. Decide scope from actual lifetime and sharing needs; do not preserve static scope just because there is one current window.
- `VibeComposerGUI` is now about 2.2k lines in the active module. It has moved from a god class toward a composition root, but public static forwarding helpers and broad static imports still obscure which owner a caller depends on. Retain the class for window lifecycle and cross-feature ordering; move a helper only when it has a distinct behavior owner.

This is an initial structural audit, not a complete field-by-field classification of all static members in the repository. Extend the inventory as each slice reaches its callers.

### Static and lifetime policy

Use `static` for immutable constants, pure/stateless utility methods, enum/factory helpers, and shared immutable data. A mutable process-wide cache may remain static only when its synchronization, invalidation, and lifetime are explicit. Mutable application services may be application-scoped objects assembled once by the composition root; they do not need static fields to remain shared for the application lifetime.

Treat mutable Swing controls, window references, editor view state, drag/selection state, and feature runtime state as instance-owned by their window, editor, or feature owner. Keep genuinely application-wide runtime state (for example, a single active MIDI output lifecycle, if the product continues to guarantee one) behind an explicit application service and inject only the operations a client uses. Revisit this decision if the application later supports multiple windows or concurrent editor sessions.

### Candidate work sequence

- **5.1 Build the ownership/lifetime inventory:** classify mutable statics in feature GUIs, `ApplicationSessionState`, `PlaybackState`, `SoloMuteState`, and UI components as immutable/global service, application session, window, feature, editor, or transient interaction state. Record current owner, actual readers/writers, desired owner, and migration seam. Resolve ambiguous sharing from call sites before changing scope.
- **5.2 Remove concrete feature-GUI lookups from shared UI:** start with arrangement popup/rendering consumers and melody panel/drop-pane consumers. Give each popup, renderer, or panel a narrow model snapshot, feature owner, or callback for the operations it uses. Migrate a caller family at a time, then make the associated feature fields instance-owned when no global callers remain.
- **5.3 Reduce oversized contexts and relays:** trace construction and call sites for `ArrangementGUI.Context` and `InstrumentPanelController.Context`. Remove unused methods and callbacks that merely forward to another owner. Group dependencies by actual behavior; split a context only when it has clients with distinct needs or when doing so removes a real dependency cycle. Prefer direct collaborators for read-only owned data and callbacks for actions.
- **5.4 Replace static feature access with composition-root instances:** have `VibeComposerGUI` own feature GUI instances and pass them or narrow collaborators to dependent components. Migrate in bounded feature groups; keep compatibility accessors temporarily only with an explicit removal checklist. Avoid an application-wide locator.
- **5.5 Review application state owners:** separate unrelated `ApplicationSessionState` concerns by lifetime and behavior. Keep persistence DTOs (`GUIConfig`) separate from live GUI/session state. Decide whether each playback, MIDI, undo, and editor service is truly application-wide or belongs to one window/session; encode the choice in construction and ownership rather than comments alone.
- **5.6 Record and address structural smells incrementally:** use the findings below as a backlog, add concrete locations and caller evidence as they are confirmed, and only refactor a smell in a behavior-focused slice.

### Structural smell backlog

- **Global mutable UI state:** feature classes own their controls semantically but expose many of them as public statics. This hides construction order, couples multiple editors/windows, and makes tests share state. Priority: high; address alongside feature-GUI instance migration.
- **Concrete-owner coupling in UI leaves:** shared components and popups depend on `ArrangementGUI`/`MelodyGUI` statics. This preserves cycles at a different layer after removing direct window dependencies. Priority: high; migrate by caller family.
- **Context interface breadth and callback forwarding:** the two broad contexts named above mix state queries, UI construction, actions, and refresh effects. Priority: medium; prune first, split only where call-site evidence supports it.
- **Global holder with mixed lifetimes:** `ApplicationSessionState` groups editor, config, audio, undo, diagnostics, and background-task values. Priority: medium; split along actual lifecycle and injection seams.
- **Composition-root facade residue:** public static forwarding methods, wildcard static imports, and direct reads from state holders make dependencies hard to see in `VibeComposerGUI` and controllers. Priority: medium; prefer explicit imports and owner APIs when touching call sites, then remove forwarding methods with no remaining callers.
- **Controller boundary drift:** controllers should own one workflow and accept dependencies at the edge. Watch for controllers that combine domain decisions, Swing presentation, and a long list of unrelated callbacks; do not split solely by file size.
- **Oversized UI/model classes and mixed responsibilities:** continue noting concrete class/method clusters (for example, arrangement table/popup behavior versus arrangement state, and MIDI editor rendering versus editing/input dispatch) before proposing extraction. Avoid line-count-only splits.
- **Mutable static collections/constants:** declarations marked `static final` may still expose mutable arrays, maps, or lists. Treat mutability and safe publication separately from whether the reference is final; prefer immutable views/data where callers do not need mutation.

### Phase 5 progress — in progress

- **5.1 Initial lifetime records:** `MelodyGUI` control fields and imported melody/candidate phrases are feature/window state owned by the active GUI instance (5.4.5 and 5.4.11). `ArpGUI` controls were in the same category and are now instance-owned (5.4.4). `PlaybackState.sequencer` and MIDI output state are application-scoped under the current single-active-output design, while its slider, time labels, section selection, and drag flag are window/playback interaction state. `SoloMuteState` is window interaction state. This is an initial confirmed subset, not a complete declaration and caller inventory; `ApplicationSessionState` and the remaining feature/component statics still need field-by-field review.
- **5.2 Melody component caller family:** removed direct `MelodyGUI` lookups from `MelodyPanel` and `MelodyMidiDropPane`. `MelodyGUI` supplies note-target generation to each panel and scale-mode option updates to its drop pane through narrow callbacks; `VibeComposerGUI` routes melody panel construction through the owning `MelodyGUI`. The panel and drop pane no longer import the feature GUI. Melody controls and imported phrase state follow the owning GUI instance (5.4.5 and 5.4.11). Arrangement renderer consumers remain to be migrated.
- **5.2 Arrangement popup caller family:** `VariationPopup` now receives focused actions for manual mode, variation updates, model refresh, and popup lifecycle; its variation table model receives suppliers for current global variation data and the actual-arrangement table it uses. `ApplyCustomSectionPopup` receives the current section names and a supplier for the selected section from `ArrangementGUI`, plus a local action callback. These popup classes and `VariationsBooleanTableModel` no longer read `ArrangementGUI` static fields directly. Arrangement rendering components and other arrangement callers remain for later slices. No build or tests were run for this slice.
- **5.3 Context audit and trimming:** moved configured-panel creation out of `InstrumentPanelController.Context` into a dedicated factory, and folded combo-arrow cleanup into that factory because it only applies during panel construction. Moved the current-seed read to an `IntSupplier`, leaving the controller context with eight lifecycle and UI effects. `ArrangementGUI` now receives `PlaybackController` directly for slider positioning, removing that relay from its context. The remaining eleven arrangement callbacks are all used and serve its button/layout integration, popup placement, config-history read, or cross-feature actions. Both contexts have a single client, and their remaining lifecycle effects are distinct, so splitting them would add interfaces without separating real clients or removing a cycle. Reviewed constructor and method call sites with `rg`; no build or tests were run for this slice.
- **5.4.1 Arrangement GUI instance relay:** removed the static `ArrangementGUI.arrangementGUI` self-reference and made the variation-popup reference instance-owned. `VibeComposerGUI` now calls its owned `arrangementGUI` instance; arrangement popups receive action/display callbacks, and section dropdowns receive an add-section callback. `Arrangement.resortByIndexes` now receives its recolor action from `ArrangementGUI`, removing the model-to-GUI singleton lookup. No compatibility accessor remains for these references. Other mutable `ArrangementGUI` statics remain for later caller-family migrations. Verified with `mvn -DskipTests compile`; tests were skipped.
- **5.4.2 Score GUI instance ownership:** made all seven mutable `ScoreGUI` controls and its popup instance fields, and converted score config transfer and display-settings setup to instance methods. `VibeComposerGUI` passes the owner to score-dependent controllers; `MainWindowControls`, `PresetViewController`, `ComposeCoordinator`, and `MidiAuditionController` use its focused API. Melody and chord actions update score transpose through their contexts, and `MidiUtils.processRawChords` receives a transpose callback rather than looking up score controls. Verified with `mvn -DskipTests compile`; tests were skipped.
- **5.4.3 Drum GUI instance ownership:** made `DrumGUI`'s mutable controls instance fields, retaining only its shared drum-number lists as static constants. `ExtraSettingsGUI`, `ComposeCoordinator`, `PresetViewController`, `AppearanceController`, and window callbacks now use the composition root's `drumGUI` instance. No mutable `DrumGUI` statics remain. Verified with `mvn -DskipTests compile`; tests were skipped.
- **5.4.4 Arp GUI instance ownership:** made all 23 mutable `ArpGUI` controls instance fields and moved construction of the BPM-affects-arpeggio checkbox into `ArpGUI`. `VibeComposerGUI`, `ExtraSettingsGUI`, `GenerationGUI`, `ComposeCoordinator`, `PresetViewController`, and `AppearanceController` now receive or use the composition root's `arpGUI` instance; no compatibility accessor or external `ArpGUI` field lookup remains. Verified with `mvn -DskipTests compile`; tests were skipped.
- **5.4.5 Melody GUI instance ownership:** made all mutable `MelodyGUI` controls instance fields and moved construction of the pattern-flip checkbox into `MelodyGUI`. The window, extra settings, generation, compose, appearance, preset snapshot, and MIDI editor paths use the owning `melodyGUI` instance or a focused callback. `MelodyGenerator` reads the copied `GUIConfig` setting instead of a live GUI control. No compatibility accessor or external `MelodyGUI` field lookup remains. Verified with `mvn -DskipTests compile`; tests were skipped.
- **5.4.6 Generation compose-randomization controls:** made `randomizeBpmOnCompose`, `randomizeTransposeOnCompose`, and `switchOnComposeRandom` instance fields. The window, appearance refresh, compose workflow, and preset snapshot now use the owned `generationGUI`; no external static reads remain for these controls. Remaining `GenerationGUI` statics include BPM/scale/seed controls and config/regeneration helpers. Verified with `mvn -DskipTests compile`; tests were skipped.
- **5.4.7 Chord GUI instance ownership:** made mutable chord controls and generated chord display state instance-owned by the window's `chordGUI`. Chord settings, generation macro controls, compose checkbox construction, preset snapshots, appearance refresh, compose preparation, and main-window actions use that owner. Arrangement renderers and variation popups receive the chord owner explicitly, while melody and MIDI editor popups receive the duration-reading callback they need. No active external mutable `ChordGUI` field lookups remain; `chordSelect` remains a stateless helper. Verified with `mvn -DskipTests compile`; tests were skipped.
- **5.4.8 Generation instrument-randomization controls:** made the instrument-randomization compose/generate checkbox and sidechain buttons instance-owned by `GenerationGUI`. The instrument GUIs read the option through a focused context callback; window compose handling, preset snapshots, and appearance refresh use the composition root's `generationGUI`. No external static checkbox lookup remains. Verified with `mvn -DskipTests compile`; tests were skipped.
- **5.4.9 Generation macro controls:** made global swing controls and the beat-duration multiplier instance-owned by `GenerationGUI`. Arp and chord generation receive only the multiplier condition they use; compose preparation, config transfer, and window workflows use the owned instance. No external static reads remain for these controls. Verified with `mvn -DskipTests compile`; tests were skipped.
- **5.4.10 Generation main controls and seed state:** made BPM, scale mode, loop count, random seed, regenerate-on-change, and the last generated seed instance-owned by `GenerationGUI`. `MainWindowControls` asks that owner to create its controls; composition, preset, arrangement, instrument-panel, melody, chord, drum, and MIDI audition callers now use the instance or focused callbacks. `RandomValueButton` receives its current-seed supplier from its owner. No mutable `GenerationGUI` statics or external static lookups remain. Verified with `mvn -DskipTests compile`; tests were skipped.
- **5.4.11 Melody import phrase ownership:** moved the imported melody and scale-detection candidate from static fields on `MelodyMidiDropPane` into the owning `MelodyGUI`. The drop pane reports valid candidates through a callback; compose and MIDI audition read the phrase from `MelodyGUI`. No active code reads or writes imported phrase state through `MelodyMidiDropPane` statics. `mvn -DskipTests compile` succeeds; tests were skipped.
- **Instrument GUI base:** added abstract `InstGUI<P>` for the shared per-instrument controls, typed panel collection, scroll pane, parent panel, settings-row controls, and sorted part serialization. These UI fields are instance-owned. Melody, bass, chord, arp, and drum GUIs each implement panel creation and their randomization hook; panel creation and randomization now dispatch through the instrument GUI instead of a switch in `VibeComposerGUI`.
- **Verification:** `mvn -DskipTests compile` succeeds. Tests were skipped; panel creation and MIDI drop behavior were not manually exercised.

### Phase 5 completion criteria

- Mutable state has a documented owner and lifetime, and static scope is retained only where application-wide sharing is a deliberate requirement.
- Shared UI components depend on feature behavior or collaborators rather than concrete feature-GUI statics.
- Context interfaces contain only operations used by their clients; broad contexts have been narrowed or have documented call-site evidence for their combined role.
- `ApplicationSessionState` no longer serves as an undifferentiated access point for unrelated lifetimes.
- Structural smells are recorded with concrete locations, impact, and an order for addressing them; no broad rewrite is required to close the phase.
- Each implementation slice preserves behavior and records its verification. No persisted config/XML shape changes are bundled into ownership-only work.
