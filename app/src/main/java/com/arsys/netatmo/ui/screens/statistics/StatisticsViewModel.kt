package com.arsys.netatmo.ui.screens.statistics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.api.NetatmoApiService
import com.arsys.netatmo.data.local.dao.HomeCacheDao
import com.arsys.netatmo.data.local.dao.TemperatureHistoryDao
import com.arsys.netatmo.data.local.entities.TemperatureHistoryEntity
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import com.arsys.netatmo.domain.model.TemperatureDataPoint
import com.arsys.netatmo.domain.usecase.GetTemperatureHistoryUseCase
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class MonthlyHeatingData(val label: String, val heatingHours: Float)

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
    val kwhPrice: Float = 0.15f,
    val contractedKw: Float = 1.5f,
    // Edit mode
    val isEditMode: Boolean = false,
    val cardOrder: List<String> = listOf("24h", "heating", "summary", "monthly", "comparison", "tempChart", "heatingChart"),
    // Monthly comparison
    val monthlyComparison: List<MonthlyHeatingData> = emptyList(),
    // Monthly summary
    val monthlyHeatingHours: Float = 0f,
    val monthlyEnergyKwh: Float = 0f,
    val monthlyCost: Float = 0f
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val getTemperatureHistory: GetTemperatureHistoryUseCase,
    private val authRepository: AuthRepository,
    private val homeCacheDao: HomeCacheDao,
    private val thermostatRepository: ThermostatRepository,
    private val temperatureHistoryDao: TemperatureHistoryDao,
    private val apiService: NetatmoApiService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val gson = Gson()
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    private var historyJob: Job? = null

    init {
        // Load persisted price config
        val prefs = context.getSharedPreferences("statistics_prefs", Context.MODE_PRIVATE)
        val kwhPrice = prefs.getFloat("kwh_price", 0.15f)
        val contractedKw = prefs.getFloat("contracted_kw", 1.5f)
        _uiState.update { it.copy(kwhPrice = kwhPrice, contractedKw = contractedKw) }

        // React to room selection changes and reload 24h history automatically
        viewModelScope.launch {
            val homeId = authRepository.selectedHomeId.first() ?: return@launch
            _uiState
                .map { it.selectedRoomId }
                .distinctUntilChanged()
                .filter { it.isNotEmpty() }
                .collect { roomId ->
                    loadHistory24h(homeId, roomId)
                    loadMonthlySummary(homeId, roomId)
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

    fun toggleEditMode() {
        _uiState.update { it.copy(isEditMode = !it.isEditMode) }
    }

    fun moveCard(from: Int, to: Int) {
        val order = _uiState.value.cardOrder.toMutableList()
        if (from < 0 || to < 0 || from >= order.size || to >= order.size) return
        val item = order.removeAt(from)
        order.add(to, item)
        _uiState.update { it.copy(cardOrder = order) }
    }

    fun setPriceConfig(kwhPrice: Float, contractedKw: Float) {
        val prefs = context.getSharedPreferences("statistics_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("kwh_price", kwhPrice)
            .putFloat("contracted_kw", contractedKw)
            .apply()
        _uiState.update { it.copy(kwhPrice = kwhPrice, contractedKw = contractedKw) }
        // Recalculate costs
        val energy = _uiState.value.totalHeatingHoursToday * contractedKw
        _uiState.update { it.copy(energyKwhToday = energy, estimatedCostToday = energy * kwhPrice) }
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
                    val kw = _uiState.value.contractedKw
                    val energyToday = heatingHoursToday * kw
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

    private fun loadMonthlySummary(homeId: String, roomId: String) {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val monthStart = cal.timeInMillis

            temperatureHistoryDao.getHistory(homeId, roomId, monthStart)
                .catch { }
                .collect { entities ->
                    val sorted = entities.sortedBy { it.timestamp }
                    val hours = calculateHeatingHours(sorted)
                    val kw = _uiState.value.contractedKw
                    val price = _uiState.value.kwhPrice
                    val energy = hours * kw
                    _uiState.update { it.copy(
                        monthlyHeatingHours = hours,
                        monthlyEnergyKwh = energy,
                        monthlyCost = energy * price
                    ) }
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

            if (days >= 30) {
                loadStatisticsFromApi(homeId, roomId, days)
            } else {
                getTemperatureHistory(homeId, roomId, days)
                    .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                    .collect { data ->
                        val temps = data.map { it.temperature }
                        val heatingPoints = data.count { it.heatingActive }
                        val heatingHours = if (data.size > 1)
                            (heatingPoints * (days * 24.0 / data.size)).toInt()
                        else 0
                        _uiState.update {
                            it.copy(
                                temperatureData = data,
                                avgTemp = if (temps.isNotEmpty()) temps.average() else null,
                                maxTemp = if (temps.isNotEmpty()) temps.max() else null,
                                minTemp = if (temps.isNotEmpty()) temps.min() else null,
                                heatingHours = heatingHours,
                                isLoading = false
                            )
                        }
                    }
            }

            if (days >= 90) {
                loadMonthlyComparison(homeId, roomId, days)
            }
        }
    }

    private suspend fun loadStatisticsFromApi(homeId: String, roomId: String, days: Int) {
        try {
            val scale = when {
                days <= 30 -> "3hours"
                days <= 90 -> "1day"
                else -> "1week"
            }
            val dateBegin = (System.currentTimeMillis() / 1000) - (days * 24 * 60 * 60L)
            val response = apiService.getRoomMeasure(
                homeId = homeId,
                roomId = roomId,
                scale = scale,
                type = "temperature",
                dateBegin = dateBegin
            )
            if (response.isSuccessful) {
                val bodies = response.body()?.body ?: emptyList()
                val data = mutableListOf<TemperatureDataPoint>()
                for (body in bodies) {
                    val step = body.stepTime ?: 3600
                    body.value.forEachIndexed { index, vals ->
                        val temp = vals.firstOrNull() ?: return@forEachIndexed
                        val tsMs = (body.beginTime + index.toLong() * step) * 1000L
                        data.add(TemperatureDataPoint(timestamp = tsMs, temperature = temp, setpoint = null, heatingActive = false))
                    }
                }
                val temps = data.map { it.temperature }
                _uiState.update {
                    it.copy(
                        temperatureData = data,
                        avgTemp = if (temps.isNotEmpty()) temps.average() else null,
                        maxTemp = if (temps.isNotEmpty()) temps.max() else null,
                        minTemp = if (temps.isNotEmpty()) temps.min() else null,
                        heatingHours = null,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Error ${response.code()}") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    private fun loadMonthlyComparison(homeId: String, roomId: String, days: Int) {
        viewModelScope.launch {
            val from = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
            temperatureHistoryDao.getHistory(homeId, roomId, from)
                .catch { }
                .first()
                .let { entities ->
                    val sorted = entities.sortedBy { it.timestamp }
                    val monthMap = mutableMapOf<String, MutableList<TemperatureHistoryEntity>>()
                    val monthLabels = mutableMapOf<String, String>()
                    val monthAbbr = arrayOf("Ene","Feb","Mar","Abr","May","Jun","Jul","Ago","Sep","Oct","Nov","Dic")
                    sorted.forEach { entity ->
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = entity.timestamp
                        val key = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
                        val label = "${monthAbbr[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR).toString().takeLast(2)}"
                        monthMap.getOrPut(key) { mutableListOf() }.add(entity)
                        monthLabels[key] = label
                    }
                    val comparison = monthMap.keys.sorted().map { key ->
                        val pts = monthMap[key] ?: emptyList()
                        val hours = calculateHeatingHours(pts)
                        MonthlyHeatingData(label = monthLabels[key] ?: key, heatingHours = hours)
                    }
                    _uiState.update { it.copy(monthlyComparison = comparison) }
                }
        }
    }
}
