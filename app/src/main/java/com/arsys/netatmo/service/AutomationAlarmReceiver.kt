package com.arsys.netatmo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Wakes the engine when the scheduled trigger instant arrives. The engine remains the
 * single executor: it evaluates triggers and applies its fired/consumed marks, so an
 * alarm-driven run and a periodic run cannot fire the same trigger twice.
 */
class AutomationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutomationAlarmScheduler.ACTION_FIRE) return
        AdvancedAutomationWorker.runNow(context)
    }
}
