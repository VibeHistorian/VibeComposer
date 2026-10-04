# Midi and Melody Generator Refactor Plan

## Intent

Refactor `MidiGenerator` and `MelodyGenerator` into smaller units with clear responsibilities, while preserving generated MIDI behavior and the existing application workflows during the migration.

This document is a roadmap, not a binding design. The implementer can split, combine, reorder, or expand phases when code inspection reveals a better boundary or new dependencies. Keep each change reviewable and update this plan when the direction changes materially.

## Current shape

`MidiGenerator` currently coordinates progression generation, arrangement and section changes, score assembly, track assignment, post-processing, and phrase generation for five instrument types. Its largest areas include `generateMasterpiece`, score setup and post-processing, progression generation, and the melody, bass, chord, arpeggio, and drum phrase methods.

`MelodyGenerator` coordinates two skeleton-generation approaches (the block algorithm and legacy algorithms), expands skeletons into full melodies, applies note targets and custom durations, handles embellishment, and infers chord progressions from user melodies. Some outputs and inputs are exposed through mutable fields or static fields.

## Working principles

- Preserve random-seed construction and call order while extracting algorithms; those details affect generated output.
- Keep the existing generator entry points as delegating facades until callers have migrated.
- Pass collaborators the specific configuration, progression, section, callback, and result data they need. Avoid making a new all-purpose state or service-locator class.
- Keep compatibility fields or forwarding methods temporarily when callers still depend on them, and record their intended removal as part of the relevant phase.
- Treat line count as an observation, not a completion target. A phase is complete when its responsibility and dependencies are clearer and the behavior remains intact.

## Phases

### Phase 1 — Map behavior and define seams

- Inventory the responsibilities and callers of both generators, including reads and writes of their public and static fields. Include `MidiGeneratorUtils`, `MidiUtils`, compose workflows, GUI consumers, and offline chord generation.
- Trace the main generation sequence from the `MidiGenerator` entry point through arrangement sections, melody generation, instrument phrases, score assembly, and result publication.
- Identify state that is per generation, state that belongs to the application/session, and values that are timing constants or derived settings.
- Record behavior-sensitive ordering, especially section/config restoration, random generator seeds, track assignment, and melody target-note persistence.

**Completion signal:** the first extraction can be made without guessing about who owns a value or when a step runs. The inventory can be updated as new findings emerge.

### Phase 2 — Split melody generation by algorithm and outcome

- Keep `MelodyGenerator` as the entry point that chooses legacy or block-based generation and coordinates the result.
- Separate block-based skeleton creation from the legacy skeleton algorithms. Keep algorithm-specific random streams and decisions together.
- Separate skeleton expansion and note-target processing from skeleton creation. Group custom-duration conversion and embellishment with the part of expansion that consumes them.
- Move user-melody parsing and chord inference into a focused collaborator if that boundary remains cohesive after extraction.
- Replace direct sharing of mutable melody result fields with a small result object or explicit accessors where practical. Keep temporary forwarding APIs for existing callers.
- Keep `MelodyBlock` as its own model; review its data mutability only if extraction exposes a concrete need.

Possible collaborators include `MelodySkeletonGenerator`, `LegacyMelodySkeletonGenerator`, `MelodyExpansion`, and `MelodyChordInference`. These names and exact boundaries are suggestions, not requirements.

**Completion signal:** `MelodyGenerator` primarily coordinates melody generation, and its algorithm collaborators receive explicit inputs and return explicit results.

**Implemented 2026-10-03:** `MelodyGenerator` now selects between `MelodyBlockSkeletonGenerator` and `LegacyMelodySkeletonGenerator`, then delegates expansion to `MelodyExpansion`. User-melody parsing and inferred progression construction live in `MelodyChordInference`. `MelodyGenerationState` carries the shared melody outputs while the facade retains the public fields used by MIDI orchestration. Existing random streams and their call order remain inside the extracted algorithms.

### Phase 3 — Isolate chord progression generation

- Move progression selection and key-change decisions out of `MidiGenerator` into a progression-focused collaborator.
- Keep the chord-name and mapped-chord output paths behaviorally distinct at first. They may share helpers, but do not force them into one algorithm until their differences are understood.
- Clarify how progression output, durations, and melody-inferred alternate progressions are handed to later phases.
- Keep existing static or public progression helpers as delegating compatibility methods while callers migrate.

A `ChordProgressionGenerator` is a likely home for this work; key-change policy can remain there or be separated if it proves independently useful.

**Completion signal:** progression algorithms can be understood and changed without navigating score assembly or instrument phrase generation.

**Implemented 2026-10-03:** `ChordProgressionGenerator` now owns both chord-name and mapped-chord progression algorithms, returning mapped chords, chord names, and durations together. It also owns key-change selection for pivot, direct, and two-five-one changes. `MidiGenerator` keeps its existing progression entry point and compatibility fields, applying returned values to those fields for current callers. Timing values, seed inputs, and configured endpoint chords are passed explicitly; the two progression algorithms remain distinct. `mvn compile` and `mvn -Dtest=GeneratorRegressionTest test` passed, including the byte-for-byte MIDI fixture comparison.

### Phase 4 — Reduce `generateMasterpiece` to orchestration

- Extract named operations for generation setup, progression preparation, per-section processing, score creation, and generated-result publication.
- Move section-specific variation, part substitution, custom chord/duration handling, and presence decisions into a section planner where they form a coherent unit.
- Make temporary changes to shared configuration or section data explicit, including where they are restored if generation exits early.
- Preserve the existing order of side effects while the boundaries are being established.

An `ArrangementGenerationPlanner` or `SectionGenerationPlanner` may fit the section decisions. Keep cross-stage sequencing in `MidiGenerator` unless another owner clearly owns the whole workflow.

**Completion signal:** the top-level generation method reads as a sequence of stages, with section rules separated from overall orchestration.

**Progress 2026-10-03:** chord progression preparation and score publication are named operations in `MidiGenerator`. `SectionGenerationPlanner` owns section variation selection, buildup variation, part substitution, custom chord/duration preparation, and instrument presence decisions. The top-level loop still sequences key-change and chord-swap decisions alongside phrase rendering.

**Progress 2026-10-03:** transition selection, section key-change policy, and alternate melody-progression eligibility now live in `SectionGenerationPlanner`. The planner receives the existing variation random generator and makes its transition draw at the same point in the section sequence. Key-change selection still delegates to `ChordProgressionGenerator`, while the planner applies custom section key and scale settings and returns the pending values to the coordinator. The section loop still applies chord swaps and sequences N-1 replacement and phrase rendering.

**Progress 2026-10-04:** `generateMasterpiece` now delegates progression prepass and section sequencing to `processArrangementSections`, score-track creation to `createScoreParts`, and section-to-track assembly to `addSectionsToScoreParts`. Score construction and result publication remain named stages. The section sequence and shared-configuration restoration stay in the coordinator, with the original operation order preserved.

**Implemented 2026-10-04:** phase 4's generation setup, per-section processing, score-part assembly, score building, and result publication are now named stages coordinated by `generateMasterpiece`.

### Phase 5 — Separate score assembly and instrument phrase generation

- Move score and track construction, track assignment, track combination, padding, and score-level post-processing behind a focused score builder or assembler.
- Extract melody phrase rendering separately from melody skeleton generation. The former turns generated melody data into a phrase for a section; the latter creates the notes.
- Move bass, chord, arpeggio, and drum phrase algorithms into instrument-focused collaborators. Split particularly large algorithms into named steps before or during extraction.
- Keep shared phrase utilities shared only when their behavior is genuinely common; avoid a generic instrument generator with many instrument-type branches.
- Preserve callback boundaries such as sequence-track assignment so offline generation can continue to use a no-op assignment path.

Candidate names include `MidiScoreBuilder`, `MelodyPhraseBuilder`, `BassPhraseGenerator`, `ChordPhraseGenerator`, `ArpPhraseGenerator`, and `DrumPhraseGenerator`.

**Completion signal:** score assembly and each instrument’s phrase rules have focused owners, while `MidiGenerator` coordinates their use.

**Progress 2026-10-03:** score track assignment, padding, combination, transposition, and humanization now live in `MidiScoreBuilder`; `MidiGenerator.setupScore` delegates to it. Instrument phrase algorithms remain in `MidiGenerator` and are the remaining Phase 5 work.

**Progress 2026-10-03:** drum, bass, and chord note-pattern algorithms now live in `DrumPhraseGenerator`, `BassPhraseGenerator`, and `ChordPhraseGenerator`. Their existing `MidiGenerator` methods remain compatibility entry points and handle section-pattern publication and final phrase processing. Shared chord-span slicing rules live in `PhrasePatternUtils`. `mvn compile` and `mvn -Dtest=GeneratorRegressionTest test` pass, including the MIDI fixture comparison. Arpeggio and melody phrase rendering remain for Phase 5.

**Progress 2026-10-03:** the three instrument renderers now extend `InstPhraseGenerator`, which owns their shared timing shape, base phrase-and-variation result, configuration, and variation callback. Chord and drum results extend the common result with their instrument-specific data. The compile and MIDI fixture regression check pass after this consolidation.

**Progress 2026-10-04:** melody section-phrase rendering now lives in `MelodyPhraseBuilder`, while the arpeggio pattern and note algorithm lives in `ArpPhraseGenerator`. Existing `MidiGenerator` entry points still publish section patterns and apply instrument-specific final processing. The compile and MIDI fixture regression check pass after both extractions.

**Implemented 2026-10-04:** score assembly and melody, bass, chord, arpeggio, and drum phrase generation have focused collaborators. `MidiGenerator` coordinates them and retains the compatibility methods used by current callers.

### Phase 6 — Migrate mutable state by lifetime

Perform this phase incrementally alongside earlier extractions when a collaborator needs a clearer input. Avoid a single large static-state migration.

- Introduce a per-run context for configuration and generation data such as current section, progression and durations, part snapshots, and melody-generation inputs or outputs.
- Move generated chord, target-note, and user-melody state to the owner that matches its actual lifetime. Preserve compose/regenerate behavior while callers are being migrated.
- Give application-lifetime values such as score history an application/session owner rather than storing them in an algorithm class.
- Consolidate duration constants and mutable duration settings only after auditing external callers. Derived timing values should have one clear owner.
- Migrate callers in groups, including GUI consumers and utility classes that currently read generator statics. Remove compatibility fields only after the final caller has moved.

**Progress 2026-10-04:** score history moved from `MidiGenerator` to the application-level `ScoreHistory` owner, and the score history views now read through it. Target-note choices and the attached user melody now live in `MelodyGUI`-owned `MelodyGenerationSettings`, passed into each compose run; generated melody patterns are instance state on `MidiGenerator`. Shared chord and progression state still has callers to migrate.

**Progress 2026-10-04:** melody transposition now reads the run's `GUIConfig` held by `MelodyGenerator`. Melody target-note generation receives the progression-direction setting explicitly, and chord-spice generation receives its force-scale setting from `ChordProgressionGenerator`; these algorithms no longer read those choices through `MidiGenerator.gc`. Removed `MidiGenerator.trackList` after confirming its only operations were declaration and an unconditional clear. `mvn compile` and `mvn -Dtest=GeneratorRegressionTest test` pass, including the existing MIDI fixture comparison. This is an incremental state migration: shared chord/progression values and mutable duration settings remain for later slices.

**Progress 2026-10-04:** generated chord names are stored on each `MidiGenerator` run and exposed as a read-only result. Melody skeleton generation, user-melody chord inference, arpeggio/drum generation, and `ComposeCoordinator` consume that run-owned value. At this point GUI consumers still use a compatibility mirror; the follow-up migration below removes that mirror. Shared custom-chord inputs, progression arrays, config access, and mutable duration settings remain for later Phase 6 slices.

**Progress 2026-10-04:** generated chord names now have an explicit application-session view in `GeneratedChordState`, which publishes immutable snapshots for GUI consumers between runs. All GUI and utility callers have migrated, and the `MidiGenerator.chordInts` compatibility field has been removed. Per-run generation still owns its chord-name result.

**Progress 2026-10-04:** custom progression names, custom chord durations, and first/last chord choices are now instance state on `MidiGenerator`. `ComposeCoordinator`, `ChordGUI`, and melody chord inference use the active generator's inputs; `configureFromConfig` is now an instance operation. The regression test uses the same per-run setup, and both `mvn compile` and `mvn -Dtest=GeneratorRegressionTest test` pass with the byte-for-byte MIDI fixture unchanged. Shared config access, progression arrays, and mutable duration settings remain for later Phase 6 slices.

**Progress 2026-10-04:** removed the post-construction `configureFromConfig` and `fillUserParameters` step. `MidiGenerator` now initializes config-backed values in its constructor. `RunOptions` carries stretch, global note-length multiplier, and drum-track combination because those values come from live controls outside `GUIConfig`; compose prepares its UI-owned melody settings before construction. Config initialization remains after collaborator construction to preserve the former operation order. `mvn compile` and `mvn -Dtest=GeneratorRegressionTest test` pass with the byte-for-byte MIDI fixture unchanged.

**Completion signal:** internal generation collaborators no longer depend on mutable global state for their inputs, and any remaining compatibility surface has known callers and a removal path.

### Phase 7 — Consolidate boundaries and document the result

- Review collaborator APIs for leaked implementation details, duplicated state, or dependencies that point back to `MidiGenerator` or `MelodyGenerator` unnecessarily.
- Remove obsolete delegation scaffolding and compatibility accessors whose callers have migrated.
- Update this plan and the relevant refactor map with the final ownership boundaries and any intentional remaining shared state.
- Use the project’s established compile check at appropriate migration boundaries; do not treat the number of classes or lines as a success metric.

**Completion signal:** the two generator classes act as understandable coordinators, collaborators have focused inputs and outputs, and remaining shared state is deliberate and documented.

**Progress 2026-10-04:** reviewed the Phase 6 ownership changes. Generated chord names are run-owned during generation and published through `GeneratedChordState` for application-session GUI use; custom progression inputs and bounds are run-owned by `MidiGenerator`. No callers of the removed `MidiGenerator.chordInts` field or post-construction configuration method remain. Final API consolidation is still outstanding while progression internals and mutable duration settings remain shared.

**Progress 2026-10-04:** removed the `MidiGenerator.gc` global mirror. GUI workflows, MIDI editing, note-name rendering, drum mapping, and generation helpers now receive config from the active GUI or generation run. Drum pitch mapping cache is per generator. `mvn compile` and `mvn -Dtest=GeneratorRegressionTest test` pass with the MIDI fixture unchanged. Progression lists and mutable duration settings still need ownership and API cleanup.

**Progress 2026-10-04:** progression lists are now package-private run state. `MidiEditPopup` uses explicit generator accessors; chord and root progressions are returned as defensive snapshots, and duration replacement copies its input. The compile and MIDI fixture regression checks pass after this API change.

**Progress 2026-10-04:** Phase 7 review confirms that the remaining `MidiGenerator` generation entry points still have active callers, so their delegation methods remain useful. The unresolved shared duration values have about 150 references across generation, editing, score display, and GUI code; ownership migration needs a dedicated pass that preserves the current active-project timing behavior.

**Progress 2026-10-04:** the global duration multiplier, drum-track collapse option, generated measure length, beat lengths, start delay, and generated melody duration choices now belong to each `MidiGenerator` run. `MidiTiming` is immutable; GUI editing views use the active editor's timing snapshot through `ApplicationSessionState`. Phrase generators read run-owned timing and duration settings. `SPLIT_DURATION_MULTIPLIER` is an immutable implementation constant. No callers remain for the removed mutable duration fields or melody-duration arrays. The Phase 7 ownership map is documented in `GENERATOR_REFACTOR_MAP.md`.

**Progress 2026-10-04:** the first compile of this migration exposed local-name shadowing between run timing and instrument phrase timing, plus a static offset helper that still depended on run state. The phrase timing locals are now named explicitly and the offset helper is an instance method. `mvn compile` and `GeneratorRegressionTest` pass; the MIDI fixture remains byte-for-byte unchanged.

**Progress 2026-10-04:** `MelodyExpansion` no longer keeps a back-reference to `MidiGenerator`. It receives timing and duration settings at construction, and section, progression, and scale inputs at the expansion call. `MelodyGenerator` remains the bridge for these values; skeleton generation and chord inference still use run state on `MidiGenerator` and need a separate boundary review. Compilation and `GeneratorRegressionTest` pass after this extraction, with the MIDI fixture unchanged.


---------------------------------------------------------------

# Verification

Run mvn compile without skipping tests - GeneratorRegressionTest has to run and confirm that regeneration was identical.
The test may be modified with user permission, in case the actual generation logic is changing (i.e. intent beyond refactoring).
