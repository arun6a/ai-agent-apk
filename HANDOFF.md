# AI Agent Android Project — Handoff Document

## ⚠️ For AI assistants taking over this project

This document explains the full project history, current state, and what NOT to do. Read this before making changes.

---

## Project Overview

**Repo**: https://github.com/arun6a/ai-agent-apk
**Current Version**: v2.2.1 (versionCode 57)
**Platform**: Android 7.0+ (API 24)
**Language**: Kotlin
**Build**: Gradle 8.10.2, JDK 17, Android SDK 34

An autonomous AI agent app that controls an Android phone via voice/text. Uses cloud AI (OpenRouter/Gemini/Z.ai) for reasoning and executes 80+ tools on the device.

---

## What This Project Is NOT

### ❌ This is NOT a web project
- Do NOT add `chat.html`, `agent.ts`, or any web files
- Do NOT add Next.js, React, or Node.js code
- Do NOT add `package.json` or TypeScript configs
- The repo should contain ONLY Android/Kotlin files

### ❌ This is NOT a local LLM project
- Local LLM (llama.cpp) was tried and REMOVED in v1.9.0
- Do NOT re-add `LocalLLM.kt`, `ModelDownloaderActivity.kt`, or `jniLibs/`
- Local LLM caused: corrupted models, CLI flag incompatibility, 85MB APK bloat, "server exited" errors
- The app is CLOUD-ONLY — this is final

### ❌ Do NOT commit secrets
- `.env` files must NEVER be committed
- API keys (OpenRouter, Gemini, GitHub tokens) go in the user's phone Settings, NOT in code
- A `.gitignore` is included — respect it

---

## Architecture (v2.2.1)

```
app/src/main/java/com/ai/agent/
├── MainActivity.kt              # Chat UI + agent loop (max 25 iterations)
├── Config.kt                    # App configuration constants
├── VoiceManager.kt             # STT (SpeechRecognizer) + TTS
├── VoiceInputActivity.kt       # Transparent activity for overlay voice input
├── ChatAdapter.kt              # RecyclerView adapter for chat bubbles
├── ChatMessage.kt              # Data class for messages
│
├── accessibility/
│   └── AgentAccessibilityService.kt  # Screen control (tap, type, swipe, readScreen)
│
├── llm/
│   ├── AIProvider.kt           # Multi-provider config (SharedPreferences)
│   │                           # Providers: OpenRouter, Gemini, Z.ai, Custom
│   │                           # Stores: provider ID, API key, endpoint, model
│   └── LLMClient.kt            # Chat client (OpenAI-compatible + Gemini format)
│                               # Constructor: LLMClient(context)
│                               # Reads config from AIProvider each call
│
├── tools/
│   └── ToolExecutor.kt         # 80+ tools (see tool list below)
│       # launchApp accepts BOTH "package" and "pkg" args (AI models vary)
│
├── service/
│   ├── AgentService.kt         # Always-on foreground service
│   └── OverlayManager.kt       # Floating button (tap=read, long-press=voice, drag=move)
│
├── rules/
│   ├── RuleEngine.kt           # Time-triggered rules
│   └── NotificationListener.kt # Notification-triggered rules
│
├── storage/
│   └── AgentDatabase.kt        # SQLite (conversations, memory, rules, logs)
│
├── browser/
│   └── BrowserController.kt   # In-app WebView (browserOpen, browserSearch, etc.)
│
└── ui/
    ├── SettingsActivity.kt
    ├── AIProviderSettingsActivity.kt  # Provider selection + API key + model picker
    └── RulesActivity.kt
```

---

## AI Provider System (KEY FEATURE)

The app supports 4 AI providers, configurable in Settings WITHOUT rebuilding:

### 1. OpenRouter (RECOMMENDED — free, unlimited)
- Endpoint: `https://openrouter.ai/api/v1/chat/completions`
- Default model: `nvidia/nemotron-3-super-120b-a12b:free` (120B params)
- API key: https://openrouter.ai/keys
- Format: OpenAI-compatible
- Cost: $0.00 (free tier)
- No daily quota limit

### 2. Google Gemini (free tier)
- Endpoint: `https://generativelanguage.googleapis.com/v1beta`
- Models: gemini-2.0-flash, 2.5-flash, 1.5-flash, 1.5-pro
- API key: https://aistudio.google.com/apikey
- Format: Gemini-specific (different request/response structure)
- Free tier: 15 req/min, 1500/day
- LLMClient.kt handles format conversion automatically

### 3. Z.ai Proxy (sandbox-only, LIMITED)
- Endpoint: `https://preview-chat-{session}.space-z.ai/api/llm/proxy`
- Model: glm-4.6
- No API key needed (sandbox proxy handles auth)
- LIMITATION: 300 requests/day, resets at UTC midnight
- Only works when sandbox dev server is running

### 4. Custom (any OpenAI-compatible)
- User enters endpoint, API key, model name
- Works with: OpenAI, Groq, Together, Mistral, Ollama, etc.

### How it works
- `AIProvider.kt` stores config in SharedPreferences
- `LLMClient(context)` reads config on each `chat()` call
- User can switch providers anytime in Settings
- App calls provider DIRECTLY (no proxy needed for OpenRouter/Gemini)

---

## Tool List (80+)

### Screen (Accessibility Service required)
readScreen, tap, clickByText, type, swipe, scrollDown, scrollUp, pressBack, pressHome, pressEnter, submitInput, lockScreen, takeScreenshotToGallery

### Apps
launchApp, listInstalledApps, getAppInfo, forceStopApp, uninstallApp

### Contacts & Phone
readContacts, searchContacts, callContact, sendSMS, getCallLog, sendEmail, shareText

### Calendar & Alarms
getCalendarEvents, createCalendarEvent, setAlarm, setTimer

### Location
getCurrentLocation, openMaps

### Device Control
getBatteryLevel, getVolume, setVolume, setBrightness, toggleFlashlight, getBluetoothState, getNetworkInfo, getDeviceInfo, pingHost, getCurrentTime

### Media
mediaPlayPause, mediaNext, mediaPrevious, takePhoto

### Files
listFiles, readFile, writeFile, copyFile, moveFile, deleteFile, createDirectory

### Clipboard
getClipboard, setClipboard

### Web
webSearch, makeHttpRequest, downloadFile, openUrl

### In-App Browser
browserOpen, browserSearch, browserReadPage, browserClick, browserFill, browserEval, browserScrollDown, browserBack, browserGetUrl

### Memory (SQLite)
remember(key, value), recall(key), recallAll

### Vision (VLM)
analyzeScreen(prompt), findElement(description), translateText

### Terminal
runShellCommand

### Local LLM (REMOVED)
localLLM — returns "removed, use cloud AI" message

---

## Agent Loop (MainActivity.kt)

```
1. User sends message
2. LLM decides tool calls (up to 6 per step, max 25 iterations)
3. ToolExecutor executes tools
4. Results fed back to LLM
5. LLM decides next step or completes task
6. Loop ends when LLM replies with empty tool_calls
7. Infinite loop guard: same tool+args 3x = stop
```

### LLM Response Format
The LLM must return JSON in `message.content`:
```json
{
  "reply": "short message to user",
  "tool_calls": [
    {"name": "launchApp", "args": {"package": "com.google.android.youtube"}}
  ]
}
```

If task is complete: `"tool_calls": []`

---

## System Prompt

Located at: `app/src/main/assets/system_prompt.txt` (4 KB)

Contains:
- Agent role description
- Full tool list with arg names
- Common app package names (YouTube, WhatsApp, Chrome, etc.)
- JSON response format rules
- Verification rules (don't fire-and-forget)

---

## Build Instructions

### Prerequisites
- JDK 17 (NOT 21 — Android Gradle Plugin requires JDK 17)
- Android SDK 34 (platforms;android-34, build-tools;34.0.0)
- Gradle 8.10.2 (wrapper included)

### Build
```bash
git clone https://github.com/arun6a/ai-agent-apk.git
cd ai-agent-apk
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk (~85MB with no jniLibs)
```

### Version Bump
Edit `app/build.gradle.kts`:
```kotlin
versionCode = 58  // increment
versionName = "2.3.0"  // new version
```

---

## What Was Tried and Abandoned

### ❌ Local LLM (llama.cpp)
- **Tried**: v1.7.0-v1.8.0
- **Problems**: 
  - llama.cpp CLI flags changed (`--no-conversation` removed in b11435+)
  - Model files corrupted on download
  - "server exited before becoming ready" errors
  - 85MB APK bloat (llama binaries in jniLibs/)
  - Android W^X protection (can't exec downloaded binaries)
- **Resolution**: Removed entirely in v1.9.0. Cloud-only is final.

### ❌ Pollinations AI fallback
- **Tried**: When Z.ai hit daily quota
- **Problems**:
  - 402 error on system prompts > 800 chars
  - Returns tool_calls in OpenAI format (empty content)
  - Stringified tool_calls (not objects)
  - Wrong tool selection (less capable model)
- **Resolution**: Removed. Now uses OpenRouter (free, unlimited, better model)

### ❌ Z.ai as primary (300/day quota)
- **Problem**: Daily quota of 300 requests exhausted quickly
- **Resolution**: OpenRouter is now primary (unlimited free). Z.ai is a fallback option.

---

## User Context

- **User**: Arun (arun6a on GitHub)
- **Device**: Android phone + tablet (no PC)
- **Development environment**: Z.ai Code sandbox
- **Phone bridge**: tanel-2 (remote command queue via HTTP)
  - Repo: https://github.com/arun6a/tanel-2
  - Channels: "default" (Termux), "ide" (AndroidIDE)
  - Used to push APKs, run commands on phone from sandbox

### How to push APK to phone
1. Build APK in sandbox
2. Copy to `public/ai-agent-vX.Y.Z.apk`
3. Update `/api/download/apk/route.ts` to serve new version
4. Queue command via tanel: `curl -sL -o /storage/.../ai-agent.apk <download_url> && am start -a android.intent.action.VIEW -d file:///...ai-agent.apk -t application/vnd.android.package-archive`

---

## Current State (as of v2.2.1)

- ✅ Multi-provider support working (OpenRouter/Gemini/Z.ai/Custom)
- ✅ 80+ tools implemented
- ✅ Agent loop with tool execution
- ✅ Voice control (STT/TTS)
- ✅ Floating overlay button
- ✅ Memory & rules (SQLite)
- ✅ Vision (VLM) for screen analysis
- ✅ In-app browser
- ✅ Pushed to GitHub (clean repo, 61 files, 476 KB)

### Known Issues
- Vision (VLM) still uses Z.ai proxy — no free vision model on OpenRouter yet
  - Phone falls back to readScreen() (text-based) when vision fails
- APK is ~85MB (due to old build artifacts — can be reduced by cleaning build dir)

---

## DO NOT Do These Things

1. ❌ Do NOT add web files (chat.html, agent.ts) — they were from an old phase
2. ❌ Do NOT re-add local LLM — it was removed for good reasons
3. ❌ Do NOT commit `.env` or API keys
4. ❌ Do NOT use Pollinations AI — use OpenRouter instead
5. ❌ Do NOT hardcode API endpoints in LLMClient — use AIProvider config
6. ❌ Do NOT change `LLMClient` constructor — it takes `Context`, not endpoint/model
7. ❌ Do NOT add sandbox files (Next.js, package.json, etc.) to this repo
8. ❌ Do NOT push build artifacts (app/build/) — they're in .gitignore

---

## If You Need to Add a New Tool

1. Add to `ToolExecutor.kt` in the `executeTool()` `when` block:
```kotlin
"myNewTool" -> {
    val arg1 = call.args["arg1"] as? String ?: return ToolResult(false, "missing arg1")
    // implement tool logic
    ToolResult(true, "result description")
}
```

2. Add to system prompt (`app/src/main/assets/system_prompt.txt`):
```
- myNewTool(arg1) — description of what it does
```

3. Rebuild and push APK

---

## Contact / History

This project was built across multiple Z.ai Code sandbox sessions with AI assistance.
The user (Arun) is a hobbyist with no PC — only Android devices.
The tanel-2 bridge allows the sandbox AI to control the phone remotely.

GitHub: https://github.com/arun6a/ai-agent-apk
tanel-2: https://github.com/arun6a/tanel-2
