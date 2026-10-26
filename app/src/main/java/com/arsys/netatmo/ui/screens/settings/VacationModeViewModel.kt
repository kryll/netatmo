package com.arsys.netatmo.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.entities.AdvancedAutomationEntity
import com.arsys.netatmo.data.repository.AdvancedAutomationRepository
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.VacationModeRepository
import com.arsys.netatmo.data.repository.VacationState
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VacationModeUiState(
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class VacationModeViewModel @Inject constructor(
    private val vacationRepo: VacationModeRepository,
    private val advancedAutomationRepository: AdvancedAutomationRepository
) : ViewModel() {

    private suspend fun deleteManagedAutomations() {
        advancedAutomationRepository.getAllAdvancedAutomations().first()
            .filter { it.description == MANAGED_MARKER || it.description == MIGRATED_MARKER }
            .forEach { advancedAutomationRepository.delete(it) }
    }

    private val _uiState = MutableStateFlow(VacationModeUiState())
    val uiState: StateFlow<VacationModeUiState> = _uiState.asStateFlow()

    val vacationState: StateFlow<VacationState> = vacationRepo.state

    fun updateStart(startMs: Long) {
        _uiState.update { it.copy(startMs = startMs, error = null) }
    }

    fun updateEnd(endMs: Long) {
        _uiState.update { it.copy(endMs = endMs, error = null) }
    }

    fun setFrozenAlerts(enabled: Boolean) {
        viewModelScope.launch { vacationRepo.setFrozenAlerts(enabled) }
    }

    fun activate() {
        val state = _uiState.value
        if (state.startMs == 0L || state.endMs == 0L) {
            _uiState.update { it.copy(error = "Selecciona las fechas de inicio y fin") }
            return
        }
        if (state.endMs <= state.startMs) {
            _uiState.update { it.copy(error = "La fecha de fin debe ser posterior a la de inicio") }
            return
        }
        if (state.endMs <= System.currentTimeMillis()) {
            _uiState.update { it.copy(error = "La fecha de fin debe ser futura") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                deleteManagedAutomations()
                if (state.startMs <= System.currentTimeMillis()) {
                    // Starts today: apply frost guard right away; Netatmo reverts at the end.
                    val result = vacationRepo.activate(state.startMs, state.endMs)
                    if (result is ApiResult.Error) {
                        _uiState.update { it.copy(error = "No se pudo activar el modo vacaciones: ${result.message}") }
                    }
                } else {
                    // Future start: an advanced automation owns it; nothing is sent now.
                    vacationRepo.scheduleFuture(state.startMs, state.endMs)
                    advancedAutomationRepository.save(
                        AdvancedAutomationEntity(
                            name = "Vacaciones",
                            description = MANAGED_MARKER,
                            enabled = true,
                            triggersJson = JSONArray().put(
                                JSONObject().put("type", "datetime").put("at", state.startMs)
                            ).toString(),
                            actionsJson = JSONArray().put(
                                JSONObject()
                                    .put("type", "set_home_mode")
                                    .put("mode", "hg")
                                    .put("endAt", state.endMs)
                            ).toString()
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Error al activar el modo vacaciones") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deactivate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                deleteManagedAutomations()
                val result = vacationRepo.deactivate()
                if (result is ApiResult.Error) {
                    _uiState.update { it.copy(error = "No se pudo restaurar la programacion: ${result.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Error al desactivar el modo vacaciones") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private companion object {
        const val MANAGED_MARKER = "vacation:managed"
        const val MIGRATED_MARKER = "migrated:vacation:start"
    }
}
