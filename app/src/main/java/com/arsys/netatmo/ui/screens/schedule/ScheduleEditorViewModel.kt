package com.arsys.netatmo.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ZoneUi(val id: Int, val name: String, val temperature: Double)
data class SlotUi(val dayOfWeek: Int, val minuteOfDay: Int, val zoneId: Int)

data class ScheduleEditorUiState(
    val scheduleId: String? = null,   // null = new
    val name: String = "Nueva programación",
    val zones: List<ZoneUi> = listOf(
        ZoneUi(0, "Confort", 21.0),
        ZoneUi(1, "Eco", 18.0),
        ZoneUi(2, "Ausente", 16.0)
    ),
    val slots: List<SlotUi> = buildDefaultSlots(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

private fun buildDefaultSlots(): List<SlotUi> {
    val slots = mutableListOf<SlotUi>()
    for (day in 0..6) {
        slots += SlotUi(day, 7 * 60, 0)   // 07:00 Confort
        slots += SlotUi(day, 23 * 60, 1)  // 23:00 Eco
    }
    return slots
}

@HiltViewModel
class ScheduleEditorViewModel @Inject constructor(
    private val thermostatRepository: ThermostatRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleEditorUiState())
    val uiState: StateFlow<ScheduleEditorUiState> = _uiState.asStateFlow()

    fun loadSchedule(scheduleId: String?) {
        if (scheduleId == null || scheduleId == "new") return
        viewModelScope.launch {
            val homeId = authRepository.selectedHomeId.first() ?: return@launch
            val result = thermostatRepository.getHomesData()
            if (result is ApiResult.Success) {
                val home = result.data.find { it.id == homeId } ?: return@launch
                val schedule = home.schedules?.find { it.id == scheduleId } ?: return@launch
                // Zone temp is stored per-room inside each zone; take the first room's temp or default
                val zones = schedule.zones?.mapIndexed { idx, z ->
                    val temp = z.rooms?.firstOrNull()?.temperature ?: when (idx) {
                        0 -> 21.0
                        1 -> 18.0
                        else -> 16.0
                    }
                    ZoneUi(z.id, z.name ?: "Zona ${z.id + 1}", temp)
                } ?: _uiState.value.zones
                val slots = schedule.timetable?.map { slot ->
                    SlotUi(
                        dayOfWeek = slot.minuteOffset / (24 * 60),
                        minuteOfDay = slot.minuteOffset % (24 * 60),
                        zoneId = slot.zoneId
                    )
                } ?: _uiState.value.slots
                _uiState.update {
                    it.copy(
                        scheduleId = scheduleId,
                        name = schedule.name,
                        zones = zones,
                        slots = slots
                    )
                }
            }
        }
    }

    fun setName(name: String) = _uiState.update { it.copy(name = name) }

    fun updateZone(zone: ZoneUi) = _uiState.update {
        it.copy(zones = it.zones.map { z -> if (z.id == zone.id) zone else z })
    }

    fun addZone() = _uiState.update {
        val newId = (it.zones.maxOfOrNull { z -> z.id } ?: -1) + 1
        it.copy(zones = it.zones + ZoneUi(newId, "Zona ${newId + 1}", 19.0))
    }

    fun removeZone(zoneId: Int) = _uiState.update {
        if (it.zones.size <= 1) return@update it
        val firstOtherId = it.zones.first { z -> z.id != zoneId }.id
        it.copy(
            zones = it.zones.filter { z -> z.id != zoneId },
            slots = it.slots.map { s -> if (s.zoneId == zoneId) s.copy(zoneId = firstOtherId) else s }
        )
    }

    fun setSlotZone(dayOfWeek: Int, minuteOfDay: Int, zoneId: Int) = _uiState.update {
        it.copy(slots = it.slots.map { s ->
            if (s.dayOfWeek == dayOfWeek && s.minuteOfDay == minuteOfDay) s.copy(zoneId = zoneId) else s
        })
    }

    fun addSlot(dayOfWeek: Int, minuteOfDay: Int) = _uiState.update {
        val zoneId = it.zones.firstOrNull()?.id ?: 0
        it.copy(
            slots = (it.slots + SlotUi(dayOfWeek, minuteOfDay, zoneId)).sortedWith(
                compareBy({ s -> s.dayOfWeek }, { s -> s.minuteOfDay })
            )
        )
    }

    fun removeSlot(dayOfWeek: Int, minuteOfDay: Int) = _uiState.update {
        it.copy(slots = it.slots.filter { s ->
            !(s.dayOfWeek == dayOfWeek && s.minuteOfDay == minuteOfDay)
        })
    }

    fun save() {
        viewModelScope.launch {
            val homeId = authRepository.selectedHomeId.first() ?: return@launch
            val state = _uiState.value
            _uiState.update { it.copy(isSaving = true, error = null) }

            val zonesPayload = state.zones.mapIndexed { idx, z ->
                mapOf("id" to idx, "name" to z.name, "temp" to z.temperature, "type" to 0)
            }
            val timetablePayload = state.slots.map { s ->
                mapOf("m_offset" to s.dayOfWeek * 24 * 60 + s.minuteOfDay, "zone_id" to s.zoneId)
            }

            val body: Map<String, Any> = mapOf(
                "home_id" to homeId,
                "name" to state.name,
                "zones" to zonesPayload,
                "timetable" to timetablePayload
            )

            val result = if (state.scheduleId != null) {
                val bodyWithId = body + ("schedule_id" to state.scheduleId)
                thermostatRepository.syncSchedule(homeId, bodyWithId)
            } else {
                thermostatRepository.createSchedule(homeId, body)
            }

            when (result) {
                is ApiResult.Success -> _uiState.update { it.copy(isSaving = false, saved = true) }
                is ApiResult.Error -> _uiState.update { it.copy(isSaving = false, error = result.message) }
                ApiResult.Loading -> {}
            }
        }
    }
}
