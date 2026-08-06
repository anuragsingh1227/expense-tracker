#!/usr/bin/env bash
# Idempotent Cloud Agent bootstrap for the Expense Tracker Android app.
# Installs JDK 17 + the Android SDK, pins Gradle to JDK 17, and warms the
# dependency cache so the first real build is fast.
set -euo pipefail

JDK17_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
ANDROID_SDK_HOME="${ANDROID_SDK_HOME:-$HOME/android-sdk}"
CMDLINE_TOOLS_ZIP_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

echo "==> Ensuring JDK 17 is installed"
if [ ! -d "$JDK17_HOME" ]; then
  sudo apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jdk
fi
export JAVA_HOME="$JDK17_HOME"

echo "==> Ensuring Android command-line tools are installed"
export ANDROID_HOME="$ANDROID_SDK_HOME"
export ANDROID_SDK_ROOT="$ANDROID_SDK_HOME"
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  tmp_dir="$(mktemp -d)"
  curl -fsSL -o "$tmp_dir/cmdline-tools.zip" "$CMDLINE_TOOLS_ZIP_URL"
  unzip -q "$tmp_dir/cmdline-tools.zip" -d "$tmp_dir"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp_dir/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp_dir"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

echo "==> Accepting SDK licenses and installing platform 34 + build-tools"
yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null

echo "==> Pinning Gradle to JDK 17"
mkdir -p "$HOME/.gradle"
if ! grep -qs "org.gradle.java.home" "$HOME/.gradle/gradle.properties"; then
  echo "org.gradle.java.home=$JDK17_HOME" >> "$HOME/.gradle/gradle.properties"
fi

echo "==> Writing local.properties (gitignored) with the SDK location"
echo "sdk.dir=$ANDROID_HOME" > "$(dirname "$0")/../local.properties"

echo "==> Warming Gradle dependency cache"
cd "$(dirname "$0")/.."
./gradlew --no-daemon :app:assembleStoreDebug :app:testStoreDebugUnitTest

echo "==> Install complete"
