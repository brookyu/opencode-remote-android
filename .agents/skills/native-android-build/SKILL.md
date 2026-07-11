---
name: native-android-build
description: Build, deploy, and launch the native Android Kotlin/Jetpack Compose app on the emulator using gradle, openjdk17, and adb.
---

# Native Android Build & Deploy Skill

Use this skill when you need to compile the native Jetpack Compose Android app (`native-android/`) and deploy it to a running emulator on this development machine.

## Prerequisites & Environment

1. **JDK 17 Home Location (Homebrew macOS):**
   You must set `JAVA_HOME` explicitly for Gradle to compile the project successfully using the Homebrew OpenJDK 17 installation:
   ```bash
   export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
   ```

2. **Android Virtual Device (AVD):**
   The default emulator image is named `opencode_remote`. Check if it is running:
   ```bash
   android emulator list
   ```
   If not running, launch it:
   ```bash
   android emulator start opencode_remote
   ```
   Verify it is connected via ADB:
   ```bash
   ~/Library/Android/sdk/platform-tools/adb devices
   ```

## Deploying the App

1. **Compile & Install:**
   Run the Gradle install task from the `native-android` subdirectory using the configured `JAVA_HOME`:
   ```bash
   cd native-android
   export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
   ./gradlew installDebug --no-daemon
   ```

2. **Launch on Device:**
   Start the app using the Activity Manager (`am`) tool via ADB:
   ```bash
   ~/Library/Android/sdk/platform-tools/adb shell am start -n ai.opencode.remote/ai.opencode.remote.MainActivity
   ```

## Verifying Deployment

To verify the app is running and inspect the UI visually, take a screenshot of the connected emulator:
```bash
android screen capture -o <output_path.png>
```
