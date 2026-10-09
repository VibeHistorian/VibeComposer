P6b adds `RolePolicyFixture.java` / `../fixtures/role-policy.java.json`: 64 production `MelodyUtils.getRandomMelodyPattern(0, seed)` choices, 23 production `DrumDefaults` blueprint tuples plus four custom 16-cell grids, and 36 audited int-seeded `BassGUI.createRandomBassPanels` branch cases (four long/int-overflow seeds × three orders × three spans). Tests convert Java exclusive velocity maxima to the app’s inclusive bounds. Bass pause draws are consumed but their GUI baking is not ported. Drum tests exercise all tuple variants with Shift chance 0. This is helper/branch evidence, not complete GUI-reroll/phrase parity.

```powershell
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -encoding UTF-8 -cp 'midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' -d VibeComposerJS/frontend/.angular/role-policy-fixtures midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Parts/Defaults/DrumDefaults.java midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Parts/Defaults/DrumSettings.java VibeComposerJS/frontend/scripts/java/RolePolicyFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' '-Djava.awt.headless=true' -cp 'VibeComposerJS/frontend/.angular/role-policy-fixtures;midimasterpiece/target/classes;midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar' org.vibehistorian.vibecomposer.Parts.Defaults.RolePolicyFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/role-policy.java.json
```

# Java pattern fixtures

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
