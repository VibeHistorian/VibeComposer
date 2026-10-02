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

### Phase 3 — Isolate chord progression generation

- Move progression selection and key-change decisions out of `MidiGenerator` into a progression-focused collaborator.
- Keep the chord-name and mapped-chord output paths behaviorally distinct at first. They may share helpers, but do not force them into one algorithm until their differences are understood.
- Clarify how progression output, durations, and melody-inferred alternate progressions are handed to later phases.
- Keep existing static or public progression helpers as delegating compatibility methods while callers migrate.

A `ChordProgressionGenerator` is a likely home for this work; key-change policy can remain there or be separated if it proves independently useful.

**Completion signal:** progression algorithms can be understood and changed without navigating score assembly or instrument phrase generation.

### Phase 4 — Reduce `generateMasterpiece` to orchestration

- Extract named operations for generation setup, progression preparation, per-section processing, score creation, and generated-result publication.
- Move section-specific variation, part substitution, custom chord/duration handling, and presence decisions into a section planner where they form a coherent unit.
- Make temporary changes to shared configuration or section data explicit, including where they are restored if generation exits early.
- Preserve the existing order of side effects while the boundaries are being established.

An `ArrangementGenerationPlanner` or `SectionGenerationPlanner` may fit the section decisions. Keep cross-stage sequencing in `MidiGenerator` unless another owner clearly owns the whole workflow.

**Completion signal:** the top-level generation method reads as a sequence of stages, with section rules separated from overall orchestration.

### Phase 5 — Separate score assembly and instrument phrase generation

- Move score and track construction, track assignment, track combination, padding, and score-level post-processing behind a focused score builder or assembler.
- Extract melody phrase rendering separately from melody skeleton generation. The former turns generated melody data into a phrase for a section; the latter creates the notes.
- Move bass, chord, arpeggio, and drum phrase algorithms into instrument-focused collaborators. Split particularly large algorithms into named steps before or during extraction.
- Keep shared phrase utilities shared only when their behavior is genuinely common; avoid a generic instrument generator with many instrument-type branches.
- Preserve callback boundaries such as sequence-track assignment so offline generation can continue to use a no-op assignment path.

Candidate names include `MidiScoreBuilder`, `MelodyPhraseBuilder`, `BassPhraseGenerator`, `ChordPhraseGenerator`, `ArpPhraseGenerator`, and `DrumPhraseGenerator`.

**Completion signal:** score assembly and each instrument’s phrase rules have focused owners, while `MidiGenerator` coordinates their use.

### Phase 6 — Migrate mutable state by lifetime

Perform this phase incrementally alongside earlier extractions when a collaborator needs a clearer input. Avoid a single large static-state migration.

- Introduce a per-run context for configuration and generation data such as current section, progression and durations, part snapshots, and melody-generation inputs or outputs.
- Move generated chord, target-note, and user-melody state to the owner that matches its actual lifetime. Preserve compose/regenerate behavior while callers are being migrated.
- Give application-lifetime values such as score history an application/session owner rather than storing them in an algorithm class.
- Consolidate duration constants and mutable duration settings only after auditing external callers. Derived timing values should have one clear owner.
- Migrate callers in groups, including GUI consumers and utility classes that currently read generator statics. Remove compatibility fields only after the final caller has moved.

**Completion signal:** internal generation collaborators no longer depend on mutable global state for their inputs, and any remaining compatibility surface has known callers and a removal path.

### Phase 7 — Consolidate boundaries and document the result

- Review collaborator APIs for leaked implementation details, duplicated state, or dependencies that point back to `MidiGenerator` or `MelodyGenerator` unnecessarily.
- Remove obsolete delegation scaffolding and compatibility accessors whose callers have migrated.
- Update this plan and the relevant refactor map with the final ownership boundaries and any intentional remaining shared state.
- Use the project’s established compile check at appropriate migration boundaries; do not treat the number of classes or lines as a success metric.

**Completion signal:** the two generator classes act as understandable coordinators, collaborators have focused inputs and outputs, and remaining shared state is deliberate and documented.
