param([string]$Jdk = 'C:/Java/jdk-24.0.2')
$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$sourceRoot = Join-Path $repository 'midimasterpiece/src/main/java/org/vibehistorian/vibecomposer'
$arp = Get-Content (Join-Path $sourceRoot 'gui/ArpGUI.java') -Raw
$start = $arp.IndexOf('if (panelGenerator.nextBoolean()) {', $arp.IndexOf('ip.growPattern'))
$end = $arp.IndexOf('if (needNewChannel)', $start)
if ($start -lt 0 -or $end -le $start) { throw 'Production Arp direction block not found.' }
$output = Join-Path $repository 'VibeComposerJS/frontend/.angular/extended-creation-fixtures'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$template = Get-Content (Join-Path $PSScriptRoot 'ExtendedCreationFixture.java.template') -Raw
$template.Replace('/* DIRECTION_BRANCH */', $arp.Substring($start, $end - $start)) | Set-Content -Encoding utf8 (Join-Path $output 'ExtendedCreationFixture.java')
$classes = Join-Path $repository 'midimasterpiece/target/classes'
$jar = Join-Path $repository 'midimasterpiece/target/VibeComposer-2.6r-beta-JAR.jar'
& "$Jdk/bin/javac.exe" -encoding UTF-8 -cp "$classes;$jar" -d $output (Join-Path $sourceRoot 'Enums/ArpPattern.java') (Join-Path $output 'ExtendedCreationFixture.java')
if ($LASTEXITCODE -ne 0) { throw 'Extended creation compilation failed.' }
$json = & "$Jdk/bin/java.exe" -cp "$output;$classes;$jar" ExtendedCreationFixture
if ($LASTEXITCODE -ne 0) { throw 'Extended creation execution failed.' }
$json | Set-Content -Encoding utf8 (Join-Path $repository 'VibeComposerJS/frontend/scripts/fixtures/extended-creation.java.json')
