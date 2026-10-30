package com.arsys.netatmo.service

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Logs WorkManager state transitions of the automation evaluation works into the diagnostic
 * log. The v201 field evidence (30 Oct 2026) showed an alarm received on time whose enqueued
 * evaluation never produced a worker run; these lines tell apart "never dispatched" (stays
 * ENQUEUED), "fails before/at worker start" (FAILED with attempts) and healthy
 * ENQUEUED -> RUNNING -> SUCCEEDED. Never logs secrets: only work label, state and reason.
 */
object AutomationWorkWatchdog {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val transitions = WorkTransitionLog()

    private val works = mapOf(
        "advanced_automation_now" to "inmediata",
        "advanced_automation_worker" to "periódica"
    )

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        works.forEach { (uniqueName, label) ->
            scope.launch {
                try {
                    WorkManager.getInstance(appContext)
                        .getWorkInfosForUniqueWorkFlow(uniqueName)
                        .collect { infos ->
                            // REPLACE can leave a cancelled instance next to the new one:
                            // report the live one when there is any.
                            val info = infos.lastOrNull { it.state != WorkInfo.State.CANCELLED }
                                ?: infos.lastOrNull()
                                ?: return@collect
                            val line = transitions.next(
                                label,
                                info.state.name,
                                info.runAttemptCount,
                                stopReasonName(info.stopReason)
                            )
                            if (line != null) DiagnosticLog.log(appContext, "WORKERESTADO", line)
                        }
                } catch (e: Exception) {
                    DiagnosticLog.log(appContext, "WORKERESTADO", "ERROR observando $label: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
    }

    private fun stopReasonName(reason: Int): String = when (reason) {
        WorkInfo.STOP_REASON_NOT_STOPPED -> "sin parada"
        WorkInfo.STOP_REASON_CANCELLED_BY_APP -> "cancelado por la app"
        WorkInfo.STOP_REASON_PREEMPT -> "interrumpido por otra tarea"
        WorkInfo.STOP_REASON_TIMEOUT -> "tiempo agotado"
        WorkInfo.STOP_REASON_DEVICE_STATE -> "estado del dispositivo"
        WorkInfo.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "restricción batería no baja"
        WorkInfo.STOP_REASON_CONSTRAINT_CHARGING -> "restricción carga"
        WorkInfo.STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "restricción reposo"
        WorkInfo.STOP_REASON_CONSTRAINT_NETWORK_METERED -> "restricción red medida"
        WorkInfo.STOP_REASON_CONSTRAINT_NETWORK_ROAMING -> "restricción itinerancia"
        WorkInfo.STOP_REASON_CONSTRAINT_NETWORK_UNMETERED -> "restricción red no medida"
        WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "restricción almacenamiento"
        WorkInfo.STOP_REASON_QUOTA -> "cuota agotada"
        WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION -> "restricción en segundo plano"
        WorkInfo.STOP_REASON_APP_STANDBY -> "espera de la app"
        WorkInfo.STOP_REASON_USER -> "usuario"
        WorkInfo.STOP_REASON_SYSTEM_PROCESSING -> "procesando en el sistema"
        else -> "motivo $reason"
    }
}
