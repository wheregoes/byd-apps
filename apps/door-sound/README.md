# Door Sound

Plays custom audio files through cabin speakers and preset tone patterns through the AVAS external speaker when door/lock events occur.

## Features

- 8 events: door open, door close, lock, unlock, hood, trunk, alarm, window left open
- **Inside speaker**: custom audio files (OGG/MP3/WAV) with per-event volume control (0-15)
- **Outside speaker (AVAS)**: 8 preset tone patterns:
  - Ding-Dong — pitch A then B (classic doorbell)
  - Dong-Ding — pitch B then A
  - Triple Beep — three separate beeps
  - Rapid Alternation — A/B/A/B/A/B
  - Long Chime — B then A, longer
  - Shop Chime — A-A-B-B with rests (entrance chime)
  - Alarm — wee-woo × 4 (siren)
  - Fanfare — A-A-A-B-B (cavalry charge)
- Auto-start on boot, plus a **BYD auto-start** button that opens BYD's own screen (see below)
- Foreground service for reliable background operation
- **Watch parked**: how long the 10-second poll and the wake lock stay alive after the car is
  switched off — 10 min (default), 30, 60 or always
- **Diagnostics** reports deep-sleep gaps, so "the car was off" stops being guesswork

## When a sound will and will not play

Three things must all be true, and each card tells you which one is missing:

1. the **Enabled** master switch is on;
2. that event's own switch is on (choosing a file or a pattern turns it on for you);
3. a file is selected (Inside) or a pattern is chosen (Outside).

The event log at the top updates on **every** vehicle event regardless of those settings, because it
is a diagnostic. **Preview** also deliberately bypasses them, so you can audition a pattern without
arming it. Between them those two behaviours used to make a completely unconfigured app look like a
working one ([#5](https://github.com/wheregoes/byd-apps/issues/5)).

Interior sounds play with `USAGE_ASSISTANCE_SONIFICATION` at a per-event gain, so they never change
the radio's volume.

### Event notes

- **Lock / unlock** fire on a transition only. BYD reports `NORMAL=0`, `SET_SECURE=1`,
  `START_SECURE=2`, so a single lock can produce two state changes; the app collapses them.
- **Window left open** fires when a window is open and the car is locked — opening a window while
  driving is deliberate, leaving one open is not.
- **Hood** and **trunk** fire on opening only.

## With the car switched off

Switching the car off does **not** reboot the head unit — measured on a DiLink 3 unit that reached
72 days of uptime across many drives. What happens instead is that the SoC suspends: the service is
still there, but nothing of it runs while the car sleeps.

That is what the two controls in the tab row are for.

**Watch parked** sets how long after a power change the service keeps a timed `PARTIAL_WAKE_LOCK`
and keeps re-reading the lock state every 10 seconds. While that window is open, a lock from the
key fob produces a sound even on a car that never pushes the event. When it closes, the log says
`poll: watch window closed, pushed events only`, the lock is released and the unit is free to
suspend. The default is 10 minutes because an always-open window keeps the SoC awake and draws from
the 12 V battery; `always` is there for people who want a lock sound hours after parking and accept
that cost.

**Diagnostics** proves which of the two happened. `elapsedRealtime()` counts suspended time and
`uptimeMillis()` does not, so a divergence across one poll interval is a suspend and nothing else:

```
park watch: 10 min after a power change
watch: open, 7m12s left
deep sleep: 3 gaps, longest 41m08s
```

`deep sleep: none observed` with a closed window and a stale `last poll` is a frozen service;
`service: STOPPED` is a killed one. Those are different problems with different fixes, and this is
how to tell them apart from a photo.

## BYD's auto-start screen reads backwards

`com.byd.appstartmanagement` (Settings → Apps → Auto-start Management) is a **block** list:

- a row switched **ON** means auto-start is **disabled** for that app;
- a row switched **OFF** means it is allowed.

Measured on a DiLink 3 head unit: with the row ON, a background app never received
`BOOT_COMPLETED` at all — the receiver was registered at priority 9900, the package was not in the
stopped state, nothing crashed, the broadcast simply never arrived. Turning the row OFF is what
makes the boot path work.

The list is keyed by the **APK directory**, which changes on every install and every in-place
upgrade, so an app that was allowed goes back to blocked after an update. The app therefore marks
its **BYD auto-start** pill in amber with `— check` until you have opened that screen once for the
version you are running, and Diagnostics prints
`autostart screen: present, NOT yet checked for this install`.

## AVAS Pattern Mechanism

The AVAS external speaker supports only **2 pitches** (confirmed by live testing):
- `TEST_AUDIO_AVAS_SET` (0xAA000104): 1 = pitch A (lower), 2 = pitch B (higher), 0 = silence
- `AVAH` (0x6E970010): 1 = tone on, 0 = tone off
- Pitch changes mid-tone by setting TEST_AVAS while AVAH stays on
- Rests between notes via TEST_AVAS = 0
- Separate beeps require full disable/re-enable of all 6 enabler commands

## Build Prerequisites

| Tool | Source |
|------|--------|
| `android.jar` (API 29) | Android SDK or `sdkmanager "platforms;android-29"` |
| `aapt2` | Android SDK build-tools |
| `javac` | JDK 11+ |
| `d8` | Android SDK build-tools |
| `apksigner` | Android SDK build-tools |
| `keytool` | JDK |

## Build

```bash
./build.sh
```

`android.jar` is expected at `$HOME/.local/share/android/platform-29/android.jar`, or wherever
`ANDROID_JAR` points. The shared pipeline lives in [`../common/build-common.sh`](../common/build-common.sh);
see the repo [README](../../README.md#building-from-source) for the one-time download command.

## Install

1. Connect via ADB: `adb connect <head-unit-ip>:5555`
2. Install: `adb install build/door-sound.apk`
3. Open app, select audio files, enable events
4. Tap **BYD auto-start** in the app and switch the Door Sound row **OFF** in BYD's list, which is
   what allows auto-start (see above) — redo this after every upgrade

## How It Works

- Listens for `BYDAutoBodyworkDevice` events via CAN bus (door open/close, remote lock/unlock)
- **Inside sounds**: `MediaPlayer` on `STREAM_MUSIC` plays user-selected audio files through cabin speakers
- **Outside sounds**: CAN bus commands to AVAS (factory test signals repurposed as tone patterns)
- Uses `BydPermissionContext` to handle BYD-specific permission checks

## Limitations

- Cannot replace the BCM-generated lock/unlock chirp (hardware limitation — BCM generates the sound directly)
- AVAS external speaker supports only 2 pitches (TEST_AVAS=1 and 2), no custom audio upload
- AVAS volume is fixed by MCU firmware (PROMPT_VOLUME_LEVEL doesn't affect it — verified)
- Sounds for an event that happens while the car sleeps depend on **Watch parked** (above): outside
  that window only a pushed CAN callback can still reach the app

## Architecture

The app compiles against stub interfaces (`stubs/`) that match BYD's internal HAL (`android.hardware.bydauto`). These stubs define the API surface — method signatures, constants, and listener interfaces — without any implementation. At runtime on the vehicle's head unit, the stubs are not loaded; the real system services provide the actual implementations. This allows building the app on any development machine without access to BYD's proprietary framework JARs.
