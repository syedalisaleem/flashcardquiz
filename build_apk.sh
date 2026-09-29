#!/usr/bin/env bash
# Builds FlashcardQuizApp.apk using Gradle
# Requires: Android SDK, JDK 17+
#
# Usage: cd flashcardquiz-master && ./build_apk.sh
# Output: dist/FlashcardQuizApp.apk

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR"
ANDROID_DIR="$ROOT/android"
OUT_DIR="$ROOT/dist"

export JAVA_HOME="$HOME/tools/jdk-17.0.12+7"
export ANDROID_HOME="$HOME/android-sdk"
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -f "$JAVA_HOME/bin/java" ]; then
    echo "ERROR: Java not found at $JAVA_HOME"
    exit 1
fi

echo "==> Java: $(java -version 2>&1 | head -1)"
echo "==> Android SDK: $ANDROID_HOME"

# Check for gradlew
if [ ! -f "$ANDROID_DIR/gradlew" ]; then
    echo "ERROR: gradlew not found in $ANDROID_DIR"
    echo "Please ensure the Android project has a Gradle wrapper."
    exit 1
fi

chmod +x "$ANDROID_DIR/gradlew"

echo "==> Building debug APK..."
cd "$ANDROID_DIR"
./gradlew assembleDebug 2>&1 | tail -20

# Find the output APK
APK_FILE=$(find "$ANDROID_DIR/app/build/outputs/apk/debug" -name "*.apk" 2>/dev/null | head -1)

if [ -z "$APK_FILE" ]; then
    echo "ERROR: APK not found after build."
    echo "Build output:"
    ls -la "$ANDROID_DIR/app/build/outputs/" 2>/dev/null || echo "No outputs directory"
    exit 1
fi

mkdir -p "$OUT_DIR"
cp "$APK_FILE" "$OUT_DIR/FlashcardQuizApp.apk"

echo ""
echo "✅ APK built successfully!"
echo "📁 Output: $OUT_DIR/FlashcardQuizApp.apk"
echo "📏 Size: $(du -h "$OUT_DIR/FlashcardQuizApp.apk" | cut -f1)"
echo ""
echo "To install on a device:"
echo "  adb install $OUT_DIR/FlashcardQuizApp.apk"
echo ""
