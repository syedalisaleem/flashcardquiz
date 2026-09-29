#!/usr/bin/env bash
# Builds an Android App Bundle (.aab) for Play Store upload.
# Requires: Android SDK, JDK 17+, and Gradle.
#
# Usage: ./build_aab.sh
# Output: dist/FlashcardQuiz.aab

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR"
ANDROID_DIR="$ROOT/android"
OUT_DIR="$ROOT/dist"
AAB_OUT="$OUT_DIR/FlashcardQuiz.aab"

# Check for Java
if [ -z "${JAVA_HOME:-}" ]; then
    # Try common locations
    for candidate in \
        "/usr/lib/jvm/java-17-openjdk" \
        "/usr/lib/jvm/java-17-openjdk-amd64" \
        "/usr/lib/jvm/java-21-openjdk" \
        "/usr/lib/jvm/java-21-openjdk-amd64" \
        "$HOME/.sdkman/candidates/java/current"; do
        if [ -d "$candidate" ]; then
            JAVA_HOME="$candidate"
            break
        fi
    done
fi

if [ -z "${JAVA_HOME:-}" ] || [ ! -f "$JAVA_HOME/bin/java" ]; then
    echo "ERROR: Java 17+ not found."
    echo "Set JAVA_HOME or install OpenJDK 17+:"
    echo "  sudo apt install openjdk-17-jdk"
    exit 1
fi

# Check for Android SDK
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"

# Also check common Linux paths
if [ ! -d "$SDK" ]; then
    for candidate in \
        "$HOME/Android/Sdk" \
        "$HOME/android-sdk" \
        "/opt/android-sdk" \
        "/usr/local/android-sdk"; do
        if [ -d "$candidate" ]; then
            SDK="$candidate"
            break
        fi
    done
fi

if [ ! -d "$SDK" ]; then
    echo "ERROR: Android SDK not found."
    echo "Set ANDROID_HOME or install Android SDK:"
    echo "  https://developer.android.com/studio#command-line-tools"
    exit 1
fi

export JAVA_HOME
export ANDROID_HOME="$SDK"

step() {
    echo -e "\033[0;36m==> $1\033[0m"
}

step "Building AAB (release)..."
cd "$ANDROID_DIR"

if [ ! -f "gradlew" ]; then
    step "Setting up Gradle wrapper..."
    if [ -f "gradlew.bat" ]; then
        # Create Unix gradlew from Windows gradlew.bat
        cat > gradlew << 'WRAPPER'
#!/bin/sh
DIRNAME=$(dirname "$0")
APP_HOME=$(cd "$DIRNAME" && pwd)
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
JAVA_EXE="java"
if [ -n "$JAVA_HOME" ]; then
    JAVA_EXE="$JAVA_HOME/bin/java"
fi
exec "$JAVA_EXE" $DEFAULT_JVM_OPTS $JAVA_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
WRAPPER
        chmod +x gradlew
    else
        echo "ERROR: No Gradle wrapper found. Run 'gradle wrapper' in the android/ directory first."
        exit 1
    fi
fi

chmod +x gradlew

step "Running Gradle build..."
./gradlew bundleRelease \
    -Pandroid.injected.signing.store.file="$ANDROID_DIR/release-key.jks" \
    -Pandroid.injected.signing.store.password="flashcard123" \
    -Pandroid.injected.signing.key.alias="flashcardquiz" \
    -Pandroid.injected.signing.key.password="flashcard123"

step "Locating AAB..."
AAB_FILE=$(find "$ANDROID_DIR/app/build/outputs/bundle/release" -name "*.aab" | head -1)

if [ -z "$AAB_FILE" ]; then
    echo "ERROR: AAB not found after build."
    exit 1
fi

mkdir -p "$OUT_DIR"
cp "$AAB_FILE" "$AAB_OUT"

echo ""
echo "✅ AAB built successfully!"
echo "📁 Output: $AAB_OUT"
echo ""
echo "To upload to Google Play Console:"
echo "  1. Go to https://play.google.com/console"
echo "  2. Create a new release"
echo "  3. Upload $AAB_OUT"
echo ""
