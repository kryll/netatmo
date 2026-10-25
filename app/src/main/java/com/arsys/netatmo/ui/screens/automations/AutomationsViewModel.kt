package com.arsys.netatmo.ui.screens.automations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.entities.*
import com.arsys.netatmo.data.local.entities.AdvancedAutomationEntity
import com.arsys.netatmo.data.repository.AdvancedAutomationRepository
import com.arsys.netatmo.data.repository.AutomationRepository
import com.arsys.netatmo.domain.model.AutomationModel
import com.arsys.netatmo.domain.model.AutomationType
import com.arsys.netatmo.domain.model.ThermostatMode
import com.arsys.netatmo.service.GeofenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class AutomationsUiState(
    val automations: List<AutomationEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AutomationsViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val advancedAutomationRepository: AdvancedAutomationRepository,
    private val geofenceManager: GeofenceManager,
    private val zoneRepository: com.arsys.netatmo.data.repository.ZoneRepository
) : ViewModel() {

    // Advanced automations live in their own table; the list shows them as AutomationEntity rows
    // of type "ADVANCED" with a negative id (-advancedId) so they never clash with the others.
    val uiState: StateFlow<AutomationsUiState> = combine(
        automationRepository.getAllAutomations(),
        advancedAutomationRepository.getAllAdvancedAutomations()
    ) { regular, advanced ->
        AutomationsUiState(
            automations = (regular + advanced.map { it.toListItem() }).sortedByDescending { it.createdAt }
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutomationsUiState(isLoading = true))

    fun toggleAutomation(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            if (id < 0) {
                advancedAutomationRepository.getById(-id)
                    ?.let { advancedAutomationRepository.update(it.copy(enabled = enabled)) }
                zoneRepository.syncGeofences()
                return@launch
            }
            automationRepository.setAutomationEnabled(id, enabled)
            val automation = automationRepository.getAutomationById(id)
            if (automation?.type == "GEOFENCE") {
                val geofence = automationRepository.getGeofenceByAutomation(id) ?: return@launch
                try {
                    if (enabled) geofenceManager.registerGeofence(geofence)
                    else geofenceManager.removeGeofence(geofence.id)
                } catch (e: SecurityException) {
                    // Location permission not granted
                }
            }
        }
    }

    fun deleteAutomation(automation: AutomationEntity) {
        viewModelScope.launch {
            if (automation.type == "ADVANCED") {
                advancedAutomationRepository.getById(-automation.id)
                    ?.let { advancedAutomationRepository.delete(it) }
                zoneRepository.syncGeofences()
                return@launch
            }
            if (automation.type == "GEOFENCE") {
                automationRepository.getGeofenceByAutomation(automation.id)?.let {
                    geofenceManager.removeGeofence(it.id)
                }
            }
            automationRepository.deleteAutomation(automation)
        }
    }
}

private fun AdvancedAutomationEntity.toListItem(): AutomationEntity {
    val data = try {
        JSONObject()
            .put("triggers", JSONArray(triggersJson))
            .put("conditions", JSONArray(conditionsJson))
            .put("actions", JSONArray(actionsJson))
            .toString()
    } catch (e: Exception) {
        "{}"
    }
    return AutomationEntity(
        id = -id,
        name = name,
        type = "ADVANCED",
        enabled = enabled,
        homeId = "",
        roomId = "",
        targetTemperature = 0.0,
        mode = mode,
        triggerData = data,
        createdAt = createdAt,
        lastTriggeredAt = lastTriggeredAt
    )
}
