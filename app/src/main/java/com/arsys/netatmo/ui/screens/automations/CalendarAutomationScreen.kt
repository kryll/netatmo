package com.arsys.netatmo.ui.screens.automations

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arsys.netatmo.ui.components.TemperatureSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarAutomationScreen(
    automationId: Long,
    navController: NavController,
    viewModel: CalendarAutomationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(automationId) {
        viewModel.load(automationId)
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (automationId == -1L) "Nueva automatización de calendario"
                        else "Editar automatización",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth()
            )

            // Seleccionar calendario
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Calendario", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (uiState.calendars.isEmpty()) {
                        Text(
                            "No se encontraron calendarios. Verifica permisos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        uiState.calendars.forEach { calendar ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.selectedCalendarId == calendar.id,
                                    onClick = { viewModel.selectCalendar(calendar) }
                                )
                                Text(calendar.name, modifier = Modifier.weight(1f))
                                Text(
                                    calendar.accountName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Filtro de evento
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FilterList, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Filtro de evento", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedTextField(
                        value = uiState.titleFilter,
                        onValueChange = { viewModel.updateTitleFilter(it) },
                        label = { Text("Título del evento (vacío = todos)") },
                        placeholder = { Text("ej: Trabajo, Reunión...") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (uiState.titleFilter.isNotBlank()) {
                                IconButton(onClick = { viewModel.updateTitleFilter("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Borrar")
                                }
                            }
                        }
                    )

                    // Coincidencia exacta
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Coincidencia exacta", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (uiState.exactMatch) "El título debe ser idéntico al filtro"
                                else "El título debe contener el filtro",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.exactMatch,
                            onCheckedChange = { viewModel.updateExactMatch(it) },
                            enabled = uiState.titleFilter.isNotBlank()
                        )
                    }

                    // Eventos disponibles del calendario
                    if (uiState.selectedCalendarId != null) {
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Eventos próximos (30 días)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (uiState.isLoadingEvents) {
                                Spacer(modifier = Modifier.width(8.dp))
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            }
                        }

                        if (!uiState.isLoadingEvents && uiState.availableEventTitles.isEmpty()) {
                            Text(
                                "No se encontraron eventos próximos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.availableEventTitles.forEach { title ->
                                    FilterChip(
                                        selected = uiState.titleFilter == title,
                                        onClick = {
                                            if (uiState.titleFilter == title) {
                                                viewModel.updateTitleFilter("")
                                            } else {
                                                viewModel.updateTitleFilter(title)
                                                viewModel.updateExactMatch(true)
                                            }
                                        },
                                        label = { Text(title, maxLines = 1) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Antelación
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Precalentar antes del evento", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${uiState.minutesBefore} minutos antes",
                        style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = uiState.minutesBefore.toFloat(),
                        onValueChange = { viewModel.updateMinutesBefore(it.toInt()) },
                        valueRange = 0f..120f,
                        steps = 23
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0, 15, 30, 60, 90).forEach { mins ->
                            FilterChip(
                                selected = uiState.minutesBefore == mins,
                                onClick = { viewModel.updateMinutesBefore(mins) },
                                label = { Text(if (mins == 0) "Al inicio" else "${mins}min") }
                            )
                        }
                    }
                }
            }

            // Temperatura objetivo
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Temperatura objetivo", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    TemperatureSlider(
                        value = uiState.targetTemperature,
                        onValueChange = { viewModel.updateTemperature(it) }
                    )
                }
            }

            if (uiState.error != null) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text("Guardar")
            }
        }
    }
}
