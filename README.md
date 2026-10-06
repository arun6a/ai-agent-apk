# AI Agent for Android

An autonomous AI agent that controls your Android phone via voice or text. Uses cloud AI (OpenRouter/Gemini/Z.ai) for reasoning and executes 80+ tools on your device.

**Current version: v2.2.1** (versionCode 57)

## Features

### Multi-Provider AI Support
Choose your AI provider in Settings — no rebuild needed:
- **OpenRouter** (free, unlimited) — Nemotron 120B, Cohere, Poolside, Liquid
- **Google Gemini** (free tier) — 2.0 Flash, 2.5 Flash, 1.5 Flash, 1.5 Pro
- **Z.ai Proxy** (sandbox) — GLM-4.6, GLM-4 Flash
- **Custom** — any OpenAI-compatible endpoint (OpenAI, Groq, Together, Ollama)

### Voice Control
- Speech-to-Text (Android SpeechRecognizer) — English + Tamil
- Text-to-Speech (Android TTS)
- Floating overlay button — tap to read screen, long-press for voice input

### Screen Control (Accessibility Service)
- readScreen() — extracts all text on screen (auto-fallback to VLM)
- tap(x, y) — tap at coordinates
- clickByText(text) — click UI element by text
- type(text) — type into focused field
- swipe, scroll, pressBack, pressHome, submitInput, takeScreenshot

### 80+ Tools
- Apps: launchApp, listInstalledApps, forceStopApp, uninstallApp
- Contacts: readContacts, searchContacts, callContact, sendSMS, getCallLog
- Calendar: getCalendarEvents, createCalendarEvent, setAlarm, setTimer
- Files: listFiles, readFile, writeFile, copyFile, moveFile, deleteFile
- Web: webSearch, makeHttpRequest, downloadFile, openUrl
- In-App Browser: browserOpen, browserSearch, browserReadPage, browserClick
- Device: getBatteryLevel, getVolume, setVolume, setBrightness, toggleFlashlight
- Media: mediaPlayPause, mediaNext, mediaPrevious, takePhoto
- Memory: remember(key, value), recall(key), recallAll()
- Location: getCurrentLocation, openMaps
- Terminal: runShellCommand
- Vision: analyzeScreen(prompt), findElement(description), translateText

### Agent Loop
1. User sends message
2. AI decides which tools to call (up to 6 per step)
3. Tools execute on the phone
4. Results fed back to AI
5. AI decides next step or completes task

### Memory & Rules (SQLite)
- Remembers facts about you (remember/recall)
- Time-triggered rules (e.g., "every day at 9am, check battery")
- Notification-triggered rules
- Always-on foreground service

## Setup

### Prerequisites
1. Android 7.0+ (API 24)
2. Enable Accessibility Service for AI Agent
3. Grant Display over other apps permission (for floating button)
4. Get a free API key:
   - OpenRouter: https://openrouter.ai/keys (recommended — free, no quota)
   - Gemini: https://aistudio.google.com/apikey

### Build from Source
```bash
git clone https://github.com/arun6a/ai-agent-apk.git
cd ai-agent-apk
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requires: JDK 17, Android SDK 34

## Architecture

```
app/src/main/java/com/ai/agent/
├── MainActivity.kt              # Chat UI + agent loop
├── Config.kt                    # App configuration
├── VoiceManager.kt             # STT/TTS
├── accessibility/
│   └── AgentAccessibilityService.kt  # Screen control
├── llm/
│   ├── AIProvider.kt           # Provider config (OpenRouter/Gemini/Z.ai/Custom)
│   └── LLMClient.kt            # Multi-provider chat client
├── tools/
│   └── ToolExecutor.kt         # 80+ tool implementations
├── service/
│   ├── AgentService.kt         # Always-on foreground service
│   └── OverlayManager.kt       # Floating button
├── rules/
│   ├── RuleEngine.kt           # Time/notification triggers
│   └── NotificationListener.kt
├── storage/
│   └── AgentDatabase.kt        # SQLite (conversations, memory, rules)
├── browser/
│   └── BrowserController.kt   # In-app WebView
└── ui/
    ├── SettingsActivity.kt
    ├── AIProviderSettingsActivity.kt
    └── RulesActivity.kt
```

## Tech Stack
- Language: Kotlin
- AI: OpenRouter (Nemotron 120B) / Gemini / Z.ai
- UI: Android Views (programmatic)
- Storage: SQLite (Android SQLiteDatabase)
- Networking: OkHttp
- Build: Gradle 8.10.2

## Version History
- v2.2.1 — Fixed launchApp arg name (accepts both "package" and "pkg")
- v2.2.0 — Multi-provider support (OpenRouter/Gemini/Z.ai/Custom)
- v1.9.0 — Removed local LLM, cloud-only
- v1.4.0 — 70+ tools (terminal, contacts, SMS, calendar, files)
- v1.0.0 — MVP with voice, accessibility, agent loop, vision

## Links
- GitHub: https://github.com/arun6a/ai-agent-apk
- OpenRouter (free AI): https://openrouter.ai
- Gemini (free AI): https://aistudio.google.com
