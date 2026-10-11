param([string]$Jdk = 'C:/Java/jdk-24.0.2')
$ErrorActionPreference = 'Stop'
$repositoryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($PSScriptRoot, '../../../..'))
$outputDirectory = Join-Path $repositoryRoot 'VibeComposerJS/frontend/.angular/harmony-inference-fixtures'
$classes = Join-Path $repositoryRoot 'midimasterpiece/target/classes'
$dependencyJar = Join-Path $repositoryRoot 'midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar'
$sourceRoot = Join-Path $repositoryRoot 'midimasterpiece/src/main/java/org/vibehistorian/vibecomposer'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$sources = @((Join-Path $sourceRoot 'MidiUtils.java'), (Join-Path $sourceRoot 'Enums/BlockType.java'))
$sources += @('MelodyBlockUtils','MelodyUtils','MelodyBlockSkeletonGenerator','MelodyExpansion','MelodyNoteUtils','MelodyPhraseBuilder','MelodyTargetUtils','MelodyChordInference','MelodyGenerationState','MelodyGenerator','SectionGenerationPlanner') | ForEach-Object { Join-Path $sourceRoot "generation/$_.java" }
$sources += @((Join-Path $PSScriptRoot 'ArpCoreFixture.java'),(Join-Path $PSScriptRoot 'HarmonyInferenceFixture.java'))
& (Join-Path $Jdk 'bin/javac.exe') -encoding UTF-8 -cp "$classes;$dependencyJar" -d $outputDirectory @sources
if ($LASTEXITCODE -ne 0) { throw 'Harmony inference fixture compilation failed.' }
$json = & (Join-Path $Jdk 'bin/java.exe') '-Djava.awt.headless=true' -cp "$outputDirectory;$classes;$dependencyJar" 'org.vibehistorian.vibecomposer.generation.HarmonyInferenceFixture'
if ($LASTEXITCODE -ne 0) { throw 'Harmony inference fixture generation failed.' }
$json | Set-Content -Encoding utf8 -LiteralPath (Join-Path $repositoryRoot 'VibeComposerJS/frontend/scripts/fixtures/harmony-inference.java.json')
