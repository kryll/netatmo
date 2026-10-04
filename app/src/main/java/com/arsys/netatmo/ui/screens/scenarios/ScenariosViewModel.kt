package com.arsys.netatmo.ui.screens.scenarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.entities.ScenarioEntity
import com.arsys.netatmo.data.repository.AutomationRepository
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import com.arsys.netatmo.domain.model.ScenarioAction
import com.arsys.netatmo.domain.model.ThermostatMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScenariosUiState(
    val scenarios: List<ScenarioEntity> = emptyList(),
    val runningScenarioId: Long? = null,
    val successMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class ScenariosViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val thermostatRepository: ThermostatRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val gson = Gson()
    private val _uiState = MutableStateFlow(ScenariosUiState())
    val uiState: StateFlow<ScenariosUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            automationRepository.getAllScenarios().collect { scenarios ->
                _uiState.update { it.copy(scenarios = scenarios) }
            }
        }
    }

    fun runScenario(scenario: ScenarioEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(runningScenarioId = scenario.id) }
            val homeId = authRepository.selectedHomeId.first() ?: return@launch
            try {
                val actions: List<ScenarioAction> = gson.fromJson(
                    scenario.actionsJson,
                    object : TypeToken<List<ScenarioAction>>() {}.type
                ) ?: emptyList()

                actions.forEach { action ->
                    thermostatRepository.setTemperature(homeId, action.roomId, action.temperature)
                }
                _uiState.update {
                    it.copy(runningScenarioId = null, successMessage = "Escenario \"${scenario.name}\" aplicado")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(runningScenarioId = null, error = e.message) }
            }
        }
    }

    fun executeQuickAction(action: String) {
        viewModelScope.launch {
            val homeId = authRepository.selectedHomeId.first() ?: return@launch
            val (mode, label) = when (action) {
                "comfort" -> ThermostatMode.MANUAL to "Modo confort activado"
                "eco"     -> ThermostatMode.SCHEDULE to "Modo eco activado"
                "away"    -> ThermostatMode.AWAY to "Modo ausente activado"
                "off"     -> ThermostatMode.OFF to "Calefacción apagada"
                else      -> return@launch
            }
            thermostatRepository.setHomeMode(homeId, mode.apiValue)
            _uiState.update { it.copy(successMessage = label) }
        }
    }

    fun deleteScenario(scenario: ScenarioEntity) {
        viewModelScope.launch {
            automationRepository.deleteScenario(scenario)
        }
    }
}
