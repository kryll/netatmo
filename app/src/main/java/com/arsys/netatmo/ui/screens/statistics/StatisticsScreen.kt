package com.arsys.netatmo.ui.screens.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arsys.netatmo.domain.model.TemperatureDataPoint
import com.arsys.netatmo.ui.theme.ComfortColor
import com.arsys.netatmo.ui.theme.WarmColor
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val periods = listOf("24h" to 1, "7 días" to 7, "30 días" to 30)
    var selectedPeriod by remember { mutableStateOf(7) }

    LaunchedEffect(selectedPeriod) {
        viewModel.loadStatistics(selectedPeriod)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estadísticas", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Period selector
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(periods) { (label, days) ->
                        FilterChip(
                            selected = selectedPeriod == days,
                            onClick = { selectedPeriod = days },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // Summary cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Temp. media",
                        value = uiState.avgTemp?.let { "%.1f°C".format(it) } ?: "--",
                        icon = Icons.Default.Thermostat,
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Temp. máx.",
                        value = uiState.maxTemp?.let { "%.1f°C".format(it) } ?: "--",
                        icon = Icons.Default.ThermostatAuto,
                        color = WarmColor
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Temp. mín.",
                        value = uiState.minTemp?.let { "%.1f°C".format(it) } ?: "--",
                        icon = Icons.Default.AcUnit,
                        color = Color(0xFF42A5F5)
                    )
                }
            }

            // Heating hours
            item {
                StatCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Horas de calefacción",
                    value = "${uiState.heatingHours ?: 0}h",
                    icon = Icons.Default.Whatshot,
                    color = WarmColor
                )
            }

            // Temperature chart
            if (uiState.temperatureData.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Temperatura",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TemperatureChart(
                                data = uiState.temperatureData,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                            // Legend
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                LegendItem(color = MaterialTheme.colorScheme.primary, label = "Temperatura")
                                LegendItem(color = WarmColor, label = "Objetivo")
                            }
                        }
                    }
                }
            }

            // Heating chart
            if (uiState.temperatureData.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Actividad de calefacción",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            HeatingChart(
                                data = uiState.temperatureData,
                                modifier = Modifier.fillMaxWidth().height(80.dp)
                            )
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TemperatureChart(
    data: List<TemperatureDataPoint>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val warmColor = WarmColor

    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas

        val minTemp = data.minOf { it.temperature }.minus(1.0).toFloat()
        val maxTemp = data.maxOf { it.temperature }.plus(1.0).toFloat()
        val minTime = data.first().timestamp.toFloat()
        val maxTime = data.last().timestamp.toFloat()

        val timeRange = maxTime - minTime
        val tempRange = maxTemp - minTemp

        fun xOf(ts: Long) = if (timeRange == 0f) size.width / 2
        else ((ts - minTime) / timeRange) * size.width

        fun yOf(temp: Double) = size.height - ((temp.toFloat() - minTemp) / tempRange) * size.height

        // Temperature line
        val path = Path()
        data.forEachIndexed { index, point ->
            val x = xOf(point.timestamp)
            val y = yOf(point.temperature)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, primaryColor, style = Stroke(width = 3f))

        // Setpoint line
        val setpointPath = Path()
        var started = false
        data.forEach { point ->
            point.setpoint?.let { sp ->
                val x = xOf(point.timestamp)
                val y = yOf(sp)
                if (!started) { setpointPath.moveTo(x, y); started = true }
                else setpointPath.lineTo(x, y)
            }
        }
        if (started) drawPath(setpointPath, warmColor, style = Stroke(width = 2f, pathEffect =
            androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 5f))))
    }
}

@Composable
fun HeatingChart(
    data: List<TemperatureDataPoint>,
    modifier: Modifier = Modifier
) {
    val heatingColor = WarmColor

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas
        val minTime = data.first().timestamp.toFloat()
        val maxTime = data.last().timestamp.toFloat()
        val timeRange = maxTime - minTime
        if (timeRange == 0f) return@Canvas

        data.zipWithNext().forEach { (a, b) ->
            if (a.heatingActive) {
                val x1 = ((a.timestamp - minTime) / timeRange) * size.width
                val x2 = ((b.timestamp - minTime) / timeRange) * size.width
                drawRect(
                    color = heatingColor.copy(alpha = 0.7f),
                    topLeft = Offset(x1, 0f),
                    size = androidx.compose.ui.geometry.Size(x2 - x1, size.height)
                )
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .let { mod ->
                    mod
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color)
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
