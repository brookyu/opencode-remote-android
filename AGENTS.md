# AGENTS.md

Guidance for AI agents working in this repo.

## Repository layout

Two independent apps that both talk to an external [OpenCode](https://github.com/sst/opencode) server. They are **not** a build pipeline of each other.

- `web/` — React + TypeScript + Vite app, packaged for Android via Capacitor. This is the **shipped** app; the root release workflows build APKs/AABs from `web/` + Capacitor. Version tracked in `web/package.json`.
- `native-android/` — separate native Kotlin + Jetpack Compose app (`applicationId ai.opencode.remote`, own Gradle project, own version `1.0.0`). Independent of `web/`; the Capacitor app is `ai.opencode.remote.web`. **No CI, no tests.**

Only `.github/workflows/` at the repo root is active. The native Kotlin app has no CI in this repo.

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

There is no `lint`, `typecheck`, or aggregate `test` script. `npm run build` (`tsc -b`) is the type check. `tsconfig.app.json` is strict with `noUnusedLocals` + `noUnusedParameters`; unused locals will fail the build.

To verify all tests, run each `test:*` script individually — there is no `npm test` alias.

### Tests are source-string regression tests

`src/*.regression.test.mjs` and `i18n.test.mjs` are plain Node scripts using `node:assert/strict`. They `readFileSync` `App.tsx` / `api.ts` / `i18n.ts` / `styles.css` and assert specific substrings/regexes exist. They are **not** behavioral tests — renaming functions, restructuring JSX, or reformatting `App.tsx` will silently break `test:ui` / `test:settings` / `test:model`. Update the assertions in the same change when refactoring.

### Architecture

- Single-page app, no router. `src/App.tsx` (~1915 lines) is the whole UI; view switching is `useState` (`setView(...)`). Entry: `main.tsx` -> `App.tsx`.
- `src/api.ts` talks to OpenCode over `@capacitor/core` `CapacitorHttp` (native on device, fetch fallback in browser). Direct `binay` decoding on native, `response.json()` on web. No retry logic — only try/catch to error messages.
- `src/types.ts` has all shared TypeScript types. The api layer hand-parses raw JSON shapes into these types.
- Icons are inline SVG components in `src/Icons.tsx` (no icon library).
- Client config persists in `localStorage` under `opencode.remote.*` keys (server, language, model, agent, theme, new-session directory, working-root-folder).
- The help page content (App.tsx ~lines 1714-1887) is hardcoded English — it does not use the `t()` translator. Adding a new locale does not internationalize the help page.
- Polling: `useEffect` on config changes sets a 3.5s `setInterval` that calls `refreshSessions(true)` and, if a session is open, `loadSelected(...)`. This interval captures `selectedSession` at creation time — changing session unmounts/remounts the effect, which is intentional.

## Native Android app

### Architecture

```
native-android/app/src/main/java/ai/opencode/remote/
├── OpenCodeApp.kt       — Application singleton (OpenCodeApp.get()), SoundPool, language/theme state
├── MainActivity.kt     — Single activity, Compose setContent, loads persisted prefs on startup
├── data/
│   ├── models/Models.kt  — All kotlinx.serialization @Serializable data classes
│   └── api/
│       ├── OpenCodeApi.kt       — Retrofit interface (all endpoints)
│       ├── ApiClient.kt         — Wrapper: configure(host,port,user,pass) + withContext(Dispatchers.IO)
│       └── AuthInterceptor.kt   — OkHttp Basic Auth interceptor (@Volatile credentials)
├── store/Preferences.kt — DataStore<Preferences> wrapper (Flow-backed)
├── viewmodel/
│   ├── SessionsViewModel.kt  — StateFlow<SessionsUiState>, session polling, folder picker
│   ├── DetailViewModel.kt    — StateFlow<DetailUiState>, message loading, send/abort, Koil polling
│   └── SettingsViewModel.kt  — StateFlow<SettingsUiState>, save/test connection
└── ui/
    ├── navigation/AppNavigation.kt  — Tab-based navigation, ViewModel factory
    ├── screens/   — Compose screens (DetailScreen, SessionsScreen, SettingsScreen, HelpScreen)
    └── theme/     — Material3 color schemes, typography, shapes
```

### Gotchas

- **`getConfig()` uses `runBlocking`**: Both `DetailViewModel.kt` and `SessionsViewModel.kt` read `ServerConfig` via `runBlocking { app.preferences.serverConfig.first() }`. This blocks the calling thread. Most calls originate from `viewModelScope.launch` (main dispatcher). Avoid adding time-sensitive work around these calls.
- **`I18n.t(context, key)` uses `resources.getIdentifier()`** on every call — it does string-name lookups per recomposition, not a cached map. Keep calls lightweight or consider caching keys to resource IDs.
- **`AbortRequest` has a `dummy` field** (`data class AbortRequest(val dummy: String = "")`) because Retrofit serializes `@Body` even for empty objects. The serializer config has `encodeDefaults = true`.
- **`HttpLoggingInterceptor` level BODY is always-on** (ApiClient.kt, not gated by `BuildConfig.DEBUG`). Avoid logging sensitive endpoints or gate it in debug builds if touching this file.
- **`ApiClient.configure()` is called on every API method** — it short-circuits if the cred key is unchanged, but `authInterceptor.setCredentials()` writes `@Volatile` fields every time. Not a hotspot, but worth knowing.
- **`sendCommand` readTimeout**: Native uses OkHttp's fixed 30s `readTimeout`. The web app overrides to `300_000` (5 min) for `sendCommand` (`api.ts`). Server commands that take >30s will timeout on native. There is no per-request override mechanism in the native Retrofit setup.

## Cross-platform parity (critical)

Business logic is duplicated between web and native. Changing one side **must** mirror the other:

| Logic | Web | Native |
|---|---|---|
| `modelKey`, `modelFromKey` | `App.tsx` lines 89-98 | `I18n.kt` lines 67-76 |
| `normalizeModelKey`, `sameModel` | `App.tsx` lines 100-112 | `I18n.kt` lines 78-84 |
| `extractText` | `App.tsx` lines 43-49 | `I18n.kt` lines 100-114 (native also handles `info.error`) |
| `normalizeMessageMarkdown` | `App.tsx` line 57-59 | `I18n.kt` lines 116-118 |
| `parentDirectory` | `App.tsx` lines 693-699 | `I18n.kt` lines 120-129 |
| `formatLimit` | `App.tsx` lines 149-154 | `I18n.kt` lines 91-98 |
| `formatTime` | `App.tsx` lines 38-41 | `I18n.kt` lines 86-89 |
| Model/agent selection flow | `App.tsx` (inside `App()`) | `DetailViewModel.kt` `DetailUiState` properties |
| Session-to-view mapping | `App.tsx` `toSessionView()` | `SessionsViewModel.kt` `SessionView` mapping in `refreshSessions` |
| Polling interval | 3.5s `setInterval` | 3.5s `delay()` in `pollingJob` |
| Reconnection (failure count) | `backgroundFailureCountRef` (3-strike) | `failureCount` (3-strike) |

The native `extractText` additionally surfaces `info.error` (API error messages) — the web version does not. If you add error-fieldsurfacing to web, check native parity.

## i18n

Custom, no library. Three locales: `en`, `it`, `zh-TW`. Unknown keys render the key string itself (intentional, asserted by `test:i18n`). A new key must be added to all three locales in **both** apps:

- Web: `web/src/i18n.ts` (`createTranslator`) — one `translations` object with three locale sub-objects.
- Native: `native-android/app/src/main/res/values/strings.xml` (en) + `values-it/strings.xml` (it) + `values-zh-rTW/strings.xml` (zh-TW). Keys use `_` instead of `.` (e.g., `sessions_title` for `sessions.title`). The `I18n.t()` function does the `.replace(".", "_")` automatically.

**There is no automated test that verifies all keys exist in all locales.** A key missing from one locale silently falls back to the key string.

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
- No test, lint, or typecheck CI runs on push/PR. Only the manual release workflows run `npm run build` (typecheck). Always run tests and `npm run build` locally before committing.