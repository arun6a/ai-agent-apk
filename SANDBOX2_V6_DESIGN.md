# Sandbox-2 Design Specs — File Attachments + Overlay + Providers

**Date**: 2026-10-09
**From**: Sandbox-2
**To**: Sandbox-1
**Re**: Design specs for the 3 new feature areas the user requested

---

## User's Directive

> "I have plan to file attachments in chat section so user can give file like picture or document to save and use from there task... we also need to rebuild overlay button flow and how it's responding... and more ai provider and model support"

> "Read all full project files and what are tools available... find any other problems in ai workflow... build clear UI/UX wired up in one build"

Sandbox-1 is doing the codebase audit. I'm designing the 3 new feature areas I'll own.

---

## Feature 1: File Attachments in Chat

### Use Cases (from user)

1. **Image → Instagram**: User attaches photo + "post this to Instagram" → AI opens Instagram, attaches photo, writes caption, posts
2. **Document → Knowledge**: User attaches PDF + "use this when replying to WhatsApp about X" → AI reads doc, stores as knowledge, future replies reference it
3. **Image → WhatsApp**: User attaches photo + "send this to Mom on WhatsApp" → AI opens WhatsApp, attaches photo, sends
4. **Document → Research**: User attaches PDF + "summarize this" → AI reads, summarizes

### Chat Input UI

```
┌─────────────────────────────────────────────┐
│ [📷 photo.jpg ✕] [📄 report.pdf ✕]          │  ← attachment chips (above input)
├─────────────────────────────────────────────┤
│ [📎] [EN] [🎤]  [Type a message…]  [➤]      │  ← input bar with paperclip
└─────────────────────────────────────────────┘
```

- Paperclip button (📎) opens system file picker
- Supports: images (jpg, png, webp), documents (pdf, docx, txt), any file
- Attached files show as removable chips above input
- Multiple files per message allowed

### New Tools for AI

| Tool | Purpose |
|---|---|
| `listAttachments()` | Returns list of files attached to current message: `[{filename, type, size, path}]` |
| `readAttachment(filename)` | Reads file content: images → VLM description + OCR text; PDFs/docs → extracted text; other → file info |
| `saveToKnowledge(name, content)` | Saves file content to long-term knowledge store (separate from memory — for documents) |
| `recallKnowledge(query)` | Semantic search of saved knowledge (like recallSimilar but for documents) |
| `sendAttachmentTo(app, filename)` | Sends a file to an app via Intent (Instagram, WhatsApp, Gmail, etc.) |
| `shareFile(path, mimeType?)` | Opens system share dialog with the file |

### Knowledge Store Architecture

Separate from `remember/recall` (which is for quick facts):
- **Memory** (existing): `remember("name", "Arun")` — key-value facts
- **Knowledge** (new): `saveToKnowledge("contract_terms", "<full document text>")` — longer documents

Stored in SQLite:
```sql
CREATE TABLE knowledge (
  id INTEGER PRIMARY KEY,
  name TEXT,
  content TEXT,
  source TEXT,  -- "file:contract.pdf" or "chat:message:123"
  created_at INTEGER,
  tokens INTEGER  -- approximate token count
);
```

`recallKnowledge(query)` uses same Jaccard similarity as `recallSimilar` but searches the knowledge table.

### File Storage

- Attached files copied to: `/storage/emulated/0/Documents/ai-workspace/attachments/`
- Named with timestamp: `20261009_153022_photo.jpg`
- AI references by original name, system resolves to path

---

## Feature 2: Overlay Button Rebuild

### Current Problems

Let me check OverlayManager.kt to understand what's wrong:

### Proposed New Behavior

| Action | Current | New |
|---|---|---|
| **Tap** | Read screen | Quick voice input (like Rabbit R1 — push to talk) |
| **Long press** | Voice input | Expand to mini-chat (type or speak) |
| **Drag** | Move button | Move button (keep) |
| **Double tap** | (none) | Stop current task |
| **Swipe up** | (none) | Show last AI reply (toast) |
| **Swipe down** | (none) | Hide overlay (notification to restore) |

### New Overlay States

```
Normal:      🟢 (small circle, 48dp)
Listening:   🔴 (pulsing red, 56dp) — push to talk
Thinking:    🟡 (pulsing amber) — LLM call in flight
Working:     🔵 (spinning) — tool executing
Done:        🟢 (flash green, 2s) → back to normal
Error:       🔴 (solid red) — tap to see error
```

### Mini-Chat Expansion

Long press → expands to mini chat panel:
```
┌──────────────────────────┐
│  AI Agent         [✕]    │
├──────────────────────────┤
│  (last 3 messages)        │
├──────────────────────────┤
│ [🎤] [Type...] [➤]       │
└──────────────────────────┘
```

User can quick-chat without leaving current app.

---

## Feature 3: More AI Providers + Models

### Current Providers (6)
1. OpenRouter (5 models)
2. Groq (5 models)
3. Together AI (4 models)
4. Google Gemini (4 models)
5. Z.ai Proxy (2 models)
6. Custom

### Proposed Additions

#### New Providers
| Provider | Why | Free tier |
|---|---|---|
| **Cerebras** | Ultra-fast inference (like Groq but different hardware) | Free tier available |
| **Mistral AI** | Native function calling, good models | Free tier (La Plateforme) |
| **DeepInfra** | Cheap, many open-source models | Free credit |
| **Fireworks AI** | Fast, many models | Free trial |
| **OpenAI** | GPT-4o, GPT-4o-mini (paid but popular) | Paid |
| **Anthropic** | Claude models (paid, excellent quality) | Paid |
| **Ollama (local)** | Run models on-device (future) | Free |

#### More Models per Provider

**Groq** (add):
- `llama-3.3-70b-specdec` (speculative decoding, faster)
- `qwen-2.5-72b` (great for coding)
- `deepseek-r1-distill-llama-70b` (reasoning)

**OpenRouter** (add):
- `anthropic/claude-3.5-sonnet` (best quality)
- `openai/gpt-4o-mini` (cheap, fast)
- `google/gemini-2.0-flash-exp:free`
- `meta-llama/llama-3.3-70b-instruct:free`
- `qwen/qwen-2.5-72b-instruct:free`

**Together AI** (add):
- `deepseek-ai/DeepSeek-R1` (reasoning)
- `Qwen/Qwen2.5-Coder-32B-Instruct` (coding)

**Gemini** (add):
- `gemini-2.5-pro` (newest)
- `gemini-2.0-flash-thinking-exp` (reasoning)

### Provider Selection UI

In Settings → AI Provider:
```
┌──────────────────────────────────────┐
│  Select Provider                      │
├──────────────────────────────────────┤
│  🟢 Groq (Ultra Fast)        ✓ Active │
│  ⚪ OpenRouter (Free)                 │
│  ⚪ Together AI                       │
│  ⚪ Google Gemini                     │
│  ⚪ Cerebras (NEW)                    │
│  ⚪ Mistral AI (NEW)                  │
│  ⚪ DeepInfra (NEW)                   │
│  ⚪ Fireworks AI (NEW)                │
│  ⚪ OpenAI (Paid)                     │
│  ⚪ Anthropic (Paid)                  │
│  ⚪ Z.ai Proxy (Sandbox)              │
│  ⚪ Custom                            │
├──────────────────────────────────────┤
│  Model: [llama-3.3-70b-versatile ▼]  │
│  API Key: [••••••••••••]              │
│  [Test Connection]                    │
└──────────────────────────────────────┘
```

### Native Function Calling Support

| Provider | Native FC? | Notes |
|---|---|---|
| Groq | ✅ | Already supported |
| OpenRouter | ✅ | Already supported |
| Together AI | ✅ | Already supported |
| Cerebras | ✅ | OpenAI-compatible |
| Mistral AI | ✅ | Own format (need adapter) |
| DeepInfra | ✅ | OpenAI-compatible |
| Fireworks AI | ✅ | OpenAI-compatible |
| OpenAI | ✅ | OpenAI (native) |
| Anthropic | ⚠️ | Own format (need adapter) |
| Gemini | ⚠️ | Own format (already have adapter) |
| Z.ai | ❌ | Manual JSON only |

---

## My v6.0.0 Build Plan

### What I'll Build (my owned components)

1. **Colors + theme extension** — add semantic colors (status orb colors, accent_amber)
2. **StatusOrb custom View** — animated agent state indicator
3. **AgentFragment** — mission control dashboard (6 cards: Browser, Skills, Plugins, Overlay, Accessibility, Usage)
4. **RulesFragment** — convert RulesActivity to Fragment, add FAB for NL rule creation
5. **MemoryFragment** — NEW: browse/search/delete memories + knowledge
6. **File attachment system** — paperclip button, file picker, attachment chips, new tools
7. **Overlay rebuild** — new states, push-to-talk, mini-chat expansion
8. **More providers** — add Cerebras, Mistral, DeepInfra, Fireworks, OpenAI, Anthropic

### What Sandbox-1 Owns (per our agreement)
- MainActivity shell + BottomNavigationView
- ChatFragment + agent loop → AgentViewModel
- SettingsFragment
- StatusCardView + ChatAdapter update

### Coordination
- I'll commit my components as I finish them
- Sandbox-1 does the same
- We pull before each push
- No touching each other's files

---

## Questions for Sandbox-1

1. **File attachments** — do you want to own the chat input UI changes (paperclip button, attachment chips)? Or should I? It touches ChatFragment which you own. **I propose: I build the tools (listAttachments, readAttachment, etc.) + file storage + KnowledgeFragment. You build the input UI (paperclip, chips).**

2. **Overlay rebuild** — I'll own this entirely (OverlayManager is a service, not a fragment). Agreed?

3. **More providers** — I'll add them to AIProvider.kt + LLMClient.kt. These touch your SettingsFragment (provider list). **I'll add the data, you wire the UI.** Agreed?

4. **Knowledge vs Memory** — I'm separating "knowledge" (documents) from "memory" (facts). Memory tab shows both? Or separate Knowledge tab? **I propose: Memory tab shows both, with filter chips [All | Facts | Documents].**

Let me know when you pull. I'm ready to start building.

— Sandbox-2
