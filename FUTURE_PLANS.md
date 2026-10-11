# Future Plans — Updated v6.3.2

**Date**: 2026-10-11
**Updated by**: Sandbox-2
**Previous version**: FUTURE_PLANS.md (by Sandbox-1, v4.0.1)

---

## What's Been Accomplished (v4.0 → v6.3.2)

Since the original FUTURE_PLANS.md, we've shipped:

- ✅ Skills system (20 built-in + user-created via chat)
- ✅ Plugin system (10 plugins)
- ✅ Token optimization (dynamic prompt, 5 modes)
- ✅ 36 trigger types (was 16)
- ✅ 12 AI providers (was 6)
- ✅ File attachments + knowledge store
- ✅ Hub activity (13 sections)
- ✅ Strategy switching + always learn
- ✅ HTTP server for remote control
- ✅ Browser ↔ Chat quick switch
- ✅ Google Voice input
- ✅ Overlay fix (no duplicates)
- ✅ App folders on install

---

## Updated Roadmap

### Phase 1: Stabilization (v6.4.0) — NEXT

| Feature | Priority | Status |
|---|---|---|
| Screenshot MIUI fix (PixelCopy/MediaProjection) | 🔴 Critical | Not started |
| Timeout system (30s/step, 3min/task) | 🔴 High | Not started |
| Ask user for help (after 3 failures) | 🟡 Medium | Not started |
| Browser stays foreground (all steps visible) | 🟡 Medium | Not started |
| Stop button polish | 🟢 Low | Partially done |
| Empty catch blocks logging | 🟢 Low | Not started |

### Phase 2: Developer AI Agent (v7.0.0)

**Vision**: The AI becomes a coding assistant that can write + deploy code on the device.

| Feature | Description |
|---|---|
| **Code execution** | AI writes Python/JavaScript code + runs it on-device (via Termux or embedded interpreter) |
| **Code generation** | "Write a script that checks my battery every hour and logs it" → AI writes + saves + creates a rule |
| **APK building** | AI can modify its own code, rebuild APK, and install (meta-programming) |
| **Termux integration** | Full Linux environment — Python, Node.js, Git, etc. |
| **GitHub integration** | AI can push code, create PRs, manage issues from the phone |
| **Debug mode** | AI reads its own logcat, identifies bugs, suggests fixes |
| **Skill compiler** | AI writes new skills in natural language → compiles to .md files |
| **Plugin builder** | AI creates new plugin JSON files based on user needs |
| **Auto-testing** | AI tests its own tools + reports failures |

**Example flows:**
```
User: "Write a Python script that checks my battery every hour and saves to a file"
AI: 
  1. Writes Python script to /storage/.../scripts/battery_log.py
  2. Creates a rule: every 1 hour → runShellCommand("python battery_log.py")
  3. Reply: "Script created + scheduled. It will log battery every hour."

User: "Check your own logs for any errors"
AI:
  1. runShellCommand("logcat -d *:E -s AgentHttpServer:V MainActivity:V")
  2. Analyzes errors
  3. Reply: "Found 3 errors. 1. Screenshot fails on MIUI. 2. Empty catch in WiFiReceiver. 3. ..."

User: "Create a new skill for checking train schedules"
AI:
  1. createSkill("train-schedule", "Check train schedules", "train|irctc|railway", "1. webSearch...")
  2. Reply: "Skill 'train-schedule' created!"
```

### Phase 3: Image & Video Editing (v7.5.0)

**Vision**: AI can edit photos + videos on-device.

| Feature | Description |
|---|---|
| **Image editing** | Crop, resize, rotate, filters, text overlay — via on-device libraries |
| **Image generation** | AI generates images (Stable Diffusion on-device, or cloud API) |
| **Video editing** | Trim, merge, add music, subtitles, transitions |
| **Screenshot annotation** | AI captures screenshot + annotates (arrows, highlights, text) |
| **Photo enhancement** | Auto-enhance brightness, contrast, noise reduction |
| **Batch processing** | "Resize all photos in this folder to 1080p" |
| **Meme generator** | AI creates memes from templates + user text |
| **Collage maker** | AI combines multiple photos into a collage |
| **QR code generator** | Generate QR codes from text/URLs |
| **Barcode scanner** | Scan barcodes + look up products |

**Example flows:**
```
User: [attaches photo] "Crop this to square and add a filter"
AI:
  1. readAttachment("photo.jpg")
  2. runShellCommand("ffmpeg -i photo.jpg -vf crop=1080:1080:0:0,scale=1080:1080 photo_square.jpg")
  3. Reply: "Done! Cropped to 1080x1080. Saved as photo_square.jpg"

User: "Generate an image of a sunset over mountains"
AI:
  1. Calls image generation API (or on-device Stable Diffusion)
  2. Saves to /storage/.../attachments/sunset_mountains.png
  3. Reply: "Generated sunset image! Saved to attachments."

User: "Make a meme with this photo and text 'When the code works'"
AI:
  1. readAttachment("photo.jpg")
  2. Adds text overlay at top + bottom
  3. Saves as meme.jpg
  4. Reply: "Meme created! Want me to share it?"
```

### Phase 4: Video Generation (v8.0.0)

**Vision**: AI generates short videos.

| Feature | Description |
|---|---|
| **Text-to-video** | "Create a 10-second video of waves on a beach" → AI generates via API |
| **Photo-to-video** | Animate a static photo (ken burns effect, zoom, pan) |
| **Slideshow video** | Create slideshow from photos + music |
| **GIF maker** | Create animated GIFs from video clips |
| **Screen recording** | Record screen + narrate (AI speaks while recording) |
| **Video transcription** | Transcribe video audio to text |
| **Video translation** | Translate video subtitles to another language |
| **Video compression** | Reduce video file size without quality loss |
| **Video format conversion** | Convert MKV→MP4, WEBM→MP4, etc. |

### Phase 5: Advanced AI (v9.0.0)

| Feature | Description |
|---|---|
| **Anthropic prompt caching** | 90% token savings |
| **Multi-model routing** | Groq for simple, Claude for complex reasoning |
| **Local LLM** | On-device Llama 3.2 for offline tasks |
| **Streaming responses** | AI replies stream in real-time |
| **Voice conversation** | Continuous voice mode |
| **Multi-turn context** | Full conversation memory (not just last 2 messages) |
| **Proactive AI** | AI suggests actions before user asks |
| **Context awareness** | AI knows location, time, calendar, recent apps |

### Phase 6: UI/UX Polish (v7.0.0 — parallel with Developer Agent)

| Feature | Description |
|---|---|
| Proper XML layouts (replace programmatic UI) | |
| Bottom navigation (5 tabs) | |
| Status orb (animated agent state) | |
| Live status card (per-step progress) | |
| Suggestion chips | |
| Onboarding wizard | |
| Markdown rendering | |
| Dark/Light theme | |

### Phase 7: Ecosystem (v10.0.0)

| Feature | Description |
|---|---|
| Telegram bot (remote control via Telegram) | |
| Web dashboard (browser-based control panel) | |
| API server (other apps call AI Agent) | |
| Tasker integration | |
| Smart home control (IoT devices) | |
| Auto-pilot mode (AI monitors + acts autonomously) | |
| Skill marketplace (share + download skills) | |
| Plugin marketplace (download new tool plugins) | |
| Workflow recorder (record actions → create skill) | |
| Multi-device sync (sync memory/rules across phones) | |

---

## Old Plans vs New Plans

| Old Plan (Sandbox-1) | Status | Updated Plan |
|---|---|---|
| Token optimization (4K→1.5K) | ✅ DONE | Dynamic prompt (11K→750 tokens in Fast mode) |
| Skills system (JSON + Kotlin) | ✅ DONE | 20 built-in skills + user-created via chat |
| Plugin system | ✅ DONE | 10 plugins |
| Marketplace | 🔄 Future | Skill + Plugin marketplace (v10.0.0) |
| Workflow recorder | 🔄 Future | Record actions → create skill (v10.0.0) |
| Telegram bot | 🔄 Future | Remote control via Telegram (v10.0.0) |
| Developer platform | ❌ Changed | Not a platform — it's a personal assistant that CAN code (v7.0.0) |

---

## What the User Wants Next?

Based on our conversations:

1. **Screenshot MIUI fix** — the #1 remaining bug
2. **UI polish** — proper layouts, not programmatic
3. **Developer agent** — AI can write + run code
4. **Image/video editing** — on-device media processing
5. **Image/video generation** — AI creates visual content

---

## Priority Order (My Recommendation)

```
v6.4.0 — Bug fixes (screenshot, timeout, browser foreground)
v7.0.0 — Developer AI Agent + UI polish (parallel)
v7.5.0 — Image editing + generation
v8.0.0 — Video editing + generation + streaming AI
v9.0.0 — Advanced AI (caching, multi-model, local LLM)
v10.0.0 — Ecosystem (Telegram, web dashboard, marketplace)
```

— Sandbox-2
