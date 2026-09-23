# Auto Answer for Meta Portal

Answers incoming calls by itself, so a Portal can be used by someone who can't reliably reach the
Answer button — someone living with dementia, or anyone for whom a ringing screen isn't something
they can act on in time.

Family ring the Portal, it rings for a few seconds, then picks up. The Portal's own call screen
takes over from there.

It is standalone: no Home Assistant, no launcher, no companion app, no account, no network
service. One small foreground service and a setup screen.

## Setting it up

### From a computer (recommended)

```powershell
.\provision.ps1
```

Plug the Portal in by USB, accept the debugging prompt on its screen, and run that. It installs
the app, grants the one permission it needs, switches auto answer on and checks it's running.

Useful switches:

| Switch | What it does |
| --- | --- |
| `-Serial <id>` | Pick a device when more than one is attached |
| `-Delay <0-15>` | Seconds to ring before answering (default 3) |
| `-Off` | Install and set up, but leave auto answer switched off |
| `-Accessibility` | Also enable the fallback answerer (see below) |

### From the browser

The app is packaged for [OpenPortal](https://openportal.cc), which manages a Portal over WebUSB
from a Chromium browser — no drivers, no adb install. The catalog entry in `openportal/app.json`
grants the permission automatically on install; auto answer is then switched on in the app.

## How it decides to answer

- **Noticing the call:** a ringing call plays a ringtone, which shows up in Android's audio
  playback state. That needs no special role and no access to notifications.
- **Answering:** `TelecomManager.acceptRingingCall()`. Meta's calling app registers with Android's
  Telecom framework, so the system will accept the call on our behalf — no screen-tapping, and
  nothing that breaks when Meta changes a button. **Proven on a real Portal, Messenger calls.**
- **Fallback:** an accessibility service that finds and clicks Answer, for if Telecom ever
  refuses. Off by default, since enabling it touches a device-wide setting, and so far it hasn't
  been needed. It only ever clicks Answer — it explicitly refuses to press decline, reject, end
  or hang up.

## Things that were decided deliberately

- **It draws nothing on the screen.** After answering, Meta's call UI is in charge, including its
  hang-up button. The person can always end a call themselves. Don't add an overlay to this app
  without rethinking that.
- **It rings first.** A few seconds gives them a chance to answer it themselves, and it's less
  abrupt for the caller. Set `-Delay 0` for an immediate pickup.
- **It starts switched off.** Nothing is answered until someone deliberately turns it on.
- **Every call is answered.** On a Portal that's narrower than it sounds: calls arrive through the
  device's Messenger/WhatsApp account, not an open phone line.

## Worth knowing before you rely on it

A Portal auto-answering calls is, in effect, a camera and microphone that strangers-to-the-moment
can open. Inbound Portal calls are video by default. That's the point of the feature, but the
household should know it's on, and whoever sets it up should be confident the account can only be
called by people who should be able to.

Portal is discontinued hardware. Calls work because Messenger and WhatsApp still support these
devices, which is Meta's decision and could change without notice. Good as a way for family to
look in on someone; not something to be the only way anyone can reach them.

## Building

No gradle wrapper — uses the cached Gradle 8.6, same as the other Portal projects here:

```powershell
$env:JAVA_HOME='F:\Android Studio\jbr'
C:\Users\*****\.gradle\wrapper\dists\gradle-8.6-bin\afr5mpiioh2wthjmwnkmdsd5w\gradle-8.6\bin\gradle.bat assembleDebug
```

`local.properties` needs `sdk.dir`; copy the one from `portal-ha-bridge` (the path escaping is
fussy). Minimum Android 9 (API 28), so it covers Portal+ Gen1 through the newer Portals.

## Checking it works

```
adb logcat -s AutoAnswer:*
```

A call should produce:

```
ringing - answering in 3s
answered via Telecom
ringing stopped
```
