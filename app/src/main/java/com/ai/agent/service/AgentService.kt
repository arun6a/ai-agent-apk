package com.ai.agent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ai.agent.R
import com.ai.agent.accessibility.AgentAccessibilityService
import com.ai.agent.llm.LLMClient
import com.ai.agent.tools.ToolExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Foreground service that:
 * 1. Keeps the AI Agent alive in the background
 * 2. Manages the floating overlay button
 * 3. Processes voice commands from the overlay
 */
class AgentService : Service() {

    companion object {
        const val CHANNEL_ID = "ai_agent_channel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "AgentService"

        const val ACTION_START = "com.ai.agent.START"
        const val ACTION_STOP = "com.ai.agent.STOP"
        const val ACTION_PROCESS_COMMAND = "com.ai.agent.PROCESS_COMMAND"
        const val EXTRA_COMMAND = "command"

        private var overlayManager: OverlayManager? = null

        fun isOverlayVisible(): Boolean = overlayManager?.isVisible() == true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var llmClient: LLMClient? = null
    private var toolExecutor: ToolExecutor? = null
    private var tts: TextToSpeech? = null
    private var ruleEngine: com.ai.agent.rules.RuleEngine? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AgentService created")
        createNotificationChannel()

        // Initialize TTS for voice commands
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("en", "IN")
            }
        }

        llmClient = LLMClient(this)
        toolExecutor = ToolExecutor(this)

        // Start rule engine
        ruleEngine = com.ai.agent.rules.RuleEngine(this)
        ruleEngine?.onRuleTriggered = { rule ->
            Log.i(TAG, "Rule triggered: ${rule.name}")
            speak("Rule triggered: ${rule.name}")
            processVoiceCommand(rule.action)  // execute the rule's action
        }
        ruleEngine?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PROCESS_COMMAND -> {
                val command = intent.getStringExtra(EXTRA_COMMAND) ?: ""
                if (command.isNotEmpty()) {
                    processVoiceCommand(command)
                }
            }
            else -> {
                showOverlay()
            }
        }
        return START_STICKY
    }

    /**
     * Process a voice command from the overlay.
     * Calls the LLM, executes tools, speaks the result.
     */
    private fun processVoiceCommand(command: String) {
        Log.i(TAG, "Processing voice command: $command")
        speak("Got it. Processing your request.")

        scope.launch {
            try {
                val systemPrompt = """
                    You are an AI agent controlling an Android phone. You help the user by executing actions on their device.

                    Available tools:
                    - readScreen() — returns all text on screen
                    - tap(x, y) — taps at coordinates (screen is 1080x2400)
                    - clickByText(text) — clicks a UI element by text
                    - type(text) — types text into focused field
                    - swipe(x1, y1, x2, y2) — swipes
                    - pressBack(), pressHome(), pressEnter(), submitInput()
                    - launchApp(package) — launches an app
                    - wait(seconds)

                    Common apps: YouTube=com.google.android.youtube, WhatsApp=com.whatsapp, Chrome=com.android.chrome, Instagram=com.instagram.android

                    Respond in JSON: {"reply": "short message", "tool_calls": [{"name": "...", "args": {...}}]}

                    After typing in a search box, call submitInput().
                    If no tools needed, use empty tool_calls.
                """.trimIndent()

                val response = llmClient?.chat(command, systemPrompt)
                if (response != null) {
                    speak(response.reply)
                    // Execute tools if any
                    if (response.toolCalls.isNotEmpty()) {
                        toolExecutor?.executeTools(response.toolCalls)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command", e)
                speak("Sorry, I had an error: ${e.message}")
            }
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "service_msg")
    }

    private fun createNotification(): Notification {
        val a11yStatus = if (AgentAccessibilityService.isRunning()) "ON" else "OFF"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Agent")
            .setContentText("Running • Accessibility $a11yStatus")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Agent Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the AI Agent running in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showOverlay() {
        try {
            if (overlayManager == null) {
                overlayManager = OverlayManager(this)
            }
            overlayManager?.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
        }
    }

    fun hideOverlay() {
        overlayManager?.hide()
    }

    fun toggleOverlay() {
        if (overlayManager?.isVisible() == true) {
            overlayManager?.hide()
        } else {
            showOverlay()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ruleEngine?.stop()
        overlayManager?.destroy()
        overlayManager = null
        tts?.stop()
        tts?.shutdown()
        Log.i(TAG, "AgentService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
