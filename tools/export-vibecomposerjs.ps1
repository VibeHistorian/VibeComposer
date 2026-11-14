# Prepare a source-only snapshot. Keep this publisher in the development repo.
#   pwsh -File tools/export-vibecomposerjs.ps1
#   pwsh -File tools/export-vibecomposerjs.ps1 -Destination D:/Production/IdeaProjects/VibeComposerJS
#   pwsh -File tools/export-vibecomposerjs.ps1 -Destination D:/Production/IdeaProjects/VibeComposerJS -Apply
# Destination preview/apply requires a clean checkout. Commit each applied snapshot once.
[CmdletBinding()]
param([string]$Destination, [switch]$Apply)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$source = Join-Path $repo 'VibeComposerJS'
$stage = Join-Path $repo '.release-export/VibeComposerJS'
$utf8 = [Text.UTF8Encoding]::new($false)

function Invoke-Git([string]$Root, [string[]]$Arguments) {
    $Root = $Root.Replace('\', '/')
    $result = & git -c "safe.directory=$Root" -C $Root @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Git failed: $Arguments" }
    return $result
}

# Every file operation resolves beneath its explicitly named root. Refuse symlinks
# and junctions so a checkout cannot redirect writes outside that root.
function Get-SafePath([string]$Root, [string]$Relative) {
    $rootPath = [IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
    $path = [IO.Path]::GetFullPath((Join-Path $rootPath $Relative))
    if (-not $path.StartsWith($rootPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path escapes root: $Relative"
    }
    $cursor = $path
    while ($cursor) {
        if (Test-Path -LiteralPath $cursor) {
            if ((Get-Item -Force -LiteralPath $cursor).Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw "Refusing reparse point: $cursor"
            }
        }
        $cursor = [IO.Path]::GetDirectoryName($cursor)
    }
    return $path
}

$files = [Collections.Generic.Dictionary[string, byte[]]]::new([StringComparer]::Ordinal)
$tracked = @(Invoke-Git $repo @('ls-files', '--', 'VibeComposerJS'))
$rootFiles = @('.gitattributes', 'App.go', 'main.go', 'go.mod', 'go.sum', 'wails.json')
$frontendFiles = @('angular.json', 'package.json', 'pnpm-lock.yaml', 'pnpm-workspace.yaml', 'tsconfig.json', 'tsconfig.app.json')
foreach ($trackedPath in $tracked) {
    $relative = $trackedPath.Substring('VibeComposerJS/'.Length)
    $include = $relative -in $rootFiles -or
        ($relative.StartsWith('frontend/') -and $relative.Substring(9) -in $frontendFiles) -or
        $relative -match '^frontend/(src|public|wailsjs|patches)/'
    if (-not $include) { continue }
    if ($relative -match '(^|/)(\.[^/]+|node_modules|__work_history|scripts|dist|build)(/|$)' -and
        $relative -notin @('.gitattributes', 'frontend/patches/.gitattributes')) { continue }
    if ($relative -match '\.md$' -and $relative -notmatch '^frontend/patches/' -and
        $relative -notmatch '(^|/)(LICENSE|COPYING|NOTICE)(\.|$)') { continue }
    if ($relative -match '(?i)(\.test\.|\.spec\.|\.(cjs|log|png|sf2|sf3|mid|midi|exe|dll|wasm|wav|mp3|zip|lnk|iml)$)') { continue }
    # Explicit extension allowlist prevents future samples/binaries entering even
    # when somebody adds them inside a source or public directory.
    if ($relative -notmatch '(?i)(\.(go|mod|sum|json|yaml|ts|js|html|css|svg|patch|md)$|(^|/)(LICENSE|COPYING|NOTICE)(\.[^/]+)?$|/licenses/[^/]+\.txt$|\.gitattributes$)') { continue }
    $path = Get-SafePath $source $relative
    $files.Add($relative, [IO.File]::ReadAllBytes($path))
}

# This workflow belongs at the release repo's root, not the development repo's
# root. Keep its template beside this publisher and include it explicitly.
$workflow = Get-SafePath $repo 'tools/release/pages.yml'
$files.Add('.github/workflows/pages.yml', [IO.File]::ReadAllBytes($workflow))

$package = [Text.Encoding]::UTF8.GetString($files['frontend/package.json']) | ConvertFrom-Json -AsHashtable
# The exported checkout deliberately excludes all development/test utilities.
$package.scripts = [ordered]@{ start = 'ng serve'; build = 'ng build'; watch = 'ng build --watch --configuration development' }
$files['frontend/package.json'] = $utf8.GetBytes(($package | ConvertTo-Json -Depth 20) + "`n")
$files['go.mod'] = $utf8.GetBytes(([Text.Encoding]::UTF8.GetString($files['go.mod']) -replace
    '(?m)^module .+$', 'module github.com/VibeHistorian/VibeComposerJS') )

# Keep the patch explanations and installation contract, omitting links and test
# instructions that refer to files intentionally absent from the release repo.
$patchInfo = [Text.Encoding]::UTF8.GetString($files['frontend/patches/README.md'])
$patchInfo = ($patchInfo -split 'After changing this patch,')[0]
$patchInfo = $patchInfo -replace '(?s)See `src/app/core/audio/README.md`.*?limitations\.', ''
$files['frontend/patches/README.md'] = $utf8.GetBytes($patchInfo.Trim() + @'


Install with the pnpm version pinned in ../package.json:
  pnpm install --frozen-lockfile
The ../pnpm-workspace.yaml file registers both patches; ../pnpm-lock.yaml
records their hashes. Do not apply them by hand or install with npm.
When updating a patch, use pnpm patch / pnpm patch-commit and commit the
patch, workspace configuration and regenerated lockfile together.
'@ + "`n")

$files['.gitignore'] = $utf8.GetBytes(@'
node_modules/
.angular/
dist/
build/
package.json.md5
.idea/
.codex/
.agents/
__work_history/
*.iml
*.log
*.png
*.sf2
*.sf3
*.mid
*.midi
*.exe
*.lnk
*.cjs
*.md
!LICENSE.md
!**/LICENSE.md
!frontend/patches/*.md
'@ + "`n")
$files['BUILDING.txt'] = $utf8.GetBytes(@'
VibeComposerJS - Angular frontend and Wails v2 desktop host

Prerequisites
  Node.js matching frontend/package.json engines; the exact pnpm version
  pinned there; Go matching go.mod; Wails CLI matching its go.mod dependency.
  Wails also needs the platform's native build tools and webview dependencies.
  Platform setup: https://wails.io/docs/gettingstarted/installation

Frontend (from frontend/)
  pnpm install --frozen-lockfile
  pnpm start                 browser development server
  pnpm run build             production output in dist/browser

Desktop (from repository root, after installing frontend dependencies)
  wails dev
  wails build -f             production app in build/bin

Always run the frozen install after pulling dependency/patch changes.
The pnpm workspace applies required library patches; see frontend/patches/.
Do not substitute npm or Yarn. Fonts are bundled through pinned packages;
their license notices are under frontend/public/licenses/.

The Wails bindings in frontend/wailsjs are included for standalone Angular
builds; Wails regenerates them when Go bindings change. Commit updated bindings.
No soundfont downloads are needed for the default synthesized instruments.
Optional SF2 banks are supplied by the user and are not included here.

This repository contains release snapshots. Development history, internal
documentation, fixtures and test tools remain in the development repository.
'@ + "`n")

# Fail before copying if a local filesystem path or binary sneaks into the export.
# localhost in Wails dev config is intentional and is not a filesystem path.
foreach ($entry in $files.GetEnumerator()) {
    $content = [Text.Encoding]::UTF8.GetString($entry.Value)
    if ($content.Contains([char]0) -or $content -match '(?i)(?<![a-z0-9])[a-z]:[\\/]|file:///|/(Users|home|mnt/[a-z])/' -or
        $content -match '\\\\[a-zA-Z0-9_.-]+\\[a-zA-Z0-9_$.-]+\\') {
        throw "Binary data or local filesystem path in $($entry.Key)"
    }
}
foreach ($required in @('main.go', 'go.mod', 'frontend/src/main.ts', 'frontend/pnpm-lock.yaml',
    'frontend/patches/smplr@1.1.0.patch', 'frontend/patches/webaudio-tinysynth@1.1.3.patch',
    'frontend/wailsjs/go/main/App.js', 'frontend/public/licenses/DM-Sans-OFL.txt', 'frontend/public/licenses/DM-Mono-OFL.txt')) {
    if (-not $files.ContainsKey($required)) { throw "Missing required file: $required" }
}

# Clear only previously exported files, leaving local validation dependencies
# and build output alone. This manifest stays in the development repo's staging
# parent, never in the published checkout.
$null = Get-SafePath $repo '.release-export/VibeComposerJS'
 $manifest = Get-SafePath $repo '.release-export/vibecomposerjs-files.json'
if (Test-Path -LiteralPath $manifest) {
    foreach ($old in (Get-Content -Raw -LiteralPath $manifest | ConvertFrom-Json)) {
        $checked = Get-SafePath $stage $old
        if (-not $files.ContainsKey($old) -and (Test-Path -LiteralPath $checked)) {
            Remove-Item -LiteralPath $checked
        }
    }
}
foreach ($entry in $files.GetEnumerator()) {
    $path = Get-SafePath $stage $entry.Key
    $null = [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path))
    [IO.File]::WriteAllBytes($path, $entry.Value)
}
[IO.File]::WriteAllText($manifest, (ConvertTo-Json -InputObject @($files.Keys | Sort-Object)), $utf8)
$sourceCommit = Invoke-Git $repo @('rev-parse', 'HEAD')
Write-Output "Prepared $($files.Count) files in $stage"
Write-Output "Source HEAD: $sourceCommit (exports current tracked working-tree contents)"
if (@(Invoke-Git $repo @('status', '--porcelain', '--', 'VibeComposerJS')).Count) {
    Write-Warning 'Source has uncommitted changes. Commit them before recording release provenance.'
}
if (-not $Destination) {
    if ($Apply) { throw '-Apply requires -Destination.' }
    return
}
$target = (Resolve-Path -LiteralPath $Destination).Path
$gitRoot = Invoke-Git $target @('rev-parse', '--show-toplevel')
if ([IO.Path]::GetFullPath($gitRoot) -ne [IO.Path]::GetFullPath($target)) { throw 'Destination must be a repository root.' }
if ($target -eq $repo -or $target.StartsWith($repo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    $repo.StartsWith($target + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Source and destination must be separate checkouts.' }
if (@(Invoke-Git $target @('status', '--porcelain', '--untracked-files=all')).Count) { throw 'Destination must be clean; commit or save its changes first.' }
$deletions = @(Invoke-Git $target @('ls-files') | Where-Object {
    $_ -notmatch '^(LICENSE|COPYING|NOTICE)(\.[^/]+)?$' -and -not $files.ContainsKey($_)
})
Write-Output "Destination: $target; $($deletions.Count) obsolete tracked files to remove. Root licenses are preserved."
foreach ($relative in $deletions) { Write-Output "Remove: $relative" }
# Resolve ALL paths before the first destination mutation.
foreach ($relative in @($files.Keys) + $deletions) { $null = Get-SafePath $target $relative }
if (-not $Apply) { Write-Output 'Preview only. Rerun with -Apply to copy; no commits or pushes are made.'; return }
foreach ($relative in $deletions) { Remove-Item -LiteralPath (Get-SafePath $target $relative) }
foreach ($entry in $files.GetEnumerator()) {
    $path = Get-SafePath $target $entry.Key
    $null = [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path))
    [IO.File]::WriteAllBytes($path, $entry.Value)
}
Write-Output 'Snapshot applied. Review and build it, then commit once for this version. Nothing was staged, committed or pushed.'
