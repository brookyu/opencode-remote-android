# OpenCode Remote Android

Native Android app to monitor and interact with [OpenCode](https://github.com/sst/opencode) sessions running on your server.

## Features

- 📋 Browse all OpenCode sessions across directories
- ✨ Create and delete sessions
- 💬 Send prompts and slash commands
- 📊 View messages, todos, and file diffs
- 🤖 Select AI agents and models
- 🌐 Multi-language support (English, Italian, Traditional Chinese)
- 🎨 Material 3 with dynamic colors and dark/light theme
- 🔔 Completion sound notification

## Requirements

- Android 12+ (API 31)
- OpenCode server running and accessible

## Setup

1. Configure your OpenCode server with Basic Auth
2. Install the app
3. Go to Settings → enter host, port, username, password
4. Test connection and save

## Building

```bash
./gradlew assembleDebug
```

## Tech Stack

- Kotlin + Jetpack Compose
- Material 3
- Retrofit + OkHttp + Kotlinx Serialization
- DataStore Preferences
- Navigation Compose
- Media3 ExoPlayer

## License

MIT
