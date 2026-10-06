# AI Agent APK — Build Plan

## Project Overview

**Goal:** Build an Android APK that runs as a foreground service, controls the phone via Accessibility, and uses AI (cloud LLM + VLM) to understand and execute user commands via voice or rules.

**Architecture:** Cloud-first (thin client). Local LLM is optional (Phase 3).

**Built with:** Kotlin + Android SDK + Gradle (built in Z.ai sandbox, not AndroidIDE)

---

## Current Status (v0.8.0)

### ✅ Phase 1: MVP (COMPLETE)
- [x] Project setup (Kotlin, Gradle, manifest, permissions)
- [x] Accessibility service (tap, type, clickByText, swipe, readScreen, back, home, launchApp)
- [x] LLM client (Z.ai API via proxy endpoint on sandbox)
- [x] Agent loop (LLM sees tool results, decides next steps, retries until done)
- [x] Voice input (STT via Android SpeechRecognizer)
- [x] Voice output (TTS via Android TextToSpeech)
- [x] Chat UI (dark theme, chat bubbles, auto-scroll)
- [x] English + Tamil language support
- [x] submitInput() tool (reliable form submission)
- [x] Partial text matching for clickByText
- [x] Infinite loop detection (stops after 3 repeated action tools)
- [x] Task interruption (send button becomes stop button)
- [x] Max 25 iterations per task

### ✅ Phase 1.5: Floating Overlay (COMPLETE)
- [x] Floating eye button (stays on top of all apps)
- [x] Tap → reads current screen → speaks description via TTS
- [x] Long-press → opens Google voice dialog → processes command
- [x] Drag → moves button to new position
- [x] Transparent VoiceInputActivity (no app background shown)
- [x] Eye icon toggle in header to show/hide overlay

### ✅ Phase 2: Rules & Memory (COMPLETE)
- [x] SQLite database (conversations, memory, rules, action log)
- [x] Persistent chat history (survives app close/reopen)
- [x] Memory tools (remember, recall, recallAll)
- [x] Memory injected into system prompt (agent always knows user facts)
- [x] RuleEngine (time-based triggers, checks every minute)
- [x] NotificationListenerService (event-based triggers)
- [x] Rules UI (RulesActivity — create/view/delete/toggle rules)
- [x] Rules button in header (opens rules management)
- [x] Rule-triggered command execution (rule fires → agent processes action)
- [x] Full agent loop for voice commands (not fire-and-forget)

### 🚧 Phase 3: Vision + Advanced (NEXT)
- [ ] Screenshot via MediaProjection API
- [ ] VLM integration (send screenshot to Z.ai vision model)
- [ ] `analyzeScreen(prompt)` tool — "what's on my screen?" with actual vision
- [ ] `findElement(description)` tool — "find the Like button" → returns coordinates
- [ ] Better tool chaining with vision verification
- [ ] Web automation tools (openUrl, fillFormField, clickButton, readPage)

### 🔮 Phase 4: Polish + Optional Local LLM (FUTURE)
- [ ] Settings UI (API config, voice settings, rules management)
- [ ] Optional llama.cpp integration (offline mode)
- [ ] Model downloader (pick from recommendations)
- [ ] Auto-fallback (local if offline, cloud if complex)
- [ ] Multi-provider support (Z.ai, OpenAI, Claude, Gemini)

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│              AI Agent APK (v0.8.0)               │
│                                                  │
│  ┌──────────────┐  ┌─────────────────────────┐  │
│  │ Chat UI      │  │ Floating Overlay         │  │
│  │ (MainActivity)│  │ (eye button on all apps)│  │
│  │ - Chat log   │  │ - Tap: read screen      │  │
│  │ - Voice btn  │  │ - Long-press: voice cmd │  │
│  │ - Rules btn  │  │ - Drag: move            │  │
│  └──────────────┘  └─────────────────────────┘  │
│         │                    │                    │
│         ▼                    ▼                    │
│  ┌──────────────────────────────────────────────┐│
│  │ Agent Loop (MainActivity + OverlayManager)    ││
│  │ 1. LLM decides tools                         ││
│  │ 2. ToolExecutor runs tools                    ││
│  │ 3. Results sent back to LLM                   ││
│  │ 4. Repeat until done (max 25 iterations)     ││
│  └──────────────────────────────────────────────┘│
│         │                    │                    │
│         ▼                    ▼                    │
│  ┌──────────────┐  ┌─────────────────────────┐  │
│  │ Voice I/O    │  │ Accessibility Service   │  │
│  │ - STT (free) │  │ - readScreen, tap, type  │  │
│  │ - TTS (free) │  │ - clickByText, swipe     │  │
│  └──────────────┘  └─────────────────────────┘  │
│         │                                         │
│         ▼                                         │
│  ┌──────────────────────────────────────────────┐│
│  │ Storage (SQLite)                               ││
│  │ - Conversations (chat history)                ││
│  │ - Memory (user facts: name, preferences)      ││
│  │ - Rules (time-based + notification triggers)  ││
│  │ - Action log                                   ││
│  └──────────────────────────────────────────────┘│
│         │                                         │
│         ▼                                         │
│  ┌──────────────────────────────────────────────┐│
│  │ Rules Engine                                   ││
│  │ - Time triggers (checks every minute)         ││
│  │ - Notification triggers (NotificationListener)││
│  │ - Fires rule action → agent processes          ││
│  └──────────────────────────────────────────────┘│
└─────────────────────────────────────────────────┘
                    ↕ (internet)
┌─────────────────────────────────────────────────┐
│    Sandbox (Next.js + proxy endpoints)           │
│  - /api/llm/proxy → forwards to Z.ai API         │
│  - /api/download/apk → serves the APK            │
│  - Auth via TANEL_TOKEN                          │
└─────────────────────────────────────────────────┘
                    ↕ (internal)
┌─────────────────────────────────────────────────┐
│         Z.ai API (free tier, internal)           │
│  - LLM: GLM-4.6 (chat completions)              │
│  - Future: VLM for vision/screenshot analysis   │
└─────────────────────────────────────────────────┘
```

---

## Project Structure

```
ai-agent-apk/
├── README.md
├── BUILD_PLAN.md                 ← This file
├── POLLER_COMMANDS.txt           ← tanel bridge commands
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── system_prompt.txt          ← LLM system prompt (tools, rules, examples)
│       ├── java/com/ai/agent/
│       │   ├── Config.kt                  ← API endpoints, screen resolution, version
│       │   ├── MainActivity.kt           ← Chat UI + agent loop + voice + memory
│       │   ├── VoiceInputActivity.kt     ← Transparent activity for overlay voice input
│       │   ├── ChatMessage.kt            ← Data model
│       │   ├── ChatAdapter.kt            ← RecyclerView adapter for chat bubbles
│       │   ├── VoiceManager.kt           ← STT + TTS (English + Tamil)
│       │   ├── accessibility/
│       │   │   └── AgentAccessibilityService.kt  ← Screen control tools
│       │   ├── llm/
│       │   │   └── LLMClient.kt          ← Z.ai API client with tool calling
│       │   ├── tools/
│       │   │   └── ToolExecutor.kt       ← Executes tools, returns results
│       │   ├── service/
│       │   │   ├── AgentService.kt       ← Foreground service + rule engine
│       │   │   └── OverlayManager.kt    ← Floating button + voice command loop
│       │   ├── storage/
│       │   │   └── AgentDatabase.kt      ← SQLite (conversations, memory, rules, log)
│       │   ├── rules/
│       │   │   ├── RuleEngine.kt         ← Time-based triggers
│       │   │   └── NotificationListener.kt ← Event-based triggers
│       │   └── ui/
│       │       └── RulesActivity.kt      ← Rules management UI
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml      ← Chat UI
│           │   └── overlay_button.xml     ← Floating button
│           ├── values/
│           │   ├── strings.xml
│           │   ├── colors.xml
│           │   ├── themes.xml
│           │   └── transparent_theme.xml
│           ├── drawable/                  ← Icons, backgrounds, shapes
│           ├── xml/
│           │   └── accessibility_service_config.xml
│           └── mipmap-anydpi-v26/         ← Launcher icon
```

---

## Complete Tool List (v0.8.0)

### Screen Tools
| Tool | Parameters | Description |
|------|-----------|-------------|
| readScreen() | — | Returns all text on screen |
| tap(x, y) | x, y: Float | Taps at coordinates |
| clickByText(text) | text: String | Clicks element by text (with partial matching) |
| type(text) | text: String | Types into focused field |
| swipe(x1, y1, x2, y2) | coords | Swipes gesture |
| scrollDown() | — | Scrolls down |
| scrollUp() | — | Scrolls up |
| pressBack() | — | Back button |
| pressHome() | — | Home button |
| pressEnter() | — | Keyboard Enter key |
| submitInput() | — | Submits form/search (most reliable) |

### App Tools
| Tool | Parameters | Description |
|------|-----------|-------------|
| launchApp(package) | package: String | Launches an app |
| listInstalledApps() | — | Lists installed apps |

### Memory Tools
| Tool | Parameters | Description |
|------|-----------|-------------|
| remember(key, value) | key, value: String | Stores a fact |
| recall(key) | key: String | Retrieves a fact |
| recallAll() | — | Lists all memories |

### Meta Tools
| Tool | Parameters | Description |
|------|-----------|-------------|
| wait(seconds) | seconds: Int | Waits |

---

## Build & Test Workflow

### Where code is written
1. **Z.ai Sandbox** — code written here (JDK 17 + Android SDK + Gradle installed)
2. **GitHub** (`arun6a/ai-agent-apk`) — version control + backup
3. **Phone** (`/storage/emulated/0/Documents/ai-workspace/projects/ai-agent-apk/`) — local copy

### How APK is compiled
```bash
export JAVA_HOME=~/jdk-17.0.13+11
export ANDROID_HOME=~/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
cd /home/z/my-project/ai-agent-apk
./gradlew assembleDebug --no-daemon
```

### How APK is deployed
1. Copy APK to sandbox `/public/` dir
2. Phone downloads via `curl -s -o <path> <sandbox-url>/api/download/apk`
3. Install on phone (tap APK in file manager)

### Build environment paths (in sandbox)
- JDK 17: `~/jdk-17.0.13+11`
- Android SDK: `~/android-sdk`
- Gradle: `~/gradle-8.10.2`

---

## Permissions Required

| Permission | Purpose |
|-----------|---------|
| INTERNET | LLM API calls via proxy |
| RECORD_AUDIO | Voice input (STT) |
| POST_NOTIFICATIONS | Foreground service notification |
| FOREGROUND_SERVICE | Keep agent alive in background |
| QUERY_ALL_PACKAGES | List installed apps |
| SYSTEM_ALERT_WINDOW | Floating overlay button |
| BIND_ACCESSIBILITY_SERVICE | Screen control (tap, type, read) |
| BIND_NOTIFICATION_LISTENER_SERVICE | Read notifications for rules |

---

## Testing Checklist

### Phase 1 (MVP)
- [x] Chat with AI (text input → response)
- [x] Voice input (tap mic → speak → text)
- [x] Voice output (TTS speaks replies)
- [x] Launch apps ("open youtube")
- [x] Search ("search BLACKPINK on youtube")
- [x] Screen reading ("what's on my screen?")
- [x] Agent loop (verifies + retries)
- [x] Task interruption (stop button)

### Phase 1.5 (Overlay)
- [x] Floating button visible on all apps
- [x] Tap → read screen → speak description
- [x] Long-press → voice input from any app
- [x] Drag → move button
- [x] Voice command loop (not fire-and-forget)

### Phase 2 (Rules & Memory)
- [x] Chat history persists
- [x] Memory: "remember my name is Arun" → "what's my name?"
- [x] Memory persists across app restarts
- [x] Rules UI (create/view/delete/toggle)
- [x] Time-based rules (e.g., "07:00" → morning briefing)
- [x] Notification-based rules (e.g., "com.whatsapp:Salman")

### Phase 3 (Vision) — NOT YET
- [ ] Screenshot + VLM
- [ ] "find the Like button" → coordinates → tap
- [ ] Image analysis ("which photo is BLACKPINK?")

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 0.1.0 | Oct 5 | Initial MVP — chat UI, mock LLM |
| 0.2.0 | Oct 5 | Accessibility service, tool execution |
| 0.2.1 | Oct 5 | LLM proxy (fix internal API unreachable) |
| 0.2.2 | Oct 5 | pressEnter tool |
| 0.3.0 | Oct 5 | Agent loop (verify + retry) |
| 0.3.1 | Oct 5 | submitInput tool, YouTube guidance |
| 0.3.2 | Oct 5 | Keyboard fix (show keyboard before Enter) |
| 0.4.0 | Oct 5 | 25 iterations, stop button, infinite loop guard |
| 0.4.1 | Oct 5 | Partial text matching, readScreen excluded from loop guard |
| 0.5.0 | Oct 5 | Floating overlay + foreground service |
| 0.5.1 | Oct 5 | Crash fix (foregroundServiceType) |
| 0.5.2 | Oct 5 | Manual overlay toggle button |
| 0.5.3 | Oct 5 | Direct overlay from Activity |
| 0.5.4 | Oct 5 | Fixed touch handling (tap vs drag) |
| 0.5.5 | Oct 5 | Rewrote touch (simplified layout) |
| 0.5.6 | Oct 5 | Long-press support |
| 0.6.0 | Oct 5 | Voice input activity, memory, rules engine, notification listener |
| 0.6.1 | Oct 6 | Fixed voice routing to overlay manager |
| 0.6.2 | Oct 6 | Fixed transparent theme, chat history persistence |
| 0.7.0 | Oct 6 | Full agent loop for voice, memory tools |
| 0.8.0 | Oct 6 | Rules UI, rule engine in service, memory in prompt |

---

## Next Steps (Phase 3: Vision)

1. Add MediaProjection API for screenshots
2. Create `/api/llm/vision` proxy endpoint on sandbox
3. Add `screenshot()` and `analyzeScreen(prompt)` tools
4. Add `findElement(description)` tool — VLM returns coordinates
5. Update system prompt with vision tools
6. Test: "find the Like button and tap it"
