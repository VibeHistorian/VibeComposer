# Java pattern fixtures

`ChordSpanFillFixture.java` calls the production Java enum, including flipped masks, at lengths 0, 1, 2, 3, 4, 5, 7, 8, 9, 16 and 32. The resulting 264 cases are saved in `../fixtures/chord-span-fill.java.json` and checked by `part-settings.test.cjs`. The fixture validates fill masks, not full phrase-generator parity.

To regenerate from the repository root, use a JDK and the Java app's built dependency JAR (the example uses the available local JDK):

```powershell
New-Item -ItemType Directory -Force -Path VibeComposerJS/frontend/.angular/fill-fixtures | Out-Null
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -cp midimasterpiece/target/VibeComposer-2.6-beta-JAR.jar -d VibeComposerJS/frontend/.angular/fill-fixtures midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Enums/ChordSpanFill.java VibeComposerJS/frontend/scripts/java/ChordSpanFillFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' -cp 'VibeComposerJS/frontend/.angular/fill-fixtures;midimasterpiece/target/VibeComposer-2.6-beta-JAR.jar' ChordSpanFillFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/chord-span-fill.java.json
```

The enum itself is compiled from current source rather than read from the JAR. The JAR only supplies dependencies; compiler output stays in the ignored `.angular` directory.

`RhythmPatternFixture.java` supplies `../fixtures/rhythm-pattern.java.json`: 192 static rhythm masks from the production enum across six patterns, eight lengths (including 3, 5, 9 and 31), and four shifts. Tests also compare each mask's flipped complement. These fixtures validate padded-pattern rotation before truncation, rather than full Java phrase-generation parity. CUSTOM, EUCLID and MELODY1 are not enabled in this slice.

```powershell
& 'C:/Java/jdk-24.0.2/bin/javac.exe' -cp midimasterpiece/target/VibeComposer-2.6-beta-JAR.jar -d VibeComposerJS/frontend/.angular/fill-fixtures midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/Enums/RhythmPattern.java VibeComposerJS/frontend/scripts/java/RhythmPatternFixture.java
& 'C:/Java/jdk-24.0.2/bin/java.exe' -cp 'VibeComposerJS/frontend/.angular/fill-fixtures;midimasterpiece/target/VibeComposer-2.6-beta-JAR.jar' RhythmPatternFixture | Set-Content -Encoding utf8 VibeComposerJS/frontend/scripts/fixtures/rhythm-pattern.java.json
```
