# BYD Auto Framework API Reference

Reverse-engineered from `framework.jar` (boot classpath), `AirConditioning.apk`, and live probing on BYD Dolphin 2024 (DiLink 3.0, Android 10).

## Architecture

```
[Your App] → BydPermissionContext → BYDAutoXxxDevice.getInstance(ctx)
                                          ↓
                                    [framework.jar boot classpath]
                                          ↓
                                    [CAN Bus / MCU via IPC]
                                          ↓
                                    [Vehicle ECU]
```

All BYD Auto device classes live in `android.hardware.bydauto.*` namespace. They are singletons obtained via `getInstance(Context)`. The Context is used for permission enforcement — `BydPermissionContext` overrides `enforceCallingOrSelfPermission()` to bypass signature-level permission checks.

## Permission System

BYD defines three permission tiers per device:
- `COMMON` — runtime permission, grantable via `pm grant`. Required for `getInstance()`.
- `GET` — signature-level (`prot=signature`). Required for read methods. Bypassed via `BydPermissionContext`.
- `SET` — signature-level. Required for write methods. Also bypassed via `BydPermissionContext`.

Source package: `com.byd.auto.permission`

### Known Permissions (from live probing)
| Permission | Type | Grantable |
|---|---|---|
| `android.permission.BYDAUTO_AC_COMMON` | runtime | yes |
| `android.permission.BYDAUTO_AC_GET` | signature | bypass only |
| `android.permission.BYDAUTO_AC_SET` | signature | bypass only |
| `android.permission.BYDAUTO_DOOR_LOCK_COMMON` | runtime | yes |
| `android.permission.BYDAUTO_DOOR_LOCK_GET` | signature | bypass only |
| `android.permission.BYDAUTO_DOOR_LOCK_SET` | signature | bypass only |
| `android.permission.BYDAUTO_PANORAMA_COMMON` | runtime | yes |
| `android.permission.BYDAUTO_PANORAMA_GET` | signature | server-side enforced |
| `android.permission.BYDAUTO_BODYWORK_COMMON` | runtime | yes |
| `android.permission.BYDAUTO_BODYWORK_GET` | signature | bypass only |

Note: Panorama GET is enforced server-side (in the IPC service), not client-side. BydPermissionContext bypass does NOT work for panorama.

---

## BYDAutoAcDevice

**Package:** `android.hardware.bydauto.ac`
**Singleton:** `BYDAutoAcDevice.getInstance(context)`

### Constants (verified on car)

| Constant | Value | Meaning |
|---|---|---|
| AC_POWER_ON | 1 | AC is on |
| AC_POWER_OFF | 0 | AC is off |
| AC_CTRLMODE_AUTO | 0 | Automatic mode |
| AC_CTRLMODE_MANUAL | 1 | Manual mode |
| AC_CYCLEMODE_OUTLOOP | 1 | Outside air |
| AC_CYCLEMODE_INLOOP | 0 | Recirculate |
| AC_WINDLEVEL_0..7 | 0-7 | Fan speed |
| AC_WINDMODE_FACE | 1 | Face vents |
| AC_WINDMODE_FOOT | 5 | Foot vents |
| AC_WINDMODE_DEFROST | 0 | Defrost |
| AC_TEMP_IN_CELSIUS_MIN | 17 | 17°C minimum |
| AC_TEMP_IN_CELSIUS_MAX | 33 | 33°C maximum |
| AC_TEMP_IN_CELSIUS_HALF_MIN | 34 | 17°C in half-degree (×2) |
| AC_TEMP_IN_CELSIUS_HALF_MAX | 66 | 33°C in half-degree (×2) |
| AC_TEMPERATURE_MAIN | zone 1 | Driver/main temp |
| AC_TEMPERATURE_DEPUTY | zone 2 | Passenger temp |
| AC_TEMPERATURE_REAR | zone 3 | Rear temp |
| AC_TEMPERATURE_OUT | zone 4 | Outside temp |
| AC_CTRL_SOURCE_UI_KEY | 0 | Touch screen source |
| AC_CTRL_SOURCE_VOICE | 1 | Voice command source |
| AC_REMOTE_CTRL_TIME_10..30 | 1-5 | Remote control durations (10/15/20/25/30 min) |
| AC_COMMAND_SUCCESS | | Command OK |
| AC_COMMAND_FAILED | | Command failed |
| AC_COMMAND_BUSY | | CAN bus busy |
| AC_COMMAND_TIMEOUT | | Command timeout |

### GET Methods (all verified working)

| Method | Returns | Live Value |
|---|---|---|
| `getAcStartState()` | int | 1 (ON) |
| `getAcOnlineState()` | int | 1 (online) |
| `getAcControlMode()` | int | 0 (AUTO) |
| `getAcType()` | int | 1 (ELECTRIC) |
| `getAcWindLevel()` | int | 1 |
| `getAcWindMode()` | int | 1 (FACE) |
| `getAcCycleMode()` | int | 1 (OUTLOOP) |
| `getAcMaxCoolingState()` | int | 0 (off) |
| `getAcCompressorMode()` | int | 1 (on) |
| `getAcVentilationState()` | int | 0 |
| `getAcWarmState()` | int | 65535 (N/A) |
| `getAcRemoteCtrlTime()` | int | 5 (30 min) |
| `getTemperatureUnit()` | int | 1 (Celsius) |
| `getTemprature(zone)` | int | zone1=26, zone2=26, zone4=29 |
| `getAcTemperatureControlMode()` | int | 3 (RANGE_SINGLE) |
| `hasFeature("ACRemoteControl")` | int | 1 (SUPPORTED!) |
| `hasFeature("ACAutoMode")` | int | 1 (supported) |
| `hasFeature("ACDefrost")` | int | -10011 (not available) |

### SET Methods (available, NOT yet tested)

| Method | Signature | Purpose |
|---|---|---|
| `start(source)` | `(int) → int` | Turn AC on. source=0 (UI), 1 (voice) |
| `stop(source)` | `(int) → int` | Turn AC off |
| `setAcTemperature(zone, temp, source, ?)` | `(int,int,int,int) → int` | Set temperature |
| `setAcWindLevel(level, source)` | `(int,int) → int` | Set fan speed 0-7 |
| `setAcWindMode(mode, source)` | `(int,int) → int` | Set vent direction |
| `setAcControlMode(mode, source)` | `(int,int) → int` | Auto/manual |
| `setAcCycleMode(mode, source)` | `(int,int) → int` | Recirculate/outside |
| `setAcMaxCoolingState(state)` | `(int) → int` | Max cooling on/off |
| `setAcCompressorMode(state, source)` | `(int,int) → int` | Compressor on/off |
| `setAcDefrostState(area, state, source)` | `(int,int,int) → int` | Defrost on/off |
| `setAcVentilationState(state, source)` | `(int,int) → int` | Ventilation on/off |
| `setAcRemoteCtrlTime(time)` | `(int) → int` | Remote control duration |
| `setAcTemperatureControlMode(mode, source)` | `(int,int) → int` | Temp control mode |
| `setAutoCleanAirState(state)` | `(int) → int` | Auto air cleaning |
| `setQuickCleanAirState(state)` | `(int) → int` | Quick air cleaning |
| `setFragrance(name, intensity)` | `(String,int) → int` | Fragrance system |
| `startRearAc(source)` | `(int) → int` | Rear AC on |
| `stopRearAc(source)` | `(int) → int` | Rear AC off |

### Low-level SET (used by BYD's own AC app)
```java
// Generic set via feature ID array + event value
acDevice.set(int[] featureIds, BYDAutoEventValue value) → int
```
BYD's `com.byd.airconditioning` APK uses this generic `set()` instead of named setters.

### Listener
```java
acDevice.registerListener(new AbsBYDAutoAcListener() {
    void onAcStartStateChanged(int state) { ... }
    void onAcTemperatureChanged(int zone, int temp) { ... }
    void onAcWindLevelChanged(int level) { ... }
    // etc
});
```

---

## BYDAutoDoorLockDevice

**Package:** `android.hardware.bydauto.doorlock`
**Singleton:** `BYDAutoDoorLockDevice.getInstance(context)`

### Constants

| Constant | Value |
|---|---|
| DOOR_LOCK_AREA_LEFT_FRONT | 1 |
| DOOR_LOCK_AREA_LEFT_REAR | 2 |
| DOOR_LOCK_AREA_RIGHT_FRONT | 3 |
| DOOR_LOCK_AREA_RIGHT_REAR | 4 |
| DOOR_LOCK_AREA_BACK | 5 |
| DOOR_LOCK_AREA_CHILDLOCK_LEFT | 6 |
| DOOR_LOCK_AREA_CHILDLOCK_RIGHT | 7 |
| DOOR_LOCK_STATE_INVALID | 0 |
| DOOR_LOCK_STATE_UNLOCK | 1 |
| DOOR_LOCK_STATE_LOCK | 2 |

### Methods

| Method | Live Result |
|---|---|
| `getDoorLockStatus(AREA_LEFT_FRONT)` | 0 (INVALID) |
| `getDoorLockStatus(AREA_RIGHT_FRONT)` | 0 (INVALID) |
| `getDoorLockStatus(AREA_LEFT_REAR)` | 0 (INVALID) |
| `getDoorLockStatus(AREA_RIGHT_REAR)` | 0 (INVALID) |
| `getDoorLockStatus(AREA_BACK)` | 0 (INVALID) |
| `getDoorLockStatus(AREA_CHILDLOCK_LEFT)` | 1 (UNLOCK) |
| `getDoorLockStatus(AREA_CHILDLOCK_RIGHT)` | 0 (INVALID) |

**Finding:** Main door lock status returns INVALID (0) on BYD Dolphin DiLink 3.0. Only child lock reports status. No dedicated `setDoorLockStatus()` method exists — lock/unlock would need to go through the generic `set()` or `postEvent()` from the base class, but the correct feature IDs are unknown.

### Listener
```java
void onDoorLockStatusChanged(int area, int status)
```

---

## BYDAutoPanoramaDevice

**Package:** `android.hardware.bydauto.panorama`
**Singleton:** `BYDAutoPanoramaDevice.getInstance(context)`
**Actual class on DiLink 3.0:** `BYDAutoPanoramaDeviceDi2l` (DiLink 2.0 compatibility layer)

### Status
All GET methods fail with server-side `SecurityException` for `BYDAUTO_PANORAMA_GET`. The `BydPermissionContext` bypass does NOT work for panorama because the permission check happens in the IPC service, not in the client-side `getInstance()`.

### SET Methods (available but untested)

| Method | Signature |
|---|---|
| `setDisplayMode(mode)` | `(int) → int` |
| `setPanoOperation(op)` | `(int) → int` |
| `setPanoOutputState(state)` | `(int) → int` |
| `setPanoRotation(angle)` | `(int) → int` |
| `setPanoramaTransparence(level)` | `(int) → int` |
| `setRFCameraSwitchState(state)` | `(int) → int` |
| `setPanoRemoteCall(flag)` | `(int) → int` |
| `setPanoFocusState(state)` | `(int) → int` |
| `setLVDSState(state)` | `(int) → int` |
| `setAPAAvmMode(mode)` | `(int) → int` |

---

## BYDAutoBodyworkDevice

**Package:** `android.hardware.bydauto.bodywork`
**Previously used in Pet Mode for door state and battery.**

### Key Methods
| Method | Purpose |
|---|---|
| `getDoorState(area)` | Door open/closed (0=closed, 1=open) |
| `getBatteryCapacity()` | Battery % |
| `getAutoVIN()` | VIN number |
| `getAutoSystemState()` | Security system (NOT door lock) |
| `getWindowState(area)` | Window open/closed |
| `getSunroofState()` | Sunroof state |
| `setAllWindowState(...)` | Control all windows |
| `setBodyWindowCtrlState(area, state)` | Control individual window |
| `setMoonRoofState(state)` | Control sunroof |

---

## BYDAutoMqttDevice

**Package:** `android.hardware.bydauto.mqtt`
**Purpose:** MQTT communication for cloud-to-car commands (how BYD mobile app talks to car)

Only has basic `registerListener`/`unregisterListener` — actual MQTT is handled by native `cloudmanager` service. Cloud commands flow:

```
BYD Mobile App → BYD Cloud → MQTT → cloudmanager (native) → BYDAutoMqttDevice → CAN bus
```

---

## Cloud Communication Flow

```
[BYD Mobile App] --HTTPS--> [BYD Cloud Servers]
        ↓
[MQTT Broker] (TLS)
        ↓
[cloudmanager service] (native C++ on head unit)
        ↓
[CloudServiceApp.apk] (com.byd.cloudserviceapp)
  - sendMsg2NativeCloudService(id, data, length)
  - CloudMsg { id, data[], length, pkg }
        ↓
[BYDAutoMqttDevice listener] → dispatches to vehicle ECUs via CAN
```

### CloudServiceApp Key Methods
- `CloudServiceImp.sendMsg(pkg, msgId, data[], length)` — send command
- `CloudServiceImp.sendMsg2NativeCloudService(msgId, data[], length)` — native bridge
- `CloudServiceImp.registerListener(pkg, msgId, listener)` — receive cloud messages

---

## Temperature Encoding

The AC API uses two encoding schemes:
1. **Direct Celsius** (zone 1-4): `getTemprature(1)` returns 26 = 26°C
2. **Half-degree** (for set): values 34-66 map to 17.0°C - 33.0°C (value/2)

`setAcTemperature(zone, temp, source, ???)` — the temp parameter likely uses the half-degree encoding.

---

## Feasibility Assessment

### AC Control from Head Unit App: CONFIRMED WORKING
- `start(0)` / `stop(0)` — **TESTED, WORKS** on car
- `setAcTemperature(1, tempCelsius, 1, 1)` — **TESTED, WORKS** (direct Celsius, source=1, param4=1)
- Fan: `set(1000, 0x1DE0000C, level)` via base class — **TESTED, WORKS** (named `setAcWindLevel` is broken)
- `setAcWindMode(mode, 1)` — **TESTED, WORKS** (source=1)
- `setAcCycleMode(mode, 0|1)` — **TESTED, WORKS**
- `setAcControlMode(mode, 1)` — **TESTED, WORKS** (0=auto, 1=manual)
- `hasFeature("ACRemoteControl") = 1` — remote control supported

### Door Lock Control: PARTIALLY FEASIBLE
- Device type: **1041**
- `getDoorLockStatus()` returns INVALID (0) for main doors (areas 1-5)
- Child lock status IS readable: `getDoorLockStatus(6)` = 1 (UNLOCK)
- Feature list (`getFeatureList()`) returns `null` — no registered features
- No dedicated `setDoorLockStatus()` method, but base class has `set(int,int,int)` and `postEvent(int,int,int,Object)`
- **CAN bus feature IDs found via manager scan:**

| Feature ID | Value | Notes |
|---|---|---|
| `0x41A00000` | 1 | Online/connected |
| `0x41A00008` | 255 (0xFF) | Possible lock status bitmask |
| `0x41A00010` | 255 (0xFF) | Possible lock status bitmask |
| `0x41A00018` | 65535 | N/A on Dolphin |
| `0x41A00020` | 0 | Unknown |
| `0x41A0002C` | 5 | Possibly door count |
| `0x41A00030` | 65535 | N/A on Dolphin |

- To attempt lock/unlock: `set(1041, 0x41A00008, value)` via base class, but correct values unknown. Risk of sending wrong CAN bus commands.

### 360 Camera Access: BLOCKED (for third-party apps)
- `BYDAutoPanoramaDevice` permissions enforced server-side — `BydPermissionContext` bypass fails
- Cameras use SEPARATE API: `AVMCamera`/`NormalCamera` from `/system/framework/bmmcamera.jar`
- `bmmcamera.jar` NOT on boot classpath — third-party apps can't load the classes
- Native JNI chain: `libbmmcamera_jni.so` → `libbmmcamera_client.so` → `libbmmcameraservice.so`
- BydCamera app runs as uid=1000 (system), gets PANORAMA_GET/SET granted via signature
- `BYD_CAMERA` permission declared but doesn't exist in system — dead reference

**Camera IDs** (from `BmmCameraInfo`):
| Constant | Value | Description |
|---|---|---|
| `CAMERA_CAR_FRONT` | "front" | Front camera |
| `CAMERA_CAR_REAR` | "rear" | Rear/backup camera |
| `CAMERA_CAR_PANO_H` | "pano_h" | Panorama high-res |
| `CAMERA_CAR_PANO_L` | "pano_l" | Panorama low-res |
| `CAMERA_CAR_RF` | "rf" | Right-front camera |
| `CAMERA_CAR_DMS` | "dms" | Driver monitoring system |
| `CAMERA_CAR_FACE` | "face" | Face detection camera |
| `CAMERA_CAR_CARGO` | "cargo" | Cargo area camera |
| `CAMERA_CAR_PANO_APA` | "apa" | Automatic parking assist |
| `CAMERA_CAR_RVS` | "rvs" | Reverse camera |

**AVMCamera API** (from `bmmcamera.jar`):
- `open(int cameraId)` → `AVMCamera` — open camera by ID
- `addPreviewSurface(Surface, int)` — add preview surface
- `startPreview()` / `stopPreview()` — control preview
- `setPreviewCallback(IPreviewCallback)` — frame callback
- `setMediaCodec(MediaCodec, int)` — hardware encoding
- `setPreviewSize(int, int)` — configure resolution (default 1280×960)
- `close()` — release camera

**IBYDAutoPanoService AIDL** (the underlying IPC service):
- `getValue(int id)` → int
- `setValue(int id, int val)` → int
- `getBuffer(int id)` → byte[]
- `setBuffer(int id, byte[])` → int
- `registerUser(listener)` → int
- `unregisterUser(listener)` → int

### Remote Control (from outside car): COMPLEX
- Requires MQTT communication through BYD's cloud infrastructure
- cloudmanager is a native service — not easily interceptable
- Would need to either:
  1. Build a companion app on the head unit that receives commands from your phone
  2. Reverse-engineer BYD's cloud API (requires BYD account token)
  3. Set up local network bridge (head unit WiFi → phone)

---

## Probe App

The `byd-probe` app (in `apps/byd-probe/`) is a diagnostic tool that enumerates and tests all BYD Auto API methods. Uses reflection + `BydPermissionContext` for full access.

Build: `cd apps/byd-probe && bash build.sh`
Install: `adb install build/byd-probe.apk`
