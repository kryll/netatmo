package com.arsys.netatmo.ui.screens.automations.advanced

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.entities.AdvancedAutomationEntity
import com.arsys.netatmo.data.repository.AdvancedAutomationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class AdvancedAutomationUiState(
    val name: String = "",
    val enabled: Boolean = true,
    val triggers: List<Map<String, Any>> = emptyList(),
    val conditions: List<Map<String, Any>> = emptyList(),
    val actions: List<Map<String, Any>> = emptyList(),
    val triggerMode: String = "all",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false
)

@HiltViewModel
class AdvancedAutomationViewModel @Inject constructor(
    private val repository: AdvancedAutomationRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val automationId: Long = savedStateHandle["automationId"] ?: -1L

    private val _uiState = MutableStateFlow(AdvancedAutomationUiState())
    val uiState: StateFlow<AdvancedAutomationUiState> = _uiState.asStateFlow()

    init {
        if (automationId != -1L) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                val entity = repository.getById(automationId)
                if (entity != null) {
                    _uiState.update {
                        it.copy(
                            name = entity.name,
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

    fun removeTrigger(index: Int) {
        _uiState.update {
            it.copy(triggers = it.triggers.toMutableList().also { list -> list.removeAt(index) })
        }
    }

    fun addCondition(condition: Map<String, Any>) {
        _uiState.update { it.copy(conditions = it.conditions + condition) }
    }

    fun removeCondition(index: Int) {
        _uiState.update {
            it.copy(conditions = it.conditions.toMutableList().also { list -> list.removeAt(index) })
        }
    }

    fun addAction(action: Map<String, Any>) {
        _uiState.update { it.copy(actions = it.actions + action) }
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

    fun save(
        name: String,
        triggers: List<Map<String, Any>>,
        conditions: List<Map<String, Any>>,
        actions: List<Map<String, Any>>,
        triggerMode: String,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            val entity = AdvancedAutomationEntity(
                id = if (automationId == -1L) 0 else automationId,
                name = name,
                enabled = enabled,
                triggersJson = mapsToJsonArray(triggers),
                conditionsJson = mapsToJsonArray(conditions),
                actionsJson = mapsToJsonArray(actions),
                triggerMode = triggerMode
            )
            repository.save(entity)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun delete() {
        if (automationId == -1L) return
        viewModelScope.launch {
            val entity = repository.getById(automationId) ?: return@launch
            repository.delete(entity)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    private fun mapsToJsonArray(list: List<Map<String, Any>>): String {
        val array = JSONArray()
        for (map in list) {
            val obj = JSONObject()
            for ((key, value) in map) {
                obj.put(key, value)
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
