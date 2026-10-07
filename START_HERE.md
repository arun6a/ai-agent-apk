# 🚀 START HERE — For the Next AI Taking Over

**Read this first, then read HANDOFF.md for full details.**

## Quick Summary

This is an **Android AI Agent app** (Kotlin) that controls a phone via voice/text.
- **Current version**: v3.3.0 (versionCode 68)
- **Repo**: https://github.com/arun6a/ai-agent-apk
- **APK**: Cloud-only (no local LLM), ~6.6 MB

## What's New in v3.3.0 (latest)

### 1. API Usage Tracker (NEW)
- `ApiUsageTracker.kt` tracks calls + tokens per task/session/day
- Shows live stats in chat: `[API: 1 calls]`
- Full report in Settings → API Usage Monitor
- Works with OpenAI format AND Gemini format

### 2. Batching Enabled (v3.2.2, kept in v3.3.0)
- System prompt now says "BATCH independent tools"
- Reduces API calls from 5-6 to 2-3 per task
- CRITICAL: Do NOT revert to "DO NOT batch"

### 3. 128K Context Utilization
- MAX_ITERATIONS: 25 → 50
- Conversation limit: 2500 → 8000 chars
- GLM-4.6 has 128K context — use it!

## Build Instructions

```bash
# Prerequisites
# - JDK 17 (download from Adoptium if missing)
# - Android SDK 34

git clone https://github.com/arun6a/ai-agent-apk.git
cd ai-agent-apk
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Key Files to Know

| File | Purpose |
|------|---------|
| `HANDOFF.md` | Full project guide (READ THIS) |
| `BUILDS/v3.3.0.md` | Latest build details |
| `app/src/main/assets/system_prompt.txt` | The AI's brain — all tools + rules |
| `app/src/main/java/com/ai/agent/llm/AIProvider.kt` | 6 providers config |
| `app/src/main/java/com/ai/agent/llm/LLMClient.kt` | Multi-provider chat client |
| `app/src/main/java/com/ai/agent/llm/ApiUsageTracker.kt` | NEW — usage tracking |
| `app/src/main/java/com/ai/agent/tools/ToolExecutor.kt` | 90+ tools |
| `app/src/main/java/com/ai/agent/MainActivity.kt` | Agent loop + chat UI |

## Do NOT Do These

1. ❌ Add web files (chat.html, agent.ts)
2. ❌ Re-add local LLM (removed v1.9.0)
3. ❌ Commit .env or API keys
4. ❌ Use Pollinations AI (use OpenRouter/Groq)
5. ❌ Revert batching to "DO NOT batch"
6. ❌ Reduce MAX_ITERATIONS below 50
7. ❌ Hardcode API endpoints (use AIProvider config)

## How to Test Batching

Send: **"Check my battery level, tell me the current time, check if Bluetooth is on"**

Expected (if batching works):
```
[1/50] [API: 1 calls] Executing getBatteryLevel, getCurrentTime, getBluetoothState...
Done! Battery: 75%, Time: 3:45 PM, Bluetooth: Off

📊 Calls: 1 | Tokens: 420 | 5s
```

If it shows 3 calls → batching not working, check system prompt rule 3.

## How to Push APK to Phone

The user uses a tanel-2 bridge to push APKs from sandbox to phone:
1. Build APK
2. Copy to `/public/ai-agent-vX.Y.Z.apk`
3. Update `/api/download/apk/route.ts`
4. Queue command via tanel on `ide` channel:
   ```bash
   curl -sL -o /storage/.../ai-agent.apk <url> && am start -a android.intent.action.VIEW ...
   ```

## User Context

- **Arun** — hobbyist, Android-only (no PC)
- Uses Z.ai Code sandboxes (may switch between them)
- Has tanel-2 bridge set up on phone (AndroidIDE + Termux)
- GitHub: arun6a/ai-agent-apk

## Need Help?

The original AI (Sandbox 1) has an AI-to-AI bridge at:
```
https://preview-chat-c9aadfe1-a665-4f7c-8232-c9d14a73c0cb.space-z.ai/api/ai-chat
```

Send a message:
```bash
curl -X POST "https://preview-chat-c9aadfe1-a665-4f7c-8232-c9d14a73c0cb.space-z.ai/api/ai-chat" \
  -H "Content-Type: application/json" \
  -d '{"from":"new-ai","message":"I have a question about the project"}'
```

Check for reply:
```bash
curl "https://preview-chat-c9aadfe1-a665-4f7c-8232-c9d14a73c0cb.space-z.ai/api/ai-chat?for=new-ai"
```
