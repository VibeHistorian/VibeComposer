# Control placement and Java reference

Companion to the [future-work plan](CONTEXTUAL_CONTROLS_PLAN.md). The plan alone defines implementation scope; a Java symbol in this reference is not a commitment to port it. Historical consumer audits and parity boundaries live in [__work_history](__work_history/README.md).

Musical values belong with their applicable project or part settings; policies choosing track values belong in expanded Tracks headers. Keep instrument identity, track presence, manual phrases and application preferences separate. Use the existing scoped commands and shared effective-value resolvers.

All Java paths below are relative to [the Java package](../midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/). Port consumer behavior rather than legacy widget layout.

## Melody setting scopes

Project-wide melody controls live in **Settings → Behavior → Melody** and are saved in the project's `melodyGlobal`, alongside Contextual Melody (enabled by default). They do not belong to UI preferences or the inspector.

| Project-wide settings | Melody track settings in the inspector |
| --- | --- |
| Target mode, Target variation, Directions from progression, Force first outline | Target direction; Melody Low MIDI, Melody High MIDI, Melody Tension |
| Avoid | Chord, Tonic and Mode target percentages; Emphasize key |
| Pattern effect | Max direction changes |
| Seven catalogue block-type weights; Change preference | New blocks |
| — | Fill pauses per chord |
| Phrase bars, beats per bar, chromatic approaches and Compose range randomization | Ending, Style and Form (one stack of three compact selectors) |

Track settings use the normal track base → section role → section track resolution, mixed selection, inherited reset, copy/freeze, history and project persistence. Automatic target preparation reads each track's base Target direction on Compose and enabled Regenerate, retaining explicit section target arrays. Contextual Melody reads the resolved direction for each phrase. The removed project-wide Melody pattern flip does not affect a track's custom-duration/grid Pattern flip.

Global Melody controls use a plain settings layout with centered knob labels. Melody pattern flip and its published-onset inversion are removed. Compose range randomization writes eligible Melody track ranges; Regenerate retains them.

## Planned work: Java lookups and Angular destinations

| Work | Java reference | Angular destination / design boundary |
| --- | --- | --- |
| Spice policies, first/last chord | `gui/ChordGUI.java`: `spiceChance`, `spiceAllowDimAug`, `spiceAllow9th13th`, `spiceParallelChance`, `spiceForceScale`, `chordSlashChance`, `firstChordSelection`, `lastChordSelection` | Project harmony-generation settings and progression consumer. |
| Global custom chord durations | `gui/ChordGUI.java`: `userDurationsEnabled`, `userChordsDurations` | Primary Chordlet durations with section inheritance and shared boundaries. |
| Phrase scale/chord inference | `useUserMelody`, `dropPane`, `userMelodyScaleModeSelect` and imported-source inference as source context | Infer from the open phrase draft and explicitly apply to project/section; reuse phrase-editor MIDI import, without a dedicated Melody source/drop area. |
| Global swing, swing unit, beat duration | `gui/GenerationGUI.java`: `globalSwingOverride`, `globalSwingOverrideValue`, `globalSwingOverrideApplyButton`, `beatDurationMultiplier`; `gui/ExtraSettingsGUI.java`: `swingUnitMultiplier` | Global musical settings with explicit part/section precedence and shared timing. |
| Context-aware sidechaining | `gui/GenerationGUI.java`: `sidechainPatterns`, `sidechainPatternsTab`, `sidechainPatterns(...)` | Scoped source/destination workflow; redesign for Angular contexts. |
| Arrangement variations | `gui/ArrangementGUI.java`: `arrangementVariationChance`, `arrangementPartVariationChance`; `Popups/ArrangementPartInclusionPopup.java`; `Section` variation data | Canvas indicators and variation popup; realized choices separate from chances and presence. |
| Global humanization | `gui/ExtraSettingsGUI.java`: `humanizeNotes` and its generation consumer | Global musical settings; explicit deterministic interaction with other timing. |
| MIDI padding | `gui/ExtraSettingsGUI.java`: `padGeneratedMidi`, `padGeneratedMidiValues`, `MidiGenerator.OutputOptions` | MIDI output-track padding and export preferences. |
| Arrangement/section velocity scaling | `gui/ArrangementGUI.java`: `arrangementScaleMidiVelocity`; saved `scaleMidiVelocityInArrangement` | Arrangement and section dB knobs with explicit MIDI velocity mapping, separate from mixer gain. |
| Compose BPM bounds | `gui/ExtraSettingsGUI.java`: `bpmLow`, `bpmHigh` | UI preferences used by tempo randomization on Compose; preference changes do not edit project tempo. |
| Instrument pool presets | `InstUtils` pool catalogues; `gui/ExtraSettingsGUI.java`: `bannedInsts`, `useAllInsts`, `reinitInstPools` as source context | Configurable pool library with import/export/reset; genre collections later. No legacy-switch parity requirement. |
| Section instrument identity | Section customized `InstPart` instruments and Java playback/export consumers | Sparse section/track identity override and explicit playback/MIDI routing. |
| Per-control locks | Angular design | Per-parameter randomization permission; instrument lock uses the same system. |
| Parameter linking | Angular design; `arpCopyMelodyInst` only as legacy context | General compatible-parameter links, including instrument choice. Do not add the special one-time Arp#1 copy rule. |

Other remaining differences and their unplanned status are listed only in the [future-work plan](CONTEXTUAL_CONTROLS_PLAN.md#other-remaining-differences-no-implementation-planned).

## Exclusions and deliberate role differences

Keep these exclusions; they are not missing work:

- Exclude `LegacyMelodySkeletonGenerator`, `melodyLegacyMode`, `melodyUseOldAlgoChance`, `melodyAvoidChordJumpsLegacy`, and legacy-only uses of `melodyFirstNoteFromChord` / `randomChordNote`, per the translation rules.
- Omit `alternatingRhythmChance` and Basic chords only: they have no applicable current block/expansion pitch consumer.
- Keep Melody Doubled rhythm, Lead chords, Max exceptions, Note exceptions, global Single note exceptions and Arpy surprises excluded. The fixture exclusions are documented in the [test guide](frontend/scripts/README.md).
- Keep the removed Arpeggio Correct by melody feature and project-wide Melody pattern flip excluded. Part custom-duration/grid Pattern flip has separate ownership.
- Bass has no production Pause consumer or GUI pause-baking/growth branch; do not add ineffective Bass controls. Automatic Bass MELODY1 choices resolve to FULL, while Chords creation does not select MELODY1.
- Accent is Melody-only; voice expansion belongs to Chords/Arpeggio. A drum track owns one percussion note and rhythm.

Java panel paths using unseeded randomness require an explicit deterministic Angular seed policy; do not claim replay compatibility with an unrecorded Java run. Java contextual phrase-planner and block-generator behavior are distinct comparison targets. Detailed inference, section-timing and creation differences remain with their [implementation records](__work_history/README.md).
