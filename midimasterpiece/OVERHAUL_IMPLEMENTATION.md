# Melody and harmony overhaul: Java implementation record

Updated: 2026-10-13. This records the implemented Java work from the [melody roadmap](MELODY_GENERATION_OVERHAUL_PLAN.md) and [chord/chordlet roadmap](CHORD_GENERATION_OVERHAUL_PLAN.md). TypeScript porting is deferred. Listening-based tuning and promotion to defaults remain acceptance gates.

## Using the new engines

See the [parameter guide](CONTEXTUAL_GENERATION_PARAMETERS.md) for combo choices, low/middle/high effects, and the current meanings of older controls.

**Contextual Melody** is in **Extra Settings → Melody** and starts ON for a fresh application launch. Loaded configs without the setting default to OFF; configs that explicitly save the setting retain their choice. Compatibility-only controls are hidden while the contextual engine is active, including on new/copied/section panels and across Full/Simple mode changes. Choose phrase form, phrase bars, beats per bar, TONAL/MODAL/JAZZ profile, range, tension, and OPEN/CLOSED/LOOP ending. Phrase dimensions describe generation; they do not rewrite the MIDI time signature. Range and pitch locks apply before explicit track/key transposition.

Select FUNCTIONAL, FIFTHS, POP_LOOP, or MODAL in chord settings. CURRENT preserves the compatibility generator. **Smooth voices** and Voice Low/High control the new rendering path. **Co-design** requires contextual melody and a planned progression strategy and permits at most two melody/harmony passes. Section variants are generated against the resulting finalized harmony; they need not reproduce the preliminary co-design melody.

Melody-panel **Anchors** accepts beat=MIDI pairs, such as 0=72,4=76. **Reroll span** accepts startBeat,endBeat on existing note boundaries and changes that window on the next regeneration. Unrelated pitches, rests, and rhythm remain fixed. A previous in-memory result from the same harmony revision is required; after editing harmony or reopening a project, first generate a fresh complete melody. Clear the window to return to full generation.

Chordlet **Ctrl+click** toggles the complete generation lock; **Alt+click** rerolls one event while scoring both neighboring joins. **Locks** exposes root, quality, bass, and duration locks independently. Manual editing remains allowed. **S** adds identity-preserving color. **Alternatives** ranks contextual edits, explains their destination, and permits audition before commitment when a MIDI device is available. **Harmonize** proposes harmony for an attached fixed melody. **Undo/Redo** retain chord definitions, property locks, and event identities.

Older presets without the new setting default to compatibility engines. New settings, note constraints, property locks, and chordlet IDs persist in XML. The legacy MIDI regression fixture remains unchanged.

## Shared harmony and ordering — M1/H1

HarmonyTimeline provides immutable events with stable IDs, content revisions, spelled symbols/roots, key/mode/function metadata, independent harmonic roots and voiced pitches, and half-open boundaries at 960 ticks per beat. Edits retain identity and identify changed events, neighboring joins, and duration-shifted successors. Repetition preserves spelling and gives each occurrence a distinct identity. Chordlet IDs survive reorder, undo, and serialization.

Section ii-V/arrival changes finish before the canonical section snapshot is published. Melody consumes it directly; bass, chords, and arps receive finalized pitches/durations and avoid a second mode transformation. MidiGenerator.getSectionHarmonySnapshots() exposes the snapshots. Color, inversion, and voicing cannot redefine a harmonic root. The application retains its normalized C context followed by explicit key transposition.

Property-locked events reject conflicting ii-V substitutions. Duration locks retain configured harmonic rhythm. Locked pitches or exhausted bounded search produce actionable errors instead of silently relaxing constraints. Editor changes require regeneration to update the MIDI score; editing does not live-rewrite already playing tracks.

## Phrase, anchor, and block planning — M2–M4

MelodyPhrasePlan represents statement/variation, question/answer, sentence, and continuous forms, phrase purposes, arrivals, recurring motifs, and phrase dimensions independent of chord boundaries. Generated attacks can cross harmonic changes. The adapter tries at most three rhythmic candidates, including a final split-at-changes fallback. Authored rhythms are not moved to make search succeed.

The pitch beam evaluates realized intervals, register, phrase contour, metric importance, reversals, leap recovery, recurring gestures, dominant tendencies, arrivals, and loop seams. Existing note targets become soft scale-degree preferences at phrase arrivals. CLOSED prefers closure where fixed harmony permits it. MODAL suppresses functional dominant obligations. JAZZ permits a conservative set of scale-compatible extensions.

ContextualBlockCatalogue realizes CHORDY/ARPY material from actual chord members: E-based material over C can use E–G–C instead of assuming E–G–B. Diatonic, inverted, interval-sequence, and fragment transformations are available. Catalogue affinities guide the beam; all realized candidates still pass harmony/role checks. Weight zero removes that catalogue preference rather than prohibiting independently generated notes of the same shape.

Notes carry CHORD, EXTENSION, PASSING, NEIGHBOR, ANTICIPATION, SUSPENSION, APPOGGIATURA, CHROMATIC_APPROACH, or REST roles and musical spelling. Anticipations repeat into their destination; suspensions require preparation and downward step resolution; appoggiaturas require explicit intent and leap/step behavior; chromatic approaches resolve by semitone. Rests cannot erase unresolved ornaments. Spelling is separate from MIDI equivalence, including altered extensions such as #9.

## Melody integration and controls — M5

The contextual engine bypasses tonic/chord quotas and avoid-note mutation. Speed, recurring pattern offsets, pauses/fills, start chance, split-long chance, anticipation chance, catalogue weights/new-block influence, target preferences, leap allowance, direction complexity, accents, dynamics, and articulation have explicit mappings. Inapplicable quota/exception, flexible/doubled-rhythm, old target-mode/pattern-effect, and drum-driven pitch-repair controls are hidden. The historical drum-accent pitch splitter is bypassed.

Articulation is checked after swing, length/transition processing, offsets, and feedback. Incompatible tails are shortened; offsets that move stable attacks into incompatible harmony report conflicts. Planned suspensions and supported extensions retain their intended dissonance. The final score humanizer is bypassed for contextual melody tracks because its unchecked offset/duration jitter happens after validation; the global Humanize Notes setting still affects accompaniment. Expressive timing needs listening, especially with large offsets or feedback.

Attached user MIDI is copied and preserved by FixedMelodyAdapter: pitches, rhythmic values, durations, dynamics, and offsets bypass mode conversion, quotas, swing, length mutation, and automatic transition repair. Explicit track/key transposition and delay effects remain user-controlled. Complete authored motifs can repeat to fill a section; incompatible section/motif lengths report an error. Explicit custom harmony stays fixed. Otherwise the contextual path harmonizes around authored notes; the editor's Harmonize action also supports this independently.

## Harmony strategies, color, and voicing — H2–H5

The earlier functional, diatonic-fifths, pop-loop, and modal strategies now use the active mode's vocabulary. Functional minor retains the raised leading tone in V7 and supports iiø7; destination-minor ii-V preparation uses iiø7–V7 instead of blindly mapping a major progression through natural minor.

Functional structure is now selected from seeded phrase alternatives before beam scoring: varied tonic-family openings/prolongations, ii/IV preparation, V/vii° arrivals where supported, and plagal closure. With contextual melody's OPEN/LOOP intent, half-cadence and deceptive alternatives are also eligible; a fixed ending conditions the selection. CLOSED keeps tonic-ending alternatives. Natural-minor subtonic chords are not substituted for leading-tone diminished dominants. This replaces the old single I–ii–V–I scoring funnel without replacing musical structure with larger random noise.

FIFTHS rotates the diatonic cycle when the first chord is unspecified. A fixed first chord determines the starting phase; a fixed last chord can determine it backward when the first is free. Following chords use the actual preceding chord, including interior locks, rather than a stale sequence index. Locked incompatible endpoints can still require an interruption: C–F–Bdim–Em is the unique uninterrupted four-chord sequence starting on C, so fixed C endpoints cannot both fit that span without a break.

Extensions are checked against actual mode/context while retaining root, core quality, and physical bass. Typed operations provide applied dominants, borrowed iv before tonic, jazz tritone substitutions with a destination, and suspended-third resolution. Alternatives filter important authored melody notes and property locks. Additional symbols include m7b5, dim7, 9, 13, 7b9, 7#9, and 7#11. Destination-aware spelling distinguishes Db7 → C from an arbitrary C# label.

HarmonyVoicingPlanner uses a bounded beam for register, bass/top constraints, voice continuity, and dominant guide-tone motion. Explicit inversions and bass locks survive. Chord omission/stretch protects thirds, sevenths, and quality-defining altered fifths; the old renderer's unvalidated future-chord substitution is disabled for canonical harmony.

HarmonyHarmonizer weights fixed-melody overlap by duration and accent while retaining harmonic rhythm and locked events. MelodyHarmonyCoDesigner returns a consistent harmony/melody pair after at most two passes, with convergence reported separately from budget exhaustion. Harmonization/co-design conservatively freeze pitch-property-locked events completely rather than weakening a partial lock. These are bounded tonal heuristics, not exhaustive reharmonization or distant-key search.

## Validation and listening — M6/H6

Run **mvn test** from midimasterpiece. Coverage includes exact timing/overlaps, stable revisions/editor IDs, phrase roles/odd meter, catalogue realization, ornament obligations, chromatic spelling, range/lock failure, local rerolls, guide-tone voicing, functional minor, contextual alternatives, fixed-melody preservation, bounded co-design, XML round-trip, Swing undo/redo, full arrangement/minor/co-design MIDI export, and byte-identical legacy output.

Run **mvn -Dtest=GenerationCorpus test** to generate target/overhaul-corpus/. It contains 16 shuffled MIDI/WAV pairs, pitch/rhythm/role traces, a manifest, and listening instructions. Four harmonic contexts and two seeds compare compatibility and contextual v2. Both receive the same literal harmony. Tempo, accompaniment voicing, instruments, and reference-tone levels are controlled. WAV uses an additive reference tone rather than the application soundfont; MIDI can be auditioned with one common soundfont.

The same command writes profile.csv for 12 additional stress cases: four contexts, 4/16/32 chords, uneven harmonic durations, three beats per bar, and narrow/wide registers. The 32-chord cases cover 88 beats and 288 planned events, with roughly 449,000 candidates examined, and took approximately 64–68 ms in the recorded run. Candidate bounds and register compliance are asserted; runtime is recorded rather than enforced as a fragile machine-dependent threshold.

Latest verification: **50 normal tests passed**, including cross-seed functional/fifths diversity, ending intent and constrained sequence joins, empty-custom-rhythm fallback, Swing startup/config/visibility, full minor/co-design and attached-melody rendering, and the byte-identical legacy MIDI fixture. The two opt-in corpus/profile tests passed in the preceding overhaul verification. git diff --check passed.

The corpus records planning runtime, note count, and largest leap. Short-example contextual timings have ranged from approximately 3–29 ms across runs on this desktop, including startup/JIT effects; this is not a guaranteed latency budget or full composition/export measurement. Listen before reading engine labels. Judge phrase identity, arrivals, tension/resolution, contour, rhythm, and accompaniment fit. No musical preference is inferred from metrics.

## Acceptance work still open

- Conduct blinded listening and tune weights/profiles. Fresh-launch contextual melody is enabled at the user's request; this default change does not constitute listening validation. No listening preference or completed visual UI audit is claimed.
- Set production latency budgets after profiling full multi-track arrangements, extreme effects, and more seeds. The delivered long/uneven/odd-meter profile is a reproducible starting point.
- Refine effects diagnostics and the compact editor with listening/UI feedback. Partial locks are deliberately conservative in harmonization and modulation. Local melody rerolls require a compatible in-memory plan.
- Learned style scoring, full counterpoint/polyphonic reharmonization, distant-key modulation search, and exhaustive jazz substitutions remain the plans' deferred upgrades.
- TypeScript remains deferred at the user's request.
