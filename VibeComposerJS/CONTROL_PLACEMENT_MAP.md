# Remaining Java control placement inventory

Companion to the [active backlog](CONTEXTUAL_CONTROLS_PLAN.md). Completed control mappings and checkpoint evidence have moved to [__work_history/control-placement-record.md](__work_history/control-placement-record.md). This document keeps unfinished families and consumer audits; a listed family may include already-supported fields that need integration with its remaining behavior.

Classification follows the actual consumer: musical values belong in Part settings/inspector; rules choosing those values belong in expanded Tracks headers. Accent is Melody-only; Voices/stretch belongs to Chords/Arpeggio; percussion selects a track-owned note instead of pitched transpose. Melody block structure/custom durations are separate from generic rhythm masks. Missing consumers are work to port, not permanent role exceptions.

## Lookup and destination key

All Java paths in this document are relative to [the Java package](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/). Links identify real source files; symbols are stable search targets even as line numbers change. The old UI is now largely under `gui/`, not directly in `VibeComposerGUI.java`.

- **Q:** between-canvas Part settings panel; these controls are not duplicated in inspector. Available for global role/track and section role/track, where musically supported.
- **I:** detailed musical settings in inspector; no default quick-panel slot. Old global-only values stay global/role settings until a scoped generator consumer exists.
- **H:** track-generation policy directly in expanded Tracks group header. The editable total beside the role name remains visible when collapsed; separate Count/Generate N controls are removed.
- **HI:** advanced track-generation policy shown directly in the expanded Tracks group alongside H controls. No reroll policy or actions in the part inspector.
- **Other:** transport, harmony panel, arrangement toolbar/section inspector, track row, mixer, editor, or deferred preferences as specified.

Preserve old parameter semantics, not old widget positions. The model/control reference distinguishes a selected value from the policy that chooses it.

## Remaining musical and creation families

| Phase | Java lookup / exact symbols | Destination / remaining work |
| --- | --- | --- |
| P9 | [MelodyPanel](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Panels/MelodyPanel.java): `customDurationValues`, `customDurationChances` → `customDurationNotes`, `customDurationChances` | Delivered: Rhythm pattern preview opens the ten-lane custom melody duration editor; templates remain distinct from manual-note replacements. |
| P9 | `melodyUseCustomDurations`, `melodyCustomDurationsRandomWeighting`, `melodyCustomDurationsStrictMode` | Delivered: scoped `melody1`/`custom` selection and popup lane weighting, with fixed duration boundaries. Optional non-strict reassignment remains deferred. |
| P10 | `melodyRhythmAccents`, `melodyRhythmAccentsMode`, `melodyRhythmAccentsPocket` | I: drum-driven Melody rhythm accents, explicit same-section source/execution order. |
| P11 | `useUserMelody`, `dropPane`, `userMelodyScaleModeSelect` | Import/editor + I: imported melody ownership, scale behavior and source inference audit. |
| Future | `arpCopyMelodyInst` | Do not port this special-case copy. Use universal dynamic source-track instrument selection, deferred by user request. |
| Future | Per-control **⍟** locks | Independent global randomization locks, separate from section snowflakes and whole-track locks. |

## Remaining general actions and integration audits

P6 creation families are delivered; see [the P6 record](__work_history/p6-creation-record.md). Drum Dynamic/Ghosts, Overrandomize and Follow Melody percentages live in expanded Tracks headers. Arpeggio weighted directions/later-track weighting and static pause baking/growth use the existing policies. Bass creation never invokes baking/growth, and its stored Pause has no production phrase consumer; Bass converts automatic MELODY1 picks to FULL, while Chords does not select MELODY1. These are audited source behaviors, not missing creation dependencies. Melody now rerolls Note exception chance and Max note exceptions through their delivered part consumers. Melody DOUBL (Doubled rhythm) and LEAD (Lead chords) have been removed from the settings catalogue, scoped values, outline copying and generation consumers; Split long remains supported.

- [PartManagerPanel](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Panels/PartManagerPanel.java) presets/overwrite actions: inspector/header menu; retain independent track identity.
- `ArpGUI.arpAffectsBpm`: global musical behavior in I, with an explicit tempo policy and matching playback/export.
- Section instrument changes and section-owned manual phrases require deliberate ownership/playback/export design; current instruments and manual replacements are track-owned. Do not silently reinterpret them during a scoped edit.
- P7c is final acceptance after the delivered P5/P6 changes, including keyboard/device alternatives and the arrangement zoom/device-scale matrix.

## Exclusions and completed behavior guardrails

Do not port [LegacyMelodySkeletonGenerator](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/LegacyMelodySkeletonGenerator.java), `melodyLegacyMode`, `melodyUseOldAlgoChance`, `melodyAvoidChordJumpsLegacy`, or old-only usages of `melodyFirstNoteFromChord` / `randomChordNote`. `alternatingRhythmChance` has no current block/expansion consumer. Basic chords only has no musical effect in the current block path; omit the ineffective toggle.

All three block target modes, tonal/chord targeting and avoid-note correction are delivered. New blocks defaults to **25%**; knobs/comboboxes reset to defaults on right-click. Keep these completed behaviors intact rather than reopening their historical checkpoint backlog.

P5e block-skeleton inference, explicit section Use Melody harmony/Swap Melody and progression-wide voicing flattening are delivered with shared P5f section timing. Flatten voicing %, Flatten large chords and Progressive voicing live in the detailed global harmony inspector and affect shared musical consumers, independently of part voicing and track policies. Preserve explicit section harmony and manual-note ownership. See the [implementation record](__work_history/p5e-harmony-integration-record.md) for seeded evidence, source/short-progression/inversion rules and P5f/P11 boundaries.

Section Chordlets/durations and sparse swing/BPM/key/scale/key-change/beat-duration context are delivered in the section inspector. See the [P5f record](__work_history/p5f-section-context-record.md) for normalized timing, explicit section-local modulation, ownership and playback/MIDI contracts.

## Harmony, arrangement, transport, and app-level controls

| Old source/control family | Destination and references | Timing |
| --- | --- | --- |
| Primary chords, Custom Chords, Custom Durations | Transport Chordlets; `ChordGUI.userChords`, `userChordsEnabled`, `userDurationsEnabled`, `userChordsDurations` | Roots/spices/inversions and drag/remove/wheel menus implemented; custom durations and N/S/Dd shortcuts remain deferred |
| Chord count/repeats/spice/first/last/formula | Harmony quick subset: `chordProgressionLength`, `allowChordRepeats`, `spiceChance`, `firstChordSelection`, `lastChordSelection`; I: `spiceAllowDimAug`, `spiceAllow9th13th`, `spiceParallelChance`, `spiceForceScale`, `chordSlashChance`, `useChordFormula`, `longProgressionSimilarity` | Remaining detailed harmony buildout; policies choose harmony, not instrument track parameters. Voicing flattening/large chords/progressive behavior is delivered. |
| Copy generated chords | `ChordGUI.copyChordsAfterGenerate` | Deferred UI/export preference |
| Global swing override, beat duration multiplier | `GenerationGUI.globalSwingOverride`, `globalSwingOverrideValue`, `globalSwingOverrideApplyButton`, `beatDurationMultiplier` | I global musical settings; optional quick project context later |
| Randomize instruments/BPM/key on Compose | `GenerationGUI.randomizeInstOnComposeOrGen`, `randomizeBpmOnCompose`, `randomizeTransposeOnCompose`, `switchOnComposeRandom` | Project policy inspector; compact master action, not every role header |
| Sidechain patterns (All/Tab), transpose batch actions | `GenerationGUI.sidechainPatterns`, `sidechainPatternsTab`; transpose action lookup in GenerationGUI | Scoped musical batch actions in I/menu; automatic rules deferred |
| Regenerate on Change, loop behavior | `GenerationGUI.regenerateWhenValuesChange`, `loopBeatCount` and playback owner | Transport/preferences; distinguish regeneration from settings reroll |
| ARRANGE/randomize/on Compose, variation chances | [ArrangementGUI](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/gui/ArrangementGUI.java): `useArrangement`, `randomizeArrangementOnCompose`, `arrangementVariationChance`, `arrangementPartVariationChance` | Arrangement toolbar + inspector; presence/variation policies separate from part settings |
| Parts/Vars/Patterns | Arrangement inclusion/variation/pattern popup actions; [ArrangementPartInclusionPopup](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Popups/ArrangementPartInclusionPopup.java), `Section.partMap`/variation data | Arrangement inspector/context menus; resolve realized choices independently from their chances |
| Recompose/replace/reset custom on compose | `ArrangementGUI.recomposeSection()`, `replaceSection()`, `prepareForCompose()`, `arrangementResetCustomPanelsOnCompose` | Section actions and explicit project policy; preserve local changes by default, P6/P7 |
| Scale MIDI velocity | `ArrangementGUI.arrangementScaleMidiVelocity` | I global/arrangement musical behavior later; never confuse with score shading |

## ExtraSettingsGUI deferred inventory

Source: [ExtraSettingsGUI](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/gui/ExtraSettingsGUI.java). Defer this panel's buildout to P8, but do not lose its MIDI-affecting inputs when designing global generation state.

| Family | Symbols | Eventual home |
| --- | --- | --- |
| Musical timing/output | `humanizeNotes`, `globalNoteLengthMultiplier`, `swingUnitMultiplier`, `stretchMidi`, `padGeneratedMidi`, `padGeneratedMidiValues` | Global musical/output inspector |
| Scale rules | `customMidiForceScale`, `transposedNotesForceScale`, `keyChangeTypeSelection` | Global musical inspector/import behavior |
| Automatic randomization | `randomizeTimingsOnCompose`, `sidechainPatternsOnCompose`, `bpmLow`, `bpmHigh`, `orderedTransposeGeneration`, `patternApplyPausesWhenGenerating` | Project policy inspector; ordered transpose may later specialize role policies |
| Instrument pools | `bannedInsts`, `useAllInsts`, `reinitInstPools` | Advanced instrument-reroll policy |
| Playback position | `pauseBehaviorCombobox`, `startFromBar`, `rememberLastPos`, `snapStartToBeat`, `moveStartToCustomizedSection`, `transposeNotePreview` | Playback preferences |
| Devices/MIDI | `soundbankFilename`, `useMidiCC`, `reuseMidiChannelAfterCopy` | Device/export preferences; translate Java-specific behavior deliberately |
| Presentation | `displayVeloRectValues`, `knobControlByDragging`, `highlightPatterns` | UI preferences |
| Persistence/validation/export naming | `customFilenameAddTimestamp`, `configHistoryStoreRegeneratedTracks`, `allowValuesOutOfRange` | App preferences; keep current validation safe until explicitly designed |

Implementation lookup workflow: search the exact symbol, follow save/load into GUIConfig/InstPart, then locate the phrase or reroll consumer. Record unsupported/no-op branches before deciding whether to reproduce or fix them. All newly active controls need matching generation, serialization, history, and scope support; inspector completeness is delivered with each implemented slice, while explicitly deferred capabilities remain in this backlog.
