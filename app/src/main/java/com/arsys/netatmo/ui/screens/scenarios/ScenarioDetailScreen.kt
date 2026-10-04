package com.arsys.netatmo.ui.screens.scenarios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun ScenarioDetailScreen(
    scenarioId: Long,
    navController: NavController,
    viewModel: ScenarioDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(scenarioId) {
        viewModel.load(scenarioId)
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
                    if (scenarioId == -1L) "Nuevo escenario" else "Editar escenario",
                    color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        HorizontalDivider(color = Color(0xFFE2E8F0))

        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text("Nombre del escenario") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Color selector
            item {
                Text("Color", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#1976D2", "#388E3C", "#F57C00", "#7B1FA2", "#D32F2F", "#00796B").forEach { color ->
                        FilterChip(
                            selected = uiState.color == color,
                            onClick = { viewModel.updateColor(color) },
                            label = { Text(color.takeLast(6)) }
                        )
                    }
                }
            }

            // Room actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Habitaciones", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { viewModel.addRoom() }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Añadir")
                    }
                }
            }

            itemsIndexed(uiState.roomActions) { index, action ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Habitación ${index + 1}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = { viewModel.removeRoom(index) }) {
                                Icon(Icons.Default.Remove, contentDescription = "Quitar",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        if (uiState.availableRooms.isNotEmpty()) {
                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                                OutlinedTextField(
                                    value = action.roomName.ifBlank { "Seleccionar habitación" },
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                                )
                                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    uiState.availableRooms.forEach { room ->
                                        DropdownMenuItem(
                                            text = { Text(room.second) },
                                            onClick = {
                                                viewModel.updateRoomId(index, room.first, room.second)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        TemperatureSlider(
                            value = action.temperature,
                            onValueChange = { viewModel.updateRoomTemperature(index, it) }
                        )
                    }
                }
            }

            if (uiState.error != null) {
                item { Text(uiState.error!!, color = MaterialTheme.colorScheme.error) }
            }

            item {
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !uiState.isSaving
                ) {
                    if (uiState.isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    else Text("Guardar escenario")
                }
            }
        }
    }
}
