P6b adds `RolePolicyFixture.java` / `../fixtures/role-policy.java.json`: 64 production `MelodyUtils.getRandomMelodyPattern(0, seed)` choices, 23 production `DrumDefaults` blueprint tuples plus four custom 16-cell grids, and 36 audited int-seeded `BassGUI.createRandomBassPanels` branch cases (four long/int-overflow seeds × three orders × three spans). Tests convert Java exclusive velocity maxima to the app’s inclusive bounds. Bass pause draws are consumed but their GUI baking is not ported. Drum tests exercise all tuple variants with Shift chance 0. This is helper/branch evidence, not complete GUI-reroll/phrase parity.

```powershell
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -encoding UTF-8 -cp 'midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' -d VibeComposerJS/frontend/.angular/role-policy-fixtures midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Parts/Defaults/DrumDefaults.java midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Parts/Defaults/DrumSettings.java VibeComposerJS/frontend/scripts/java/RolePolicyFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' '-Djava.awt.headless=true' -cp 'VibeComposerJS/frontend/.angular/role-policy-fixtures;midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' org.vibehistorian.vibecomposer.Parts.Defaults.RolePolicyFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/role-policy.java.json
```

# Java pattern fixtures

P5e's part articulation checkpoint adds `MelodyExtendedFixture.java` / `../fixtures/melody-extended.java.json`: **192 complete production Melody phrases**, calling the current `MelodyBlockSkeletonGenerator`, `MelodyExpansion` and `MelodyPhraseBuilder`, including swing and length/collision preparation. It covers Doubled rhythm 0/35/100, exception limits 0–4 and chances 0/40/100, Split long 0/45/100, Lead chords 0/25/100 and Start note 100/80/0, combined with four exact long/int-overflow seeds, part seed/seed offsets, repeated/inverted/zero block identities, Flex, speed, target lists, transpose, key/mode, pause/fill/flip, accents, swing and length. Tests compare MIDI pitch/velocity exactly and onset/duration within 1e-10, retaining all 80 preceding core cases.

The supported phrase context remains root-only, tonic-relative target mode 2, New Blocks 100, pattern effect 2 and maximum direction changes 2. Global tonal targets, key emphasis, avoid-note replacement, Single note exceptions, arpy surprises and custom durations remain off in the original extended fixture. This is complete phrase evidence for the six new part controls, not parity for all extended global Melody or full-song assembly. Split constructor durations use jMusic's 0.9 rhythm multiplier; unsplit durations keep Java's randomized 0.95 path. Pause protection preserves jMusic's overloaded `equals(Note)` (pitch/duration, ignoring velocity); Flex retains the cached direction budget before target adjustment. Alternating rhythm has no current block-path consumer.

The second P5e checkpoint adds `melody-global.java.json`: **192 additional complete production phrases** from the same harness with its `globals` argument. It varies Max direction changes 1–6, Single note exceptions and Fill pauses per chord, combined with the extended part matrix. All other fixed context inputs above remain unchanged. Generation compiles current production consumers ahead of `target/classes` and the dependency JAR. Regenerate with `& 'VibeComposerJS/frontend/scripts/java/generate-melody-extended-fixtures.ps1' -Jdk 'C:/Java/jdk-24.0.2' -Globals`. Tests compare exact pitch/velocity and timing/duration within 1e-10; globals use an independent generation input, never part overrides.

The third P5e checkpoint adds `melody-effects.java.json`: **1,152 additional complete production phrases**, running all six combinations of Pattern effect 0/1/2 and Arpy surprises off/on against the original 192 extended cases. Regenerate with `& 'VibeComposerJS/frontend/scripts/java/generate-melody-extended-fixtures.ps1' -Jdk 'C:/Java/jdk-24.0.2' -Effects`. This checks rhythm-only forced block lengths/stored durations, notes-only unforced regeneration, Rhythm + Notes reuse, Flex's final-block replacement and even four-note timing at speed extremes. Pitches/velocities are exact and onsets/durations agree within 1e-10. The root-only tonic-relative context and other fixed global exclusions above still apply; these fixtures do not establish static catalogue, tonal target, custom duration, rhythm accent or full-song parity.

The fourth P5e checkpoint adds `melody-targets-extended.java.json`: **4,608 production target-helper cases**, using all three `NoteTargetDirection` selections, variations 1–6, both direction sources, 32 int seeds (including extrema) and four diatonic progressions. Regenerate with `& 'VibeComposerJS/frontend/scripts/java/generate-melody-target-fixtures.ps1' -Jdk 'C:/Java/jdk-24.0.2'`. The script explicitly compiles current `MidiUtils`, `MelodyUtils` and `MelodyTargetUtils` before built/bundled classes. Tests check exact target lists, including seed overflow, closest-note search, the ANY shuffle and Java's literal root-insertion indexing. Target mode stays 2; arbitrary Chordlet targeting remains outside this oracle.

`melody-prepared-targets.java.json` adds **192 complete production phrases** after target preparation. Regenerate with `& 'VibeComposerJS/frontend/scripts/java/generate-melody-extended-fixtures.ps1' -Jdk 'C:/Java/jdk-24.0.2' -Targets`. Tests first check each prepared list from its helper seed/progression/global inputs, then compare the complete phrase with exact pitch/velocity and timing/duration within 1e-10. This retains the root-only tonic-relative phrase context; Split long and Lead chords are disabled in this matrix because some prepared-target/articulation combinations make production `MelodyNoteUtils.getAllowedPitchFromRange` throw when its early-chord B restriction removes the only candidate. TypeScript deliberately retains that in-range B and consumes the normal bounded draw; a focused functional regression validates the fallback. Existing complete part-articulation and effects fixtures still pass. Force first outline's event ownership/copied fields, locks, history, persistence and MIDI are validated functionally, not claimed as complete GUI runtime parity.

With the existing production `target/classes` and dependency JAR, regenerate from the repository root. The script compiles the current block, expansion and phrase-builder sources explicitly before bundled classes:

```powershell
& 'VibeComposerJS/frontend/scripts/java/generate-melody-extended-fixtures.ps1' -Jdk 'C:/Java/jdk-24.0.2'
```

P5c/P5d add **256 complete production Arpeggio phrases**, **192 complete production drum phrases** and **64 production melody onset-map cases** in `arpeggio-core.java.json`, `drum-advanced.java.json` and `melody-context.java.json`. `ArpCoreFixture` calls current `ArpPhraseGenerator`/`ArpPattern` and production swing, covering all eight pitch directions, doubled-source rotation, custom chord indices, contour/chord-relative contour, seeded octave adjustment, melody correction, MELODY1, Hits/Span/Repeat, pause/split/fill/flip, custom zero velocities, voice expansion, four exact long seeds and an explicit part seed. It supplies actual C/B/F major/minor voicings directly and fixes chord durations to four beats, one progression cycle, no arrangement variations and the phrase duration multiplier to 1.0. The existing translation's one/two-octave voicings remain supported. Java null chord-index results become rests safely; pitches beyond MIDI range after octave/contour processing are clamped by the shared track wrapper.

`DrumAdvancedFixture` calls current `DrumPhraseGenerator`, swing and `JMusicUtilsCustom.humanize`, including Ghosts with static/custom/melody rhythms, rests and zero velocities, span groups, split secondary velocities, fill/flip, Humanize 0/20/100 and four exact long seeds. Drum humanization uses normal stretch, a primary order of 1 and the full-long score seed (`seed + 999`, `nextLong + 4001`); secondary TS tracks use their established ID-derived seed. The comparison retains single-pitch tracks, production OPENHAT_CHANCE 0, duration multiplier 0.95 and no forced dynamics/arrangement variations. This is phrase-level timing evidence, not full-song score-humanization parity. The scoped Humanize input is a deliberate extension of Java's global drum preference; omission stays zero to preserve existing translated timing. Manual replacements retain their notes.

`MelodyContextFixture` calls `MelodyPatternUtils.patternsFromNotes`, covering rest/empty buckets and offsets near the production 0.01-beat boundaries. Runtime dependencies use the first Melody track in stored order, resolve its settings against the same section and generate its onset map before swing/feedback. Manual melody supplies its own onset map. Presence/mute/solo do not remove this dependency; missing Melody or disabled generated Melody supplies an empty grid. Context pitches use the translated current key/mode, with part transpose already applied; global arrangement transpose remains the later common wrapper. The map is cached by immutable project/section identity. Bass/Chords MELODY1 remains unexposed pending their own consumer slices.

Melody rhythm derives 16 subdivisions per chord and progression-wide Span, ignoring retained grid Hits/Shift/Repeat while selected; those fields remain stored for switching back. Custom velocities apply only when the 32-cell list covers the full melody grid (one/two chords), matching Java's list-length condition. Both consumers fall back to random velocity bounds for longer progressions. The editors show the applicable 16/32-cell velocity grid or random bounds according to the effective section progression. Arpeggio follows the per-chord onset map (Java bypasses its baked pause list); Drums applies Pause/Flip to the flattened map. Basic reroll direction choices remain the existing four-selection P6 behavior; advanced creation policies remain P6h.

Regenerate with existing compiled Java dependencies; the script compiles the current production consumers explicitly ahead of `target/classes` and the dependency JAR:

```powershell
& 'VibeComposerJS/frontend/scripts/java/generate-p5-fixtures.ps1' -Jdk 'C:/Java/jdk-24.0.2'
```

`ChordletHarmonyFixture.java` supplies `../fixtures/chordlet-harmony.java.json`: **2,244 production chord voicings**, calling current `MidiUtils.getNormalMappedChord`/`chordInversion` for 12 roots, all 17 spice names and inversions -5…5. This is harmonic helper parity, not Swing gesture or advanced melody-target parity. It preserves Java's maj9/m9 equivalence. Compile current MidiUtils with the fixture, not the stale JAR class:

```powershell
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -encoding UTF-8 -cp 'midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' -d VibeComposerJS/frontend/.angular/chord-fixtures midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/MidiUtils.java VibeComposerJS/frontend/scripts/java/ChordletHarmonyFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' '-Djava.awt.headless=true' -cp 'VibeComposerJS/frontend/.angular/chord-fixtures;midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' ChordletHarmonyFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/chordlet-harmony.java.json
```

P5b adds `ChordCoreFixture.java` / `../fixtures/chord-core.java.json`: **288 complete production Chords phrases**, using current `ChordPhraseGenerator`, `Chord.makeAndStoreNotesBackwards`, `StrumType.adjustNoteOffsets`, phrase assembly and swing. Cases cover four exact long seeds, explicit part seeds, C/B/F major/minor, 1/3/4/7 chords, eight rhythms, Hits 1–32, Span 1–4, all join modes, fills/flips, velocity grids with zeros, voice expansion, independent transition draws/split extremes, all nine strum types and per-tone pauses. The harness fixes arrangement variations off, uses explicit close/open diatonic triads, four-beat chords and global duration multiplier 1.0 (swing retains production 0.95). This validates the supported musical consumer, not advanced harmony/MELODY1 or deterministic GUI policies. TS corrects the primary order seed to +20001. Struminess is a documented GUI no-op correction outside this oracle.

From the repository root, with existing compiled Java dependencies:

```powershell
New-Item -ItemType Directory -Force VibeComposerJS/frontend/.angular/chord-fixtures | Out-Null
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -encoding UTF-8 -cp 'midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' -d VibeComposerJS/frontend/.angular/chord-fixtures midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/InstPhraseGenerator.java midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/ChordPhraseGenerator.java midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/PhrasePatternUtils.java midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/MidiUtils.java midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Chord.java midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Enums/StrumType.java VibeComposerJS/frontend/scripts/java/ChordCoreFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' '-Djava.awt.headless=true' -cp 'VibeComposerJS/frontend/.angular/chord-fixtures;midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' org.vibehistorian.vibecomposer.generation.ChordCoreFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/chord-core.java.json
```

P6e adds `MelodyTargetFixture.java` / `../fixtures/melody-targets.java.json`: 128 production `MelodyTargetUtils.generateNoteTargetOffsets` outputs across 32 int seeds and four diatonic progressions. The supported target path fixes mode 2, variation 3, ASC and randomized directions. Compile the current `MidiUtils`, `MelodyUtils` and `MelodyTargetUtils` alongside the fixture so stale target classes do not become the oracle. This is helper parity, not complete advanced Melody phrase parity.

```powershell
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -encoding UTF-8 -cp 'midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' -d VibeComposerJS/frontend/.angular/melody-target-fixtures midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/MidiUtils.java midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/MelodyUtils.java midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/MelodyTargetUtils.java VibeComposerJS/frontend/scripts/java/MelodyTargetFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' '-Djava.awt.headless=true' -cp 'VibeComposerJS/frontend/.angular/melody-target-fixtures;midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' org.vibehistorian.vibecomposer.generation.MelodyTargetFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/melody-targets.java.json
```

P6a adds `ArpeggioPolicyFixture.java` and `../fixtures/arpeggio-policy.java.json`: **100 production `ChordSpanFill.getWeighted` outputs** and **256 hit-choice draws** across four exact long seeds, using production `OMNI.getRandomFromArray` for powers [2,4,4,8,8,8,8] and the audited ArpGUI 2–8/5-redraw/7→8 branch for unrestricted hits. The latter injects a deterministic Random in place of Java GUI's unseeded Random. This is policy-helper/branch evidence, not complete GUI-reroll or phrase parity. TS rerolls use a separately documented deterministic project seed/counter stream; unsupported MELODY1, later-panel weighting, pattern growth/pause baking and pitch-direction rerolls are excluded.

```powershell
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -encoding UTF-8 -cp 'midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' -d VibeComposerJS/frontend/.angular/policy-fixtures VibeComposerJS/frontend/scripts/java/ArpeggioPolicyFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' -cp 'VibeComposerJS/frontend/.angular/policy-fixtures;midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' ArpeggioPolicyFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/arpeggio-policy.java.json
```

P5a adds `BassCoreFixture.java` and `../fixtures/bass-core.java.json`: **432 complete production bass consumer phrases** from the current `BassPhraseGenerator`. Coverage includes four exact long/int-overflow seeds and explicit int seeds, C/B/F in major/natural minor, 1/3/4/7 chords, all nine supported rhythms including alternating, Hits 1/3/4/5/8/10/16/32, Span 1–4, NOJOIN/EXPAND/JOIN, shift/flip, Euclidean/custom grids, velocity grids/zeros, fills and Note variance 0/20/100. Pitch/velocity are exact; timing/duration tolerance is 1e-10. Silent Java notes are omitted. Inclusive TS maximum 89 maps to exclusive Java maximum 90.

The oracle supplies explicit diatonic triads in the translated bass register (36 + pitch class), and invokes the production consumer with one progression traversal, four-beat chord durations, CHORUS1 (section-type melody offset 0), empty variations, no transition/arrangement scaling or melody rhythm map, and run duration multiplier 1.0. This matches the preceding translated bass duration contract; Java's normal whole-song 0.95 multiplier is not adopted for Bass. The output is a complete consumer phrase, not the MidiGenerator bass wrapper/full-song pipeline. Part transpose/length/timing and quieter upper-octave doubling remain the existing TS wrappers and scoped MIDI tests. Secondary tracks retain stable-ID seed derivation. Section-type offsets, transitions, variable durations, MELODY1 and arrangement variations/scaling remain later slices.

Omitted `patternJoinMode` resolves to NOJOIN, deliberately preserving the previous TS subdivision path. Explicit EXPAND/JOIN follows Java, including JOIN's boundary suppression and next-chord lookahead before flip/fill. Java's part default is EXPAND. No compatibility migration is performed.

```powershell
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -encoding UTF-8 -cp 'midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' -d VibeComposerJS/frontend/.angular/bass-fixtures midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/InstPhraseGenerator.java midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/BassPhraseGenerator.java VibeComposerJS/frontend/scripts/java/BassCoreFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' '-Djava.awt.headless=true' -cp 'VibeComposerJS/frontend/.angular/bass-fixtures;midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' org.vibehistorian.vibecomposer.generation.BassCoreFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/bass-core.java.json
```

P4g adds `drum-shared.java.json`: another **96 complete production drum phrases**, emitted by `DrumCoreFixture shared` after the compile command below. These combine Span 1–4 (including incomplete groups), Pause 0/35/100, Split 0/40/100 and Swing 0/25/50/66/100 with the original rhythm/fill/velocity/seed matrix. Other fixed drum inputs and the single-pitch contract are unchanged. These are complete phrases, not mask-only comparisons.

```powershell
& 'C:/Java/jdk-24.0.2/bin/java.exe' '-Djava.awt.headless=true' -cp 'VibeComposerJS/frontend/.angular/drum-fixtures;midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' org.vibehistorian.vibecomposer.generation.DrumCoreFixture shared | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/drum-shared.java.json
```

`SharedPartFixture.java` supplies `shared-part.java.json`: **578 production helper cases**. It invokes `InstPart.getFinalPatternCopy`, `MidiUtils.intersperse`, `PhrasePatternUtils.partOfList/partOfListClean`, `MidiUtils.convertChordToLength` and `PhraseEffectUtils.multiDelayPhrase`. Coverage includes Hits 1/3/8/10/32, Span 1–4, Arpeggio Repeat 1–4, flip, sentinel-rest versus zero insertion, velocity slicing, close/open/multi-octave voice arrays reduced/expanded to 2–6 voices, and feedback count/duration/velocity extremes. Feedback source onset is 12 beats to retain negative delays during comparison; the TS timeline clips events earlier than beat zero, an explicit editor/export boundary rule.

These helper fixtures do **not** establish complete Java Bass/Chord/Arpeggio phrase parity. At P4g those generators preserved existing translated articulation/pitch/dynamics while integrating shared consumers. P5a adds Bass join modes and the bounded complete-phrase evidence above. Arpeggio preserves its translated direction/octave/pitch and per-chord dynamic streams; its pause stream uses part seed + 30004 and split stream + 30002. Chords preserves its preceding per-chord velocity stream and adds Java's pause stream + 20051. P4g does not translate advanced join/strum/transitions, contour/correction, ghosts or reroll policies. All previous fixtures remain in the suite.

```powershell
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -encoding UTF-8 -cp 'midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' -d VibeComposerJS/frontend/.angular/shared-fixtures midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/PhrasePatternUtils.java midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/PhraseEffectUtils.java VibeComposerJS/frontend/scripts/java/SharedPartFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' '-Djava.awt.headless=true' -cp 'VibeComposerJS/frontend/.angular/shared-fixtures;midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' org.vibehistorian.vibecomposer.generation.SharedPartFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/shared-part.java.json
```

`DrumCoreFixture.java` supplies `../fixtures/drum-core.java.json`: **96 complete supported single-pitch percussion phrases**, using the production `DrumPhraseGenerator` and `MidiGenerator.swingPhrase`. It covers four exact long/int-overflow seeds, Java's 16-note percussion pool, all eight supported rhythm identities, Hits 1/3/4/5/8/10/16/32, Shift/Flip, Euclidean pulses, custom grids, Fill/Flip, velocity grids/zeros and Swing. TypeScript compares pitch/dynamics exactly and onset/duration within 1e-10. Zero-dynamic Java notes are omitted as silent events. UI maximum velocity 89 is inclusive and maps to Java's exclusive bound 90.

The original comparison fixes order/orderOffset to 1, chord span/repeat to 1, Pause/Exception to 0, Ghosts off, four-beat progression slots, one measure through the progression, run duration multiplier 0.95, Swing unit 1, no substitutions/melody-following/custom mapping, no forced dynamics/variations/transitions and no arrangement velocity scaling. Part length uses the existing TypeScript wrapper and is tested through parsed MIDI, rather than claimed as part of this Java oracle. Secondary tracks retain the existing stable-ID seed derivation. P4g's additional comparisons are described above; Ghosts/advanced drum options remain P5.

Compile current production classes as described below for Melody; explicitly compile the drum consumer into the fixture directory too. Compiler output stays in `.angular`.

```powershell
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -encoding UTF-8 -cp 'midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' -d VibeComposerJS/frontend/.angular/drum-fixtures midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/InstPhraseGenerator.java midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/DrumPhraseGenerator.java VibeComposerJS/frontend/scripts/java/DrumCoreFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' '-Djava.awt.headless=true' -cp 'VibeComposerJS/frontend/.angular/drum-fixtures;midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' org.vibehistorian.vibecomposer.generation.DrumCoreFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/drum-core.java.json
```

`MelodyCoreFixture.java` supplies `../fixtures/melody-core.java.json`: **80 complete supported melody phrases**, rather than helper-only masks. It invokes the current production `MelodyGenerator`, `MelodyBlockSkeletonGenerator`, `MelodyExpansion` and `MelodyPhraseBuilder`, including production swing and note-length preparation. It covers four seeds (including exact longs above JavaScript's safe range and int overflow), seed offsets, explicit part seeds, four keys, major/natural minor, 1/3/4/7/8 chord slots, speed -100/0/50/100, repeated/inverted/zero block identities, target lists, Flex, Max Block Change, Block Jump, pauses/fill, accents, swing and length. TypeScript compares pitch/dynamics exactly and beat timing/duration within 1e-10.

The supported P1b configuration fixes New Blocks to 100%, tonic-relative target mode 2, rhythm+notes pattern effect 2 and maximum direction changes 2. Doubled rhythm, note exceptions, splitting/leading, arpy surprises, key emphasis, target percentages, custom durations, avoid-note replacement, transitions, section variations and arrangement velocity scaling are disabled; Start% is 100 and Fill Pauses Per Chord is false. It supplies root-only progression inputs, so chord-driven minor-chord remapping/inference is outside this comparison. Extended configurations remain P5. The Java melody role's seed offset is zero and orderOffset is 1; secondary TypeScript tracks continue using the existing stable-ID seed derivation once, before the Java int narrowing.

Compile the Java app's **current production sources** first (`mvn -DskipTests compile` from `midimasterpiece`), then regenerate from the repository root. `target/classes` precedes the dependency JAR so its older bundled generator classes cannot supply the oracle. The fixture disables logging and emits ASCII-safe JSON for Windows console encoding.

```powershell
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -encoding UTF-8 -cp 'midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' -d VibeComposerJS/frontend/.angular/melody-fixtures VibeComposerJS/frontend/scripts/java/MelodyCoreFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' '-Djava.awt.headless=true' -cp 'VibeComposerJS/frontend/.angular/melody-fixtures;midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' org.vibehistorian.vibecomposer.generation.MelodyCoreFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/melody-core.java.json
```

`VelocityPatternFixture.java` supplies `../fixtures/velocity-pattern.java.json`: 84 cases across four 32-cell velocity grids, seven Hits values and three shifts. It uses production `ChordPart`/`InstPart` storage and `MidiGeneratorUtils.multiplyVelocity` with 100% volume. Its selection loop mirrors the audited `ChordPhraseGenerator` loop (first Hits entries, no rhythm Shift/Flip). This validates stored velocity selection and unscaled zero handling, not full phrase generation.

```powershell
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -cp midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar -d VibeComposerJS/frontend/.angular/fill-fixtures midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Parts/InstPart.java midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Parts/ChordPart.java midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation/MidiGeneratorUtils.java VibeComposerJS/frontend/scripts/java/VelocityPatternFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' -cp 'VibeComposerJS/frontend/.angular/fill-fixtures;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' VelocityPatternFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/velocity-pattern.java.json
```

`CustomPatternFixture.java` supplies `../fixtures/custom-pattern.java.json`: 1,152 cases across four 32-cell grids, Hits 1–32 and Shift 0–8. It invokes production `InstPart.getFinalPatternCopy()` through `ChordPart`, then takes Hits as the chord consumer does. Tests check these masks and their flipped complements. The fixture validates custom-grid rotation and truncation, not full Java chord generation or widget painting behavior.

```powershell
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -cp midimasterpiece/target/VibeComposer-2.6-beta-JAR.jar -d VibeComposerJS/frontend/.angular/fill-fixtures midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Parts/InstPart.java midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Parts/ChordPart.java VibeComposerJS/frontend/scripts/java/CustomPatternFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' -cp 'VibeComposerJS/frontend/.angular/fill-fixtures;midimasterpiece/target/VibeComposer-2.6-beta-JAR.jar' CustomPatternFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/custom-pattern.java.json
```

`ChordSpanFillFixture.java` calls the production Java enum, including flipped masks, at lengths 0, 1, 2, 3, 4, 5, 7, 8, 9, 16 and 32. The resulting 264 cases are saved in `../fixtures/chord-span-fill.java.json` and checked by `part-settings.test.cjs`. The fixture validates fill masks, not full phrase-generator parity.

To regenerate from the repository root, use a JDK and the Java app's built dependency JAR (the example uses the available local JDK):

```powershell
New-Item -ItemType Directory -Force -Path VibeComposerJS/frontend/.angular/fill-fixtures | Out-Null
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -cp midimasterpiece/target/VibeComposer-2.6-beta-JAR.jar -d VibeComposerJS/frontend/.angular/fill-fixtures midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Enums/ChordSpanFill.java VibeComposerJS/frontend/scripts/java/ChordSpanFillFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' -cp 'VibeComposerJS/frontend/.angular/fill-fixtures;midimasterpiece/target/VibeComposer-2.6-beta-JAR.jar' ChordSpanFillFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/chord-span-fill.java.json
```

The enum itself is compiled from current source rather than read from the JAR. The JAR only supplies dependencies; compiler output stays in the ignored `.angular` directory.

P6d adds `DrumRerollFixture.java.template` / `../fixtures/drum-reroll.java.json`: **480 exact pitch-selection cases** covering four exact long seeds, target counts 0/1/2/3/4/8/16/64, reroll/add modes and eight remaining-kit combinations. The generator inserts the pitch-selection block **verbatim from current `DrumGUI.createRandomDrumPanels`** into a small deterministic harness with lightweight remaining-panel records and compiles production `InstUtils` for its percussion catalogue. Only the parent Random is seeded explicitly; the pitch branch is not rewritten in the fixture. This validates core reservation, duplicate thinning, sorting, modulo sampling and Java's literal indexed add-pool removals, rather than claiming complete GUI random draw or phrase parity. Pause baking/growth, supported family defaults, history/ownership and MIDI are checked separately by functional tests.

Regenerate from the repository root (compiler outputs stay in `.angular`):

```powershell
& 'VibeComposerJS/frontend/scripts/java/generate-drum-reroll-fixtures.ps1' -Jdk 'C:/Java/jdk-24.0.2'
```

`RhythmPatternFixture.java` supplies `../fixtures/rhythm-pattern.java.json`: 192 static rhythm masks from the production enum across six patterns, eight lengths (including 3, 5, 9 and 31), and four shifts. Tests also compare each mask's flipped complement. These fixtures validate padded-pattern rotation before truncation, rather than full Java phrase-generation parity. CUSTOM and MELODY1 remain deferred; EUCLID has its own fixture below.

```powershell
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -cp midimasterpiece/target/VibeComposer-2.6-beta-JAR.jar -d VibeComposerJS/frontend/.angular/fill-fixtures midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Enums/RhythmPattern.java VibeComposerJS/frontend/scripts/java/RhythmPatternFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' -cp 'VibeComposerJS/frontend/.angular/fill-fixtures;midimasterpiece/target/VibeComposer-2.6-beta-JAR.jar' RhythmPatternFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/rhythm-pattern.java.json
```

`EuclideanPatternFixture.java` supplies `../fixtures/euclidean-pattern.java.json`: all 5,040 combinations of Hits 1–32, Pulses 0–Hits, and Shift 0–8, calling `RhythmPattern.makeEuclideanPattern(..., null)`. Tests compare each mask and its flipped complement. The new Pulses setting represents the count of positive entries in Java's `comboPanel.getTruePattern()` / `InstPart.customPattern`, not an additional Java part field. Generation caps requested Pulses at Hits; the helper itself requires Pulses ≤ Hits. This verifies the rhythm algorithm, not the remaining chord-generator behavior.

```powershell
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -cp midimasterpiece/target/VibeComposer-2.6-beta-JAR.jar -d VibeComposerJS/frontend/.angular/fill-fixtures midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Enums/RhythmPattern.java VibeComposerJS/frontend/scripts/java/EuclideanPatternFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' -cp 'VibeComposerJS/frontend/.angular/fill-fixtures;midimasterpiece/target/VibeComposer-2.6-beta-JAR.jar' EuclideanPatternFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/euclidean-pattern.java.json
```


P5e's catalogue checkpoint adds `MelodyCatalogueFixture.java` / `../fixtures/melody-catalogue.java.json`: **4,500 production catalogue helper cases** comparing family, shape and the subsequent random draw. The matrix covers four exact seeds, changes −7–7, approximation 0/1/4, unrestricted and 1–4 note lengths, variance/direction constraints, five weight combinations and empty/default/reverse ranked preferences. It calls production `MelodyBlockUtils.getRandomByApproxBlockChangeAndLength`; it does not reimplement the oracle.

`MelodyExtendedFixture catalogue` / `../fixtures/melody-catalogue-phrases.java.json` adds **1,536 complete phrases**: eight new/static probability and preference combinations × the 192 extended seed/part cases, including all pattern effects, cached/forced lengths, Flex and post-selection family replacement. New blocks probabilities include 0/25/50/75/100; weights include all 100, all zero, one family, mixed families and only Interval/Note. As with the prepared-target fixture, Split long and Lead chords are disabled because Java's early-chord B filter can empty a split-pitch range and throw. The preceding articulation/effects fixtures retain their comparisons. Supported context remains root-only tonic-relative mode 2, without tonal targeting, custom durations or legacy generation.

Production selection quirks remain literal: duplicate direct/inverse zero-change candidates, first-five-family primary thinning, all-seven-family replacement weights, unchanged pools for zero/unmatched primary weights, fallback classification and the length-one shortcut. These weights are preferences, not strict bans. Default project New blocks 100 and empty rank preserve preceding output; the Default order action selects Java's `0,7,4,3,1,2,5,6` order. Both fixture scripts compile current catalogue/helper sources ahead of target classes/dependency JAR. Run from `VibeComposerJS/frontend`:

```powershell
./scripts/java/generate-melody-catalogue-fixtures.ps1
./scripts/java/generate-melody-extended-fixtures.ps1 -Catalogue
node --test --test-name-pattern='catalogue' scripts/part-settings.test.cjs
```
