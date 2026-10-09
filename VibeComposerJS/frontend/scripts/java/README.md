# Java pattern fixtures

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
