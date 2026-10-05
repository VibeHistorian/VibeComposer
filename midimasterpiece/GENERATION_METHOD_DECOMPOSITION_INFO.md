# Generation Method Decomposition Progress

Updated: 2026-10-05

This file records completed stages from [the decomposition plan](GENERATION_METHOD_DECOMPOSITION_PLAN.md).

## Phase 1 — Arrangement-section processing

**Status: Complete.** The current `MidiGenerator` makes the major section stages explicit:

- `processArrangementSections` delegates section decisions to `prepareSectionPlan` and phrase rendering to `renderSection`, while named operations handle timeline advancement and temporary variation-setting restoration.
- `fillOtherPartsForSection` delegates bass, chord, arp, and drum phrase work to `fillBassPartsForSection`, `fillChordPartsForSection`, `fillArpPartsForSection`, and `fillDrumPartsForSection`. The shared empty phrase is created once for the section.
- `postprocessMelodyRhythmAccents` delegates pocket-aware drum-hit selection, per-phrase work, and note splitting/pitch/dynamic adjustment to `selectMelodyAccentDrumHits`, `processPhraseRhythmAccents`, and `splitNotesAtDrumHits`.

The stage boundaries preserve the existing progression, variation, section mutation, and phrase ordering.

## Phase 2 — Block-based melody skeleton generation

**Status: Complete.** The block-duration helper stages remain as recorded below. The main coordinator now takes a `SkeletonGenerationRequest` and delegates these stages:

- `prepareSkeletonGeneration` resolves effective chords and target-note choices, creates the run's seeded streams, and returns the per-call setup.
- `selectChordRhythm` resolves a cached or newly generated chord rhythm.
- `selectBlocksForChord` reuses or generates blocks and returns block changes, custom durations, and the starting note data needed for assembly.
- `assembleChordNotes` converts selected blocks to ordered notes and updates the first-pass chord melody map.
- `publishSkeletonResults` performs alternate-progression inference and publishes generated variations after the loop.

`generateMelodyBlocksForDurations` delegates these operations to focused helpers:

- `getBlockDuration` resolves the effective duration for a block, including custom user durations.
- `createBlockRhythm` selects duration weights and creates the block rhythm.
- `shouldGenerateNewBlock` retains the seeded decision and resets the block-note random source at its original point.
- `getForcedBlockLength` resolves forced lengths from explicit settings or custom note durations.
- `selectBlockShape` chooses and, when needed, replaces the block shape, returning the selected type and notes in `BlockShape`.
- `reconcileBlockDurations` uses matching custom durations or asks the selected rhythm to make durations.

The outer block loop still updates variance and direction budgets, applies arpy-surprise timing, and constructs `MelodyBlock` objects in their existing order. The `generateMelodyBlockSkeletonFromChords` measure/chord loop retains variation decisions and seeded draw positions. The final `MelodyBlock` representation and duration ordering remain unchanged.

## Phase 3 — Skeleton expansion and note targeting

**Status: Complete.** `convertMelodySkeletonToFullMelody` now sequences four stages:

- `expandSkeletonNotes` traverses and splits the skeleton, returning ordered notes grouped by chord and the pause/filler inputs.
- `applySkeletonPitchAdjustments` applies note targets, bad-interval removal, and avoid-note replacement.
- `applySkeletonPausesAndFiller` selects pauses and combines filled pauses while preserving the chord-indexed map.
- `finalizeExpandedMelody` repairs chord starts and applies accent lengths, delayed starts, and variation-specific timing.

`applyNoteTargets` remains the coordinator for tonic, mode, and chord targets. Custom-duration mapping and conversion remain in `MelodyExpansion`, with durations handed off by melody block.

## Phase 4 — Legacy melody algorithm and utility families

**Status: In progress.** The legacy melody fallback now delegates setup to `prepareLegacySkeletonSetup`, chord rhythm selection and surprise handling to `generateLegacyChordDurations`, chord pitch/note generation to `generateLegacyChordNotes`, and final progression/variation publication to `publishLegacySkeletonResults`. Seeded random sources are created in the setup stage, and each chord continues to consume its rhythm, exception, pitch, and duration draws in the original order. Mutable pitch direction, jump range, and per-measure pitch carryover are explicit in small state objects.

The remaining Phase 4 work is the cohesion review and any warranted extraction from `MelodyUtils` and `MidiGeneratorUtils`.

The block utility family has also moved to `MelodyBlockUtils`: block selection, block-change sequences, shape measurements, weight normalization, and generated block shapes live there. Existing `MelodyUtils` methods delegate to it for compatibility, and `MelodyBlockSkeletonGenerator` calls the focused owner directly.

Direction construction and chord-aware note-target selection from `MidiGeneratorUtils` now live in `MelodyTargetUtils`. The melody block, legacy melody, and arpeggio generation paths call the focused owner directly. Existing `MidiGeneratorUtils` methods, including the public GUI entry point, delegate for compatibility.

## Verification

- `mvn compile` — passed.
- `mvn -Dtest=GeneratorRegressionTest test` — passed; 1 test, 0 failures, and generated MIDI matched the reference bytes.
- After Phase 2 and Phase 3 — `mvn compile` passed; `mvn -Dtest=GeneratorRegressionTest test` passed with 1 test, 0 failures, and generated MIDI matching the reference bytes.
- After the legacy melody algorithm extraction — `mvn compile` passed; `mvn -Dtest=GeneratorRegressionTest test` passed with 1 test, 0 failures, and generated MIDI matching the reference bytes.
- After the block utility extraction — `mvn compile` passed; `mvn -Dtest=GeneratorRegressionTest test` passed with 1 test, 0 failures, and generated MIDI matching the reference bytes.
- After the direction and target utility extraction — `mvn compile` passed; `mvn -Dtest=GeneratorRegressionTest test` passed with 1 test, 0 failures, and generated MIDI matching the reference bytes.
