# Runs DarkBot in Unity packet mode (BROWSER_API=UNITY_PACKET) against the live game.
#
# Prerequisites:
#   1. DarkBot/config.json -> BOT_SETTINGS.API_CONFIG.BROWSER_API must be "UNITY_PACKET"
#      (the dropdown shows it as "Unity Packet API"; a stale value like SIMULATOR_API
#      makes gson discard the whole config silently).
#   2. DarkBot/login.unity.properties holds the credentials:
#        - Portal login:   username=... password=... server=es2
#        - Saved session:  gameSid=<map-server sid> userId=... server=es2 mapId=12
#          (or sid=<dosid> to go through the portal SSO exchange)
#      Optional diagnostic flags for validation runs:
#        traceOutbound=1            -> [unity-c2s]/[unity-s2c] packet trace on stdout
#        diagnosticMove=1           -> one controlled movement once the session is READY
#        diagnosticMoveDistance=200 -> distance in map units for that movement
#
# Usage:
#   .\run-unity.ps1                 # login + start the bot
#   .\run-unity.ps1 -NoStart       # login only (session connects, bot stays idle)
param(
    [switch]$NoStart
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not (Test-Path 'login.unity.properties')) {
    Write-Error "login.unity.properties not found next to this script."
    exit 1
}

$configPath = Join-Path $PSScriptRoot 'config.json'
if ((Test-Path $configPath) -and -not ((Get-Content $configPath -Raw) -match '"UNITY_PACKET"')) {
    Write-Warning "config.json does not mention UNITY_PACKET - check BROWSER_API before assuming packet mode."
}

$startArg = if ($NoStart) { '' } else { ' -start' }
$logDir = Join-Path $PSScriptRoot 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$stamp = Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'
$logFile = Join-Path $logDir "unity-run-$stamp.log"

Write-Host "[run-unity] console output also saved to $logFile"
& .\gradlew.bat run --refresh-dependencies "--args=-login login.unity.properties$startArg" 2>&1 |
    Tee-Object -FilePath $logFile

exit $LASTEXITCODE
