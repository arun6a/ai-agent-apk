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
        // Start AgentService directly with the rule action.
        // (Previously we sent a broadcast that nobody received — now we use a service intent.)
        val serviceIntent = Intent(this, com.ai.agent.service.AgentService::class.java).apply {
            action = com.ai.agent.service.AgentService.ACTION_PROCESS_RULE
            putExtra(com.ai.agent.service.AgentService.EXTRA_RULE_NAME, rule.name)
            putExtra(com.ai.agent.service.AgentService.EXTRA_RULE_ACTION, rule.action)
            putExtra(com.ai.agent.service.AgentService.EXTRA_RULE_TRIGGER_TYPE, rule.triggerType)
            putExtra(com.ai.agent.service.AgentService.EXTRA_NOTIFICATION_TITLE, notifTitle)
            putExtra(com.ai.agent.service.AgentService.EXTRA_NOTIFICATION_TEXT, notifText)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Also log it
        db.logAction("Notification rule fired: ${rule.name} (title=$notifTitle) — ${rule.action}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
