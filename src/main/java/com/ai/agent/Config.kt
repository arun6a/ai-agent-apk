package com.ai.agent

/**
 * App configuration.
 * The LLM proxy runs on the sandbox and forwards requests to Z.ai API.
 * The phone calls the public sandbox URL, not the internal API directly.
 */
object Config {
    // LLM proxy endpoint (publicly accessible sandbox URL)
    // This proxies requests to Z.ai's internal API with proper auth headers.
    const val LLM_ENDPOINT = "https://preview-chat-c9aadfe1-a665-4f7c-8232-c9d14a73c0cb.space-z.ai/api/llm/proxy"
    const val LLM_MODEL = "glm-4.6"

    // Screen resolution (Nothing A015)
    const val SCREEN_WIDTH = 1080
    const val SCREEN_HEIGHT = 2400

    // App version
    const val VERSION = "0.2.1"
}
