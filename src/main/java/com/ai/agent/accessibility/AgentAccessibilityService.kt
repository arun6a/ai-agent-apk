package com.ai.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class AgentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AgentA11y"
        private var instance: AgentAccessibilityService? = null
        fun getInstance(): AgentAccessibilityService? = instance
        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() { Log.w(TAG, "Interrupted") }

    override fun onDestroy() {
        instance = null
        Log.i(TAG, "Destroyed")
        super.onDestroy()
    }

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "(screen is null)"
        val texts = mutableListOf<String>()
        collectTexts(root, texts)
        return if (texts.isEmpty()) "(no text on screen)" else texts.joinToString("\n")
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, texts: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) texts.add(text)
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty() && desc != text) texts.add("[$desc]")
        for (i in 0 until node.childCount) collectTexts(node.getChild(i), texts)
    }

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100)).build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs)).build()
        return dispatchGesture(gesture, null, null)
    }

    fun clickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        findNodeByText(root, text)?.let { return performClick(it) }
        findNodeContainingText(root, text)?.let { return performClick(it) }
        return false
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (text.equals(node.text?.toString(), true) || text.equals(node.contentDescription?.toString(), true)) return node
        for (i in 0 until node.childCount) { node.getChild(i)?.let { findNodeByText(it, text)?.let { return it } } }
        return null
    }

    private fun findNodeContainingText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nt = node.text?.toString() ?: ""
        val nd = node.contentDescription?.toString() ?: ""
        if (text.length >= 5) {
            if (nt.contains(text, true) || nd.contains(text, true)) return node
            val st = text.take(15)
            if (nt.contains(st, true) || nd.contains(st, true)) return node
        }
        for (i in 0 until node.childCount) { node.getChild(i)?.let { findNodeContainingText(it, text)?.let { return it } } }
        return null
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        var parent = node.parent; var d = 0
        while (parent != null && d < 5) { if (parent.isClickable) return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK); parent = parent.parent; d++ }
        return false
    }

    fun type(text: String): Boolean {
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun showKeyboard(): Boolean {
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val rect = Rect(); focused.getBoundsInScreen(rect)
        if (!rect.isEmpty) { tap(rect.exactCenterX(), rect.exactCenterY()); return true }
        return false
    }

    fun findAndClickSubmit(): Boolean {
        val root = rootInActiveWindow ?: return false
        listOf("Search","Go","Send","Submit","Done","Next").forEach { findNodeByText(root, it)?.let { if (performClick(it)) return true } }
        listOf("Search","Submit","Go","Send").forEach { findNodeByDescription(root, it)?.let { if (performClick(it)) return true } }
        return false
    }

    private fun findNodeByDescription(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (desc.equals(node.contentDescription?.toString(), true)) return node
        for (i in 0 until node.childCount) { node.getChild(i)?.let { findNodeByDescription(it, desc)?.let { return it } } }
        return null
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun pressEnter(): Boolean = tap(970f, 2230f)

    fun submitInput(): Boolean {
        showKeyboard(); Thread.sleep(500)
        listOf(Pair(970f, 2150f), Pair(970f, 2200f), Pair(970f, 2100f), Pair(1000f, 2150f)).forEach { (x, y) ->
            tap(x, y); Thread.sleep(500)
            if (rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) == null) return true
        }
        return findAndClickSubmit()
    }

    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent); true } else false
        } catch (e: Exception) { false }
    }

    fun scrollDown(): Boolean = swipe(540f, 1800f, 540f, 400f, 400)
    fun scrollUp(): Boolean = swipe(540f, 400f, 540f, 1800f, 400)
    fun lockScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    // === SCREENSHOT (Vision) ===

    fun captureScreen(callback: (String?) -> Unit) {
        try {
            // Use fully qualified Android class to avoid Kotlin resolution issues
            val asClass = android.accessibilityservice.AccessibilityService::class.java
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            
            // Create the callback using the fully qualified nested class
            val cbClass = android.accessibilityservice.AccessibilityService.TakeScreenshotCallback::class.java
            val cbMethod = asClass.getMethod("takeScreenshot", java.util.concurrent.Executor::class.java, cbClass)
            
            // Create callback proxy via dynamic proxy
            val callbackObj = java.lang.reflect.Proxy.newProxyInstance(
                cbClass.classLoader,
                arrayOf(cbClass),
                java.lang.reflect.InvocationHandler { _, method, args ->
                    when (method.name) {
                        "onSuccess" -> {
                            try {
                                val result = args[0] as android.accessibilityservice.AccessibilityService.ScreenshotResult
                                Log.i(TAG, "Screenshot received")
                                val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                                if (bitmap == null) {
                                    Log.e(TAG, "wrapHardwareBuffer returned null")
                                    result.hardwareBuffer.close()
                                    callback(null)
                                    return@InvocationHandler null
                                }
                                Log.i(TAG, "Bitmap: ${bitmap.width}x${bitmap.height}")
                                
                                val stream = java.io.ByteArrayOutputStream()
                                val w = bitmap.width; val h = bitmap.height
                                val scaled = if (w > 480) {
                                    val r = 480f / w
                                    android.graphics.Bitmap.createScaledBitmap(bitmap, 480, (h * r).toInt(), true)
                                } else bitmap
                                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, stream)
                                val b64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                                Log.i(TAG, "Screenshot done: ${stream.toByteArray().size} bytes")
                                result.hardwareBuffer.close()
                                callback(b64)
                            } catch (e: Exception) {
                                Log.e(TAG, "Screenshot processing failed", e)
                                callback(null)
                            }
                        }
                        "onFailure" -> {
                            val errorCode = args[0] as Int
                            Log.e(TAG, "Screenshot failed: error $errorCode")
                            callback(null)
                        }
                    }
                    null
                }
            )
            
            cbMethod.invoke(this, executor, callbackObj)
        } catch (e: Exception) {
            Log.e(TAG, "takeScreenshot error", e)
            callback(null)
        }
    }
}
