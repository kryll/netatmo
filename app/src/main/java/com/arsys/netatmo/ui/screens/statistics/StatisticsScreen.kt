package com.arsys.netatmo.ui.screens.statistics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arsys.netatmo.data.local.entities.TemperatureHistoryEntity
import com.arsys.netatmo.domain.model.TemperatureDataPoint
import com.arsys.netatmo.ui.theme.BoilerActiveColor
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainer
import com.arsys.netatmo.ui.theme.SurfaceContainerHigh
import com.arsys.netatmo.ui.theme.SurfaceContainerHighest
import com.arsys.netatmo.ui.theme.SurfaceContainerLow
import com.arsys.netatmo.ui.theme.WarmColor
import java.util.Calendar

// ─────────────────────────────────────────────────────────────────────────────
// Main screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val periods = listOf(
        "24h" to 1, "7d" to 7, "30d" to 30,
        "90d" to 90, "180d" to 180, "365d" to 365
    )
    var selectedPeriod by remember { mutableStateOf(7) }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Text(
                text = "Estadísticas",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            IconButton(
                onClick = { viewModel.toggleEditMode() },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = if (uiState.isEditMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (uiState.isEditMode) "Terminar edición" else "Editar",
                    tint = if (uiState.isEditMode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = OutlineVariant, thickness = 1.dp)

        // ── Scrollable content ─────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Room chips
            if (uiState.availableRooms.size > 1) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(uiState.availableRooms) { (roomId, roomName) ->
                            val selected = uiState.selectedRoomId == roomId
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectRoom(roomId) },
                                label = {
                                    Text(roomName, style = MaterialTheme.typography.labelMedium)
                                },
                                shape = RoundedCornerShape(50.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = SurfaceContainerHigh,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }

            // Period chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(periods) { (label, days) ->
                        val selected = selectedPeriod == days
                        FilterChip(
                            selected = selected,
                            onClick = { selectedPeriod = days },
                            label = {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(50.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = SurfaceContainerHigh,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // Draggable cards
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
                        "summary" -> StatsSummaryRow(
                            avgTemp = uiState.avgTemp,
                            maxTemp = uiState.maxTemp,
                            minTemp = uiState.minTemp,
                            modifier = Modifier.fillMaxWidth()
                        )
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
                            TemperatureChartCard(
                                data = uiState.temperatureData,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "heatingChart" -> if (uiState.temperatureData.isNotEmpty()) {
                            HeatingChartCard(
                                data = uiState.temperatureData,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Draggable wrapper
// ─────────────────────────────────────────────────────────────────────────────

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
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    val modifier = if (isEditMode) {
        Modifier
            .fillMaxWidth()
            .border(1.dp, primaryColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .pointerInput(cardKey, cardOrder) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragOffsetY = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY += dragAmount.y
                        val cardHeightPx = 320f
                        val steps = (dragOffsetY / cardHeightPx).toInt()
                        if (steps != 0) {
                            val target = (currentIndex + steps).coerceIn(0, cardOrder.size - 1)
                            if (target != currentIndex) {
                                onMove(currentIndex, target)
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
                tint = onSurfaceVariantColor,
                modifier = Modifier.padding(start = 4.dp, end = 8.dp)
            )
            Box(modifier = Modifier.weight(1f)) { content() }
        }
    } else {
        Box(modifier = modifier) { content() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Price config dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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
        containerColor = SurfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(
                "Configuración de precio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Precio kWh (€)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = OutlineVariant,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                OutlinedTextField(
                    value = kwText,
                    onValueChange = { kwText = it },
                    label = { Text("Potencia contratada (kW)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = OutlineVariant,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val price = priceText.replace(",", ".").toFloatOrNull() ?: currentPrice
                val kw = kwText.replace(",", ".").toFloatOrNull() ?: currentKw
                onConfirm(price, kw)
            }) {
                Text(
                    "Guardar",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Card A — 24h temperature history
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TemperatureHistory24hCard(
    points: List<TemperatureHistoryEntity>,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val gridColor = SurfaceContainerHigh
    val surfaceColor = SurfaceContainer
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f).toArgb()

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        "Curva Térmica (24h)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Temperatura real vs consigna",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ShowChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Legend
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            ) {
                LegendItem(color = primary, label = "Temperatura real")
                LegendItem(color = errorColor.copy(alpha = 0.75f), label = "Consigna", dashed = true)
            }

            // Chart canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val leftPad = 38f
                val bottomPad = 28f
                val chartLeft = leftPad
                val chartRight = size.width
                val chartTop = 4f
                val chartBottom = size.height - bottomPad
                val chartWidth = chartRight - chartLeft
                val chartHeight = chartBottom - chartTop

                val tempMin = 14f
                val tempRange = 16f   // spans 14–30 °C

                fun xOf(ts: Long, startTs: Long, durationMs: Long): Float {
                    if (durationMs <= 0L) return chartLeft
                    return chartLeft + (ts - startTs).toFloat() / durationMs * chartWidth
                }

                fun yOf(temp: Float): Float =
                    chartBottom - (temp - tempMin) / tempRange * chartHeight

                // Grid lines
                listOf(16f, 19f, 22f, 25f).forEach { t ->
                    drawLine(
                        color = gridColor,
                        start = Offset(chartLeft, yOf(t)),
                        end = Offset(chartRight, yOf(t)),
                        strokeWidth = 1f
                    )
                }

                // Y-axis labels
                val yPaint = android.graphics.Paint().apply {
                    color = labelArgb
                    textSize = 24f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                listOf("16°" to 16f, "19°" to 19f, "22°" to 22f, "25°" to 25f).forEach { (lbl, t) ->
                    drawContext.canvas.nativeCanvas.drawText(
                        lbl, chartLeft - 4f, yOf(t) + 8f, yPaint
                    )
                }

                if (points.size >= 2) {
                    val startTs = points.first().timestamp
                    val endTs = points.last().timestamp
                    val durationMs = (endTs - startTs).coerceAtLeast(1L)

                    // Area under the temp line
                    val areaPath = Path()
                    points.forEachIndexed { idx, pt ->
                        val x = xOf(pt.timestamp, startTs, durationMs)
                        val y = yOf(pt.temperature.toFloat())
                        if (idx == 0) areaPath.moveTo(x, y) else areaPath.lineTo(x, y)
                    }
                    areaPath.lineTo(xOf(points.last().timestamp, startTs, durationMs), chartBottom)
                    areaPath.lineTo(xOf(points.first().timestamp, startTs, durationMs), chartBottom)
                    areaPath.close()
                    drawPath(areaPath, primary.copy(alpha = 0.1f))

                    // Temperature line
                    val tempPath = Path()
                    points.forEachIndexed { idx, pt ->
                        val x = xOf(pt.timestamp, startTs, durationMs)
                        val y = yOf(pt.temperature.toFloat())
                        if (idx == 0) tempPath.moveTo(x, y) else tempPath.lineTo(x, y)
                    }
                    drawPath(
                        tempPath, primary,
                        style = Stroke(width = 3f, cap = StrokeCap.Round)
                    )

                    // Setpoint dashed
                    val spPath = Path()
                    var spStarted = false
                    points.forEach { pt ->
                        pt.setpoint?.let { sp ->
                            val x = xOf(pt.timestamp, startTs, durationMs)
                            val y = yOf(sp.toFloat())
                            if (!spStarted) { spPath.moveTo(x, y); spStarted = true }
                            else spPath.lineTo(x, y)
                        }
                    }
                    if (spStarted) {
                        drawPath(
                            spPath, errorColor.copy(alpha = 0.75f),
                            style = Stroke(
                                width = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                            )
                        )
                    }

                    // Dot markers (sparse)
                    val step = maxOf(1, points.size / 24)
                    points.forEachIndexed { idx, pt ->
                        if (idx % step == 0) {
                            val x = xOf(pt.timestamp, startTs, durationMs)
                            val y = yOf(pt.temperature.toFloat())
                            drawCircle(primary, radius = 4f, center = Offset(x, y))
                            drawCircle(surfaceColor, radius = 2f, center = Offset(x, y))
                        }
                    }
                }

                // X-axis labels
                val xPaint = android.graphics.Paint().apply {
                    color = labelArgb
                    textSize = 24f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEachIndexed { i, lbl ->
                    val x = chartLeft + i.toFloat() / 4f * chartWidth
                    drawContext.canvas.nativeCanvas.drawText(lbl, x, size.height - 6f, xPaint)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card B — Heating report
// ─────────────────────────────────────────────────────────────────────────────

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
    val heatingFraction = (heatingHoursToday / 24f).coerceIn(0f, 1f)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(BoilerActiveColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = BoilerActiveColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            "Caldera hoy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Rendimiento térmico activo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Price pill button
                Surface(
                    onClick = onEditPrice,
                    shape = RoundedCornerShape(50.dp),
                    color = SurfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Configurar precio",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            "%.2f €/kWh".format(kwhPrice),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Metric tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeatingMetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Tiempo",
                    value = "%.1f".format(heatingHoursToday),
                    unit = "h",
                    icon = Icons.Default.Timer,
                    iconColor = MaterialTheme.colorScheme.secondaryContainer
                )
                HeatingMetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Consumo",
                    value = "%.2f".format(energyToday),
                    unit = "kWh",
                    icon = Icons.Default.Bolt,
                    iconColor = MaterialTheme.colorScheme.tertiary
                )
                HeatingMetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Coste",
                    value = "%.2f".format(costToday),
                    unit = "€",
                    icon = Icons.Default.Euro,
                    iconColor = MaterialTheme.colorScheme.primary
                )
            }

            // Progress bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Calefacción activa hoy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${(heatingFraction * 100).toInt()}% del día",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                LinearProgressIndicator(
                    progress = { heatingFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50.dp)),
                    color = BoilerActiveColor,
                    trackColor = SurfaceContainerHighest
                )
            }

            // Info note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceContainerLow)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Eco,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "%.1f kW contratados · %.2f €/kWh".format(contractedKw, kwhPrice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HeatingMetricTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceContainerHigh)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(14.dp))
        }
        Row(verticalAlignment = Alignment.Baseline, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                unit,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card C — Stats summary (3 tiles)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StatsSummaryRow(
    avgTemp: Double?,
    maxTemp: Double?,
    minTemp: Double?,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceTint = MaterialTheme.colorScheme.surfaceTint

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Temp. media",
            value = avgTemp?.let { "%.1f°C".format(it) } ?: "--",
            icon = Icons.Default.Thermostat,
            iconBgColor = primary.copy(alpha = 0.15f),
            iconColor = primary
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Temp. máx.",
            value = maxTemp?.let { "%.1f°C".format(it) } ?: "--",
            icon = Icons.Default.ThermostatAuto,
            iconBgColor = WarmColor.copy(alpha = 0.15f),
            iconColor = WarmColor
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Temp. mín.",
            value = minTemp?.let { "%.1f°C".format(it) } ?: "--",
            icon = Icons.Default.AcUnit,
            iconBgColor = surfaceTint.copy(alpha = 0.15f),
            iconColor = surfaceTint
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    iconColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(17.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card D — Monthly heating summary
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MonthlyHeatingCard(
    monthlyHeatingHours: Float,
    monthlyEnergyKwh: Float,
    monthlyCost: Float,
    modifier: Modifier = Modifier
) {
    val monthNames = arrayOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )
    val monthName = monthNames[Calendar.getInstance().get(Calendar.MONTH)]

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Resumen mensual",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                ) {
                    Text(
                        monthName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Accumulated hours tile
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.HourglassBottom,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "%.1f h acumuladas este mes".format(monthlyHeatingHours),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 3 stat boxes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MonthlyStatBox(
                    value = "%.1fh".format(monthlyHeatingHours),
                    label = "Horas calef.",
                    color = WarmColor
                )
                MonthlyStatBox(
                    value = "%.1f kWh".format(monthlyEnergyKwh),
                    label = "Energía",
                    color = MaterialTheme.colorScheme.tertiary
                )
                MonthlyStatBox(
                    value = "%.2f€".format(monthlyCost),
                    label = "Coste est.",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MonthlyStatBox(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card E — Monthly comparison bar chart
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MonthlyComparisonCard(
    data: List<MonthlyHeatingData>,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.secondaryContainer
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f).toArgb()

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CompareArrows,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    "Comparativa mensual",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Bar chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                if (data.isEmpty()) return@Canvas
                val maxHours = data.maxOf { it.heatingHours }.coerceAtLeast(0.1f)
                val bottomPad = 36f
                val chartBottom = size.height - bottomPad
                val barWidth = size.width / data.size
                val barPad = barWidth * 0.2f

                val labelPaint = android.graphics.Paint().apply {
                    color = labelArgb
                    textSize = 24f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                data.forEachIndexed { i, item ->
                    val barH = (item.heatingHours / maxHours) * chartBottom * 0.85f
                    val left = i * barWidth + barPad
                    val right = (i + 1) * barWidth - barPad
                    val top = chartBottom - barH
                    val isLatest = i == data.size - 1

                    drawRect(
                        color = if (isLatest) primary else barColor,
                        topLeft = Offset(left, top),
                        size = Size(right - left, barH)
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        item.label,
                        left + (right - left) / 2f,
                        size.height - 8f,
                        labelPaint
                    )
                }
            }

            Text(
                "Horas de calefacción por mes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Temperature chart card (long period)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TemperatureChartCard(
    data: List<TemperatureDataPoint>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Temperatura",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TemperatureChart(
                data = data,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem(color = MaterialTheme.colorScheme.primary, label = "Temperatura")
                LegendItem(color = WarmColor, label = "Objetivo", dashed = true)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Heating activity chart card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HeatingChartCard(
    data: List<TemperatureDataPoint>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Actividad de calefacción",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            HeatingChart(
                data = data,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable chart primitives
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TemperatureChart(
    data: List<TemperatureDataPoint>,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val gridColor = SurfaceContainerHigh

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

        fun yOf(temp: Double) =
            size.height - ((temp.toFloat() - minTemp) / tempRange) * size.height

        // Subtle grid
        repeat(5) { i ->
            val y = size.height * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        // Temperature line
        val path = Path()
        data.forEachIndexed { idx, pt ->
            val x = xOf(pt.timestamp)
            val y = yOf(pt.temperature)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, primary, style = Stroke(width = 3f, cap = StrokeCap.Round))

        // Setpoint dashed
        val spPath = Path()
        var started = false
        data.forEach { pt ->
            pt.setpoint?.let { sp ->
                val x = xOf(pt.timestamp)
                val y = yOf(sp)
                if (!started) { spPath.moveTo(x, y); started = true }
                else spPath.lineTo(x, y)
            }
        }
        if (started) {
            drawPath(
                spPath, WarmColor,
                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f)))
            )
        }
    }
}

@Composable
fun HeatingChart(
    data: List<TemperatureDataPoint>,
    modifier: Modifier = Modifier
) {
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
                    color = BoilerActiveColor.copy(alpha = 0.6f),
                    topLeft = Offset(x1, 0f),
                    size = Size(x2 - x1, size.height)
                )
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String, dashed: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (dashed) {
            Canvas(modifier = Modifier.size(width = 16.dp, height = 3.dp)) {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))
                )
            }
        } else {
            Canvas(modifier = Modifier.size(10.dp)) { drawCircle(color) }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Legacy StatCard — kept for compatibility
// ─────────────────────────────────────────────────────────────────────────────

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
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
            }
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
