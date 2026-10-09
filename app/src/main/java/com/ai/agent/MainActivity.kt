package com.ai.agent

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ai.agent.accessibility.AgentAccessibilityService
import com.ai.agent.databinding.ActivityMainBinding
import com.ai.agent.llm.LLMClient
import com.ai.agent.llm.ApiUsageTracker
import com.ai.agent.service.AgentService
import com.ai.agent.service.OverlayManager
import com.ai.agent.storage.AgentDatabase
import com.ai.agent.tools.ToolExecutor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ChatAdapter
    private lateinit var voiceManager: VoiceManager
    private lateinit var llmClient: LLMClient
    private lateinit var toolExecutor: ToolExecutor
    private lateinit var database: AgentDatabase
    private var ruleEngine: com.ai.agent.rules.RuleEngine? = null

    private var currentLang = "en-IN"

    // Agent state — supports interruption and task management
    private var agentJob: Job? = null
    @Volatile private var stopRequested = false

    // Direct overlay manager (simpler than service-based)
    private var overlayManager: OverlayManager? = null

    companion object {
        private const val REQUEST_RECORD_AUDIO = 100
        private const val MAX_ITERATIONS = 50           // more headroom for complex tasks
        private const val MAX_REPEATED_TOOLS = 3  // batch independent tools to save API calls        // if same tool+args called N times, stop (infinite loop guard)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // v2.2: Multi-provider support — reads config from AIProvider (SharedPreferences)
        llmClient = LLMClient(this)
        toolExecutor = ToolExecutor(this)
        database = AgentDatabase(this)

        adapter = ChatAdapter()
        binding.messagesList.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.messagesList.adapter = adapter

        // Load conversation history from database
        val history = database.getRecentConversations(30)
        if (history.isNotEmpty()) {
            for ((role, text) in history) {
                adapter.addMessage(ChatMessage(
                    text = text,
                    isUser = role == "user"
                ))
            }
        } else {
            // First time — show welcome message
            val a11yEnabled = AgentAccessibilityService.isRunning()
            val welcomeText = if (a11yEnabled) {
                "Hi! I'm your AI Agent. Accessibility is ON. The floating eye button is now on your screen — tap it from ANY app to ask \"what am I looking at?\". You can also chat here. Try: \"open youtube and search BLACKPINK\"."
            } else {
                "Hi! I'm your AI Agent. To enable phone control: 1) Long-press the status text to enable Accessibility. 2) Grant \"Display over other apps\" permission for the floating button."
            }
            adapter.addMessage(ChatMessage(text = welcomeText, isUser = false))
        }

        voiceManager = VoiceManager(this)
        setupVoiceCallbacks()
        setupUIListeners()
        checkAudioPermission()

        // Start rule engine directly from Activity (not depending on AgentService)
        startRuleEngine()

        // Check all permissions on startup
        checkAllPermissions()

        // Start the foreground service + floating overlay
        try {
            startAgentService()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start agent service", e)
        }
    }

    private fun startRuleEngine() {
        ruleEngine = com.ai.agent.rules.RuleEngine(this)
        ruleEngine?.onRuleTriggered = { rule ->
            Log.i("MainActivity", "Rule triggered: ${rule.name}")
            runOnUiThread {
                adapter.addMessage(ChatMessage(
                    text = "📋 Rule triggered: ${rule.name}\nAction: ${rule.action}",
                    isUser = false
                ))
                voiceManager.speak("Rule triggered: ${rule.name}", currentLang)
            }
            // Process the rule action via the chat agent loop (smart path)
            lifecycleScope.launch {
                processRuleAction(rule.action)
            }
        }
        ruleEngine?.start()
        Log.i("MainActivity", "Rule engine started (legacy handler-based)")

        // Also schedule all time-based rules with AlarmManager (battery-efficient, survives app kill)
        try {
            val scheduler = com.ai.agent.rules.RuleScheduler(this)
            scheduler.scheduleAllTimeRules()
            Log.i("MainActivity", "Scheduled all time rules with AlarmManager")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to schedule time rules", e)
        }

        // v5.0.0: Prune old completed WorkManager entries so its DB doesn't grow forever
        try {
            androidx.work.WorkManager.getInstance(this).pruneWork()
            Log.i("MainActivity", "Pruned old WorkManager entries")
        } catch (e: Exception) {
            Log.w("MainActivity", "WorkManager prune failed (not critical)", e)
        }
    }

    private suspend fun processRuleAction(action: String) {
        adapter.addMessage(ChatMessage(
            text = "Executing: $action...",
            isUser = false,
            status = MessageStatus.THINKING
        ))

        try {
            val basePrompt = assets.open("system_prompt.txt").bufferedReader().use { it.readText() }
            val memory = database.getAllMemory()
            val memoryStr = if (memory.isEmpty()) "No memories stored yet."
            else memory.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
            // Inject PluginManager (active plugin tools) + SkillManager (available skills)
            val pluginPrompt = com.ai.agent.llm.PluginManager.getEnabledPluginsPrompt(this)
            val skillPrompt = com.ai.agent.skills.SkillManager(this).also { it.loadSkills() }.generatePromptSection()
            val systemPrompt = "$basePrompt$pluginPrompt$skillPrompt\n\n## What I Remember About the User\n$memoryStr"

            val response = llmClient.chat(action, systemPrompt)
            var finalReply = response.reply

            if (response.toolCalls.isNotEmpty()) {
                val results = toolExecutor.executeTools(response.toolCalls)
                val summary = buildString {
                    append(response.reply)
                    append("\n")
                    results.forEachIndexed { i, result ->
                        val call = response.toolCalls[i]
                        append("• ${call.name} → ${if (result.success) "✓" else "✗"}\n")
                    }
                }
                finalReply = summary
            }

            adapter.updateLastMessage(finalReply)
            database.addConversation("ai", finalReply)
            voiceManager.speak(finalReply.take(500), currentLang)
        } catch (e: Exception) {
            adapter.updateLastMessage("Rule error: ${e.message}")
        }
    }

    private fun checkAllPermissions() {
        // Only show the popup on first launch, OR if explicitly requested via "Check Permissions" menu.
        // Showing it on every onResume() is annoying and makes the popup feel like a bug.
        // The user can manually trigger via the menu (or "Settings → Check Permissions").
        val prefs = getSharedPreferences("ai_agent_ui", MODE_PRIVATE)
        val firstLaunch = prefs.getBoolean("first_launch_done", false)
        if (firstLaunch) return  // don't nag the user every resume
        prefs.edit().putBoolean("first_launch_done", true).apply()

        showPermissionSetupDialog()
    }

    /**
     * Public method — called when user taps "Check Permissions" in the menu.
     * Always shows the dialog regardless of first-launch state.
     */
    fun showPermissionSetupDialog() {
        val missing = mutableListOf<String>()

        // Use isEnabled(this) which checks Android system settings, not just the
        // in-memory instance (which is null at launch until onServiceConnected fires).
        if (!AgentAccessibilityService.isEnabled(this)) {
            missing.add("Accessibility Service (required for screen control)")
        }
        if (!Settings.canDrawOverlays(this)) {
            missing.add("Display over other apps (for floating button)")
        }
        // Check notification listener
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (enabledListeners == null || !enabledListeners.contains(packageName)) {
            missing.add("Notification Access (for notification-based rules)")
        }

        if (missing.isNotEmpty()) {
            val message = buildString {
                append("The following permissions are needed for full functionality:\n\n")
                missing.forEachIndexed { i, perm ->
                    append("${i + 1}. $perm\n")
                }
                append("\nWould you like to enable them now?")
            }

            AlertDialog.Builder(this)
                .setTitle("Setup Required")
                .setMessage(message)
                .setPositiveButton("Enable Accessibility") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNeutralButton("Later", null)
                .show()
        }
    }

    private fun startAgentService() {
        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Enable Floating Button")
                .setMessage("To use the AI Agent from any app, I need permission to display over other apps.\n\n1. Tap 'Open Settings'\n2. Find 'AI Agent'\n3. Toggle 'Allow display over other apps' ON\n4. Return to this app")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Skip", null)
                .show()
            return
        }

        // Start the foreground service
        try {
            val intent = Intent(this, AgentService::class.java).apply {
                action = AgentService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            showToast("AI Agent service started — floating button should appear")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start service", e)
            showToast("Could not start background service: ${e.message}")
        }
    }

    // Broadcast receiver for messages from overlay/voice input
    private var messageReceiver: BroadcastReceiver? = null

    private fun registerMessageReceiver() {
        if (messageReceiver != null) return  // already registered
        messageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    val text = intent?.getStringExtra("text") ?: return
                    val isUser = intent.getBooleanExtra("isUser", false)

                    runOnUiThread {
                        try {
                            // Always ADD as new message (don't replace)
                            adapter.addMessage(ChatMessage(text = text, isUser = isUser))
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error in message receiver", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error processing broadcast", e)
                }
            }
        }
        val filter = IntentFilter("com.ai.agent.SHOW_MESSAGE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(messageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(messageReceiver, filter)
        }
        Log.i("MainActivity", "Message receiver registered")
    }

    private fun unregisterMessageReceiver() {
        if (messageReceiver != null) {
            try {
                unregisterReceiver(messageReceiver)
            } catch (e: Exception) {
                Log.w("MainActivity", "Receiver not registered: ${e.message}")
            }
            messageReceiver = null
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            updateAccessibilityStatus()
            registerMessageReceiver()

            // Reload chat history to catch any messages saved while in background
            // (e.g., voice commands processed by the overlay)
            val history = database.getRecentConversations(30)
            if (history.isNotEmpty()) {
                // Check if there are new messages since last load
                val lastMsg = if (adapter.itemCount > 0) {
                    // Get the last message text from the adapter
                    // Simple approach: if history has more messages than adapter, reload all
                    history.size > adapter.itemCount
                } else {
                    true
                }

                if (lastMsg) {
                    // Reload all messages from database
                    adapter.clear()
                    for ((role, text) in history) {
                        adapter.addMessage(ChatMessage(text = text, isUser = role == "user"))
                    }
                }
            }

            // If overlay permission was just granted, start the service
            if (Settings.canDrawOverlays(this) && !AgentService.isOverlayVisible()) {
                startAgentService()
            }
            // Re-check permissions
            checkAllPermissions()
        } catch (e: Exception) {
            Log.e("MainActivity", "onResume error", e)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterMessageReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.destroy()
        overlayManager?.destroy()
        ruleEngine?.stop()
    }

    private fun updateAccessibilityStatus() {
        // Use isEnabled(this) which checks the system setting (true source of state),
        // not isRunning() which is null at launch until onServiceConnected fires.
        val systemEnabled = AgentAccessibilityService.isEnabled(this)
        val instanceAlive = AgentAccessibilityService.isRunning()
        
        if (systemEnabled && instanceAlive) {
            binding.statusText.text = "Connected • Accessibility ON"
            binding.statusDot.setBackgroundResource(R.drawable.status_dot)
        } else if (systemEnabled && !instanceAlive) {
            // System setting says enabled but service process died (MIUI killed it)
            binding.statusText.text = "Reconnecting • Accessibility enabled (waiting for service)"
            binding.statusDot.setBackgroundResource(R.drawable.status_dot)
            // Try to re-trigger the service binding by re-enabling programmatically
            Log.w("MainActivity", "Accessibility enabled in settings but service not running — MIUI may have killed it")
        } else {
            binding.statusText.text = "Accessibility OFF"
            binding.statusDot.setBackgroundResource(R.drawable.status_dot_off)
        }
    }

    private fun setupVoiceCallbacks() {
        voiceManager.onPartialResult = { text ->
            binding.interimText.text = "$text..."
        }
        voiceManager.onFinalResult = { text ->
            binding.interimText.visibility = View.GONE
            binding.textInput.setText(text)
            binding.textInput.setSelection(text.length)
            updateSendButton()
        }
        voiceManager.onError = { error ->
            binding.interimText.visibility = View.GONE
            binding.voiceBtn.setBackgroundResource(R.drawable.voice_button_bg)
            showToast(error)
        }
        voiceManager.onListeningStateChanged = { isListening ->
            if (isListening) {
                binding.voiceBtn.setBackgroundResource(R.drawable.voice_button_listening_bg)
                binding.interimText.visibility = View.VISIBLE
                binding.interimText.text = "Listening..."
            } else {
                binding.voiceBtn.setBackgroundResource(R.drawable.voice_button_bg)
                binding.interimText.visibility = View.GONE
            }
        }
        voiceManager.onSpeakingStateChanged = { isSpeaking ->
            binding.stopSpeakBtn.visibility = if (isSpeaking) View.VISIBLE else View.GONE
        }
    }

    private fun setupUIListeners() {
        binding.langBtn.setOnClickListener {
            currentLang = if (currentLang == "en-IN") "ta-IN" else "en-IN"
            binding.langBtn.text = if (currentLang == "en-IN") "EN" else "தமி"
            voiceManager.setLanguage(currentLang)
        }
        binding.voiceBtn.setOnClickListener {
            if (voiceManager.isSpeaking()) {
                voiceManager.stopSpeaking()
            } else {
                checkAudioPermissionAndListen()
            }
        }
        binding.stopSpeakBtn.setOnClickListener { voiceManager.stopSpeaking() }

        // Overlay toggle button — directly shows/hides the floating button
        binding.overlayBtn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                AlertDialog.Builder(this)
                    .setTitle("Permission Needed")
                    .setMessage("I need 'Display over other apps' permission to show the floating button. This lets the AI Agent overlay a button on top of any app.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else if (overlayManager?.isVisible() == true) {
                // Hide overlay
                overlayManager?.hide()
                showToast("Floating button hidden")
            } else {
                // Show overlay directly from Activity
                try {
                    if (overlayManager == null) {
                        overlayManager = OverlayManager(this)
                    }
                    overlayManager?.show()
                    showToast("Floating button shown — switch to any app and tap it!")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to show overlay", e)
                    showToast("Error: ${e.message}")
                }
            }
        }
        binding.statusText.setOnLongClickListener {
            openAccessibilitySettings()
            true
        }

        // Rules button — shows options menu
        binding.rulesBtn.setOnClickListener {
            val options = arrayOf("Rules & Scheduled Tasks", "Enable Notification Access", "Clear Chat History", "Settings")
            AlertDialog.Builder(this)
                .setTitle("Options")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> startActivity(Intent(this, com.ai.agent.ui.RulesActivity::class.java))
                        1 -> startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        2 -> {
                            AlertDialog.Builder(this)
                                .setTitle("Clear History")
                                .setMessage("Delete all chat history?")
                                .setPositiveButton("Clear") { _, _ ->
                                    database.clearConversations()
                                    adapter.clear()
                                    adapter.addMessage(ChatMessage(text = "History cleared.", isUser = false))
                                    showToast("History cleared")
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                        3 -> startActivity(Intent(this, com.ai.agent.ui.SettingsActivity::class.java))
                    }
                }
                .show()
        }

        // Settings button
        binding.settingsBtn.setOnClickListener {
            startActivity(Intent(this, com.ai.agent.ui.SettingsActivity::class.java))
        }
        binding.textInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateSendButton() }
        })
        binding.textInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }
        binding.sendBtn.setOnClickListener { sendMessage() }
    }

    private fun updateSendButton() {
        val hasText = binding.textInput.text.toString().trim().isNotEmpty()
        val isRunning = agentJob?.isActive == true
        // When running: button is always enabled (acts as STOP)
        // When not running: button enabled only if there's text
        binding.sendBtn.isEnabled = isRunning || hasText
        // Change appearance: red stop icon when running, send icon when not
        binding.sendBtn.setImageResource(if (isRunning) R.drawable.ic_stop else R.drawable.ic_send)
        binding.sendBtn.background = if (isRunning) {
            ContextCompat.getDrawable(this, R.drawable.voice_button_listening_bg)  // red background
        } else {
            ContextCompat.getDrawable(this, R.drawable.send_button_bg)  // green background
        }
    }

    private fun sendMessage() {
        val text = binding.textInput.text.toString().trim()

        // If agent is running, STOP it
        val isRunning = agentJob?.isActive == true
        if (isRunning) {
            stopAgent()
            // If there's no text, just stop — don't send a new message
            if (text.isEmpty()) return
            // If there's text, fall through to send new message
        }

        if (text.isEmpty()) return

        adapter.addMessage(ChatMessage(text = text, isUser = true))
        database.addConversation("user", text)

        binding.textInput.setText("")
        updateSendButton()

        adapter.addMessage(ChatMessage(
            text = "Thinking...",
            isUser = false,
            status = MessageStatus.THINKING
        ))

        stopRequested = false
        agentJob = lifecycleScope.launch {
            runAgentLoop(text)
        }
        // Update button immediately to show STOP icon
        updateSendButton()
    }

    // v5.0.8: Helper to remove the "Thinking..." message once the first step appears
    private fun removeThinkingMessage() {
        try {
            // The adapter's last message — if it's "Thinking...", remove it
            val count = adapter.itemCount
            if (count > 0) {
                // We can't easily remove from RecyclerView adapter without a method,
                // but addMessage for steps will push it down naturally.
                // The "Thinking..." will scroll up as steps are added below it.
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "removeThinkingMessage failed", e)
        }
    }

    private fun stopAgent() {
        stopRequested = true
        agentJob?.cancel()
        agentJob = null
        adapter.updateLastMessage("(⏹ Task stopped by user)")
        voiceManager.stopSpeaking()
        // Force update the button back to send mode
        binding.sendBtn.setImageResource(R.drawable.ic_send)
        binding.sendBtn.background = ContextCompat.getDrawable(this, R.drawable.send_button_bg)
        binding.sendBtn.isEnabled = binding.textInput.text.toString().trim().isNotEmpty()
    }

    /**
     * The agent loop — iterative task execution.
     * - LLM sees tool results, decides next steps
     * - Verifies each action with readScreen()
     * - Retries on failure
     * - Stops when task is complete OR max iterations OR infinite loop detected OR user stops
     */
    private suspend fun runAgentLoop(userMessage: String) {
        // Hoisted out of try{} so the catch block can reference it for partial-progress reports.
        var workLog = StringBuilder()
        try {
            val basePrompt = assets.open("system_prompt.txt").bufferedReader().use { it.readText() }

            // Inject memory into system prompt
            val memory = database.getAllMemory()
            val memoryStr = if (memory.isEmpty()) {
                "No memories stored yet."
            } else {
                memory.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
            }
            // Inject PluginManager (active plugin tools) + SkillManager (available skills)
            val pluginPrompt = com.ai.agent.llm.PluginManager.getEnabledPluginsPrompt(this)
            val skillPrompt = com.ai.agent.skills.SkillManager(this).also { it.loadSkills() }.generatePromptSection()
            val systemPrompt = "$basePrompt$pluginPrompt$skillPrompt\n\n## What I Remember About the User\n$memoryStr"

            // Build conversation with recent history (last 6 messages)
            val conversation = StringBuilder()
            val history = database.getRecentConversations(4)
            if (history.isNotEmpty()) {
                conversation.append("[Recent conversation history:\n")
                for ((role, text) in history) {
                    conversation.append(if (role == "user") "User: " else "AI: ")
                    conversation.append(text.take(150))  // truncate long messages
                    conversation.append("\n")
                }
                conversation.append("]\n\n")
            }
            conversation.append(userMessage)

            // Track tool calls for infinite-loop detection
            val toolCallHistory = mutableListOf<String>()

            var finalReply = ""
            var iteration = 0
            // (workLog declared above try{} so catch can read it)

            ApiUsageTracker.startNewTask()

            while (iteration < MAX_ITERATIONS && !stopRequested) {
                iteration++

                // Call LLM with current conversation state
                val llmResponse = llmClient.chat(conversation.toString(), systemPrompt)

                // If no tool calls, the task is complete
                if (llmResponse.toolCalls.isEmpty()) {
                    finalReply = llmResponse.reply
                    break
                }

                // === INFINITE LOOP DETECTION ===
                // Check if the same ACTION tool (not readScreen/wait) has been called too many times.
                // readScreen() and wait() are verification tools — they're expected to repeat.
                val actionTools = llmResponse.toolCalls.filter { it.name != "readScreen" && it.name != "wait" }
                for (call in actionTools) {
                    val signature = "${call.name}(${call.args.entries.joinToString(",") { "${it.key}=${it.value}" }})"
                    toolCallHistory.add(signature)
                    val count = toolCallHistory.count { it == signature }
                    if (count >= MAX_REPEATED_TOOLS) {
                        finalReply = "I stopped because I tried \"$signature\" $count times — it's not working. I may be stuck on this step.\n\n$workLog\n\nThe task might need a different approach. Try breaking it into smaller steps, or ask me to read the screen and describe what I see."
                        adapter.updateLastMessage(finalReply)
                        voiceManager.speak("I'm stuck on a step. I tried the same action $count times and it's not working.", currentLang)
                        return
                    }
                }

                // LLM wants to call tools — execute them
                val toolSummary = StringBuilder()
                toolSummary.append(if (iteration == 1) llmResponse.reply else "Step $iteration: ${llmResponse.reply}")

                val results = toolExecutor.executeTools(llmResponse.toolCalls)

                val resultsForLLM = StringBuilder()
                resultsForLLM.append("\n\nI executed these tools:\n")

                results.forEachIndexed { i, result ->
                    val call = llmResponse.toolCalls[i]
                    val argsStr = if (call.args.isNotEmpty()) {
                        call.args.entries.joinToString(", ") { "${it.key}=${it.value}" }
                    } else ""
                    toolSummary.append("\n• ${call.name}($argsStr) → ${if (result.success) "✓" else "✗"}")
                    if (!result.success) {
                        toolSummary.append(" ${result.output}")
                    }
                    resultsForLLM.append("Tool: ${call.name}($argsStr)\n")
                    resultsForLLM.append("Result: ${result.output}\n\n")
                }

                if (iteration == 1) {
                    workLog = toolSummary
                } else {
                    workLog.append("\n\n").append(toolSummary)
                }

                // Show iteration progress in the chat
                val callCount = ApiUsageTracker.getTaskCalls()
                // v5.0.8: Remove "Thinking..." placeholder when first step appears
                if (iteration == 1) {
                    adapter.removeLastIfEquals("Thinking...")
                }
                // v5.0.8: ADD a new message per step (was: updateLastMessage which overwrote the same bubble)
                // This way the user can see ALL steps, not just the latest one.
                adapter.addMessage(ChatMessage(
                    text = "[$iteration/$MAX_ITERATIONS] [API: $callCount calls] $toolSummary",
                    isUser = false,
                    status = MessageStatus.THINKING
                ))

                // Append results to conversation for the next LLM call
                conversation.append("\n\n[Tool results from step $iteration]:")
                conversation.append(resultsForLLM)
                conversation.append("\nBased on these results, decide the next step. If the task is complete, reply with just the final message and no tool_calls. If a step failed, try a DIFFERENT approach (don't repeat the same action). If you need to verify, call readScreen().")

                // Trim old history to prevent conversation from growing too long
                if (conversation.length > 8000) {
                    val originalMsg = userMessage
                    val recentHistory = conversation.substring(conversation.length - 6000)
                    conversation.clear()
                    conversation.append(originalMsg)
                    conversation.append("\n\n[Previous steps omitted. Most recent steps:]")
                    conversation.append(recentHistory)
                }

                kotlinx.coroutines.delay(300)
            }

            if (finalReply.isEmpty()) {
                finalReply = if (iteration >= MAX_ITERATIONS && !stopRequested) {
                    "I reached the maximum of $MAX_ITERATIONS steps. Here's what I accomplished:\n$workLog\n\nThe task might need more steps — try asking me to continue, or break it into smaller parts."
                } else if (stopRequested) {
                    workLog.toString()
                } else {
                    workLog.toString()
                }
            }

            // v5.0.8: Add final reply as a NEW message (not updateLastMessage)
            // The step messages above are already separate messages, so the final reply
            // should also be a separate message — not overwriting the last step.
            adapter.addMessage(ChatMessage(
                text = finalReply,
                isUser = false
            ))
            database.addConversation("ai", finalReply)  // SAVE AI reply to database
            database.logAction("Processed: $userMessage → $finalReply")  // Log action
            voiceManager.speak(finalReply.take(500), currentLang)

        } catch (e: kotlinx.coroutines.CancellationException) {
            // User started a new message or pressed stop — not a real error
            if (!stopRequested) {
                val msg = "Task interrupted (a new message was sent or the task was stopped). Last progress:\n$workLog"
                adapter.updateLastMessage(msg)
                database.addConversation("ai", msg)
            }
            throw e  // re-throw so the parent coroutine machinery knows
        } catch (e: Exception) {
            if (!stopRequested) {
                adapter.updateLastMessage("Error: ${e.message}")
                database.addConversation("ai", "Error: ${e.message}")  // Save error too
            }
        } finally {
            updateSendButton()
        }
    }

    private fun openAccessibilitySettings() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility")
            .setMessage("To control your phone, AI Agent needs Accessibility permission.\n\n1. Tap 'Open Settings'\n2. Find 'AI Agent' in the list\n3. Toggle it ON\n4. Return to this app")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        }
    }

    private fun checkAudioPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            voiceManager.startListening()
        } else {
            checkAudioPermission()
            showToast("Please grant microphone permission")
        }
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

}
