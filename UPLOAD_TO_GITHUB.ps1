#Requires -Version 5.1
[CmdletBinding()]
param(
    [string]$ApkPath,
    [string]$VersionName,
    [long]$VersionCode = 0,
    [string]$Repository = 'Abu3obida10/Abo-Uobidah-TV-Smart',
    [string]$ReleaseNotes = 'إصلاحات وتحسينات لتطبيق Abo Uobidah TV.'
)
$publisher = Join-Path $PSScriptRoot 'PUBLISH_RELEASE_ONLY.ps1'
if (-not (Test-Path -LiteralPath $publisher)) { throw 'PUBLISH_RELEASE_ONLY.ps1 is missing.' }
$argsList = @('-NoProfile','-ExecutionPolicy','Bypass','-File',$publisher,'-Repository',$Repository,'-ReleaseNotes',$ReleaseNotes)
if ($ApkPath) { $argsList += @('-ApkPath',$ApkPath) }
if ($VersionName) { $argsList += @('-VersionName',$VersionName) }
if ($VersionCode -gt 0) { $argsList += @('-VersionCode',[string]$VersionCode) }
& powershell.exe @argsList
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
