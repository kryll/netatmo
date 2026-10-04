package com.arsys.netatmo.ui.screens.automations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.entities.AutomationEntity
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AutomationRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OutdoorTempUiState(
    val name: String = "",
    val condition: String = "below",
    val thresholdTemp: Double = 5.0,
    val targetTemp: Double = 21.0,
    val mode: String = "manual",
    val enabled: Boolean = true,
    val outdoorTemp: Double? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false
)

private data class OutdoorTempTriggerData(
    val condition: String,
    val thresholdTemp: Double,
    val targetHomeId: String,
    val targetRoomId: String,
    val targetTemp: Double,
    val mode: String
)

@HiltViewModel
class OutdoorTempAutomationViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val thermostatRepository: ThermostatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val gson = Gson()
    private val automationId: Long = savedStateHandle["automationId"] ?: -1L

    private val _uiState = MutableStateFlow(OutdoorTempUiState())
    val uiState: StateFlow<OutdoorTempUiState> = _uiState.asStateFlow()

    init {
        if (automationId != -1L) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                val automation = automationRepository.getAutomationById(automationId)
                if (automation != null) {
                    val triggerData = try {
                        gson.fromJson(automation.triggerData, OutdoorTempTriggerData::class.java)
                    } catch (e: Exception) {
                        null
                    }
                    _uiState.update {
                        it.copy(
                            name = automation.name,
                            condition = triggerData?.condition ?: "below",
                            thresholdTemp = triggerData?.thresholdTemp ?: 5.0,
                            targetTemp = triggerData?.targetTemp ?: automation.targetTemperature,
                            mode = triggerData?.mode ?: automation.mode,
                            enabled = automation.enabled,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
        loadOutdoorTemp()
    }

    private fun loadOutdoorTemp() {
        viewModelScope.launch {
            try {
                val result = thermostatRepository.getHomesData()
                if (result is ApiResult.Success) {
                    val outdoorModule = result.data
                        .flatMap { home -> home.modules ?: emptyList() }
                        .firstOrNull { module -> module.type == "NAModule1" }
                    if (outdoorModule != null) {
                        _uiState.update { it.copy(outdoorTemp = null) }
                    }
                }
            } catch (e: Exception) {
                // Outdoor temperature loading is non-critical; state remains unchanged.
            }
        }
    }

    fun save(
        name: String,
        condition: String,
        thresholdTemp: Double,
        targetTemp: Double,
        mode: String,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            val triggerData = gson.toJson(
                OutdoorTempTriggerData(
                    condition = condition,
                    thresholdTemp = thresholdTemp,
                    targetHomeId = "",
                    targetRoomId = "",
                    targetTemp = targetTemp,
                    mode = mode
                )
            )
            val entity = AutomationEntity(
                id = if (automationId == -1L) 0L else automationId,
                name = name,
                type = "OUTDOOR_TEMP",
                enabled = enabled,
                homeId = "",
                roomId = "",
                targetTemperature = targetTemp,
                mode = mode,
                triggerData = triggerData
            )
            automationRepository.saveAutomation(entity)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            val automation = automationRepository.getAutomationById(automationId) ?: return@launch
            automationRepository.deleteAutomation(automation)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }
}
