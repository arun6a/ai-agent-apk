# AI Agent APK

An Android app that turns your phone into an AI-powered autonomous agent. Built with Kotlin, runs as a foreground service, controls the phone via Accessibility, and uses cloud AI (Z.ai API) for reasoning + vision.

## Current Version: v1.4.1

### 70+ Tools Available

| Category | Tools |
|----------|-------|
| **Screen Control** | readScreen, tap, clickByText, type, swipe, scrollDown/Up, pressBack/Home/Enter, submitInput |
| **App Management** | launchApp, listInstalledApps, getAppInfo, forceStopApp, uninstallApp |
| **Vision (VLM)** | analyzeScreen, findElement, takeScreenshotToGallery |
| **Memory** | remember, recall, recallAll |
| **Terminal** | runShellCommand |
| **Contacts** | readContacts, searchContacts, callContact |
| **Phone & SMS** | sendSMS, getCallLog, sendEmail, shareText |
| **Calendar** | getCalendarEvents, createCalendarEvent, setAlarm, setTimer |
| **Location** | getCurrentLocation, openMaps |
| **Device Control** | getBatteryLevel, getVolume, setVolume, setBrightness, toggleFlashlight, lockScreen, getBluetoothState, getNetworkInfo, getDeviceInfo |
| **Media** | mediaPlayPause, mediaNext, mediaPrevious |
| **Files** | listFiles, readFile, writeFile, copyFile, moveFile, deleteFile, createDirectory |
| **Web** | webSearch, makeHttpRequest, downloadFile, openUrl |
| **Clipboard** | getClipboard, setClipboard |
| **System** | getCurrentTime, pingHost, translateText |
| **Camera** | takePhoto |

### Features
- ✅ Chat UI with dark theme
- ✅ Voice input (STT) + output (TTS) — English + Tamil
- ✅ Agent loop (verify + retry, up to 25 iterations)
- ✅ Floating overlay button (tap=read screen, long-press=voice, drag=move)
- ✅ Persistent memory (SQLite)
- ✅ Rules engine (time-based + notification triggers)
- ✅ Settings UI
- ✅ Task interruption (stop button)
- ✅ Chat history persistence
- ✅ LLM proxy on sandbox (Z.ai GLM-4.6)
- ✅ VLM proxy on sandbox (Z.ai GLM-4V for vision)
- ✅ Web search proxy on sandbox

## Architecture

```
Phone APK ←→ Sandbox (Next.js proxy) ←→ Z.ai API (LLM + VLM)
```

The phone calls the sandbox's public URL, which proxies to Z.ai's internal API with proper auth.

## Build

```bash
export JAVA_HOME=~/jdk-17.0.13+11
export ANDROID_HOME=~/android-sdk
cd /home/z/my-project/ai-agent-apk
./gradlew assembleDebug --no-daemon
```

## GitHub
https://github.com/arun6a/ai-agent-apk

## License
MIT
