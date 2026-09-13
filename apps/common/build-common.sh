#!/bin/bash
# Shared build pipeline for every app in this repo.
#
# Each app's build.sh sets OUT_APK + PACKAGE and sources this file. Everything
# else -- SDK location, BYD stubs, signing identity, alignment -- lives here so
# the four apps cannot drift apart again.
#
# Overridable from the environment: ANDROID_JAR, AAPT2.
set -euo pipefail

# $0 is the app's build.sh even while this file is being sourced.
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
COMMON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$COMMON_DIR/../.." && pwd)"
cd "$APP_DIR"

: "${OUT_APK:?build.sh must set OUT_APK before sourcing build-common.sh}"
: "${PACKAGE:?build.sh must set PACKAGE before sourcing build-common.sh}"

ANDROID_JAR="${ANDROID_JAR:-$HOME/.local/share/android/platform-29/android.jar}"
AAPT2="${AAPT2:-/usr/bin/aapt2}"
BUILD_DIR="build"
STUBS_DIR="$COMMON_DIR/stubs"
BYD_SDK_DEX="$COMMON_DIR/libs/byd_sdk.dex"
MIN_SDK=28
TARGET_SDK=29

# One keystore for the whole repo, outside build/ so a clean build keeps the
# same signing identity. Regenerating it per build (the old behaviour) made
# every reinstall a signature mismatch and in-place upgrades impossible.
KEYSTORE="$REPO_ROOT/keystore/byd-apps.keystore"
KEY_ALIAS="byd-apps"
KEY_PASS="bydapps"

if [ ! -f "$ANDROID_JAR" ]; then
    cat >&2 <<EOF
ERROR: android.jar not found at $ANDROID_JAR

Install it once:
  mkdir -p "\$HOME/.local/share/android/platform-29"
  curl -fsSL -o /tmp/platform-29.zip https://dl.google.com/android/repository/platform-29_r05.zip
  unzip -oj /tmp/platform-29.zip android-10/android.jar -d "\$HOME/.local/share/android/platform-29"

Or set ANDROID_JAR to an existing API 29 android.jar.
EOF
    exit 1
fi

echo "=== Cleaning ($PACKAGE) ==="
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"/{compiled-res,gen,classes,stubs-classes,dex}

echo "=== Compiling resources ==="
"$AAPT2" compile --dir src/main/res -o "$BUILD_DIR/compiled-res/"

echo "=== Linking resources ==="
"$AAPT2" link \
    --manifest src/main/AndroidManifest.xml \
    -I "$ANDROID_JAR" \
    -o "$BUILD_DIR/app-unsigned.apk" \
    --java "$BUILD_DIR/gen" \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    "$BUILD_DIR"/compiled-res/*.flat

echo "=== Compiling BYD API stubs ==="
find "$STUBS_DIR" -name "*.java" | xargs javac \
    --release 11 \
    -cp "$ANDROID_JAR" \
    -d "$BUILD_DIR/stubs-classes"

echo "=== Compiling app sources ==="
find src/main/java -name "*.java" | xargs javac \
    --release 11 \
    -cp "$ANDROID_JAR:$BUILD_DIR/stubs-classes:$BUILD_DIR/gen" \
    -d "$BUILD_DIR/classes"

# The stubs exist only to satisfy javac; the real classes ship in byd_sdk.dex,
# which d8 merges in as a second input.
#
# TOOLCHAIN LIMIT -- d8 8.2.2-dev cannot dex Java **enums** or **anonymous inner
# classes**. Either one fails with:
#   NullPointerException: Cannot invoke "String.length()" because "<parameter1>" is null
# Reproduced with both the distro build-tools and Google's official
# build-tools_r34, and unaffected by --lib, --min-api or --no-desugaring.
# Lambdas, method references, nested classes, generics and string switch are all
# fine. So: use `private final Runnable r = this::method;` instead of
# `new Runnable() {...}`, and a final class of static constants instead of an
# enum. Every app in this repo already follows that.
echo "=== Dexing app + BYD SDK ==="
d8 --min-api "$MIN_SDK" \
    --lib "$ANDROID_JAR" \
    --classpath "$BUILD_DIR/stubs-classes" \
    --output "$BUILD_DIR/dex" \
    "$BYD_SDK_DEX" \
    $(find "$BUILD_DIR/classes" -name "*.class")

echo "=== Packaging APK ==="
cp "$BUILD_DIR/app-unsigned.apk" "$BUILD_DIR/$OUT_APK"
(cd "$BUILD_DIR/dex" && zip -q -u "../$OUT_APK" classes.dex)
zipalign -p -f 4 "$BUILD_DIR/$OUT_APK" "$BUILD_DIR/aligned-$OUT_APK"
mv "$BUILD_DIR/aligned-$OUT_APK" "$BUILD_DIR/$OUT_APK"

if [ ! -f "$KEYSTORE" ]; then
    echo "=== Generating repo keystore (first build only) ==="
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -alias "$KEY_ALIAS" \
        -keyalg RSA -keysize 2048 \
        -validity 10000 \
        -storepass "$KEY_PASS" -keypass "$KEY_PASS" \
        -dname "CN=BYD Apps, O=wheregoes"
fi

# minSdk 28 would make apksigner skip v1; DiLink on Android 10 needs both.
echo "=== Signing APK ==="
apksigner sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$KEY_PASS" \
    --key-pass "pass:$KEY_PASS" \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    "$BUILD_DIR/$OUT_APK"

# Verify across the whole range, not just minSdk: at minSdk 28 apksigner picks
# v3 and reports v1/v2 as "false" even when both are present and valid.
apksigner verify -v --min-sdk-version 21 --max-sdk-version "$TARGET_SDK" "$BUILD_DIR/$OUT_APK" \
    | grep -E "^Verified using v[123] scheme"

echo ""
echo "=== Build complete: $BUILD_DIR/$OUT_APK ==="
echo "Install: adb install -r $(basename "$APP_DIR")/$BUILD_DIR/$OUT_APK"
echo "Then enable it in Settings > Apps > Auto-start management"
