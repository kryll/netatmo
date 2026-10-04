package com.arsys.netatmo.ui.screens.automations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Thermostat
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
fun OutdoorTempAutomationScreen(
    automationId: Long,
    navController: NavController,
    viewModel: OutdoorTempAutomationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var name by remember { mutableStateOf("Temperatura exterior") }
    var condition by remember { mutableStateOf("below") }
    var threshold by remember { mutableStateOf(5.0) }
    var targetTemp by remember { mutableStateOf(20.0) }
    var mode by remember { mutableStateOf("heating") }
    var enabled by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        uiState?.let {
            name = it.name
            condition = it.condition
            threshold = it.thresholdTemp
            targetTemp = it.targetTemp
            mode = it.mode
            enabled = it.enabled
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar automatización") },
            text = { Text("¿Seguro que quieres eliminar esta automatización? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
                        navController.popBackStack()
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (automationId == -1L) "Nueva automatización exterior"
                               else "Editar automatización exterior",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Accent)
                    }
                },
                actions = {
                    if (automationId != -1L) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = {
                        viewModel.save(name, condition, threshold, targetTemp, mode, enabled)
                        navController.popBackStack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Text("Guardar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current outdoor temperature card
            if (uiState.outdoorTemp != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Accent.copy(alpha = 0.10f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Accent.copy(alpha = 0.20f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Thermostat,
                                contentDescription = null,
                                tint = Accent,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Temperatura actual exterior",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "%.1f°C".format(uiState.outdoorTemp),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // Name field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Condition selector
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Condición",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = condition == "below",
                            onClick = { condition = "below" },
                            label = { Text("Por debajo de") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = condition == "above",
                            onClick = { condition = "above" },
                            label = { Text("Por encima de") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Threshold temperature slider (−15°C to 40°C, step 0.5°C)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Temperatura umbral",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "-15°C",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "%.1f°C".format(threshold),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Accent
                        )
                        Text(
                            text = "40°C",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = threshold.toFloat(),
                        onValueChange = { threshold = Math.round(it * 2).toDouble() / 2 },
                        valueRange = -15f..40f,
                        steps = 109,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Target temperature slider (5°C to 30°C, step 0.5°C)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Temperatura objetivo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TemperatureSlider(
                        value = targetTemp,
                        onValueChange = { targetTemp = it },
                        minTemp = 5f,
                        maxTemp = 30f
                    )
                }
            }

            // Mode selector
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Modo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = mode == "heating",
                            onClick = { mode = "heating" },
                            label = { Text("Calefacción") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = mode == "cooling",
                            onClick = { mode = "cooling" },
                            label = { Text("Refrigeración") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Enable/disable switch
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Automatización activa",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            if (enabled) "Se ejecutará cuando se cumpla la condición"
                            else "No se ejecutará",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
