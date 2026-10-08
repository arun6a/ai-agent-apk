# AI Agent Android — Handoff Document

## ⚠️ For AI assistants taking over this project

**READ THIS ENTIRE FILE BEFORE MAKING ANY CHANGES.**

This document explains the full project history, current state, architecture, and what NOT to do. It was built across multiple Z.ai Code sandbox sessions with different AI assistants contributing.

---

## Project Overview

**Repo**: https://github.com/arun6a/ai-agent-apk
**Current Version**: v5.0.0 (versionCode 83)
**Platform**: Android 7.0+ (API 24)
**Language**: Kotlin
**Build**: Gradle 8.10.2, JDK 17, Android SDK 34
**APK Size**: ~26 MB (cloud AI + ML Kit text-recognition on-device OCR since v4.3.0)

An autonomous AI agent app that controls an Android phone via voice/text. Uses cloud AI (OpenRouter/Groq/Gemini/Z.ai) for reasoning and executes 90+ tools on the device. Supports proactive rules (time/event-triggered automation).

---

## Version History (Chronological)

| Version | Key Changes | Built By |
|---------|------------|----------|
| v2.2.1 | Multi-provider support (OpenRouter/Gemini/Z.ai/Custom) | Sandbox 1 (original AI) |
| v2.2.2 | Fix permission popup + configurable sandbox URLs | Sandbox 2 |
| v2.3.0 | Fix agent brain — verify before claiming Done | Sandbox 2 |
| v2.3.1 | Fix accessibility service false negative (async binding) | Sandbox 2 |
| v2.4.0 | Add Groq/Together providers, multi-provider VLM | Sandbox 2 |
| v3.0.0 | Proactive Assistant — AI-driven rules + AlarmManager | Sandbox 2 |
| v3.1.0 | 9 new event triggers + BootReceiver + new tools | Sandbox 2 |
| v3.3.0 | API usage monitor + batching + 128K context optimization | Sandbox 1 |
| v3.3.1 | Fix Gemini URL construction (HTML error) | Sandbox 1 |
| v3.3.2 | Fix batching — read-only only, never batch actions | Sandbox 1 |
| **v3.4.0** | **15 new activity tools (openActivity, openDeepLink, etc.)** | **Sandbox 1** |
| v4.0.0-v4.1.5 | Hybrid Browser Agent (visible in-app browser + smart AI) | Sandbox 2 |
| v4.2.0 | Skills system (Markdown skills) + PluginManager (modular JSON plugins) | Sandbox 1+2 |
| v4.3.0 | ML Kit on-device OCR (3-tier hybrid screen reading) | Sandbox 2 |
| **v5.0.0** | **readScreenStructured + WorkManager + Notification Reply + Vector Memory** | **Sandbox 2** |

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
- The app is CLOUD-ONLY — this is final

### ❌ Do NOT commit secrets
- `.env` files must NEVER be committed
- API keys go in the phone's Settings (SharedPreferences), NOT in code
- A `.gitignore` is included — respect it

### ❌ Do NOT use Pollinations AI as a fallback
- Was tried and caused format conversion bugs (OpenAI tool_calls format)
- Use OpenRouter (free Nemotron 120B) or Groq (free, 14,400/day) instead

### ❌ Do NOT revert the batching change
- v3.1.0 said "DO NOT batch multiple action tools"
- v3.2.2+ changed this to "BATCH independent tools to save API requests"
- This is CORRECT — Z.ai GLM-4.6 has 128K context, can handle multiple tools per call
- Batching reduces API requests from 5-6 to 2-3 per complex task

---

## Architecture (v3.3.0)

```
app/src/main/java/com/ai/agent/
├── MainActivity.kt              # Chat UI + agent loop (max 50 iterations)
├── Config.kt                    # App configuration constants
├── VoiceManager.kt             # STT (SpeechRecognizer) + TTS
├── VoiceInputActivity.kt       # Transparent activity for overlay voice input
├── ChatAdapter.kt              # RecyclerView adapter for chat bubbles
├── ChatMessage.kt              # Data class for messages
│
├── accessibility/
│   └── AgentAccessibilityService.kt  # Screen control (tap, type, swipe, readScreen)
│       # Uses waitForAccessibilityService() — handles async binding (v3.3.1 fix)
│
├── llm/
│   ├── AIProvider.kt           # Multi-provider config (SharedPreferences)
│   │                           # 6 providers: OpenRouter, Groq, Together, Gemini, Z.ai, Custom
│   │                           # Stores: provider ID, API key, endpoint, model
│   ├── LLMClient.kt            # Multi-provider chat client
│   │                           # Constructor: LLMClient(context)
│   │                           # Supports: OpenAI-compatible + Gemini format
│   │                           # Tracks: API usage via ApiUsageTracker
│   └── ApiUsageTracker.kt     # NEW v3.3.0 — tracks calls + tokens
│                               # Per-task, per-session, per-day stats
│
├── tools/
│   └── ToolExecutor.kt         # 90+ tools (see tool list below)
│       # launchApp accepts BOTH "package" and "pkg" args
│       # launchApp returns screen content (1500 chars) after 2.5s wait
│       # executeTools() runs multiple tools sequentially (supports batching)
│
├── service/
│   ├── AgentService.kt         # Always-on foreground service
│   │                           # processRuleAction() uses FULL agent loop
│   └── OverlayManager.kt       # Floating button (tap=read, long-press=voice, drag=move)
│
├── rules/                      # Proactive rules system (v3.0.0+)
│   ├── RuleEngine.kt           # Rule management
│   ├── RuleScheduler.kt        # AlarmManager scheduling (survives reboot)
│   ├── RuleTriggerReceiver.kt  # BroadcastReceiver for time triggers
│   ├── BootReceiver.kt         # Reschedules rules on boot
│   ├── BatteryRuleReceiver.kt  # Battery low/charging/discharging
│   ├── IncomingCallReceiver.kt # Phone state + contact lookup
│   ├── SMSReceiver.kt          # SMS_RECEIVED
│   ├── HeadsetReceiver.kt     # HEADSET_PLUG
│   ├── ScreenReceiver.kt      # SCREEN_ON/OFF + USER_PRESENT
│   ├── WiFiReceiver.kt        # NETWORK_STATE_CHANGED
│   ├── PackageReceiver.kt     # PACKAGE_ADDED/REMOVED
│   └── NotificationListener.kt # Notification access
│
├── storage/
│   └── AgentDatabase.kt        # SQLite (conversations, memory, rules, logs)
│
├── browser/
│   └── BrowserController.kt   # In-app WebView
│
└── ui/
    ├── SettingsActivity.kt     # Settings + API Usage Monitor section
    ├── AIProviderSettingsActivity.kt  # Provider selection + API key + model picker
    └── RulesActivity.kt        # Rules list view
```

---

## AI Provider System (KEY FEATURE)

The app supports 6 AI providers, configurable in Settings WITHOUT rebuilding:

### 1. OpenRouter (Free, Recommended)
- Endpoint: `https://openrouter.ai/api/v1/chat/completions`
- Default model: `nvidia/nemotron-3-super-120b-a12b:free` (120B params)
- API key: https://openrouter.ai/keys
- Format: OpenAI-compatible
- Cost: $0.00 (free tier)
- Limit: 50 requests/day free (1000/day with $10 credit)

### 2. Groq (Fastest, Best Free Tier)
- Endpoint: `https://api.groq.com/openai/v1/chat/completions`
- Models: llama-3.3-70b, llama-3.2-11b-vision, mixtral-8x7b
- API key: https://console.groq.com/keys
- Limit: 30 req/min, 14,400 req/day FREE
- Format: OpenAI-compatible

### 3. Together AI
- Models: Llama 3.3 70B Turbo, Llama 3.1 405B, Qwen 2.5 72B
- API key: https://api.together.ai
- Limit: $5 free credit

### 4. Google Gemini (Free Tier)
- Endpoint: `https://generativelanguage.googleapis.com/v1beta`
- Models: gemini-2.0-flash, 2.5-flash, 1.5-flash, 1.5-pro
- API key: https://aistudio.google.com/apikey
- Format: Gemini-specific (LLMClient handles conversion)
- Limit: 15 req/min, 1500 req/day

### 5. Z.ai Proxy (Sandbox, Limited)
- Endpoint: sandbox `/api/llm/proxy`
- Model: glm-4.6 (128K context!)
- No API key needed (sandbox proxy handles auth)
- LIMITATION: 300 requests/day, 30 req/10min (BURST limit)
- Only works when sandbox dev server is running

### 6. Custom (any OpenAI-compatible)
- User enters endpoint, API key, model name
- Works with: OpenAI, Mistral, Ollama, etc.

### How It Works
- `AIProvider.kt` stores config in SharedPreferences
- `LLMClient(context)` reads config on each `chat()` call
- User can switch providers anytime in Settings
- App calls provider DIRECTLY (no proxy needed for OpenRouter/Groq/Gemini)

---

## API Usage Tracker (NEW v3.3.0)

`ApiUsageTracker.kt` tracks:
- **Per-task**: calls, tokens (prompt+completion), elapsed time
- **Per-session**: total calls + tokens since app started
- **Per-day**: total calls + tokens (resets at midnight)
- **Provider used** per call

### How to View Stats
1. **In chat**: Each message shows `[API: N calls]` during execution
2. **After task**: Shows `📊 Calls: N | Tokens: N (prompt+completion) | Ns`
3. **In Settings**: Full report with task/session/daily breakdown

### How It Works
- `LLMClient.parseOpenAIResponse()` extracts `usage.prompt_tokens` + `completion_tokens`
- `LLMClient.parseGeminiResponse()` extracts `usageMetadata`
- Both call `ApiUsageTracker.recordCall(context, prompt, completion, provider)`

---

## Tool List (90+)

### Screen (Accessibility Service required)
readScreen, tap, clickByText, type, swipe, scrollDown, scrollUp, pressBack, pressHome, pressEnter, submitInput, lockScreen, takeScreenshotToGallery

### Apps
launchApp, listInstalledApps, getAppInfo, forceStopApp, uninstallApp, openDialer, openContact, openSettings, shareText, shareFile

### Phone & Contacts
readContacts, searchContacts, callContact, sendSMS, getCallLog, sendEmail

### Calendar & Alarms
getCalendarEvents, createCalendarEvent, setAlarm, setTimer

### Device
getBatteryLevel, getVolume, setVolume, setBrightness, toggleFlashlight, getBluetoothState, getNetworkInfo, getDeviceInfo, getCurrentTime, pingHost

### Media
mediaPlayPause, mediaNext, mediaPrevious, takePhoto

### Files & Terminal
listFiles, readFile, writeFile, copyFile, moveFile, deleteFile, createDirectory, runShellCommand

### Web & Browser
webSearch, makeHttpRequest, downloadFile, openUrl, browserOpen, browserSearch, browserReadPage, browserClick, browserFill, browserEval, browserScrollDown, browserBack, browserGetUrl

### Memory & Clipboard
remember, recall, recallAll, recallSimilar (semantic Jaccard), searchMemory (full-text), getClipboard, setClipboard

### Screen Structure (v5.0.0 — saves VLM calls)
readScreenStructured (JSON tree of clickable elements + bounds)

### Notification Reply (v5.0.0 — instant, no app opening)
replyToNotification(package, message) — uses Notification.Action + RemoteInput

### Vision
analyzeScreen, findElement, translateText

### Rules (Proactive)
createRule, listRules, deleteRule, modifyRule

### Triggers (16 types)
time, notification, battery_low, charging, discharging, incoming_call, sms_received, headset_connected, headset_disconnected, screen_on, screen_off, user_unlocked, wifi_connected, wifi_disconnected, app_installed, app_uninstalled

---

## Agent Loop (MainActivity.kt)

```
1. User sends message
2. ApiUsageTracker.startNewTask() — reset task counter
3. LLM decides tool calls (BATCH independent tools, max 50 iterations)
4. ToolExecutor.executeTools() runs them sequentially
5. Results fed back to LLM
6. LLM decides next step or completes task
7. Loop ends when LLM replies with empty tool_calls
8. Shows final reply + API stats
```

### Batching (CRITICAL — v3.2.2+)
- **BATCH independent tools**: if 3 tools don't depend on each other, call all 3 in ONE response
- Example: "check battery, time, wifi" → [getBatteryLevel, getCurrentTime, getNetworkInfo] = 1 call
- **SPLIT when dependent**: "open youtube then search" → launchApp → [type, submitInput] = 2 calls
- This reduces API requests from 5-6 to 2-3 per complex task

### LLM Response Format
```json
{
  "reply": "short message to user",
  "tool_calls": [
    {"name": "getBatteryLevel", "args": {}},
    {"name": "getCurrentTime", "args": {}}
  ]
}
```

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
# APK: app/build/outputs/apk/debug/app-debug.apk (~6.6 MB)
```

### Version Bump
Edit `app/build.gradle.kts`:
```kotlin
versionCode = 69  // increment
versionName = "3.4.0"  // new version
```

---

## Key Technical Decisions

### Why Cloud-Only (No Local LLM)
- Local LLM (llama.cpp) was tried in v1.7.0-v1.8.0
- Failed: CLI flag incompatibility, corrupted models, "server exited" errors, 85MB APK bloat
- Cloud AI (OpenRouter/Groq/Gemini) is faster, smarter, and free

### Why Batching Was Enabled (v3.2.2)
- v3.1.0 said "DO NOT batch" — caused 5-6 API calls per task
- Z.ai GLM-4.6 has 128K context — can handle multiple tools per call
- Batching reduces requests to 2-3 per task → 50% fewer API calls
- Critical for staying under daily quotas (Z.ai: 300/day, OpenRouter: 50/day)

### Why MAX_ITERATIONS = 50
- Was 25 in v2.2.1 — too low for complex tasks
- 50 gives headroom for multi-step automation
- Combined with batching, uses fewer total API calls

### Why Conversation Limit = 8000 chars
- Was 2500 (too aggressive trimming)
- GLM-4.6 has 128K context (~32K chars usable)
- 8000 chars allows ~10-15 iterations before trimming

---

## User Context

- **User**: Arun (arun6a on GitHub)
- **Device**: Android phone + tablet (no PC)
- **Development**: Z.ai Code sandbox (switches between sandboxes)
- **Phone bridge**: tanel-2 (remote command queue via HTTP)
  - Repo: https://github.com/arun6a/tanel-2
  - Channels: "default" (Termux), "ide" (AndroidIDE)
  - Used to push APKs and run commands on phone from sandbox

### How to Push APK to Phone
1. Build APK in sandbox
2. Copy to `public/ai-agent-vX.Y.Z.apk`
3. Update `/api/download/apk/route.ts` to serve new version
4. Queue command via tanel:
   ```
   curl -sL -o /storage/.../ai-agent.apk <download_url> && \
   am start -a android.intent.action.VIEW -d file:///...ai-agent.apk -t application/vnd.android.package-archive
   ```

---

## DO NOT Do These Things

1. ❌ Do NOT add web files (chat.html, agent.ts)
2. ❌ Do NOT re-add local LLM (removed for good reasons)
3. ❌ Do NOT commit `.env` or API keys
4. ❌ Do NOT use Pollinations AI — use OpenRouter/Groq instead
5. ❌ Do NOT hardcode API endpoints — use AIProvider config
6. ❌ Do NOT change `LLMClient` constructor — it takes `Context`
7. ❌ Do NOT add sandbox files (Next.js, package.json) to this repo
8. ❌ Do NOT revert batching to "DO NOT batch"
9. ❌ Do NOT reduce MAX_ITERATIONS below 50
10. ❌ Do NOT reduce conversation limit below 8000

---

## How to Add a New Tool

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

## Current State (as of v5.0.0)

- ✅ Multi-provider support (6 providers)
- ✅ 100+ tools implemented (including 4 new in v5.0.0)
- ✅ Agent loop with batching (2-3 calls per task, was 5-6)
- ✅ API usage monitoring (calls + tokens, per task/session/day)
- ✅ Voice control (STT/TTS)
- ✅ Floating overlay button
- ✅ Memory & rules (SQLite) + **semantic recall** (v5.0.0)
- ✅ Proactive rules (16 trigger types) + **WorkManager reliability** (v5.0.0)
- ✅ Vision (multi-provider VLM) + **readScreenStructured** (v5.0.0 — saves VLM calls)
- ✅ In-app browser (visible, shared login)
- ✅ 128K context utilization (GLM-4.6)
- ✅ ML Kit on-device OCR (v4.3.0)
- ✅ **Direct notification reply** without opening apps (v5.0.0)
- ✅ Skills + Plugins (v4.2.0) — modular AI tool definitions

### Known Issues
- Vision (VLM) may not work with all providers — falls back to readScreen() (text) or readScreenStructured() (v5.0.0)
- OpenRouter free tier: 50 req/day (add $10 for 1000/day)
- Z.ai: 300 req/day + 30 req/10min burst limit
- MIUI may kill NotificationListener — needs re-enable in Settings → Notifications → Notification access
- recallSimilar uses Jaccard word-overlap (crude, no embeddings) — fine for typical memory stores, can be upgraded later

### Recommended Provider for Agent Loop
**Groq** (14,400 req/day free) — enough for ~2000 complex tasks per day
