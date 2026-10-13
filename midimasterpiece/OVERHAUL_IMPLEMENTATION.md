# Melody and harmony overhaul: implementation record

Updated: 2026-10-13. Implementation currently targets the Java application. This is the first functional increment of the [melody](MELODY_GENERATION_OVERHAUL_PLAN.md) and [chord/chordlet](CHORD_GENERATION_OVERHAUL_PLAN.md) roadmaps, not completion of every phase.

## Using the new behavior

- In Melody settings, enable **Contextual Melody**. Legacy Mode takes precedence; disable it to use the new engine. A supplied user melody continues through its existing path.
- Select **OPEN**, **CLOSED**, or **LOOP** for ending intent. CLOSED prefers a final tonic when the final chord supports it; it does not fabricate a cadence over arbitrary fixed harmony.
- Set **Melody Low MIDI**, **Melody High MIDI**, and **Melody Tension**. The range is measured before track/global transposition. Speed controls activity; Max Block Change supplies the realized leap allowance. Pattern offsets provide recurring rhythm identities.
- In chord settings, choose **FUNCTIONAL**, **FIFTHS**, **POP_LOOP**, or **MODAL**. **CURRENT** retains the previous generator. Endpoint selections and locked chordlets constrain the new planners.
- **Ctrl+click a chordlet** to lock its entire chord definition; a white outline marks it. **Alt+click** rerolls one unlocked event while evaluating both neighboring joins. Manual editing remains possible even on a generation-locked event.
- The **S** action now adds extensions while preserving the existing root, core quality, inversion, and locked chords. **Undo** restores the preceding progression/color/local-reroll edit, including lock state.

Existing XML presets without the new settings select the compatibility engines. New settings and chordlet locks are serialized. The UI does not automatically switch an existing project to the new generators.

## Delivered foundation

`HarmonyTimeline` snapshots chords, independent harmonic roots, display identities, and exact boundaries at 960 ticks per beat. Its arrays and pitch-class sets are defensively copied. Boundary intervals are half-open; overlap queries include every affected harmony event. Inversion does not redefine the root. Timing outside the supported tick precision returns a clear error rather than silently accumulating drift.

When either new generator is selected, section ii-V/arrival replacements happen before melody rendering. The section's progression and durations are frozen before dependent parts consume them. Contextual generation bypasses the old melody-only progression swap. Section display names for generated harmony are derived from the frozen events rather than stale base names. Locked endpoints conflicting with ii-V changes are reported rather than overwritten.

The app still uses its existing normalized-key/mode-rendering conventions. The contextual melody adapter realizes the current mode before scoring and avoids a second mode transformation. This initial snapshot is not yet the full spelled, function-annotated, revision-based harmony model specified in the plans.

## Delivered melody behavior

`ContextualMelodyPlanner` searches realized MIDI pitches with a bounded deterministic beam. It uses rhythmic/metric importance, local harmony, overlapping chords, register/contour preference, actual leap size, leap recovery, dominant-resolution preferences, recurring gesture direction, and ending/loop intent.

Strong or long events require chord compatibility in this initial policy. Weak passing/neighbor notes must have an appropriate stepwise approach and immediate stable resolution. Rests cannot erase an unresolved ornament. Chromatic chord tones remain eligible even when absent from the global scale. The result includes pitches, timing, roles, score, and candidate count.

The adapter consumes active section durations instead of assuming global chord durations. It supports recurring generated rhythm, pauses/fill, custom duration input, velocity bounds/section volume, and bounded articulation. Its custom-rhythm conversion preserves authored order, gaps/rests, and source data; it bypasses the historical helper's clone-ordering issue. Custom MIDI/user melody editing remains on the existing path. Articulation spill into an incompatible following chord is shortened after swing/length processing.

Tonic/mode/chord quotas and universal avoid-note mutation are bypassed by the contextual engine. An impossible request or exhausted bounded search reports the failing tick; range, pitch locks, and rhythm are not silently relaxed. Pure planner pitch locks are available to callers; a per-note lock UI is not implemented yet.

Compatibility-specific block controls are not all mapped to the new engine. Block-type weights, block-jump approximation, generated-block chance, target-note offsets, and the old exception/splitting policies continue to apply to the existing block engine. Contextual melody uses its new policy rather than claiming parity with these controls.

## Delivered chord behavior

`HarmonicProgressionPlanner` provides:

- Functional phrase scoring with predominant/dominant/tonic arrival preferences.
- The diatonic descending-fifths pattern, including its diminished chord and diatonically adjusted tritone root link.
- Pop-schema loops with evaluation of the final-to-first join.
- A basic modal-vamp preference for a retained center rather than obligatory dominant closure.
- Lookahead through a bounded progression beam, hard chord/endpoint locks, and similarity rewarded during planning rather than copied destructively afterward.
- Deterministic extension-only color and a local reroll that changes only the selected event.

The two chord-generation entry points share the new planner. Matching existing duration lists are retained. Root spelling, accidental, quality, and inversion are parsed independently; color does not turn a dominant seventh into a major seventh or a minor chord into a major chord. Flat/Unicode accidental mapping is supported in the chord parser, and chordlet reset retains accidental roots and inversions.

Independent correctness fixes include the circle-index precedence error, the wrong tonic-insertion index in target selection, and the minor-ninth definition (minor third and minor seventh). Respice now accepts an explicit seed and does not reconstruct the chord from its first letter. The historical respice routine remains separately named for comparisons.

`MelodicMotion` supplies correct pitch/displacement reversal measurements and realized leap size for the overhaul. The original block generator's historical direction-budget and quota-correction behavior is not rewritten in this increment, preserving its reference MIDI output.

## Validation

Tests are in `src/test/java/org/vibehistorian/vibecomposer/generation/`:

- `ContextualGenerationTest`: exact/immutable harmony boundaries, independent roots, overlap checking, passing/neighbor roles, chromatic chord tones, cadence arrival, deterministic bounded search, range/lock conflicts, fifth sequences, functional phrases, endpoint preservation under maximum color/similarity, identity-preserving color, local rerolls, parser correctness, persisted locks, and config compatibility/round-trip.
- Its full-pipeline case generates MIDI from the existing reference arrangement with the contextual engine selected. Temporary output files are cleaned up; the existing temporary MIDI is restored.
- `ChordletGenerationTest`: actual Swing editor operations on the event dispatch thread, including lock-aware local reroll/undo and accidental/inversion preservation.
- Existing `GeneratorRegressionTest`: byte-for-byte historical MIDI fixture comparison.

Run from `midimasterpiece`:

```text
mvn -Dtest=ChordletGenerationTest,ContextualGenerationTest,GeneratorRegressionTest test
```

Automated correctness and MIDI export are verified independently from perceptual quality. No blinded listening result or completed visual UI audit is claimed by this increment.

Latest verification: Maven build and all 23 tests passed, including full contextual MIDI export, Swing chordlet operations, custom rhythm preservation, XML round-trip, and the unchanged historical MIDI fixture. Documentation links and `git diff --check` also passed.

## Remaining roadmap

| Area | Remaining work |
| --- | --- |
| Full harmony model (M1/H1) | Stable event IDs/revisions, spelling/function/context metadata, atomic publication across all consumers, and regeneration/invalidation of edited spans. |
| Phrase planning (M2/M3) | Explicit statement/continuation/question-answer roles, jointly revised pitch/rhythm candidates, anchors independent of chord-boundary attacks, and style-dependent tension policies. |
| Contextual blocks (M4) | Instantiate the existing catalogue from actual chord members; planned anticipations, suspensions, appoggiaturas, chromatic approaches, and richer motif transformations. |
| Melody controls/integration (M5) | Map or retire every old block control, expose anchor/note locks, local melody rerolls, variation/embellishment policies, and complete validation after all timing effects. |
| Harmony operations (H3/H5) | Typed quality changes, applied dominants, mixture, destination-aware substitutions, and proper minor-key/modulation planning. The initial functional strategy uses normalized major-degree roles; modal realization does not guarantee classical minor cadence behavior. |
| Voicing/editor (H4) | Voice-continuity optimization, guide-tone omission rules, independent root/quality/duration/bass locks, ranked alternatives, preview/audition, and broader undo/redo. |
| Harmonization (H5) | Time/accent-weighted fixed-melody harmonization and an explicitly bounded co-design workflow. Contextual generation currently advertises fixed harmony rather than an inferred alternate progression. |
| Evaluation (M6/H6) | Fixed-seed multi-style listening corpus, audio exports, blinded comparison, runtime profiling, tuning, and a decision on defaults. |
| TypeScript | Port the new contracts/algorithms independently from existing Java-parity fixtures and follow `../VibeComposerJS/TRANSLATION_RULES.md`. |

The next coherent implementation unit is explicit phrase/anchor planning and contextual block realization on the delivered timeline, followed by guide-tone-aware voicing and typed chromatic harmony operations. Keep those changes separate from the compatibility fixtures and expose actual coverage rather than marking the entire roadmap complete.
