param([string]$Jdk = 'C:/Java/jdk-24.0.2')
$ErrorActionPreference = 'Stop'
$repositoryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($PSScriptRoot, '../../../..'))
$outputDirectory = Join-Path $repositoryRoot 'VibeComposerJS/frontend/.angular/p5-fixtures'
$classes = Join-Path $repositoryRoot 'midimasterpiece/target/classes'
$dependencyJar = Join-Path $repositoryRoot 'midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar'
$fixtureDirectory = Join-Path $repositoryRoot 'VibeComposerJS/frontend/scripts/fixtures'
$javaSourceRoot = Join-Path $repositoryRoot 'midimasterpiece/src/main/java/org/vibehistorian/vibecomposer'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$sources = @(
    (Join-Path $javaSourceRoot 'generation/InstPhraseGenerator.java'),
    (Join-Path $javaSourceRoot 'generation/ArpPhraseGenerator.java'),
    (Join-Path $javaSourceRoot 'Enums/ArpPattern.java'),
    (Join-Path $javaSourceRoot 'generation/DrumPhraseGenerator.java'),
    (Join-Path $javaSourceRoot 'generation/RhythmOffsetUtils.java'),
    (Join-Path $javaSourceRoot 'generation/MelodyPatternUtils.java'),
    (Join-Path $javaSourceRoot 'JMusicUtilsCustom.java'),
    (Join-Path $PSScriptRoot 'ArpCoreFixture.java'),
    (Join-Path $PSScriptRoot 'DrumAdvancedFixture.java'),
    (Join-Path $PSScriptRoot 'MelodyContextFixture.java')
)
& (Join-Path $Jdk 'bin/javac.exe') -encoding UTF-8 -cp "$classes;$dependencyJar" -d $outputDirectory @sources
if ($LASTEXITCODE -ne 0) { throw 'P5 fixture compilation failed.' }
foreach ($fixture in @(
    @{ Class = 'ArpCoreFixture'; File = 'arpeggio-core.java.json' },
    @{ Class = 'DrumAdvancedFixture'; File = 'drum-advanced.java.json' },
    @{ Class = 'MelodyContextFixture'; File = 'melody-context.java.json' }
)) {
    $json = & (Join-Path $Jdk 'bin/java.exe') '-Djava.awt.headless=true' -cp "$outputDirectory;$classes;$dependencyJar" "org.vibehistorian.vibecomposer.generation.$($fixture.Class)"
    if ($LASTEXITCODE -ne 0) { throw "Fixture generation failed: $($fixture.Class)" }
    $json | Set-Content -Encoding utf8 -LiteralPath (Join-Path $fixtureDirectory $fixture.File)
}
