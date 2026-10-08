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
    private const val KEY_PROXY_BASE_URL = "proxy_base_url"  // Sandbox base URL for vision/web-search proxy
    private const val KEY_BROWSER_MODE = "browser_mode"  // "in_app" | "chrome" | "auto"

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
                ModelInfo("google/gemma-4-31b-it:free", "Gemma 4 31B (Vision!)", "31B Google model. VISION-capable — can analyze screenshots. Free."),
                ModelInfo("nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free", "Nemotron Omni 30B (Vision+Audio)", "30B NVIDIA. VISION + AUDIO. Free."),
                ModelInfo("cohere/north-mini-code:free", "Cohere North Mini", "Good for coding tasks. Free."),
                ModelInfo("liquid/lfm-2.5-2.6b:free", "Liquid LFM 2.5 2.6B", "Very fast, small. Free.")
            )
        ),
        ProviderInfo(
            id = "groq",
            name = "Groq (Ultra Fast)",
            description = "Ultra-fast LLM inference on custom hardware (LPU). " +
                    "Free tier: 30 req/min, 14400/day. Get key at console.groq.com/keys",
            defaultEndpoint = "https://api.groq.com/openai/v1/chat/completions",
            needsApiKey = true,
            models = listOf(
                ModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B (Recommended)", "Meta's 70B model. Smart, capable. Free tier."),
                ModelInfo("llama-3.1-8b-instant", "Llama 3.1 8B Instant", "Very fast, good for chat. Free tier."),
                ModelInfo("llama-3.2-11b-vision-preview", "Llama 3.2 11B Vision (Vision!)", "VISION-capable — can analyze screenshots. Free."),
                ModelInfo("llama-3.2-90b-vision-preview", "Llama 3.2 90B Vision", "Most capable Groq vision model. Free."),
                ModelInfo("mixtral-8x7b-32768", "Mixtral 8x7B", "MoE model, fast. 32k context. Free.")
            )
        ),
        ProviderInfo(
            id = "together",
            name = "Together AI",
            description = "Many open-source models (Llama, Qwen, Mistral). " +
                    "$5 free credit on signup. Get key at api.together.xyz/settings/api-keys",
            defaultEndpoint = "https://api.together.xyz/v1/chat/completions",
            needsApiKey = true,
            models = listOf(
                ModelInfo("meta-llama/Llama-3.3-70B-Instruct-Turbo", "Llama 3.3 70B Turbo", "Fast, capable. Paid."),
                ModelInfo("meta-llama/Meta-Llama-3.1-405B-Instruct-Turbo", "Llama 3.1 405B", "Most capable. Paid."),
                ModelInfo("Qwen/Qwen2.5-72B-Instruct-Turbo", "Qwen 2.5 72B", "Great for coding. Paid."),
                ModelInfo("meta-llama/Llama-Vision-Free", "Llama Vision (Free)", "VISION-capable. Free.")
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
                ModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash (Recommended)", "Fast, capable, vision-capable. Free tier."),
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
            defaultEndpoint = "https://preview-chat-1855dd56-e3a8-4ac4-a21c-824fbab8e552.space-z.ai/api/llm/proxy",
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
                    "Works with: OpenAI, Ollama, LM Studio, vLLM, etc.",
            defaultEndpoint = "",
            needsApiKey = true,
            models = listOf(
                ModelInfo("gpt-4o-mini", "GPT-4o mini", "OpenAI's cheap model. Requires OpenAI API key."),
                ModelInfo("llama-3.3-70b", "Llama 3.3 70B", "Meta's Llama. Use with any OpenAI-compatible server."),
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

    // === Sandbox proxy base URL ===
    // Used by tools (webSearch, analyzeScreen) that need a sandbox-side proxy
    // because OpenRouter doesn't have free vision and we need Z.ai's VLM.
    // The URL is configurable so users can point to their own sandbox without rebuilding.
    //
    // Default: current Z.ai Code sandbox URL.
    // Set via setProxyBaseUrl() (e.g., from Settings).
    val DEFAULT_PROXY_BASE_URL = "https://preview-chat-1855dd56-e3a8-4ac4-a21c-824fbab8e552.space-z.ai"

    fun getProxyBaseUrl(context: Context): String {
        return getPrefs(context).getString(KEY_PROXY_BASE_URL, DEFAULT_PROXY_BASE_URL)
            ?: DEFAULT_PROXY_BASE_URL
    }

    fun setProxyBaseUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_PROXY_BASE_URL, url.trimEnd('/')).apply()
    }

    /** Full URL for the LLM proxy endpoint (POST /api/llm/proxy). */
    fun getLlmProxyUrl(context: Context): String =
        getProxyBaseUrl(context).trimEnd('/') + "/api/llm/proxy"

    /** Full URL for the vision endpoint (POST /api/llm/vision). */
    fun getVisionUrl(context: Context): String =
        getProxyBaseUrl(context).trimEnd('/') + "/api/llm/vision"

    /** Full URL for the web-search endpoint (POST /api/web-search). */
    fun getWebSearchUrl(context: Context): String =
        getProxyBaseUrl(context).trimEnd('/') + "/api/web-search"

    // === Browser Mode ===
    // Controls which browser the AI uses:
    // "in_app" → visible in-app WebView (no accessibility needed, user can see + intervene)
    // "chrome" → Chrome app via Intent (needs accessibility, uses Chrome login state)
    // "auto" → try in-app first, suggest Chrome for logged-in sites
    val DEFAULT_BROWSER_MODE = "auto"

    fun getBrowserMode(context: Context): String {
        return getPrefs(context).getString(KEY_BROWSER_MODE, DEFAULT_BROWSER_MODE) ?: DEFAULT_BROWSER_MODE
    }

    fun setBrowserMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_BROWSER_MODE, mode).apply()
    }
}
