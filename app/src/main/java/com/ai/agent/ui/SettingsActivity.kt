package com.ai.agent.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ai.agent.storage.AgentDatabase
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class SettingsActivity : AppCompatActivity() {

    private lateinit var database: AgentDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AgentDatabase(this)

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "Settings"
            textSize = 24f
            setTextColor(0xFFFAFAFA.toInt())
            setPadding(0, 0, 0, 24)
        })

        // === ALWAYS-ON MODE ===
        layout.addView(SectionTitle("Always-On Mode"))
        layout.addView(InfoText("When enabled, the AI Agent runs as a foreground service that Android won't kill. " +
            "Rules and scheduled tasks will fire reliably. The floating button stays available."))
        
        val alwaysOnSwitch = android.widget.Switch(this).apply {
            text = "Enable Always-On Mode"
            isChecked = getSharedPreferences("ai_agent", android.content.Context.MODE_PRIVATE)
                .getBoolean("always_on", false)
            setOnCheckedChangeListener { _, isChecked ->
                getSharedPreferences("ai_agent", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("always_on", isChecked).apply()
                
                if (isChecked) {
                    // Start foreground service
                    val intent = android.content.Intent(this@SettingsActivity, 
                        com.ai.agent.service.AgentService::class.java).apply {
                        action = com.ai.agent.service.AgentService.ACTION_START
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    Toast.makeText(this@SettingsActivity, "Always-On mode enabled", Toast.LENGTH_SHORT).show()
                } else {
                    // Stop service
                    val intent = android.content.Intent(this@SettingsActivity,
                        com.ai.agent.service.AgentService::class.java).apply {
                        action = com.ai.agent.service.AgentService.ACTION_STOP
                    }
                    startService(intent)
                    Toast.makeText(this@SettingsActivity, "Always-On mode disabled", Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(alwaysOnSwitch)


        // === AI PROVIDER ===
        layout.addView(SectionTitle("AI Provider"))
        val currentProvider = com.ai.agent.llm.AIProvider.getCurrentProvider(this)
        val currentModel = com.ai.agent.llm.AIProvider.getModel(this)
        val configured = com.ai.agent.llm.AIProvider.isConfigured(this)
        layout.addView(InfoText(
            "Current: ${currentProvider.name}\n" +
            "Model: $currentModel\n" +
            "Status: ${if (configured) "✅ Ready" else "❌ Needs API key"}\n\n" +
            "Choose between OpenRouter (free), Gemini (free), Z.ai proxy, or custom.\n" +
            "Each provider has different models and limits."
        ))
        layout.addView(Button(this).apply {
            text = "⚙️ Configure AI Provider"
            setOnClickListener {
                startActivity(android.content.Intent(this@SettingsActivity, AIProviderSettingsActivity::class.java))
            }
        })
        layout.addView(Button(this).apply {
            text = "Test Current Provider"
            setOnClickListener { testAPI() }
        })

        // === Memory ===
        layout.addView(SectionTitle("Memory"))
        val memoryCount = database.getAllMemory().size
        layout.addView(InfoText("Stored memories: $memoryCount\n\nThe agent remembers facts about you. Clear to start fresh."))
        layout.addView(Button(this).apply {
            text = "Clear All Memory"
            setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Clear Memory")
                    .setMessage("Delete all stored memories? The agent will forget everything it knows about you.")
                    .setPositiveButton("Clear") { _, _ ->
                        database.clearMemory()
                        Toast.makeText(this@SettingsActivity, "Memory cleared", Toast.LENGTH_SHORT).show()
                        recreate()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })

        // === Chat History ===
        layout.addView(SectionTitle("Chat History"))
        layout.addView(InfoText("All conversations are stored locally on your device."))
        layout.addView(Button(this).apply {
            text = "Clear Chat History"
            setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Clear History")
                    .setMessage("Delete all chat history?")
                    .setPositiveButton("Clear") { _, _ ->
                        database.clearConversations()
                        Toast.makeText(this@SettingsActivity, "History cleared", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })

        // === Voice Settings ===
        layout.addView(SectionTitle("Voice"))
        layout.addView(InfoText("STT: Android SpeechRecognizer (free)\nTTS: Android TextToSpeech (free)\nLanguages: English (en-IN), Tamil (ta-IN)\n\nFuture: ElevenLabs voice cloning, Whisper STT"))

        // === Permissions ===
        layout.addView(SectionTitle("Permissions"))
        val a11yRunning = com.ai.agent.accessibility.AgentAccessibilityService.isRunning()
        val overlayGranted = android.provider.Settings.canDrawOverlays(this)
        val notifListeners = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val notifGranted = notifListeners?.contains(packageName) == true

        layout.addView(InfoText("Accessibility Service: ${if (a11yRunning) "✅ Enabled" else "❌ Disabled"}"))
        layout.addView(InfoText("Display over apps: ${if (overlayGranted) "✅ Enabled" else "❌ Disabled"}"))
        layout.addView(InfoText("Notification Access: ${if (notifGranted) "✅ Enabled" else "❌ Disabled"}"))

        layout.addView(Button(this).apply {
            text = "Open Accessibility Settings"
            setOnClickListener {
                startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })
        layout.addView(Button(this).apply {
            text = "Open Overlay Permission"
            setOnClickListener {
                startActivity(android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${packageName}")
                ))
            }
        })
        layout.addView(Button(this).apply {
            text = "Open Notification Access"
            setOnClickListener {
                startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
        })

        // === About ===
        layout.addView(SectionTitle("About"))
        layout.addView(InfoText("AI Agent v1.0.0\nBuilt with Kotlin + Z.ai API\nGitHub: arun6a/ai-agent-apk\n\nThis app uses cloud AI (Z.ai free tier) for reasoning and vision. All data stays on your device except API calls."))

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private inner class SectionTitle(text: String) : TextView(this@SettingsActivity) {
        init {
            this.text = text
            textSize = 18f
            setTextColor(0xFF10B981.toInt())
            setPadding(0, 24, 0, 8)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    }

    private inner class InfoText(text: String) : TextView(this@SettingsActivity) {
        init {
            this.text = text
            textSize = 13f
            setTextColor(0xFFA1A1AA.toInt())
            setPadding(0, 0, 0, 16)
        }
    }

    private fun testAPI() {
        Toast.makeText(this, "Testing API...", Toast.LENGTH_SHORT).show()
        // Simple test: send a request to the LLM proxy
        Thread {
            try {
                val client = okhttp3.OkHttpClient()
                val body = """{"model":"glm-4.6","messages":[{"role":"user","content":"Say OK"}],"thinking":{"type":"disabled"}}"""
                    .toRequestBody("application/json".toMediaType())
                val request = okhttp3.Request.Builder()
                    .url("https://preview-chat-c9aadfe1-a665-4f7c-8232-c9d14a73c0cb.space-z.ai/api/llm/proxy")
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val text = response.body?.string() ?: ""
                val success = response.isSuccessful
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "✅ API connected! Response: ${text.take(100)}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "❌ API error: ${response.code}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "❌ Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}

// This will be appended
