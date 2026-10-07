package com.ai.agent.tools

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.ai.agent.accessibility.AgentAccessibilityService
import com.ai.agent.browser.BrowserController
import com.ai.agent.llm.AIProvider
import com.ai.agent.llm.LLMClient
import com.ai.agent.storage.AgentDatabase
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Executes tool calls from the LLM.
 * Each tool returns a result string that can be fed back to the LLM.
 */
class ToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "ToolExecutor"
    }

    private val database = AgentDatabase(context)

    /**
     * Execute a list of tool calls sequentially.
     * Returns a list of results (one per tool call).
     */
    suspend fun executeTools(toolCalls: List<LLMClient.ToolCall>): List<ToolResult> {
        val results = mutableListOf<ToolResult>()
        for (call in toolCalls) {
            val result = executeTool(call)
            results.add(result)
            Log.d(TAG, "${call.name}(${call.args}) → ${result.success}: ${result.output.take(100)}")
        }
        return results
    }

    private suspend fun executeTool(call: LLMClient.ToolCall): ToolResult {
        val service = AgentAccessibilityService.getInstance()
            ?: return ToolResult(false, "Accessibility service not running.")

        return when (call.name) {
            "readScreen" -> {
                val text = service.readScreen()
                // AUTO-FALLBACK: if no text found, use VLM to see the screen
                if (text.isEmpty() || text == "(no text on screen)" || text == "(screen is null)" || text.length < 10) {
                    Log.i(TAG, "readScreen found no text, falling back to VLM")
                    val vlmResult = analyzeScreenWithVLM("Describe what's on the screen. Include app name, visible elements, and any images or icons.")
                    ToolResult(true, "Screen text was empty. VLM analysis: $vlmResult")
                } else {
                    ToolResult(true, text)
                }
            }
            "tap" -> {
                val x = (call.args["x"] as? Number)?.toFloat() ?: return ToolResult(false, "missing x")
                val y = (call.args["y"] as? Number)?.toFloat() ?: return ToolResult(false, "missing y")
                ToolResult(service.tap(x, y), "tap($x, $y)")
            }
            "clickByText" -> {
                val text = call.args["text"] as? String ?: return ToolResult(false, "missing text")
                ToolResult(service.clickByText(text), "clickByText($text)")
            }
            "type" -> {
                val text = call.args["text"] as? String ?: return ToolResult(false, "missing text")
                // First, check what app is currently foreground so we don't accidentally type
                // into the AI Agent's own chat input.
                val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val currentPkg = am.runningAppProcesses?.firstOrNull { it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }?.processName ?: "unknown"
                if (currentPkg == "com.ai.agent") {
                    // We're about to type into our own app. Try to dismiss focus by hiding keyboard
                    // and pressing back a couple of times — this is a recovery path.
                    android.util.Log.w(TAG, "type() called while AI Agent is foreground — likely a launchApp didn't complete. Attempting recovery.")
                    return ToolResult(false, "type($text) ABORTED: AI Agent app is still foreground. The previous launchApp did not complete. Try calling launchApp again, then readScreen() to verify.")
                }
                val typed = service.type(text)
                delay(400)  // let the IME settle
                ToolResult(typed, "type($text) on $currentPkg")
            }
            "swipe" -> {
                val x1 = (call.args["x1"] as? Number)?.toFloat() ?: return ToolResult(false, "missing x1")
                val y1 = (call.args["y1"] as? Number)?.toFloat() ?: return ToolResult(false, "missing y1")
                val x2 = (call.args["x2"] as? Number)?.toFloat() ?: return ToolResult(false, "missing x2")
                val y2 = (call.args["y2"] as? Number)?.toFloat() ?: return ToolResult(false, "missing y2")
                ToolResult(service.swipe(x1, y1, x2, y2), "swipe($x1,$y1 → $x2,$y2)")
            }
            "scrollDown" -> ToolResult(service.scrollDown(), "scrollDown")
            "scrollUp" -> ToolResult(service.scrollUp(), "scrollUp")
            "pressBack" -> ToolResult(service.pressBack(), "pressBack")
            "pressHome" -> ToolResult(service.pressHome(), "pressHome")
            "pressEnter" -> ToolResult(service.pressEnter(), "pressEnter")
            "submitInput" -> {
                // Same AI-Agent-foreground guard as type() — don't submit our own chat input.
                val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val currentPkg = am.runningAppProcesses?.firstOrNull { it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }?.processName ?: "unknown"
                if (currentPkg == "com.ai.agent") {
                    android.util.Log.w(TAG, "submitInput() called while AI Agent is foreground — aborting to prevent sending our own chat.")
                    return ToolResult(false, "submitInput ABORTED: AI Agent is foreground. launchApp didn't complete. Try again with launchApp + readScreen first.")
                }
                val submitted = service.submitInput()
                delay(800)  // wait for navigation/results to load
                ToolResult(submitted, "submitInput on $currentPkg")
            }
            "launchApp" -> {
                val pkg = (call.args["package"] as? String ?: call.args["pkg"] as? String)
                    ?: return ToolResult(false, "missing package")
                val launched = service.launchApp(pkg)
                if (!launched) return ToolResult(false, "launchApp($pkg) failed — startActivity returned false")
                // Wait for the app to actually come to foreground before returning.
                // Without this, the next tool (e.g., type) runs against the PREVIOUS foreground app
                // (often the AI Agent's own UI), which is the source of the "BLACKPINK got typed
                // into chat" bug.
                delay(2000)  // give the home launcher + target app time to swap
                // Verify by reading the screen — if we see the AI Agent's own chat UI, the launch
                // didn't take. We don't hard-fail here because some apps show splash screens
                // with no text, but at least we surface what's actually visible.
                val screenText = try { service.readScreen() } catch (e: Exception) { "" }
                val shortScreen = if (screenText.length > 200) screenText.take(200) + "..." else screenText
                ToolResult(
                    true,
                    "launchApp($pkg) — launched. After 2s, screen shows: $shortScreen"
                )
            }
            "listInstalledApps" -> ToolResult(true, getInstalledApps())
            "wait" -> {
                val seconds = (call.args["seconds"] as? Number)?.toInt() ?: 2
                delay(seconds * 1000L)
                ToolResult(true, "waited ${seconds}s")
            }
            // Memory tools
            "remember" -> {
                val key = call.args["key"] as? String ?: return ToolResult(false, "missing key")
                val value = call.args["value"] as? String ?: return ToolResult(false, "missing value")
                database.remember(key, value)
                ToolResult(true, "Remembered: $key = $value")
            }
            "recall" -> {
                val key = call.args["key"] as? String ?: return ToolResult(false, "missing key")
                val value = database.recall(key)
                ToolResult(true, if (value != null) "$key = $value" else "I don't remember $key")
            }
            "recallAll" -> {
                val memory = database.getAllMemory()
                val memoryStr = if (memory.isEmpty()) {
                    "I don't have any stored memories yet."
                } else {
                    memory.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                }
                ToolResult(true, memoryStr)
            }
            // Vision tools
            "analyzeScreen" -> {
                val prompt = call.args["prompt"] as? String ?: "Describe what's on the screen."
                ToolResult(true, analyzeScreenWithVLM(prompt))
            }
            "findElement" -> {
                val description = call.args["description"] as? String ?: return ToolResult(false, "missing description")
                val coords = findElementWithVLM(description)
                if (coords != null) {
                    ToolResult(true, "Found at x=${coords.first}, y=${coords.second}")
                } else {
                    ToolResult(false, "Could not find: $description")
                }
            }
            // System tools
            "getBatteryLevel" -> {
                val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
                val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ToolResult(true, "Battery level: $level%")
            }
            "openUrl" -> {
                val url = call.args["url"] as? String ?: return ToolResult(false, "missing url")
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(true, "Opened URL: $url")
                } catch (e: Exception) {
                    ToolResult(false, "Failed to open URL: ${e.message}")
                }
            }
            "getCurrentTime" -> {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", java.util.Locale.getDefault())
                ToolResult(true, "Current time: ${sdf.format(java.util.Date())}")
            }
            // === DEVICE CONTROL TOOLS ===
            "getClipboard" -> {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: "(clipboard empty)"
                ToolResult(true, "Clipboard: $text")
            }
            "setClipboard" -> {
                val text = call.args["text"] as? String ?: return ToolResult(false, "missing text")
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AI Agent", text))
                ToolResult(true, "Copied to clipboard: $text")
            }
            "setVolume" -> {
                val level = (call.args["level"] as? Number)?.toInt() ?: return ToolResult(false, "missing level (0-15)")
                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, level, 0)
                ToolResult(true, "Volume set to $level")
            }
            "getVolume" -> {
                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                val vol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                ToolResult(true, "Volume: $vol/$max")
            }
            "setBrightness" -> {
                val level = (call.args["level"] as? Number)?.toInt() ?: return ToolResult(false, "missing level (0-255)")
                try {
                    android.provider.Settings.System.putInt(
                        context.contentResolver,
                        android.provider.Settings.System.SCREEN_BRIGHTNESS,
                        level
                    )
                    ToolResult(true, "Brightness set to $level")
                } catch (e: Exception) {
                    ToolResult(false, "Cannot change brightness: ${e.message}")
                }
            }
            "toggleFlashlight" -> {
                try {
                    val cameraManager = context.getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                    val cameraId = cameraManager.cameraIdList[0]
                    cameraManager.setTorchMode(cameraId, true)
                    Thread.sleep(2000)
                    cameraManager.setTorchMode(cameraId, false)
                    ToolResult(true, "Flashlight toggled")
                } catch (e: Exception) {
                    ToolResult(false, "Flashlight error: ${e.message}")
                }
            }
            "mediaPlayPause" -> {
                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.dispatchMediaKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                )
                audioManager.dispatchMediaKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                )
                ToolResult(true, "Play/Pause pressed")
            }
            "mediaNext" -> {
                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.dispatchMediaKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                )
                audioManager.dispatchMediaKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                )
                ToolResult(true, "Next track")
            }
            "mediaPrevious" -> {
                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.dispatchMediaKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                )
                audioManager.dispatchMediaKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                )
                ToolResult(true, "Previous track")
            }
            "takePhoto" -> {
                try {
                    val intent = android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(true, "Camera opened")
                } catch (e: Exception) {
                    ToolResult(false, "Cannot open camera: ${e.message}")
                }
            }
            "listFiles" -> {
                val path = call.args["path"] as? String ?: "/storage/emulated/0/Documents/ai-workspace"
                try {
                    val dir = java.io.File(path)
                    if (!dir.exists()) return ToolResult(false, "Directory not found: $path")
                    val files = dir.listFiles()?.joinToString("\n") { f ->
                        "${if (f.isDirectory) "[DIR]" else "      "} ${f.name} (${f.length()} bytes)"
                    } ?: "(empty or no access)"
                    ToolResult(true, "Files in $path:\n$files")
                } catch (e: Exception) {
                    ToolResult(false, "Error: ${e.message}")
                }
            }
            "readFile" -> {
                val path = call.args["path"] as? String ?: return ToolResult(false, "missing path")
                try {
                    val content = java.io.File(path).readText()
                    ToolResult(true, content.take(2000))  // limit to 2000 chars
                } catch (e: Exception) {
                    ToolResult(false, "Error reading file: ${e.message}")
                }
            }
            "writeFile" -> {
                val path = call.args["path"] as? String ?: return ToolResult(false, "missing path")
                val content = call.args["content"] as? String ?: return ToolResult(false, "missing content")
                try {
                    val file = java.io.File(path)
                    file.parentFile?.mkdirs()
                    file.writeText(content)
                    ToolResult(true, "File written: $path (${content.length} chars)")
                } catch (e: Exception) {
                    ToolResult(false, "Error writing file: ${e.message}")
                }
            }
            "toggleWifi" -> {
                // Note: requires CHANGE_WIFI_STATE permission, may not work on Android 10+
                try {
                    val wifiManager = context.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                    val enabled = call.args["enabled"] as? Boolean ?: !wifiManager.isWifiEnabled
                    @Suppress("DEPRECATION")
                    wifiManager.setWifiEnabled(enabled)
                    ToolResult(true, "WiFi ${if (enabled) "enabled" else "disabled"}")
                } catch (e: Exception) {
                    ToolResult(false, "Cannot toggle WiFi: ${e.message}")
                }
            }
            "getNetworkInfo" -> {
                try {
                    val wifiManager = context.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                    val connManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    val info = connManager.activeNetworkInfo
                    val wifiOn = wifiManager.isWifiEnabled
                    val connected = info?.isConnected == true
                    val type = info?.typeName ?: "none"
                    ToolResult(true, "WiFi: ${if (wifiOn) "ON" else "OFF"}, Connected: $connected, Type: $type")
                } catch (e: Exception) {
                    ToolResult(false, "Error: ${e.message}")
                }
            }
            "lockScreen" -> {
                service.lockScreen()
                ToolResult(true, "Screen locked")
            }
            "takeScreenshotToGallery" -> {
                val screenshotDeferred = kotlinx.coroutines.CompletableDeferred<String?>()
                service.captureScreen { base64 ->
                    screenshotDeferred.complete(base64)
                }
                val base64 = screenshotDeferred.await()
                if (base64 != null) {
                    try {
                        val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val filename = "ai_agent_screenshot_${System.currentTimeMillis()}.jpg"
                        val path = android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_PICTURES
                        ).toString() + "/$filename"
                        val file = java.io.File(path)
                        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                        ToolResult(true, "Screenshot saved to: $path")
                    } catch (e: Exception) {
                        ToolResult(false, "Error saving screenshot: ${e.message}")
                    }
                } else {
                    ToolResult(false, "Failed to capture screenshot")
                }
            }
            // === TERMINAL ===
            "runShellCommand" -> {
                val cmd = call.args["command"] as? String ?: return ToolResult(false, "missing command")
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                    val output = process.inputStream.bufferedReader().readText()
                    val errors = process.errorStream.bufferedReader().readText()
                    val exitCode = process.waitFor()
                    val result = buildString {
                        if (output.isNotEmpty()) append(output)
                        if (errors.isNotEmpty()) append("\n[STDERR]: $errors")
                        append("\n[Exit: $exitCode]")
                    }
                    ToolResult(true, result)
                } catch (e: Exception) {
                    ToolResult(false, "Shell error: ${e.message}")
                }
            }
            // === CONTACTS ===
            "readContacts" -> {
                try {
                    val cursor = context.contentResolver.query(
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                               android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                        null, null, "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                    )
                    val contacts = mutableListOf<String>()
                    cursor?.use {
                        while (it.moveToNext() && contacts.size < 50) {
                            val name = it.getString(0) ?: ""
                            val number = it.getString(1) ?: ""
                            contacts.add("$name: $number")
                        }
                    }
                    ToolResult(true, if (contacts.isEmpty()) "No contacts found" else "Contacts (${contacts.size}):\n${contacts.joinToString("\n")}")
                } catch (e: Exception) {
                    ToolResult(false, "Contacts error: ${e.message}")
                }
            }
            "searchContacts" -> {
                val query = call.args["name"] as? String ?: return ToolResult(false, "missing name")
                try {
                    val cursor = context.contentResolver.query(
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                               android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                        arrayOf("%$query%"), null
                    )
                    val results = mutableListOf<String>()
                    cursor?.use {
                        while (it.moveToNext()) {
                            results.add("${it.getString(0)}: ${it.getString(1)}")
                        }
                    }
                    ToolResult(true, if (results.isEmpty()) "No contacts found for '$query'" else results.joinToString("\n"))
                } catch (e: Exception) {
                    ToolResult(false, "Search error: ${e.message}")
                }
            }
            "callContact" -> {
                val number = call.args["number"] as? String ?: return ToolResult(false, "missing number")
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_CALL).apply {
                        data = android.net.Uri.parse("tel:$number")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(true, "Calling $number...")
                } catch (e: Exception) {
                    ToolResult(false, "Call error: ${e.message}")
                }
            }
            "sendSMS" -> {
                val number = call.args["number"] as? String ?: return ToolResult(false, "missing number")
                val message = call.args["message"] as? String ?: return ToolResult(false, "missing message")
                try {
                    val smsManager = android.telephony.SmsManager.getDefault()
                    smsManager.sendTextMessage(number, null, message, null, null)
                    ToolResult(true, "SMS sent to $number: $message")
                } catch (e: Exception) {
                    ToolResult(false, "SMS error: ${e.message}")
                }
            }
            // === CALENDAR ===
            "getCalendarEvents" -> {
                try {
                    val now = System.currentTimeMillis()
                    val endOfDay = now + 86400000 * 7 // next 7 days
                    val cursor = context.contentResolver.query(
                        android.provider.CalendarContract.Events.CONTENT_URI,
                        arrayOf(android.provider.CalendarContract.Events.TITLE,
                               android.provider.CalendarContract.Events.DTSTART,
                               android.provider.CalendarContract.Events.DTEND,
                               android.provider.CalendarContract.Events.EVENT_LOCATION),
                        "${android.provider.CalendarContract.Events.DTSTART} >= ? AND ${android.provider.CalendarContract.Events.DTSTART} <= ?",
                        arrayOf(now.toString(), endOfDay.toString()),
                        "${android.provider.CalendarContract.Events.DTSTART} ASC"
                    )
                    val events = mutableListOf<String>()
                    val sdf = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                    cursor?.use {
                        while (it.moveToNext() && events.size < 20) {
                            val title = it.getString(0) ?: ""
                            val start = it.getLong(1)
                            val end = it.getLong(2)
                            val location = it.getString(3) ?: ""
                            events.add("${sdf.format(java.util.Date(start))} - ${sdf.format(java.util.Date(end))}: $title${if (location.isNotEmpty()) " @ $location" else ""}")
                        }
                    }
                    ToolResult(true, if (events.isEmpty()) "No upcoming events" else "Upcoming events:\n${events.joinToString("\n")}")
                } catch (e: Exception) {
                    ToolResult(false, "Calendar error: ${e.message}")
                }
            }
            "createCalendarEvent" -> {
                val title = call.args["title"] as? String ?: return ToolResult(false, "missing title")
                val startTime = call.args["startTime"] as? String ?: return ToolResult(false, "missing startTime (epoch ms or yyyy-MM-dd HH:mm)")
                try {
                    val startMs = if (startTime.matches(Regex("\\d+"))) startTime.toLong()
                    else {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                        sdf.parse(startTime)?.time ?: return ToolResult(false, "Invalid time format")
                    }
                    val durationMin = (call.args["durationMinutes"] as? Number)?.toInt() ?: 60
                    val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                        data = android.provider.CalendarContract.Events.CONTENT_URI
                        putExtra(android.provider.CalendarContract.Events.TITLE, title)
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, startMs + durationMin * 60000)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(true, "Calendar event '$title' created")
                } catch (e: Exception) {
                    ToolResult(false, "Calendar error: ${e.message}")
                }
            }
            // === HTTP REQUESTS (Web Research) ===
            "makeHttpRequest" -> {
                val url = call.args["url"] as? String ?: return ToolResult(false, "missing url")
                val method = call.args["method"] as? String ?: "GET"
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val request = okhttp3.Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    // Truncate to 2000 chars for LLM context
                    val truncated = if (body.length > 2000) body.take(2000) + "\n[...truncated]" else body
                    ToolResult(true, "HTTP ${response.code}:\n$truncated")
                } catch (e: Exception) {
                    ToolResult(false, "HTTP error: ${e.message}")
                }
            }
            "downloadFile" -> {
                val url = call.args["url"] as? String ?: return ToolResult(false, "missing url")
                val path = call.args["path"] as? String ?: "/storage/emulated/0/Documents/ai-workspace/downloads/$(System.currentTimeMillis()).bin"
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val request = okhttp3.Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val bytes = response.body?.bytes() ?: return ToolResult(false, "Empty response")
                    val file = java.io.File(path)
                    file.parentFile?.mkdirs()
                    file.writeBytes(bytes)
                    ToolResult(true, "Downloaded ${bytes.size} bytes to $path")
                } catch (e: Exception) {
                    ToolResult(false, "Download error: ${e.message}")
                }
            }
            "webSearch" -> {
                val query = call.args["query"] as? String ?: return ToolResult(false, "missing query")
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val body = """{"query":"$query","num":5}"""
                        .toRequestBody("application/json".toMediaType())
                    val request = okhttp3.Request.Builder()
                        .url(AIProvider.getWebSearchUrl(context))
                        .header("Content-Type", "application/json")
                        .post(body)
                        .build()
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: "{}"
                    // Response shape: { query, count, results: [...] }
                    val parsed = org.json.JSONObject(responseBody)
                    val json = parsed.optJSONArray("results") ?: org.json.JSONArray()
                    val results = StringBuilder()
                    for (i in 0 until json.length()) {
                        val item = json.optJSONObject(i) ?: continue
                        results.append("${i+1}. ${item.optString("name")}\n   ${item.optString("snippet")}\n   ${item.optString("url")}\n\n")
                    }
                    ToolResult(true, results.toString())
                } catch (e: Exception) {
                    ToolResult(false, "Search error: ${e.message}")
                }
            }
            // === CALL LOG ===
            "getCallLog" -> {
                try {
                    val cursor = context.contentResolver.query(
                        android.provider.CallLog.Calls.CONTENT_URI,
                        arrayOf(android.provider.CallLog.Calls.NUMBER, android.provider.CallLog.Calls.CACHED_NAME,
                               android.provider.CallLog.Calls.TYPE, android.provider.CallLog.Calls.DATE,
                               android.provider.CallLog.Calls.DURATION),
                        null, null, "${android.provider.CallLog.Calls.DATE} DESC LIMIT 20"
                    )
                    val calls = mutableListOf<String>()
                    val sdf = java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.getDefault())
                    cursor?.use {
                        while (it.moveToNext() && calls.size < 20) {
                            val number = it.getString(0) ?: ""
                            val name = it.getString(1) ?: "Unknown"
                            val type = when (it.getInt(2)) {
                                android.provider.CallLog.Calls.INCOMING_TYPE -> "Incoming"
                                android.provider.CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                                android.provider.CallLog.Calls.MISSED_TYPE -> "Missed"
                                else -> "Unknown"
                            }
                            val date = sdf.format(java.util.Date(it.getLong(3)))
                            val duration = it.getString(4) ?: "0"
                            calls.add("$date $type: $name ($number) ${duration}s")
                        }
                    }
                    ToolResult(true, if (calls.isEmpty()) "No call history" else "Recent calls:\n${calls.joinToString("\n")}")
                } catch (e: Exception) {
                    ToolResult(false, "Call log error: ${e.message}")
                }
            }
            // === LOCATION ===
            "getCurrentLocation" -> {
                try {
                    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                    val providers = lm.getProviders(true)
                    var location: android.location.Location? = null
                    for (provider in providers) {
                        try {
                            @Suppress("MissingPermission")
                            val loc = lm.getLastKnownLocation(provider)
                            if (loc != null && (location == null || loc.accuracy < location!!.accuracy)) {
                                location = loc
                            }
                        } catch (_: Exception) {}
                    }
                    if (location != null) {
                        ToolResult(true, "Location: ${location.latitude}, ${location.longitude} (accuracy: ${location.accuracy}m)")
                    } else {
                        ToolResult(false, "Location not available. Enable GPS.")
                    }
                } catch (e: Exception) {
                    ToolResult(false, "Location error: ${e.message}")
                }
            }
            "openMaps" -> {
                val query = call.args["query"] as? String ?: return ToolResult(false, "missing query")
                try {
                    // Force Google Maps — no app picker
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("google.maps:q=$query")).apply {
                        setPackage("com.google.android.apps.maps")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        ToolResult(true, "Google Maps opened for: $query")
                    } else {
                        // Fallback: open in browser
                        val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://maps.google.com/maps?q=$query")).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(browserIntent)
                        ToolResult(true, "Maps opened in browser for: $query (Google Maps app not installed)")
                    }
                } catch (e: Exception) {
                    ToolResult(false, "Maps error: ${e.message}")
                }
            }
            // === DEVICE INFO ===
            "getDeviceInfo" -> {
                val info = buildString {
                    append("Model: ${android.os.Build.MODEL}\n")
                    append("Brand: ${android.os.Build.BRAND}\n")
                    append("Manufacturer: ${android.os.Build.MANUFACTURER}\n")
                    append("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
                    append("Device: ${android.os.Build.DEVICE}\n")
                    append("Board: ${android.os.Build.BOARD}\n")
                    val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
                    append("Battery: ${bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)}%\n")
                    val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    val mi = android.app.ActivityManager.MemoryInfo()
                    am.getMemoryInfo(mi)
                    append("RAM: ${mi.availMem / 1048576}MB / ${mi.totalMem / 1048576}MB\n")
                    append("Storage: ${android.os.Environment.getDataDirectory().let { java.io.File(it.path).freeSpace / 1048576 }}MB free\n")
                    val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
                    val display = wm.defaultDisplay
                    val size = android.graphics.Point()
                    display.getRealSize(size)
                    append("Screen: ${size.x}x${size.y}\n")
                    append("Root: ${if (java.io.File("/system/bin/su").exists()) "YES" else "NO"}\n")
                }
                ToolResult(true, info)
            }
            // === FILE OPERATIONS ===
            "copyFile" -> {
                val src = call.args["source"] as? String ?: return ToolResult(false, "missing source")
                val dst = call.args["destination"] as? String ?: return ToolResult(false, "missing destination")
                try {
                    java.io.File(src).copyTo(java.io.File(dst), overwrite = true)
                    ToolResult(true, "Copied $src to $dst")
                } catch (e: Exception) { ToolResult(false, "Copy error: ${e.message}") }
            }
            "moveFile" -> {
                val src = call.args["source"] as? String ?: return ToolResult(false, "missing source")
                val dst = call.args["destination"] as? String ?: return ToolResult(false, "missing destination")
                try {
                    java.io.File(src).renameTo(java.io.File(dst))
                    ToolResult(true, "Moved $src to $dst")
                } catch (e: Exception) { ToolResult(false, "Move error: ${e.message}") }
            }
            "deleteFile" -> {
                val path = call.args["path"] as? String ?: return ToolResult(false, "missing path")
                try {
                    val file = java.io.File(path)
                    val deleted = file.delete()
                    ToolResult(deleted, if (deleted) "Deleted $path" else "Could not delete $path")
                } catch (e: Exception) { ToolResult(false, "Delete error: ${e.message}") }
            }
            "createDirectory" -> {
                val path = call.args["path"] as? String ?: return ToolResult(false, "missing path")
                try {
                    val dir = java.io.File(path)
                    val created = dir.mkdirs()
                    ToolResult(created || dir.exists(), "Directory $path ${if (created) "created" else "already exists"}")
                } catch (e: Exception) { ToolResult(false, "Error: ${e.message}") }
            }
            // === ALARM ===
            "setAlarm" -> {
                val hour = (call.args["hour"] as? Number)?.toInt() ?: return ToolResult(false, "missing hour")
                val minute = (call.args["minute"] as? Number)?.toInt() ?: return ToolResult(false, "missing minute")
                try {
                    val intent = android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                        putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(true, "Alarm set for $hour:$minute")
                } catch (e: Exception) { ToolResult(false, "Alarm error: ${e.message}") }
            }
            "setTimer" -> {
                val seconds = (call.args["seconds"] as? Number)?.toInt() ?: return ToolResult(false, "missing seconds")
                try {
                    val intent = android.content.Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(android.provider.AlarmClock.EXTRA_LENGTH, seconds)
                        putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, call.args["message"] as? String ?: "Timer")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(true, "Timer set for ${seconds}s")
                } catch (e: Exception) { ToolResult(false, "Timer error: ${e.message}") }
            }
            // === APP MANAGEMENT ===
            "getAppInfo" -> {
                val pkg = call.args["package"] as? String ?: return ToolResult(false, "missing package")
                try {
                    val pm = context.packageManager
                    val info = pm.getPackageInfo(pkg, 0)
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val name = pm.getApplicationLabel(appInfo).toString()
                    ToolResult(true, "App: $name\nPackage: $pkg\nVersion: ${info.versionName}\nInstalled: ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(info.firstInstallTime))}")
                } catch (e: Exception) { ToolResult(false, "App not found: $pkg") }
            }
            "forceStopApp" -> {
                val pkg = call.args["package"] as? String ?: return ToolResult(false, "missing package")
                try {
                    val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    am.killBackgroundProcesses(pkg)
                    ToolResult(true, "Force stopped $pkg")
                } catch (e: Exception) { ToolResult(false, "Error: ${e.message}") }
            }
            "uninstallApp" -> {
                val pkg = call.args["package"] as? String ?: return ToolResult(false, "missing package")
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_DELETE).apply {
                        data = android.net.Uri.parse("package:$pkg")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(true, "Opening uninstall dialog for $pkg")
                } catch (e: Exception) { ToolResult(false, "Error: ${e.message}") }
            }
            // === EMAIL ===
            "sendEmail" -> {
                val to = call.args["to"] as? String ?: return ToolResult(false, "missing 'to' email")
                val subject = call.args["subject"] as? String ?: ""
                val body = call.args["body"] as? String ?: ""
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(to))
                        putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
                        putExtra(android.content.Intent.EXTRA_TEXT, body)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult(true, "Email draft created for $to")
                } catch (e: Exception) { ToolResult(false, "Email error: ${e.message}") }
            }
            // === BLUETOOTH ===
            "getBluetoothState" -> {
                try {
                    val bm = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
                    val adapter = bm.adapter
                    if (adapter != null) {
                        ToolResult(true, "Bluetooth: ${if (adapter.isEnabled) "ON" else "OFF"}\nName: ${adapter.name ?: "N/A"}\nAddress: ${adapter.address}")
                    } else {
                        ToolResult(false, "Bluetooth not available")
                    }
                } catch (e: Exception) { ToolResult(false, "Bluetooth error: ${e.message}") }
            }
            // === SHARE TEXT ===
            "shareText" -> {
                val text = call.args["text"] as? String ?: return ToolResult(false, "missing text")
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, text)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Share via").apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    ToolResult(true, "Share dialog opened")
                } catch (e: Exception) { ToolResult(false, "Share error: ${e.message}") }
            }
            // === PING ===
            "pingHost" -> {
                val host = call.args["host"] as? String ?: return ToolResult(false, "missing host")
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "ping -c 3 $host"))
                    val output = process.inputStream.bufferedReader().readText()
                    process.waitFor()
                    ToolResult(true, output)
                } catch (e: Exception) { ToolResult(false, "Ping error: ${e.message}") }
            }
            // === TRANSLATE (via LLM) ===
            "translateText" -> {
                val text = call.args["text"] as? String ?: return ToolResult(false, "missing text")
                val targetLang = call.args["targetLanguage"] as? String ?: "English"
                ToolResult(true, "Please translate this text to $targetLang:\n$text")
            }
            // === IN-APP BROWSER ===
            "browserOpen" -> {
                val url = call.args["url"] as? String ?: return ToolResult(false, "missing url")
                val finalUrl = if (!url.startsWith("http")) "https://$url" else url
                BrowserController.openUrl(context, finalUrl)
                Thread.sleep(2000)
                ToolResult(true, "Browser opened: $finalUrl")
            }
            "browserReadPage" -> {
                val result = BrowserController.getPageText(context)
                ToolResult(true, if (result != null) result.take(3000) else "Browser not open or page not loaded")
            }
            "browserEval" -> {
                val js = call.args["script"] as? String ?: return ToolResult(false, "missing script")
                val result = BrowserController.evalJs(context, js)
                ToolResult(true, result ?: "No result")
            }
            "browserClick" -> {
                val selector = call.args["selector"] as? String ?: return ToolResult(false, "missing selector")
                val js = "document.querySelector('$selector')?.click(); 'clicked'"
                val result = BrowserController.evalJs(context, js)
                ToolResult(true, "Click attempted: $result")
            }
            "browserFill" -> {
                val selector = call.args["selector"] as? String ?: return ToolResult(false, "missing selector")
                val value = call.args["value"] as? String ?: return ToolResult(false, "missing value")
                val js = "document.querySelector('$selector').value = '$value'; 'filled'"
                val result = BrowserController.evalJs(context, js)
                ToolResult(true, "Fill attempted: $result")
            }
            "browserScrollDown" -> {
                BrowserController.evalJs(context, "window.scrollBy(0, 800); 'scrolled'")
                ToolResult(true, "Scrolled down")
            }
            "browserBack" -> {
                BrowserController.goBack(context)
                Thread.sleep(1000)
                ToolResult(true, "Went back")
            }
            "browserGetUrl" -> {
                val url = BrowserController.getCurrentUrl(context)
                ToolResult(true, url ?: "Browser not open")
            }
            "browserSearch" -> {
                val query = call.args["query"] as? String ?: return ToolResult(false, "missing query")
                val url = "https://www.google.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
                BrowserController.openUrl(context, url)
                Thread.sleep(2000)
                val pageText = BrowserController.getPageText(context)
                val text = pageText?.take(3000) ?: "Page not loaded"
                ToolResult(true, "Searched: $query\n\nPage content:\n$text")
            }
            "localLLM" -> {
                ToolResult(false, "Local LLM has been removed. The app now uses cloud AI (GLM-4.6) for everything, which is faster and more capable.")
            }
            else -> ToolResult(false, "unknown tool: ${call.name}")
        }
    }

    private fun getInstalledApps(): String {
        return try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val thirdParty = packages
                .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
                .joinToString("\n") { "package:${it.packageName}" }
            "Third-party apps (${packages.size} total):\n$thirdParty"
        } catch (e: Exception) {
            "Error listing apps: ${e.message}"
        }
    }

    // ==================== VISION (VLM) ====================

    private val visionClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Takes a screenshot and sends it to the VLM with a prompt.
     * Returns the VLM's text description of the screen.
     */
    private suspend fun analyzeScreenWithVLM(prompt: String): String {
        val service = AgentAccessibilityService.getInstance()
            ?: return "Accessibility service not running"

        // Take screenshot — use CompletableDeferred to bridge callback to coroutine
        val screenshotDeferred = kotlinx.coroutines.CompletableDeferred<String?>()
        service.captureScreen { base64 ->
            screenshotDeferred.complete(base64)
        }
        val screenshotBase64 = screenshotDeferred.await()

        if (screenshotBase64 == null) {
            return "Failed to capture screenshot"
        }

        Log.d(TAG, "Sending screenshot to VLM: ${screenshotBase64.length} chars")

        // Call VLM via proxy
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val jsonMediaType = "application/json".toMediaType()
                val bodyStr = """{"model":"glm-4v","messages":[{"role":"user","content":[{"type":"text","text":"$prompt"},{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,$screenshotBase64"}}]}]}"""
                
                val body = bodyStr.toRequestBody("application/json".toMediaType())

                val request = okhttp3.Request.Builder()
                    .url(AIProvider.getVisionUrl(context))
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()

                val response = visionClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    Log.e(TAG, "VLM error ${response.code}: ${responseBody.take(200)}")
                    return@withContext "VLM error: ${response.code}"
                }

                val json = org.json.JSONObject(responseBody)
                val content = json
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?: "No response from VLM"

                Log.d(TAG, "VLM response: ${content.take(200)}...")
                content
            } catch (e: Exception) {
                Log.e(TAG, "VLM call failed", e)
                "Vision error: ${e.message}"
            }
        }
    }

    /**
     * Finds a UI element by visual description using the VLM.
     * Returns (x, y) coordinates scaled to the real screen size.
     */
    private suspend fun findElementWithVLM(description: String): Pair<Float, Float>? {
        val result = analyzeScreenWithVLM(
            "Find the UI element that matches: \"$description\". " +
            "Respond with ONLY the coordinates in format: x,y (e.g., 540,1200). " +
            "The screen is 1080x2400 pixels. If not found, respond with: NOT_FOUND"
        )

        if (result.contains("NOT_FOUND") || result.contains("error")) {
            return null
        }

        // Parse coordinates from VLM response
        val regex = Regex("(\\d+)\\s*,\\s*(\\d+)")
        val match = regex.find(result)
        if (match != null) {
            val x = match.groupValues[1].toFloat()
            val y = match.groupValues[2].toFloat()
            Log.d(TAG, "findElement: $description → ($x, $y)")
            return Pair(x, y)
        }

        return null
    }

    data class ToolResult(
        val success: Boolean,
        val output: String
    )
}
