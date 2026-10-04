package com.arsys.netatmo.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.dao.HomeCacheDao
import com.arsys.netatmo.data.local.dao.TemperatureHistoryDao
import com.arsys.netatmo.data.local.entities.TemperatureHistoryEntity
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import com.arsys.netatmo.domain.model.TemperatureDataPoint
import com.arsys.netatmo.domain.usecase.GetTemperatureHistoryUseCase
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val selectedRoomId: String = "",
    // 24h history points from TemperatureHistoryDao
    val historyPoints: List<TemperatureHistoryEntity> = emptyList(),
    // Heating report metrics (today / last 24h)
    val totalHeatingHoursToday: Float = 0f,
    val totalHeatingHoursWeek: Float = 0f,
    val energyKwhToday: Float = 0f,
    val estimatedCostToday: Float = 0f,
    val kwhPrice: Float = 0.15f
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val getTemperatureHistory: GetTemperatureHistoryUseCase,
    private val authRepository: AuthRepository,
    private val homeCacheDao: HomeCacheDao,
    private val thermostatRepository: ThermostatRepository,
    private val temperatureHistoryDao: TemperatureHistoryDao
) : ViewModel() {

    private val gson = Gson()
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    private var historyJob: Job? = null

    init {
        // React to room selection changes and reload 24h history automatically
        viewModelScope.launch {
            val homeId = authRepository.selectedHomeId.first() ?: return@launch
            _uiState
                .map { it.selectedRoomId }
                .distinctUntilChanged()
                .filter { it.isNotEmpty() }
                .collect { roomId ->
                    loadHistory24h(homeId, roomId)
                }
        }

        // Load room list from HomeCacheDao Flow
        viewModelScope.launch {
            val homeId = authRepository.selectedHomeId.first() ?: return@launch
            homeCacheDao.getAllHomes()
                .collect { homes ->
                    val home = homes.find { it.homeId == homeId }
                    home?.let { cachedHome ->
                        val rooms: List<com.arsys.netatmo.data.api.models.Room> = try {
                            gson.fromJson(
                                cachedHome.roomsJson,
                                Array<com.arsys.netatmo.data.api.models.Room>::class.java
                            )?.toList() ?: emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                        val roomPairs = rooms.map { r -> r.id to r.name }
                        val currentRoomId = _uiState.value.selectedRoomId
                        val firstRoomId = if (currentRoomId.isEmpty())
                            roomPairs.firstOrNull()?.first ?: ""
                        else
                            currentRoomId
                        _uiState.update { state ->
                            state.copy(availableRooms = roomPairs, selectedRoomId = firstRoomId)
                        }
                    }
                }
        }
    }

    /** Switch the active room; the init observer reacts and reloads the 24h history. */
    fun selectRoom(roomId: String) {
        _uiState.update { it.copy(selectedRoomId = roomId) }
    }

    /** Subscribe to the last-24h TemperatureHistory for [homeId]/[roomId]. */
    private fun loadHistory24h(homeId: String, roomId: String) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            val from = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            temperatureHistoryDao.getHistory(homeId, roomId, from)
                .catch { /* ignore – no local data yet */ }
                .collect { entities ->
                    val sorted = entities.sortedBy { it.timestamp }
                    val heatingHoursToday = calculateHeatingHours(sorted)
                    val energyToday = heatingHoursToday * 1.5f   // 1.5 kW boiler
                    val price = _uiState.value.kwhPrice
                    _uiState.update { state ->
                        state.copy(
                            historyPoints = sorted,
                            totalHeatingHoursToday = heatingHoursToday,
                            energyKwhToday = energyToday,
                            estimatedCostToday = energyToday * price
                        )
                    }
                }
        }
    }

    /**
     * Sum the time intervals where [TemperatureHistoryEntity.heatingActive] is true.
     * Returns the total in hours.
     */
    private fun calculateHeatingHours(points: List<TemperatureHistoryEntity>): Float {
        if (points.size < 2) return 0f
        var heatingMs = 0L
        for (i in 0 until points.size - 1) {
            if (points[i].heatingActive) {
                heatingMs += points[i + 1].timestamp - points[i].timestamp
            }
        }
        return heatingMs / 3_600_000f
    }

    /** Load multi-day aggregate statistics for the period selector. */
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
