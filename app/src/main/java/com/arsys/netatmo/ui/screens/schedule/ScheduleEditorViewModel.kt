package com.arsys.netatmo.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.api.models.Room
import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ZoneUi(
    val id: Int,
    val name: String,
    val temperature: Double,
    val roomTemps: Map<String, Double> = emptyMap()
)

data class SlotUi(val dayOfWeek: Int, val minuteOfDay: Int, val zoneId: Int)

data class ScheduleEditorUiState(
    val scheduleId: String? = null,
    val name: String = "Nueva programación",
    val zones: List<ZoneUi> = DEFAULT_ZONES,
    val slots: List<SlotUi> = buildDefaultSlots(),
    val rooms: List<Room> = emptyList(),
    val selectedDay: Int = 0,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

private val DEFAULT_ZONES = listOf(
    ZoneUi(0, "Noche", 16.0),
    ZoneUi(1, "Mañana", 19.0),
    ZoneUi(2, "Eco", 16.0),
    ZoneUi(3, "Confort", 21.0)
)

private fun buildDefaultSlots(): List<SlotUi> {
    val slots = mutableListOf<SlotUi>()
    for (day in 0..6) {
        slots += SlotUi(day, 0, 0)
        slots += SlotUi(day, 7 * 60, 1)
        slots += SlotUi(day, 9 * 60, 2)
        slots += SlotUi(day, 23 * 60, 3)
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
        viewModelScope.launch {
            val homeId = authRepository.selectedHomeId.first() ?: return@launch
            val result = thermostatRepository.getHomesData()
            if (result is ApiResult.Success) {
                val home = result.data.find { it.id == homeId } ?: return@launch
                val rooms = home.rooms ?: emptyList()

                if (scheduleId == null || scheduleId == "new") {
                    val zonesWithRooms = DEFAULT_ZONES.map { zone ->
                        zone.copy(roomTemps = rooms.associate { it.id to zone.temperature })
                    }
                    _uiState.update { it.copy(rooms = rooms, zones = zonesWithRooms) }
                    return@launch
                }

                val schedule = home.schedules?.find { it.id == scheduleId } ?: return@launch
                val zones = schedule.zones?.map { z ->
                    val roomTemps = z.rooms?.associate { zr -> zr.id to (zr.temperature ?: 20.0) } ?: emptyMap()
                    val defaultTemp = roomTemps.values.firstOrNull() ?: 20.0
                    ZoneUi(z.id, z.name ?: "Zona ${z.id + 1}", defaultTemp, roomTemps)
                } ?: DEFAULT_ZONES
                val slots = schedule.timetable?.map { slot ->
                    SlotUi(
                        dayOfWeek = slot.minuteOffset / (24 * 60),
                        minuteOfDay = slot.minuteOffset % (24 * 60),
                        zoneId = slot.zoneId
                    )
                } ?: buildDefaultSlots()
                _uiState.update {
                    it.copy(
                        scheduleId = scheduleId,
                        name = schedule.name,
                        zones = zones,
                        slots = slots,
                        rooms = rooms
                    )
                }
            }
        }
    }

    fun selectDay(day: Int) = _uiState.update { it.copy(selectedDay = day) }

    fun setName(name: String) = _uiState.update { it.copy(name = name) }

    fun updateZone(zone: ZoneUi) = _uiState.update {
        it.copy(zones = it.zones.map { z -> if (z.id == zone.id) zone else z })
    }

    fun updateZoneRoomTemp(zoneId: Int, roomId: String, temp: Double) = _uiState.update {
        it.copy(zones = it.zones.map { z ->
            if (z.id == zoneId) z.copy(roomTemps = z.roomTemps + (roomId to temp))
            else z
        })
    }

    fun addZone() = _uiState.update {
        val newId = (it.zones.maxOfOrNull { z -> z.id } ?: -1) + 1
        val defaultTemp = 19.0
        val newZone = ZoneUi(
            newId, "Zona ${newId + 1}", defaultTemp,
            it.rooms.associate { room -> room.id to defaultTemp }
        )
        it.copy(zones = it.zones + newZone)
    }

    fun removeZone(zoneId: Int) = _uiState.update {
        if (it.zones.size <= 1) return@update it
        val firstOtherId = it.zones.first { z -> z.id != zoneId }.id
        it.copy(
            zones = it.zones.filter { z -> z.id != zoneId },
            slots = it.slots.map { s -> if (s.zoneId == zoneId) s.copy(zoneId = firstOtherId) else s }
        )
    }

    fun addSlot(dayOfWeek: Int, minuteOfDay: Int, zoneId: Int) = _uiState.update {
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

    fun setSlotZone(dayOfWeek: Int, minuteOfDay: Int, zoneId: Int) = _uiState.update {
        it.copy(slots = it.slots.map { s ->
            if (s.dayOfWeek == dayOfWeek && s.minuteOfDay == minuteOfDay) s.copy(zoneId = zoneId)
            else s
        })
    }

    fun save() {
        viewModelScope.launch {
            val homeId = authRepository.selectedHomeId.first() ?: return@launch
            val state = _uiState.value
            _uiState.update { it.copy(isSaving = true, error = null) }

            val zonesPayload = state.zones.map { z ->
                val roomsPayload = if (z.roomTemps.isNotEmpty()) {
                    z.roomTemps.map { (roomId, temp) ->
                        mapOf("id" to roomId, "therm_setpoint_temperature" to temp)
                    }
                } else {
                    state.rooms.map { room ->
                        mapOf("id" to room.id, "therm_setpoint_temperature" to z.temperature)
                    }
                }
                mapOf("id" to z.id, "name" to z.name, "rooms" to roomsPayload, "type" to 0)
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
                thermostatRepository.syncSchedule(homeId, body + ("schedule_id" to state.scheduleId))
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
