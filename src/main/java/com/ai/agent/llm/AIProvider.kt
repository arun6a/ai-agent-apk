package com.ai.agent.llm

import android.content.Context
import android.content.SharedPreferences

/**
 * AI Provider configuration — lets the user pick which AI service to use.
 *
 * Supported providers:
 * - OpenRouter: Free models (Nemotron 120B, Cohere, etc.). OpenAI-compatible. https://openrouter.ai
 * - Gemini: Google's AI. Free tier available. https://aistudio.google.com
 * - Z.ai Proxy: Uses the sandbox proxy (no API key needed, but sandbox must be running)
 * - Custom: User enters any OpenAI-compatible endpoint + key + model
 *
 * The config is stored in SharedPreferences so it persists across app restarts.
 */
object AIProvider {

    private const val PREFS = "ai_agent_provider"
    private const val KEY_PROVIDER = "provider"          // "openrouter" | "gemini" | "zai" | "custom"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_MODEL = "model"

    // === Provider presets ===
    data class ProviderInfo(
        val id: String,
        val name: String,
        val description: String,
        val defaultEndpoint: String,
        val needsApiKey: Boolean,
        val models: List<ModelInfo>
    )

    data class ModelInfo(
        val id: String,
        val name: String,
        val description: String
    )

    val PROVIDERS = listOf(
        ProviderInfo(
            id = "openrouter",
            name = "OpenRouter (Free)",
            description = "Free models including Nemotron 120B, Cohere, and more. " +
                    "Get a free API key at openrouter.ai/keys. No daily quota.",
            defaultEndpoint = "https://openrouter.ai/api/v1/chat/completions",
            needsApiKey = true,
            models = listOf(
                ModelInfo("nvidia/nemotron-3-super-120b-a12b:free", "Nemotron 120B (Best)", "120B params, very capable, supports tool calling. Free."),
                ModelInfo("cohere/north-mini-code:free", "Cohere North Mini", "Good for coding tasks. Free."),
                ModelInfo("poolside/laguna-s-2.1:free", "Poolside Laguna S", "General purpose. Free."),
                ModelInfo("liquid/lfm-2.5-2.6b:free", "Liquid LFM 2.5 2.6B", "Very fast, small. Free.")
            )
        ),
        ProviderInfo(
            id = "gemini",
            name = "Google Gemini",
            description = "Google's AI. Free tier: 15 req/min, 1500/day. " +
                    "Get API key at aistudio.google.com/apikey",
            defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta",
            needsApiKey = true,
            models = listOf(
                ModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash", "Fast, capable, good for most tasks. Free tier."),
                ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash", "Newer, smarter. Free tier."),
                ModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", "Stable, fast. Free tier."),
                ModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro", "Most capable, slower. Free tier.")
            )
        ),
        ProviderInfo(
            id = "zai",
            name = "Z.ai Proxy (Sandbox)",
            description = "Uses the sandbox proxy. No API key needed, but the sandbox " +
                    "dev server must be running. GLM-4.6 model. 300 requests/day.",
            defaultEndpoint = "https://preview-chat-c9aadfe1-a665-4f7c-8232-c9d14a73c0cb.space-z.ai/api/llm/proxy",
            needsApiKey = false,
            models = listOf(
                ModelInfo("glm-4.6", "GLM-4.6", "Z.ai's flagship model. Smart, supports tools."),
                ModelInfo("glm-4-flash", "GLM-4 Flash", "Faster, less capable.")
            )
        ),
        ProviderInfo(
            id = "custom",
            name = "Custom (OpenAI-compatible)",
            description = "Use any OpenAI-compatible API. Enter endpoint, API key, and model name. " +
                    "Works with OpenAI, Together, Groq, Mistral, Ollama, etc.",
            defaultEndpoint = "",
            needsApiKey = true,
            models = listOf(
                ModelInfo("gpt-4o-mini", "GPT-4o mini", "OpenAI's cheap model. Requires OpenAI API key."),
                ModelInfo("llama-3.3-70b", "Llama 3.3 70B", "Meta's Llama. Use with Groq/Together."),
                ModelInfo("custom", "Custom Model", "Enter your model name in the model field.")
            )
        )
    )

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun getProviderId(context: Context): String {
        return getPrefs(context).getString(KEY_PROVIDER, "zai") ?: "zai"
    }

    fun setProvider(context: Context, providerId: String) {
        getPrefs(context).edit().putString(KEY_PROVIDER, providerId).apply()
    }

    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }

    fun setApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, key).apply()
    }

    fun getEndpoint(context: Context): String {
        val providerId = getProviderId(context)
        val default = PROVIDERS.find { it.id == providerId }?.defaultEndpoint ?: ""
        return getPrefs(context).getString(KEY_ENDPOINT, default) ?: default
    }

    fun setEndpoint(context: Context, endpoint: String) {
        getPrefs(context).edit().putString(KEY_ENDPOINT, endpoint).apply()
    }

    fun getModel(context: Context): String {
        val providerId = getProviderId(context)
        val default = PROVIDERS.find { it.id == providerId }?.models?.firstOrNull()?.id ?: "glm-4.6"
        return getPrefs(context).getString(KEY_MODEL, default) ?: default
    }

    fun setModel(context: Context, model: String) {
        getPrefs(context).edit().putString(KEY_MODEL, model).apply()
    }

    fun getCurrentProvider(context: Context): ProviderInfo {
        val id = getProviderId(context)
        return PROVIDERS.find { it.id == id } ?: PROVIDERS.first { it.id == "zai" }
    }

    /**
     * Check if the current provider is fully configured (has API key if needed).
     */
    fun isConfigured(context: Context): Boolean {
        val provider = getCurrentProvider(context)
        if (!provider.needsApiKey) return true
        return getApiKey(context).isNotEmpty()
    }
}
