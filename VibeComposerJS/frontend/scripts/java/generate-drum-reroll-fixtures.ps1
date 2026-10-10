param([string]$Jdk = 'C:/Java/jdk-24.0.2')
$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
Push-Location $repository
try {
    $drumSource = Get-Content 'midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/gui/DrumGUI.java' -Raw
    $branchStart = $drumSource.IndexOf('List<Integer> pitches = new ArrayList<>();')
    $branchEnd = $drumSource.IndexOf('int chords = 2;', $branchStart)
    if ($branchStart -lt 0 -or $branchEnd -le $branchStart) { throw 'Production drum pitch branch not found.' }
    $branch = $drumSource.Substring($branchStart, $branchEnd - $branchStart)
    $template = Get-Content 'VibeComposerJS/frontend/scripts/java/DrumRerollFixture.java.template' -Raw
    $outputDirectory = 'VibeComposerJS/frontend/.angular/drum-reroll-fixtures'
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
    $template.Replace('/* PITCH_BRANCH */', $branch) | Set-Content -Encoding utf8 "$outputDirectory/DrumRerollFixture.java"
    & "$Jdk/bin/javac.exe" -encoding UTF-8 -d $outputDirectory 'midimasterpiece/src/main/java/org/vibehistorian/vibecomposer/InstUtils.java' "$outputDirectory/DrumRerollFixture.java"
    if ($LASTEXITCODE -ne 0) { throw 'Fixture compilation failed.' }
    $fixtureJson = & "$Jdk/bin/java.exe" -cp $outputDirectory DrumRerollFixture
    if ($LASTEXITCODE -ne 0) { throw 'Fixture execution failed.' }
    $fixtureJson | Set-Content -Encoding utf8 'VibeComposerJS/frontend/scripts/fixtures/drum-reroll.java.json'
} finally {
    Pop-Location
}
