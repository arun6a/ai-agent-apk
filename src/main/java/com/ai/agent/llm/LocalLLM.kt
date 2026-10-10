package com.ai.agent.llm

import android.content.Context
import android.util.Log
import android.content.SharedPreferences
import java.io.File

/**
 * Manages local LLM models (GGUF format).
 * 
 * Uses llama.cpp binary to run inference locally.
 * The binary must be available at the app's native lib path.
 */
class LocalLLM(val context: Context) {

    companion object {
        private const val TAG = "LocalLLM"
        private const val MODELS_DIR = "models"
        private const val PREFS = "ai_agent"
        
        val RECOMMENDED_MODELS = listOf(
            ModelInfo(
                name = "Qwen 2.5 0.5B Instruct",
                url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                size = "400 MB",
                ramNeeded = "1 GB",
                description = "Very fast, very small. Best for phones with limited RAM.",
                contextWindow = 4096
            ),
            ModelInfo(
                name = "Qwen 2.5 1.5B Instruct",
                url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                size = "1.1 GB",
                ramNeeded = "2 GB",
                description = "Good balance of speed and quality. Recommended for most phones.",
                contextWindow = 4096
            ),
            ModelInfo(
                name = "DeepSeek-R1 1.5B",
                url = "https://huggingface.co/lmstudio-community/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/deepseek-r1-distill-qwen-1.5b-q4_k_m.gguf",
                size = "1.1 GB",
                ramNeeded = "2 GB",
                description = "DeepSeek R1 distilled to 1.5B. Excellent reasoning for its size. Great for complex tasks.",
                contextWindow = 4096
            ),
            ModelInfo(
                name = "Llama 3.2 1B Instruct",
                url = "https://huggingface.co/lmstudio-community/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                size = "900 MB",
                ramNeeded = "1.5 GB",
                description = "Meta's Llama 3.2 1B. Good instruction following.",
                contextWindow = 4096
            ),
            ModelInfo(
                name = "DeepSeek-R1 7B (Large)",
                url = "https://huggingface.co/lmstudio-community/DeepSeek-R1-Distill-Qwen-7B-GGUF/resolve/main/deepseek-r1-distill-qwen-7b-q4_k_m.gguf",
                size = "4.5 GB",
                ramNeeded = "6 GB",
                description = "DeepSeek R1 7B distilled. Excellent reasoning. Needs 6GB+ RAM. For tablets or phones with 8GB+ RAM.",
                contextWindow = 8192
            ),
            ModelInfo(
                name = "Llama 3.2 3B Instruct",
                url = "https://huggingface.co/lmstudio-community/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                size = "2.0 GB",
                ramNeeded = "3.5 GB",
                description = "Better quality than 1B, needs more RAM.",
                contextWindow = 4096
            )
        )
    }

    data class ModelInfo(
        val name: String,
        val url: String,
        val size: String,
        val ramNeeded: String,
        val description: String,
        val contextWindow: Int
    )

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getModelsDir(): File {
        val dir = File(context.filesDir, MODELS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getDownloadedModels(): List<File> {
        return getModelsDir().listFiles()?.filter { it.name.endsWith(".gguf") } ?: emptyList()
    }

    fun isModelDownloaded(modelName: String): Boolean {
        return getDownloadedModels().any { it.name.contains(modelName, ignoreCase = true) }
    }

    fun getActiveModel(): File? {
        val activeModel = prefs.getString("active_model", null) ?: return null
        val file = File(getModelsDir(), activeModel)
        return if (file.exists()) file else null
    }

    fun setActiveModel(modelFileName: String) {
        prefs.edit().putString("active_model", modelFileName).apply()
        Log.i(TAG, "Active model set to: $modelFileName")
    }

    /**
     * Check if local LLM is enabled AND a model is available.
     */
    fun isEnabled(): Boolean {
        val enabled = prefs.getBoolean("use_local_llm", false)
        val hasModel = getActiveModel() != null
        val hasBinary = isBinaryAvailable()
        Log.i(TAG, "isEnabled: setting=$enabled, model=$hasModel, binary=$hasBinary")
        return enabled && hasModel && hasBinary
    }

    fun isAvailable(): Boolean = getActiveModel() != null

    /**
     * Find the llama.cpp binary on the phone.
     * Checks: app native lib dir, /data/data/com.termux/files/usr/bin/,
     * and common locations.
     */
    private fun findLlamaBinary(): String? {
        // Android 10+ blocks exec() of downloaded binaries (W^X protection).
        // The ONLY way to run native code is from nativeLibraryDir, where Android
        // extracts .so files from the APK and makes them executable.
        // 
        // We bundle the llama-cli binary as libllama_exec.so in jniLibs/arm64-v8a/
        // Android extracts it to nativeLibraryDir where it's executable.
        
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        Log.i(TAG, "nativeLibraryDir: $nativeLibDir")
        
        // The binary is named libllama_exec.so (Android requires lib*.so naming)
        val execBinary = File(nativeLibDir, "libllama_exec.so")
        if (execBinary.exists()) {
            execBinary.setExecutable(true, false)
            Log.i(TAG, "Found binary in nativeLibDir: ${execBinary.absolutePath} (size=${execBinary.length()})")
            return execBinary.absolutePath
        }
        
        Log.w(TAG, "Binary not found in nativeLibraryDir: $nativeLibDir")
        
        // Check Termux as fallback
        listOf(
            "/data/data/com.termux/files/usr/bin/llama",
            "/data/data/com.termux/files/usr/bin/llama-cli"
        ).forEach { path -> File(path).let { if (it.exists()) return path } }
        
        return null
    }

    /**
     * Run inference using llama.cpp binary.
     * Returns the generated text, or null on failure.
     *
     * IMPORTANT: llama.cpp CLI flags changed in recent versions (b11435+).
     * - `--no-conversation` was REMOVED (it's now server-only). Conversation mode
     *   is OFF by default when using `-f` (file input), so we don't need it.
     * - `--simple-io` produces clean stdout without ANSI codes.
     * - `--log-disable` suppresses stderr log output.
     * - `--no-display-prompt` prevents the prompt from being echoed in output.
     *
     * We try multiple command variants with a fallback chain, so the binary
     * works regardless of which llama.cpp version is packaged.
     */
    fun generate(prompt: String, systemPrompt: String = ""): String? {
        val model = getActiveModel() ?: run {
            Log.e(TAG, "No active model")
            return null
        }

        val llamaPath = findLlamaBinary()
        if (llamaPath == null) {
            Log.e(TAG, "llama binary not found. Install via Termux: pkg install llama.cpp")
            return null
        }

        Log.i(TAG, "Using binary: $llamaPath")
        Log.i(TAG, "Using model: ${model.name}")

        // Build ChatML prompt (works for Qwen, DeepSeek-R1 distilled, Llama 3.2 instruct)
        val fullPrompt = if (systemPrompt.isNotEmpty()) {
            "<|im_start|>system\n$systemPrompt<|im_end|>\n<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"
        } else {
            "<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"
        }

        return try {
            // Write prompt to temp file
            val promptFile = File(context.cacheDir, "llm_prompt.txt")
            promptFile.writeText(fullPrompt)

            val binDir = context.applicationInfo.nativeLibraryDir
            val modelPath = model.absolutePath
            val promptPath = promptFile.absolutePath

            // Fallback chain: try flags from most-compatible to most-featured.
            // We capture BOTH stdout and stderr (merged via 2>&1) so we can
            // detect "invalid argument" / "unknown option" errors and move on.
            //
            // Variant 1: bare minimum (most compatible — works on ALL versions)
            // Variant 2: + --no-display-prompt (stops prompt echo)
            // Variant 3: + --simple-io (cleaner output)
            // Variant 4: + --log-disable (suppress logs)
            // Variant 5: -p (inline prompt) instead of -f (file)
            val commands = listOf(
                "LD_LIBRARY_PATH=$binDir \"$llamaPath\" -m \"$modelPath\" -f \"$promptPath\" -n 512 --temp 0.7 --top-p 0.9 2>&1",
                "LD_LIBRARY_PATH=$binDir \"$llamaPath\" -m \"$modelPath\" -f \"$promptPath\" -n 512 --temp 0.7 --top-p 0.9 --no-display-prompt 2>&1",
                "LD_LIBRARY_PATH=$binDir \"$llamaPath\" -m \"$modelPath\" -f \"$promptPath\" -n 512 --temp 0.7 --top-p 0.9 --no-display-prompt --simple-io 2>&1",
                "LD_LIBRARY_PATH=$binDir \"$llamaPath\" -m \"$modelPath\" -f \"$promptPath\" -n 512 --temp 0.7 --top-p 0.9 --no-display-prompt --simple-io --log-disable 2>&1",
                "LD_LIBRARY_PATH=$binDir \"$llamaPath\" -m \"$modelPath\" -p \"$fullPrompt\" -n 512 --temp 0.7 --top-p 0.9 2>&1"
            )

            var lastError = ""
            for ((index, cmd) in commands.withIndex()) {
                Log.i(TAG, "Running local LLM (variant ${index + 1}/${commands.size})...")
                Log.i(TAG, "Command: $cmd")

                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                Log.i(TAG, "LLM exit=$exitCode, output=${output.length} chars")
                Log.i(TAG, "LLM output preview: ${output.take(500)}")

                // Check for flag rejection errors — try next variant
                if (output.contains("invalid argument", ignoreCase = true) ||
                    output.contains("unknown option", ignoreCase = true) ||
                    output.contains("unrecognized", ignoreCase = true)) {
                    lastError = "Variant ${index + 1} rejected: ${output.lineSequence().firstOrNull { it.contains("invalid", ignoreCase = true) || it.contains("unknown", ignoreCase = true) || it.contains("unrecognized", ignoreCase = true) } ?: ""}"
                    Log.w(TAG, lastError)
                    continue
                }

                // If output is empty, try next variant
                if (output.isBlank()) {
                    lastError = "Variant ${index + 1} produced no output (exit=$exitCode)"
                    Log.w(TAG, lastError)
                    continue
                }

                val cleaned = cleanOutput(output, fullPrompt)
                if (cleaned.isNotEmpty()) {
                    Log.i(TAG, "LLM generated ${cleaned.length} chars: ${cleaned.take(200)}")
                    return cleaned
                } else {
                    lastError = "Variant ${index + 1} output was empty after cleaning"
                    Log.w(TAG, lastError)
                }
            }

            Log.e(TAG, "All LLM command variants failed. Last error: $lastError")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Local LLM failed", e)
            null
        }
    }

    /**
     * Clean llama.cpp stdout to extract just the generated text.
     *
     * Handles:
     * - Prompt echo (when --no-display-prompt isn't supported)
     * - ChatML tokens (<|im_start|>, <|im_end|>)
     * - DeepSeek-R1 reasoning tokens (<think>, </think>)
     * - Stray llama.cpp log lines that may leak to stdout
     */
    private fun cleanOutput(output: String, originalPrompt: String): String {
        var cleaned = output.trim()

        // 1. If the prompt was echoed back, remove everything up to and including
        //    the last "<|im_start|>assistant\n" marker
        val assistantMarker = "<|im_start|>assistant\n"
        val lastAssistantIdx = cleaned.lastIndexOf(assistantMarker)
        if (lastAssistantIdx >= 0) {
            cleaned = cleaned.substring(lastAssistantIdx + assistantMarker.length)
        }

        // 2. Remove ChatML tokens
        listOf("<|im_start|>system", "<|im_start|>user", "<|im_start|>assistant", "<|im_start|>", "<|im_end|>").forEach {
            cleaned = cleaned.replace(it, "")
        }

        // 3. Remove DeepSeek-R1 reasoning blocks (keep the final answer only)
        //    Format: <think>reasoning...</think>actual answer
        //    We use char-by-char construction to avoid HTML parsing issues
        val THINK_OPEN = "<" + "think" + ">"
        val THINK_CLOSE = "</" + "think" + ">"
        if (cleaned.contains(THINK_OPEN)) {
            val thinkEnd = cleaned.indexOf(THINK_CLOSE)
            if (thinkEnd >= 0) {
                cleaned = cleaned.substring(thinkEnd + THINK_CLOSE.length).trim()
            }
        }

        // 4. Remove stray llama.cpp log lines (some versions print to stdout)
        cleaned = cleaned.lines().filterNot { line ->
            val t = line.trim()
            t.startsWith("build:") || t.startsWith("system_info") ||
            t.startsWith("llama_model_loader") || t.startsWith("llm_load") ||
            t.startsWith("load_tensors") || t.startsWith("llama_new") ||
            t.startsWith("llama_kv") || t.startsWith("ggml_cuda") ||
            t.startsWith("ggml_metal") || t.startsWith("BLAS") ||
            t.startsWith("CPU") || t.startsWith("model_loader") ||
            t.startsWith("device") || t.startsWith("print_info") ||
            t.startsWith("load_backend") || t.startsWith("main:") ||
            t.startsWith("sampling:") || t.startsWith("encode:") ||
            t.startsWith("decode:") || t.startsWith("llama_perf") ||
            t.startsWith("\u001b[") // ANSI escape codes
        }.joinToString("\n").trim()

        return cleaned
    }

    /**
     * Check if llama binary is available on the phone.
     */
    fun isBinaryAvailable(): Boolean {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        return File(nativeLibDir, "libllama_exec.so").exists()
    }

    /**
     * Download the llama.cpp binary from the sandbox.
     * Saves to app's filesDir/llama-bin/
     */
    fun downloadBinary(callback: (Boolean, String) -> Unit) {
        val binDir = File(context.filesDir, "llama-bin")
        if (!binDir.exists()) binDir.mkdirs()

        val baseUrl = "https://preview-chat-c9aadfe1-a665-4f7c-8232-c9d14a73c0cb.space-z.ai/llama-bin"
        val files = listOf(
            "llama", "llama-cli",
            "libllama-cli-impl.so", "libllama-common.so", "libllama.so",
            "libggml.so", "libggml-base.so", "libggml-cpu-android_armv8.2_1.so"
        )

        Thread {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                for ((index, file) in files.withIndex()) {
                    val targetFile = File(binDir, file)
                    if (targetFile.exists()) continue  // Skip already downloaded

                    android.util.Log.i(TAG, "Downloading $file (${index + 1}/${files.size})...")

                    val request = okhttp3.Request.Builder()
                        .url("$baseUrl/$file")
                        .build()

                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        callback(false, "Failed to download $file: ${response.code}")
                        return@Thread
                    }

                    response.body?.byteStream()?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Make executable if it's a binary (not .so)
                    if (!file.endsWith(".so")) {
                        targetFile.setExecutable(true)
                    }

                    android.util.Log.i(TAG, "Downloaded $file (${targetFile.length() / 1048576} MB)")
                }

                android.util.Log.i(TAG, "All binary files downloaded!")
                callback(true, "Binary downloaded successfully!")

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Binary download failed", e)
                callback(false, "Download failed: ${e.message}")
            }
        }.start()
    }
}
