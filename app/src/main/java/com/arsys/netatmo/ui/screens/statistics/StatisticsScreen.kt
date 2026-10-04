package com.arsys.netatmo.ui.screens.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arsys.netatmo.data.local.entities.TemperatureHistoryEntity
import com.arsys.netatmo.domain.model.TemperatureDataPoint
import com.arsys.netatmo.ui.theme.WarmColor
import java.util.Calendar

private val Accent = Color(0xFF0284C7)
private val TextPrimary = Color(0xFF1E293B)
private val TextSecondary = Color(0xFF64748B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val periods = listOf("24h" to 1, "7 días" to 7, "30 días" to 30, "3 meses" to 90, "6 meses" to 180, "12 meses" to 365)
    var selectedPeriod by remember { mutableStateOf(7) }

    // Price config dialog state
    var showPriceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPeriod, uiState.selectedRoomId) {
        viewModel.loadStatistics(selectedPeriod)
    }

    if (showPriceDialog) {
        PriceConfigDialog(
            currentPrice = uiState.kwhPrice,
            currentKw = uiState.contractedKw,
            onConfirm = { price, kw ->
                viewModel.setPriceConfig(price, kw)
                showPriceDialog = false
            },
            onDismiss = { showPriceDialog = false }
        )
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
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            IconButton(
                onClick = { viewModel.toggleEditMode() },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = if (uiState.isEditMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (uiState.isEditMode) "Terminar edición" else "Editar",
                    tint = if (uiState.isEditMode) Accent else TextSecondary
                )
            }
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

            // Cards in order
            items(uiState.cardOrder) { cardKey ->
                DraggableCardWrapper(
                    cardKey = cardKey,
                    cardOrder = uiState.cardOrder,
                    isEditMode = uiState.isEditMode,
                    onMove = { from, to -> viewModel.moveCard(from, to) }
                ) {
                    when (cardKey) {
                        "24h" -> TemperatureHistory24hCard(
                            points = uiState.historyPoints,
                            modifier = Modifier.fillMaxWidth()
                        )
                        "heating" -> HeatingReportCard(
                            heatingHoursToday = uiState.totalHeatingHoursToday,
                            energyToday = uiState.energyKwhToday,
                            costToday = uiState.estimatedCostToday,
                            kwhPrice = uiState.kwhPrice,
                            contractedKw = uiState.contractedKw,
                            onEditPrice = { showPriceDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                        "summary" -> Row(
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
                        "monthly" -> MonthlyHeatingCard(
                            monthlyHeatingHours = uiState.monthlyHeatingHours,
                            monthlyEnergyKwh = uiState.monthlyEnergyKwh,
                            monthlyCost = uiState.monthlyCost,
                            modifier = Modifier.fillMaxWidth()
                        )
                        "comparison" -> if (uiState.monthlyComparison.isNotEmpty()) {
                            MonthlyComparisonCard(
                                data = uiState.monthlyComparison,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "tempChart" -> if (uiState.temperatureData.isNotEmpty()) {
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
                        "heatingChart" -> if (uiState.temperatureData.isNotEmpty()) {
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

@Composable
fun DraggableCardWrapper(
    cardKey: String,
    cardOrder: List<String>,
    isEditMode: Boolean,
    onMove: (Int, Int) -> Unit,
    content: @Composable () -> Unit
) {
    val currentIndex = cardOrder.indexOf(cardKey)
    var dragOffsetY by remember { mutableStateOf(0f) }

    val modifier = if (isEditMode) {
        Modifier
            .fillMaxWidth()
            .border(1.dp, Accent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .pointerInput(cardKey, cardOrder) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragOffsetY = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY += dragAmount.y
                        // Approximate card height ~120dp = ~320px
                        val cardHeightPx = 320f
                        val steps = (dragOffsetY / cardHeightPx).toInt()
                        if (steps != 0) {
                            val targetIndex = (currentIndex + steps).coerceIn(0, cardOrder.size - 1)
                            if (targetIndex != currentIndex) {
                                onMove(currentIndex, targetIndex)
                                dragOffsetY = 0f
                            }
                        }
                    },
                    onDragEnd = { dragOffsetY = 0f },
                    onDragCancel = { dragOffsetY = 0f }
                )
            }
    } else {
        Modifier.fillMaxWidth()
    }

    if (isEditMode) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Arrastrar",
                tint = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, end = 8.dp)
            )
            Box(modifier = Modifier.weight(1f)) { content() }
        }
    } else {
        Box(modifier = modifier) { content() }
    }
}

@Composable
fun PriceConfigDialog(
    currentPrice: Float,
    currentKw: Float,
    onConfirm: (Float, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var priceText by remember { mutableStateOf(currentPrice.toString()) }
    var kwText by remember { mutableStateOf(currentKw.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configuración de precio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Precio kWh (€)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = kwText,
                    onValueChange = { kwText = it },
                    label = { Text("Potencia contratada (kW)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val price = priceText.replace(",", ".").toFloatOrNull() ?: currentPrice
                val kw = kwText.replace(",", ".").toFloatOrNull() ?: currentKw
                onConfirm(price, kw)
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun MonthlyHeatingCard(
    monthlyHeatingHours: Float,
    monthlyEnergyKwh: Float,
    monthlyCost: Float,
    modifier: Modifier = Modifier
) {
    val cal = Calendar.getInstance()
    val monthNames = arrayOf("Enero","Febrero","Marzo","Abril","Mayo","Junio",
        "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre")
    val monthName = monthNames[cal.get(Calendar.MONTH)]

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Resumen mensual — $monthName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HeatingStatBox(
                    value = "%.1fh".format(monthlyHeatingHours),
                    label = "Horas calef."
                )
                HeatingStatBox(
                    value = "%.1f kWh".format(monthlyEnergyKwh),
                    label = "Energía"
                )
                HeatingStatBox(
                    value = "%.2f€".format(monthlyCost),
                    label = "Coste est."
                )
            }
        }
    }
}

@Composable
fun MonthlyComparisonCard(
    data: List<MonthlyHeatingData>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Comparativa mensual",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                if (data.isEmpty()) return@Canvas
                val maxHours = data.maxOf { it.heatingHours }.coerceAtLeast(0.1f)
                val bottomPad = 36f
                val chartBottom = size.height - bottomPad
                val chartHeight = chartBottom
                val barWidth = size.width / data.size
                val barPad = barWidth * 0.15f

                val barColor = android.graphics.Color.argb(220, 2, 132, 199)
                val labelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(200, 100, 116, 139)
                    textSize = 26f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                data.forEachIndexed { i, item ->
                    val barH = (item.heatingHours / maxHours) * chartHeight
                    val left = i * barWidth + barPad
                    val right = (i + 1) * barWidth - barPad
                    val top = chartBottom - barH

                    drawRect(
                        color = Accent,
                        topLeft = Offset(left, top),
                        size = Size(right - left, barH)
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        item.label,
                        left + (right - left) / 2f,
                        size.height - 6f,
                        labelPaint
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Horas de calefacción por mes",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
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
                val leftPad = 40f
                val bottomPad = 26f
                val chartLeft = leftPad
                val chartRight = size.width
                val chartTop = 0f
                val chartBottom = size.height - bottomPad
                val chartWidth = chartRight - chartLeft
                val chartHeight = chartBottom - chartTop

                val tempMin = 14f
                val tempRange = 16f

                fun xOf(ts: Long, startTs: Long, durationMs: Long): Float {
                    if (durationMs <= 0L) return chartLeft
                    return chartLeft + (ts - startTs).toFloat() / durationMs * chartWidth
                }

                fun yOf(temp: Float): Float =
                    chartBottom - (temp - tempMin) / tempRange * chartHeight

                listOf(16f, 18f, 20f, 22f, 24f).forEach { gridTemp ->
                    val y = yOf(gridTemp)
                    drawLine(
                        color = gridColor,
                        start = Offset(chartLeft, y),
                        end = Offset(chartRight, y),
                        strokeWidth = 1.5f
                    )
                }

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

                if (points.size >= 2) {
                    val startTs = points.first().timestamp
                    val endTs = points.last().timestamp
                    val durationMs = (endTs - startTs).coerceAtLeast(1L)

                    val tempPath = Path()
                    points.forEachIndexed { index, point ->
                        val x = xOf(point.timestamp, startTs, durationMs)
                        val y = yOf(point.temperature.toFloat())
                        if (index == 0) tempPath.moveTo(x, y) else tempPath.lineTo(x, y)
                    }
                    drawPath(tempPath, tempColor, style = Stroke(width = 3f))

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatingReportCard(
    heatingHoursToday: Float,
    energyToday: Float,
    costToday: Float,
    kwhPrice: Float,
    contractedKw: Float,
    onEditPrice: () -> Unit,
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
            // Clickable row to open price config
            Surface(
                onClick = onEditPrice,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "%.1f kW · %.2f €/kWh".format(contractedKw, kwhPrice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar precio",
                        tint = Accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
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

        val path = Path()
        data.forEachIndexed { index, point ->
            val x = xOf(point.timestamp)
            val y = yOf(point.temperature)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, primaryColor, style = Stroke(width = 3f))

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
                    size = Size(x2 - x1, size.height)
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
