package com.ai.agent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.WindowManager
import com.ai.agent.service.AgentService

/**
 * Transparent activity that handles voice input from the floating overlay.
 * Opens invisibly, starts speech recognition, sends result to AgentService, closes.
 *
 * Theme: @android:style/Theme.Translucent.NoTitleBar (set in manifest)
 * This makes the activity completely invisible — no background, no UI.
 */
class VoiceInputActivity : Activity() {

    companion object {
        const val REQUEST_SPEECH = 100
        private const val TAG = "VoiceInputActivity"
        const val EXTRA_LANG = "lang"
    }

    private var lang = "en-IN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make the activity completely transparent and non-interactive
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
        // Don't show any UI — the activity is invisible
        // (Theme.Translucent.NoTitleBar handles this, but we also clear any background)

        lang = intent.getStringExtra(EXTRA_LANG) ?: "en-IN"

        // Check if speech recognition is available
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available")
            finish()
            return
        }

        startSpeechRecognition()
    }

    private fun startSpeechRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your command...")
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_SPEECH)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SPEECH) {
            if (resultCode == RESULT_OK && data != null) {
                val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val text = results?.firstOrNull() ?: ""
                Log.d(TAG, "Voice input received: $text")

                if (text.isNotEmpty()) {
                    // Send the command directly to the OverlayManager for processing
                    // The OverlayManager has the LLM client and TTS
                    com.ai.agent.service.OverlayManager.processVoiceCommand(text)

                    // Also start the service to ensure it keeps running
                    val serviceIntent = Intent(this, AgentService::class.java).apply {
                        action = AgentService.ACTION_PROCESS_COMMAND
                        putExtra(AgentService.EXTRA_COMMAND, text)
                    }
                    startService(serviceIntent)
                }
            } else {
                Log.d(TAG, "No speech result (resultCode=$resultCode)")
            }
        }
        finish()  // close the transparent activity immediately
    }
}

