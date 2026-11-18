package com.arsys.netatmo.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import com.arsys.netatmo.domain.model.ThermostatMode
import com.arsys.netatmo.domain.model.ThermostatState
import com.arsys.netatmo.domain.usecase.GetThermostatStateUseCase
import com.arsys.netatmo.domain.usecase.SetModeUseCase
import com.arsys.netatmo.domain.usecase.SetTemperatureUseCase
import com.arsys.netatmo.service.BoostManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val thermostatState: ThermostatState? = null,
    val error: String? = null,
    val selectedHomeId: String? = null,
    val isSettingTemp: Boolean = false,
    val successMessage: String? = null,
    val outdoorTemperature: Double? = null,
    // True when homestatus answers OK but without rooms (Netatmo API degraded)
    val thermostatDataUnavailable: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getThermostatState: GetThermostatStateUseCase,
    private val setTemperature: SetTemperatureUseCase,
    private val setMode: SetModeUseCase,
    private val authRepository: AuthRepository,
    private val repository: ThermostatRepository,
    private val boostManager: BoostManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val boostState = boostManager.boostState
    val boostRemainingMinutes = boostManager.remainingMinutes

    init {
        viewModelScope.launch {
            authRepository.selectedHomeId.collect { homeId ->
                _uiState.update { it.copy(selectedHomeId = homeId) }
                homeId?.let { loadThermostatData(it) }
            }
        }
    }

    fun loadThermostatData(homeId: String? = null) {
        val id = homeId ?: _uiState.value.selectedHomeId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = getThermostatState(id)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            thermostatState = result.data,
                            thermostatDataUnavailable = result.data.rooms.isEmpty() && result.data.knownRoomNames.isNotEmpty()
                        )
                    }
                    // Best-effort: fetch outdoor temperature from NAModule1 if available
                    val outdoorTemp = repository.getOutdoorTemperature(id)
                    _uiState.update { it.copy(outdoorTemperature = outdoorTemp) }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = friendlyConnectionError(result.message),
                            thermostatDataUnavailable = false
                        )
                    }
                }
                ApiResult.Loading -> {}
            }
        }
    }

    fun setRoomTemperature(roomId: String, temperature: Double, durationMinutes: Int = 60) {
        val homeId = _uiState.value.selectedHomeId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSettingTemp = true) }
            when (val result = setTemperature(homeId, roomId, temperature, durationMinutes)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSettingTemp = false, successMessage = "Temperatura ajustada a ${temperature}°C") }
                    delay(500)
                    loadThermostatData()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isSettingTemp = false, error = result.message) }
                }
                ApiResult.Loading -> {}
            }
        }
    }

    fun setRoomMode(roomId: String, mode: ThermostatMode) {
        val homeId = _uiState.value.selectedHomeId ?: return
        viewModelScope.launch {
            when (val result = setMode(homeId, roomId, mode)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(successMessage = "Modo cambiado a ${mode.displayName}") }
                    delay(500)
                    loadThermostatData()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = result.message) }
                }
                ApiResult.Loading -> {}
            }
        }
    }

    fun switchSchedule(scheduleId: String) {
        val homeId = _uiState.value.thermostatState?.homeId ?: return
        viewModelScope.launch {
            when (repository.switchSchedule(homeId, scheduleId)) {
                is ApiResult.Success -> loadThermostatData()
                is ApiResult.Error -> { /* silent */ }
                ApiResult.Loading -> {}
            }
        }
    }

    fun startBoost(deltaTemp: Double, durationMinutes: Int) {
        val homeId = _uiState.value.selectedHomeId ?: return
        val room = _uiState.value.thermostatState?.rooms?.firstOrNull() ?: return
        val originalTemp = room.targetTemp ?: room.currentTemp ?: return
        viewModelScope.launch {
            val result = boostManager.startBoost(
                homeId = homeId,
                roomId = room.id,
                originalTemp = originalTemp,
                deltaTemp = deltaTemp,
                durationMinutes = durationMinutes,
                previousMode = room.mode.apiValue
            )
            if (result is ApiResult.Error) {
                _uiState.update { it.copy(error = "No se pudo activar el modo Turbo: ${result.message}") }
            }
        }
    }

    fun stopBoost() {
        viewModelScope.launch {
            boostManager.stopBoost()
        }
    }

    /**
     * Activates home-level away mode. When [endTimeEpochSeconds] is null the mode stays
     * until the user changes it; otherwise Netatmo's servers return the home to schedule
     * mode at that time (works with the app closed — no local timer involved).
     */
    fun setAwayMode(endTimeEpochSeconds: Long?) {
        val homeId = _uiState.value.selectedHomeId ?: return
        // Defensive: a past end time must never reach the API (Netatmo would reject or
        // end the mode immediately); the sheet validates too, but do not rely on it.
        if (endTimeEpochSeconds != null && endTimeEpochSeconds <= System.currentTimeMillis() / 1000) {
            _uiState.update { it.copy(error = "La fecha y hora debe ser posterior a ahora") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSettingTemp = true) }
            when (val result = repository.setHomeMode(homeId, "away", endTimeEpochSeconds)) {
                is ApiResult.Success -> {
                    val msg = if (endTimeEpochSeconds != null)
                        "Modo Ausente activado hasta el ${formatEndTimeEs(endTimeEpochSeconds)}"
                    else
                        "Modo Ausente activado hasta que lo cambies"
                    _uiState.update { it.copy(isSettingTemp = false, successMessage = msg) }
                    delay(500)
                    loadThermostatData()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isSettingTemp = false, error = "No se pudo activar el modo Ausente: ${result.message}") }
                }
                ApiResult.Loading -> {}
            }
        }
    }

    /** Leaves away mode (or any home-level mode) and returns to the scheduled mode. */
    fun returnToSchedule() {
        val homeId = _uiState.value.selectedHomeId ?: return
        viewModelScope.launch {
            when (val result = repository.setHomeMode(homeId, "schedule")) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(successMessage = "Modo Programado restaurado") }
                    delay(500)
                    loadThermostatData()
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(error = "No se pudo volver al modo Programado: ${result.message}") }
                }
                ApiResult.Loading -> {}
            }
        }
    }

    /** Map low-level network/API errors to a clear, actionable Spanish message. */
    private fun friendlyConnectionError(message: String?): String {
        val msg = message ?: return "No se puede conectar con Netatmo. Inténtalo de nuevo."
        val lower = msg.lowercase()
        return when {
            "unable to resolve host" in lower || "failed to connect" in lower ||
                "timeout" in lower || "unknownhost" in lower || "error de red" in lower ->
                "No se puede conectar con Netatmo. Comprueba tu conexión e inténtalo de nuevo."
            msg.startsWith("Error 5") ->
                "Netatmo no responde ahora mismo ($msg). Inténtalo de nuevo en unos minutos."
            else -> msg
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
    fun dismissSuccess() = _uiState.update { it.copy(successMessage = null) }
}

/** "20/11 a las 18:30" — device-local rendering of an epoch-seconds instant. */
internal fun formatEndTimeEs(epochSeconds: Long): String {
    val fmt = java.text.SimpleDateFormat("dd/MM 'a las' HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(epochSeconds * 1000))
}
