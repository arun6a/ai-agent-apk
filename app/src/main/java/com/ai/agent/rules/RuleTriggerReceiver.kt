package com.ai.agent.rules

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ai.agent.storage.AgentDatabase

/**
 * Receives AlarmManager broadcasts for scheduled time rules.
 *
 * v5.0.0 — WorkManager integration:
 * When a rule fires (via AlarmManager — exact timing for HH:MM):
 * 1. This receiver is triggered by AlarmManager
 * 2. We enqueue a RuleWorker via WorkManager — system-managed background execution
 * 3. RuleWorker starts AgentService (foreground) with the rule's action
 * 4. We reschedule the rule for its next occurrence
 *
 * Why use BOTH AlarmManager + WorkManager:
 * - AlarmManager: precise HH:MM timing (fires exactly at 07:00, fires in Doze)
 * - WorkManager: survives app kills, retries on failure, system-managed process
 *
 * If the app is killed between AlarmManager firing and AgentService starting,
 * WorkManager will still execute the worker (it runs in a system process).
 */
class RuleTriggerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RuleTriggerReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Received rule trigger broadcast: ${intent.action}")

        val ruleId = intent.getLongExtra(RuleScheduler.EXTRA_RULE_ID, -1L)
        if (ruleId == -1L) {
            Log.e(TAG, "No rule_id extra in broadcast — ignoring")
            return
        }

        val db = AgentDatabase(context)
        val rule = db.getRules(enabledOnly = false).find { it.id == ruleId }
        if (rule == null) {
            Log.e(TAG, "Rule id=$ruleId not found in DB — probably deleted")
            return
        }
        if (!rule.enabled) {
            Log.i(TAG, "Rule id=$ruleId is disabled — skipping")
            return
        }

        Log.i(TAG, "Rule fired: ${rule.name} → action: ${rule.action}")

        // 1. Log the trigger
        db.logAction("Rule fired: ${rule.name} — ${rule.action}")

        // 2. Enqueue RuleWorker via WorkManager (reliable execution)
        //    Unique work name prevents duplicate executions of the same rule firing twice.
        val workRequest = OneTimeWorkRequestBuilder<RuleWorker>()
            .setInputData(
                workDataOf(
                    RuleWorker.KEY_RULE_ID to rule.id,
                    RuleWorker.KEY_RULE_NAME to rule.name,
                    RuleWorker.KEY_RULE_ACTION to rule.action,
                    RuleWorker.KEY_RULE_TRIGGER to rule.triggerType
                )
            )
            .build()

        val uniqueWorkName = "rule_${rule.id}_${System.currentTimeMillis()}"
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )
        Log.i(TAG, "Enqueued RuleWorker for ${rule.name} (work=$uniqueWorkName)")

        // 3. Reschedule the rule for its next occurrence
        // We do this from the receiver (not the service) so it happens immediately,
        // even if the worker takes time to run the action.
        val scheduler = RuleScheduler(context)
        if (rule.triggerType == "time") {
            scheduler.scheduleTimeRule(rule)
        }
    }
}
