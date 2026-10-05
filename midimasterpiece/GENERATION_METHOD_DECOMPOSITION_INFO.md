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

## Verification

- `mvn compile` — passed.
- `mvn -Dtest=GeneratorRegressionTest test` — passed; 1 test, 0 failures, and generated MIDI matched the reference bytes.
- After Phase 2 and Phase 3 — `mvn compile` passed; `mvn -Dtest=GeneratorRegressionTest test` passed with 1 test, 0 failures, and generated MIDI matching the reference bytes.
