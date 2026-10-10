package com.arsys.netatmo.ui.screens.maintenance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.dao.MaintenanceDao
import com.arsys.netatmo.data.local.entities.MaintenanceRecordEntity
import com.arsys.netatmo.domain.model.MaintenanceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MaintenanceFilter(val label: String) {
    ALL("Todos"),
    ANNUAL("Revisiones SAT"),
    PURGE_PRESSURE("Purgas y Presión"),
    CERTIFICATE("Con Certificado")
}

data class MaintenanceUiState(
    val records: List<MaintenanceRecordEntity> = emptyList(),
    val isLoading: Boolean = true,
    val activeFilter: MaintenanceFilter = MaintenanceFilter.ALL,
    val sentIds: Set<Long> = emptySet()
) {
    val filteredRecords: List<MaintenanceRecordEntity>
        get() = when (activeFilter) {
            MaintenanceFilter.ALL -> records
            MaintenanceFilter.ANNUAL -> records.filter { it.type == MaintenanceType.ANNUAL_REVISION.name }
            MaintenanceFilter.PURGE_PRESSURE -> records.filter {
                it.type == MaintenanceType.PURGE.name || it.type == MaintenanceType.PRESSURE_CHECK.name
            }
            MaintenanceFilter.CERTIFICATE -> records.filter { it.certificateRef.isNotBlank() }
        }

    val totalCount: Int get() = records.size
    val certCount: Int get() = records.count { it.certificateRef.isNotBlank() }
    val annualCount: Int get() = records.count { it.type == MaintenanceType.ANNUAL_REVISION.name }
    val purgeCount: Int get() = records.count {
        it.type == MaintenanceType.PURGE.name || it.type == MaintenanceType.PRESSURE_CHECK.name
    }
}

@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    private val dao: MaintenanceDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(MaintenanceUiState())
    val uiState: StateFlow<MaintenanceUiState> = _uiState.asStateFlow()

    // Keep for CertificateDetailScreen
    private val _selectedRecord = MutableStateFlow<MaintenanceRecordEntity?>(null)
    val selectedRecord = _selectedRecord.asStateFlow()

    init {
        viewModelScope.launch {
            dao.getAll().collect { list ->
                _uiState.update { it.copy(records = list, isLoading = false) }
            }
        }
    }

    fun selectRecord(id: Long) {
        viewModelScope.launch {
            _selectedRecord.value = dao.getById(id)
        }
    }

    fun setFilter(filter: MaintenanceFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun addRecord(
        type: MaintenanceType,
        description: String,
        techName: String = "",
        certRef: String = "",
        warrantyUntil: Long? = null
    ) {
        viewModelScope.launch {
            dao.insert(
                MaintenanceRecordEntity(
                    date = System.currentTimeMillis(),
                    type = type.name,
                    description = description,
                    technicianName = techName,
                    certificateRef = certRef,
                    warrantyExtendedUntil = warrantyUntil
                )
            )
        }
    }

    fun deleteRecord(record: MaintenanceRecordEntity) {
        viewModelScope.launch { dao.delete(record) }
    }

    fun markAsSent(id: Long) {
        _uiState.update { it.copy(sentIds = it.sentIds + id) }
    }

    fun isSent(id: Long): Boolean = _uiState.value.sentIds.contains(id)

    // Expose stateflow of records for backward compat
    val records: StateFlow<List<MaintenanceRecordEntity>> = _uiState
        .map { it.records }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
