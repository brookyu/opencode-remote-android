# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**`AGENTS.md` is the canonical, detailed agent guide for this repo — read it first.** This file summarizes the essentials.

## Repository layout

Two **independent** apps that both talk to an external [OpenCode](https://github.com/sst/opencode) server (not in this repo):

- `web/` — React + TypeScript + Vite, packaged for Android via Capacitor. **This is the shipped app** (`ai.opencode.remote.web`); release workflows build APKs/AABs from it. Version source of truth: `web/package.json#version`.
- `native-android/` — separate native Kotlin + Jetpack Compose app (`ai.opencode.remote`), own Gradle project. **No CI, no tests.**

## Commands (run inside `web/`)

```bash
npm install
npm run dev            # vite --host (reachable from phone on LAN)
npm run build          # tsc -b && vite build — THE typecheck gate; strict tsconfig with noUnusedLocals/Parameters
npm run test:i18n      # each test:* is run individually; there is no aggregate `npm test`
npm run test:ui
npm run test:settings
npm run test:model
```

No `lint` script exists. **No CI runs tests/typecheck on push** — only manual release workflows run `npm run build`. Always run tests + build locally before committing.

To run/test against a real server (external, default port 4096, Basic Auth):

```bash
OPENCODE_SERVER_USERNAME=opencode OPENCODE_SERVER_PASSWORD=secret \
  npx -y opencode-ai serve --hostname 0.0.0.0 --port 4096   # add --cors http://localhost:5173 for browser dev
```

## Architecture (web)

- Single-page app, no router. `src/App.tsx` (~1900 lines) is the whole UI; view switching via `useState`. Entry: `main.tsx` → `App.tsx`.
- `src/api.ts` talks to OpenCode via Capacitor `CapacitorHttp` (native on device, fetch fallback in browser), hand-parsing raw JSON into `src/types.ts` types. `sendCommand` gets a 300s timeout override.
- Config persists in `localStorage` under `opencode.remote.*` keys. Icons are inline SVG in `src/Icons.tsx`. Polling: 3.5s `setInterval` in a `useEffect`.

**Tests are source-string regression tests**: `src/*.regression.test.mjs` `readFileSync` the sources and assert substrings/regexes — renaming functions or restructuring JSX silently breaks them. Update assertions in the same change when refactoring.

## Native Android app

Single-activity Compose app: `MainActivity.kt` → `ui/navigation/AppNavigation.kt` (tab nav) → screens + `viewmodel/` (StateFlow-based: `SessionsViewModel`, `DetailViewModel`, `SettingsViewModel`) → `data/api/` (Retrofit `OpenCodeApi`, `ApiClient` wrapper, Basic Auth interceptor) → `store/Preferences.kt` (DataStore). Models are kotlinx.serialization classes in `data/models/Models.kt`.

Notable gotchas (details in AGENTS.md): `getConfig()` uses `runBlocking` on the main dispatcher; OkHttp readTimeout is a fixed 30s (long server commands time out on native); `HttpLoggingInterceptor` BODY is always on; `I18n.t()` does `resources.getIdentifier()` per call.

## Cross-platform parity (critical)

Business logic is **duplicated** between web (`App.tsx`) and native (`I18n.kt`, ViewModels) — changing one side **must** mirror the other: `modelKey`/`normalizeModelKey`/`sameModel`, `extractText` (native also surfaces `info.error`), `normalizeMessageMarkdown`, `parentDirectory`, `formatLimit`, `formatTime`, session-to-view mapping, 3.5s polling, 3-strike reconnection. AGENTS.md has the full file:line mapping table.

## i18n

Custom, no library. Locales: `en`, `it`, `zh-TW`. New keys must be added to all three locales in **both** apps — web `web/src/i18n.ts` (one `translations` object) and native `res/values*/strings.xml` (dots in keys become underscores, e.g. `sessions.title` → `sessions_title`). No test verifies key coverage across locales; a missing key silently renders the key string. The help page is hardcoded English.

## Releases & conventions

- `web/android/` is **generated and gitignored** — never edit it. CI: `npm run build` → `npx cap add android` → `cap sync` → `node web/scripts/sync-android-version.mjs <versionName> <versionCode>` (`versionCode = major*10000 + minor*100 + patch`) → `./gradlew assembleRelease`/`bundleRelease`.
- Release workflows (root `.github/workflows/`): `android-apk.yml`, `android-aab.yml` — both `workflow_dispatch`; signed when the four `ANDROID_*` secrets are present; GitHub Release on `v*` tags.
- Conventional commits (`feat:`/`fix:`/`chore:`); releases are `chore: release vX.Y.Z` + tag `vX.Y.Z`.
- Both apps allow cleartext HTTP to LAN dev servers (`usesCleartextTraffic="true"`, Capacitor `cleartext: true`).
