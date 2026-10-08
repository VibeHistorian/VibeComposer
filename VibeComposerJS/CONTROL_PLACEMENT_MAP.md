# Legacy control placement map

Companion to [CONTEXTUAL_CONTROLS_PLAN.md](CONTEXTUAL_CONTROLS_PLAN.md). Symbols reflect the Java source inspected on 2026-10-08. Multiple symbols on a row form a related control family; each must be traced independently before porting. This is a placement backlog, not a claim of completed TypeScript parity.

## Lookup and destination key

All Java paths in this document are relative to [the Java package](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/). Links identify real source files; symbols are stable search targets even as line numbers change. The old UI is now largely under `gui/`, not directly in `VibeComposerGUI.java`.

- **Q:** between-canvas Part settings panel, with the same complete control in inspector. Available for global role/track and section role/track, where musically supported.
- **I:** detailed musical settings in inspector; no default quick-panel slot. Old global-only values stay global/role settings until a scoped generator consumer exists.
- **H:** track-generation policy directly in expanded Tracks group header, also in its inspector policy section.
- **HI:** advanced track-generation policy in inspector; header offers access, not the full control wall.
- **Other:** transport, harmony panel, arrangement toolbar/section inspector, track row, mixer, editor, or deferred preferences as specified.

Quick priority is a proposed product decision. Preserve old parameter semantics, not old widget positions. The model/control reference distinguishes a selected value from the policy that chooses it.

## Shared part settings and track actions

References: [InstPanel](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Panels/InstPanel.java), [InstPart](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Parts/InstPart.java), [VisualPatternPanel](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Panels/VisualPatternPanel.java).

| Old label/function | UI/model lookup | Destination | Stage/notes |
| --- | --- | --- | --- |
| Transpose | `InstPanel.transpose` → `InstPart.transpose` | Q | P3; part transpose separate from transport transpose |
| Length | `noteLengthMultiplier` | Q | P3; preserve percent units, not score timing length |
| Velocity range | `minMaxVelSlider` → `velocityMin`, `velocityMax` | Q | P3; range control, not track volume; validate ordering |
| Fill ALL/ODD/EVEN/F1…/HALF… | `chordSpanFill`, `fillFlip` → same fields | Q | P4; [ChordSpanFill](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Enums/ChordSpanFill.java); preserve actual Java masks rather than guessing parity from labels |
| Hits, Span, Repeat | `hitsPerPattern`, `chordSpan`, `patternRepeat` | Q | P4; actual values, unlike reroll hit rules |
| Rhythm pattern + inverse | `pattern`, `patternFlip` | Q | P4; [RhythmPattern](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Enums/RhythmPattern.java) |
| Pattern grid, custom velocities | `comboPanel` → `customPattern`, `customVelocities` | Q | P4; grid editing and velocity detail share stored data |
| Shift | `patternShift` | Q | P4; actual rotation, distinct from Shift% reroll chance |
| Pause%, Split% | `pauseChance`, `exceptionChance` | Q | P4; phrase-generation probabilities, not track-reroll rules |
| Swing%, Accent | `swingPercent`, `accents` | Q / I for Accent initially | P4; document interaction with global/section swing |
| Voices + enable | `chordNotesStretch`, `stretchEnabled` | Q for chords/arp | P5; actual voice expansion, not reroll VOICES mode/bounds |
| Offset | `offset` | I; optional secondary Q | P5; audit Java units before translating |
| Delays, FB Dur., FB Vol. | `feedbackCount`, `feedbackDuration`, `feedbackVol` | I | P5; note-producing feedback, not necessarily audio effects |
| Seed/Clear seed | `patternSeed` → `patternSeed`, `getPatternSeedWithPartOffset()` | I; scoped action near heading | P5/P6; keep seed and reroll action distinct |
| Instrument, MIDI channel | `instrument`, `midiChannel` | Track row summary + inspector/mixer | P1/P5; base identity stays track-owned; section instrument switching needs a deliberate playback/export design |
| Lock / `?` reroll | `lockInst`, `randomizeButton` | Track row + inspector policy | P6; distinguish whole-track and instrument-only locks |
| Exclude generation | `muteInst` → `InstPart.muted` | Inspector part enable + row action | P5; do not conflate with mixer mute or section presence |
| Volume, pan, solo/mute | `volSlider`, `panSlider`, `soloMuter` | Mixer + row M/S | Existing behavior; musical velocity remains separate |
| Copy, delete, order | `copyButton`, `removeButton`, `panelOrder`, `orderOffset` | Track row/inspector actions | Preserve IDs and seed-order correspondence; no settings knob |
| MIDI edits/import | `midiMVI`, `customMidi` | In-canvas editor + inspector | P3/P5; section/track phrase ownership must be explicit |

## Role-specific musical controls

| Role | Old control symbols → stored fields | Quick subset | Remaining inspector controls / notes |
| --- | --- | --- | --- |
| Bass | [BassPanel](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Panels/BassPanel.java): `useRhythm`, `alternatingRhythm`, `doubleOct`, `noteVariation`, `melodyPattern`, `patternJoinMode` → [BassPart](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Parts/BassPart.java) | Note Variance, Oct. Interval, Use Pattern | Random Alt. Rhythm is a phrase setting despite its label; Melody1 Pattern and join mode are advanced. P5 |
| Chords | [ChordPanel](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Panels/ChordPanel.java): `strum`, `strumType`, `strumPauseChance`, `transitionChance`, `transitionSplit`, `patternJoinMode`, `instPoolPicker` → [ChordPart](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Parts/ChordPart.java) | Strum + type; Voices from shared controls | Strum Pause, Transition%, transition split, join mode. `instPoolPicker` is a reroll instrument-pool choice: HI, not a note control. P5/P6 |
| Arp | [ArpPanel](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Panels/ArpPanel.java): `arpPattern`, `arpPatternRotate`, `arpContour`, `arpContourChordMode` → [ArpPart](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Parts/ArpPart.java): `arpPatternCustom` plus corresponding fields | Pitch direction/picker, Rotate, Voices | Contour and chord-relative mode/custom direction detail. Keep separate from shared rhythm pattern. P5 |
| Drums | [DrumPanel](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Panels/DrumPanel.java): `isVelocityPattern` → [DrumPart](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Parts/DrumPart.java) | Ghosts, drum-note selector plus shared pattern/velocity | Individual drum track chooses a percussion note, not a pitched GM program. P5 |
| Melody | [MelodyPanel](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Panels/MelodyPanel.java): `speed`, `fillPauses`, `noteTargets`, `patternStructure`, `patternFlexible`, `maxBlockChange`, `blockJump` → [MelodyPart](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Parts/MelodyPart.java): `chordNoteChoices`, `melodyPatternOffsets`, corresponding scalar fields | Speed, Fill Pauses, block structure and note-target list access | Flex, Max Block Change, Block Jump; expanded target/block editors in inspector. P1 supported core, P5 extended |
| Melody exceptions/rhythm | `maxNoteExceptions`, `alternatingRhythmChance`, `doubledRhythmChance`, `splitChance`, `noteExceptionChance`, `leadChordsChance`, `startNoteChance` | Split Long% as optional melody knob | Full set in I; all are musical probabilities, not policy maxima. P5 |
| Melody durations | `customDurationValues`, `customDurationChances` → `customDurationNotes`, `customDurationChances` | Compact duration-editor opener | Full custom rhythm/duration editor in I/editor; coordinate with melody global duration flags. P5 |

Current TS bass `noteVariation`/`octaveInterval` and chord `noteLengthPercent` already cover small parts of these families. The current bass/chord rhythm enums, two arp rates/octaves, and drum groove presets are simplified generators; preserve or replace deliberately as the full engine arrives.

The P1a checkpoint adds Melody's role identity, red presentation, manual-note editing, instrument/mix/channel routing and section presence. Its musical generation controls above are still deferred to the actual current block-generator port (P1b/P5); no melody knobs or reroll controls are active yet. Melody presence defaults follow [Arrangement.defaultSections](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Arrangement.java), whose `Section` constructor arguments use Melody first, followed by Bass, Chords, Arp and Drums.

## Track-generation policies

Shared [InstGUI](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/gui/InstGUI.java) owns `addPanelButton`, `generatePanelButton`, `randomPanelsToGenerate`: compact Add/Reroll in header (H), explicit Generate N action in inspector (HI). Existing tracks retain IDs when rerolled. `enabledCheckBox` is role enable (musical/arrangement control), and `groupFilterSlider` (LP) is mix/device control, not randomization policy. [PartManagerPanel](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Panels/PartManagerPanel.java) presets/overwrite actions belong in inspector/header menu later; do not mix them into pattern controls.

### Arpeggio

Source: [ArpGUI](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/gui/ArpGUI.java), especially `initArpGenSettings()` and `createRandomArpPanels()`.

| Old label | Exact symbol(s) | Placement | Relationship |
| --- | --- | --- | --- |
| Arp#, Random#, One#, Limit 2^n | `randomArpHitsPicker`, `randomArpHitsPerPattern`, `randomArpAllSameHits`, `randomArpLimitPowerOfTwo` | H | Choose fixed/random/shared hit counts; Java uses weighted powers-of-two candidates, not an arbitrary divisibility filter |
| Fills, Transpose, Patterns | `randomArpUseChordFill`, `randomArpTranspose`, `randomArpPattern` | H | Policy for choosing corresponding actual part settings; see caveat below |
| VOICES mode/number/Chance | `randomArpStretchType`, `randomArpStretchPicker`, `randomArpStretchGenerationChance` | H | NONE/FIXED/AT_MOST voice expansion policy |
| Max Split%, length bounds | `randomArpMaxExceptionChance`, `randomArpMinLength`, `randomArpMaxLength` | H | Chooses `exceptionChance`/`noteLengthMultiplier`, including fast-arp adjustments |
| Shift%, Max Repeat | `randomArpShiftChance`, `randomArpMaxRepeat` | HI | Chooses actual shift/repeat values |
| Min Vel, Max Vel | `randomArpMinVel`, `randomArpMaxVel` | HI | Writes track velocity bounds on reroll; selected-track velocity range is Q |
| One Inst., Arp#1 Copy Melody Inst. | `randomArpAllSameInst`, `arpCopyMelodyInst` | HI | Shared instrument/copy rule; respects instrument randomization permission |
| on Compose | `randomArpsGenerateOnCompose` | H | Automatic track-settings generation trigger |
| Clear Seeds | local `clearArpPatternSeeds` action | Header menu / I action | Changes actual part seeds, not policy |

Exceptions: `randomArpUseOctaveAdjustments` (Rand. Oct.) and `randomArpCorrectMelodyNotes` (Correct Notes by Melody) are **I musical role settings**, optionally Q later. `saveToConfig()` writes `useOctaveAdjustments` and `randomArpCorrectMelodyNotes`; [ArpPhraseGenerator](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/ArpPhraseGenerator.java) consumes them during note generation. `arpAffectsBpm` is global musical behavior in I, with a later explicit tempo policy. `randomArpTranspose` is constructed/displayed but the inspected `createRandomArpPanels()` transpose branch does not consult it; record this legacy discrepancy and implement the new policy intentionally, rather than promise parity with an ineffective toggle.

### Chords

Source: [ChordGUI](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/gui/ChordGUI.java), `initChordGenSettings()`/`createRandomChordPanels()` and config save/load; [ChordGenSettings](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Panels/ChordGenSettings.java).

| Control family | Exact symbols | Placement |
| --- | --- | --- |
| Automatic generation | `randomChordsGenerateOnCompose` | H |
| Fills, Transpose, Patterns, Vary Length | `randomChordUseChordFill`, `randomChordTranspose`, `randomChordPattern`, `randomChordVaryLength` | H |
| Delay, Strum, Split, strum amount policy | `randomChordDelay`, `randomChordStrum`, `randomChordSplit`, `randomChordStruminess` | H, amount detail HI |
| Voice expansion | `randomChordStretchType`, `randomChordStretchPicker`, `randomChordStretchGenerationChance`, `randomChordExpandChance` | H, expansion detail HI |
| Max Split, sustain, shift, strum pauses | `randomChordMaxSplitChance`, `randomChordSustainChance`, `randomChordShiftChance`, `randomChordMaxStrumPauseChance` | Max Split H; rest HI |
| Velocity bounds | `randomChordMinVel`, `randomChordMaxVel` | HI |

`randomChordVoicingChance` (Flatten Voicing%) is an **I global harmony-generation setting**, not merely a track-reroll checkbox: it is saved in `ChordGenSettings.flattenVoicingChance` and consumed by MidiGenerator, MelodyChordInference and SectionGenerationPlanner. Keep it with detailed harmony and expose scoped variants only when those consumers accept them.

### Drums

Source: [DrumGUI](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/gui/DrumGUI.java), `initDrumGenSettings()`/`createRandomDrumPanels()`; [DrumGenSettings](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Panels/DrumGenSettings.java).

| Control family | Exact symbols | Placement |
| --- | --- | --- |
| on Compose, Fills, Patterns | `randomDrumsGenerateOnCompose`, `randomDrumUseChordFill`, `randomDrumPattern` | H |
| Max Swing+-, Random Offset | `randomDrumMaxSwingAdjust`, `randomDrumSlide` | H |
| On Generate hits multiplier | `randomDrumHitsMultiplierOnGenerate` | H |
| Dynamic%, Shift% | `randomDrumVelocityPatternChance`, `randomDrumShiftChance` | HI; chooses Ghosts/velocity pattern and shift |
| Multiply Hits By (immediate action) | `randomDrumHitsMultiplier` | Q cell/group action or I | Despite name, immediately transforms hits/custom patterns; not stored reroll policy |
| Humanize | `humanizeDrums` | I musical role setting | Direct note timing; deferred quick placement |
| Mapping, combined export, display ordering | `drumCustomMapping`, `drumCustomMappingNumbers`, `combineDrumTracks`, `bottomUpReverseDrumPanels` | Deferred device/export/UI settings | Keep independent track identity even if export combines drums |

### Bass and melody policies

[BassGUI](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/gui/BassGUI.java) exposes shared Add/Generate/count and presets rather than a large dedicated checkbox policy. Trace `createRandomBassPanels()` and its shared instrument-randomization dependency; do not move `BassPanel.alternatingRhythm` or Note Variance into H just because they affect randomness.

[MelodyGUI](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/gui/MelodyGUI.java): `generateMelodiesOnCompose`, `randomMelodyOnRegenerate`, `randomMelodySameSeed`, `melodyTargetNotesRandomizeOnCompose`, `melodyPatternRandomizeOnCompose` → H/HI trigger/seed policy, backed by `createRandomMelodyPanels()` and `applyGeneratedTargetNotes()`. Actual target values and block structure remain Q/I. `combineMelodyTracks` → deferred export setting. Block weights and New Blocks% are musical generator inputs, not track-reroll policies.

## Melody global musical settings and exclusions

These are currently owned by [MelodyGUI](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/gui/MelodyGUI.java), serialized through GUIConfig, and consumed across [MelodyGenerator](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/MelodyGenerator.java), [MelodyBlockSkeletonGenerator](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/MelodyBlockSkeletonGenerator.java), and helpers. Put them in the melody musical inspector initially, preserving their global/role scope. Add section/track scope only by supplying typed effective generation inputs, not by assuming every Java GUI field is already per-part.

| Family | Exact controls | Placement/stage |
| --- | --- | --- |
| Tonal targeting | `melodyChordNoteTarget`, `melodyTonicNoteTarget`, `melodyModeNoteTarget`, `melodyEmphasizeKey`, `melodyBasicChordsOnly`, `melodyReplaceAvoidNotes` | I, P5 |
| Block/target generation | `melodyNewBlocksChance`, `melodyBlockTargetMode`, `melodyTargetNoteVariation`, `noteTargetDirectionChoice`, `melodyUseDirectionsFromProgression`, `melodyMaxDirChanges` | I; selected New Blocks/target mode optional Q after core, P5 |
| Block preferences | `melodyBlockTypePreference`, `melodyBlockChoicePreference` | I expanded editor, P5; check which preferences feed current block path |
| Pattern/rhythm effects | `melody1ForcePatterns`, `melodyPatternFlip`, `melodyPatternEffect`, `melodyArpySurprises`, `melodySingleNoteExceptions`, `melodyFillPausesPerChord` | I, P5; not reroll-policy toggles |
| Rhythm accents | `melodyRhythmAccents`, `melodyRhythmAccentsMode`, `melodyRhythmAccentsPocket` | I, P5 |
| Custom duration behavior | `melodyUseCustomDurations`, `melodyCustomDurationsRandomWeighting`, `melodyCustomDurationsStrictMode` | I custom duration editor, P5 |
| Imported melody | `useUserMelody`, `dropPane`, `userMelodyScaleModeSelect` | Editor/import + I, later P5 slice |
| Excluded old skeleton | `melodyUseOldAlgoChance`, `melodyAvoidChordJumpsLegacy`; old-only usages of `melodyFirstNoteFromChord`, `randomChordNote` | No port; verify consumer reachability before including any old-only control |
| Old algorithm selector | `melodyLegacyMode` | No port: `MelodyGenerator.makeFullMelodyMap()` uses it to select `legacySkeletonGenerator.generate()` instead of the block skeleton |

The forbidden implementation is [LegacyMelodySkeletonGenerator](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/LegacyMelodySkeletonGenerator.java). Do not introduce an algorithm chooser containing it. Keep currently useful block-based inputs even if old source labels are confusing.

## Harmony, arrangement, transport, and app-level controls

| Old source/control family | Destination and references | Timing |
| --- | --- | --- |
| Primary chords, Custom Chords, Custom Durations | Existing compact harmony panel above Tracks; `ChordGUI.userChords`, `userChordsEnabled`, `userDurationsEnabled`, `userChordsDurations` | P5 extended chord symbols/durations; current degrees-only UI is partial |
| Chord count/repeats/spice/first/last/formula | Harmony quick subset: `chordProgressionLength`, `allowChordRepeats`, `spiceChance`, `firstChordSelection`, `lastChordSelection`; I: `spiceAllowDimAug`, `spiceAllow9th13th`, `spiceFlattenBigChords`, `spiceParallelChance`, `spiceForceScale`, `chordSlashChance`, `useChordFormula`, `longProgressionSimilarity`, `squishChordsProgressively` | Detailed harmony buildout; policies choose harmony, not instrument track parameters |
| Copy generated chords | `ChordGUI.copyChordsAfterGenerate` | Deferred UI/export preference |
| Key/transpose, tempo, mode, main seed | Transport + existing seed/project control; [GenerationGUI](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/gui/GenerationGUI.java): `mainBpm`, `scaleMode`, `randomSeed`; global transpose lookup in VibeComposerGUI | Existing transport; seed decimal string preserved |
| Global swing override, beat duration multiplier | `GenerationGUI.globalSwingOverride`, `globalSwingOverrideValue`, `globalSwingOverrideApplyButton`, `beatDurationMultiplier` | I global musical settings; optional quick project context later |
| Randomize instruments/BPM/key on Compose | `GenerationGUI.randomizeInstOnComposeOrGen`, `randomizeBpmOnCompose`, `randomizeTransposeOnCompose`, `switchOnComposeRandom` | Project policy inspector; compact master action, not every role header |
| Sidechain patterns (All/Tab), transpose batch actions | `GenerationGUI.sidechainPatterns`, `sidechainPatternsTab`; transpose action lookup in GenerationGUI | Scoped musical batch actions in I/menu; automatic rules deferred |
| Regenerate on Change, loop behavior | `GenerationGUI.regenerateWhenValuesChange`, `loopBeatCount` and playback owner | Transport/preferences; distinguish regeneration from settings reroll |
| ARRANGE/randomize/on Compose, variation chances | [ArrangementGUI](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/gui/ArrangementGUI.java): `useArrangement`, `randomizeArrangementOnCompose`, `arrangementVariationChance`, `arrangementPartVariationChance` | Arrangement toolbar + inspector; presence/variation policies separate from part settings |
| Parts/Vars/Patterns | Arrangement inclusion/variation/pattern popup actions; [ArrangementPartInclusionPopup](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Popups/ArrangementPartInclusionPopup.java), `Section.partMap`/variation data | Arrangement inspector/context menus; resolve realized choices independently from their chances |
| MANUAL, Global/section selector, Apply/Apply.. | `ArrangementGUI.manualArrangement`, `isCustomSection()`, `applyCustomPanelsToSection()`, `switchPanelsForSectionSelection()`; [ApplyCustomSectionPopup](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Popups/ApplyCustomSectionPopup.java) | New explicit context, auto-created local overrides, scoped apply/copy, P3/P7 |
| Customized role lists and clear/reset | [Section](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Section.java): `getInstPartList()`, `setInstPartList()`, `hasCustomizedParts()`, `resetCustomizedParts()` | Sparse role/track patches, customized badges, reset to inheritance, P3/P7 |
| Section harmony/length | `Section.customChordsEnabled`, `customDurationsEnabled`, `customChords`, `customDurations`, measures | Header-selected section inspector, P5; harmony quick access follows section scope |
| Section swing/BPM/key/scale/timing | [SectionConfig](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/SectionConfig.java): `sectionSwingOverride`, `sectionBpm`, `customKeyChange`, `customScale`, `customKeyChangeType`, `beatDurationMultiplierIndex` | Section inspector, P5 slices; add tempo maps/export behavior first |
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

## Implemented workflow slice — P7a

The old MANUAL/Apply family (`ArrangementGUI.applyCustomPanelsToSection()`, `switchPanelsForSectionSelection()`, `Section.setInstPartList()`, and `ApplyCustomSectionPopup`) is now partially represented by explicit local actions in the quick panel and inspector. These are deliberate sparse-patch equivalents, rather than a Java full-list storage port:

| New action | Scope and effect | Implementation lookup |
| --- | --- | --- |
| Apply cell/track overrides | Merge explicit source fields at the same layer in header-selected destinations; preserve destination track exceptions | `ProjectService.applyPartSettingsToSections(..., 'overrides')` |
| Apply effective values | Replace current matching track patches with independent source-effective snapshots at each destination | `ProjectService.applyPartSettingsToSections(..., 'effective')` |
| Freeze effective settings / Freeze current tracks | Snapshot supported part values per current track in the source section; preserve mixed values | `ProjectService.freezePartSettings()` |
| Reset cell + track overrides | Clear both layers for the selected role and its current tracks | `ProjectService.resetCellPartSettings()` |

All actions share `PartScopeActionsComponent` and `WorkspaceCanvasComponent.runPartWorkflow()`. The source section is excluded from range copying. Track presence, instruments, manual phrases and global values are preserved. Freeze covers implemented part controls only; global harmony/seed and future unported controls continue to follow their own settings. Apply to Global remains deferred.
