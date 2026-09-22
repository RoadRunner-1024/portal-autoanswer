<#
.SYNOPSIS
    Set up Auto Answer on a Meta Portal, end to end, over adb.

.DESCRIPTION
    Installs the app, grants the one permission it needs, switches auto answer on and checks it
    actually took. Written so a Portal can be prepared for someone else without anyone having to
    tap through settings on the device itself.

    Answering goes through Android's Telecom framework (TelecomManager.acceptRingingCall), which
    needs ANSWER_PHONE_CALLS - grantable over adb, not from inside the app. That route is proven
    on a real Portal. The accessibility service is only a fallback for if Telecom ever refuses,
    and is left OFF unless you ask for it with -Accessibility, since enabling it touches a
    device-wide setting.

    NOTE: this file is deliberately plain ASCII. PowerShell 5.1 reads a UTF-8 file with no BOM as
    ANSI, and a mangled em-dash turns into bytes PowerShell treats as smart quotes, which breaks
    parsing in confusing ways.

.PARAMETER Serial
    Target device, as shown by "adb devices". Optional when only one Portal is attached.

.PARAMETER Apk
    APK to install. Defaults to the debug build in this repo.

.PARAMETER Delay
    Seconds to let it ring before answering (0-15). A few seconds gives the person a chance to
    answer it themselves and is less abrupt for the caller. Default 3.

.PARAMETER Off
    Install and grant, but leave auto answer switched off. Someone turns it on in the app later.

.PARAMETER Accessibility
    Also enable the fallback answerer. Appends to the device's accessibility services rather than
    replacing them, so anything already enabled keeps working.

.EXAMPLE
    .\provision.ps1

.EXAMPLE
    .\provision.ps1 -Serial 818PGA02P110YR09 -Delay 5
#>
[CmdletBinding()]
param(
    [string]$Serial,
    [string]$Apk,
    [ValidateRange(0, 15)][int]$Delay = 3,
    [switch]$Off,
    [switch]$Accessibility
)

# PowerShell 5.1 turns a native command's stderr into error records, and plenty of adb
# subcommands chatter there harmlessly - so don't stop on it. Outcomes are checked
# explicitly below instead.
$ErrorActionPreference = 'Continue'
$pkg = 'com.aeonos.autoanswer'
$accessibilityService = "$pkg/$pkg.AnswerAccessibilityService"

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "    OK   $msg" -ForegroundColor Green }
function Write-Note($msg) { Write-Host "    !    $msg" -ForegroundColor Yellow }
function Fail($msg)       { Write-Host "`nFAILED: $msg" -ForegroundColor Red; exit 1 }

# ---- adb -----------------------------------------------------------------------
$adb = $null
foreach ($candidate in @(
    'F:\claude\platform-tools\adb.exe',
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    'adb'
)) {
    $resolved = Get-Command $candidate -ErrorAction SilentlyContinue
    if ($resolved) { $adb = $resolved.Source; break }
}
if (-not $adb) { Fail "adb not found. Install platform-tools, or put adb on PATH." }

# Splatted rather than a wrapper function: a function would try to bind flags like -r as its
# own parameters.
$ser = @()

# ---- device --------------------------------------------------------------------
Write-Step "Looking for a Portal"
& $adb start-server | Out-Null
$devices = @(& $adb devices |
    Select-String -Pattern '^\S+\s+device$' |
    ForEach-Object { ($_.ToString() -split '\s+')[0] })
if ($devices.Count -eq 0) {
    Fail "No device. Plug the Portal in by USB and accept the 'Allow USB debugging' prompt on its screen."
}
if (-not $Serial -and $devices.Count -gt 1) {
    Fail "More than one device attached ($($devices -join ', ')). Pick one with -Serial."
}
if (-not $Serial) { $Serial = $devices[0] }
$ser = @('-s', $Serial)

$model = ((& $adb @ser shell getprop ro.product.device) -join '').Trim()
$rel   = ((& $adb @ser shell getprop ro.build.version.release) -join '').Trim()
Write-Ok "$Serial  (device=$model, Android $rel)"

# ---- install -------------------------------------------------------------------
if (-not $Apk) { $Apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk' }
if (-not (Test-Path $Apk)) { Fail "APK not found at $Apk. Build it first, or pass -Apk <path>." }

Write-Step "Installing $(Split-Path $Apk -Leaf)"
$installOut = (& $adb @ser install -r "$Apk") -join "`n"
if ($installOut -notmatch 'Success') { Fail "Install failed:`n$installOut" }
Write-Ok "installed"

# ---- permission ----------------------------------------------------------------
Write-Step "Granting the permission it needs to answer"
& $adb @ser shell pm grant $pkg android.permission.ANSWER_PHONE_CALLS | Out-Null
$granted = ((& $adb @ser shell dumpsys package $pkg) -join "`n") -match 'ANSWER_PHONE_CALLS: granted=true'
if ($granted) {
    Write-Ok "ANSWER_PHONE_CALLS granted"
} else {
    Write-Note "ANSWER_PHONE_CALLS NOT granted - it will need the -Accessibility fallback instead"
}

# ---- accessibility fallback (opt-in) -------------------------------------------
if ($Accessibility) {
    Write-Step "Enabling the fallback answerer"
    # Append rather than replace: this setting is a colon-separated list shared with every other
    # accessibility service on the device, and clobbering it would switch those off.
    $current = ((& $adb @ser shell settings get secure enabled_accessibility_services) -join '').Trim()
    if ($current -eq 'null') { $current = '' }
    if ($current -like "*$accessibilityService*") {
        Write-Ok "already enabled"
    } else {
        $updated = if ($current) { "$current" + ":" + "$accessibilityService" } else { $accessibilityService }
        & $adb @ser shell settings put secure enabled_accessibility_services "$updated" | Out-Null
        & $adb @ser shell settings put secure accessibility_enabled 1 | Out-Null
        Write-Ok "enabled (appended, existing services untouched)"
    }
}

# ---- configure and start -------------------------------------------------------
Write-Step "Configuring"
$enabled = (-not $Off).ToString().ToLower()
& $adb @ser shell am broadcast -a "$pkg.CONFIG" --ez enabled $enabled --ei delay $Delay --ez boot true | Out-Null
& $adb @ser shell am start -n "$pkg/.MainActivity" | Out-Null
Start-Sleep -Seconds 3
Write-Ok "auto answer = $enabled, rings for $Delay s first, starts on boot"

# ---- verify --------------------------------------------------------------------
Write-Step "Checking it is running"
$log = (& $adb @ser logcat -d -s AutoAnswer:*) -join "`n"
$line = ($log -split "`n" | Where-Object { $_ -match 'watching for incoming calls' } | Select-Object -Last 1)
if ($line) {
    Write-Ok $line.Trim()
} else {
    Write-Note "no startup line in the log yet - open the app on the Portal and check it says Ready"
}

Write-Host "`nDone." -ForegroundColor Green
if ($Off) {
    Write-Host "Auto answer is OFF. Turn it on in the Auto Answer app when you are ready." -ForegroundColor Yellow
} else {
    Write-Host "Ring the Portal to test. To watch what happens:" -ForegroundColor Gray
    Write-Host "  $adb -s $Serial logcat -s AutoAnswer:*" -ForegroundColor Gray
    Write-Host "Expect 'ringing - answering in $Delay s' then 'answered via Telecom'." -ForegroundColor Gray
}
