# Camping for BYD

V2L camping dashboard for BYD Dolphin. Monitor discharge rate, battery life, door security, and control AC and windows — all from a full-screen dark-mode interface designed for overnight camping with V2L.

## Features

- Real-time V2L discharge rate (watts) with remaining time estimate
- Battery level monitoring with configurable low-battery alarm
- Door security alarm — audible alert when doors open while armed
- AC quick controls (on/off, temperature adjustment)
- Window controls (open/close all)
- Wake-up alarm clock with large clock display
- Session logging with energy usage summary and cost tracking
- Night mode with adjustable screen brightness
- Persistent foreground service — survives infotainment auto-close
- Auto-start on vehicle boot
- English and Português (BR)

## Installation

### USB Drive (Easiest)

1. Create a folder named `Third Party Apps 55` on a USB drive
2. Copy `camping.apk` into that folder
3. Plug USB into the car
4. Enter password: `BYD6125F`
5. Tap the APK to install

### ADB over WiFi

```bash
adb connect 192.168.10.10:5555
adb install build/camping.apk
```

## First-Time Setup (Important!)

After installing, you **must** whitelist the app to prevent the infotainment system from killing it:

1. Go to **Settings** on the head unit
2. Open **Apps** > **Auto-start Management**
3. Find **Camping** and **enable** auto-start

Without this step, the infotainment system will close the app after a few minutes.

## Usage

1. Open Camping from the app drawer
2. The app enters full-screen dark mode with V2L monitoring
3. Discharge rate stabilizes after ~2 minutes of SOC sampling
4. Tap control buttons to toggle AC, adjust temperature, arm door alarm
5. Use settings (gear icon) to configure battery alarm threshold, energy cost, brightness
6. Press "Stop" to end camping session and see summary

## V2L Discharge Monitoring

The app calculates discharge rate by sampling battery SOC every 30 seconds and computing the energy flow rate. The remaining time estimate accounts for your configured low-battery alarm threshold.

Battery capacity: 44.9 kWh (BYD Dolphin standard). A direct V2L power API via CAN bus is planned for more accurate readings.

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

Output: `build/camping.apk`

## License

MIT
