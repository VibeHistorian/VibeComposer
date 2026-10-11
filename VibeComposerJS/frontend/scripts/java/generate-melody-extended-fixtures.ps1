param([string]$Jdk = 'C:/Java/jdk-24.0.2', [switch]$Globals, [switch]$Effects, [switch]$Targets, [switch]$Catalogue, [switch]$Emphasis, [switch]$Tonal, [switch]$Harmony)
$ErrorActionPreference = 'Stop'
$repositoryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($PSScriptRoot, '../../../..'))
$outputDirectory = Join-Path $repositoryRoot 'VibeComposerJS/frontend/.angular/melody-fixtures'
$classes = Join-Path $repositoryRoot 'midimasterpiece/target/classes'
$dependencyJar = Join-Path $repositoryRoot 'midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar'
$javaSourceRoot = Join-Path $repositoryRoot 'midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$sources = @(
    (Join-Path $javaSourceRoot '../Enums/BlockType.java'),
    (Join-Path $javaSourceRoot 'MelodyBlockUtils.java'),
    (Join-Path $javaSourceRoot 'MelodyUtils.java'),
    (Join-Path $javaSourceRoot 'MelodyBlockSkeletonGenerator.java'),
    (Join-Path $javaSourceRoot 'MelodyExpansion.java'),
    (Join-Path $javaSourceRoot 'MelodyNoteUtils.java'),
    (Join-Path $javaSourceRoot 'MelodyPhraseBuilder.java'),
    (Join-Path $javaSourceRoot 'MelodyTargetUtils.java'),
    (Join-Path $PSScriptRoot 'MelodyExtendedFixture.java')
)
& (Join-Path $Jdk 'bin/javac.exe') -encoding UTF-8 -cp "$classes;$dependencyJar" -d $outputDirectory @sources
if ($LASTEXITCODE -ne 0) { throw 'Extended Melody fixture compilation failed.' }
$fixtureArguments = @()
if ($Globals) { $fixtureArguments = @('globals') }
if ($Effects) { $fixtureArguments = @('effects') }
if ($Targets) { $fixtureArguments = @('targets') }
if ($Catalogue) { $fixtureArguments = @('catalogue') }
if ($Emphasis) { $fixtureArguments = @('emphasis') }
if ($Tonal) { $fixtureArguments = @('tonal') }
if ($Harmony) { $fixtureArguments = @('harmony') }
$json = & (Join-Path $Jdk 'bin/java.exe') '-Djava.awt.headless=true' -cp "$outputDirectory;$classes;$dependencyJar" 'org.vibehistorian.vibecomposer.generation.MelodyExtendedFixture' @fixtureArguments
if ($LASTEXITCODE -ne 0) { throw 'Extended Melody fixture generation failed.' }
$fixtureName = if ($Harmony) { 'melody-harmony.java.json' } elseif ($Tonal) { 'melody-tonal.java.json' } elseif ($Emphasis) { 'melody-emphasis.java.json' } elseif ($Catalogue) { 'melody-catalogue-phrases.java.json' } elseif ($Targets) { 'melody-prepared-targets.java.json' } elseif ($Effects) { 'melody-effects.java.json' } elseif ($Globals) { 'melody-global.java.json' } else { 'melody-extended.java.json' }
$json | Set-Content -Encoding utf8 -LiteralPath (Join-Path $repositoryRoot "VibeComposerJS/frontend/scripts/fixtures/$fixtureName")
