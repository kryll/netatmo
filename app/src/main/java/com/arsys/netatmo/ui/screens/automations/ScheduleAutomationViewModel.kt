package com.arsys.netatmo.ui.screens.automations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.entities.AutomationEntity
import com.arsys.netatmo.data.repository.AutomationRepository
import com.arsys.netatmo.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class ScheduleUiState(
    val name: String = "",
    val selectedDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    val startHour: Int = 7,
    val startMinute: Int = 0,
    val endHour: Int = 22,
    val endMinute: Int = 0,
    val targetTemperature: Double = 20.0,
    val enabled: Boolean = true,
    val saved: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ScheduleAutomationViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private var editingId: Long = -1L

    fun load(automationId: Long) {
        if (automationId == -1L) return
        editingId = automationId
        viewModelScope.launch {
            val automation = automationRepository.getAutomationById(automationId) ?: return@launch
            try {
                val trigger = JSONObject(automation.triggerData)
                val daysArray = trigger.optJSONArray("days")
                val days = mutableSetOf<Int>()
                if (daysArray != null) {
                    for (i in 0 until daysArray.length()) {
                        days.add(daysArray.getInt(i))
                    }
                }
                _uiState.update {
                    it.copy(
                        name = automation.name,
                        selectedDays = days.ifEmpty { setOf(1, 2, 3, 4, 5) },
                        startHour = trigger.optInt("startHour", 7),
                        startMinute = trigger.optInt("startMinute", 0),
                        endHour = trigger.optInt("endHour", 22),
                        endMinute = trigger.optInt("endMinute", 0),
                        targetTemperature = automation.targetTemperature,
                        enabled = automation.enabled
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        name = automation.name,
                        targetTemperature = automation.targetTemperature,
                        enabled = automation.enabled
                    )
                }
            }
        }
    }

    fun updateName(name: String) = _uiState.update { it.copy(name = name, error = null) }

    fun toggleDay(day: Int) {
        _uiState.update { state ->
            val days = state.selectedDays.toMutableSet()
            if (days.contains(day)) days.remove(day) else days.add(day)
            state.copy(selectedDays = days)
        }
    }

    fun updateStartHour(hour: Int) = _uiState.update { it.copy(startHour = hour) }
    fun updateStartMinute(minute: Int) = _uiState.update { it.copy(startMinute = minute) }
    fun updateEndHour(hour: Int) = _uiState.update { it.copy(endHour = hour) }
    fun updateEndMinute(minute: Int) = _uiState.update { it.copy(endMinute = minute) }
    fun updateTemperature(temp: Double) = _uiState.update { it.copy(targetTemperature = temp) }
    fun toggleEnabled() = _uiState.update { it.copy(enabled = !it.enabled) }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "El nombre es obligatorio") }
            return
        }
        if (state.selectedDays.isEmpty()) {
            _uiState.update { it.copy(error = "Selecciona al menos un día") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val homeId = authRepository.selectedHomeId.first() ?: ""
                val triggerData = JSONObject().apply {
                    put("days", org.json.JSONArray(state.selectedDays.sorted()))
                    put("startHour", state.startHour)
                    put("startMinute", state.startMinute)
                    put("endHour", state.endHour)
                    put("endMinute", state.endMinute)
                }.toString()
                val automation = AutomationEntity(
                    id = if (editingId == -1L) 0L else editingId,
                    name = state.name,
                    type = "SCHEDULE",
                    enabled = state.enabled,
                    homeId = homeId,
                    roomId = "",
                    targetTemperature = state.targetTemperature,
                    mode = "manual",
                    triggerData = triggerData
                )
                automationRepository.saveAutomation(automation)
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Error al guardar") }
            }
        }
    }
}
