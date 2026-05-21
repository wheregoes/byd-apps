# Cabin for BYD

Keep your pets safe in the car. Inspired by Tesla's Pet Mode — designed for BYD vehicles.

Full-screen display shows cabin temperature and a reassuring message to passersby, while monitoring vehicle state and keeping the screen always on at maximum brightness.

## Features

- Large temperature display visible through tinted windows (light mode default)
- Customizable pet name and avatar (dog, cat, or paw print)
- Door state monitoring (locked/unlocked alerts)
- AC status display
- Always-on screen with maximum brightness
- Persistent service — survives infotainment auto-close
- Auto-start on vehicle boot
- Dark mode option
- 6 languages: English, Português (BR), Português (PT), Español, Français, 中文
- °C and °F support with locale auto-detection

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
| Screen | 1920x720 widescreen |

## Building from Source

Requirements:
- JDK 11+
- Android SDK build-tools (`aapt2`, `d8`, `apksigner`)
- `android.jar` for API 29 at `/tmp/android-10/android.jar`

```bash
chmod +x build.sh
./build.sh
```

Output: `build/pet-mode.apk`

## License

MIT
