package com.ai.agent.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred

/**
 * Controls an in-app WebView that the AI can use for web research.
 * The WebView runs in the background (no visible UI) — the AI reads page content via JavaScript.
 */
object BrowserController {

    private const val TAG = "BrowserController"
    private var webView: WebView? = null
    private var handler = Handler(Looper.getMainLooper())

    private fun ensureWebView(context: Context): WebView {
        if (webView != null) return webView!!
        
        handler.post {
            webView = WebView(context.applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.i(TAG, "Page loaded: $url")
                    }
                }
            }
        }
        // Wait for WebView to be created on main thread
        Thread.sleep(500)
        return webView!!
    }

    fun openUrl(context: Context, url: String) {
        Log.i(TAG, "Opening URL: $url")
        val wv = ensureWebView(context)
        handler.post {
            wv.loadUrl(url)
        }
    }

    fun getPageText(context: Context): String? {
        val wv = webView ?: return null
        val deferred = CompletableDeferred<String?>()
        handler.post {
            wv.evaluateJavascript("document.body ? document.body.innerText : 'empty'") { result ->
                // Result comes as a JSON string (quoted)
                val text = result?.let {
                    it.trim('"').replace("\\n", "\n").replace("\\\"", "\"")
                }
                deferred.complete(text)
            }
        }
        return try {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(5000) { deferred.await() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPageText error", e)
            null
        }
    }

    fun evalJs(context: Context, script: String): String? {
        val wv = webView ?: return null
        val deferred = CompletableDeferred<String?>()
        handler.post {
            wv.evaluateJavascript(script) { result ->
                deferred.complete(result)
            }
        }
        return try {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(5000) { deferred.await() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "evalJs error", e)
            null
        }
    }

    fun goBack(context: Context) {
        val wv = webView ?: return
        handler.post {
            if (wv.canGoBack()) wv.goBack()
        }
    }

    fun getCurrentUrl(context: Context): String? {
        val wv = webView ?: return null
        return try {
            kotlinx.coroutines.runBlocking {
                val deferred = CompletableDeferred<String?>()
                handler.post {
                    deferred.complete(wv.url)
                }
                kotlinx.coroutines.withTimeoutOrNull(2000) { deferred.await() }
            }
        } catch (e: Exception) { null }
    }

    fun destroy() {
        handler.post {
            webView?.destroy()
            webView = null
        }
    }
}
