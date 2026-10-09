package com.ai.agent.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import com.ai.agent.R

/**
 * Visible in-app browser that both the AI and the user can use.
 *
 * Features:
 * - WebView is visible (user can see what AI is doing)
 * - Cookie persistence (user logs in once → AI has the session)
 * - AI/User mode toggle (switch who controls the browser)
 * - AI status bar (shows "AI is working..." or "AI needs your help")
 * - URL bar (shows current URL)
 * - Navigation buttons (back, forward, refresh, share)
 *
 * The AI accesses this WebView via BrowserController, which holds a reference
 * to the WebView instance created here.
 */
class BrowserActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BrowserActivity"
        const val EXTRA_URL = "url"

        /**
         * Launch the browser with a specific URL.
         * v5.0.9: Uses SINGLE_TOP + REORDER_TO_FRONT so we REUSE the existing
         * BrowserActivity instance instead of creating a new one every time.
         * The URL is passed via Intent extra and loaded in onNewIntent().
         */
        fun launch(context: Context, url: String) {
            val intent = Intent(context, BrowserActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                         Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                         Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }

        /** Reference to the active WebView — used by BrowserController */
        var activeWebView: WebView? = null
            private set

        /** Reference to the activity — used by BrowserController to run JS on UI thread */
        var activeInstance: BrowserActivity? = null
            private set
    }

    private lateinit var webView: WebView
    private lateinit var urlBar: TextView
    private lateinit var modeToggle: ToggleButton
    private lateinit var aiStatusBar: LinearLayout
    private lateinit var aiStatusText: TextView
    private lateinit var btnImDone: Button
    private lateinit var loadingBar: ProgressBar
    private lateinit var btnBack: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnWebBack: ImageButton
    private lateinit var btnWebForward: ImageButton
    private lateinit var btnShare: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        webView = findViewById(R.id.webView)
        urlBar = findViewById(R.id.urlBar)
        modeToggle = findViewById(R.id.modeToggle)
        aiStatusBar = findViewById(R.id.aiStatusBar)
        aiStatusText = findViewById(R.id.aiStatusText)
        btnImDone = findViewById(R.id.btnImDone)
        loadingBar = findViewById(R.id.loadingBar)
        btnBack = findViewById(R.id.btnBack)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnWebBack = findViewById(R.id.btnWebBack)
        btnWebForward = findViewById(R.id.btnWebForward)
        btnShare = findViewById(R.id.btnShare)

        setupWebView()

        // Back button → close browser → return to chat
        btnBack.setOnClickListener { finish() }

        // Refresh
        btnRefresh.setOnClickListener { webView.reload() }

        // Browser back/forward
        btnWebBack.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        btnWebForward.setOnClickListener { if (webView.canGoForward()) webView.goForward() }

        // Share URL
        btnShare.setOnClickListener {
            val url = webView.url ?: ""
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            startActivity(Intent.createChooser(shareIntent, "Share URL"))
        }

        // Mode toggle: AI ↔ User
        modeToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // AI mode
                showAIStatus("🤖 AI is working...")
            } else {
                // User mode
                hideAIStatus()
                Toast.makeText(this, "👤 User mode — you control the browser", Toast.LENGTH_SHORT).show()
            }
        }

        // "I'm done" button → switch back to AI mode
        btnImDone.setOnClickListener {
            modeToggle.isChecked = true  // back to AI mode
            btnImDone.visibility = View.GONE
        }

        // Load initial URL if provided
        val initialUrl = intent.getStringExtra(EXTRA_URL)
        if (initialUrl != null) {
            val finalUrl = if (!initialUrl.startsWith("http")) "https://$initialUrl" else initialUrl
            webView.loadUrl(finalUrl)
        }

        // Make WebView available to BrowserController
        activeWebView = webView
        activeInstance = this
        Log.i(TAG, "BrowserActivity created — WebView available for AI")
    }

    /**
     * v5.0.9: Called when launch() reuses the existing BrowserActivity instance
     * (SINGLE_TOP + REORDER_TO_FRONT). Loads the new URL instead of creating a new activity.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newUrl = intent.getStringExtra(EXTRA_URL)
        if (newUrl != null) {
            val finalUrl = if (!newUrl.startsWith("http")) "https://$newUrl" else newUrl
            Log.i(TAG, "onNewIntent: loading new URL: $finalUrl")
            webView.loadUrl(finalUrl)
        }
        // Re-establish references (in case they were cleared)
        activeWebView = webView
        activeInstance = this
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        // Cookie persistence — critical for login state
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                loadingBar.visibility = View.VISIBLE
                urlBar.text = url ?: "Loading..."
                Log.i(TAG, "Page loading: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadingBar.visibility = View.GONE
                urlBar.text = url ?: ""
                CookieManager.getInstance().flush()  // persist cookies
                Log.i(TAG, "Page loaded: $url")

                // Smart detection: check if page has login form / captcha
                view?.evaluateJavascript("""
                    (function() {
                        var hasPassword = document.querySelector('input[type="password"]') != null;
                        var hasCaptcha = document.querySelector('iframe[src*="captcha"]') != null ||
                                        document.querySelector('.g-recaptcha') != null ||
                                        document.querySelector('.h-captcha') != null;
                        var hasLoginText = document.body && document.body.innerText &&
                            (document.body.innerText.toLowerCase().includes('please log in') ||
                             document.body.innerText.toLowerCase().includes('sign in') ||
                             document.body.innerText.toLowerCase().includes('verify you are human'));
                        return JSON.stringify({hasPassword: hasPassword, hasCaptcha: hasCaptcha, hasLoginText: hasLoginText});
                    })();
                """.trimIndent()) { result ->
                    try {
                        val cleaned = result?.trim('"')?.replace("\\\"", "\"")?.replace("\\\\", "\\")
                        val json = org.json.JSONObject(cleaned ?: "{}")
                        val hasPassword = json.optBoolean("hasPassword", false)
                        val hasCaptcha = json.optBoolean("hasCaptcha", false)
                        val hasLoginText = json.optBoolean("hasLoginText", false)

                        if (hasCaptcha || hasPassword || hasLoginText) {
                            runOnUiThread {
                                showAIStatus("⚠️ This page needs your help — please log in or solve captcha")
                                btnImDone.visibility = View.VISIBLE
                                modeToggle.isChecked = false  // switch to user mode
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Login detection parse error: ${e.message}")
                    }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    loadingBar.visibility = View.VISIBLE
                } else {
                    loadingBar.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Show the AI status bar with a message.
     * Called by BrowserController when AI is doing something.
     */
    fun showAIStatus(message: String) {
        runOnUiThread {
            aiStatusBar.visibility = View.VISIBLE
            aiStatusText.text = message
        }
    }

    /**
     * Hide the AI status bar.
     */
    fun hideAIStatus() {
        runOnUiThread {
            aiStatusBar.visibility = View.GONE
        }
    }

    /**
     * Show "I need your help" + switch to user mode.
     * Called by BrowserController when AI detects it's stuck.
     */
    fun requestUserHelp(message: String) {
        runOnUiThread {
            showAIStatus("⚠️ $message")
            btnImDone.visibility = View.VISIBLE
            modeToggle.isChecked = false  // switch to user mode
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Check if AI mode is active.
     */
    fun isAIMode(): Boolean = modeToggle.isChecked

    override fun onDestroy() {
        super.onDestroy()
        CookieManager.getInstance().flush()
        // v5.0.9: Only clear references if the activity is actually finishing
        // (user pressed Back to close). If the system is just destroying it for
        // memory/config change, keep the references so the AI can continue.
        if (isFinishing) {
            activeWebView = null
            activeInstance = null
            Log.i(TAG, "BrowserActivity finishing — WebView references cleared")
        } else {
            Log.i(TAG, "BrowserActivity destroyed (not finishing) — keeping WebView references")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // If WebView can go back, go back in browser history
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
