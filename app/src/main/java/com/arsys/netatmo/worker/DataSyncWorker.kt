package com.arsys.netatmo.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class DataSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val thermostatRepository: ThermostatRepository,
    private val authRepository: AuthRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val homeId = authRepository.selectedHomeId.first() ?: return Result.success()
        return when (thermostatRepository.getHomeStatus(homeId)) {
            is ApiResult.Success -> Result.success()
            is ApiResult.Error -> Result.retry()
            ApiResult.Loading -> Result.retry()
        }
    }
}
