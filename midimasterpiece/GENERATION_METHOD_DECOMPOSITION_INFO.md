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

**Status: In progress.** `generateMelodyBlocksForDurations` now delegates these operations to focused helpers:

- `getBlockDuration` resolves the effective duration for a block, including custom user durations.
- `createBlockRhythm` selects duration weights and creates the block rhythm.
- `shouldGenerateNewBlock` retains the seeded decision and resets the block-note random source at its original point.
- `getForcedBlockLength` resolves forced lengths from explicit settings or custom note durations.
- `selectBlockShape` chooses and, when needed, replaces the block shape, returning the selected type and notes in `BlockShape`.
- `reconcileBlockDurations` uses matching custom durations or asks the selected rhythm to make durations.

The outer loop still updates variance and direction budgets, applies arpy-surprise timing, and constructs `MelodyBlock` objects in their existing order. The final `MelodyBlock` representation and duration ordering remain unchanged. The larger `generateMelodyBlockSkeletonFromChords` coordinator still needs its request type, preparation, per-chord selection/assembly, and alternate-progression stages.

## Verification

- `mvn compile` — passed.
- `mvn -Dtest=GeneratorRegressionTest test` — passed; 1 test, 0 failures, and generated MIDI matched the reference bytes.
