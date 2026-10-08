package com.ai.agent.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ai.agent.rules.RuleScheduler
import com.ai.agent.storage.AgentDatabase

/**
 * Rules & Scheduled Tasks — clean list view.
 *
 * Users don't fill in forms here. Instead:
 * - They ask the AI in chat ("every morning at 7am, tell me the weather")
 * - The AI calls createRule() tool → rule saved → appears here
 * - Here, the user can: toggle, delete, or open the chat to ask the AI to modify
 *
 * This screen is for management, not creation.
 */
class RulesActivity : AppCompatActivity() {

    private lateinit var database: AgentDatabase
    private lateinit var scheduler: RuleScheduler
    private lateinit var adapter: RulesAdapter
    private val rules = mutableListOf<AgentDatabase.Rule>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AgentDatabase(this)
        scheduler = RuleScheduler(this)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        // === Header ===
        val header = TextView(this).apply {
            text = "Rules & Schedules"
            textSize = 22f
            setTextColor(0xFFFAFAFA.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        rootLayout.addView(header)

        val subtitle = TextView(this).apply {
            text = "Ask the AI in chat to create rules. Manage them here."
            textSize = 13f
            setTextColor(0xFFA1A1AA.toInt())
            setPadding(0, 0, 0, 24)
        }
        rootLayout.addView(subtitle)

        // === Example prompts ===
        val examplesCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(0xFF1F1F23.toInt())
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 24
            layoutParams = params
        }
        val examplesTitle = TextView(this).apply {
            text = "💬 Try asking the AI:"
            textSize = 14f
            setTextColor(0xFF10B981.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }
        examplesCard.addView(examplesTitle)
        val examplesText = TextView(this).apply {
            text = """
                • "Every morning at 7am, tell me the weather and my calendar"
                • "When WhatsApp messages from Mom arrive, reply 'I'll call back'"
                • "Every weekday at 6pm, remind me to log off work"
                • "When battery drops below 20%, tell me"
                • "When phone starts charging, read my notifications"
            """.trimIndent()
            textSize = 12f
            setTextColor(0xFFD4D4D8.toInt())
            setLineSpacing(4f, 1f)
        }
        examplesCard.addView(examplesText)
        rootLayout.addView(examplesCard)

        // === Open chat button ===
        val openChatBtn = Button(this).apply {
            text = "Open Chat to Create Rule"
            setOnClickListener {
                finish()  // go back to MainActivity
            }
        }
        rootLayout.addView(openChatBtn)

        // Spacer
        rootLayout.addView(TextView(this).apply {
            text = "Your Rules"
            textSize = 16f
            setTextColor(0xFFFAFAFA.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 24, 0, 8)
        })

        // === Rules list ===
        val recyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        adapter = RulesAdapter(
            rules,
            onToggle = { rule, enabled ->
                database.toggleRule(rule.id, enabled)
                if (rule.triggerType == "time") {
                    if (enabled) {
                        scheduler.scheduleTimeRule(database.getRules().find { it.id == rule.id }!!)
                    } else {
                        scheduler.cancelRule(rule.id)
                    }
                }
                loadRules()
            },
            onDelete = { rule ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Rule")
                    .setMessage("Delete \"${rule.name}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        if (rule.triggerType == "time") {
                            scheduler.cancelRule(rule.id)
                        }
                        database.deleteRule(rule.id)
                        loadRules()
                        Toast.makeText(this, "Rule deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        rootLayout.addView(recyclerView)

        // === Notification access link ===
        val notifAccessBtn = Button(this).apply {
            text = "Enable Notification Access (for notification rules)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        rootLayout.addView(notifAccessBtn)

        // === Battery optimization (for time rules to fire in Doze) ===
        val batteryBtn = Button(this).apply {
            text = "Disable Battery Optimization (for reliable scheduling)"
            setOnClickListener {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@RulesActivity, "Cannot open battery settings: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        rootLayout.addView(batteryBtn)

        // Wrap in ScrollView in case list is long
        val scrollView = ScrollView(this).apply {
            addView(rootLayout)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(scrollView)
        loadRules()
    }

    private fun loadRules() {
        rules.clear()
        rules.addAll(database.getRules(enabledOnly = false))
        adapter.notifyDataSetChanged()
    }
}

class RulesAdapter(
    private val rules: List<AgentDatabase.Rule>,
    private val onToggle: (AgentDatabase.Rule, Boolean) -> Unit,
    private val onDelete: (AgentDatabase.Rule) -> Unit
) : RecyclerView.Adapter<RulesAdapter.RuleViewHolder>() {

    class RuleViewHolder(val container: LinearLayout) : RecyclerView.ViewHolder(container) {
        val nameText: TextView = container.getChildAt(0) as TextView
        val triggerText: TextView = container.getChildAt(1) as TextView
        val actionText: TextView = container.getChildAt(2) as TextView
        val toggle: Switch = container.getChildAt(3) as Switch
        val deleteBtn: Button = container.getChildAt(4) as Button
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val context = parent.context
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(0xFF18181B.toInt())
            val params = layoutParams as LinearLayout.LayoutParams
            params.bottomMargin = 12
            layoutParams = params
        }

        val nameText = TextView(context).apply {
            textSize = 16f
            setTextColor(0xFFFAFAFA.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        container.addView(nameText)

        val triggerText = TextView(context).apply {
            textSize = 12f
            setTextColor(0xFF10B981.toInt())
            setPadding(0, 4, 0, 0)
        }
        container.addView(triggerText)

        val actionText = TextView(context).apply {
            textSize = 13f
            setTextColor(0xFFA1A1AA.toInt())
            setPadding(0, 4, 0, 12)
        }
        container.addView(actionText)

        val toggle = Switch(context).apply {
            text = "Enabled"
        }
        container.addView(toggle)

        val deleteBtn = Button(context).apply {
            text = "Delete"
        }
        container.addView(deleteBtn)

        return RuleViewHolder(container)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        val rule = rules[position]
        holder.nameText.text = rule.name
        val triggerLabel = when (rule.triggerType) {
            "time" -> "⏰ ${rule.triggerValue}" + (if (rule.days.isNullOrEmpty()) " (daily)" else " (${rule.days})")
            "notification" -> "🔔 ${rule.triggerValue}"
            "battery_low" -> "🔋 Below ${rule.triggerValue}%"
            "charging" -> "🔌 When charging"
            else -> "▶ ${rule.triggerType}: ${rule.triggerValue}"
        }
        holder.triggerText.text = triggerLabel
        holder.actionText.text = rule.action
        holder.toggle.isChecked = rule.enabled
        holder.toggle.setOnCheckedChangeListener { _, isChecked ->
            onToggle(rule, isChecked)
        }
        holder.deleteBtn.setOnClickListener { onDelete(rule) }
    }

    override fun getItemCount() = rules.size
}
