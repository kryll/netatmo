package com.arsys.netatmo.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.arsys.netatmo.service.AdvancedAutomationEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class AdvancedAutomationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val engine: AdvancedAutomationEngine,
    private val alarmScheduler: AutomationAlarmScheduler
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        DiagnosticLog.log(applicationContext, "WORKER", "Inicio evaluación (intento ${runAttemptCount + 1}, tags=${tags.filter { it.contains("Automation") || it.contains("automation") }})")
        try {
            engine.evaluateAndExecuteAll()
        } catch (e: Exception) {
            // Same as v195: the exception still propagates and fails/retries the work as before.
            DiagnosticLog.log(applicationContext, "WORKER", "ERROR evaluando (se propaga, el worker falla como antes): ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
        DiagnosticLog.log(applicationContext, "WORKER", "Fin evaluación")
        // Rearm the next exact alarm after every run (a fired trigger may reveal a new "next").
        try {
            alarmScheduler.reschedule()
        } catch (e: Exception) {
            DiagnosticLog.log(applicationContext, "ALARMA", "ERROR rearmando: ${e.javaClass.simpleName}: ${e.message}")
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "advanced_automation_worker"

        /** Evaluates the automations once right away (after saving one, or when the app opens). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<AdvancedAutomationWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                // Expedited so the immediate evaluation is not held back by standby buckets or
                // deferral; falls back to a normal job when the expedited quota is exhausted.
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("advanced_automation_now", ExistingWorkPolicy.REPLACE, request)
            DiagnosticLog.log(context, "WORKER", "Evaluación inmediata encolada (acelerada, id ${request.id})")
        }

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<AdvancedAutomationWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
