package com.arsys.netatmo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Wakes the engine when the scheduled trigger instant arrives.
 *
 * The evaluation runs directly in this broadcast's goAsync window instead of only enqueueing
 * a WorkManager job. Field evidence from v201 (José's log, 30 Oct 2026) showed the alarm
 * arriving on time but the enqueued job never starting for minutes, even with the app open:
 * WorkManager dispatch was the failure point, so the alarm path no longer depends on it.
 * The engine remains the single executor: its mutex and fired/consumed marks keep an
 * alarm-driven run and a periodic run from firing the same trigger twice. If the direct run
 * does not finish within the broadcast window (slow network), the worker is enqueued as the
 * fallback, as expedited work.
 */
@AndroidEntryPoint
class AutomationAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var engine: AdvancedAutomationEngine

    @Inject
    lateinit var alarmScheduler: AutomationAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutomationAlarmScheduler.ACTION_FIRE) return
        runCatching {
            val armed = context.getSharedPreferences("advanced_automation_state", Context.MODE_PRIVATE)
                .getLong("alarm_next_at", 0L)
            val lateS = if (armed > 0) (System.currentTimeMillis() - armed) / 1000 else 0
            DiagnosticLog.log(context, "ALARMA", "Alarma recibida. Prevista ${DiagnosticLog.fmt(armed)}, retraso ${lateS}s. Evaluación directa (sin depender del worker).")
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val startedAt = System.currentTimeMillis()
                try {
                    val finished = withTimeoutOrNull(DIRECT_RUN_TIMEOUT_MS) {
                        engine.evaluateAndExecuteAll()
                    }
                    if (finished == null) {
                        DiagnosticLog.log(context, "ALARMA", "Evaluación directa superó ${DIRECT_RUN_TIMEOUT_MS / 1000}s; se encola el worker acelerado como respaldo.")
                        AdvancedAutomationWorker.runNow(context)
                    } else {
                        DiagnosticLog.log(context, "ALARMA", "Evaluación directa terminada en ${System.currentTimeMillis() - startedAt}ms.")
                    }
                } catch (e: Exception) {
                    DiagnosticLog.log(context, "ALARMA", "ERROR en evaluación directa: ${e.javaClass.simpleName}: ${e.message}. Se encola el worker acelerado como respaldo.")
                    AdvancedAutomationWorker.runNow(context)
                }
                try {
                    alarmScheduler.reschedule()
                } catch (e: Exception) {
                    DiagnosticLog.log(context, "ALARMA", "ERROR rearmando: ${e.javaClass.simpleName}: ${e.message}")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        // goAsync gives roughly 10s before the system considers the broadcast stalled; leave margin.
        const val DIRECT_RUN_TIMEOUT_MS = 8_000L
    }
}
