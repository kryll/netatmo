package com.arsys.netatmo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Wakes the engine when the scheduled trigger instant arrives. The engine remains the
 * single executor: it evaluates triggers and applies its fired/consumed marks, so an
 * alarm-driven run and a periodic run cannot fire the same trigger twice.
 *
 * The evaluation is enqueued as expedited work (see AdvancedAutomationWorker.runNow):
 * Android runs expedited jobs immediately, even from Doze, subject to the expedited
 * quota, and falls back to a normal job when the quota is exhausted. The
 * AutomationWorkWatchdog logs the work state transitions so a missed or failed
 * dispatch is visible in the diagnostic log instead of passing silently.
 *
 * v205 ran the engine directly in this receiver with an 8s cap; that was reverted in
 * v206: a capped run can interrupt the engine between actions of one automation, and
 * with the fired/consumed marks kept, the remaining actions of a one-shot trigger
 * would never run. Running the engine to completion needs a real execution envelope
 * (worker), not a broadcast window.
 */
class AutomationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutomationAlarmScheduler.ACTION_FIRE) return
        runCatching {
            val armed = context.getSharedPreferences("advanced_automation_state", Context.MODE_PRIVATE)
                .getLong("alarm_next_at", 0L)
            val lateS = if (armed > 0) (System.currentTimeMillis() - armed) / 1000 else 0
            DiagnosticLog.log(context, "ALARMA", "Alarma recibida. Prevista ${DiagnosticLog.fmt(armed)}, retraso ${lateS}s. Encolando evaluación acelerada.")
        }
        AdvancedAutomationWorker.runNow(context)
    }
}
