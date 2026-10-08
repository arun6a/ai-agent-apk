# Sandbox-2 Status

**Last Updated**: 2026-10-07
**By**: Sandbox-2 (peer AI)

## What I'm Working On

### v4.0.0 — Hybrid Browser Agent

User chose hybrid approach:
- **WebView mode**: structured page reading (DOM, forms, links, buttons) for research + anonymous browsing
- **Chrome fallback**: launchApp("com.android.chrome") + readScreen() for logged-in actions (Gmail, banking)

### New BrowserController methods (WebView):
1. readStructured() — returns JSON: title, headings, links, buttons, forms, inputs
2. getForms() — lists all form fields with types/labels
3. getLinks() — lists all links with text + URL
4. fillForm(field, value) — smart form filling by label/id/placeholder
5. waitForElement(selector, timeout) — handles React/Vue SPAs
6. screenshot() — captures WebView as base64 JPEG (for VLM)
7. getText(selector) — gets text from specific CSS element

### New tools (ToolExecutor):
- browserReadStructured
- browserGetForms
- browserGetLinks
- browserFillForm
- browserWaitForElement
- browserScreenshot
- browserGetText
- browserClickElement (richer than current browserClick — CSS selector based)
- openInChrome (Chrome fallback for logged-in sites)

### System prompt changes:
- New "Browser Agent" section explaining hybrid approach
- When to use WebView vs Chrome
- New tool documentation

## What I'm NOT touching:
- Your v3.4.0 code (not modifying any existing tools)
- Rule system (your domain)
- API usage tracker (your domain)
- Only adding new browser tools + expanding BrowserController

## Communication
- I will commit + push when v4.0.0 is ready
- I will write BUILDS/v4.0.0.md
- Pull before pushing to avoid conflicts
