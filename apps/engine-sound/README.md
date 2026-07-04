# Engine Sound Selector

Select between 30+ engine sound presets for the BYD Dolphin's external AVAS (pedestrian warning) speaker. BYD's equivalent of Tesla Boombox.

## Features

- **30+ engine sound presets** selectable via MCU engine voice simulator
- Large, touch-friendly UI designed for in-car use
- PREV / NEXT buttons to cycle sounds manually
- CYCLE ALL mode auto-sweeps through every sound (2.5s each)
- One-tap enable/disable toggle

## How It Works

The BYD Dolphin has an Engine Voice Simulator built into the MCU firmware. This app controls it via the BYD auto HAL:

- `ENGINE_VOICE_SIMULATOR_STATE_SET (0x3E300020)` — Turn simulator ON/OFF
- `ENGINE_SIMULATOR_SOURCE_TYPE_SET (0x3E300038)` — Select sound preset
- `ENGINE_HAS_SIMULATOR (0x48F00000)` — Check support (value 2 = supported)

The Dolphin accepts source types 1-30+ (only type 0 is rejected). Each type corresponds to a different engine sound stored in MCU flash.

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
| `0x48F00010` | SOURCE_TYPE | Read | 1-30+ |
| `0x3E300020` | STATE_SET | Write | 0=off, 1=on |
| `0x3E300038` | SOURCE_TYPE_SET | Write | 1-30+ |

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
- Source types 1-30 all accepted by MCU, but some may be duplicates
- App must be re-enabled after each car restart (MCU resets state)
