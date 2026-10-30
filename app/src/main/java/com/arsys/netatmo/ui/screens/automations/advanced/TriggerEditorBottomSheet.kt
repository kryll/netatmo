package com.arsys.netatmo.ui.screens.automations.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arsys.netatmo.data.local.entities.ZoneEntity
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainerLow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerEditorBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (Map<String, Any>) -> Unit,
    zones: List<ZoneEntity> = emptyList(),
    onManageZones: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by remember { mutableStateOf("time") }

    // Time
    var time by remember { mutableStateOf("07:00") }
    val days = remember { mutableStateListOf(1, 2, 3, 4, 5) }

    // Numeric state
    var numericEntity by remember { mutableStateOf("outdoor_temp") }
    var numericCondition by remember { mutableStateOf("below") }
    var threshold by remember { mutableFloatStateOf(5f) }

    // Sun
    var sunEvent by remember { mutableStateOf("sunset") }
    var sunOffset by remember { mutableStateOf("0") }

    // Geofence
    var geofenceEvent by remember { mutableStateOf("enter") }
    var geofenceZoneId by remember { mutableStateOf("") }

    // Time window (franja)
    var windowStart by remember { mutableStateOf("22:00") }
    var windowEnd by remember { mutableStateOf("07:00") }

    // Date & time (one-shot)
    var dateTimeDate by remember { mutableStateOf("") } // AAAA-MM-DD
    var dateTimeTime by remember { mutableStateOf("12:00") }

    // Calendar
    var calendars by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var calendarId by remember { mutableStateOf(-1L) }
    var calendarTitle by remember { mutableStateOf("") }
    var calendarMinutesBefore by remember { mutableStateOf("30") }
    var calendarPermissionDenied by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val calendarPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) calendars = queryDeviceCalendars(context) else calendarPermissionDenied = true
    }
    androidx.compose.runtime.LaunchedEffect(selectedType) {
        if (selectedType == "calendar" && calendars.isEmpty() &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            calendars = queryDeviceCalendars(context)
        }
    }

    val triggerTypes = listOf(
        "time" to "Hora",
        "time_window" to "Franja",
        "numeric_state" to "Temperatura",
        "geofence" to "Zona",
        "calendar" to "Calendario",
        "datetime" to "Fecha"
    )

    val dayLabels = listOf(1 to "L", 2 to "M", 3 to "X", 4 to "J", 5 to "V", 6 to "S", 7 to "D")

    val primary = MaterialTheme.colorScheme.primary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        properties = ModalBottomSheetDefaults.properties
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = "Añadir disparador",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Type selector
            SheetLabel("Tipo de disparador")
            WrappingFilterChips(
                items = triggerTypes,
                selected = selectedType,
                accentColor = primary,
                onSelect = { selectedType = it }
            )

            // Type-specific fields
            when (selectedType) {
                "time" -> {
                    SheetLabel("Hora (HH:MM)")
                    SheetTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = "Hora"
                    )
                    SheetLabel("Días de la semana")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        dayLabels.forEach { (dayNum, dayLabel) ->
                            FilterChip(
                                selected = days.contains(dayNum),
                                onClick = {
                                    if (days.contains(dayNum)) days.remove(dayNum)
                                    else days.add(dayNum)
                                },
                                label = { Text(dayLabel, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primary.copy(alpha = 0.15f),
                                    selectedLabelColor = primary,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = days.contains(dayNum),
                                    selectedBorderColor = primary.copy(alpha = 0.5f),
                                    borderColor = OutlineVariant
                                )
                            )
                        }
                    }
                }

                "time_window" -> {
                    SheetLabel("Días de la semana")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        dayLabels.forEach { (dayNum, dayLabel) ->
                            FilterChip(
                                selected = days.contains(dayNum),
                                onClick = {
                                    if (days.contains(dayNum)) days.remove(dayNum)
                                    else days.add(dayNum)
                                },
                                label = { Text(dayLabel, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primary.copy(alpha = 0.15f),
                                    selectedLabelColor = primary,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = days.contains(dayNum),
                                    selectedBorderColor = primary.copy(alpha = 0.5f),
                                    borderColor = OutlineVariant
                                )
                            )
                        }
                    }
                    TimePickerField(label = "Desde", value = windowStart, onValueChange = { windowStart = it })
                    TimePickerField(label = "Hasta", value = windowEnd, onValueChange = { windowEnd = it })
                    Text(
                        "Se aplica de nuevo en cada comprobación mientras estés dentro de la franja.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                "datetime" -> {
                    DatePickerField(label = "Fecha", value = dateTimeDate, onValueChange = { dateTimeDate = it })
                    TimePickerField(label = "Hora", value = dateTimeTime, onValueChange = { dateTimeTime = it })
                    Text(
                        "Se ejecuta una sola vez, a partir de esa fecha y hora.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                "calendar" -> {
                    if (calendarPermissionDenied) {
                        Text(
                            "Sin permiso de calendario no se puede usar este disparador.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (calendars.isEmpty() && !calendarPermissionDenied) {
                        Text(
                            "Se necesita permiso de calendario para listar tus calendarios.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { calendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR) }) {
                            Text("Conceder permiso de calendario")
                        }
                    }
                    if (calendars.isNotEmpty()) {
                        SheetLabel("Calendario")
                        calendars.forEach { (id, name) ->
                            FilterChip(
                                selected = calendarId == id,
                                onClick = { calendarId = id },
                                label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primary.copy(alpha = 0.15f),
                                    selectedLabelColor = primary,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = calendarId == id,
                                    selectedBorderColor = primary.copy(alpha = 0.5f),
                                    borderColor = OutlineVariant
                                )
                            )
                        }
                        SheetLabel("Título del evento contiene (opcional)")
                        SheetTextField(value = calendarTitle, onValueChange = { calendarTitle = it }, label = "Filtro de título")
                        SheetLabel("Minutos de antelación")
                        SheetTextField(value = calendarMinutesBefore, onValueChange = { calendarMinutesBefore = it }, label = "Minutos antes")
                    }
                }

                "numeric_state" -> {
                    SheetLabel("Entidad")
                    WrappingFilterChips(
                        items = listOf("outdoor_temp" to "Temp. exterior", "indoor_temp" to "Temp. interior"),
                        selected = numericEntity,
                        accentColor = primary,
                        onSelect = { numericEntity = it; threshold = if (it == "outdoor_temp") 5f else 20f }
                    )
                    SheetLabel("Condición")
                    WrappingFilterChips(
                        items = listOf("below" to "Por debajo de", "above" to "Por encima de"),
                        selected = numericCondition,
                        accentColor = primary,
                        onSelect = { numericCondition = it }
                    )
                    val (rangeMin, rangeMax) = if (numericEntity == "outdoor_temp") -15f to 40f else 5f to 35f
                    SheetLabel("Umbral: ${threshold.toInt()}°C")
                    Slider(
                        value = threshold.coerceIn(rangeMin, rangeMax),
                        onValueChange = { threshold = it },
                        valueRange = rangeMin..rangeMax,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = primary,
                            activeTrackColor = primary,
                            inactiveTrackColor = OutlineVariant
                        )
                    )
                }

                "sun" -> {
                    SheetLabel("Evento solar")
                    WrappingFilterChips(
                        items = listOf("sunset" to "Puesta de sol", "sunrise" to "Salida del sol"),
                        selected = sunEvent,
                        accentColor = primary,
                        onSelect = { sunEvent = it }
                    )
                    SheetLabel("Desfase en minutos (negativo = antes)")
                    SheetTextField(
                        value = sunOffset,
                        onValueChange = { sunOffset = it },
                        label = "Desfase (min)"
                    )
                }

                "geofence" -> {
                    SheetLabel("Evento de geovalla")
                    WrappingFilterChips(
                        items = listOf("enter" to "Al llegar", "exit" to "Al salir"),
                        selected = geofenceEvent,
                        accentColor = primary,
                        onSelect = { geofenceEvent = it }
                    )
                    SheetLabel("Zona")
                    if (zones.isEmpty()) {
                        Text(
                            "Todavía no tienes zonas guardadas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        zones.forEach { zone ->
                            FilterChip(
                                selected = geofenceZoneId == zone.id,
                                onClick = { geofenceZoneId = zone.id },
                                label = { Text("${zone.name} · ${zone.radius.toInt()} m", style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primary.copy(alpha = 0.15f),
                                    selectedLabelColor = primary,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = geofenceZoneId == zone.id,
                                    selectedBorderColor = primary.copy(alpha = 0.5f),
                                    borderColor = OutlineVariant
                                )
                            )
                        }
                    }
                    TextButton(onClick = onManageZones) {
                        Text(if (zones.isEmpty()) "Añadir zona" else "Gestionar zonas")
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Add button
            Button(
                enabled = when (selectedType) {
                    "geofence" -> zones.any { it.id == geofenceZoneId }
                    "calendar" -> calendarId >= 0
                    "datetime" -> parseDateTimeMs(dateTimeDate, dateTimeTime) != null
                    "time_window" -> days.isNotEmpty() &&
                        isValidHhMm(windowStart) && isValidHhMm(windowEnd) && windowStart != windowEnd
                    else -> true
                },
                onClick = {
                    val map: Map<String, Any> = when (selectedType) {
                        "time" -> mapOf(
                            "type" to "time",
                            "platform" to "time",
                            "time" to time,
                            "days" to days.sorted()
                        )
                        "numeric_state" -> buildMap {
                            put("type", "numeric_state")
                            put("platform", "numeric_state")
                            put("entity", numericEntity)
                            put(numericCondition, threshold.toDouble())
                        }
                        "sun" -> mapOf(
                            "type" to "sun",
                            "platform" to "sun",
                            "event" to sunEvent,
                            "offset" to (sunOffset.toIntOrNull() ?: 0)
                        )
                        "geofence" -> mapOf(
                            "type" to "geofence",
                            "platform" to "geofence",
                            "event" to geofenceEvent,
                            "zoneId" to geofenceZoneId,
                            "zone" to (zones.firstOrNull { it.id == geofenceZoneId }?.name ?: "")
                        )
                        "time_window" -> mapOf(
                            "type" to "time_window",
                            "days" to days.sorted(),
                            "start" to windowStart,
                            "end" to windowEnd
                        )
                        "datetime" -> mapOf(
                            "type" to "datetime",
                            "at" to (parseDateTimeMs(dateTimeDate, dateTimeTime) ?: 0L)
                        )
                        "calendar" -> buildMap {
                            put("type", "calendar")
                            put("calendarId", calendarId)
                            put("calendarName", calendars.firstOrNull { it.first == calendarId }?.second ?: "")
                            put("title", calendarTitle.trim())
                            put("minutesBefore", calendarMinutesBefore.toIntOrNull()?.coerceAtLeast(0) ?: 30)
                        }
                        else -> emptyMap()
                    }
                    onAdd(map)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Añadir disparador", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text("Cancelar")
            }
        }
    }
}

// ── Shared sheet helpers ──────────────────────────────────────────────────────

@Composable
internal fun SheetLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium
    )
}

@Composable
internal fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = OutlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = SurfaceContainerLow,
            focusedContainerColor = SurfaceContainerLow
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WrappingFilterChips(
    items: List<Pair<String, String>>,
    selected: String,
    accentColor: androidx.compose.ui.graphics.Color,
    onSelect: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accentColor.copy(alpha = 0.15f),
                    selectedLabelColor = accentColor,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == value,
                    selectedBorderColor = accentColor.copy(alpha = 0.5f),
                    borderColor = OutlineVariant
                )
            )
        }
    }
}


// ── Calendar + date helpers ──────────────────────────────────────────────────

internal fun isValidHhMm(value: String): Boolean =
    Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matches(value.trim())

/** Parses "AAAA-MM-DD" + "HH:MM" in local time; null when invalid. */
internal fun parseDateTimeMs(date: String, time: String): Long? {
    return try {
        val d = date.trim().split("-").map { it.toInt() }
        val t = time.trim().split(":").map { it.toInt() }
        if (d.size != 3 || t.size != 2) return null
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            set(d[0], d[1] - 1, d[2], t[0], t[1])
        }.timeInMillis
    } catch (e: Exception) {
        null
    }
}

internal fun queryDeviceCalendars(context: android.content.Context): List<Pair<Long, String>> {
    val result = mutableListOf<Pair<Long, String>>()
    try {
        context.contentResolver.query(
            android.provider.CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                android.provider.CalendarContract.Calendars._ID,
                android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
            ),
            "${android.provider.CalendarContract.Calendars.VISIBLE} = 1",
            null,
            "${android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getLong(0) to (cursor.getString(1) ?: "Calendario"))
            }
        }
    } catch (e: SecurityException) {
        // Permission not granted yet.
    }
    return result
}

// ── Date/time picker fields ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerField(label: String, value: String, onValueChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    SheetLabel(label)
    OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(if (value.isEmpty()) "Elegir fecha" else formatDateEs(value))
    }
    if (show) {
        val state = rememberDatePickerState(initialSelectedDateMillis = utcMsFromYmd(value))
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onValueChange(ymdFromUtcMs(it)) }
                    show = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerField(label: String, value: String, onValueChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    SheetLabel(label)
    OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(value)
    }
    if (show) {
        val parts = value.split(":")
        val state = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 12,
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange("%02d:%02d".format(state.hour, state.minute))
                    show = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Cancelar") } },
            text = { TimePicker(state = state) }
        )
    }
}

internal fun utcMsFromYmd(ymd: String): Long? = try {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
    fmt.parse(ymd)?.time
} catch (e: Exception) { null }

internal fun ymdFromUtcMs(ms: Long): String {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return fmt.format(java.util.Date(ms))
}

internal fun formatDateEs(ymd: String): String = try {
    val p = ymd.split("-").map { it.toInt() }
    "%02d/%02d/%04d".format(p[2], p[1], p[0])
} catch (e: Exception) { ymd }
