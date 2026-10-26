# Run once from an administrator PowerShell after reviewing/building the source.
# The launcher itself must subsequently run WITHOUT administrator elevation.
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Fa-f0-9]{64}$')]
    [string] $ExpectedSha256
)
$ErrorActionPreference = 'Stop'
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Installation requires an administrator PowerShell. Running the browser broker does not.'
}
$source = Join-Path $PSScriptRoot 'build\BrowserLauncher.exe'
$bytes = [IO.File]::ReadAllBytes($source)
$hasher = [Security.Cryptography.SHA256]::Create()
try { $sourceHash = [BitConverter]::ToString($hasher.ComputeHash($bytes)).Replace('-', '') } finally { $hasher.Dispose() }
if ($sourceHash -ne $ExpectedSha256) {
    throw 'The built launcher changed after review; installation stopped.'
}
$destination = 'C:\Program Files\VibeComposerBrowserLauncher'
# Reject pre-existing directory junctions, links, and unexpected files.
if (Test-Path -LiteralPath $destination) {
    if ((Get-Item -LiteralPath $destination -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw 'The installation directory must not be a reparse point.'
    }
} else {
    New-Item -ItemType Directory -Path $destination | Out-Null
}
$admins = [Security.Principal.SecurityIdentifier]::new('S-1-5-32-544')
$system = [Security.Principal.SecurityIdentifier]::new('S-1-5-18')
$users = [Security.Principal.SecurityIdentifier]::new('S-1-5-32-545')
$acl = [Security.AccessControl.DirectorySecurity]::new()
$acl.SetAccessRuleProtection($true, $false)
$acl.SetOwner($admins)
$inherit = [Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit'
$propagation = [Security.AccessControl.PropagationFlags]::None
$allow = [Security.AccessControl.AccessControlType]::Allow
foreach ($sid in @($admins, $system)) {
    $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid, 'FullControl', $inherit, $propagation, $allow))
}
$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($users, 'ReadAndExecute', $inherit, $propagation, $allow))
Set-Acl -LiteralPath $destination -AclObject $acl
$target = Join-Path $destination 'BrowserLauncher.exe'
if (Test-Path -LiteralPath $target) {
    $item = Get-Item -LiteralPath $target -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'The installed executable must not be a reparse point.' }
}
[IO.File]::WriteAllBytes($target, $bytes)
$fileAcl = [Security.AccessControl.FileSecurity]::new()
$fileAcl.SetAccessRuleProtection($true, $false)
$fileAcl.SetOwner($admins)
foreach ($sid in @($admins, $system)) {
    $fileAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid, 'FullControl', $allow))
}
$fileAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($users, 'ReadAndExecute', $allow))
Set-Acl -LiteralPath $target -AclObject $fileAcl
if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $ExpectedSha256) { throw 'Installed hash mismatch.' }
Write-Output "Installed protected browser launcher: $target"
Write-Output "SHA256: $ExpectedSha256"
