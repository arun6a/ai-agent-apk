package com.ai.agent.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ai.agent.MainActivity
import com.ai.agent.R
import com.ai.agent.llm.AIProvider
import com.ai.agent.storage.AgentDatabase
import kotlinx.coroutines.launch

/**
 * AgentFragment — mission control dashboard (v6.0.0).
 * Shows: status, provider, API usage, quick toggles, browser/skills/plugins entry.
 */
class AgentFragment : Fragment() {

    private lateinit var database: AgentDatabase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_agent, container, false)
        database = AgentDatabase(requireContext())
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val statusText = view.findViewById<TextView>(R.id.agentStatusText)
        val providerText = view.findViewById<TextView>(R.id.agentProviderText)
        val usageText = view.findViewById<TextView>(R.id.agentUsageText)

        // Status
        val prefs = AIProvider.getPrefs(requireContext())
        val provider = AIProvider.getCurrentProvider(requireContext())
        val model = AIProvider.getModel(requireContext())
        statusText.text = "Status: Ready"
        providerText.text = "Provider: ${provider.name}\nModel: $model"

        // API usage today
        val (calls, tokens) = com.ai.agent.llm.ApiUsageTracker.getTodayStats(requireContext())
        usageText.text = "Today: $calls calls · $tokens tokens"

        // Quick actions
        view.findViewById<Button>(R.id.btnOpenBrowser).setOnClickListener {
            val intent = android.content.Intent(requireContext(), BrowserActivity::class.java)
            startActivity(intent)
        }
        view.findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            // Switch to Settings tab
            (activity as? MainActivity)?.switchToSettings()
        }
        view.findViewById<Button>(R.id.btnCheckAccessibility).setOnClickListener {
            val enabled = com.ai.agent.accessibility.AgentAccessibilityService.isEnabled(requireContext())
            Toast.makeText(requireContext(), "Accessibility: ${if (enabled) "ON ✓" else "OFF ✗"}", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.btnCheckOverlay).setOnClickListener {
            val enabled = android.provider.Settings.canDrawOverlays(requireContext())
            Toast.makeText(requireContext(), "Overlay: ${if (enabled) "ON ✓" else "OFF ✗"}", Toast.LENGTH_SHORT).show()
        }

        // Skills + Plugins count
        val skillsText = view.findViewById<TextView>(R.id.agentSkillsText)
        try {
            val skillManager = com.ai.agent.skills.SkillManager(requireContext()).also { it.loadSkills() }
            val pluginCount = requireContext().assets.list("plugins")?.size ?: 0
            skillsText.text = "Skills: ${skillManager.getSkillCount()} · Plugins: $pluginCount"
        } catch (e: Exception) {
            skillsText.text = "Skills/Plugins: (error loading)"
        }

        // Memory + Knowledge count
        val memoryText = view.findViewById<TextView>(R.id.agentMemoryText)
        lifecycleScope.launch {
            try {
                val memoryCount = database.getAllMemory().size
                val knowledgeCount = database.getAllKnowledge().size
                memoryText.text = "Memory: $memoryCount facts · Knowledge: $knowledgeCount docs"
            } catch (e: Exception) {
                memoryText.text = "Memory: (error)"
            }
        }
    }
}
