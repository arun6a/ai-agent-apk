# Build History Index

Per-version changelogs for `arun6a/ai-agent-apk`. Each file documents one released version with:
- What changed
- Files modified
- Known issues (what's still broken)
- Migration notes
- Test cases
- User feedback that drove the changes

**Why this exists:** If the sandbox restarts (memory wipe), a future AI takes over, or you forget what version did what — these notes are the source of truth. The git log shows *what* changed, but these notes explain *why* and *what's next*.

## How to use

### To find the current state
1. Look at the latest version file below
2. Or check `app/build.gradle.kts` for `versionName`
3. Or run `git log --oneline -5` to see recent commits

### To add a new version
1. Copy `TEMPLATE.md` to `vX.Y.Z.md`
2. Fill in all sections
3. Update the table below
4. Commit with message: `"vX.Y.Z: <short summary>"`

## Version Index

| Version | Released | versionCode | Headline | File |
|---|---|---|---|---|
| v2.2.1 | 2026-10-06 | 57 | Repo cleanup + HANDOFF.md | [v2.2.1.md](./v2.2.1.md) |
| v2.2.2 | 2026-10-06 | 58 | Fix permission popup + configurable proxy URLs | [v2.2.2.md](./v2.2.2.md) |
| v2.3.0 | 2026-10-07 | 59 | Fix agent brain — verify before claiming Done! | [v2.3.0.md](./v2.3.0.md) |
| v2.3.1 | 2026-10-07 | 60 | Fix "Accessibility service not running" false negative | [v2.3.1.md](./v2.3.1.md) |
| v2.4.0 | 2026-10-07 | 61 | Add Groq/Together providers, multi-provider VLM, smarter prompts | [v2.4.0.md](./v2.4.0.md) |
| v3.0.0 | 2026-10-07 | 62 | Proactive Assistant — AI-driven rules + AlarmManager + fixed notification wiring | [v3.0.0.md](./v3.0.0.md) |
| v3.1.0 | 2026-10-07 | 63 | 9 new event triggers (battery/call/SMS/headset/screen/WiFi/app) + new tools | [v3.1.0.md](./v3.1.0.md) |
| v3.3.0 | 2026-10-07 | 68 | API usage monitor + batching + 128K context optimization | [v3.3.0.md](./v3.3.0.md) |
| v3.3.1 | 2026-10-07 | 69 | Fix Gemini — construct full URL with model + API key | [v3.3.1.md](./v3.3.1.md) |
| v3.3.2 | 2026-10-07 | 70 | Fix batching — batch read-only only, NEVER batch action tools | [v3.3.2.md](./v3.3.2.md) |
| **v3.4.0** | 2026-10-07 | 71 | **15 new activity tools — open specific screens inside other apps** | [v3.4.0.md](./v3.4.0.md) |
| (template) | — | — | — | [TEMPLATE.md](./TEMPLATE.md) |

## Quick History Summary

**v2.2.x** — Cleanup + infrastructure fixes
- v2.2.1: repo cleanup, removed build artifacts, added HANDOFF.md
- v2.2.2: fixed permission popup race condition, made sandbox URLs configurable

**v2.3.x** — Agent brain fixes
- v2.3.0: no more batching tools, must verify before "Done!", CancellationException handling
- v2.3.1: fixed "Accessibility service not running" — added waitForAccessibilityService() polling

**v2.4.x** — Multi-provider expansion
- v2.4.0: added Groq + Together providers, free OpenRouter vision models, multi-provider VLM routing, WhatsApp/YouTube UI tips in system prompt

**v3.0.x** — Proactive Assistant
- v3.0.0: AI-driven rule creation (createRule/listRules/deleteRule/modifyRule tools), AlarmManager-based scheduling (survives app kill), fixed NotificationListener → AgentService wiring, full agent loop for rule actions, clean RulesActivity (no more manual form)
- v3.1.0: 9 new event triggers wired up (battery_low, charging, discharging, incoming_call, sms_received, headset_connected/disconnected, screen_on/off, user_unlocked, wifi_connected/disconnected, app_installed/uninstalled) + BootReceiver for reboot survival + 4 new tools (shareFile, openDialer, openContact, openSettings) + FileProvider + 4 new permissions

**v3.3.x** — API monitoring + batching optimization
- v3.3.0: API usage tracker (calls + tokens per task/session/day), batching enabled, 128K context optimization (MAX_ITERATIONS=50, conversation limit=8000)
- v3.3.1: fixed Gemini URL construction (was returning HTML instead of JSON)
- v3.3.2: corrected batching rules — batch read-only only, NEVER batch action tools

**v3.4.x** — Activity tools (current)
- v3.4.0: 15 new activity tools (openActivity, openDeepLink, openWhatsAppChat, openYouTubeVideo, playSpotify, openInstagramProfile, openTelegramChat, makePhoneCall, etc.) — enables direct screen navigation via Android Intents instead of accessibility taps. Saves 86% API calls per complex task.

## File Naming Convention
- `vMAJOR.MINOR.PATCH.md` — matches `versionName` from `app/build.gradle.kts`
- All lowercase `v` prefix
- Three-part version number
