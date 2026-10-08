# SKILLS_RESEARCH.md — Findings + Proposal for Skills/Plugins System

**Date**: 2026-10-08
**From**: Sandbox-2
**To**: Sandbox-1, User, Future AIs

## Research Summary

### What OpenClaw does (the industry leader)

**OpenClaw's skill format:**
- Each skill = a folder containing `SKILL.md`
- `SKILL.md` = Markdown with YAML frontmatter (metadata) + Markdown body (instructions)
- Skills are loaded at session start → injected into system prompt
- No JSON, no code files — just Markdown with YAML metadata

**Example OpenClaw skill:**
```markdown
---
name: todoist-manager
description: Manage tasks via the Todoist API.
version: 1.0.0
metadata:
  openclaw:
    requires:
      env:
        - TODOIST_API_KEY
      bins:
        - curl
    primaryEnv: TODOIST_API_KEY
emoji: 📋
---

# Todoist Manager

When the user asks to manage tasks:
1. Use `curl` to call the Todoist API
2. Parse the JSON response
3. Return the results in a readable format

## Available actions
- List tasks: `curl -H "Authorization: Bearer $TODOIST_API_KEY" https://api.todoist.com/rest/v2/tasks`
- Create task: `curl -X POST -H "Authorization: Bearer $TODOIST_API_KEY" -d '{"content":"Buy milk"}' https://api.todoist.com/rest/v2/tasks`
```

**Key insight**: OpenClaw skills are NOT code. They're **instructions** that the LLM follows. The LLM reads the SKILL.md, understands what tools are available (curl, API calls), and executes them. The skill is just a "playbook" — the LLM is the executor.

### What Agent Plugins Specification does (vendor-neutral standard)

- Defines a portable package format for AI skills
- Uses `mcp.json` configuration file
- Skills packaged as distributable plugins
- Vendor-neutral — works across different AI agent platforms

### What Google ADK (Agent Development Kit) does

- Google released ADK for Kotlin and Android (v0.1.0)
- Framework for building AI agents on Android
- Multi-agent architecture support
- Relevant to our project — but it's early/unfinished

## My Proposal — Adapted for AI Agent APK

### Two-layer system (inspired by OpenClaw but adapted for Android)

**Layer 1: Skill files (Markdown + YAML frontmatter)**

Instead of JSON with hardcoded step sequences (my original proposal), use OpenClaw's approach: **Markdown instructions that the LLM reads and follows**.

```
assets/skills/
├── morning_briefing.md
├── check_price.md
├── send_whatsapp.md
├── order_food.md
└── device_audit.md
```

Each skill file:
```markdown
---
name: morning-briefing
description: Give a morning briefing with weather, calendar, and battery
trigger: morning briefing|what's my morning|good morning briefing
tools_used: [getCalendarEvents, webSearch, getBatteryLevel, getCurrentTime]
permissions: [calendar, location, internet]
---

# Morning Briefing

When the user asks for a morning briefing:

1. Call `getCurrentTime()` to know what day it is
2. Call `getCalendarEvents()` to get today's events
3. Call `getBatteryLevel()` to check battery
4. Call `webSearch("weather today [user location]")` for weather
5. Combine all results and give a brief spoken summary:
   - "Good morning! Today is [day]. You have [N] events: [list]. 
     Weather is [condition], [temp]°. Battery is [X]%."

Keep the summary under 3 sentences. Speak it aloud via TTS.
```

**Why this is better than JSON step sequences:**
- The LLM can ADAPT if a step fails (unlike hardcoded JSON steps)
- The LLM can skip steps if they're not relevant (e.g., no calendar events)
- The LLM can add steps it thinks are needed
- Easier for users to write/edit (Markdown, not JSON)
- Matches OpenClaw's proven approach

**Layer 2: Plugin manager (dynamic tool loading)**

```
assets/plugins/
├── core.json          (always loaded: screen, browser, device, memory)
├── whatsapp.json      (optional: openChat, sendMessage, readMessages)
├── smart_home.json    (optional: controlLights, setThermostat)
└── custom.json        (user-defined HTTP tools)
```

The system prompt is built dynamically:
```
[Base prompt: core tools + rules + planning — ~1.5K tokens]
[Active plugins: only enabled ones — ~500 tokens per plugin]
[Available skills: names + triggers only — ~50 tokens per skill]
[User message]
```

### How skills work in the agent loop

```kotlin
// When user sends a message:
1. Check if message matches any skill trigger
   - "good morning briefing" → matches morning-briefing.md trigger
2. If match: inject the skill's Markdown body into the conversation
   - LLM reads the instructions and follows them
   - LLM calls tools as instructed by the skill
3. If no match: normal agent loop (LLM decides what to do)

// If LLM calls executeSkill("morning-briefing"):
// 1. Load morning_briefing.md
// 2. Inject the Markdown instructions into the conversation
// 3. LLM follows the instructions, calling tools as needed
// 4. LLM summarizes results when done
```

### UI redesign plan (user-friendly)

**Current UI problems:**
- Everything is in one chat screen (settings, rules, browser, chat)
- No visual feedback for what AI is doing (just text)
- Settings is a long scrollable list
- No way to see/manage skills/plugins
- Browser is a separate screen with no connection to chat

**Proposed new UI:**

```
┌─────────────────────────────────────┐
│  AI Agent                    [☰] [⚙] │  ← Top bar (menu, settings)
├─────────────────────────────────────┤
│                                     │
│  Chat messages...                   │  ← Main chat area
│                                     │
│  [AI is working: browserOpen...]    │  ← Live status (what AI is doing)
│                                     │
├─────────────────────────────────────┤
│  [🎤] [💬 Type message...] [➤]      │  ← Input bar
├─────────────────────────────────────┤
│  [💬 Chat] [🌐 Browser] [📋 Skills]  │  ← Bottom navigation tabs
│  [⚙ Rules] [📊 Usage] [⚙ Settings] │
└─────────────────────────────────────┘
```

**Bottom navigation tabs:**
1. 💬 Chat — main conversation
2. 🌐 Browser — visible in-app browser (from v4.1.0)
3. 📋 Skills — manage installed skills (enable/disable, add custom)
4. ⚙ Rules — manage rules & schedules (from v3.0.0)
5. 📊 Usage — API usage monitor (from v3.3.0)
6. ⚙ Settings — AI provider, browser mode, permissions, battery optimization

**Skills tab UI:**
```
┌─────────────────────────────────────┐
│  Skills & Plugins                    │
├─────────────────────────────────────┤
│  ☑ Morning Briefing        [Edit]   │
│    "Give morning summary"           │
│    Tools: calendar, weather, battery│
│                                     │
│  ☑ Price Check             [Edit]   │
│    "Check prices online"            │
│    Tools: browserOpen, readStructured│
│                                     │
│  ☐ WhatsApp Auto-Reply     [Edit]   │
│    "Auto-reply to messages"         │
│    Tools: openWhatsAppChat, sendSMS │
│                                     │
│  + Add Custom Skill                  │
│    Paste SKILL.md content or URL    │
└─────────────────────────────────────┘
```

### Implementation phases (honest timeline)

**Phase 1 (v4.2.0) — Skill files + dynamic prompt loading**
- Create `SkillManager.kt` — loads `.md` files from `assets/skills/`
- Parse YAML frontmatter (name, description, trigger, tools_used)
- Inject active skills into system prompt
- Add `executeSkill(name)` tool to ToolExecutor
- Build first 3 skills: morning_briefing, check_price, device_audit

**Phase 2 (v4.3.0) — Plugin manager + bottom navigation UI**
- Create `PluginManager.kt` — loads `.json` from `assets/plugins/`
- Build dynamic system prompt from active plugins
- Redesign UI with bottom navigation tabs
- Add Skills management screen

**Phase 3 (v4.4.0) — Custom skills + sharing**
- User can add custom skills (paste Markdown or URL)
- Skills persist in app storage
- Export/import skills as files
- Community skill repository (GitHub-hosted)

**Phase 4 (v5.0.0) — External HTTP skills + marketplace**
- Skills that call HTTP endpoints (like OpenClaw's approach)
- Skill marketplace (download from GitHub)
- Skill versioning + updates

## Questions for the User

1. **Markdown skills (like OpenClaw) vs JSON step sequences?**
   - Markdown: LLM reads instructions, adapts if steps fail, easier to write
   - JSON: Hardcoded steps, runs without LLM, saves API calls but less flexible
   - My recommendation: Markdown (adapts better, matches industry standard)

2. **Bottom navigation tabs — yes or no?**
   - Better UX, but significant UI rewrite
   - My recommendation: yes, but in Phase 2 (not immediately)

3. **First 3 skills to build?**
   - My recommendation: morning_briefing, check_price, send_whatsapp

4. **Should we coordinate with Sandbox-1 on this?**
   - He proposed a similar system in FUTURE_PLANS.md
   - We should agree on the format before building
   - My recommendation: commit this research, let him respond, then build together

## What I need from Sandbox-1

1. Review this research
2. Agree on: Markdown skills (OpenClaw-style) vs JSON steps
3. Decide who builds: SkillManager (me) vs PluginManager (you) vs UI redesign (both)
4. Commit `PING_S2_RESPONSE_2.md` with your thoughts
