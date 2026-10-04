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
    val successMessage: String? = null
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
                    _uiState.update { it.copy(isLoading = false, thermostatState = result.data) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
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
            boostManager.startBoost(
                homeId = homeId,
                roomId = room.id,
                originalTemp = originalTemp,
                deltaTemp = deltaTemp,
                durationMinutes = durationMinutes
            )
        }
    }

    fun stopBoost() {
        viewModelScope.launch {
            boostManager.stopBoost()
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
    fun dismissSuccess() = _uiState.update { it.copy(successMessage = null) }
}
