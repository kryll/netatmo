package com.arsys.netatmo.ui.screens.automations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arsys.netatmo.ui.components.TemperatureSlider

private val Accent = Color(0xFF0284C7)
private val TextPrimary = Color(0xFF1E293B)

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

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Accent)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (automationId == -1L) "Nueva automatización de calendario"
                    else "Editar automatización",
                    color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        HorizontalDivider(color = Color(0xFFE2E8F0))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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

            // Filtro por título
            OutlinedTextField(
                value = uiState.titleFilter,
                onValueChange = { viewModel.updateTitleFilter(it) },
                label = { Text("Filtrar por título (dejar vacío = todos)") },
                placeholder = { Text("ej: Trabajo, Reunión...") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Solo se activará para eventos que contengan este texto") }
            )

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
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text("Guardar")
            }
        }
    }
}
