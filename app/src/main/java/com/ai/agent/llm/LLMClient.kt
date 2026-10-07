package com.ai.agent.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.ai.agent.llm.ApiUsageTracker

/**
 * LLM client that supports multiple AI providers:
 * - OpenRouter (free models, OpenAI-compatible)
 * - Google Gemini (free tier, different format)
 * - Z.ai proxy (sandbox, no API key)
 * - Custom (any OpenAI-compatible endpoint)
 *
 * The provider config is read from SharedPreferences (AIProvider).
 * The user can switch providers in Settings without rebuilding the app.
 */
class LLMClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)  // longer for big models
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    data class LLMResponse(
        val reply: String,
        val toolCalls: List<ToolCall>
    )

    data class ToolCall(
        val name: String,
        val args: Map<String, Any>
    )

    /**
     * Sends a chat message and returns the LLM's response.
     * Uses the provider configured in Settings.
     */
    suspend fun chat(userMessage: String, systemPrompt: String): LLMResponse = withContext(Dispatchers.IO) {
        val provider = AIProvider.getCurrentProvider(context)
        val apiKey = AIProvider.getApiKey(context)
        val endpoint = AIProvider.getEndpoint(context)
        val model = AIProvider.getModel(context)

        Log.i("LLMClient", "Provider: ${provider.id}, Model: $model, Endpoint: $endpoint")

        if (provider.needsApiKey && apiKey.isEmpty()) {
            return@withContext LLMResponse(
                "No API key set. Open Settings → AI Provider to add your ${provider.name} API key.",
                emptyList()
            )
        }

        var lastError: String? = null

        // Retry up to 3 times with increasing delay
        for (attempt in 1..3) {
            try {
                val requestBody = when (provider.id) {
                    "gemini" -> buildGeminiRequest(model, userMessage, systemPrompt)
                    else -> buildOpenAIRequest(model, userMessage, systemPrompt, provider.id)
                }

                val requestBuilder = Request.Builder()
                    .url(endpoint)
                    .header("Content-Type", "application/json")

                // Add auth headers based on provider
                when (provider.id) {
                    "openrouter" -> {
                        requestBuilder.header("Authorization", "Bearer $apiKey")
                        requestBuilder.header("HTTP-Referer", "https://github.com/arun6a/ai-agent-apk")
                        requestBuilder.header("X-Title", "AI Agent Phone")
                    }
                    "groq", "together" -> {
                        // Groq + Together use OpenAI-compatible auth (Bearer token, no extra headers)
                        requestBuilder.header("Authorization", "Bearer $apiKey")
                    }
                    "gemini" -> {
                        // Gemini uses query param, not header — handled in endpoint
                    }
                    "custom" -> {
                        if (apiKey.isNotEmpty()) {
                            requestBuilder.header("Authorization", "Bearer $apiKey")
                        }
                    }
                    "zai" -> {
                        // Z.ai proxy needs no auth from the phone (sandbox handles it)
                    }
                }

                val request = requestBuilder.post(requestBody.toRequestBody(jsonMediaType)).build()

                Log.d("LLMClient", "Request attempt $attempt to $endpoint")
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.code == 429) {
                    val waitMs = attempt * 5000L
                    Log.w("LLMClient", "Rate limited (429), waiting ${waitMs}ms before retry $attempt/3")
                    lastError = "Rate limited (429). Daily quota may be exhausted."
                    Thread.sleep(waitMs)
                    continue
                }

                if (!response.isSuccessful) {
                    Log.e("LLMClient", "API error ${response.code}: ${responseBody.take(300)}")
                    if (attempt < 3) {
                        Thread.sleep(2000)
                        lastError = "API returned ${response.code}"
                        continue
                    }
                    return@withContext LLMResponse(
                        "Error: API returned ${response.code}. ${responseBody.take(200)}",
                        emptyList()
                    )
                }

                // Parse response based on provider format
                val content = when (provider.id) {
                    "gemini" -> parseGeminiResponse(responseBody)
                    else -> parseOpenAIResponse(responseBody)
                }

                Log.d("LLMClient", "Response: ${content.take(200)}...")
                return@withContext parseResponse(content)
            } catch (e: Exception) {
                Log.e("LLMClient", "Request failed (attempt $attempt)", e)
                lastError = e.message
                if (attempt < 3) Thread.sleep(2000)
            }
        }

        LLMResponse("Error: $lastError. Please try again in a moment.", emptyList())
    }

    /**
     * Build request body for OpenAI-compatible APIs (OpenRouter, Z.ai, custom).
     */
    private fun buildOpenAIRequest(model: String, userMessage: String, systemPrompt: String, providerId: String): String {
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        })

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            // Z.ai uses "thinking" param, OpenRouter/custom don't need it
            if (providerId == "zai") {
                put("thinking", JSONObject().put("type", "disabled"))
            }
        }

        return body.toString()
    }

    /**
     * Build request body for Gemini API (different format).
     * Endpoint: https://generativelanguage.googleapis.com/v1beta/models/<model>:generateContent?key=<key>
     */
    private fun buildGeminiRequest(model: String, userMessage: String, systemPrompt: String): String {
        val body = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 1024)
            })
        }
        return body.toString()
    }

    /**
     * Parse OpenAI-format response (used by OpenRouter, Z.ai, custom).
     */
    private fun parseOpenAIResponse(responseBody: String): String {
        val json = JSONObject(responseBody)
        
        // Track token usage
        try {
            val usage = json.optJSONObject("usage")
            if (usage != null) {
                val promptTokens = usage.optInt("prompt_tokens", 0)
                val completionTokens = usage.optInt("completion_tokens", 0)
                val provider = AIProvider.getProviderId(context)
                ApiUsageTracker.recordCall(context, promptTokens, completionTokens, provider)
            }
        } catch (e: Exception) {
            Log.w("LLMClient", "Could not parse usage: ${e.message}")
        }
        
        return json
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?: "No response from AI"
    }

    /**
     * Parse Gemini-format response.
     */
    private fun parseGeminiResponse(responseBody: String): String {
        val json = JSONObject(responseBody)
        
        // Track token usage (Gemini returns usageMetadata)
        try {
            val usage = json.optJSONObject("usageMetadata")
            if (usage != null) {
                val promptTokens = usage.optInt("promptTokenCount", 0)
                val completionTokens = usage.optInt("candidatesTokenCount", 0)
                ApiUsageTracker.recordCall(context, promptTokens, completionTokens, "gemini")
            }
        } catch (e: Exception) {
            Log.w("LLMClient", "Could not parse Gemini usage: ${e.message}")
        }
        
        val candidates = json.optJSONArray("candidates")
        val content = candidates
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            ?: "No response from Gemini"
        return content
    }

    /**
     * Parses the LLM's response content into the phone's expected format.
     * Expected: {"reply": "...", "tool_calls": [{"name": "...", "args": {...}}]}
     */
    private fun parseResponse(content: String): LLMResponse {
        var cleaned = content.trim()
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replace(Regex("^```(?:json)?\\s*"), "")
                .replace(Regex("\\s*```$"), "")
        }

        return try {
            val json = JSONObject(cleaned)
            val reply = json.optString("reply", "Done.")
            val toolCalls = mutableListOf<ToolCall>()
            val callsArray = json.optJSONArray("tool_calls")
            if (callsArray != null) {
                for (i in 0 until callsArray.length()) {
                    val call = callsArray.optJSONObject(i) ?: continue
                    val name = call.optString("name", "")
                    val argsObj = call.optJSONObject("args") ?: JSONObject()
                    val args = mutableMapOf<String, Any>()
                    val keys = argsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        args[key] = argsObj.get(key)
                    }
                    if (name.isNotEmpty()) {
                        toolCalls.add(ToolCall(name, args))
                    }
                }
            }
            LLMResponse(reply, toolCalls)
        } catch (e: Exception) {
            LLMResponse(content, emptyList())
        }
    }
}
