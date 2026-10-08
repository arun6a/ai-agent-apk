package com.ai.agent.rules

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ai.agent.service.AgentService
import com.ai.agent.storage.AgentDatabase

/**
 * Receives AlarmManager broadcasts for scheduled time rules.
 *
 * When a rule fires:
 * 1. This receiver is triggered by AlarmManager at the scheduled time
 * 2. We start AgentService (foreground) with the rule's action
 * 3. AgentService runs the action via the agent loop
 * 4. Then we reschedule the rule for its next occurrence
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
            // Still reschedule in case user re-enables later
            return
        }

        Log.i(TAG, "Rule fired: ${rule.name} → action: ${rule.action}")

        // 1. Log the trigger
        db.logAction("Rule fired: ${rule.name} — ${rule.action}")

        // 2. Start AgentService to execute the rule's action
        val serviceIntent = Intent(context, AgentService::class.java).apply {
            action = AgentService.ACTION_PROCESS_RULE
            putExtra(AgentService.EXTRA_RULE_NAME, rule.name)
            putExtra(AgentService.EXTRA_RULE_ACTION, rule.action)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // 3. Reschedule the rule for its next occurrence
        // We do this from the receiver (not the service) so it happens immediately,
        // even if the service takes time to run the action.
        val scheduler = RuleScheduler(context)
        if (rule.triggerType == "time") {
            scheduler.scheduleTimeRule(rule)
        }
    }
}
