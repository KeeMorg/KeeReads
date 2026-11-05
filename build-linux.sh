#!/bin/sh
set -eu
if ! command -v gradle >/dev/null 2>&1; then
  echo 'Gradle not installed. Install Gradle 8.11.1 + Android SDK 35, or open in Android Studio.' >&2
  exit 1
fi
gradle :app:assembleDebug
echo 'APK: app/build/outputs/apk/debug/app-debug.apk'
