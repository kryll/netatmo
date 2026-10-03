package com.arsys.netatmo.ui.screens.automations

import android.content.Context
import android.provider.CalendarContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsys.netatmo.data.local.entities.AutomationEntity
import com.arsys.netatmo.data.local.entities.CalendarAutomationEntity
import com.arsys.netatmo.data.repository.AutomationRepository
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.domain.model.CalendarInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CalendarAutomationUiState(
    val name: String = "",
    val calendars: List<CalendarInfo> = emptyList(),
    val selectedCalendarId: Long? = null,
    val selectedCalendarName: String = "",
    val titleFilter: String = "",
    val exactMatch: Boolean = false,
    val availableEventTitles: List<String> = emptyList(),
    val isLoadingEvents: Boolean = false,
    val minutesBefore: Int = 30,
    val targetTemperature: Double = 21.0,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CalendarAutomationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val automationRepository: AutomationRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarAutomationUiState())
    val uiState: StateFlow<CalendarAutomationUiState> = _uiState.asStateFlow()

    private var editingAutomationId: Long = -1L
    private var editingCalendarEntityId: Long = 0L

    init {
        loadCalendars()
    }

    fun load(automationId: Long) {
        if (automationId == -1L) return
        editingAutomationId = automationId
        viewModelScope.launch {
            val automation = automationRepository.getAutomationById(automationId) ?: return@launch
            val calEntity = automationRepository.getCalendarAutomationByAutomationId(automationId)
            editingCalendarEntityId = calEntity?.id ?: 0L
            _uiState.update {
                it.copy(
                    name = automation.name,
                    targetTemperature = automation.targetTemperature,
                    selectedCalendarId = calEntity?.calendarId,
                    selectedCalendarName = calEntity?.calendarName ?: "",
                    titleFilter = calEntity?.eventTitleFilter ?: "",
                    exactMatch = calEntity?.eventTitleExactMatch ?: false,
                    minutesBefore = calEntity?.minutesBefore ?: 30
                )
            }
            calEntity?.calendarId?.let { loadEventsForCalendar(it) }
        }
    }

    private fun loadCalendars() {
        viewModelScope.launch {
            val calendars = withContext(Dispatchers.IO) {
                try {
                    getDeviceCalendars()
                } catch (e: SecurityException) {
                    emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
            _uiState.update { it.copy(calendars = calendars) }
        }
    }

    private fun getDeviceCalendars(): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                calendars.add(
                    CalendarInfo(
                        id = cursor.getLong(0),
                        name = cursor.getString(1) ?: "",
                        accountName = cursor.getString(2) ?: "",
                        color = cursor.getInt(3)
                    )
                )
            }
        }
        return calendars
    }

    private fun loadEventsForCalendar(calendarId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEvents = true) }
            val titles = withContext(Dispatchers.IO) {
                try {
                    getUpcomingEventTitles(calendarId)
                } catch (e: SecurityException) {
                    emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
            _uiState.update { it.copy(availableEventTitles = titles, isLoadingEvents = false) }
        }
    }

    private fun getUpcomingEventTitles(calendarId: Long): List<String> {
        val titles = mutableSetOf<String>()
        val now = System.currentTimeMillis()
        val thirtyDays = 30L * 24 * 60 * 60 * 1000
        val projection = arrayOf(CalendarContract.Events.TITLE)
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? " +
                "AND ${CalendarContract.Events.DTSTART} BETWEEN ? AND ?"
        val selectionArgs = arrayOf(calendarId.toString(), now.toString(), (now + thirtyDays).toString())
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection, selection, selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(0) ?: continue
                if (title.isNotBlank()) titles.add(title)
            }
        }
        return titles.toList()
    }

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }

    fun selectCalendar(calendar: CalendarInfo) {
        _uiState.update {
            it.copy(
                selectedCalendarId = calendar.id,
                selectedCalendarName = calendar.name,
                availableEventTitles = emptyList()
            )
        }
        loadEventsForCalendar(calendar.id)
    }

    fun updateTitleFilter(filter: String) = _uiState.update { it.copy(titleFilter = filter) }
    fun updateExactMatch(exact: Boolean) = _uiState.update { it.copy(exactMatch = exact) }
    fun updateMinutesBefore(mins: Int) = _uiState.update { it.copy(minutesBefore = mins) }
    fun updateTemperature(temp: Double) = _uiState.update { it.copy(targetTemperature = temp) }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "El nombre es obligatorio") }
            return
        }
        if (state.selectedCalendarId == null) {
            _uiState.update { it.copy(error = "Selecciona un calendario") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val homeId = authRepository.selectedHomeId.first() ?: ""
                val automation = AutomationEntity(
                    id = if (editingAutomationId == -1L) 0L else editingAutomationId,
                    name = state.name,
                    type = "CALENDAR",
                    homeId = homeId,
                    roomId = "",
                    targetTemperature = state.targetTemperature,
                    mode = "manual",
                    triggerData = "{}"
                )
                val automationId = automationRepository.saveAutomation(automation)
                automationRepository.saveCalendarAutomation(
                    CalendarAutomationEntity(
                        id = editingCalendarEntityId,
                        automationId = automationId,
                        calendarId = state.selectedCalendarId,
                        calendarName = state.selectedCalendarName,
                        eventTitleFilter = state.titleFilter.takeIf { it.isNotBlank() },
                        eventTitleExactMatch = state.exactMatch,
                        minutesBefore = state.minutesBefore,
                        targetTemperature = state.targetTemperature
                    )
                )
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}
