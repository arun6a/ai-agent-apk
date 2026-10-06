package com.ai.agent.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ai.agent.R
import com.ai.agent.rules.RuleEngine
import com.ai.agent.storage.AgentDatabase

class RulesActivity : AppCompatActivity() {

    private lateinit var ruleEngine: RuleEngine
    private lateinit var database: AgentDatabase
    private lateinit var adapter: RulesAdapter
    private val rules = mutableListOf<AgentDatabase.Rule>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ruleEngine = RuleEngine(this)
        database = AgentDatabase(this)

        // Build UI programmatically (no XML layout needed)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        // Title
        val title = TextView(this).apply {
            text = "Rules & Scheduled Tasks"
            textSize = 20f
            setTextColor(0xFFFAFAFA.toInt())
            setPadding(0, 0, 0, 24)
        }
        layout.addView(title)

        // Add rule button
        val addBtn = Button(this).apply {
            text = "+ Add New Rule"
            setOnClickListener { showAddRuleDialog() }
        }
        layout.addView(addBtn)

        // Rules list
        val recyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        adapter = RulesAdapter(
            rules,
            onToggle = { rule, enabled ->
                ruleEngine.toggleRule(rule.id, enabled)
                loadRules()
            },
            onDelete = { rule ->
                AlertDialog.Builder(this)
                    .setTitle("Delete Rule")
                    .setMessage("Delete \"${rule.name}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        ruleEngine.deleteRule(rule.id)
                        loadRules()
                        Toast.makeText(this, "Rule deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        layout.addView(recyclerView)

        // Help text
        val help = TextView(this).apply {
            text = """
                How rules work:

                • Time-based: triggers at a specific time daily
                  Example: "07:00" → every day at 7:00 AM

                • Notification-based: triggers when a notification arrives
                  Example: "com.whatsapp" → any WhatsApp notification
                  Example: "com.whatsapp:Salman" → WhatsApp from Salman

                The action is a natural language command that the AI will execute.
                Example: "read my notifications and summarize them"

                Enable Notification Access in Settings for notification rules to work.
            """.trimIndent()
            textSize = 12f
            setTextColor(0xFFA1A1AA.toInt())
            setPadding(0, 48, 0, 0)
        }
        layout.addView(help)

        setContentView(layout)
        loadRules()
    }

    private fun loadRules() {
        rules.clear()
        rules.addAll(database.getRules(enabledOnly = false))
        adapter.notifyDataSetChanged()
    }

    private fun showAddRuleDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        val nameInput = EditText(this).apply {
            hint = "Rule name (e.g., Morning Briefing)"
        }
        dialogView.addView(nameInput)

        val triggerTypeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@RulesActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("time", "notification")
            )
        }
        dialogView.addView(triggerTypeSpinner)

        val triggerLabel = TextView(this).apply {
            text = "Time (HH:MM) or notification trigger (package or package:sender):"
            setPadding(0, 16, 0, 0)
        }
        dialogView.addView(triggerLabel)

        val triggerInput = EditText(this).apply {
            hint = "e.g., 07:00  or  com.whatsapp:Salman"
        }
        dialogView.addView(triggerInput)

        val actionLabel = TextView(this).apply {
            text = "What should the AI do? (natural language)"
            setPadding(0, 16, 0, 0)
        }
        dialogView.addView(actionLabel)

        val actionInput = EditText(this).apply {
            hint = "e.g., Read my notifications and summarize them"
        }
        dialogView.addView(actionInput)

        AlertDialog.Builder(this)
            .setTitle("Add New Rule")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val triggerType = triggerTypeSpinner.selectedItem.toString()
                val triggerValue = triggerInput.text.toString().trim()
                val action = actionInput.text.toString().trim()

                if (name.isEmpty() || triggerValue.isEmpty() || action.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (triggerType == "time") {
                    ruleEngine.addTimeRule(name, triggerValue, action)
                } else {
                    ruleEngine.addNotificationRule(name, triggerValue, action)
                }

                loadRules()
                Toast.makeText(this, "Rule added!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
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
        }
        container.addView(triggerText)

        val actionText = TextView(context).apply {
            textSize = 13f
            setTextColor(0xFFA1A1AA.toInt())
            setPadding(0, 4, 0, 8)
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
        holder.triggerText.text = "▶ ${rule.triggerType}: ${rule.triggerValue}"
        holder.actionText.text = rule.action
        holder.toggle.isChecked = rule.enabled
        holder.toggle.setOnCheckedChangeListener { _, isChecked ->
            onToggle(rule, isChecked)
        }
        holder.deleteBtn.setOnClickListener { onDelete(rule) }
    }

    override fun getItemCount() = rules.size
}
