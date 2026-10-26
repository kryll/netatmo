package com.arsys.netatmo.ui.screens.automations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.entities.*
import com.arsys.netatmo.data.local.entities.AdvancedAutomationEntity
import com.arsys.netatmo.data.repository.AdvancedAutomationRepository
import com.arsys.netatmo.data.repository.AutomationRepository
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val zoneRepository: com.arsys.netatmo.data.repository.ZoneRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Once the migration ran, classic rows stay in their tables (recoverable) but the list
    // only shows their advanced equivalents, so nothing looks duplicated.
    private val classicsMigrated: Boolean
        get() = context.getSharedPreferences("classic_migration", Context.MODE_PRIVATE)
            .getInt("version", 0) >= 1

    // Advanced automations live in their own table; the list shows them as AutomationEntity rows
    // of type "ADVANCED" with a negative id (-advancedId) so they never clash with the others.
    val uiState: StateFlow<AutomationsUiState> = combine(
        automationRepository.getAllAutomations(),
        advancedAutomationRepository.getAllAdvancedAutomations()
    ) { regular, advanced ->
        // Migrated classics stay disabled and hidden (their advanced equivalent is shown);
        // a reverted classic is enabled again and reappears here.
        val visible = if (classicsMigrated) {
            regular.filter { it.type !in CLASSIC_MIGRATED_TYPES || it.enabled }
        } else {
            regular
        }
        AutomationsUiState(
            automations = (visible + advanced.map { it.toListItem() }).sortedByDescending { it.createdAt }
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
            automationRepository.deleteAutomation(automation)
        }
    }

    private companion object {
        val CLASSIC_MIGRATED_TYPES = setOf("GEOFENCE", "SCHEDULE", "CALENDAR")
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
