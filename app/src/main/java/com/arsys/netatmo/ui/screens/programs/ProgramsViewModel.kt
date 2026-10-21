package com.arsys.netatmo.ui.screens.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgramUi(
    val id: String,
    val name: String,
    val isSelected: Boolean,
    val zoneCount: Int,
    val slotCount: Int,
    val minTemp: Double?,
    val maxTemp: Double?
)

data class ProgramsUiState(
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val programs: List<ProgramUi> = emptyList(),
    val followsSchedule: Boolean = true,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class ProgramsViewModel @Inject constructor(
    private val repository: ThermostatRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgramsUiState())
    val uiState: StateFlow<ProgramsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadPrograms() }
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        refresh()
    }

    fun activate(program: ProgramUi) {
        runAction {
            val homeId = authRepository.selectedHomeId.first() ?: return@runAction "No hay hogar seleccionado"
            if (!program.isSelected) {
                val switched = repository.switchSchedule(homeId, program.id)
                if (switched is ApiResult.Error) {
                    return@runAction "No se pudo activar \"${program.name}\": ${switched.message}"
                }
            }
            when (val mode = repository.setHomeMode(homeId, "schedule")) {
                is ApiResult.Success -> "Programa \"${program.name}\" activado"
                is ApiResult.Error ->
                    "El programa se seleccionó, pero no se pudo volver al modo programado: ${mode.message}"
                ApiResult.Loading -> null
            }
        }
    }

    fun deactivate() {
        runAction {
            val homeId = authRepository.selectedHomeId.first() ?: return@runAction "No hay hogar seleccionado"
            when (val mode = repository.setHomeMode(homeId, "hg")) {
                is ApiResult.Success -> "Programa desactivado: el termostato está en antihielo"
                is ApiResult.Error -> "No se pudo desactivar el programa: ${mode.message}"
                ApiResult.Loading -> null
            }
        }
    }

    fun delete(program: ProgramUi) {
        if (program.isSelected) {
            _uiState.update { it.copy(message = "No se puede eliminar el programa activo") }
            return
        }
        runAction {
            val homeId = authRepository.selectedHomeId.first() ?: return@runAction "No hay hogar seleccionado"
            when (val result = repository.deleteSchedule(homeId, program.id)) {
                is ApiResult.Success -> "Programa \"${program.name}\" eliminado"
                is ApiResult.Error -> "No se pudo eliminar \"${program.name}\": ${result.message}"
                ApiResult.Loading -> null
            }
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    private fun runAction(block: suspend () -> String?) {
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            val message = block()
            delay(500)
            loadJob?.cancel()
            loadPrograms()
            _uiState.update { it.copy(isWorking = false, message = message ?: it.message) }
        }
    }

    private suspend fun loadPrograms() {
        val homeId = authRepository.selectedHomeId.first()
        if (homeId == null) {
            _uiState.update { it.copy(isLoading = false, error = "No hay ningún hogar seleccionado") }
            return
        }
        when (val result = repository.getHomesData()) {
            is ApiResult.Success -> {
                val home = result.data.find { it.id == homeId }
                if (home == null) {
                    _uiState.update { it.copy(isLoading = false, error = "No se encontró el hogar seleccionado") }
                    return
                }
                val programs = home.schedules.orEmpty()
                    .filter { it.type == null || it.type == "therm" }
                    .map { schedule ->
                        val temps = schedule.zones.orEmpty()
                            .flatMap { zone -> zone.rooms.orEmpty() }
                            .mapNotNull { room -> room.temperature }
                        ProgramUi(
                            id = schedule.id,
                            name = schedule.name,
                            isSelected = schedule.selected == true,
                            zoneCount = schedule.zones.orEmpty().size,
                            slotCount = schedule.timetable.orEmpty().size,
                            minTemp = temps.minOrNull(),
                            maxTemp = temps.maxOrNull()
                        )
                    }
                val follows = if (programs.any { it.isSelected }) readFollowsSchedule(homeId) else true
                _uiState.update {
                    it.copy(isLoading = false, programs = programs, followsSchedule = follows, error = null)
                }
            }
            is ApiResult.Error -> _uiState.update {
                if (it.programs.isEmpty()) it.copy(isLoading = false, error = result.message)
                else it.copy(isLoading = false, message = "No se pudo actualizar: ${result.message}")
            }
            ApiResult.Loading -> {}
        }
    }

    // El hogar deja de seguir el programa si alguna habitación está en antihielo, ausente o apagada.
    // Si el estado no se puede leer se asume que sigue el programa.
    private suspend fun readFollowsSchedule(homeId: String): Boolean {
        val status = repository.getHomeStatus(homeId)
        if (status !is ApiResult.Success) return true
        val modes = status.data.rooms.orEmpty().mapNotNull { it.setpointMode }
        return modes.none { it == "hg" || it == "away" || it == "off" }
    }
}
