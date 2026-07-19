---
name: native-android-build
description: Build, deploy, and launch the native Android Kotlin/Jetpack Compose app on an emulator or device using gradle and adb.
---

# Native Android Build & Deploy Skill

Use this skill when you need to compile the native Jetpack Compose Android app (`native-android/`) and deploy it to a running emulator or USB device.

> **Environment warning (verified 2026-07-19 on brookyu's Mac):** this repo's dev machines differ. Check what's actually installed before assuming anything below works. On the MacBook there is **no `android` CLI, no Homebrew openjdk@17, no AVDs, no system images** — an earlier version of this skill referenced all four and none existed. Verify with: `emulator -list-avds`, `adb devices`, `ls $ANDROID_HOME`.

## Prerequisites & Environment

1. **JDK:** set `JAVA_HOME` explicitly for Gradle. Try in order:
   ```bash
   # Android Studio's bundled JBR (present on the MacBook — verified working)
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   # or Homebrew OpenJDK 17, if installed on this machine
   export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
   ```

2. **Android SDK:** Gradle needs the SDK location. Either:
   ```bash
   export ANDROID_HOME="$HOME/Library/Android/sdk"
   ```
   or put `sdk.dir=/path/to/sdk` in `native-android/local.properties` (gitignored).

3. **Device:** the app installs to whatever `adb devices` shows — a booted emulator or a USB/wireless phone with debugging enabled.
   - List emulators: `~/Library/Android/sdk/emulator/emulator -list-avds`
   - Boot one: `~/Library/Android/sdk/emulator/emulator -avd <name> &`
   - If no AVD exists, create one first (requires `cmdline-tools` + a system image, ~1 GB download via `sdkmanager`).

## Deploying the App

```bash
cd native-android
export JAVA_HOME=...        # see above
export ANDROID_HOME=...     # see above
./gradlew installDebug --no-daemon
~/Library/Android/sdk/platform-tools/adb shell am start -n ai.opencode.remote/ai.opencode.remote.MainActivity
```

Compile check only (faster, no device needed): `./gradlew compileDebugKotlin --no-daemon`.

Screenshot for visual verification: `adb exec-out screencap -p > /tmp/screen.png`.

## Feature under test: markdown artifact viewer (added 2026-07-19)

When verifying on a live server, exercise these paths:

- **What it does:** markdown files (`.md`/`.markdown`/`.mdx`) produced by the agent (implementation plans, walkthroughs) are viewable in-app. Two entry points in the session Detail screen:
  1. a **Files strip** above the messages — markdown files from the session diff (`/session/:id/diff`, already loaded);
  2. **artifact chips** under assistant bubbles — `.md` paths detected in message text via `extractMarkdownFilePaths` (`I18n.kt`, parity twin in `web/src/App.tsx`).
- Tapping fetches the file via **`GET /file/content?path=…&directory=…`** (new `getFileContent` in `OpenCodeApi.kt`/`ApiClient.kt`) and renders it in a full-screen dialog (`FileViewerDialog` in `DetailScreen.kt`).
- **⚠️ Unverified assumption:** the `/file/content` response is modeled as `{ "content": "..." }` with unknown fields ignored. If the live server wraps the payload differently the viewer shows empty content — check this first if the feature appears broken.
- **Parity:** the same feature exists in `web/` (`api.ts` `getFileContent`, `fileViewer` state + modal in `App.tsx`). Keep detection regex and behavior in sync (see the parity table in AGENTS.md).
- Web checks before committing: `cd web && npm run build && npm run test:i18n && npm run test:ui && npm run test:settings && npm run test:model`.
