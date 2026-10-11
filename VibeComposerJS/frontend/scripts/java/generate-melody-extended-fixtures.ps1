param([string]$Jdk = 'C:/Java/jdk-24.0.2')
$ErrorActionPreference = 'Stop'
$repositoryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($PSScriptRoot, '../../../..'))
$outputDirectory = Join-Path $repositoryRoot 'VibeComposerJS/frontend/.angular/melody-fixtures'
$classes = Join-Path $repositoryRoot 'midimasterpiece/target/classes'
$dependencyJar = Join-Path $repositoryRoot 'midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar'
$javaSourceRoot = Join-Path $repositoryRoot 'midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/generation'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$sources = @(
    (Join-Path $javaSourceRoot 'MelodyBlockSkeletonGenerator.java'),
    (Join-Path $javaSourceRoot 'MelodyExpansion.java'),
    (Join-Path $javaSourceRoot 'MelodyPhraseBuilder.java'),
    (Join-Path $PSScriptRoot 'MelodyExtendedFixture.java')
)
& (Join-Path $Jdk 'bin/javac.exe') -encoding UTF-8 -cp "$classes;$dependencyJar" -d $outputDirectory @sources
if ($LASTEXITCODE -ne 0) { throw 'Extended Melody fixture compilation failed.' }
$json = & (Join-Path $Jdk 'bin/java.exe') '-Djava.awt.headless=true' -cp "$outputDirectory;$classes;$dependencyJar" 'org.vibehistorian.vibecomposer.generation.MelodyExtendedFixture'
if ($LASTEXITCODE -ne 0) { throw 'Extended Melody fixture generation failed.' }
$json | Set-Content -Encoding utf8 -LiteralPath (Join-Path $repositoryRoot 'VibeComposerJS/frontend/scripts/fixtures/melody-extended.java.json')
