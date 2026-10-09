package com.ai.agent.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ai.agent.R
import com.ai.agent.storage.AgentDatabase
import kotlinx.coroutines.launch

/**
 * MemoryFragment — browse/search/delete memories + knowledge (v6.0.0).
 * First UI for the vector memory feature.
 */
class MemoryFragment : Fragment() {

    private lateinit var database: AgentDatabase
    private lateinit var container: LinearLayout
    private var showKnowledge = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_memory, container, false)
        database = AgentDatabase(requireContext())
        this.container = view.findViewById(R.id.memoryListContainer)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val searchInput = view.findViewById<EditText>(R.id.memorySearchInput)
        val btnSearch = view.findViewById<Button>(R.id.btnMemorySearch)
        val btnAddMemory = view.findViewById<Button>(R.id.btnAddMemory)
        val btnAddKnowledge = view.findViewById<Button>(R.id.btnAddKnowledge)
        val btnToggleView = view.findViewById<Button>(R.id.btnToggleMemoryKnowledge)

        btnSearch.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isEmpty()) {
                loadAll()
            } else {
                search(query)
            }
        }

        btnAddMemory.setOnClickListener {
            showAddMemoryDialog()
        }

        btnAddKnowledge.setOnClickListener {
            showAddKnowledgeDialog()
        }

        btnToggleView.setOnClickListener {
            showKnowledge = !showKnowledge
            btnToggleView.text = if (showKnowledge) "📋 Knowledge" else "🧠 Memory"
            loadAll()
        }

        loadAll()
    }

    private fun loadAll() {
        container.removeAllViews()
        if (showKnowledge) {
            // Show knowledge
            val all = database.getAllKnowledge()
            if (all.isEmpty()) {
                addEmptyState("No knowledge stored.\nTap 'Add Knowledge' to save a document.")
                return
            }
            addHeader("Knowledge (${all.size} documents)")
            for ((name, content, source) in all) {
                addKnowledgeItem(name, content, source)
            }
        } else {
            // Show memory (facts)
            val all = database.getAllMemory()
            if (all.isEmpty()) {
                addEmptyState("No memories stored.\nTap 'Add Memory' to save a fact.")
                return
            }
            addHeader("Memory (${all.size} facts)")
            for ((key, value) in all) {
                addMemoryItem(key, value)
            }
        }
    }

    private fun search(query: String) {
        container.removeAllViews()
        if (showKnowledge) {
            val results = database.recallKnowledge(query)
            if (results.isEmpty()) {
                addEmptyState("No knowledge found for: $query")
                return
            }
            addHeader("Knowledge results (${results.size}):")
            for ((name, content, score) in results) {
                addKnowledgeItem(name, content, "similarity: ${"%.0f".format(score * 100)}%")
            }
        } else {
            val results = database.recallSimilar(query)
            if (results.isEmpty()) {
                addEmptyState("No similar memories for: $query")
                return
            }
            addHeader("Similar memories (${results.size}):")
            for ((key, value, score) in results) {
                addMemoryItem(key, value, "${"%.0f".format(score * 100)}% match")
            }
        }
    }

    private fun addHeader(text: String) {
        val tv = TextView(requireContext()).apply {
            this.text = text
            setTextColor(requireContext().getColor(R.color.text_primary))
            textSize = 14f
            setPadding(0, 16, 0, 8)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        container.addView(tv)
    }

    private fun addEmptyState(text: String) {
        val tv = TextView(requireContext()).apply {
            this.text = text
            setTextColor(requireContext().getColor(R.color.text_secondary))
            textSize = 12f
            setPadding(0, 48, 0, 0)
            gravity = android.view.Gravity.CENTER
        }
        container.addView(tv)
    }

    private fun addMemoryItem(key: String, value: String, suffix: String = "") {
        val tv = TextView(requireContext()).apply {
            text = "🔑 $key → $value $suffix"
            setTextColor(requireContext().getColor(R.color.text_primary))
            textSize = 12f
            setPadding(12, 12, 12, 12)
            setBackgroundResource(R.color.bg_surface)
            setOnClickListener {
                showMemoryOptions(key, value)
            }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 6)
        tv.layoutParams = params
        container.addView(tv)
    }

    private fun addKnowledgeItem(name: String, content: String, source: String) {
        val preview = if (content.length > 150) content.take(150) + "..." else content
        val tv = TextView(requireContext()).apply {
            text = "📄 $name\n$preview\n($source)"
            setTextColor(requireContext().getColor(R.color.text_primary))
            textSize = 11f
            setPadding(12, 12, 12, 12)
            setBackgroundResource(R.color.bg_surface)
            setOnClickListener {
                showKnowledgeOptions(name, content)
            }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 6)
        tv.layoutParams = params
        container.addView(tv)
    }

    private fun showMemoryOptions(key: String, value: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(key)
            .setMessage(value)
            .setPositiveButton("Delete") { _, _ ->
                database.forgetMemory(key)
                Toast.makeText(requireContext(), "Forgot: $key", Toast.LENGTH_SHORT).show()
                loadAll()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showKnowledgeOptions(name: String, content: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(name)
            .setMessage(content)
            .setPositiveButton("Delete") { _, _ ->
                database.deleteKnowledge(name)
                Toast.makeText(requireContext(), "Deleted: $name", Toast.LENGTH_SHORT).show()
                loadAll()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showAddMemoryDialog() {
        val keyInput = EditText(requireContext()).apply {
            hint = "Key (e.g., name, age, city)"
        }
        val valueInput = EditText(requireContext()).apply {
            hint = "Value (e.g., Arun, 28, Chennai)"
        }
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            addView(keyInput)
            addView(valueInput)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Add Memory")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val key = keyInput.text.toString().trim()
                val value = valueInput.text.toString().trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    database.remember(key, value)
                    Toast.makeText(requireContext(), "Saved: $key", Toast.LENGTH_SHORT).show()
                    loadAll()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddKnowledgeDialog() {
        val nameInput = EditText(requireContext()).apply {
            hint = "Name (e.g., contract_terms, meeting_notes)"
        }
        val contentInput = EditText(requireContext()).apply {
            hint = "Content (paste document text here)"
            inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 5
        }
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            addView(nameInput)
            addView(contentInput)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Add Knowledge")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val content = contentInput.text.toString().trim()
                if (name.isNotEmpty() && content.isNotEmpty()) {
                    database.saveKnowledge(name, content, "manual")
                    Toast.makeText(requireContext(), "Saved knowledge: $name", Toast.LENGTH_SHORT).show()
                    loadAll()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
