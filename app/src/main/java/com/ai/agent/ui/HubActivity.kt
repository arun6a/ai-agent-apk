package com.ai.agent.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ai.agent.Config
import com.ai.agent.R
import com.ai.agent.storage.AgentDatabase

/**
 * HubActivity (v6.1.3) — single screen combining:
 * - Skills (browse, create, delete)
 * - Rules (list, create, delete)
 * - Tools (all 100+ tools grouped by category)
 * - Plugins (toggle on/off)
 * - Settings (launch SettingsActivity)
 *
 * Replaces the old rulesBtn + settingsBtn in the header.
 */
class HubActivity : AppCompatActivity() {

    private lateinit var database: AgentDatabase
    private var currentSection = "skills"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AgentDatabase(this)

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 32)
        }
        scroll.addView(layout)
        setContentView(scroll)

        // Title
        val title = TextView(this).apply {
            text = "🤖 AI Agent Hub"
            textSize = 20f
            setTextColor(getColor(R.color.text_primary))
            setPadding(0, 0, 0, 16)
        }
        layout.addView(title)

        // Version
        val versionText = TextView(this).apply {
            text = "Version ${Config.VERSION} • ${com.ai.agent.tools.ToolExecutor.getAvailableToolNames().size} tools available"
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, 0, 0, 24)
        }
        layout.addView(versionText)

        // Section buttons
        val sectionLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        val sections = listOf(
            "skills" to "📋 Skills", "rules" to "⏰ Rules", "tools" to "🔧 Tools",
            "plugins" to "🔌 Plugins", "memory" to "🧠 Memory", "knowledge" to "📚 Knowledge",
            "history" to "💬 History", "files" to "📁 Files", "settings" to "⚙️ Settings"
        )
        for ((id, label) in sections) {
            val btn = Button(this).apply {
                text = label
                textSize = 10f
                setOnClickListener {
                    currentSection = id
                    layout.removeViewAt(layout.childCount - 1) // remove content
                    layout.addView(buildSection(id))
                }
            }
            sectionLayout.addView(btn)
        }
        layout.addView(sectionLayout)

        // Content area
        layout.addView(buildSection(currentSection))
    }

    private fun buildSection(section: String): View {
        return when (section) {
            "skills" -> buildSkillsSection()
            "rules" -> buildRulesSection()
            "tools" -> buildToolsSection()
            "plugins" -> buildPluginsSection()
            "memory" -> buildMemorySection()
            "knowledge" -> buildKnowledgeSection()
            "history" -> buildHistorySection()
            "files" -> buildFilesSection()
            "settings" -> buildSettingsSection()
            else -> TextView(this).apply { text = "Unknown section" }
        }
    }

    // === SKILLS ===
    private fun buildSkillsSection(): View {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        try {
            val skillManager = com.ai.agent.skills.SkillManager(this).also { it.loadSkills() }
            val allSkills = skillManager.getSkills()
            layout.addView(infoText("Loaded ${allSkills.size} skills. Tap to view, long-press to delete.\n"))

            for (skill in allSkills) {
                val btn = Button(this).apply {
                    text = "📝 ${skill.name}\n   Trigger: ${skill.triggers.joinToString(" | ")}"
                    textSize = 10f
                    setOnClickListener {
                        AlertDialog.Builder(this@HubActivity)
                            .setTitle("Skill: ${skill.name}")
                            .setMessage("Description: ${skill.description}\n\nTriggers: ${skill.triggers.joinToString(", ")}\n\nTools: ${skill.toolsUsed.joinToString(", ")}\n\nInstructions:\n${skill.body}")
                            .setPositiveButton("Close", null)
                            .setNegativeButton("Delete") { _, _ ->
                                // v6.2.0: Delete skill
                                val userSkillFile = java.io.File("/storage/emulated/0/Documents/ai-workspace/skills/${skill.name.replace("-", "_")}.md")
                                if (userSkillFile.exists()) {
                                    userSkillFile.delete()
                                    Toast.makeText(this@HubActivity, "Skill '${skill.name}' deleted!", Toast.LENGTH_SHORT).show()
                                    layout.removeViewAt(layout.childCount - 1)
                                    layout.addView(buildSkillsSection())
                                } else {
                                    Toast.makeText(this@HubActivity, "Built-in skills cannot be deleted (they're in the APK)", Toast.LENGTH_LONG).show()
                                }
                            }
                            .show()
                    }
                }
                layout.addView(btn)
            }

            layout.addView(Button(this).apply {
                text = "➕ Create Skill"
                setOnClickListener { showCreateSkillDialog(layout) }
            })

            // v6.2.0: Show user-created skills folder info
            val userSkillsDir = java.io.File("/storage/emulated/0/Documents/ai-workspace/skills")
            val userSkillCount = userSkillsDir.listFiles { f -> f.extension == "md" }?.size ?: 0
            layout.addView(infoText("\n📂 User skills folder: ${userSkillsDir.absolutePath}\n   $userSkillCount user-created skills"))
        } catch (e: Exception) {
            layout.addView(infoText("Error: ${e.message}"))
        }
        return layout
    }

    private fun showCreateSkillDialog(parentLayout: LinearLayout) {
        val nameInput = android.widget.EditText(this).apply { hint = "Skill name (e.g., morning-coffee)" }
        val triggerInput = android.widget.EditText(this).apply { hint = "Trigger words (e.g., coffee|morning coffee)" }
        val instructionsInput = android.widget.EditText(this).apply {
            hint = "Instructions (what tools to call)"
            minLines = 4
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            addView(nameInput)
            addView(triggerInput)
            addView(instructionsInput)
        }
        AlertDialog.Builder(this)
            .setTitle("Create Skill")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val name = nameInput.text.toString().trim()
                val trigger = triggerInput.text.toString().trim()
                val instructions = instructionsInput.text.toString().trim()
                if (name.isNotEmpty() && trigger.isNotEmpty() && instructions.isNotEmpty()) {
                    val skillsDir = java.io.File("/storage/emulated/0/Documents/ai-workspace/skills")
                    if (!skillsDir.exists()) skillsDir.mkdirs()
                    val file = java.io.File(skillsDir, "${name.replace(" ", "_")}.md")
                    file.writeText("---\nname: ${name.replace(" ", "-")}\ndescription: User-created skill\ntrigger: $trigger\nversion: 1.0\n---\n# ${name}\n\n$instructions\n")
                    Toast.makeText(this, "Skill '$name' created!", Toast.LENGTH_SHORT).show()
                    parentLayout.removeViewAt(parentLayout.childCount - 1)
                    parentLayout.addView(buildSkillsSection())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // === RULES ===
    private fun buildRulesSection(): View {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        try {
            val rules = database.getRules(enabledOnly = false)
            if (rules.isEmpty()) {
                layout.addView(infoText("No rules yet. Ask the AI: 'remind me to X at Y'\n"))
            } else {
                layout.addView(infoText("${rules.size} rules:\n"))
                for (rule in rules) {
                    val btn = Button(this).apply {
                        text = "${if (rule.enabled) "✅" else "⏸"} ${rule.name}\n   ${rule.triggerType}: ${rule.triggerValue}\n   Action: ${rule.action.take(80)}"
                        textSize = 10f
                        setOnClickListener {
                            AlertDialog.Builder(this@HubActivity)
                                .setTitle(rule.name)
                                .setMessage("Trigger: ${rule.triggerType} (${rule.triggerValue})\nAction: ${rule.action}\nDays: ${rule.days ?: "daily"}\nEnabled: ${rule.enabled}")
                                .setPositiveButton("Delete") { _, _ ->
                                    database.deleteRule(rule.id)
                                    Toast.makeText(this@HubActivity, "Deleted: ${rule.name}", Toast.LENGTH_SHORT).show()
                                    layout.removeViewAt(layout.childCount - 1)
                                    layout.addView(buildRulesSection())
                                }
                                .setNegativeButton("Close", null)
                                .show()
                        }
                    }
                    layout.addView(btn)
                }
            }
            layout.addView(Button(this).apply {
                text = "Open Rules Manager →"
                setOnClickListener {
                    startActivity(Intent(this@HubActivity, RulesActivity::class.java))
                }
            })
        } catch (e: Exception) {
            layout.addView(infoText("Error: ${e.message}"))
        }
        return layout
    }

    // === TOOLS ===
    private fun buildToolsSection(): View {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolCategories = mapOf(
            "Screen" to listOf("readScreen", "readScreenStructured", "tap", "clickByText", "type", "swipe", "scrollDown", "scrollUp", "pressBack", "pressHome", "pressEnter", "submitInput", "lockScreen"),
            "Apps" to listOf("launchApp", "listInstalledApps", "getAppInfo", "forceStopApp", "openSettings", "openAppSettings"),
            "Browser" to listOf("browserOpen", "browserReadStructured", "browserClickText", "browserClickElement", "browserFillForm", "browserGetLinks", "browserGetForms", "browserListClickable", "browserScreenshot", "browserScrollDown", "browserEval", "browserBack", "browserGetUrl", "browserSearch"),
            "Web" to listOf("webSearch", "fetchPageText", "makeHttpRequest", "downloadFile", "openUrl"),
            "Memory" to listOf("remember", "recall", "recallAll", "recallSimilar", "searchMemory"),
            "Knowledge" to listOf("saveToKnowledge", "recallKnowledge", "listKnowledge"),
            "Files" to listOf("listFiles", "readFile", "writeFile", "copyFile", "moveFile", "deleteFile", "createDirectory", "listAttachments", "readAttachment", "sendAttachmentTo", "shareFile"),
            "Rules" to listOf("createRule", "listRules", "deleteRule", "modifyRule"),
            "Contacts" to listOf("readContacts", "searchContacts", "callContact", "sendSMS", "getCallLog", "sendEmail", "openDialer", "makePhoneCall", "composeEmail"),
            "Calendar" to listOf("getCalendarEvents", "createCalendarEvent", "setAlarm", "setTimer"),
            "Device" to listOf("getBatteryLevel", "getCurrentTime", "getBluetoothState", "getNetworkInfo", "getDeviceInfo", "pingHost", "getVolume", "setVolume", "setBrightness", "toggleFlashlight", "toggleWifi"),
            "Location" to listOf("getCurrentLocation", "openMaps", "openMapsLocation"),
            "Media" to listOf("mediaPlayPause", "mediaNext", "mediaPrevious", "takePhoto", "takeScreenshotToGallery"),
            "Vision" to listOf("analyzeScreen", "findElement", "translateText"),
            "Notification" to listOf("replyToNotification"),
            "Activity Shortcuts" to listOf("searchInApp", "openWhatsAppChat", "openYouTubeVideo", "playSpotify", "openInstagramProfile", "openTelegramChat", "openGoogleSearch", "shareToApp", "openActivity", "openDeepLink"),
            "Skills" to listOf("executeSkill", "listSkills", "createSkill", "deleteSkill", "getSkill"),
            "Async" to listOf("wait", "waitAndContinue")
        )
        var total = 0
        for ((category, tools) in toolCategories) {
            total += tools.size
            layout.addView(infoText("📁 $category (${tools.size}):\n${tools.joinToString(", ")}\n"))
        }
        layout.addView(infoText("\n📊 Total: $total tools"))
        return layout
    }

    // === PLUGINS ===
    private fun buildPluginsSection(): View {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        try {
            val report = com.ai.agent.llm.PluginManager.getStatusReport(this)
            layout.addView(infoText("$report\n"))
            layout.addView(Button(this).apply {
                text = "🔄 Reload Plugins"
                setOnClickListener {
                    com.ai.agent.llm.PluginManager.reload(this@HubActivity)
                    Toast.makeText(this@HubActivity, "Plugins reloaded", Toast.LENGTH_SHORT).show()
                }
            })
            // List plugin files
            val plugins = assets.list("plugins") ?: emptyArray()
            for (pluginFile in plugins) {
                layout.addView(infoText("📄 $pluginFile"))
            }
        } catch (e: Exception) {
            layout.addView(infoText("Error: ${e.message}"))
        }
        return layout
    }

    // === SETTINGS ===
    private fun buildSettingsSection(): View {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(infoText("Quick settings:\n"))
        layout.addView(Button(this).apply {
            text = "⚙️ Full Settings →"
            setOnClickListener { startActivity(Intent(this@HubActivity, SettingsActivity::class.java)) }
        })
        layout.addView(Button(this).apply {
            text = "🤖 AI Provider →"
            setOnClickListener { startActivity(Intent(this@HubActivity, AIProviderSettingsActivity::class.java)) }
        })
        layout.addView(Button(this).apply {
            text = "🌐 Browser →"
            setOnClickListener { startActivity(Intent(this@HubActivity, BrowserActivity::class.java)) }
        })
        layout.addView(Button(this).apply {
            text = "🗑️ Clear Chat History"
            setOnClickListener {
                AlertDialog.Builder(this@HubActivity)
                    .setTitle("Clear History")
                    .setMessage("Delete all chat history?")
                    .setPositiveButton("Clear") { _, _ ->
                        database.clearConversations()
                        Toast.makeText(this@HubActivity, "History cleared", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })
        // Memory count
        val memoryCount = database.getAllMemory().size
        val knowledgeCount = database.getAllKnowledge().size
        layout.addView(infoText("\n🧠 Memory: $memoryCount facts\n📚 Knowledge: $knowledgeCount docs"))
        return layout
    }

    // === MEMORY (v6.2.0) ===
    private fun buildMemorySection(): View {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        try {
            val allMemory = database.getAllMemory()
            if (allMemory.isEmpty()) {
                layout.addView(infoText("No memories stored yet.\n\nAsk the AI: 'remember my name is Arun'"))
            } else {
                layout.addView(infoText("${allMemory.size} memories:\n"))
                for ((key, value) in allMemory) {
                    val btn = Button(this).apply {
                        text = "🔑 $key → $value"
                        textSize = 10f
                        setOnClickListener {
                            AlertDialog.Builder(this@HubActivity)
                                .setTitle(key)
                                .setMessage(value)
                                .setPositiveButton("Close", null)
                                .setNegativeButton("Forget") { _, _ ->
                                    database.forgetMemory(key)
                                    Toast.makeText(this@HubActivity, "Forgot: $key", Toast.LENGTH_SHORT).show()
                                    layout.removeViewAt(layout.childCount - 1)
                                    layout.addView(buildMemorySection())
                                }
                                .show()
                        }
                    }
                    layout.addView(btn)
                }
            }
            layout.addView(Button(this).apply {
                text = "🗑️ Clear All Memory"
                setOnClickListener {
                    AlertDialog.Builder(this@HubActivity)
                        .setTitle("Clear All Memory")
                        .setMessage("Delete ALL memories? This cannot be undone.")
                        .setPositiveButton("Clear All") { _, _ ->
                            for ((key, _) in database.getAllMemory()) {
                                database.forgetMemory(key)
                            }
                            Toast.makeText(this@HubActivity, "All memory cleared", Toast.LENGTH_SHORT).show()
                            layout.removeViewAt(layout.childCount - 1)
                            layout.addView(buildMemorySection())
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            })
        } catch (e: Exception) {
            layout.addView(infoText("Error: ${e.message}"))
        }
        return layout
    }

    // === KNOWLEDGE (v6.2.0) ===
    private fun buildKnowledgeSection(): View {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        try {
            val allKnowledge = database.getAllKnowledge()
            if (allKnowledge.isEmpty()) {
                layout.addView(infoText("No knowledge stored yet.\n\nAsk the AI: 'save this as knowledge: <text>'\nOr attach a document and ask to save it."))
            } else {
                layout.addView(infoText("${allKnowledge.size} knowledge documents:\n"))
                for ((name, content, source) in allKnowledge) {
                    val btn = Button(this).apply {
                        text = "📄 $name\n   ${content.take(80)}... ($source)"
                        textSize = 10f
                        setOnClickListener {
                            AlertDialog.Builder(this@HubActivity)
                                .setTitle(name)
                                .setMessage("Source: $source\n\nContent:\n$content")
                                .setPositiveButton("Close", null)
                                .setNegativeButton("Delete") { _, _ ->
                                    database.deleteKnowledge(name)
                                    Toast.makeText(this@HubActivity, "Deleted: $name", Toast.LENGTH_SHORT).show()
                                    layout.removeViewAt(layout.childCount - 1)
                                    layout.addView(buildKnowledgeSection())
                                }
                                .show()
                        }
                    }
                    layout.addView(btn)
                }
            }
            layout.addView(Button(this).apply {
                text = "🗑️ Clear All Knowledge"
                setOnClickListener {
                    AlertDialog.Builder(this@HubActivity)
                        .setTitle("Clear All Knowledge")
                        .setMessage("Delete ALL knowledge documents?")
                        .setPositiveButton("Clear All") { _, _ ->
                            for ((name, _, _) in database.getAllKnowledge()) {
                                database.deleteKnowledge(name)
                            }
                            Toast.makeText(this@HubActivity, "All knowledge cleared", Toast.LENGTH_SHORT).show()
                            layout.removeViewAt(layout.childCount - 1)
                            layout.addView(buildKnowledgeSection())
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            })
        } catch (e: Exception) {
            layout.addView(infoText("Error: ${e.message}"))
        }
        return layout
    }

    // === HISTORY (v6.2.0) ===
    private fun buildHistorySection(): View {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        try {
            val history = database.getRecentConversations(50)
            if (history.isEmpty()) {
                layout.addView(infoText("No chat history yet.\n\nStart chatting with the AI to build history."))
            } else {
                layout.addView(infoText("${history.size} recent messages:\n"))
                for ((role, text) in history) {
                    val prefix = if (role == "user") "👤" else "🤖"
                    val tv = TextView(this).apply {
                        this.text = "$prefix ${text.take(150)}${if (text.length > 150) "..." else ""}"
                        textSize = 11f
                        setTextColor(getColor(R.color.text_secondary))
                        setPadding(8, 8, 8, 8)
                        setBackgroundResource(R.color.bg_surface)
                    }
                    val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    params.setMargins(0, 0, 0, 4)
                    tv.layoutParams = params
                    layout.addView(tv)
                }
                layout.addView(Button(this).apply {
                    text = "🗑️ Clear All History"
                    setOnClickListener {
                        AlertDialog.Builder(this@HubActivity)
                            .setTitle("Clear History")
                            .setMessage("Delete all chat history?")
                            .setPositiveButton("Clear") { _, _ ->
                                database.clearConversations()
                                Toast.makeText(this@HubActivity, "History cleared", Toast.LENGTH_SHORT).show()
                                layout.removeViewAt(layout.childCount - 1)
                                layout.addView(buildHistorySection())
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                })
            }
        } catch (e: Exception) {
            layout.addView(infoText("Error: ${e.message}"))
        }
        return layout
    }

    // === FILES (v6.2.0) ===
    private fun buildFilesSection(): View {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val basePath = "/storage/emulated/0/Documents/ai-workspace"
        val folders = listOf("attachments", "skills", "knowledge", "downloads", "screenshots")

        layout.addView(infoText("App workspace: $basePath\n"))
        for (folder in folders) {
            val dir = java.io.File("$basePath/$folder")
            val files = if (dir.exists()) dir.listFiles() ?: emptyArray() else emptyArray()
            val totalSize = files.sumOf { it.length() }
            val sizeKb = totalSize / 1024

            layout.addView(infoText("📁 $folder/ (${files.size} files, ${sizeKb}KB)"))
            for (file in files.take(10)) {
                val fileSizeKb = file.length() / 1024
                layout.addView(infoText("   📄 ${file.name} (${fileSizeKb}KB)"))
            }
            if (files.size > 10) {
                layout.addView(infoText("   ... +${files.size - 10} more"))
            }
            layout.addView(infoText(""))
        }
        return layout
    }

    private fun infoText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(8, 8, 8, 8)
        }
    }
}
