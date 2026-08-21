#!/data/data/com.termux/files/usr/bin/bash
# WitchScans Extension Build Script
# Usage: ./build.sh

set -e

EXT_DIR="/data/data/com.termux/files/home/extensions-source"
ANDROID_HOME="/storage/emulated/0/Download/Uzantı/extensions-source-dosyalari/android-sdk/android-sdk"
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
OUTPUT="/storage/emulated/0/Download/Uzantı/WP REST/tachiyomi-en.witchscans-v1.6.1.apk"

export ANDROID_HOME JAVA_HOME

cd "$EXT_DIR"

echo "Building WitchScans extension..."
./gradlew :src:en:witchscans:assembleDebug 2>&1 | tail -5

APK=$(find src/en/witchscans/build -name "*.apk" 2>/dev/null | head -1)
if [ -n "$APK" ]; then
    cp "$APK" "$OUTPUT"
    echo "Build SUCCESS: $OUTPUT"
    aapt2 dump badging "$OUTPUT" 2>&1 | head -3
else
    echo "Build FAILED: no APK found"
    exit 1
fi
