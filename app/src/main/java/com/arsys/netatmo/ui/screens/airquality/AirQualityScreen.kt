package com.arsys.netatmo.ui.screens.airquality

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val BgColor = Color(0xFF101419)
private val CardColor = Color(0xFF1C2025)
private val BorderColor = Color(0xFF3F4850)
private val TextPrimary = Color(0xFFE0E2EA)
private val TextSecondary = Color(0xFFBFC7D2)
private val GreenOk = Color(0xFF62DF7D)
private val OrangeWarn = Color(0xFFFFB599)
private val RedBad = Color(0xFFFFB4AB)
private val BlueAccent = Color(0xFF93CCFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirQualityScreen(
    onBack: () -> Unit,
    viewModel: AirQualityViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calidad del Aire", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = BlueAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E13))
            )
        },
        containerColor = BgColor
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BlueAccent)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            state.error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF93000A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(err, modifier = Modifier.padding(16.dp), color = Color(0xFFFFDAD6))
                }
            }

            // 2x2 grid of metric cards
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val co2Color = when {
                    state.co2Ppm == null -> TextSecondary
                    state.co2Ppm < 800 -> GreenOk
                    state.co2Ppm < 1200 -> OrangeWarn
                    else -> RedBad
                }
                val co2Label = when {
                    state.co2Ppm == null -> "Sin datos"
                    state.co2Ppm < 800 -> "Buena"
                    state.co2Ppm < 1200 -> "Moderada"
                    else -> "Mala"
                }
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Air,
                    iconTint = co2Color,
                    value = state.co2Ppm?.toString() ?: "--",
                    unit = "ppm",
                    label = "CO₂",
                    sublabel = co2Label,
                    sublabelColor = co2Color
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.WaterDrop,
                    iconTint = BlueAccent,
                    value = state.humidity?.toString() ?: "--",
                    unit = "%",
                    label = "Humedad"
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.VolumeUp,
                    iconTint = OrangeWarn,
                    value = state.noiseDb?.toString() ?: "--",
                    unit = "dB",
                    label = "Ruido"
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Compress,
                    iconTint = TextSecondary,
                    value = state.pressureMbar?.let { "%.0f".format(it) } ?: "--",
                    unit = "mbar",
                    label = "Presión"
                )
            }

            // Overall quality
            val overallColor = when {
                state.co2Ppm == null -> TextSecondary
                state.co2Ppm < 800 -> GreenOk
                state.co2Ppm < 1200 -> OrangeWarn
                else -> RedBad
            }
            val overallLabel = when {
                state.co2Ppm == null -> "Sin datos del sensor"
                state.co2Ppm < 800 -> "Calidad excelente"
                state.co2Ppm < 1200 -> "Calidad moderada"
                else -> "Calidad deficiente — ventila la habitación"
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = overallColor, modifier = Modifier.size(32.dp))
                    Column {
                        Text("Calidad general", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(overallLabel, color = overallColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }

            if (state.co2Ppm == null && state.humidity == null && state.noiseDb == null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = BlueAccent)
                        Text("Estación meteorológica necesaria", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Los datos de CO₂, humedad y ruido requieren una estación Netatmo NAMain conectada al sistema.",
                            color = TextSecondary, fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    value: String,
    unit: String,
    label: String,
    sublabel: String? = null,
    sublabelColor: Color = TextSecondary
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(unit, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text(label, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            sublabel?.let { Text(it, color = sublabelColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
