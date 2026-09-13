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
- Auto-start on boot
- Foreground service for reliable background operation

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
4. Whitelist in BYD auto-start manager for persistent background operation

## How It Works

- Listens for `BYDAutoBodyworkDevice` events via CAN bus (door open/close, remote lock/unlock)
- **Inside sounds**: `MediaPlayer` on `STREAM_MUSIC` plays user-selected audio files through cabin speakers
- **Outside sounds**: CAN bus commands to AVAS (factory test signals repurposed as tone patterns)
- Uses `BydPermissionContext` to handle BYD-specific permission checks

## Limitations

- Cannot replace the BCM-generated lock/unlock chirp (hardware limitation — BCM generates the sound directly)
- AVAS external speaker supports only 2 pitches (TEST_AVAS=1 and 2), no custom audio upload
- AVAS volume is fixed by MCU firmware (PROMPT_VOLUME_LEVEL doesn't affect it — verified)
- Requires rooted head unit for sideloading

## Architecture

The app compiles against stub interfaces (`stubs/`) that match BYD's internal HAL (`android.hardware.bydauto`). These stubs define the API surface — method signatures, constants, and listener interfaces — without any implementation. At runtime on the vehicle's head unit, the stubs are not loaded; the real system services provide the actual implementations. This allows building the app on any development machine without access to BYD's proprietary framework JARs.
