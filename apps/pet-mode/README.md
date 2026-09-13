# Cabin for BYD

Keep your pets safe in the car. Inspired by Tesla's Pet Mode — designed for BYD vehicles.

Full-screen display shows cabin temperature and a reassuring message to passersby, while monitoring vehicle state and keeping the screen always on at maximum brightness.

## Features

- Large temperature display, plus an **Outside view** built to be read through dark tint film
- Customizable pet name and avatar (dog, cat, or paw print)
- Door state monitoring (locked/unlocked alerts)
- AC status display
- Always-on screen with maximum brightness
- Persistent service — survives infotainment auto-close
- Auto-start on vehicle boot
- Dark mode option
- Climate readings withdrawn when the feed goes stale, rather than showing a stale number
- 12V battery low warning
- 6 languages: English, Português (BR), Português (PT), Español, Français, 中文
- °C and °F support with locale auto-detection

## Outside view (beacon mode)

The pretty interior view is a glass-and-gradient design: lovely from the driver's seat, and close to
unreadable from the pavement through dark tint. Outside view is the same data at maximum legibility.

| | Interior view | Outside view |
|---|---|---|
| Background | aurora gradient, drifting orbs, floating paws | flat `#000000`, nothing else |
| Type | `sans-serif-light` / `-thin` | `sans-serif-black` |
| Contrast | roughly 1.4:1 for the secondary text | 21:1 |
| Temperature | ~320sp | autosized, about 44% of screen height |
| Status | AC, doors, battery, active timer | AC, doors, exterior temperature only |

Screen brightness is already pinned to maximum in both views, so the remaining levers are contrast,
stroke weight, glyph size, and getting the decoration out from behind the text.

Set it under Settings → Display:

- **Auto** (default) — Outside view whenever the car is locked, interior view when it is unlocked
- **Outside view** — always on
- **Interior** — never

Touching the screen returns to the interior view for 60 seconds. When the AC is off, or the 12V
battery is low, the background turns red and the status line pulses; the temperature and the message
never pulse, so they stay readable.

## Installation

### USB Drive (Easiest)

1. Create a folder named `Third Party Apps 55` on a USB drive
2. Copy `pet-mode.apk` into that folder
3. Plug USB into the car
4. Enter password: `BYD6125F`
5. Tap the APK to install

### ADB over WiFi

```bash
adb connect 192.168.10.10:5555
adb install build/pet-mode.apk
```

## First-Time Setup (Important!)

After installing, you **must** whitelist the app to prevent the infotainment system from killing it:

1. Go to **Settings** on the head unit
2. Open **Apps** > **Auto-start Management**
3. Find **Cabin** and **enable** auto-start

Without this step, the infotainment system will close the app after a few minutes.

## Usage

1. Open Cabin from the app drawer
2. The app immediately enters full-screen mode with maximum brightness
3. Tap the gear icon (top-left) to configure:
   - Pet name
   - Avatar (dog, cat, or paw)
   - Temperature unit (°C/°F)
   - Dark/light mode
4. Press back to exit (with confirmation dialog)

## Temperature & AC

The app attempts to read cabin temperature from the BYD CAN bus using the BYDAUTO AC API. If AC signals are not available on your firmware version, the temperature will show as "--".

AC signal discovery is automatic — the app probes known device types and feature IDs on first launch and caches any found signals.

## Compatibility

| Property | Value |
|----------|-------|
| Vehicles | BYD Dolphin 25/26 (likely works on other DiLink 3 models) |
| Platform | DiLink 3.0 (global version) |
| Android | 10 (API 29) |
| Architecture | ARM64 (Qualcomm QCM6125) |
| Screen | 1920x1080 at 240dpi (= 1280x720dp), landscape |

## Building from Source

Requirements:
- JDK 11+
- Android SDK build-tools (`aapt2`, `d8`, `apksigner`)
- `android.jar` for API 29 at `$HOME/.local/share/android/platform-29/android.jar` (or set `ANDROID_JAR`)

The shared pipeline lives in [`../common/build-common.sh`](../common/build-common.sh); see the repo
[README](../../README.md#building-from-source) for the one-time download command.

```bash
chmod +x build.sh
./build.sh
```

Output: `build/pet-mode.apk`

## License

MIT
