package com.arsys.netatmo.ui.screens.automations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.entities.*
import com.arsys.netatmo.data.repository.AutomationRepository
import com.arsys.netatmo.domain.model.AutomationModel
import com.arsys.netatmo.domain.model.AutomationType
import com.arsys.netatmo.domain.model.ThermostatMode
import com.arsys.netatmo.service.GeofenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AutomationsUiState(
    val automations: List<AutomationEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AutomationsViewModel @Inject constructor(
    private val automationRepository: AutomationRepository,
    private val geofenceManager: GeofenceManager
) : ViewModel() {

    val uiState: StateFlow<AutomationsUiState> = automationRepository.getAllAutomations()
        .map { AutomationsUiState(automations = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutomationsUiState(isLoading = true))

    fun toggleAutomation(id: Long, enabled: Boolean) {
        viewModelScope.launch {
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
            if (automation.type == "GEOFENCE") {
                automationRepository.getGeofenceByAutomation(automation.id)?.let {
                    geofenceManager.removeGeofence(it.id)
                }
            }
            automationRepository.deleteAutomation(automation)
        }
    }
}
