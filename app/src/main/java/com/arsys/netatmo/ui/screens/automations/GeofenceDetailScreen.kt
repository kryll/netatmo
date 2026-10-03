package com.arsys.netatmo.ui.screens.automations

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
fun GeofenceDetailScreen(
    automationId: Long,
    navController: NavController,
    viewModel: GeofenceDetailViewModel = hiltViewModel()
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
                        if (automationId == -1L) "Nueva Geovalla" else "Editar Geovalla",
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
            // Nombre
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Nombre de la automatización") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Ubicación
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ubicación", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.locationName,
                        onValueChange = { viewModel.updateLocationName(it) },
                        label = { Text("Nombre del lugar (ej: Casa)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.latitude,
                            onValueChange = { viewModel.updateLatitude(it) },
                            label = { Text("Latitud") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = uiState.longitude,
                            onValueChange = { viewModel.updateLongitude(it) },
                            label = { Text("Longitud") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Radio: ${uiState.radius.toInt()} metros",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = uiState.radius,
                        onValueChange = { viewModel.updateRadius(it) },
                        valueRange = 50f..2000f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { viewModel.useCurrentLocation() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Usar mi ubicación actual")
                    }
                }
            }

            // Al llegar
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Login, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Al llegar", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold)
                        }
                        Switch(
                            checked = uiState.triggerOnEnter,
                            onCheckedChange = { viewModel.updateTriggerOnEnter(it) }
                        )
                    }
                    if (uiState.triggerOnEnter) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Temperatura al llegar", style = MaterialTheme.typography.labelMedium)
                        TemperatureSlider(
                            value = uiState.tempOnEnter,
                            onValueChange = { viewModel.updateTempOnEnter(it) }
                        )
                    }
                }
            }

            // Al salir
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Logout, contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Al salir", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold)
                        }
                        Switch(
                            checked = uiState.triggerOnExit,
                            onCheckedChange = { viewModel.updateTriggerOnExit(it) }
                        )
                    }
                    if (uiState.triggerOnExit) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Temperatura al salir", style = MaterialTheme.typography.labelMedium)
                        TemperatureSlider(
                            value = uiState.tempOnExit,
                            onValueChange = { viewModel.updateTempOnExit(it) }
                        )
                    }
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
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Guardar automatización")
                }
            }
        }
    }
}
