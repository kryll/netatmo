package com.arsys.netatmo.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.entities.FamilyMemberEntity
import com.arsys.netatmo.service.FamilyPresenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FamilyUiState(
    val members: List<FamilyMemberEntity> = emptyList(),
    val showAddDialog: Boolean = false,
    val newName: String = ""
)

@HiltViewModel
class FamilyManagementViewModel @Inject constructor(
    private val manager: FamilyPresenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FamilyUiState())
    val uiState: StateFlow<FamilyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            manager.getAllMembers().collect { members ->
                _uiState.update { it.copy(members = members) }
            }
        }
    }

    fun togglePresence(id: Long, isHome: Boolean) {
        viewModelScope.launch {
            manager.setPresence(id, isHome)
        }
    }

    fun deleteMember(member: FamilyMemberEntity) {
        viewModelScope.launch {
            manager.deleteMember(member)
        }
    }

    fun addMember() {
        val name = _uiState.value.newName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            manager.addMember(FamilyMemberEntity(name = name))
            _uiState.update { it.copy(showAddDialog = false, newName = "") }
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, newName = "") }
    }

    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false, newName = "") }
    }

    fun updateNewName(name: String) {
        _uiState.update { it.copy(newName = name) }
    }
}
