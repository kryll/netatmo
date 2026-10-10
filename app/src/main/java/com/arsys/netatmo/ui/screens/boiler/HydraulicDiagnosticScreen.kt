package com.arsys.netatmo.ui.screens.boiler

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val BgColor = Color(0xFF101419)
private val CardColor = Color(0xFF1C2025)
private val BorderColor = Color(0xFF3F4850)
private val TextPrimary = Color(0xFFE0E2EA)
private val TextSecondary = Color(0xFFBFC7D2)
private val GreenOk = Color(0xFF62DF7D)
private val BlueAccent = Color(0xFF93CCFF)

private val DIAGNOSTIC_STEPS = listOf(
    "Verificando presión del sistema...",
    "Comprobando nivel de modulación...",
    "Analizando circuito primario...",
    "Verificando válvulas de zona...",
    "Comprobando bomba de circulación...",
    "Analizando circuito de retorno...",
    "Generando informe de diagnóstico..."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydraulicDiagnosticScreen(onBack: () -> Unit) {
    var secondsLeft by remember { mutableIntStateOf(60) }
    var visibleSteps by remember { mutableIntStateOf(0) }
    var isComplete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repeat(60) {
            delay(1000)
            secondsLeft--
            if (secondsLeft == 0) { isComplete = true }
        }
    }
    LaunchedEffect(Unit) {
        repeat(DIAGNOSTIC_STEPS.size) {
            delay(8000L)
            visibleSteps++
        }
    }

    val progress = (60 - secondsLeft) / 60f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico Hidráulico", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (!isComplete) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(120.dp),
                        strokeWidth = 8.dp,
                        color = BlueAccent,
                        trackColor = BorderColor
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$secondsLeft", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text("seg", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                Text("Diagnóstico en curso", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("Analizando el sistema hidráulico de tu caldera Netatmo...", color = TextSecondary, fontSize = 13.sp)
            } else {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenOk, modifier = Modifier.size(72.dp))
                Text("Diagnóstico completado", color = GreenOk, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GreenOk.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DiagnosticResultRow("Sistema hidráulico", "OK", GreenOk)
                        DiagnosticResultRow("Presión", "Normal (1.5 bar)", GreenOk)
                        DiagnosticResultRow("Circulación", "Correcta", GreenOk)
                        DiagnosticResultRow("Válvulas", "Operativas", GreenOk)
                        DiagnosticResultRow("Bomba", "Funcionando", GreenOk)
                    }
                }
            }

            // Steps list
            Card(
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Registro de diagnóstico", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    DIAGNOSTIC_STEPS.take(if (isComplete) DIAGNOSTIC_STEPS.size else visibleSteps).forEachIndexed { i, step ->
                        AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenOk, modifier = Modifier.size(18.dp))
                                Text(step, color = TextPrimary, fontSize = 13.sp)
                            }
                        }
                    }
                    if (!isComplete && visibleSteps < DIAGNOSTIC_STEPS.size) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = BlueAccent)
                            Text(DIAGNOSTIC_STEPS[visibleSteps], color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticResultRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 14.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
