package com.arsys.netatmo.ui.screens.automations.advanced

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.entities.AdvancedAutomationEntity
import com.arsys.netatmo.data.repository.AdvancedAutomationRepository
import com.arsys.netatmo.service.AdvancedAutomationEngine
import com.arsys.netatmo.service.AdvancedAutomationWorker
import com.arsys.netatmo.service.migrateDatetimeConsumptionLocked
import com.arsys.netatmo.service.saveAutomationStateMutex
import kotlinx.coroutines.sync.withLock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class AdvancedAutomationUiState(
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val triggers: List<Map<String, Any>> = emptyList(),
    val conditions: List<Map<String, Any>> = emptyList(),
    val actions: List<Map<String, Any>> = emptyList(),
    val triggerMode: String = "all",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val testReport: String? = null,
    val isTesting: Boolean = false
)

@HiltViewModel
class AdvancedAutomationViewModel @Inject constructor(
    private val repository: AdvancedAutomationRepository,
    private val engine: AdvancedAutomationEngine,
    private val classicAutomationMigrator: com.arsys.netatmo.service.ClassicAutomationMigrator,
    private val zoneRepository: com.arsys.netatmo.data.repository.ZoneRepository,
    private val automationRepository: com.arsys.netatmo.data.repository.AutomationRepository,
    private val alarmScheduler: com.arsys.netatmo.service.AutomationAlarmScheduler,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val automationId: Long = savedStateHandle["automationId"] ?: -1L

    private val _uiState = MutableStateFlow(AdvancedAutomationUiState())
    val uiState: StateFlow<AdvancedAutomationUiState> = _uiState.asStateFlow()

    val zones: StateFlow<List<com.arsys.netatmo.data.local.entities.ZoneEntity>> =
        zoneRepository.observeZones().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scenarios: StateFlow<List<com.arsys.netatmo.data.local.entities.ScenarioEntity>> =
        automationRepository.getAllScenarios().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (automationId != -1L) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                val entity = repository.getById(automationId)
                if (entity != null) {
                    _uiState.update {
                        it.copy(
                            name = entity.name,
                            description = entity.description,
                            enabled = entity.enabled,
                            triggers = jsonArrayToMaps(entity.triggersJson),
                            conditions = jsonArrayToMaps(entity.conditionsJson),
                            actions = jsonArrayToMaps(entity.actionsJson),
                            triggerMode = entity.triggerMode,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun addTrigger(trigger: Map<String, Any>) {
        _uiState.update { it.copy(triggers = it.triggers + trigger) }
    }

    fun updateTrigger(index: Int, trigger: Map<String, Any>) {
        _uiState.update {
            if (index !in it.triggers.indices) it
            else it.copy(triggers = it.triggers.toMutableList().also { list -> list[index] = trigger })
        }
    }

    fun removeTrigger(index: Int) {
        _uiState.update {
            it.copy(triggers = it.triggers.toMutableList().also { list -> list.removeAt(index) })
        }
    }

    fun addCondition(condition: Map<String, Any>) {
        _uiState.update { it.copy(conditions = it.conditions + condition) }
    }

    fun updateCondition(index: Int, condition: Map<String, Any>) {
        _uiState.update {
            if (index !in it.conditions.indices) it
            else it.copy(conditions = it.conditions.toMutableList().also { list -> list[index] = condition })
        }
    }

    fun removeCondition(index: Int) {
        _uiState.update {
            it.copy(conditions = it.conditions.toMutableList().also { list -> list.removeAt(index) })
        }
    }

    fun addAction(action: Map<String, Any>) {
        _uiState.update { it.copy(actions = it.actions + action) }
    }

    fun updateAction(index: Int, action: Map<String, Any>) {
        _uiState.update {
            if (index !in it.actions.indices) it
            else it.copy(actions = it.actions.toMutableList().also { list -> list[index] = action })
        }
    }

    fun removeAction(index: Int) {
        _uiState.update {
            it.copy(actions = it.actions.toMutableList().also { list -> list.removeAt(index) })
        }
    }

    fun setTriggerMode(mode: String) {
        _uiState.update { it.copy(triggerMode = mode) }
    }

    fun setEnabled(enabled: Boolean) {
        _uiState.update { it.copy(enabled = enabled) }
    }

    fun setName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    /** Reverts this migrated automation to its classic original (see ClassicAutomationMigrator). */
    fun revertMigration() {
        viewModelScope.launch {
            val entity = repository.getById(automationId) ?: return@launch
            if (classicAutomationMigrator.rollbackFor(entity)) {
                _uiState.update { it.copy(isDeleted = true) }
            }
        }
    }

    fun save(
        name: String,
        triggers: List<Map<String, Any>>,
        conditions: List<Map<String, Any>>,
        actions: List<Map<String, Any>>,
        triggerMode: String,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            // Editing keeps createdAt and the last-run info of the stored automation.
            val existing = if (automationId == -1L) null else repository.getById(automationId)
            val entity = (existing ?: AdvancedAutomationEntity(name = name)).copy(
                name = name.trim().ifBlank { "Automatización avanzada" },
                enabled = enabled,
                triggersJson = mapsToJsonArray(triggers),
                conditionsJson = mapsToJsonArray(conditions),
                actionsJson = mapsToJsonArray(actions),
                triggerMode = triggerMode
            )
            // Inside the short shared section: migrate the legacy marks against the STORED
            // trigger list BEFORE overwriting it, so the positional marks are always
            // translated against the structure they were written for, even when this save
            // adds, deletes or reorders triggers (see DatetimeConsumptionMigration).
            saveAutomationStateMutex.withLock {
                if (existing != null) migrateDatetimeConsumptionLocked(context, repository, entity.id)
                repository.save(entity)
            }
            zoneRepository.syncGeofences()
            alarmScheduler.reschedule()
            AdvancedAutomationWorker.runNow(context)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    /** Runs the automation as currently edited (saved or not) and shows what it read and did. */
    fun testNow(
        name: String,
        triggers: List<Map<String, Any>>,
        conditions: List<Map<String, Any>>,
        actions: List<Map<String, Any>>,
        triggerMode: String
    ) {
        if (_uiState.value.isTesting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testReport = null) }
            val entity = AdvancedAutomationEntity(
                id = if (automationId == -1L) 0 else automationId,
                name = name.ifBlank { "Automatización avanzada" },
                triggersJson = mapsToJsonArray(triggers),
                conditionsJson = mapsToJsonArray(conditions),
                actionsJson = mapsToJsonArray(actions),
                triggerMode = triggerMode
            )
            val report = try {
                engine.testRun(entity)
            } catch (e: Exception) {
                "ERROR: ${e.message ?: e.javaClass.simpleName}"
            }
            _uiState.update { it.copy(isTesting = false, testReport = report) }
        }
    }

    fun dismissTestReport() {
        _uiState.update { it.copy(testReport = null) }
    }

    fun delete() {
        if (automationId == -1L) return
        viewModelScope.launch {
            val entity = repository.getById(automationId) ?: return@launch
            repository.delete(entity)
            zoneRepository.syncGeofences()
            alarmScheduler.reschedule()
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    private fun mapsToJsonArray(list: List<Map<String, Any>>): String {
        val array = JSONArray()
        for (map in list) {
            val obj = JSONObject()
            for ((key, value) in map) {
                obj.put(key, JSONObject.wrap(value))
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun jsonArrayToMaps(json: String): List<Map<String, Any>> {
        val result = mutableListOf<Map<String, Any>>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val map = mutableMapOf<String, Any>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.get(key)
            }
            result.add(map)
        }
        return result
    }
}
