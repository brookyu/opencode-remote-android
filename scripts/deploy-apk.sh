#!/usr/bin/env bash
set -euo pipefail

# deploy-apk.sh — Copy a built native Android APK to the Mac mini update server
# Generate update.json metadata consumed by the Android auto-update feature.
#
# Usage:
#   ./scripts/deploy-apk.sh [--apk <path>] [--version <name>] [--code <number>]
#
# Defaults:
#   --apk     native-android/app/build/outputs/apk/release/app-release.apk
#   --version auto-detected from build.gradle.kts
#   --code    auto-detected from build.gradle.kts
#
# Prerequisites:
#   1. SSH access to the Mac mini (zenhost.local via Tailscale)
#   2. The server directory /opt/opencode-update/ exists
#   3. sha256sum is available (macOS: `brew install coreutils`, then use gsha256sum)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_GRADLE="$PROJECT_ROOT/native-android/app/build.gradle.kts"

# --- Parse arguments ---
APK_PATH="${APK_PATH:-}"
VERSION_NAME="${VERSION_NAME:-}"
VERSION_CODE="${VERSION_CODE:-}"
SSH_USER="${SSH_USER:-brookyu}"
SSH_HOST="${SSH_HOST:-macmini}"
REMOTE_DIR="${REMOTE_DIR:-/Users/brookyu/opencode-update}"
# Public URL accessible from Android devices via the Aliyun cloud server (hermes-zenhost-bridge).
# Port 3457 must be opened in the Aliyun security group.
REMOTE_BASE_URL="${REMOTE_BASE_URL:-http://124.223.197.48:3457}"
SHA256_CMD="${SHA256_CMD:-sha256sum}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk)  APK_PATH="$2";  shift 2 ;;
    --version) VERSION_NAME="$2"; shift 2 ;;
    --code) VERSION_CODE="$2"; shift 2 ;;
    --host) SSH_HOST="$2"; shift 2 ;;
    --user) SSH_USER="$2"; shift 2 ;;
    *) echo "Unknown argument: $1"; exit 1 ;;
  esac
done

# --- Auto-detect defaults ---
if [[ -z "$APK_PATH" ]]; then
  APK_PATH="$PROJECT_ROOT/native-android/app/build/outputs/apk/release/app-release.apk"
fi

if [[ ! -f "$APK_PATH" ]]; then
  echo "Error: APK not found at $APK_PATH"
  echo "Build it first: cd native-android && ./gradlew assembleRelease"
  exit 1
fi

if [[ -z "$VERSION_NAME" || -z "$VERSION_CODE" ]]; then
  if [[ -f "$BUILD_GRADLE" ]]; then
    if [[ -z "$VERSION_NAME" ]]; then
      VERSION_NAME=$(grep 'versionName' "$BUILD_GRADLE" | sed -n 's/.*versionName[[:space:]]*=[[:space:]]*"\(.*\)".*/\1/p')
    fi
    if [[ -z "$VERSION_CODE" ]]; then
      VERSION_CODE=$(grep 'versionCode' "$BUILD_GRADLE" | sed -n 's/.*versionCode[[:space:]]*=[[:space:]]*\([0-9]*\).*/\1/p')
    fi
  fi
fi

if [[ -z "$VERSION_NAME" ]]; then
  echo "Error: Could not determine version name. Pass --version or set VERSION_NAME env var."
  exit 1
fi

if [[ -z "$VERSION_CODE" ]]; then
  echo "Error: Could not determine version code. Pass --code or set VERSION_CODE env var."
  exit 1
fi

# --- Calculate checksum ---
APK_CHECKSUM=$("$SHA256_CMD" < "$APK_PATH" | cut -d' ' -f1)
APK_FILENAME="opencode-remote-v${VERSION_NAME}.apk"

echo "=== OpenCode Remote APK Deploy ==="
echo " APK:          $APK_PATH"
echo " Version:      $VERSION_NAME (code $VERSION_CODE)"
echo " Checksum:     $APK_CHECKSUM"
echo " Remote host:  ${SSH_USER}@${SSH_HOST}:${REMOTE_DIR}"
echo ""

# --- Upload APK ---
echo "Uploading APK to server…"
ssh "${SSH_USER}@${SSH_HOST}" "mkdir -p ${REMOTE_DIR}/apks"
scp "$APK_PATH" "${SSH_USER}@${SSH_HOST}:${REMOTE_DIR}/apks/${APK_FILENAME}"

# --- Generate and upload update.json ---
UPDATE_JSON=$(cat <<EOF
{
  "versionCode": ${VERSION_CODE},
  "versionName": "${VERSION_NAME}",
  "downloadUrl": "${REMOTE_BASE_URL}/apks/${APK_FILENAME}",
  "releaseNotes": "",
  "minVersionCode": 1,
  "checksum": "${APK_CHECKSUM}"
}
EOF
)

echo "$UPDATE_JSON" | ssh "${SSH_USER}@${SSH_HOST}" "cat > ${REMOTE_DIR}/update.json"

echo ""
echo "=== Deploy Complete ==="
echo " APK deployed:  ${REMOTE_BASE_URL}/apks/${APK_FILENAME}"
echo " Metadata:      ${REMOTE_BASE_URL}/update.json"
echo ""
