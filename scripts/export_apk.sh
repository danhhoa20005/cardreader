#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "${SKIP_BUMP:-0}" != "1" ]]; then
  ./gradlew bumpVersion --console=plain
fi

MAJOR=$(grep '^VERSION_MAJOR=' version.properties | cut -d= -f2)
MINOR=$(grep '^VERSION_MINOR=' version.properties | cut -d= -f2)
PATCH=$(grep '^VERSION_PATCH=' version.properties | cut -d= -f2)
CODE=$(grep '^VERSION_CODE=' version.properties | cut -d= -f2)
VER="${MAJOR}.${MINOR}.${PATCH}"

./gradlew testDebugUnitTest lintRelease assembleRelease --console=plain

mkdir -p "$ROOT_DIR/apk_release"
APK_NAME="CardReader_v${VER}_LAN_8888.apk"

rm -f "/Users/MAC/Downloads/${APK_NAME}" "$ROOT_DIR/apk_release/${APK_NAME}"
cp "$ROOT_DIR/app/build/outputs/apk/release/app-release.apk" "/Users/MAC/Downloads/${APK_NAME}"
cp "$ROOT_DIR/app/build/outputs/apk/release/app-release.apk" "$ROOT_DIR/apk_release/${APK_NAME}"
touch "/Users/MAC/Downloads/${APK_NAME}"

echo "Exported ${APK_NAME} (versionName=${VER}, versionCode=${CODE}) -> /Users/MAC/Downloads/${APK_NAME}"
