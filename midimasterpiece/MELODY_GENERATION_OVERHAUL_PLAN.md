# Melody generation overhaul plan

Status: Java implementation extended on 2026-10-13 through phrase/anchor planning, contextual catalogue realization, ornament roles, style profiles, local locks/rerolls, fixed-melody preservation, and a MIDI/audio comparison corpus. Listening/tuning and default-selection gates remain open. TypeScript is deferred. See [implementation status and usage](OVERHAUL_IMPLEMENTATION.md).

Companion: [Chord and chordlet generation overhaul](CHORD_GENERATION_OVERHAUL_PLAN.md).

## 1. Objective and scope

Replace the assumption that good chord targets plus plausible pitch shapes necessarily make good melodies with a generator that plans phrases, places musical events in time, and evaluates their harmonic and melodic relationships.

Keep the useful parts of the current system: seeded generation, reusable blocks, recurring patterns, custom rhythms, section variations, and editable targets. Blocks become candidate material within a musical plan. They cease to be the primary definition of musical correctness.

The first implementation should produce controllable tonal/pop melodies over fixed harmony. Modal, chromatic, and jazz behavior should follow through explicit style policies. Do not enforce common-practice voice-leading rules across all styles. Do not add the old legacy melody algorithm to the TypeScript rewrite.

This is a behavior overhaul, distinct from the behavior-preserving extraction work in `refactors_history/GENERATOR_REFACTOR_PLAN.md`. New output is expected. Preserve existing presets and generation through an explicit compatibility path while comparing the new engine.

## 2. Current implementation and diagnosed weaknesses

Paths below are relative to `src/main/java/org/vibehistorian/vibecomposer/`.

| Area | Current code | Consequence |
| --- | --- | --- |
| Targets | `generation/MelodyTargetUtils.java`, `MelodyBlockUtils.getStartingNote()` | Directions and proximity select targets, without modeling phrase importance or pending tendency-tone resolutions. |
| Bridge planning | `MelodyBlockUtils.blockChangeSequence()` | Random displacements are corrected to a sum, rather than selecting a musically meaningful sequence of intermediate arrivals. |
| Shape selection | `Enums/BlockType.java`, `MelodyBlockUtils.getRandomByApproxBlockChangeAndLength()` | Endpoint displacement, length, variance, and direction budget dominate; actual harmony and metric placement are absent. |
| Harmony checks | `MelodyBlockSkeletonGenerator.selectBlockShape()` | The CHORDY check uses fixed C-major starting degrees rather than the sounding chord. Shape labels do not establish passing, neighbor, or arpeggio function. |
| Rhythm | `createBlockRhythm()`, `reconcileBlockDurations()`, `assembleChordNotes()` | Pitch and rhythm are joined after selection; key emphasis can reorder durations without evaluating local harmonic tension. |
| Boundaries | `selectBlocksForChord()`, `assembleChordNotes()` | Exact bridges put the next chord's target at the end of the current chord. Approximate endpoints can disagree with the planned displacement used for the next block start. |
| Corrections | `MelodyExpansion.applyNoteTargets()`, `MelodyNoteUtils` | Pitch-class quotas and broad avoid-note rules can destroy purposeful ornaments, motifs, and resolutions. |
| Final harmony | `generation/MidiGenerator.renderSection()` | Some ii-V replacements occur after melody parts are filled. A better melody engine cannot compensate for consuming the wrong harmony. |

Internal C-major generation followed by transposition is not inherently wrong. The weakness is losing chord spelling, interval quality, and local chromatic context before evaluating musical choices. Existing modal and borrowed-chord adjustments mitigate some cases; they do not replace contextual planning.

### Concrete correctness fixes to isolate first

- Separate reversal counting for a sequence of displacements from reversal counting for pitch positions. `calculateDirectionChanges()` currently sends displacements to the pitch-position function.
- Count reversals in zero-net-change shapes, e.g. `[0,2,1,0]`, and carry the actual preceding motion across block boundaries.
- Fix the generated-block direction budget: the update guarded by `!restrictDirectionChanges` currently skips ordinary nonzero-endpoint cases.
- In `MelodyExpansion.applyChordNoteTargets()`, either use the constructed `sortedNotes` or replace the entire correction policy; the present mutation loop iterates over `notes`.
- Fix target insertion indexing in `MelodyTargetUtils`: the candidate chord is checked at `order`, but the tonic offset is written at `i`.
- Audit the fallback that replaces an empty candidate set with a loose random block. Never report constraints as satisfied after silently bypassing them.

These are independently reviewable fixes. They are not sufficient to deliver the musical overhaul.

## 3. Shared foundation: finalized harmony and exact event timing

Both plans require an immutable harmony timeline. Each event has an identity, exact start and duration, key/mode context, chord definition, structural function or an explicit unknown function, bass intent, and permitted tensions. Voicings are derived separately.

Represent time in integer ticks with a documented resolution, or exact rational beats; do not accumulate floating-point beat comparisons through planning. Define time boundaries as half-open intervals. Evaluate a sustained note against every harmony event it overlaps, not only the chord present at its onset.

Resolve section overrides, harmonic substitutions, modulation preparation, and melody-derived harmony before rendering dependent parts. Every part consumes the same timeline revision. A subsequent harmony edit invalidates affected generated material; locked user notes are preserved and used as constraints.

For an initial melody-only prototype, construct this timeline from a fixed progression without requiring the new chord generator. The chord overhaul can later supply richer metadata through the same contract.

## 4. Proposed musical model

Names are suggested responsibilities, not a requirement to create one class per row.

| Model | Required information |
| --- | --- |
| `MelodyRequest` | Harmony revision, meter, tempo, section intent, range/tessitura, style, seed, existing motifs, user locks. |
| `PhrasePlan` | Phrase spans, opening/continuation/closing roles, arrival strengths, cadence intent, climax region, density and rest envelope. |
| `RhythmPlan` | Onsets, durations, accents, ties/rests, motif identifiers, explicitly permitted anticipations and suspensions. |
| `MelodicAnchor` | Exact event position, candidate pitches, structural importance, harmonic role, resolution obligations. |
| `MelodyCandidate` | Realized pitches and timing, ornament roles, motif transformation, boundary state, score components. |
| `MelodyResult` | Notes, chosen plan, harmony revision, diagnostics, seed/algorithm identity, unresolved optional preferences. |

Distinguish MIDI pitch from spelled pitch and scale degree. Diatonic degree offsets remain useful for motif transformations; semitone intervals determine realized leap size, and spelling determines interval/function identity. Pitch-class equality alone is insufficient.

Represent note roles such as stable chord tone, permitted extension, passing tone, neighbor, appoggiatura, anticipation, suspension, and chromatic approach. A role has conditions, not just a label. For example, a passing tone requires an appropriate stepwise connection; a suspension requires preparation, overlap, and resolution. Some styles allow deliberate unresolved tensions.

## 5. Generation pipeline

### A. Plan phrase purpose before connecting notes

Start with a small family of editable phrase templates: a repeated statement with a changed ending, a question/answer pair, and a continuous loop. Fit them to the actual section length and harmonic rhythm. Support short and odd-length sections without forcing an eight-bar template.

Choose a few structural arrivals across the phrase. A chord change need not be an anchor, and an anchor need not be a new attack at a chord boundary. Specify whether the phrase should close, remain open, or reconnect smoothly to its beginning.

Plan a broad tessitura and climax region, not a hard rule requiring every melody to form a single arch. Avoid competing high points unless repetition or style makes them intentional.

### B. Plan rhythm and accents, then refine them jointly with pitch

Generate a recurring rhythmic motif with controlled variants. Preserve recognizable onset/accent relationships across repetitions. Place rests and density changes according to phrase role.

Metric weight must include beat hierarchy, duration, articulation, and phrase position. Long duration and downbeat placement are related but distinct. Respect custom-duration locks and tuplets.

Rhythm-first is a practical search order, not a claim that pitch and rhythm are independent. Retain several rhythm candidates and allow local rhythmic revision when pitch roles cannot be realized convincingly. Strict custom rhythms remain fixed.

### C. Select anchors with lookahead

Build a small candidate set for each anchor from chord identity, function, allowed extensions, register, and style. Score sequences of anchors, rather than selecting every target independently.

Carry obligations such as a leading tone approaching tonic or a dominant seventh resolving downward when the chosen style requests that behavior. Reward common-tone continuity, intentional contrary motion, and a convincing cadence. A chord tone may be tense; a non-chord tone may be an appropriate anchor if its role and resolution are planned.

Use bounded beam search initially. Candidate order and tie-breaking must be deterministic. Fix the beam width and candidate budget in settings; record them with results. Avoid a full combinatorial search over every MIDI pitch and tick.

### D. Generate connecting candidates with role-aware blocks

Adapt the current catalogue into contour/rhythm templates with applicability metadata. Instantiate a CHORDY block from actual chord members, rather than universally stacking scale thirds. Instantiate neighbor and passing figures relative to their stable notes and harmony.

Allow diatonic sequence, interval-preserving sequence, inversion, rhythmic displacement, fragmentation, and changed endings as distinct motif operations. They are not interchangeable transformations. Define what remains recognizable under each operation.

Candidates must include neighboring context. Evaluate the incoming interval, internal motion, outgoing interval, and harmonic overlap. Carry actual realized endpoints forward. If the endpoint tolerance is used, recompute the boundary state; never keep an inconsistent planned displacement.

Reaching the next chord's target early is an explicit anticipation choice. Its onset, duration, accent, and harmonic compatibility must pass the anticipation policy. A suspension must be created through a tie/overlap and resolution, not approximated by an unrelated note after the change.

### E. Score complete local sequences and phrase outcomes

Reject hard violations first. Rank viable candidates using interpretable, normalized components:

- Harmonic fit weighted by accent, duration, register, and chord overlap.
- Prepared/resolved tensions and retained tendency-tone obligations.
- Actual interval size and quality, leap recovery, repeated-leap patterns, and tessitura.
- Motif identity, useful variation, and excessive repetition or novelty.
- Phrase arrival, climax placement, density/rest shaping, and ending/loop continuity.
- Relationship to bass and prominent accompaniment notes when available; otherwise use harmonic/bass intent, without pretending to know final voicings.

Hard constraints cover valid timing/pitches, user locks, declared range, and structural requirements explicitly selected by the user. Most theory rules remain style-weighted preferences. A hard cadence requirement is only hard when requested.

Normalize scores by musical exposure or opportunities, not raw note count, so adding ornaments does not automatically improve a score. Keep the score breakdown available for development and optional explanation, rather than exposing implementation metrics as default musical controls.

### F. Apply performance effects and validate the realized result

Humanization, swing, articulation, and embellishment may alter harmonic overlap or melodic intervals. Apply bounded effects, then revalidate affected relationships. Embellishment uses the same role rules as ordinary generation.

Retire broad pitch quota corrections and universal avoid-note replacement from the new engine. If repair is needed, regenerate a small affected span with its boundary conditions and motif identity intact. Preserve user locks.

If the search fails: retry alternative candidates, simplify ornaments, permit repeated stable notes/rests where legal, then return an explicit unsatisfied-constraint result. Never move locked notes, alter fixed harmony, or silently exceed range to hide failure. Optional stylistic preferences can be relaxed in a documented order.

## 6. Controls and reproducibility

Use a small set of musical controls: phrase/loop ending, activity, rhythmic repetition, melodic repetition, contour, range, leap allowance, tension, chromaticism, and variation. Group related controls; do not expose every scoring coefficient initially.

Map existing settings explicitly. Speed influences density and rhythmic values; block jump becomes a realized leap preference/bound; direction changes become contour complexity; target-note choices become optional anchor locks/preferences; pattern offsets become motif recurrence. Existing tonic/chord percentages remain compatibility controls until replaced by accent-weighted stability and arrival intent.

Separate deterministic random streams for phrase, rhythm, anchors, connecting material, and performance. Derive streams from stable phrase/event/motif IDs. Local rerolls should not perturb unrelated phrases. Stable IDs survive reorder and serialization.

Maintain exact Java-random compatibility in the existing translation path. The new algorithm has its own explicit identity and documented stream derivation; seeded reproducibility does not mean preserving historical MIDI bytes. Do not disguise a new algorithm as a faithful port of the old one.

## 7. Implementation phases and gates

| Phase | Work | Completion gate |
| --- | --- | --- |
| M0: evidence and defects | Capture representative seeds/MIDI; isolate the correctness fixes above; record actual defaults and control consumers. | Repeatable baseline corpus and focused behavior tests; intended fixture changes explained. |
| M1: timeline and context | Add immutable harmony/timing adapter; fix late harmonic replacement ordering; pass meter/range/locks explicitly. | Melody and accompaniment share one harmony revision; overlaps and section boundaries are tested. |
| M2: phrase and rhythm planning | Add initial phrase templates, metric hierarchy, motif rhythms, custom rhythm handling. | Phrase roles and repeated rhythms survive varying chord durations, meters, and section lengths. |
| M3: anchor planning | Add candidate anchors, function-aware lookahead, cadence/loop policy, style profiles. | Controlled examples demonstrate arrivals and resolution choices without forcing every chord change into an attack. |
| M4: contextual blocks | Instantiate role-aware blocks; evaluate realized intervals and boundaries; bounded deterministic search. | Passing/neighbor roles, anticipations, suspensions, and leap behavior pass the musical cases below. |
| M5: integration | Replace quota mutation; validate effects; map controls; implement local rerolls and diagnostics. | Presets/locks/custom rhythms remain usable; new engine can be selected and compared. |
| M6: evaluation and default | Tune with blinded listening and fixed-seed corpus; profile runtime; decide new defaults. | Preference improves across multiple styles without range/timing/lock regressions. |

Implement M1 and the chord plan's shared timeline phase together. M2-M4 can use fixed user harmony before the new progression planner is complete. Keep each phase independently usable and reviewable.

## 8. Validation and listening corpus

Automated checks validate relationships and determinism, not an assertion that a numeric score proves musical quality.

| Case | Required observation |
| --- | --- |
| C major, C-D-E | Weak passing D is retained; an accented/long D requires an allowed tension role or a different candidate. |
| G7-C | In a functional closing profile, B-C and F-E are available and preferred when applicable; other styles may choose different outcomes. |
| C-Dm | F before the boundary is scored as anticipation against C; no automatic long accented F is assumed safe. |
| E-G-B over C | The shape is evaluated as actual pitches against C, not accepted merely because it is CHORDY. |
| `[0,7]`, `[0,2,1,0]` | Octave leap and zero-net reversal receive correct interval/contour measurements. |
| Am-E7-Am | G-sharp remains available as the local leading tone; global-scale snapping does not erase the dominant's identity. |
| Sustained note over a change | Both harmony spans are evaluated; a planned suspension retains preparation and resolution. |
| Repeated motif under new harmony | Motif identity is preserved with deliberate harmonic adaptation, not arbitrary pitch repair. |
| Loop and closing phrase | Last-to-first continuity and final closure are separate objectives with observable differences. |
| Fixed rhythm, target locks, narrow range | Locks survive; impossible requests return deterministic diagnostics. |
| Late chord edit / section ii-V | All dependent generated notes use the new harmony revision; locked notes remain intact. |

Use diatonic pop loops, functional cadences, natural-minor and harmonic-minor contexts, modal vamps, borrowed chords, uneven harmonic rhythm, short/odd-length sections, and several registers/tempos. Export matching MIDI and audio with the same instruments, loudness, and seed lists. Compare current, defect-fixed, and new engines without labels during listening.

Track timing validity, role-resolution success, accent-weighted harmonic fit, interval distribution, motif retention, range compliance, failure rate, and runtime. These support listening; none is a standalone quality target. Set performance limits after measuring the corpus on the target desktop/browser.

## 9. Code placement and translation

Keep `generation/MelodyGenerator.java` as the orchestration seam while the new planner is introduced. Reuse `MelodyBlock` only if its pitch/duration representation can express roles and timing clearly; do not overload its existing integers with incompatible semantics. Keep playback/rendering and harmonic inference separate from candidate search.

TypeScript counterparts already exist in `../VibeComposerJS/frontend/src/app/core/music/`: `melody-generator.ts`, `melody-context.ts`, `melody-blocks.ts`, `melody-catalogue.ts`, `melody-targets.ts`, `melody-expansion.ts`, and the tonal/chord-target modules. The shared harmony seam is `project-harmony.ts` with `harmony.ts` and `harmony-voicing.ts`.

If the overhaul is implemented in TypeScript, follow `../VibeComposerJS/TRANSLATION_RULES.md`: keep core logic framework-independent, preserve bigint seed inputs, use the existing workspace/inspector and undo model, and exclude the old legacy melody algorithm. Add new-engine fixtures independently from Java-parity fixtures. Portability is a contract; this plan does not require simultaneous changes to both applications.

## 10. Risks and deferred work

The largest risks are a technically correct but bland engine, too many style rules, search cost, and conflicting user locks. Address them with multiple candidates, modest style policies, deterministic bounds, and explicit failure outcomes.

Defer learned scoring, full counterpoint, lyric prosody, continuous joint melody/harmony optimization, and automatic style recognition. Permit chromatic approaches and modal profiles after the first tonal engine works; avoid equating richer theory coverage with better default melodies.

## Theory references

- [Open Music Theory: embellishing tones](https://viva.pressbooks.pub/openmusictheory/chapter/embellishing-tones/) — role, preparation, and resolution definitions.
- [Open Music Theory: tendency tones](https://openmusictheory.github.io/tendencyTonesFunctionalDissonances.html) — context-dependent melodic obligations.
- [Open Music Theory: harmony and phrase endings](https://viva.pressbooks.pub/openmusictheory/chapter/intro-to-harmony/) — coordinated melodic/harmonic closure.

The architectural choices and search policies in this plan are proposals based on the code review, not prescriptions from these references.
