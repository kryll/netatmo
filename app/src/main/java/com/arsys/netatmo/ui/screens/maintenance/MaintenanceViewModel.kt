package com.arsys.netatmo.ui.screens.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.dao.MaintenanceDao
import com.arsys.netatmo.data.local.entities.MaintenanceRecordEntity
import com.arsys.netatmo.domain.model.MaintenanceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    private val dao: MaintenanceDao
) : ViewModel() {

    val records = dao.getAll().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _selectedRecord = MutableStateFlow<MaintenanceRecordEntity?>(null)
    val selectedRecord = _selectedRecord.asStateFlow()

    fun selectRecord(id: Long) {
        viewModelScope.launch {
            _selectedRecord.value = dao.getById(id)
        }
    }

    fun addRecord(type: MaintenanceType, description: String, techName: String, certRef: String) {
        viewModelScope.launch {
            dao.insert(MaintenanceRecordEntity(
                date = System.currentTimeMillis(),
                type = type.name,
                description = description,
                technicianName = techName,
                certificateRef = certRef
            ))
        }
    }

    fun deleteRecord(record: MaintenanceRecordEntity) {
        viewModelScope.launch { dao.delete(record) }
    }
}
