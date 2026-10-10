package com.arsys.netatmo.ui.screens.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BgColor = Color(0xFF101419)
private val CardColor = Color(0xFF1C2025)
private val BorderColor = Color(0xFF3F4850)
private val TextPrimary = Color(0xFFE0E2EA)
private val TextSecondary = Color(0xFFBFC7D2)
private val BlueAccent = Color(0xFF93CCFF)
private val GreenOk = Color(0xFF62DF7D)

private val ROOMS = listOf("Salón", "Dormitorio principal", "Cocina", "Dormitorio 2", "Baño")

private val CAL_STEPS = listOf(
    "Cerrando válvula completamente...",
    "Detectando rango de movimiento...",
    "Estableciendo posición óptima...",
    "Verificando respuesta térmica...",
    "Guardando calibración..."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValveCalibrationScreen(onBack: () -> Unit) {
    var selectedRoom by remember { mutableStateOf<String?>(null) }
    var isCalibrating by remember { mutableStateOf(false) }
    var isCalibrated by remember { mutableStateOf(false) }
    var currentStep by remember { mutableIntStateOf(-1) }
    var valvePosition by remember { mutableFloatStateOf(50f) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibración de Válvula", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E13))
            )
        },
        containerColor = BgColor
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Selecciona una zona", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ROOMS) { room ->
                    val isSelected = room == selectedRoom
                    Card(
                        onClick = {
                            if (!isCalibrating) {
                                selectedRoom = room
                                isCalibrated = false
                                currentStep = -1
                                valvePosition = 50f
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) BlueAccent.copy(alpha = 0.1f) else CardColor),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isSelected) BlueAccent else BorderColor)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = if (isSelected) BlueAccent else TextSecondary, modifier = Modifier.size(22.dp))
                                Text(room, color = if (isSelected) BlueAccent else TextPrimary, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                            }
                            if (isSelected) Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BlueAccent)
                        }
                    }

                    if (isSelected) {
                        Spacer(Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardColor),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Válvula — $room", color = TextPrimary, fontWeight = FontWeight.SemiBold)

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Posición", color = TextSecondary, fontSize = 13.sp)
                                        Text("${valvePosition.toInt()}%", color = TextPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = valvePosition,
                                        onValueChange = { if (!isCalibrating) valvePosition = it },
                                        valueRange = 0f..100f,
                                        enabled = !isCalibrating,
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = BlueAccent,
                                            thumbColor = BlueAccent,
                                            inactiveTrackColor = BorderColor
                                        )
                                    )
                                }

                                if (isCalibrating) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LinearProgressIndicator(
                                            progress = { (currentStep + 1).toFloat() / CAL_STEPS.size },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = BlueAccent,
                                            trackColor = BorderColor
                                        )
                                        CAL_STEPS.take(currentStep + 1).forEach { step ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenOk, modifier = Modifier.size(16.dp))
                                                Text(step, color = TextSecondary, fontSize = 12.sp)
                                            }
                                        }
                                        if (currentStep + 1 < CAL_STEPS.size) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = BlueAccent)
                                                Text(CAL_STEPS[currentStep + 1], color = TextSecondary, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }

                                if (isCalibrated) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenOk, modifier = Modifier.size(24.dp))
                                        Text("Válvula calibrada correctamente", color = GreenOk, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                if (!isCalibrating && !isCalibrated) {
                                    Button(
                                        onClick = {
                                            isCalibrating = true
                                            currentStep = -1
                                            scope.launch {
                                                CAL_STEPS.forEachIndexed { i, _ ->
                                                    currentStep = i - 1
                                                    valvePosition = when (i) {
                                                        0 -> 0f; 1 -> 30f; 2 -> 65f; 3 -> 72f; else -> 70f
                                                    }
                                                    delay(3000)
                                                }
                                                currentStep = CAL_STEPS.size - 1
                                                delay(1000)
                                                isCalibrating = false
                                                isCalibrated = true
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Color(0xFF001D31)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Calibrar válvula", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
