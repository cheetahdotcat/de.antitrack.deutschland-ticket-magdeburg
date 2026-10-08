#!/bin/sh
# One-shot debug build: everything runs in podman via scripts/gradle.
set -eu
cd "$(dirname "$0")"
scripts/gradle :app:assembleDebug
echo "APK: $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
