# Future Plans — From Sandbox-1 (Original AI)

**Date**: 2026-10-08
**Author**: Sandbox-1 (original AI, built v1.0-v3.4.0)
**For**: Sandbox-2 (current AI) and future contributors

## Current State (v4.0.1)

- 100+ tools (screen, apps, browser, files, contacts, rules, vision)
- 6 AI providers (OpenRouter, Groq, Together, Gemini, Z.ai, Custom)
- Hybrid browser agent (WebView + Chrome fallback)
- Proactive rules (16 trigger types)
- API usage monitoring
- 113+ total tools after v4.0.0 browser tools

## User's Vision: Skills & Plugins System

The user and I discussed that the future direction should be **Skills and Plugins**, NOT a developer platform. Here's what that means:

### Skills System (Modular Capabilities)

Instead of 100+ hardcoded tools, use modular skills:

```
/skills/
├── whatsapp/
│   ├── skill.json      # name, description, tools
│   ├── tools.kt         # implementation
│   └── prompt.txt       # how AI should use it
├── shopping/
│   ├── skill.json
│   ├── tools.kt
│   └── prompt.txt
├── smart_home/
│   └── ...
```

### Benefits

| Feature | Hardcoded Tools (Current) | Skills System (Future) |
|---------|--------------------------|----------------------|
| Add new tool | Rebuild APK | Drop in a file |
| Remove unused | Can't | Uninstall skill |
| Share with others | Fork repo | Share skill file |
| Update | Full APK update | Replace one file |
| Token usage | All tools in prompt (4K tokens) | Only active skills (1.5K tokens) |

### Skill Format (Suggested)

```json
{
  "name": "WhatsApp",
  "description": "Send messages, read chats, auto-reply",
  "version": "1.0",
  "tools": ["openChat", "sendMessage", "readMessages", "autoReply"],
  "permissions": ["contacts", "notifications"],
  "prompt": "Use openChat(phone) to open a chat, sendMessage(phone, msg) to send..."
}
```

### Dynamic Prompt Loading

```
Base prompt (1K tokens): "You are a phone AI agent with core tools: [30 tools]"
+ Active skill (500 tokens): "## WhatsApp: openChat(phone), sendMessage(phone, msg)"
+ Active skill (500 tokens): "## Shopping: checkPrice(product, store)"
= Total: 2K tokens (50% less than current 4K!)
```

## Priority Roadmap (My Recommendation)

### Phase 1: Token Optimization (Immediate — Biggest Win)
- **System prompt**: 4K → 1.5K tokens (trim verbose examples)
- **Screen output**: 3K chars → 500 chars (summarized, not raw)
- **Smart verification**: Skip readScreen when action clearly succeeded
- **Impact**: 3x more tasks per day on any provider

### Phase 2: Skills System (Next Major Version)
- Extract tools into skill files (JSON + Kotlin)
- Dynamic prompt loading (only active skills)
- In-app skill browser (install/uninstall)
- Start with: WhatsApp, Shopping, Email, Smart Home

### Phase 3: Vision via Groq (This Week)
- Groq has `llama-3.2-11b-vision` (free, fast)
- Add as vision provider in AIProvider.kt
- No more Z.ai dependency for screenshots
- Vision becomes unlimited (14,400/day)

### Phase 4: Natural Language Rules (Next Sprint)
- Instead of JSON: `createRule(name, triggerType, ...)`
- User says: "Remind me to call Mom every Sunday at 6pm"
- AI parses and creates rule automatically
- No technical knowledge needed

### Phase 5: Workflow Recorder (Future)
- User does something manually once
- App records: "Opened YouTube → searched cats → played first video"
- Saves as reusable workflow
- Next time: "Play cats on YouTube" → replays (1 call instead of 7)

### Phase 6: Telegram Bot (Future)
- Control phone from Telegram (any device)
- Send: "Open YouTube" from laptop → phone does it
- Receive: "Task done" notification
- Remote control from anywhere

## Architecture Vision

```
┌─────────────────────────────────┐
│         User Input              │
│  (Voice, Text, Telegram, Web)   │
└──────────────┬──────────────────┘
               │
┌──────────────▼──────────────────┐
│       AI Router (Smart)         │
│  - Simple → Local LLM (tablet)  │
│  - Complex → Cloud (Groq)       │
│  - Vision → Groq Vision          │
└──────────────┬──────────────────┘
               │
┌──────────────▼──────────────────┐
│      Skill Manager              │
│  - Loads active skills          │
│  - Builds dynamic prompt        │
│  - Routes to skill tools        │
└──────────────┬──────────────────┘
               │
┌──────────────▼──────────────────┐
│      Tool Executor              │
│  - Core tools (30)              │
│  - Skill tools (dynamic)        │
└──────────────┬──────────────────┘
               │
┌──────────────▼──────────────────┐
│      Memory & Learning          │
│  - SQLite: conversations        │
│  - Pattern detection            │
│  - Workflow recorder            │
└─────────────────────────────────┘
```

## Key Insights From Our Journey

1. **Cloud AI limits are real** — 300/day (Z.ai), 500K tokens/day (Groq), 50/day (OpenRouter)
2. **Token optimization > more API keys** — Reducing tokens per task is the real solution
3. **Local LLM failed** — Termux/Node.js issues on tablet, but concept is sound
4. **Tablet is capable** — 11.7GB RAM, 8 cores, arm64 — perfect for local LLM when Termux works
5. **Batching works** — Read-only tools batched, actions one-per-call (v3.3.2)
6. **Activity tools are powerful** — v3.4.0 deep links save 86% API calls vs accessibility taps

## What NOT to Do (From Experience)

1. ❌ Don't add local LLM via llama.cpp (failed, use Termux+llama-server if trying again)
2. ❌ Don't use Pollinations AI (format issues, 402 errors)
3. ❌ Don't batch action tools (causes "typing into own chat" bug)
4. ❌ Don't commit .env or API keys (GitHub blocks it)
5. ❌ Don't add web files (chat.html, agent.ts) — replaced by APK

## Communication

- **GitHub commits** are our communication channel (sandbox-2 polls GitHub)
- **AI bridge** at `/api/ai-chat` doesn't work reliably between sandboxes
- If you need me, commit a file called `PING_S1.md` and I'll respond

## Final Thoughts

The project is in excellent shape. v4.0.1 has:
- Solid foundation (100+ tools, 6 providers)
- Hybrid browser agent
- Proactive rules
- API monitoring

The skills system is the natural next step — it solves token bloat, enables customization, and allows community contributions. Start with token optimization (Phase 1), then build the skills architecture (Phase 2).

Good luck! 🚀
