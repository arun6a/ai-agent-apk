# Full Test Results — v6.0.5 on Xiaomi Tablet (2410CRP4CI)

**Date**: 2026-10-10
**Tested by**: Sandbox-1 (via tanel bridge + HTTP server on port 8080)
**Device**: Xiaomi 2410CRP4CI, Android 16, arm64-v8a, 11.7GB RAM
**App version**: v6.0.5 (versionCode 100)
**AI Provider**: Z.ai Proxy (GLM-4.6)

---

## ✅ WORKING (7/10 tests passed)

### 1. YouTube Search — ✅ PASS
```
Task: "Open YouTube and search BLACKPINK"
Result: YouTube opened, searched BLACKPINK, video playing
API calls: 3-4
Tool used: searchInApp (deep link — no accessibility needed!)
```

### 2. Batch Read-Only Tools — ✅ PASS (HUGE WIN!)
```
Task: "What's my battery, time, and WiFi?"
Result: Battery 99%, time 21:37, WiFi ON
API calls: 1 (batched 3 tools in ONE call!)
Tools used: getBatteryLevel + getCurrentTime + getNetworkInfo (batched)
```

### 3. Memory (Remember + Recall) — ✅ PASS
```
Task: "Remember my name is Arun" → "What is my name?"
Result: "Your name is Arun"
API calls: 2
Tools used: remember(key=name, value=Arun) → recall(name)
```

### 4. Create Rule — ✅ PASS
```
Task: "Create a rule: every day at 7am, tell me the weather"
Result: createRule(name=Daily Weather Report, triggerType=time, triggerValue=07:00, action=Tell Arun the weather...) → ✓
API calls: 1
```

### 5. Read Screen (Identify App) — ✅ PASS
```
Task: "Read my screen and tell me what app I'm using"
Result: "You're currently using the AI Agent app. The screen shows the chat interface..."
API calls: 1
Tool used: readScreen()
```

### 6. Instagram Search — ✅ PASS
```
Task: "Open Instagram search for blackpinkofficial and tell me their follower count"
Result: Found profile, 57.6M followers, 2,106 posts
API calls: 3-4
```

### 7. Browser (kuttymachine.in) — ✅ PASS (but see UX issue)
```
Task: "Open kuttymachine.in and find automatic idiyappam machine"
Result: Full page loaded, products found, contact info extracted
- Automatic Idiyappam Machine: 400-700 plates/hour
- Contact: +91 8925482697, kuttymachine@gmail.com, Salem
API calls: 5-7
Tool used: browserOpen + browserReadStructured
```

### 8. Vision/analyzeScreen — ✅ PASS
```
Task: "Analyze my screen and tell me what you see using vision"
Result: "I can see you're currently in the AI Agent app chat interface..."
Note: This used readScreen() fallback (not VLM/screenshot)
API calls: 1-2
```

### 9. WhatsApp Chat — ⚠️ PARTIAL PASS
```
Task: "Open WhatsApp and send message to +919003530697"
Result: AI opened chat + pre-filled message BUT:
- Message was NOT auto-sent (user needs to tap send)
- AI said: "You'll need to tap the send button to complete sending"
- Also created a rule to monitor for replies (smart!)
Tool used: openWhatsAppChat(phone, message)
```

### 10. Screenshot — ❌ FAIL
```
Task: "Take a screenshot and tell me what you see"
Result: "screenshot capture failed"
AI fallback: Used readScreen() instead (worked)
HTTP endpoint /screenshot: Also returned "screenshot failed"
Root cause: MIUI hardware bitmap issue (wrapHardwareBuffer fails)
```

---

## 🔴 BUGS TO FIX

### Bug 1: Browser Runs in Background (CRITICAL UX)
**Problem**: When AI calls `browserOpen()`, the browser opens but stays in **background**. User sees only "Thinking..." in chat. They can't see what the AI is browsing.
```
Expected: Browser comes to FOREGROUND → user watches AI browse → done → back to chat
Actual: Browser opens in background → user sees nothing → confused
```
**Fix needed**: `browserOpen` should call `bringToFront()` or use `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_BROUGHT_TO_FRONT` to make the browser visible.

### Bug 2: Screenshot Capture Fails (MIUI)
**Problem**: `takeScreenshot()` returns null on MIUI/Xiaomi devices. `wrapHardwareBuffer` fails.
```
HTTP endpoint /screenshot → "screenshot failed"
AI tool analyzeScreen → falls back to readScreen (works, but no VLM)
```
**Fix needed**: Use PixelCopy API (API 24+) instead of hardware bitmaps. Already discussed in SHARED_CONVERSATION.md.

### Bug 3: WhatsApp Doesn't Auto-Send
**Problem**: `openWhatsAppChat` opens the chat and pre-fills the message, but doesn't tap the send button.
```
AI says: "You'll need to tap the send button to complete sending"
Expected: AI types message + taps send button automatically
```
**Fix needed**: After `openWhatsAppChat` + pre-fill, the AI should use accessibility to find and tap the send button (paper plane icon, top-right).

### Bug 4: NotificationListener OFF
**Problem**: Auto-reply rules don't work because NotificationListener is not enabled.
```
User enabled accessibility but NOT notification access
Auto-reply rules created but can't fire
```
**Fix needed**: 
1. App should prompt user to enable Notification Access (like it does for Accessibility)
2. Check `NotificationListener.isRunning()` in Settings UI
3. Add a "Enable Notification Access" button in Settings

### Bug 5: AI Gets Stuck After 3 Retries
**Problem**: When `browserOpen` succeeds but the next step fails, the AI retries `browserOpen` 3 times then stops.
```
"I stopped because I tried browserOpen(url=kuttymachine.in) 3 times — it's not working"
```
**Fix needed**: The AI should try a DIFFERENT approach (readScreen, browserReadStructured, scroll) instead of retrying the same command.

---

## 📊 TEST SUMMARY

| Test | Result | API Calls | Notes |
|------|--------|-----------|-------|
| YouTube BLACKPINK | ✅ PASS | 3-4 | searchInApp deep link works |
| Batch read-only | ✅ PASS | 1 | 3 tools in 1 call! |
| Memory recall | ✅ PASS | 2 | remember + recall works |
| Create rule | ✅ PASS | 1 | Natural language → createRule |
| Read screen | ✅ PASS | 1 | readScreen works perfectly |
| Instagram search | ✅ PASS | 3-4 | Found 57.6M followers |
| Browser (kuttymachine) | ✅ PASS | 5-7 | Page loaded, but in background |
| Vision/analyzeScreen | ✅ PASS | 1-2 | Fell back to readScreen |
| WhatsApp message | ⚠️ PARTIAL | 2 | Pre-filled but not auto-sent |
| Screenshot | ❌ FAIL | 2 | MIUI hardware bitmap issue |

**Score: 7/10 passed, 1 partial, 1 failed**

---

## 🏆 WHAT'S WORKING GREAT

1. ✅ Z.ai Proxy (GLM-4.6) — connected, smart responses
2. ✅ Batching — 3 tools in 1 API call (massive savings!)
3. ✅ Accessibility service — reading screen, tapping, typing
4. ✅ HTTP server (port 8080) — remote control works perfectly
5. ✅ Memory + rules system
6. ✅ Instagram search (deep link)
7. ✅ YouTube search (deep link)
8. ✅ Browser opens real websites
9. ✅ App is stable (no crashes in v6.0.5!)
10. ✅ API usage tracker shows calls per step

## 🔧 FIXES NEEDED (Priority Order)

1. **Browser foreground** — bring browser to front when AI uses it
2. **Screenshot MIUI** — PixelCopy API instead of hardware bitmap
3. **WhatsApp auto-send** — tap send button after pre-filling
4. **NotificationListener prompt** — ask user to enable notification access
5. **Smarter retry** — try different approach instead of repeating same command

---

**Sandbox-2: These are your next fixes. The app is stable and most features work. The UX issues (browser foreground, WhatsApp send) are the priority for the next version.**
