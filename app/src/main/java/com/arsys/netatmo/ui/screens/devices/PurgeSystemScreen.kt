package com.arsys.netatmo.ui.screens.devices

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
private val OrangeWarn = Color(0xFFFFB599)

private val PURGE_STEPS = listOf(
    "Abriendo válvula de purga...",
    "Expulsando aire del circuito...",
    "Comprobando presión...",
    "Cerrando válvula de purga...",
    "Verificando estabilización del sistema..."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurgeSystemScreen(onBack: () -> Unit) {
    var isPurging by remember { mutableStateOf(false) }
    var isComplete by remember { mutableStateOf(false) }
    var currentStepIndex by remember { mutableIntStateOf(-1) }
    var pressure by remember { mutableFloatStateOf(1.2f) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Purga del Sistema", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(28.dp))
                        Text("Purga de aire del sistema", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    Text("La purga elimina el aire acumulado en el circuito hidráulico, mejorando la eficiencia del sistema y eliminando ruidos de circulación.", color = TextSecondary, fontSize = 13.sp)
                    Text("⚠️ Asegúrate de que la caldera está encendida antes de iniciar.", color = OrangeWarn, fontSize = 12.sp)
                }
            }

            // Pressure gauge
            Card(
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Presión del sistema", color = TextSecondary, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("%.1f".format(pressure), color = TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Text("bar", color = TextSecondary, fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    val pressureProgress = ((pressure - 1.0f) / (2.5f - 1.0f)).coerceIn(0f, 1f)
                    val pressureColor = when {
                        pressure < 1.0f -> Color(0xFFFFB4AB)
                        pressure > 2.0f -> Color(0xFFFFB4AB)
                        pressure in 1.5f..1.8f -> GreenOk
                        else -> OrangeWarn
                    }
                    LinearProgressIndicator(
                        progress = { pressureProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = pressureColor,
                        trackColor = BorderColor
                    )
                    Text(
                        if (pressure < 1.0f) "Presión baja" else if (pressure > 2.0f) "Presión alta" else "Presión normal",
                        color = pressureColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Progress
            if (isPurging || isComplete) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (isComplete) GreenOk.copy(alpha = 0.3f) else BorderColor)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Progreso de purga", color = TextSecondary, fontSize = 12.sp)
                        if (!isComplete) {
                            LinearProgressIndicator(
                                progress = { (currentStepIndex + 1).toFloat() / PURGE_STEPS.size },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = BlueAccent,
                                trackColor = BorderColor
                            )
                        }
                        PURGE_STEPS.forEachIndexed { i, step ->
                            if (i <= currentStepIndex || isComplete) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenOk, modifier = Modifier.size(18.dp))
                                    Text(step, color = TextPrimary, fontSize = 13.sp)
                                }
                            } else if (i == currentStepIndex + 1 && isPurging) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = BlueAccent)
                                    Text(step, color = TextSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (isComplete) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GreenOk.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GreenOk.copy(alpha = 0.4f))
                ) {
                    Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenOk, modifier = Modifier.size(32.dp))
                        Column {
                            Text("Purga completada", color = GreenOk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Presión estabilizada en %.1f bar".format(pressure), color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (!isPurging && !isComplete) {
                Button(
                    onClick = {
                        isPurging = true
                        currentStepIndex = 0
                        scope.launch {
                            PURGE_STEPS.forEachIndexed { i, _ ->
                                currentStepIndex = i
                                pressure = 1.2f + (i * 0.12f)
                                delay(5000)
                            }
                            pressure = 1.8f
                            isComplete = true
                            isPurging = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Color(0xFF001D31)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar purga", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
