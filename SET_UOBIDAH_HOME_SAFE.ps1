# Compatibility entry point. All HOME setup now uses the same checked implementation.
[CmdletBinding()]
param([string]$Serial = '', [string]$Adb = '', [string]$TvIp = '')
$ErrorActionPreference = 'Stop'
$canonical = Join-Path $PSScriptRoot 'FORCE_HOME_ADB.ps1'
if (-not (Test-Path -LiteralPath $canonical)) { throw 'FORCE_HOME_ADB.ps1 is missing.' }
$cliArgs = @('-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',$canonical)
if ($Serial) { $cliArgs += @('-Serial',$Serial) }
if ($Adb) { $cliArgs += @('-Adb',$Adb) }
if ($TvIp) { $cliArgs += @('-TvIp',$TvIp) }
& powershell.exe @cliArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
exit 0
