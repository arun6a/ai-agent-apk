# Full Codebase Audit — v5.1.1

**Date**: 2026-10-09
**By**: Sandbox-1 (original AI)
**Version audited**: v5.1.1 (versionCode 94)

---

## Codebase Summary

| Metric | Value |
|--------|-------|
| **Total files** | 122 |
| **Kotlin/Java files** | 36 |
| **Total lines of code** | 9,858 |
| **Largest file** | ToolExecutor.kt (2,238 lines) |
| **System prompt** | 44,311 bytes (~11,077 tokens!) |
| **Permissions** | 38 |
| **Tools** | 118 |
| **Plugins** | 6 |
| **Skills** | 3 |
| **Dependencies** | 11 |

---

## Critical Issues (Must Fix)

### 1. 🔴 System Prompt is 11K tokens (was 4K!)
**File**: `app/src/main/assets/system_prompt.txt`
**Problem**: Grew from 4K (v3.4.0) → 11K tokens (v5.1.1). This is HUGE.
- Every API call sends 11K tokens just for the system prompt
- On Groq (500K/day): only 45 calls before token limit
- On Z.ai (300/day): OK, but wasteful
**Fix**: Trim back to ~3-4K tokens. Remove duplicate examples, consolidate sections.

### 2. 🔴 Hardcoded Sandbox URL (WRONG sandbox!)
**File**: `AIProvider.kt` line 107, 190
**Problem**: Points to `preview-chat-1855dd56-...space-z.ai` — this is a DIFFERENT sandbox URL (sandbox-2's), not the original.
**Impact**: Z.ai proxy provider won't work for users who had the old URL.
**Fix**: Make it configurable (already is via Settings, but default is wrong).

### 3. 🔴 ToolExecutor.kt is 2,238 lines (monolith)
**Problem**: Single file with 118 tools. Hard to maintain, hard to test, hard to review.
**Fix**: Split into categories (already planned via Plugins/Skills system, but not wired yet).

### 4. 🔴 ModelDownloaderActivity still in manifest
**File**: `AndroidManifest.xml`
**Problem**: References `ModelDownloaderActivity` which was REMOVED in v1.9.0 (local LLM deleted).
**Fix**: Remove from manifest.

---

## Warnings (Should Fix)

### 5. 🟡 Empty catch blocks (10 found)
**Files**: AgentAccessibilityService, WiFiReceiver, NotificationListener, BootReceiver, RuleWorker, IncomingCallReceiver
**Problem**: `catch (e: Exception) { }` — swallows errors silently
**Fix**: Add logging: `Log.e(TAG, "Error", e)` at minimum

### 6. 🟡 Large methods (>100 lines)
**Files**:
- `MainActivity.runAgentLoop()` — 200+ lines
- `LLMClient.chat()` — 100+ lines  
- `SettingsActivity.onCreate()` — 400+ lines
- `ToolExecutor.executeTool()` — 1900+ lines (the when block)
**Fix**: Break into smaller methods. Especially executeTool — should use plugin/skill routing.

### 7. 🟡 Deprecated API usage
**Files**: BrowserActivity.kt, VoiceInputActivity.kt
**Problem**: Using deprecated Android APIs
**Fix**: Update to current APIs

### 8. 🟡 38 permissions (a lot)
**Problem**: Some may be unnecessary or could be requested on-demand
**Fix**: Audit each permission — remove unused ones

### 9. 🟡 No unit tests
**Problem**: Zero test files
**Fix**: Add basic tests for ToolExecutor, LLMClient, AgentDatabase

### 10. 🟡 PluginManager not wired into LLMClient
**Problem**: PluginManager exists but `getEnabledPluginsPrompt()` is not called in `LLMClient.chat()`
**Fix**: Wire it in (sandbox-2 may have done this in v5.x — need to verify)

---

## Good Things (Working Well) ✅

### Architecture
- Clean separation: llm/ tools/ rules/ service/ browser/ ui/ storage/ skills/ ml/
- Multi-provider support (6 providers)
- Modular: Plugins + Skills system built
- API usage tracking
- ML Kit for offline OCR

### Code Quality
- Consistent naming conventions
- Good use of data classes
- SharedPreferences for config
- SQLite for persistence
- Coroutines for async

### Features
- 118 tools (impressive!)
- 16 rule triggers
- Hybrid browser (WebView + Chrome)
- Vector memory (recallSimilar)
- Notification reply
- WorkManager for reliable rules
- Direct web search (no proxy needed)

---

## Token Usage Analysis

| Component | Tokens | % of total |
|-----------|--------|-----------|
| System prompt | 11,077 | 70% |
| Conversation history | ~2,000 | 13% |
| Tool results | ~2,000 | 13% |
| User message | ~200 | 4% |
| **Total per call** | ~15,277 | 100% |

**Problem**: System prompt dominates (70%). Should be <40%.

**On Groq (500K/day)**:
- 500,000 / 15,277 = **32 calls/day** (terrible!)
- If prompt trimmed to 3K: 500,000 / 7,277 = **68 calls/day** (2x better)

---

## Recommendations for v6.0.0

### Must Do (Before UI rebuild)
1. **Trim system prompt** from 11K → 3-4K tokens
2. **Fix hardcoded URL** in AIProvider.kt
3. **Remove ModelDownloaderActivity** from manifest
4. **Wire PluginManager** into LLMClient (if not done)

### Should Do (During v6.0.0)
5. **Split ToolExecutor** into category files (or use plugin routing)
6. **Add logging** to empty catch blocks
7. **Add basic tests** for core components
8. **Audit permissions** — remove unused

### Nice to Have (After v6.0.0)
9. **Refactor large methods** (especially executeTool)
10. **Update deprecated APIs**
11. **Add ProGuard rules** for smaller APK
12. **Add CI/CD** (GitHub Actions)

---

## File-by-File Assessment

| File | Lines | Status | Notes |
|------|-------|--------|-------|
| ToolExecutor.kt | 2,238 | ⚠️ Too large | Split or use plugin routing |
| MainActivity.kt | 811 | ⚠️ Large | runAgentLoop should be in ViewModel |
| BrowserController.kt | 747 | ✅ OK | Well-structured |
| OverlayManager.kt | 619 | ✅ OK | Complex but necessary |
| LLMClient.kt | 531 | ✅ OK | Multi-provider, good error handling |
| SettingsActivity.kt | 401 | ⚠️ Large | onCreate too long, needs sections |
| AgentService.kt | 381 | ✅ OK | Clean service |
| AIProviderSettingsActivity.kt | 359 | ✅ OK | Good UI code |
| AgentAccessibilityService.kt | 318 | ✅ OK | Core functionality |
| BrowserActivity.kt | 307 | ✅ OK | Will become Fragment |
| AgentDatabase.kt | 301 | ✅ OK | Good SQLite wrapper |
| RulesActivity.kt | 287 | ✅ OK | Will become Fragment |
| AIProvider.kt | 244 | ⚠️ Hardcoded URL | Fix default endpoint |
| PluginManager.kt | 234 | ✅ OK | Built by me, clean |
| RuleScheduler.kt | 183 | ✅ OK | AlarmManager integration |
| ApiUsageTracker.kt | 170 | ✅ OK | Built by me, works well |
| NotificationListener.kt | 155 | ✅ OK | Notification reply works |
| SkillManager.kt | 151 | ✅ OK | Clean, loads .md files |
| IncomingCallReceiver.kt | 145 | ✅ OK | Call detection |
| MLKitHelper.kt | ~100 | ✅ OK | On-device OCR |
| Other receivers (7 files) | ~100 each | ✅ OK | Clean, focused |

---

## Summary

The codebase is **healthy but needs optimization**:
- ✅ Architecture is sound
- ✅ Features are impressive (118 tools!)
- ✅ Code quality is decent
- 🔴 System prompt is WAY too big (11K tokens — trim to 4K)
- 🔴 ToolExecutor needs splitting (2,238 lines)
- 🟡 Some technical debt (empty catches, deprecated APIs, no tests)

**Priority for v6.0.0**: Trim system prompt FIRST, then do the UI rebuild. The 11K token prompt is the #1 problem right now.
