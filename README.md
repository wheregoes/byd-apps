# BYD Apps

> **Disclaimer:** This is an unofficial, community-driven project with no affiliation,
> endorsement, or sponsorship from BYD or any of its subsidiaries. It involves reverse
> engineering of BYD's internal Android services for educational and interoperability
> purposes only. Use at your own risk — modifying vehicle software may void your warranty
> or violate BYD's terms of service. The authors assume no liability for any damage to
> your vehicle, software, or data.

Open-source Android apps for BYD electric vehicles.

Built on reverse-engineered BYD APIs — these apps interact directly with the vehicle's CAN bus through BYD's internal Android services. Not available on Google Play.

## Apps

| App | Description | Status |
|-----|-------------|--------|
| [Door Sound](apps/door-sound/) | Custom interior sounds on door, hood, trunk, lock/unlock, alarm and window events; AVAS tone patterns on the exterior speaker | Ready |
| [Engine Sound](apps/engine-sound/) | Select AVAS engine sound presets on the exterior speaker (BYD's Boombox); the preset range is probed from your vehicle at runtime | Ready |
| [Cabin](apps/pet-mode/) | Keep pets safe — AC monitoring, temperature display, always-on screen, plus a high-contrast Outside view readable through tinted glass | Ready |
| [BYD Probe](apps/byd-probe/) | Diagnostic tool — enumerates all BYD Auto API methods via reflection | Dev tool |

Quick start: copy APK to `Third Party Apps 55/` on USB → plug in → password `BYD6125F`.

## Compatibility

| Property | Value |
|----------|-------|
| Vehicles | BYD Dolphin 25/26 (likely works on other DiLink 3 models) |
| Known incompatible | **DiLink 5** — see [#3](https://github.com/wheregoes/byd-apps/issues/3) |
| Platform | DiLink 3.0 (global version) |
| Android | 10 (API 29) |
| Architecture | ARM64 (Qualcomm QCM6125) |
| Tested Firmware | 13.1.32.2507250.1 (Jul 25 2025) |

> **Tested on firmware 13.1.32.2507250.1.** Older versions likely work. Newer firmware updates from BYD may change or break things — no guarantees.

## Installing Apps on Your BYD

No root required. Two methods:

### USB Drive (Easiest)

1. Create a folder named `Third Party Apps 55` on a USB drive
2. Copy the APK into that folder
3. Plug USB into the car
4. Enter password: `BYD6125F`
5. Tap the APK to install

### ADB over WiFi

Requires enabling USB debugging first — see the full [Sideloading Guide](https://github.com/wheregoes/byd-dolphin-hacking/blob/master/docs/sideloading-guide.md).

```bash
adb connect 192.168.10.10:5555
adb install build/door-sound.apk
```

## Exterior audio: what is and is not possible

A recurring question ([#4](https://github.com/wheregoes/byd-apps/issues/4)): **a custom audio file
cannot be routed to the exterior AVAS speaker on this platform.** If you lock the car and walk away,
your own audio file will never be what you hear from outside.

Measured on a Dolphin:

- the AVAS custom-source feature ids `0x35201036` and `0x35201040` both return `-10011`;
- `0x1C10000E`, `0x32B1C042` and `0x1B100043` are not accepted at all;
- the AVAS sounds live in MCU flash and are not reachable from Android;
- `setBuffer` caps at 128 bytes, roughly 8 ms of 8 kHz audio.

So there are exactly two audio routes:

| Route | What can play | Used by |
|---|---|---|
| Interior speakers | any audio file you choose | Door Sound, "Inside speaker" tab |
| Exterior AVAS speaker | the vehicle's own two tones (`0xAA000104`) and its built-in engine presets | Door Sound "Outside speaker" tab, Engine Sound |

Note also that a successful `setInt` proves nothing on this platform — it returns success even for
values the MCU ignores. Every write in these apps is read back and verified.

## Building from Source

```bash
# one-time: fetch android.jar for API 29
mkdir -p "$HOME/.local/share/android/platform-29"
curl -fsSL -o /tmp/platform-29.zip \
  https://dl.google.com/android/repository/platform-29_r05.zip
unzip -oj /tmp/platform-29.zip android-10/android.jar \
  -d "$HOME/.local/share/android/platform-29"

# then, per app
cd apps/door-sound && ./build.sh
```

All four apps share `apps/common/build-common.sh`, the BYD API stubs in `apps/common/stubs/` and
`apps/common/libs/byd_sdk.dex`. Each app's `build.sh` only sets its output name and package.

Requirements:

- JDK 11+
- Android SDK build-tools (`aapt2`, `d8`, `apksigner`, `zipalign`)
- `android.jar` for API 29 (the command above, or set `ANDROID_JAR`)

Signing uses one repo-wide keystore at `keystore/byd-apps.keystore`, generated on first build and
gitignored. It lives outside `build/` on purpose: when the key was regenerated per build, every
reinstall was a signature mismatch and in-place upgrades were impossible.

> **Toolchain limit:** `d8` 8.2.2 cannot dex Java enums or anonymous inner classes — both fail with
> `NullPointerException: Cannot invoke "String.length()"`. Use `this::method` references and final
> classes of static constants instead. Details in `apps/common/build-common.sh`.

## Research

Detailed API reference in [`research/byd-auto-api-reference.md`](research/byd-auto-api-reference.md) — covers AC, door lock, panorama, bodywork, and cloud communication APIs with live-verified values.

These apps are based on findings from the [byd-dolphin-hacking](https://github.com/wheregoes/byd-dolphin-hacking) research repo — CAN bus protocol documentation, BYD API reverse engineering, and audio architecture analysis.

## Contributing

Every modification, finding, discovery, or change made to the BYD Dolphin must be committed and pushed. See [CLAUDE.md](CLAUDE.md).

## Troubleshooting

- App killed after minutes? → Settings → Apps → Auto-start Management → enable auto-start for the app.
- Temperature shows "--"? → either the AC signals are unavailable on your firmware, or the climate
  feed went stale: Cabin withdraws readings older than 120 s rather than leaving a stale number on
  screen, and shows "Climate signal lost" when it does.
- Door Sound logs the event but plays nothing? → playback needs the master switch **and** that
  event's own switch **and** a selected file. Each card states which one is missing. Note that
  Preview deliberately bypasses the switches.
- Engine Sound offers fewer presets than expected? → the count is per model and probed from your
  car. A Dolphin reports 10, a Song Pro 2. Use "Re-scan presets" after a firmware update.
- New firmware broke things? → Report in issues; no guarantees on updates.

## License

MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
