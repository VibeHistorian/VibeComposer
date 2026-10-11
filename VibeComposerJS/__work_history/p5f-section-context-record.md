# P5f section harmony and timing — 2026-10-11

Delivered header-selected section Chordlets, chord durations, swing/BPM/key/scale/transposition and half/normal/double beat duration. Arrangement, score selection ranges, playback and MIDI share effective section boundaries. Context stays separate from musical part patches and reroll policy.

## Ownership and precedence

- `section.customChords` stores root/spice/inversion Chordlets; `chordDegrees` retains explicit diatonic harmony. Both outrank inferred harmony/cadence. Editing a Chordlet materializes the displayed progression. Movement carries chords, degrees and durations in one command.
- `section.customDurations` replaces a complete beat array, with 0.25–32 beats per slot. Duration-only overrides repeat the primary harmony as needed. Stored chord/degree/duration arrays must agree in length when present together. Defaults are four beats per slot.
- `section.context` is sparse: BPM 30–300, swing 0–100, supported project key/scale, exact transposition −24…24, direct/ending ii–V cadence and multiplier 0.5/1/2. Omission inherits project tempo/key/scale, part swing, zero key change and normal duration. Zero is a real override; reset removes fields independently.
- Section swing supersedes part swing for generated Melody, Chords, Arpeggio and Drums. Java Bass has no swing consumer and retains that exception. Key/scale feed shared harmony; key change transposes generated pitched notes exactly. Track-wide manual replacements keep their saved pitches and rhythms.
- The workspace's `measures` remains a chord-slot count. Four slots with `[2,6,1,3]` last twelve beats; double duration makes twenty-four. Longer sections repeat the progression/duration cycle. A following inherited section returns to project tempo/key/scale.
- JSON/session restore validate these arrays and sparse fields without a schema gate or migration. Commands preserve part patches, track identity, mix, presence, locks and exact decimal seeds. No-op/invalid edits preserve redo; duplicated sections own independent arrays.

## Java audit and translation boundaries

`SectionGenerationPlanner.prepareCustomProgression()` supplies custom roots/voicings/durations and maps multiplier indexes 0/1/2 to half/normal/double. `SectionConfig` owns the sparse timing/key fields. `MidiGenerator.renderSection()` and `replaceLastChordsForTwoFiveOne()` replace ending slots with Dm/G7 relative to the target key, after generating Melody; Java's next-section modulation is stateful.

The translation exposes section-local direct transposition and optional ending ii–V cadence. It does not silently change the next section's key. Explicit harmony outranks cadence; short progressions retain their chords. Automatic random/pivot/delayed modulation is not exposed here. Scale support remains Major/Natural minor.

**Timing design:** seeded phrase generators retain normalized four-beat slots and their existing draw order. `ResolvedHarmony.durations` and `mapChordNotes()` map note onset and end separately onto variable durations, including crossed boundaries. Absolute offsets and feedback run afterward. All five role consumers and first-Melody dependencies use this same grid/output. Project base-skeleton inference remains independent of section Melody onset maps.

Existing four-beat Java fixture parity and deterministic variable-timing replay are preserved. This is **not complete Java phrase parity at non-four-beat timing**: Java's duration-dependent block choices, alternating Bass draws, split thresholds and note-density branches can differ. Melody custom-duration templates/strict/random-weighting consumers remain P9. Duration-only harmony retention and explicit scoped modulation are deliberate translation choices.

Playback integrates the shared tempo points for note duration, seeking, LIVE reload and partial-section loops. MIDI writes tempo events at beat ticks and note durations in ticks. Selected loops repeat only their selected tempo segments.

## Evidence

- Five focused P5f tests cover all-role variable timing (including Melody-following Arpeggio/Drums), MIDI tick/second agreement, section focus, tempo-crossing sustains, partial loops, LIVE reload, exact octave transposition, manual ownership, invalid/no-op history, restore, independent duplication, cadence precedence and chord movement.
- Full part-settings/playback/selection/overview/score suites: 243 passed. Existing Java phrase/inference/voicing fixtures remain passing. Focused harmony/dependency checks passed after typed duration integration.
- Production browser workflows with thirty tracks at 1440/780/390px cover BPM/swing/durations, history, section Chordlet menus/inversions, primary/other-section/track isolation and control bounds. The arrangement zoom/scrollbar/device-scale matrix passed.
- Reviewed `frontend/.angular/contextual-section-{width}.png`. Section context spans the inspector at narrow widths; numeric input/inheritance rows avoid overflow.
- Production build passes with existing initial-bundle budget and Pixi CommonJS warnings.
