package com.arsys.netatmo.ui.screens.scenarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.dao.HomeCacheDao
import com.arsys.netatmo.data.local.entities.ScenarioEntity
import com.arsys.netatmo.data.repository.AutomationRepository
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.domain.model.ScenarioAction
import com.arsys.netatmo.domain.model.ThermostatMode
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoomActionUi(
    val roomId: String = "",
    val roomName: String = "",
    val temperature: Double = 20.0,
    val mode: ThermostatMode = ThermostatMode.MANUAL
)

data class ScenarioDetailUiState(
    val name: String = "",
    val color: String = "#1976D2",
    val roomActions: List<RoomActionUi> = listOf(RoomActionUi()),
    val availableRooms: List<Pair<String, String>> = emptyList(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ScenarioDetailViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val authRepository: AuthRepository,
    private val homeCacheDao: HomeCacheDao
) : ViewModel() {

    private val gson = Gson()
    private val _uiState = MutableStateFlow(ScenarioDetailUiState())
    val uiState: StateFlow<ScenarioDetailUiState> = _uiState.asStateFlow()

    private var editingId: Long = -1L

    init {
        loadRooms()
    }

    private fun loadRooms() {
        viewModelScope.launch {
            val homeId = authRepository.selectedHomeId.first() ?: return@launch
            val home = homeCacheDao.getHome(homeId) ?: return@launch
            val rooms: List<com.arsys.netatmo.data.api.models.Room> = try {
                gson.fromJson(home.roomsJson, Array<com.arsys.netatmo.data.api.models.Room>::class.java)?.toList() ?: emptyList()
            } catch (e: Exception) { emptyList() }
            _uiState.update { it.copy(availableRooms = rooms.map { r -> r.id to r.name }) }
        }
    }

    fun load(scenarioId: Long) {
        if (scenarioId == -1L) return
        editingId = scenarioId
        viewModelScope.launch {
            val scenario = automationRepository.getScenarioById(scenarioId) ?: return@launch
            val actions: List<ScenarioAction> = try {
                gson.fromJson(scenario.actionsJson, Array<ScenarioAction>::class.java)?.toList() ?: emptyList()
            } catch (e: Exception) { emptyList() }
            _uiState.update {
                it.copy(
                    name = scenario.name,
                    color = scenario.color,
                    roomActions = actions.map { a ->
                        RoomActionUi(roomId = a.roomId, roomName = a.roomName, temperature = a.temperature, mode = a.mode)
                    }.ifEmpty { listOf(RoomActionUi()) }
                )
            }
        }
    }

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }
    fun updateColor(color: String) = _uiState.update { it.copy(color = color) }

    fun addRoom() = _uiState.update {
        it.copy(roomActions = it.roomActions + RoomActionUi())
    }

    fun removeRoom(index: Int) = _uiState.update {
        if (it.roomActions.size > 1)
            it.copy(roomActions = it.roomActions.toMutableList().also { list -> list.removeAt(index) })
        else it
    }

    fun updateRoomId(index: Int, roomId: String, roomName: String) = _uiState.update {
        val list = it.roomActions.toMutableList()
        list[index] = list[index].copy(roomId = roomId, roomName = roomName)
        it.copy(roomActions = list)
    }

    fun updateRoomTemperature(index: Int, temp: Double) = _uiState.update {
        val list = it.roomActions.toMutableList()
        list[index] = list[index].copy(temperature = temp)
        it.copy(roomActions = list)
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "El nombre es obligatorio") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val homeId = authRepository.selectedHomeId.first() ?: ""
            val actions = state.roomActions.map { a ->
                ScenarioAction(roomId = a.roomId, roomName = a.roomName, temperature = a.temperature, mode = a.mode)
            }
            val scenario = ScenarioEntity(
                id = if (editingId == -1L) 0L else editingId,
                name = state.name,
                color = state.color,
                homeId = homeId,
                actionsJson = gson.toJson(actions)
            )
            try {
                automationRepository.saveScenario(scenario)
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}
