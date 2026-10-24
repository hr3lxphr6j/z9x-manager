#!/usr/bin/env bash
# Thin wrapper — real build is Gradle + Kotlin.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
./gradlew :app:assembleRelease --no-daemon "$@"
APK="$(ls -1 app/build/outputs/apk/named/Z9XManager-v*.apk 2>/dev/null | head -1 || true)"
if [[ -z "$APK" ]]; then
  APK="$(ls -1 app/build/outputs/apk/release/*.apk 2>/dev/null | head -1 || true)"
fi
echo "OK: ${APK:-'(see app/build/outputs/apk/)'}"
sha256sum "$APK" 2>/dev/null || true
