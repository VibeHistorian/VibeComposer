# Chord and chordlet generation overhaul plan

Status: implementation started in the Java app on 2026-10-13. Initial progression strategies, safe color changes, and chordlet locks/rerolls are implemented; the complete roadmap is not finished. See [implementation status and usage](OVERHAUL_IMPLEMENTATION.md) for delivered behavior, validation, and remaining work.

Companion: [Melody generation overhaul](MELODY_GENERATION_OVERHAUL_PLAN.md).

## 1. Objective and scope

Generate progressions with an explicit musical purpose, then preserve or deliberately revise that purpose through spice changes, substitutions, section variations, modulation, and voicing. Make chordlets useful for constrained generation and local harmonic editing, rather than treating each displayed chord as an isolated root-plus-suffix string.

Prioritize reliable diatonic functional and pop-loop generation, coherent extensions, accurate chord identity, and shared harmony across all parts. Offer descending-fifths sequences as one strategy. Broader substitutions, modal mixture, modulation, and reharmonization are optional later upgrades.

This changes musical behavior. It is separate from the behavior-preserving generator extraction in `refactors_history/GENERATOR_REFACTOR_PLAN.md`. Retain a comparison/compatibility path and distinguish intentional new behavior from faithful Java-to-TypeScript translation.

## 2. What the current generator actually does

Paths below are relative to `src/main/java/org/vibehistorian/vibecomposer/`.

The main generator is not a single circle-of-fifths walk. `generation/ChordProgressionGenerator.java` selects from the forward/backward adjacency maps in `MidiUtils`, substitutes spicy or parallel chords, then optionally copies earlier chords into the second half of an eight-chord progression.

The explicit `MidiUtils.progressionCircle` (`C-F-Bdim-Em-Am-Dm-G-C`) is used in `applyChordFreqMap()` as a tie-break in melody-based chord inference. It represents a diatonic descending-fifths sequence, including the diatonically adjusted F-B link. It is not the chromatic circle of twelve perfect fifths. Improving this tie-break alone will not overhaul normal progression generation.

| Finding | Evidence | Musical or integration effect |
| --- | --- | --- |
| Local adjacency rather than phrase planning | `createChordProgressionRulesMap()`, `createChordProgressionForwardRulesMap()` | No explicit tonic prolongation, cadence strength, harmonic-rhythm plan, or loop boundary objective. |
| Selection and realized harmony diverge | `applySpiceAndParallelChanges()` followed by `next = rules.get(chordString)` | Next-chord rules use the unspiced base chord even when quality/root changes alter the actual harmonic role. |
| Endpoint selection is not sufficient protection | Configured last chord is selected before spice/parallel changes and later similarity copying | The realized ending can differ from the intended endpoint or function. |
| Quality is manipulated as text | `generateSpicyChordString()`, `MidiUtils.respiceChords()` | Root parsing and substring replacements cannot reliably express extensions, altered tones, borrowed quality, and function. |
| Respice can reconstruct a different base chord | `respiceChords()` derives `baseChord` from the first letter and uses an unseeded `Random` | A no-spice result can lose an accidental or quality; repeatability is not part of the operation's input. |
| Repetition is copied after generation | Eight-chord similarity replacement | New joins, endpoint conditions, and chromatic resolutions are not re-evaluated. |
| Harmony can change after melody | `generation/MidiGenerator.renderSection()` invokes ii-V replacement after `fillMelodyPartsForSection()` | Melody and accompaniment can reference different chords. Replacement helpers update pitch arrays without atomically publishing all chord metadata. |
| Voicing and identity are mixed | Pitch arrays, squishing, chordlet inversion, `alignWithMelodyTargetNotes()` | Bass intent, harmonic root, and top-voice alignment need clearer independent semantics. |
| Track rendering can introduce future harmony | `generation/ChordPhraseGenerator.generate()` uses transition notes from the next chord | Sounding anticipations need defined policy and validation against melody/bass. |

The circle index expression `.get((circleIndex) + 1 % progressionCircle.size())` also has incorrect wraparound precedence. In the current list, `indexOf("C")` finds the first C, so the duplicated terminal C masks the usual wrap case. Fix the representation and expression together; do not describe this as a guaranteed current crash.

## 3. Shared architecture: identity, function, time, and voicing

Define a canonical immutable harmony timeline before any dependent part is rendered. Use stable event IDs and exact tick/rational-beat timing. Derive display names, root/bass data, pitch-class sets, and voicings from this source instead of maintaining unrelated mutable lists.

Suggested responsibilities:

| Model | Information |
| --- | --- |
| `ChordDefinition` | Spelled root, quality, extensions, alterations, suspensions, explicit omissions, optional custom pitch definition. |
| `HarmonyEvent` | ID, start/duration, chord definition, local key/mode, phrase role, functional interpretation(s), bass intent, locks, provenance. |
| `HarmonyPlan` | Events, phrase spans, cadence/loop goals, tension envelope, style policy, revision and seed identity. |
| `VoicingPlan` | Concrete register/voice assignments, doubled/omitted tones, bass/top-note constraints, instrument range. |
| `HarmonyEdit` | Requested operation, affected events, preserved properties, required resolutions, proposed replacement window. |
| `HarmonyResult` | Timeline, derived views, diagnostics, selected candidate rationale, unresolved optional preferences. |

Chord definition and harmonic function are distinct. G7 may be V in C, V/IV in D, or a nonfunctional sonority in a modal context. Cmaj7 and C7 share a root but imply different harmonic behavior. Function may be ambiguous or unknown; preserve that instead of assigning certainty from a root lookup.

Represent inversion/bass separately from harmonic root, and separate both from global transposition and octave placement. The current `.n` inversion syntax should be parsed through an adapter with documented legacy semantics; it is not automatically equivalent to standard slash-bass notation.

Keep original text/custom voicings for round-trip compatibility. Generated chord spellings should follow key and function (e.g. G-sharp as the leading tone of A minor), while enharmonic equality remains available for playback. A failed parse must not silently become a nearby diatonic chord.

## 4. Generation strategies

Use a shared model and evaluator with several strategies. Do not force every useful pop or modal progression into tonic-predominant-dominant syntax.

### A. Functional phrase planner — first priority

Plan broad harmonic roles and arrivals before chord identities. Initial phrase templates include tonic prolongation, predominant-dominant preparation, authentic/half/deceptive/plagal endings, and repeated phrases with changed endings.

Infer templates only for generated material. Explicit user chords and locks override a template. Define exact closure requirements: a V-I pair alone is not a complete perfect-authentic-cadence guarantee, which also depends on bass/inversion, melody, and placement.

Expand roles into key/mode-aware candidates. Use bounded lookahead or beam search to connect fixed endpoints through a viable phrase. Generating backward is a legitimate search strategy, but the resulting forward sequence must be evaluated under the same transition rules. Do not treat independently authored forward/backward tables as automatically equivalent.

Generate harmonic rhythm alongside phrase roles. Longer stable spans, increased activity in continuation, and well-placed arrivals should be available as preferences. Preserve fixed durations exactly; arbitrary or changing meters are supported by the timeline.

### B. Descending-fifths sequence — first priority

Offer explicit sequence length, starting degree, direction, exit target, and diatonic/chromatic variant. A diatonic cycle in C can contain `C-F-Bdim-Em-Am-Dm-G-C`; the diminished chord and F-B root interval are consequences of staying diatonic, not defects to correct by making every link a perfect fifth.

A chromatic dominant sequence is different: e.g. `E7-A7-D7-G7-C` successively tonicizes destinations. Derive every chord from the sequence's local target and preserve guide-tone resolutions. Do not turn every same-root chord into a random seventh quality.

Plan entry/exit and phrase placement. A sequence is usually a span within a larger phrase; repeating the entire cycle indefinitely is an option, not the default explanation of functional harmony.

### C. Pop loops and modal vamps — first or second release

Provide editable common schemas and a weighted degree-transition strategy, with last-to-first evaluation. Phrase closure may be intentionally weak. Include common-tone retention, bass contour, and rhythmic identity as objectives.

Modal vamps need a modal center and characteristic degrees rather than obligatory major-key dominants. A blues dominant-quality tonic must not automatically be classified as a secondary dominant requiring resolution.

### D. Melody-constrained harmonization — later upgrade

Replace pitch-frequency matching plus a circle tie-break with time-weighted chord candidates and sequence-level scoring. Long/accented melody notes, deliberate non-chord roles, bass constraints, and harmonic rhythm matter more than an unweighted inventory of pitches.

Support separate workflows: generate melody over fixed harmony; harmonize a fixed user melody; and reharmonize an existing melody. Avoid uncontrolled inference/regeneration cycles. An optional co-design workflow uses a fixed maximum number of passes and an explicit convergence/objective policy.

## 5. Spice and substitution policy

Replace random suffix replacement with typed operations that declare what changes and what must remain true.

| Operation | Example | Required checks |
| --- | --- | --- |
| Extension | Dm-Dm7, C-Cmaj9 | Preserve intended core quality/function, playable voicing, and compatibility with important melody notes. |
| Suspension | G-Gsus4-G | Create or preserve a resolution when requested; account for third omission and event durations. |
| Quality change | G-Gm, C-C7 | Reclassify function; re-evaluate both neighbors and tonal center. |
| Applied dominant | Am-E7-Am context | Identify local target; permit G-sharp intentionally; retain destination/resolution or explicit style exception. |
| Modal mixture | F-Fm before C | Preserve phrase purpose while evaluating borrowed tones and their motion. |
| Functional substitute | G7-Db7 before C | Only in enabled styles; preserve destination and inspect bass/guide tones and melody compatibility. |
| Sequence copy/variation | Repeat first phrase with changed ending | Preserve locks and phrase intent; validate the new joins and loop boundary. |
| Modulation preparation | Dm7-G7 leading into C in a new key | Define destination key/time and actual arrival; update the timeline before generating melody. |

Scale membership is one policy, not a complete quality test. `forceScale` should explicitly mean strict diatonic output in the active context; it excludes applied dominants/borrowed tones unless an operation deliberately changes that context. Do not first generate a functional chromatic chord and then snap away its defining tone.

Spice amount should initially control extension/color frequency. Harmonic substitution and chromaticism need distinct controls, so asking for richer voicings does not unexpectedly change chord function. Preserve a documented mapping from the old combined spice setting.

For each edit: generate candidates, instantiate timing/tones, evaluate predecessor and successor, check relevant phrase/loop goals, and commit one complete timeline revision. If more than one event is required, propose a bounded window rather than altering one chord in isolation. Refuse or return alternatives for edits that contradict hard locks.

## 6. Voicing and sounding harmony

After chord identity is stable, choose concrete voicings using voice continuity, common tones, register, spacing, bass/top-note constraints, and required thirds/sevenths. Penalize crossing and excessive movement according to style; strict parallel-fifth/octave restrictions are optional contrapuntal policies, not universal pop rules.

Preserve third/seventh guide-tone identity where essential to the selected function. Omitting or replacing tones must be a voicing decision with known consequences. Extensions can be redistributed across tracks; a chord track is not required to play every symbolic chord member.

Chordlet inversion and melody alignment should constrain bass/top voice or register explicitly. Do not infer a new harmonic root from the lowest note after inversion. Keep global chord identity shared while allowing track-specific register, spread, strum, and permitted anticipations.

A track's next-chord anticipation changes sounding harmony locally. Either score it against overlapping bass/melody or disable it for spans whose functional requirements it violates. Strum and note-length spill across chord changes also require overlap-aware checks.

## 7. Generation ordering and melody integration

Recommended orchestration:

1. Resolve global settings, section overrides, user chords/durations, and all locks.
2. Plan phrase harmony, modulation boundaries, and required ii-V/other preparations.
3. Apply permitted extensions, substitutions, and repeated-section variants; validate phrase/loop goals.
4. Finalize immutable harmonic events and local contexts.
5. Generate melody from that timeline, or validate fixed melody as a constraint.
6. Choose accompaniment voicings, incorporating any melody-register preferences without changing chord identity.
7. Render bass, chords, arpeggios, and drums; apply bounded performance effects and validate overlaps.
8. Publish chordlets, labels, metadata, and generated notes atomically from one revision.

When harmonizing a fixed melody, step 2 consumes its notes. When deriving an alternate progression from an initial generated melody, create and finalize that alternate timeline before rendering its dependent parts. Never make a last-minute harmony change only for accompaniment.

A harmony change marks overlapping generated parts stale. Regenerate affected spans with stable boundary context and seeds. Preserve manual/locked notes, or show the incompatibility and offer alternatives. Do not silently replace the user's melody to protect a generated chord choice.

## 8. Chordlet editor upgrades

Keep the existing efficient compact editor. Add musical operations progressively rather than a separate complex theory screen.

- Lock root, quality, duration, bass/inversion, or the entire event independently.
- Reroll one chord, an unlocked span, or the progression while respecting its neighbors and phrase goals.
- Separate actions for add color, substitute function, change bass/voicing, and regenerate progression.
- Show chord text, optional Roman numeral/function, duration, and local key. Ambiguous function remains optional/uncertain.
- Offer ranked contextual alternatives with short explanations such as "prepares Am" or "keeps the melody E".
- Preserve spelling, accidentals, custom pitches, and explicit inversions across respice/reset operations unless the chosen action changes them.
- Allow phrase arrival/loop intent and tension controls at progression level; keep dense per-event details contextual.
- Preview and audition alternatives before committing when multiple spans/keys would change. Commit as one undoable transaction.
- Clearly distinguish manually edited, locked, generated, and stale material without exposing internal search scores in the default UI.

First-release editor scope: per-event locks, local reroll, extension-only spice, accurate labels, and one-step undo. Ranked reharmonization alternatives and function-aware multi-event edits are later upgrades.

## 9. Deterministic search and evaluation

Hard constraints cover parsing/timing, locked properties, requested event count/duration, declared range, and explicitly selected endpoint/cadence conditions. Style preferences cover function continuity, tonal center, chromatic tension, voice motion, novelty, repetition, and melody compatibility.

Use stable candidate ordering, bounded beam width, and seeded selection among strong candidates. Score whole phrase outcomes and new joins, not just immediate next-chord plausibility. Keep a rejection/score trace available for development and contextual explanations.

When constraints conflict, preserve user material and return a deterministic explanation. Relax only optional preferences in an explicit order. Do not replace an invalid sequence with C, force a locked endpoint, or bypass a chromatic resolution silently.

Random streams for structural progression, durations, color, substitution, voicing, and performance must be separate. Operations such as respice accept an explicit seed. Store algorithm identity, settings, and seeds; local edits should not reroll unrelated events.

## 10. Phased implementation and acceptance gates

| Phase | Work | Gate |
| --- | --- | --- |
| H0: audit and immediate fixes | Inventory mutation sites/callers; isolate circle indexing, respice parsing/reproducibility, endpoint overwrites, and mismatched labels/arrays. | Seeded regression corpus and a documented mutation map; every intentional output change explained. |
| H1: canonical timeline | Typed chord adapter, exact event timing, immutable revisions, explicit roots/bass/voicings; move harmonic replacement before melody. | All parts and chordlets consume one finalized harmony revision. Fixed/custom progressions round-trip. |
| H2: initial strategies | Functional phrase planner, diatonic fifth sequences, pop-loop strategy, locked endpoints/durations. | Each strategy fulfills declared goals across keys, modes, lengths, and last-to-first joins. |
| H3: contextual color | Extension-only operations, guide tones, typed quality changes, neighbor/phrase validation. | Spice does not erase a required dominant or locked root/quality; defining chord tones remain available. |
| H4: voicing and chordlets | Voice continuity, bass/top constraints, local reroll/locks, audition and undo. | Smooth voicings preserve identity and register limits; edits update metadata and dependent parts together. |
| H5: optional chromatic upgrades | Applied dominants, modal mixture, modulation, contextual substitutions and melody harmonization. | Every operation has destination/context metadata and style-specific acceptance cases. |
| H6: default selection | Blinded listening, preset mapping, runtime profiling, compatibility/new-engine comparisons. | Quality improves across the target corpus without stale harmony, lock violations, or unbounded search. |

H1 is shared with melody phase M1. M2-M4 can proceed on fixed harmony while H2-H5 are developed. Do not require advanced chromatic harmony before delivering a useful melody improvement.

## 11. Validation corpus

| Case | Required behavior |
| --- | --- |
| Diatonic fifth sequence in C and other keys | Degree/quality pattern is preserved, including the diatonic tritone link; entry and exit meet the declared goal. |
| Dm7-G7-C and Dm7b5-E7-Am | Functional identity and necessary chromatic tones survive color operations. |
| G7-C vs Gm-C | Quality change is recognized as a harmonic change, not a harmless suffix decoration. |
| C-Am-F-G loop | Last-to-first join is evaluated; output need not force a classical cadence inside every loop. |
| F-Fm-C | Borrowed quality is intentional and preserved, with melody/bass compatibility assessed. |
| Fixed first/last chords plus maximum spice/similarity | Hard endpoint locks survive all later transformations. |
| Sharp/flat roots, inversions, custom voicings | Parse/display/serialize/respice round-trip without accidental loss or implicit root replacement. |
| Uneven durations and sustained melody | Every overlap is evaluated, with exact total duration and boundary ownership. |
| Section modulation/ii-V | Melody, bass, chord labels, and accompaniment share the finalized key/harmony timeline. |
| Repeated phrase with edited ending | Motif/structure copying preserves locks and revalidates new joins. |
| Modal or blues profile | Function policy permits the intended nonclassical behavior instead of imposing inappropriate dominant resolution. |
| Impossible locked span | Deterministic diagnostic; no hidden substitution or unlocked neighboring corruption. |

Automate deterministic results, structural/lock invariants, parse round-trips, overlap handling, voice/range limits, and bounded failure behavior. Retain the existing `GeneratorRegressionTest` and TypeScript Java-parity fixtures for the compatibility path; add separate musical cases for the new engine.

Listening should compare progression alone, bass plus chords, and the full melody/accompaniment result using identical instruments/loudness. Include both low-color and high-color settings. Numeric function/voice-leading scores are diagnostics, not proof of musical quality. Establish runtime targets from desktop/browser measurements before selecting default search budgets.

## 12. Implementation seams and compatibility

Java entry points: `generation/ChordProgressionGenerator.java` for planning, `MidiUtils.java` for legacy parsing/mapping adapters, `generation/MidiGenerator.java` and `SectionGenerationPlanner` for section orchestration, `generation/ChordPhraseGenerator.java` for rendering, and `Components/Chordlet.java` / `Panels/ChordletPanel.java` for editing. `GeneratedChordState` should become a published view of the canonical result, not a separate source of chord truth.

TypeScript already separates `harmony.ts`, `harmony-voicing.ts`, and `project-harmony.ts`; `chord-generator.ts` primarily renders chord parts. Extend those responsibilities rather than assuming the renderer owns progression planning. The UI seam is `../VibeComposerJS/frontend/src/app/shared/chordlet-editor.component.*`.

Follow `../VibeComposerJS/TRANSLATION_RULES.md` for any implementation there: framework-independent core, bigint seeds/Java-random compatibility in the parity path, consolidated workspace UI, serialized settings, and undoable state updates. Review actual TypeScript behavior independently; do not copy Java defects merely because they appear in this audit.

## 13. Recommended priority and deferred upgrades

Deliver shared timeline consistency first, then phrase/loop-aware progression planning, coherent extension-only spice, and contextual chordlet editing. These provide more immediate value than increasing the number of chord suffixes or forcing stronger fifth-motion bias.

Defer learned style models, full polyphonic reharmonization, distant-key modulation search, exhaustive jazz substitution, and automatic Roman-numeral certainty. Keep advanced strategies optional and testable. Preserve direct manual editing even when the generator dislikes the result.

## Theory references

- [Open Music Theory: harmonic functions](https://open-musictheory.github.io/docs/harmony/harmonicFunctions/) — function as contextual harmonic organization.
- [Fundamentals, Function, and Form: diatonic descending-fifth sequences](https://milnepublishing.geneseo.edu/fundamentals-function-form/chapter/25-diatonic-descending-fifth-sequences/) — the diatonic sequence model.
- [Open Music Theory: chromatic sequences](https://viva.pressbooks.pub/openmusictheory/chapter/chromatic-sequences/) — distinction between diatonic and chromatic sequence behavior.
- [Open Music Theory: substitutions](https://viva.pressbooks.pub/openmusictheory/chapter/substitutions/) — contextual alternatives and applied-function relationships.

The architecture, rollout order, editor features, and scoring policies are proposals based on the code review, not claims that these textbooks prescribe a generator implementation.
