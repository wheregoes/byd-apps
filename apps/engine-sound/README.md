# Engine Sound Selector

Select engine sound presets on the BYD Dolphin's external AVAS (pedestrian warning) speaker. BYD's equivalent of Tesla Boombox.

## Features

- **Presets discovered from your vehicle at runtime** — the count is per model, so it is never hardcoded
- Scrollable preset chips: reach preset 10 in one tap instead of ten
- Long-press a preset to give it a name you will recognise
- Large, touch-friendly UI designed for in-car use
- PREV / NEXT buttons, coalesced so rapid taps produce one CAN write
- CYCLE ALL mode auto-sweeps through every preset (2.5s each)
- Every write is read back; a preset the vehicle rejects is reported, not silently accepted

## How It Works

The BYD Dolphin has an Engine Voice Simulator built into the MCU firmware. This app controls it via the BYD auto HAL:

- `ENGINE_VOICE_SIMULATOR_STATE_SET (0x3E300020)` — Turn simulator ON/OFF
- `ENGINE_SIMULATOR_SOURCE_TYPE_SET (0x3E300038)` — Select sound preset
- `ENGINE_HAS_SIMULATOR (0x48F00000)` — Check support (value 2 = supported)

### How many presets?

There is no count register to read: `0x48F00013` returns `1`, meaning "a voice source exists", not
"one source exists". So the app probes `0x3E300038` upward, verifying the readback each time, and
stops at the first value the vehicle does not confirm. The result is cached in
`engine_sound_prefs/max_src_type`; "Re-scan presets" clears it.

Measured so far: **10 on a Dolphin**, **2 on a Song Pro** ([#5](https://github.com/wheregoes/byd-apps/issues/5)).
Source type 0 is always rejected.

The earlier "30+ presets" claim in this README was never verified and was wrong; values above the
vehicle's real maximum are silently clamped by the MCU while `setInt` still returns success.

## Install

```bash
adb install build/engine-sound.apk
```

## Usage

1. Open "Engine Sound" app
2. Toggle **ENGINE SOUND: ON**
3. Drive at low speed (<30 km/h) — AVAS only plays at low speeds
4. Press **NEXT >** or **< PREV** to try different sounds
5. Or press **CYCLE ALL SOUNDS** to auto-sweep through every preset

## Technical Details

The app uses `BydPermissionContext` to bypass BYD permission checks, then calls `getSystemService("auto")` to access the BYD auto HAL. The HAL communicates with the MCU via CAN bus.

### Feature IDs

| Feature ID | Name | Access | Values |
|-----------|------|--------|--------|
| `0x48F00000` | HAS_SIMULATOR | Read | 2 = supported |
| `0x48F00013` | HAS_VOICE_SOURCE | Read | 1 = yes |
| `0x48F0000A` | SIMULATOR_STATE | Read | 0=off, 1=on |
| `0x48F00010` | SOURCE_TYPE | Read | 1..max (10 on Dolphin) |
| `0x3E300020` | STATE_SET | Write | 0=off, 1=on |
| `0x3E300038` | SOURCE_TYPE_SET | Write | 1..max; 0 rejected |

### AVAS Enable Sequence

When toggling ON, the app sends these commands to fully enable the AVAS path:
- `0xAA000148` (PA_CONTROL) = 1
- `0xAA000142` (MCU_SPEAK) = 1
- `0xAA00011A` (FM_SPEAK) = 1
- `0xAA000171` (AVAS_CONFIG) = 1
- `0x3E300020` (STATE_SET) = 1
- `0x3E300038` (SOURCE_TYPE_SET) = current type

## Limitations

- AVAS only plays at low speeds (typically <30 km/h) for pedestrian warning
- The simulator may reset to OFF when the car is turned off
- Presets above the vehicle's maximum are ignored by the MCU even though `setInt` reports success — the app detects this by reading the value back
- App must be re-enabled after each car restart (MCU resets state)
