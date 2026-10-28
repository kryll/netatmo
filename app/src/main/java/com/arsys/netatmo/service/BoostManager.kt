package com.arsys.netatmo.service

import android.content.Context
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.ThermostatRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class BoostState(
    val homeId: String,
    val roomId: String,
    val boostedTemp: Double,
    val originalTemp: Double,
    /** Setpoint mode of the room before the boost ("manual", "schedule", ...), restored at the end. */
    val previousMode: String,
    val endTimeMs: Long,
    val durationMinutes: Int
)

@Singleton
class BoostManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val thermostatRepository: ThermostatRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _boostState = MutableStateFlow<BoostState?>(null)
    val boostState: StateFlow<BoostState?> = _boostState.asStateFlow()

    private val _remainingMinutes = MutableStateFlow(0)
    val remainingMinutes: StateFlow<Int> = _remainingMinutes.asStateFlow()

    private var timerJob: Job? = null

    /**
     * Raises the setpoint for [durationMinutes]. The boosted manual setpoint carries an end
     * time, so Netatmo resumes the schedule by itself when it ends; the restore below only
     * re-applies the previous mode (or the original setpoint if the room was already manual).
     * Returns the API result: on failure no boost state is shown.
     */
    suspend fun startBoost(
        homeId: String,
        roomId: String,
        originalTemp: Double,
        deltaTemp: Double,
        durationMinutes: Int,
        previousMode: String = "manual"
    ): ApiResult<Unit> {
        val boostedTemp = (originalTemp + deltaTemp).coerceAtMost(30.0)
        val endMs = System.currentTimeMillis() + durationMinutes * 60_000L

        val result = thermostatRepository.setTemperature(
            homeId = homeId,
            roomId = roomId,
            temperature = boostedTemp,
            durationMinutes = durationMinutes
        )
        if (result is ApiResult.Error) return result

        _boostState.value = BoostState(
            homeId = homeId,
            roomId = roomId,
            boostedTemp = boostedTemp,
            originalTemp = originalTemp,
            previousMode = previousMode,
            endTimeMs = endMs,
            durationMinutes = durationMinutes
        )

        timerJob?.cancel()
        timerJob = scope.launch {
            while (System.currentTimeMillis() < endMs) {
                _remainingMinutes.value =
                    ((endMs - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(0)
                delay(30_000L)
            }
            restoreTemperature()
        }
        return result
    }

    suspend fun stopBoost() {
        timerJob?.cancel()
        timerJob = null
        restoreTemperature()
    }

    private suspend fun restoreTemperature() {
        val state = _boostState.value ?: return
        if (state.previousMode == "manual") {
            // The room was already manual: pin the original setpoint back (Netatmo's own
            // end-time revert would switch it to the schedule instead).
            thermostatRepository.setTemperature(
                homeId = state.homeId,
                roomId = state.roomId,
                temperature = state.originalTemp,
                durationMinutes = 0
            )
        } else {
            // Schedule (or a home mode): go back to it instead of pinning a permanent manual
            // setpoint, which would silently stop the room from following its programme.
            thermostatRepository.setMode(state.homeId, state.roomId, state.previousMode)
        }
        _boostState.value = null
        _remainingMinutes.value = 0
    }
}
