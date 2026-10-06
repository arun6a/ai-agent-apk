package com.ai.agent.rules

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ai.agent.storage.AgentDatabase

/**
 * Listens for notifications from all apps.
 * When a notification matches a rule, triggers the rule action.
 */
class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"
    }

    private val db by lazy { AgentDatabase(this) }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        val notification = sbn.notification ?: return
        val extras = notification.extras

        val title = extras.getString(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text

        Log.d(TAG, "Notification from $packageName: $title - $text")

        // Check if any rules match this package
        val rules = db.getRules(enabledOnly = true).filter { it.triggerType == "notification" }
        for (rule in rules) {
            val trigger = rule.triggerValue
            val shouldTrigger = when {
                // "com.whatsapp:Mom" — match package AND sender
                trigger.contains(":") -> {
                    val parts = trigger.split(":", limit = 2)
                    val pkg = parts[0]
                    val sender = parts.getOrNull(1) ?: ""
                    packageName == pkg && (title.contains(sender, ignoreCase = true) || text.contains(sender, ignoreCase = true))
                }
                // Just package name — match any notification from this app
                else -> packageName == trigger
            }

            if (shouldTrigger) {
                Log.i(TAG, "Notification rule fired: ${rule.name} (from $packageName)")
                triggerRule(rule, title, bigText)
            }
        }
    }

    private fun triggerRule(rule: AgentDatabase.Rule, notifTitle: String, notifText: String) {
        // Send a broadcast that the AgentService can pick up
        val intent = Intent("com.ai.agent.RULE_TRIGGERED").apply {
            putExtra("rule_name", rule.name)
            putExtra("rule_action", rule.action)
            putExtra("notification_title", notifTitle)
            putExtra("notification_text", notifText)
        }
        sendBroadcast(intent)

        // Also log it
        db.logAction("Rule fired: ${rule.name} — ${rule.action}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
