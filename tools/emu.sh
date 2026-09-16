#!/bin/bash
# Emulator UI workbench for the BYD apps.
#
# Runs a BYD_FAKE=1 build (apps/common/fake/, emulator-only) on an AVD whose
# geometry matches the DiLink 3 head unit: 1920x1080 at density 240, landscape,
# stable frame [0,84][1920,990] via `wm overscan`.
#
# It proves layout, states and copy. It proves NOTHING about CAN timing,
# readback or MCU behaviour -- the fake answers instantly and never lies.
# Verify those on the car (adb connect 192.168.10.10:5555).
#
#   tools/emu.sh setup                     # one-time: SDK, system image, AVD
#   tools/emu.sh start                     # boot it (EMU_HEADLESS=1 for no window)
#   tools/emu.sh install pet-mode          # fake build -> install -> launch
#   tools/emu.sh vehicle lock=1 ac=0       # drive the fake vehicle
#   tools/emu.sh shot /tmp/x.png           # screenshot
#   tools/emu.sh hier                      # on-screen view bounds
#   tools/emu.sh stop
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/.local/share/android/sdk}"
export ANDROID_SDK_ROOT="$SDK"          # avdmanager/emulator locate images through this
# platform-tools goes AFTER the system PATH on purpose: two adb versions each
# kill the other's server on first contact, which drops the car's WiFi ADB
# connection every time this script runs. The distro adb stays in charge.
PATH="$SDK/cmdline-tools/latest/bin:$SDK/emulator:$PATH:$SDK/platform-tools"
# The car is often connected at the same time; an untargeted `adb install`
# would hit whichever device adb picks.
export ANDROID_SERIAL=emulator-5554

AVD=dilink3
IMAGE="system-images;android-29;default;x86_64"
CLT_ZIP=commandlinetools-linux-16111833_latest.zip
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
INI="$HOME/.android/avd/$AVD.avd/config.ini"
AAPT2="${AAPT2:-/usr/bin/aapt2}"
APPS="pet-mode door-sound engine-sound byd-probe"

die() { echo "ERROR: $*" >&2; exit 1; }

usage() {
    cat <<EOF
usage: tools/emu.sh <command>

  setup                 install cmdline-tools, platform-tools, emulator,
                        $IMAGE, and create the "$AVD" AVD with head-unit geometry
  start                 boot the AVD and wait for sys.boot_completed
                        (EMU_HEADLESS=1 to run without a window)
  stop                  kill the running AVD
  install <app>         BYD_FAKE=1 build, install, set geometry, launch
                        <app>: $APPS
  vehicle <k=v> ...     drive the fake vehicle
  shot [file]           screencap to file (default /tmp/emu.png)
  hier                  on-screen view ids with bounds and visibility

vehicle keys:
  temp=24               AC set temperature, degrees C
  outside=27            outside temperature, degrees C
  ac=0|1                AC off/on          (fires onAcStoped/onAcStarted)
  door=AREA:STATE       1-4 doors, 5 hood, 6 trunk, 7 cap; 0 closed, 1 open
  lock=0|1|2            system state: normal, set-secure, start-secure
  power=0|1|2           power level: off, ACC, on
  lock_quiet=0|1|2      system state with NO event (exercises poll fallbacks)
  power_quiet=0|1|2     power level with NO event
  soc=70                traction battery percentage
  voltage=N             12V level event (raw, undocumented scale)
  alarm=0|1             alarm state event
  window=AREA:STATE     window state event
  sim=0|1               engine sound simulator off/on (feature 0x48F0000A)
  preset=N              engine sound source type (feature 0x48F00010)
  fid=0xHEX:VALUE       raw feature write, e.g. fid=0x48F00000:0

Every running app gets the broadcast, but each process holds its own copy of
the state, so an app launched afterwards starts from the defaults above.
EOF
}

cfg() { sed -i "/^$1=/d" "$INI"; echo "$1=$2" >> "$INI"; }

cmd_setup() {
    mkdir -p "$SDK/cmdline-tools"
    if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
        echo "=== Fetching $CLT_ZIP ==="
        curl -fL -o "/tmp/$CLT_ZIP" "https://dl.google.com/android/repository/$CLT_ZIP"
        rm -rf /tmp/cmdline-tools
        unzip -q "/tmp/$CLT_ZIP" -d /tmp
        # sdkmanager requires its own directory to sit at cmdline-tools/latest.
        mv /tmp/cmdline-tools "$SDK/cmdline-tools/latest"
    fi
    echo "=== Accepting licenses ==="
    # `yes` dies of SIGPIPE when sdkmanager exits; pipefail would abort here.
    (yes || true) | sdkmanager --sdk_root="$SDK" --licenses >/dev/null
    echo "=== Installing platform-tools, emulator, system image ==="
    sdkmanager --sdk_root="$SDK" "platform-tools" "emulator" "$IMAGE"
    if [ ! -d "$HOME/.android/avd/$AVD.avd" ]; then
        echo "=== Creating AVD $AVD ==="
        # No --device: the generic profile is overridden below.
        echo no | avdmanager create avd -n "$AVD" -k "$IMAGE"
    fi
    [ -f "$INI" ] || die "AVD config not found at $INI"
    echo "=== Applying head-unit geometry ==="
    cfg hw.lcd.width 1920
    cfg hw.lcd.height 1080
    cfg hw.lcd.density 240
    cfg skin.name 1920x1080
    cfg skin.path _no_skin
    cfg hw.initialOrientation landscape
    cfg hw.keyboard yes
    # The car has a soft navigation bar; `install` pads the emulator's 72 px
    # bar to the car's 90 px with overscan.
    cfg hw.mainKeys no
    cfg hw.gpu.enabled yes
    cfg hw.gpu.mode host
    cfg hw.ramSize 2048
    cfg hw.cpu.ncore 4
    cfg vm.heapSize 256
    cfg disk.dataPartition.size 2G
    emulator -list-avds
}

cmd_start() {
    if adb get-state >/dev/null 2>&1; then
        echo "$ANDROID_SERIAL already running"
    else
        echo "=== Booting $AVD ==="
        # No audio: sound is verified on the car, and PulseAudio is a known
        # emulator hang source.
        "$SDK/emulator/emulator" -avd "$AVD" -no-boot-anim -no-audio \
            ${EMU_HEADLESS:+-no-window} >/tmp/emu.log 2>&1 &
        adb wait-for-device
    fi
    for _ in $(seq 180); do
        if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then
            echo "=== Booted: $(adb shell wm size | tr -d '\r') $(adb shell wm density | tr -d '\r') ==="
            return 0
        fi
        sleep 1
    done
    tail -n 30 /tmp/emu.log >&2
    die "boot timed out after 180s (see /tmp/emu.log)"
}

cmd_install() {
    local app="${1:-}"
    [ -n "$app" ] || die "install needs an app: $APPS"
    local dir="$REPO_ROOT/apps/$app"
    [ -f "$dir/build.sh" ] || die "no such app: $app (have: $APPS)"
    local pkg out apk act
    pkg=$(grep -o 'PACKAGE="[^"]*"' "$dir/build.sh" | cut -d'"' -f2)
    out=$(grep -o 'OUT_APK="[^"]*"' "$dir/build.sh" | cut -d'"' -f2)
    [ -n "$pkg" ] && [ -n "$out" ] || die "could not read PACKAGE/OUT_APK from $dir/build.sh"
    (cd "$dir" && BYD_FAKE=1 ./build.sh)
    apk="$dir/build/fake-$out"
    adb install -r "$apk"
    # The AOSP bars are smaller than the car's (status 36 vs 84, nav 72 vs 90),
    # so overscan pads whichever bars the app's theme actually shows:
    #   pet-mode      immersive sticky, no bars  -> full 1920x1080
    #   engine-sound  Fullscreen, nav bar only   -> content [0,0][1920,990]
    #   others        both bars                  -> content [0,84][1920,990]
    # Cabin's SettingsActivity does show both bars, so it gets 66 px more
    # height here than on the car; keep that much bottom slack in it.
    case "$app" in
        pet-mode) adb shell wm overscan reset ;;
        engine-sound) adb shell wm overscan 0,0,0,18 ;;
        *) adb shell wm overscan 0,48,0,18 ;;
    esac
    act=$("$AAPT2" dump badging "$apk" \
        | grep -o "launchable-activity: name='[^']*'" | cut -d"'" -f2)
    [ -n "$act" ] || die "no launchable activity in $apk"
    adb shell am start -n "$pkg/$act"
}

cmd_vehicle() {
    [ "$#" -gt 0 ] || die "vehicle needs at least one key=value"
    local args=(shell am broadcast -a com.wheregoes.byd.FAKE)
    local kv
    for kv in "$@"; do
        case "$kv" in
            *=*) args+=(--es "${kv%%=*}" "${kv#*=}") ;;
            *) die "not a key=value: $kv" ;;
        esac
    done
    adb "${args[@]}"
}

case "${1:-}" in
    setup) cmd_setup ;;
    start) cmd_start ;;
    stop) adb emu kill ;;
    install) shift; cmd_install "$@" ;;
    vehicle) shift; cmd_vehicle "$@" ;;
    shot)
        out="${2:-/tmp/emu.png}"
        adb exec-out screencap -p > "$out"
        echo "$out"
        ;;
    hier)
        # Scoped to the focused package: `dumpsys activity top` dumps the top
        # activity of every stack, so a backgrounded app's views show up too.
        pkg=$(adb shell dumpsys window \
            | sed -n 's/.*mCurrentFocus=Window{[^ ]* [^ ]* \([^/]*\)\/.*/\1/p' | head -1)
        [ -n "$pkg" ] || die "no focused window"
        adb shell dumpsys activity "$pkg" | grep -E 'app:id/'
        ;;
    help|-h|--help) usage ;;
    *) usage >&2; exit 1 ;;
esac
