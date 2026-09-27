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

The first extraction phase has established feature GUI classes and reduced `VibeComposerGUI.java` from about 10.5k to about 7.1k lines. The remaining size is driven less by feature construction and more by coordination, application state, and cross-module access:

- `VibeComposerGUI` still owns broad static state for theme, layout, active configuration, playback, MIDI editing, undo, and the application window. A source scan finds about 300 active `VibeComposerGUI` references across `Components`, `Panels`, and `Popups`, so this coupling needs staged migration.
- Instrument GUIs previously received five shared arrays through their `Context` interfaces and selected controls by instrument index. Phase 2.1 moved those controls and panel collections into their owning modules; the parent now preserves the established instrument order only for cross-instrument operations.
- `copyGUItoConfig` and `copyConfigToGUI` in `VibeComposerGUI` now coordinate feature-owned mappings and retain application-level version, seed, MIDI mode, BPM, and scale mode transfer. Panel restoration remains a narrow callback because layout creation belongs to the window.
- `GUIConfig` is a JAXB persistence object with a flat field/getter shape. Restructuring it would affect saved preset compatibility and is not required to give GUI modules ownership of their mappings.
- `VibeComposerCoreGUI` is currently an empty skeleton. It should not become a new catch-all for state simply because the former parent class is large.

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

### Phase 3 status

Phase 3 has started. Continue with small behavior-preserving changes that simplify workflows or reduce unnecessary responsibilities and API surface. Keep each slice documented separately.
