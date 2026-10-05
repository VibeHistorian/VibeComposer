# Generation Method Decomposition Plan

## Intent

Split the long methods and broad utility classes in `generation` into logical stages with clear inputs and outputs. This is a follow-up to `refactors_history/GENERATOR_REFACTOR_PLAN.md`: the main generator responsibilities already have focused collaborators, so this plan concentrates on method-level decomposition and data handoffs.

Keep each change reviewable and behavior-preserving. Treat line count as a signal for investigation, not as the goal. Keep `MidiGenerator` and `MelodyGenerator` as the existing entry points while their callers depend on them.

## Working principles

- Preserve random-seed construction, random draw order, and the point at which each draw occurs. These affect generated output.
- Preserve mutation and side-effect order for sections, progression state, generated melody state, and temporary GUI configuration values.
- Pass each stage only the data it consumes. Prefer a small request or result type when it clarifies a long argument list; avoid a general-purpose generation context.
- Keep existing compatibility entry points and static utility methods delegating to extracted code until callers can be migrated safely.
- Extract cohesive work units with meaningful outputs. Avoid splitting a method into helpers that only rename fragments while retaining the same implicit state dependencies.

## Phases

### Phase 1 — Make arrangement-section processing explicit — COMPLETE

**Completed:** 2026-10-05. The section planning/rendering boundary, instrument-specific phrase stages, accent-hit selection, and note-level accent transformation are in place.

`MidiGenerator.processArrangementSections` (currently about 245 lines) sequences progression pre-processing, section-level decisions, phrase generation, timeline updates, and configuration restoration. Keep the overall ordering in `MidiGenerator`, but make each section’s decisions visible as a stage boundary.

- Separate arrangement/progression initialization and the pre-melody pass from the per-section loop.
- Introduce a per-section `SectionPlan` containing the section, seed and order, selected variations and transition, effective chord/root progressions and durations, key-change values, and section flags needed by phrase rendering. Build it at the current decision points so random draws remain in their current order.
- Have the section rendering stage consume that plan and produce the section’s melody, bass, chord, arp, and drum phrases. Return only the timeline/key state needed by the next section; keep section and generator mutations at their current points until equivalence is established.
- Give timeline advancement and restoration of temporary arrangement variation settings named operations. Preserve the current restoration order and scope.

Split `fillOtherPartsForSection` into instrument-specific operations for bass, chords, arps, and drums. Each operation should receive its part configuration, presence/variation data, active progression, and measure length, then return or assign that instrument’s section phrase list. Keep the shared empty-phrase behavior explicit.

Split `postprocessMelodyRhythmAccents` into drum-hit selection (including the pocket option), per-phrase accent processing, and note-splitting/pitch-or-dynamic adjustment. The note-level operation should receive drum-hit times, timing and accent settings, the melody part’s seeded random source, and a phrase; its result should be the adjusted note list. `findDrumHitTimes` is already a useful boundary for the first stage.

### Phase 2 — Decompose block-based melody skeleton generation — COMPLETE

**Completed:** 2026-10-05. `generateMelodyBlocksForDurations` separates block rhythm and shape selection, forced-length resolution, and custom-duration reconciliation. `generateMelodyBlockSkeletonFromChords` now accepts a `SkeletonGenerationRequest` and delegates preparation, per-chord rhythm selection, block reuse/generation, note assembly, and final progression/variation publication to named stages. Its measure/chord loop retains variation decisions and random draws at their original points.

The request carries the melody part, section, chord and root progressions, measure count, seed offset, variations, block-jump preference, progression durations, generated chord names, current progression snapshots, and modified scale. The preparation result owns effective chords, target-note choices, and seeded streams. Per-chord selection returns the reusable/new blocks and custom-duration mapping; assembly preserves note ordering and first-pass chord-map updates. Final inference and variation publication remain after the loop.

Also decompose `generateMelodyBlocksForDurations` (currently about 140 lines). Separate block duration/rhythm selection, block-shape selection, and reconciliation with forced lengths or custom user durations. Keep the final `MelodyBlock` representation and its duration ordering stable. **Complete.**

### Phase 3 — Decompose skeleton expansion and note targeting — COMPLETE

**Completed:** 2026-10-05. `convertMelodySkeletonToFullMelody` now sequences skeleton traversal and splitting, pitch-target/scale adjustments, pause and filler processing, and final chord-start/accent/delay/variation timing repairs. The chord-indexed `Map<Integer, List<Note>>` remains the output, and `applyNoteTargets` remains the coordinator for tonic, mode, and chord targets.

Custom-duration mapping and block conversion remain grouped in `MelodyExpansion`; their handoff remains durations grouped by melody block.

The expansion method’s output remains `Map<Integer, List<Note>>`, indexed by chord. Preserve note identity and list ordering where later steps depend on them.

### Phase 4 — Isolate the legacy melody algorithm and utility families

`LegacyMelodySkeletonGenerator.algoGen2GenerateMelodySkeletonFromChords` (currently about 290 lines) remains the fallback path. Split it into setup, chord-by-chord rhythm and pitch generation, and final note assembly. Keep the old-algorithm choice and its probability in `MelodyGenerator`; retain the legacy algorithm’s random streams and draw order.

After the algorithm extractions, review the large utility classes by cohesion:

- In `MelodyUtils`, group block-shape and block-change operations separately from melody-note pattern extraction, chord inference helpers, and rhythm transforms.
- In `MidiGeneratorUtils`, group direction/target-note selection separately from pitch/velocity helpers and note/phrase post-processing.
- Keep existing utility entry points as delegates during migration. Move a group only when its callers and dependencies confirm a focused owner; do not split methods solely to reduce file length.

## Handoffs and verification

Each extracted stage should have a short input/output contract before implementation proceeds. Check those contracts against the existing run-state owners in `refactors_history/GENERATOR_REFACTOR_MAP.md`, especially `MelodyGenerationState`, `MelodyGenerationSettings`, `MidiTiming`, and progression state on `MidiGenerator`.

Run the existing byte-for-byte MIDI regression check after each behavior-moving phase. If a regression appears, first compare seed construction, random draw order, list ordering, and side-effect timing before changing expected output. Keep any test-fixture change outside this refactor unless the intended work explicitly changes generation behavior.

---------------------------------------------------------------

# Verification

Run mvn compile without skipping tests - GeneratorRegressionTest has to run and confirm that regeneration was identical.
The test may be modified with user permission, in case the actual generation logic is changing (i.e. intent beyond refactoring).
