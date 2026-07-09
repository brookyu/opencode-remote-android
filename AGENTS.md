# AGENTS.md

Guidance for AI agents working in this repo.

## Repository layout

Two independent apps that both talk to an external [OpenCode](https://github.com/sst/opencode) server. They are **not** a build pipeline of each other.

- `web/` — React + TypeScript + Vite app, packaged for Android via Capacitor. This is the **shipped** app; the root release workflows build APKs/AABs from `web/` + Capacitor. Version tracked in `web/package.json`.
- `native-android/` — separate native Kotlin + Jetpack Compose app (`applicationId ai.opencode.remote`, own Gradle project, own version `1.0.0`). Independent of `web/`; the Capacitor app is `ai.opencode.remote.web`.

Only `.github/workflows/` at the repo root is active. `native-android/.github/workflows/` is vestigial (subdirectory `.github` is not read as a workflow root) — the native Kotlin app has no CI in this repo.

## The OpenCode server is external

This repo does not contain the server. To run/test the app, start it separately:

```bash
OPENCODE_SERVER_USERNAME=opencode OPENCODE_SERVER_PASSWORD=secret \
  npx -y opencode-ai serve --hostname 0.0.0.0 --port 4096
```

Default port `4096`, Basic Auth via env vars. For browser debugging add `--cors http://localhost:5173`. The Capacitor native HTTP path (on-device) bypasses CORS.

## Web app (primary)

### Commands (run inside `web/`)

```bash
npm install
npm run dev          # vite --host: reachable from a phone on LAN
npm run build        # tsc -b && vite build — this is the typecheck gate
npm run test:i18n    # node --experimental-strip-types, imports i18n.ts directly
npm run test:ui
npm run test:settings
npm run test:model
```

There is no `lint` or `typecheck` script. `npm run build` (`tsc -b`) is the type check. `tsconfig.app.json` is strict with `noUnusedLocals` + `noUnusedParameters`; unused locals will fail the build.

### Tests are source-string regression tests

`src/*.regression.test.mjs` and `i18n.test.mjs` are plain Node scripts using `node:assert/strict`. They `readFileSync` `App.tsx` / `api.ts` / `i18n.ts` / `styles.css` and assert specific substrings/regexes exist. They are **not** behavioral tests — renaming functions, restructuring JSX, or reformatting `App.tsx` will silently break `test:ui` / `test:settings` / `test:model`. Update the assertions in the same change when refactoring.

### Architecture

- Single-page app, no router. `src/App.tsx` (~1900 lines) is the whole UI; view switching is `useState` (`setView(...)`). Entry: `main.tsx` -> `App.tsx`.
- `src/api.ts` talks to OpenCode over `@capacitor/core` `CapacitorHttp` (native on device, fetch fallback in browser).
- Icons are inline SVG components in `src/Icons.tsx` (no icon library).
- Client config persists in `localStorage` under `opencode.remote.*` keys (server, language, model, agent, theme, new-session directory).

## i18n

Custom, no library. Three locales: `en`, `it`, `zh-TW`. Unknown keys render the key string itself (intentional, asserted by `test:i18n`). A new key must be added to all three locales. Web: `src/i18n.ts` (`createTranslator`). Android native parity: `native-android/app/src/main/res/values*/strings.xml` + `I18n.kt`.

## Android release builds (from `web/`)

`web/android/` is **generated and gitignored** — never edit it. CI does:

1. `npm run build` (in `web/`)
2. `npx cap add android` — creates `web/android/`
3. `npx cap sync android`
4. `node web/scripts/sync-android-version.mjs <versionName> <versionCode>` — rewrites `web/android/app/build.gradle(.kts)` version fields. **Version source of truth is `web/package.json#version`.** `versionCode` = `major*10000 + minor*100 + patch`. Do not hand-edit the generated gradle version fields.
5. `./gradlew assembleRelease` (APK) or `bundleRelease` (AAB) in `web/android`.

Workflows (root `.github/workflows/`):
- `android-apk.yml` — `workflow_dispatch`. Signs when all four secrets are present; publishes a GitHub Release on `v*` tags (and requires the secrets there).
- `android-aab.yml` — `workflow_dispatch`, Play Store AAB.

Signing secrets (repo-level): `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

Local manual packaging (optional): `npm run build && npx cap add android && npx cap sync android` in `web/`, then open `web/android` in Android Studio.

## Conventions

- Conventional commits: `feat:`, `fix:`, `chore:`. Releases: `chore: release vX.Y.Z` commit + git tag `vX.Y.Z`.
- PR branches: `fix/...`, `feature/...`, sometimes `fix/<topic>-<timestamp>`.
- Both apps allow cleartext HTTP to a LAN/dev server: `AndroidManifest.xml` sets `usesCleartextTraffic="true"` and `capacitor.config.ts` sets `cleartext: true` + `androidScheme: "http"`, so `http://<LAN-IP>:4096` works without TLS.
