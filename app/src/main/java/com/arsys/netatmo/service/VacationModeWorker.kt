package com.arsys.netatmo.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.VacationModeRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class VacationModeWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val vacationRepository: VacationModeRepository
) : CoroutineWorker(ctx, params) {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_END = "end"
        private const val KEY_ACTION = "action"
        private const val START_WORK_NAME = "vacation_start"
        private const val END_WORK_NAME = "vacation_end"
        private const val MAX_ATTEMPTS = 5

        fun schedule(context: Context, action: String, delayMs: Long) {
            val request = OneTimeWorkRequestBuilder<VacationModeWorker>()
                .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_ACTION to action))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            val name = if (action == ACTION_START) START_WORK_NAME else END_WORK_NAME
            WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(START_WORK_NAME)
            workManager.cancelUniqueWork(END_WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val result = when (inputData.getString(KEY_ACTION)) {
            ACTION_START -> vacationRepository.applyScheduledStart()
            ACTION_END -> vacationRepository.finishVacation()
            else -> return Result.failure()
        }
        if (result is ApiResult.Success) return Result.success()
        if (runAttemptCount < MAX_ATTEMPTS) return Result.retry()
        vacationRepository.markInactive()
        return Result.failure()
    }
}
