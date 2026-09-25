# Original prompt (do not modify): Create a plan to split VibeComposerGUI (careful: >10k lines, won't fit into context window), 
# currently owning a very large amount of organizable chunks of the GUI, into smaller GUI classes split logically (e.g. MelodyGUI, BassGUI, ChordGUI, ArpGUI, DrumGUI, ArrangementGUI, ScoreGUI, ExtraSettingsGUI, GenerationGUI, VibeComposerCoreGUI). 
# The VibeComposerGUI class owns a very large number of fields, both static and non-static, which are typically instantiated once via various init methods and then live for the lifetime of the application (effectively singletons).

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

1. Inventory: I will start by scanning VibeComposerGUI.java to identify all fields and categorize them into the 10 requested buckets.
2. Refactoring Loop: For each category (e.g., Melody):
   • Identify the specific lines/fields belonging to that category.
   • Create the new class file.
   • Move the code semantically and safely; for this migration, turning a field or method into a static field/method is OK.
3. Once a method and all its associated fields are fully migrated to the new GUI class, its usages in VibeComposerGUI/in other callers should be replaced also.
4. IMPORTANT: when deactivating an obsolete field/method in VibeComposerGUI, don't delete it - only mark it with 2 underscores '__'; deletions will be handled only after the whole thing is migrated and ready.
5. IMPORTANT: for references to the original, VibeComposerGUI_MigrationBackup.java is available (this file shouldn't be edited, only read).

# Checkpoint 3 - Rough plan for the next steps:
1.  Address MelodyGUI:
    *   Create `MelodyGUI.java`.
    *   Move fields related to melody (e.g., `melodyPanels`, `melody...` related fields, `MelodyMidiDropPane`, `melodyBlockChoicePreference`, etc.) from `VibeComposerGUI.java` to `MelodyGUI.java`.
    *   Move the `initMelody`, `initMelodyGenSettings`, and associated methods to `MelodyGUI.java`.
    *   Update `VibeComposerGUI.java` to use `MelodyGUI` instance.
2.  Address BassGUI:
    *   Create `BassGUI.java`.
    *   Move `bassPanels` and `initBass` to `BassGUI.java`.
3.  Address ChordGUI:
    *   Create `ChordGUI.java`.
    *   Move `chordSettingsPanel`, `chord...` fields, `initChords`, `initChordGenSettings`, `initChordProgressionSettings`, `initCustomChords` to `ChordGUI.java`.
4.  Address ArpGUI:
    *   Create `Arp_GUI.java` (Note: The user asked for `ArpGUI`, but I should check if the package is `org.vibehistorian.vibecomposer`).
    *   Move `arpSettingsPanel`, `arp...` fields, `initArps`, `initArpGenSettings` to `ArpGUI.java`.
5.  Address DrumGUI:
    *   Create `DrumGUI.java`.
    *   Move `drumSettingsPanel`, `drum...` fields, `initDrums`, `initDrumGenSettings` to `DrumGUI.java`.
6.  Address ArrangementGUI:
    *   Create `ArrangementGUI.java`.
    *   Move `initArrangementSettings` and related fields.
7.  Address ScoreGUI:
    *   Create `ScoreGUI.java`.
    *   Move `scorePopup`, `score...` fields, `initScoreSettings`.
8.  Address ExtraSettingsGUI:
    *   Create `ExtraSettingsGUI.java`.
    *   Move `initExtraSettings` and its sub-methods.
9.  Address GenerationGUI:
    *   Create `GenerationGUI.java`.
    *   Move `initExtraSettingsGeneration`.
10. Address VibeComposerCoreGUI:
    *   Identify the remaining "Core" logic (UI framework, shared components like `currentChords`, `midimessage`, `time`, `sequencer` etc.).
    *   Move these to `VibeComposerCoreGUI`.

***************************************************************

## Mapping Progress
- [x] Melody
- [x] Bass
- [x] Chord
- [x] Arp
- [x] Drum
- [x] Arrangement
- [x] Score
- [ ] ExtraSettings
- [ ] Generation
- [ ] Core/CoreGUI


------------------------------------------------------------------
# Status:
- MelodyGUI migration: complete for melody UI state, settings construction, initial panel setup, melody panel randomization, and seed randomization.
- BassGUI migration: complete for bass panel state, bass tab construction, and random bass panel creation.
- ChordGUI migration: complete for chord UI state, settings construction, chord tab setup, progression controls, custom chord controls, and randomized chord panel creation. Cross-instrument workflows remain in VibeComposerGUI and access chord state through ChordGUI.
- ArpGUI migration: complete for arp UI state, settings and tab construction, and randomized arp panel creation. Shared window operations and cross-instrument access are supplied through its context or remain in VibeComposerGUI.
- DrumGUI migration: complete for drum UI state, generation settings and tab construction, and randomized drum panel creation. Shared window operations and cross-instrument workflows remain in VibeComposerGUI through DrumGUI.Context.
- ArrangementGUI migration: in progress for arrangement state ownership, control and table initialization, action dispatch, and popup/model helpers. Cross-instrument custom-panel application and table rendering/editing handlers remain in VibeComposerGUI.
- ScoreGUI migration: complete for score UI state, score tab and display settings, score rendering initialization, and popup toggling. Playback and MIDI workflows remain in VibeComposerGUI and access score state through ScoreGUI.
- Phase 1: In progress

