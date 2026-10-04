package com.arsys.netatmo.ui.screens.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arsys.netatmo.data.local.entities.TemperatureHistoryEntity
import com.arsys.netatmo.domain.model.TemperatureDataPoint
import com.arsys.netatmo.ui.theme.WarmColor

private val Accent = Color(0xFF0284C7)
private val TextPrimary = Color(0xFF1E293B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val periods = listOf("24h" to 1, "7 días" to 7, "30 días" to 30)
    var selectedPeriod by remember { mutableStateOf(7) }

    LaunchedEffect(selectedPeriod, uiState.selectedRoomId) {
        viewModel.loadStatistics(selectedPeriod)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Text(
                text = "Estadísticas",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider(color = Color(0xFFE2E8F0))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Room selector
            if (uiState.availableRooms.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.availableRooms) { (roomId, roomName) ->
                            FilterChip(
                                selected = uiState.selectedRoomId == roomId,
                                onClick = { viewModel.selectRoom(roomId) },
                                label = { Text(roomName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Period selector
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(periods) { (label, days) ->
                        FilterChip(
                            selected = selectedPeriod == days,
                            onClick = { selectedPeriod = days },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Accent,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // 24h Temperature History Card
            item {
                TemperatureHistory24hCard(
                    points = uiState.historyPoints,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Heating Report Card
            item {
                HeatingReportCard(
                    heatingHoursToday = uiState.totalHeatingHoursToday,
                    energyToday = uiState.energyKwhToday,
                    costToday = uiState.estimatedCostToday,
                    kwhPrice = uiState.kwhPrice,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Summary cards (aggregate period)
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
                        color = Accent
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

            // Heating hours (aggregate)
            item {
                StatCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Horas de calefacción",
                    value = "${uiState.heatingHours ?: 0}h",
                    icon = Icons.Default.Whatshot,
                    color = WarmColor
                )
            }

            // Multi-day temperature chart
            if (uiState.temperatureData.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                LegendItem(color = Accent, label = "Temperatura")
                                LegendItem(color = WarmColor, label = "Objetivo")
                            }
                        }
                    }
                }
            }

            // Heating activity chart (multi-day)
            if (uiState.temperatureData.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
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
                        CircularProgressIndicator(color = Accent)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 24h Temperature History Card
// ---------------------------------------------------------------------------

@Composable
fun TemperatureHistory24hCard(
    points: List<TemperatureHistoryEntity>,
    modifier: Modifier = Modifier
) {
    val tempColor = Color(0xFF0284C7)
    val setpointColor = Color(0xFFEF4444).copy(alpha = 0.6f)
    val gridColor = Color(0xFFE2E8F0)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Últimas 24 horas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                // Layout constants (in px)
                val leftPad = 40f   // space for Y-axis labels
                val bottomPad = 26f // space for X-axis labels
                val chartLeft = leftPad
                val chartRight = size.width
                val chartTop = 0f
                val chartBottom = size.height - bottomPad
                val chartWidth = chartRight - chartLeft
                val chartHeight = chartBottom - chartTop

                // Temperature scale: 14°C … 30°C  (range = 16)
                val tempMin = 14f
                val tempRange = 16f

                fun xOf(ts: Long, startTs: Long, durationMs: Long): Float {
                    if (durationMs <= 0L) return chartLeft
                    return chartLeft + (ts - startTs).toFloat() / durationMs * chartWidth
                }

                fun yOf(temp: Float): Float =
                    chartBottom - (temp - tempMin) / tempRange * chartHeight

                // --- Grid lines ---
                val gridPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                }
                listOf(16f, 18f, 20f, 22f, 24f).forEach { gridTemp ->
                    val y = yOf(gridTemp)
                    drawLine(
                        color = gridColor,
                        start = Offset(chartLeft, y),
                        end = Offset(chartRight, y),
                        strokeWidth = 1.5f
                    )
                }

                // --- Y-axis labels ---
                val yLabelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(180, 100, 116, 139)
                    textSize = 26f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                listOf("14°" to 14f, "20°" to 20f, "28°" to 28f).forEach { (label, temp) ->
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        chartLeft - 6f,
                        yOf(temp) + 9f,
                        yLabelPaint
                    )
                }

                // --- Data lines and circles ---
                if (points.size >= 2) {
                    val startTs = points.first().timestamp
                    val endTs = points.last().timestamp
                    val durationMs = (endTs - startTs).coerceAtLeast(1L)

                    // Temperature line
                    val tempPath = Path()
                    points.forEachIndexed { index, point ->
                        val x = xOf(point.timestamp, startTs, durationMs)
                        val y = yOf(point.temperature.toFloat())
                        if (index == 0) tempPath.moveTo(x, y) else tempPath.lineTo(x, y)
                    }
                    drawPath(tempPath, tempColor, style = Stroke(width = 3f))

                    // Setpoint dashed line
                    val setpointPath = Path()
                    var setpointStarted = false
                    points.forEach { point ->
                        point.setpoint?.let { sp ->
                            val x = xOf(point.timestamp, startTs, durationMs)
                            val y = yOf(sp.toFloat())
                            if (!setpointStarted) {
                                setpointPath.moveTo(x, y)
                                setpointStarted = true
                            } else {
                                setpointPath.lineTo(x, y)
                            }
                        }
                    }
                    if (setpointStarted) {
                        drawPath(
                            setpointPath,
                            setpointColor,
                            style = Stroke(
                                width = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                            )
                        )
                    }

                    // Data point circles – subsample for readability
                    val step = maxOf(1, points.size / 30)
                    points.forEachIndexed { index, point ->
                        if (index % step == 0) {
                            val x = xOf(point.timestamp, startTs, durationMs)
                            val y = yOf(point.temperature.toFloat())
                            drawCircle(tempColor, radius = 4.5f, center = Offset(x, y))
                            drawCircle(Color.White, radius = 2f, center = Offset(x, y))
                        }
                    }
                }

                // --- X-axis labels ---
                val xLabelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(180, 100, 116, 139)
                    textSize = 26f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                listOf("0h", "6h", "12h", "18h", "24h").forEachIndexed { index, label ->
                    val x = chartLeft + index.toFloat() / 4f * chartWidth
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        x,
                        size.height - 4f,
                        xLabelPaint
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                LegendItem(color = Color(0xFF0284C7), label = "Temperatura")
                LegendItem(color = Color(0xFFEF4444).copy(alpha = 0.6f), label = "Objetivo")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Heating Report Card
// ---------------------------------------------------------------------------

@Composable
fun HeatingReportCard(
    heatingHoursToday: Float,
    energyToday: Float,
    costToday: Float,
    kwhPrice: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Informe de calefacción",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HeatingStatBox(
                    value = "%.1fh".format(heatingHoursToday),
                    label = "Hoy"
                )
                HeatingStatBox(
                    value = "%.1f kWh".format(energyToday),
                    label = "Energía"
                )
                HeatingStatBox(
                    value = "%.2f€".format(costToday),
                    label = "Coste est."
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Estimación: 1.5 kW · %.2f €/kWh".format(kwhPrice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeatingStatBox(
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = WarmColor
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// Existing reusable composables
// ---------------------------------------------------------------------------

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TemperatureChart(
    data: List<TemperatureDataPoint>,
    modifier: Modifier = Modifier
) {
    val primaryColor = Accent
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
        if (started) drawPath(
            setpointPath, warmColor,
            style = Stroke(
                width = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
            )
        )
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
            modifier = Modifier.size(12.dp),
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
