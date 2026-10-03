package com.arsys.netatmo.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.dao.HomeCacheDao
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import com.arsys.netatmo.domain.model.TemperatureDataPoint
import com.arsys.netatmo.domain.usecase.GetTemperatureHistoryUseCase
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatisticsUiState(
    val temperatureData: List<TemperatureDataPoint> = emptyList(),
    val avgTemp: Double? = null,
    val maxTemp: Double? = null,
    val minTemp: Double? = null,
    val heatingHours: Int? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val availableRooms: List<Pair<String, String>> = emptyList(),
    val selectedRoomId: String = ""
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val getTemperatureHistory: GetTemperatureHistoryUseCase,
    private val authRepository: AuthRepository,
    private val homeCacheDao: HomeCacheDao,
    private val thermostatRepository: ThermostatRepository
) : ViewModel() {

    private val gson = Gson()
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val homeId = authRepository.selectedHomeId.first() ?: return@launch
            val home = homeCacheDao.getHome(homeId)
            home?.let {
                val rooms: List<com.arsys.netatmo.data.api.models.Room> = try {
                    gson.fromJson(it.roomsJson, Array<com.arsys.netatmo.data.api.models.Room>::class.java)
                        ?.toList() ?: emptyList()
                } catch (e: Exception) { emptyList() }

                val roomPairs = rooms.map { r -> r.id to r.name }
                val firstRoomId = roomPairs.firstOrNull()?.first ?: ""
                _uiState.update {
                    it.copy(availableRooms = roomPairs, selectedRoomId = firstRoomId)
                }
            }
        }
    }

    fun selectRoom(roomId: String) {
        _uiState.update { it.copy(selectedRoomId = roomId) }
    }

    fun loadStatistics(days: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val homeId = authRepository.selectedHomeId.first() ?: run {
                _uiState.update { it.copy(isLoading = false, error = "No hay hogar seleccionado") }
                return@launch
            }
            val roomId = _uiState.value.selectedRoomId

            getTemperatureHistory(homeId, roomId, days)
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { data ->
                    if (data.isEmpty()) {
                        // Intentar cargar datos remotos si no hay historial local
                        thermostatRepository.getHomeStatus(homeId)
                        _uiState.update { it.copy(isLoading = false) }
                        return@collect
                    }
                    val temps = data.map { it.temperature }
                    val heatingPoints = data.count { it.heatingActive }
                    val heatingHours = if (data.size > 1)
                        (heatingPoints * (days * 24.0 / data.size)).toInt()
                    else 0

                    _uiState.update {
                        it.copy(
                            temperatureData = data,
                            avgTemp = temps.average(),
                            maxTemp = temps.max(),
                            minTemp = temps.min(),
                            heatingHours = heatingHours,
                            isLoading = false
                        )
                    }
                }
        }
    }
}
