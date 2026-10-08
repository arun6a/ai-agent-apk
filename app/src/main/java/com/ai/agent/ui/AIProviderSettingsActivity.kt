package com.ai.agent.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ai.agent.llm.AIProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * AI Provider settings — lets the user choose which AI service to use.
 *
 * The user can:
 * 1. Select a provider (OpenRouter, Gemini, Z.ai proxy, Custom)
 * 2. Enter their API key (for providers that need one)
 * 3. Select a model from the provider's presets
 * 4. Enter a custom endpoint (for Custom provider)
 * 5. Test the connection
 */
class AIProviderSettingsActivity : AppCompatActivity() {

    private lateinit var providerSpinner: Spinner
    private lateinit var apiKeyInput: EditText
    private lateinit var endpointInput: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var statusText: TextView
    private lateinit var providerInfoText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "AI Provider"
            textSize = 24f
            setTextColor(0xFFFAFAFA.toInt())
            setPadding(0, 0, 0, 16)
        })

        layout.addView(TextView(this).apply {
            text = "Choose which AI service powers your agent. " +
                    "Each provider has different models, pricing, and limits.\n\n" +
                    "Recommended: OpenRouter (free, no quota) or Gemini (free tier)."
            textSize = 13f
            setTextColor(0xFFA1A1AA.toInt())
            setPadding(0, 0, 0, 24)
        })

        // === Provider selection ===
        layout.addView(TextView(this).apply {
            text = "Provider"
            textSize = 16f
            setTextColor(0xFF10B981.toInt())
            setPadding(0, 0, 0, 8)
        })

        providerSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@AIProviderSettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                AIProvider.PROVIDERS.map { "${it.name} (${it.id})" }
            )
        }
        layout.addView(providerSpinner)

        providerInfoText = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF71717A.toInt())
            setPadding(0, 8, 0, 24)
        }
        layout.addView(providerInfoText)

        // === API Key ===
        layout.addView(TextView(this).apply {
            text = "API Key"
            textSize = 16f
            setTextColor(0xFF10B981.toInt())
            setPadding(0, 0, 0, 8)
        })

        apiKeyInput = EditText(this).apply {
            hint = "Enter API key (e.g. sk-or-v1-...)"
            inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(0xFFFAFAFA.toInt())
            setHintTextColor(0xFF52525B.toInt())
        }
        layout.addView(apiKeyInput)

        // === Endpoint ===
        layout.addView(TextView(this).apply {
            text = "Endpoint (API URL)"
            textSize = 16f
            setTextColor(0xFF10B981.toInt())
            setPadding(0, 16, 0, 8)
        })

        endpointInput = EditText(this).apply {
            hint = "https://..."
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(0xFFFAFAFA.toInt())
            setHintTextColor(0xFF52525B.toInt())
        }
        layout.addView(endpointInput)

        // === Model selection ===
        layout.addView(TextView(this).apply {
            text = "Model"
            textSize = 16f
            setTextColor(0xFF10B981.toInt())
            setPadding(0, 16, 0, 8)
        })

        modelSpinner = Spinner(this)
        layout.addView(modelSpinner)

        val modelInfoText = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF71717A.toInt())
            setPadding(0, 8, 0, 24)
        }
        layout.addView(modelInfoText)

        // === Status ===
        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF10B981.toInt())
            setPadding(0, 16, 0, 16)
        }
        layout.addView(statusText)

        // === Save button ===
        layout.addView(Button(this).apply {
            text = "Save Settings"
            setOnClickListener {
                saveSettings()
            }
        })

        // === Test button ===
        layout.addView(Button(this).apply {
            text = "Test Connection"
            setOnClickListener {
                saveSettings()
                testConnection()
            }
        })

        // === Get API key links ===
        layout.addView(TextView(this).apply {
            text = "\nGet API Keys:"
            textSize = 14f
            setTextColor(0xFFFAFAFA.toInt())
            setPadding(0, 24, 0, 8)
        })
        layout.addView(TextView(this).apply {
            text = "• OpenRouter (free): openrouter.ai/keys\n" +
                    "• Gemini (free): aistudio.google.com/apikey\n" +
                    "• Z.ai Proxy: no key needed (uses sandbox)"
            textSize = 12f
            setTextColor(0xFFA1A1AA.toInt())
        })

        // Load current settings
        loadSettings()

        // Update model list when provider changes
        providerSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateProviderInfo()
                updateModelList()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Update model info when model changes
        modelSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val provider = AIProvider.PROVIDERS[providerSpinner.selectedItemPosition]
                if (position < provider.models.size) {
                    val model = provider.models[position]
                    modelInfoText.text = model.description
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun loadSettings() {
        val providerId = AIProvider.getProviderId(this)
        val providerIndex = AIProvider.PROVIDERS.indexOfFirst { it.id == providerId }
        if (providerIndex >= 0) {
            providerSpinner.setSelection(providerIndex)
        }

        apiKeyInput.setText(AIProvider.getApiKey(this))
        endpointInput.setText(AIProvider.getEndpoint(this))

        updateProviderInfo()
        updateModelList()

        // Set model selection after updating list
        val currentModel = AIProvider.getModel(this)
        val provider = AIProvider.getCurrentProvider(this)
        val modelIndex = provider.models.indexOfFirst { it.id == currentModel }
        if (modelIndex >= 0) {
            modelSpinner.setSelection(modelIndex)
        }

        updateStatus()
    }

    private fun updateProviderInfo() {
        val provider = AIProvider.PROVIDERS[providerSpinner.selectedItemPosition]
        providerInfoText.text = provider.description

        // Update endpoint default if empty
        if (endpointInput.text.isBlank() || endpointInput.text.toString().startsWith("https://openrouter") ||
            endpointInput.text.toString().startsWith("https://generativelanguage") ||
            endpointInput.text.toString().startsWith("https://preview-chat") ||
            endpointInput.text.toString().contains("api/llm/proxy")) {
            endpointInput.setText(provider.defaultEndpoint)
        }

        // Show/hide API key field
        apiKeyInput.isEnabled = provider.needsApiKey
        apiKeyInput.alpha = if (provider.needsApiKey) 1.0f else 0.5f
        if (!provider.needsApiKey) {
            apiKeyInput.hint = "Not required for ${provider.name}"
        } else {
            apiKeyInput.hint = "Enter API key (e.g. sk-or-v1-...)"
        }
    }

    private fun updateModelList() {
        val provider = AIProvider.PROVIDERS[providerSpinner.selectedItemPosition]
        val models = provider.models.map { "${it.name} (${it.id})" }
        modelSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            models
        )
    }

    private fun updateStatus() {
        val provider = AIProvider.getCurrentProvider(this)
        val configured = AIProvider.isConfigured(this)
        statusText.text = if (configured) {
            "✅ Ready: ${provider.name} / ${AIProvider.getModel(this)}"
        } else {
            "❌ Not configured: ${provider.name} needs an API key"
        }
    }

    private fun saveSettings() {
        val providerIndex = providerSpinner.selectedItemPosition
        val provider = AIProvider.PROVIDERS[providerIndex]
        val modelIndex = modelSpinner.selectedItemPosition
        val model = provider.models.getOrNull(modelIndex)?.id ?: provider.models.first().id

        AIProvider.setProvider(this, provider.id)
        AIProvider.setApiKey(this, apiKeyInput.text.toString().trim())
        AIProvider.setEndpoint(this, endpointInput.text.toString().trim())
        AIProvider.setModel(this, model)

        updateStatus()
        Toast.makeText(this, "Saved: ${provider.name} / $model", Toast.LENGTH_SHORT).show()
    }

    private fun testConnection() {
        Toast.makeText(this, "Testing connection...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val provider = AIProvider.getCurrentProvider(this)
                val apiKey = AIProvider.getApiKey(this)
                val endpoint = AIProvider.getEndpoint(this)
                val model = AIProvider.getModel(this)

                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val testUrl = when (provider.id) {
                    "gemini" -> "$endpoint/models/$model:generateContent?key=$apiKey"
                    else -> endpoint
                }

                val testBody = when (provider.id) {
                    "gemini" -> """{"contents":[{"parts":[{"text":"Say OK"}]}]}"""
                    else -> """{"model":"$model","messages":[{"role":"user","content":"Say OK"}]}"""
                }

                val requestBody = testBody.toRequestBody("application/json".toMediaType())

                val requestBuilder = okhttp3.Request.Builder()
                    .url(testUrl)
                    .header("Content-Type", "application/json")
                    .post(requestBody)

                when (provider.id) {
                    "openrouter" -> {
                        requestBuilder.header("Authorization", "Bearer $apiKey")
                        requestBuilder.header("HTTP-Referer", "https://github.com/arun6a/ai-agent-apk")
                        requestBuilder.header("X-Title", "AI Agent Phone")
                    }
                    "custom" -> {
                        if (apiKey.isNotEmpty()) {
                            requestBuilder.header("Authorization", "Bearer $apiKey")
                        }
                    }
                }

                val response = client.newCall(requestBuilder.build()).execute()
                val body = response.body?.string() ?: ""
                val success = response.isSuccessful

                runOnUiThread {
                    if (success) {
                        Toast.makeText(
                            this,
                            "✅ Connected! Response: ${body.take(100)}",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            "❌ Failed (${response.code}): ${body.take(150)}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
