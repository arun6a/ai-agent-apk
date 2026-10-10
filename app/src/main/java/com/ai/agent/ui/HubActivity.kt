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
        val sections = listOf("skills" to "📋 Skills", "rules" to "⏰ Rules", "tools" to "🔧 Tools", "plugins" to "🔌 Plugins", "settings" to "⚙️ Settings")
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
            layout.addView(infoText("Loaded ${allSkills.size} skills. Tap to view instructions.\n"))

            for (skill in allSkills) {
                val btn = Button(this).apply {
                    text = "📝 ${skill.name}\n   Trigger: ${skill.triggers.joinToString(" | ")}"
                    textSize = 10f
                    setOnClickListener {
                        AlertDialog.Builder(this@HubActivity)
                            .setTitle("Skill: ${skill.name}")
                            .setMessage("Description: ${skill.description}\n\nTriggers: ${skill.triggers.joinToString(", ")}\n\nTools: ${skill.toolsUsed.joinToString(", ")}\n\nInstructions:\n${skill.body}")
                            .setPositiveButton("Close", null)
                            .show()
                    }
                }
                layout.addView(btn)
            }

            layout.addView(Button(this).apply {
                text = "➕ Create Skill"
                setOnClickListener { showCreateSkillDialog(layout) }
            })
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

    private fun infoText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
            setPadding(8, 8, 8, 8)
        }
    }
}
