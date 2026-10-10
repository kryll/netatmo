package com.arsys.netatmo.ui.screens.statistics

import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arsys.netatmo.data.local.entities.TemperatureHistoryEntity
import com.arsys.netatmo.domain.model.TemperatureDataPoint
import com.arsys.netatmo.ui.theme.BoilerActiveColor
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainer
import com.arsys.netatmo.ui.theme.SurfaceContainerHigh
import com.arsys.netatmo.ui.theme.SurfaceContainerHighest
import com.arsys.netatmo.ui.theme.SurfaceContainerLow
import java.util.Calendar

// Design token aliases
private val BgColor = Color(0xFF101419)
private val Primary = Color(0xFF93CCFF)
private val Secondary = Color(0xFFF66018)        // orange (secondaryContainer)
private val Tertiary = Color(0xFF62DF7D)          // green
private val TextPrimary = Color(0xFFE0E2EA)
private val TextSecondary = Color(0xFFBFC7D2)
private val GreenGood = Color(0xFF62DF7D)
private val AmberWarn = Color(0xFFF59E0B)
private val RedBad = Color(0xFFFFB4AB)

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 140.dp, // space for fixed header + status row
                start = 16.dp,
                end = 16.dp,
                bottom = 24.dp
            ),
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
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.GridView,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                shape = RoundedCornerShape(50.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = SurfaceContainer,
                                    labelColor = TextSecondary,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
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
                                containerColor = SurfaceContainer,
                                labelColor = TextSecondary,
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
                            color = Primary,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            // Export button
            item {
                Button(
                    onClick = { /* TODO: export */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceContainerHigh,
                        contentColor = TextPrimary
                    ),
                    border = BorderStroke(1.dp, OutlineVariant)
                ) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Exportar informe de consumo (CSV/PDF)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // ── Fixed frosted-glass header ─────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgColor.copy(alpha = 0.85f))
                .statusBarsPadding()
        ) {
            // Main header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo circle + title
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DeviceThermostat,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Netatmo Smart",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Estadísticas",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                }
                // Edit icon
                IconButton(onClick = { viewModel.toggleEditMode() }) {
                    Icon(
                        imageVector = if (uiState.isEditMode) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = if (uiState.isEditMode) "Terminar edición" else "Editar",
                        tint = if (uiState.isEditMode) Primary else TextSecondary
                    )
                }
                // Avatar pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(SurfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "JA",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Sub-header status row
            StatusSubHeader()

            HorizontalDivider(color = OutlineVariant, thickness = 1.dp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-header status row with animated pulse dot
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusSubHeader() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Animated pulse dot
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(GreenGood.copy(alpha = pulseAlpha))
            }
            Text(
                "Actualizado hace 3 min · Mi Casa",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        // Sincronizado chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(GreenGood.copy(alpha = 0.1f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                "Sincronizado",
                style = MaterialTheme.typography.labelSmall,
                color = GreenGood,
                fontWeight = FontWeight.SemiBold
            )
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

    val modifier = if (isEditMode) {
        Modifier
            .fillMaxWidth()
            .border(1.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
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
                tint = TextSecondary,
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
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
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
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = OutlineVariant,
                        focusedLabelColor = Primary,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = Primary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = kwText,
                    onValueChange = { kwText = it },
                    label = { Text("Potencia contratada (kW)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = OutlineVariant,
                        focusedLabelColor = Primary,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = Primary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
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
                Text("Guardar", color = Primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Card A — 24h temperature history (cubic spline, gradient fill, orange setpoint)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TemperatureHistory24hCard(
    points: List<TemperatureHistoryEntity>,
    modifier: Modifier = Modifier
) {
    val labelArgb = TextSecondary.copy(alpha = 0.75f).toArgb()

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
                        "Curva Térmica 24h",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        "Temperatura real vs consigna",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
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
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Legend
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            ) {
                LegendItem(color = Primary, label = "Temperatura real")
                LegendItem(color = Secondary.copy(alpha = 0.85f), label = "Consigna", dashed = true)
            }

            // Chart canvas — cubic spline + gradient area fill
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
                val tempRange = 16f

                fun xOf(ts: Long, startTs: Long, durationMs: Long): Float {
                    if (durationMs <= 0L) return chartLeft
                    return chartLeft + (ts - startTs).toFloat() / durationMs * chartWidth
                }
                fun yOf(temp: Float): Float =
                    chartBottom - (temp - tempMin) / tempRange * chartHeight

                // Grid lines
                listOf(16f, 19f, 22f, 25f).forEach { t ->
                    drawLine(
                        color = OutlineVariant.copy(alpha = 0.5f),
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
                    drawContext.canvas.nativeCanvas.drawText(lbl, chartLeft - 4f, yOf(t) + 8f, yPaint)
                }

                if (points.size >= 2) {
                    val startTs = points.first().timestamp
                    val endTs = points.last().timestamp
                    val durationMs = (endTs - startTs).coerceAtLeast(1L)

                    // Build spline control points
                    val pts = points.map { pt ->
                        Offset(xOf(pt.timestamp, startTs, durationMs), yOf(pt.temperature.toFloat()))
                    }

                    // Cubic spline path (catmull-rom)
                    val splinePath = buildCatmullRomPath(pts)

                    // Area fill with gradient (35% to 0% opacity)
                    val areaPath = Path()
                    areaPath.addPath(splinePath)
                    areaPath.lineTo(pts.last().x, chartBottom)
                    areaPath.lineTo(pts.first().x, chartBottom)
                    areaPath.close()

                    drawPath(
                        areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Primary.copy(alpha = 0.35f), Primary.copy(alpha = 0f)),
                            startY = chartTop,
                            endY = chartBottom
                        )
                    )

                    // Temperature spline line
                    drawPath(splinePath, Primary, style = Stroke(width = 3f, cap = StrokeCap.Round))

                    // Setpoint dashed line (orange = Secondary)
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
                            spPath, Secondary.copy(alpha = 0.85f),
                            style = Stroke(
                                width = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                            )
                        )
                    }

                    // Latest point floating indicator (crosshair dot)
                    val last = pts.last()
                    drawLine(
                        color = Primary.copy(alpha = 0.3f),
                        start = Offset(last.x, chartTop),
                        end = Offset(last.x, chartBottom),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                    )
                    drawCircle(Primary, radius = 6f, center = last)
                    drawCircle(SurfaceContainer, radius = 3f, center = last)
                }

                // X-axis labels — 7 labels (00:00 to 23:59 / 24:00)
                val xPaint = android.graphics.Paint().apply {
                    color = labelArgb
                    textSize = 22f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                listOf("00:00", "04:00", "08:00", "12:00", "16:00", "20:00", "24:00")
                    .forEachIndexed { i, lbl ->
                        val x = chartLeft + i.toFloat() / 6f * chartWidth
                        drawContext.canvas.nativeCanvas.drawText(lbl, x, size.height - 6f, xPaint)
                    }
            }
        }
    }
}

/** Builds a Catmull-Rom spline Path through a list of points. */
private fun buildCatmullRomPath(pts: List<Offset>): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    if (pts.size == 1) { path.moveTo(pts[0].x, pts[0].y); return path }
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 0 until pts.size - 1) {
        val p0 = if (i > 0) pts[i - 1] else pts[i]
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = if (i + 2 < pts.size) pts[i + 2] else pts[i + 1]
        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    return path
}

// ─────────────────────────────────────────────────────────────────────────────
// Card B — Heating report (with trend badges + rich info note + gradient bar)
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
                            .background(Secondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            "Caldera hoy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            "Rendimiento térmico activo",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
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
                            tint = TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            "%.2f €/kWh".format(kwhPrice),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Metric tiles with trend badges
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
                    iconColor = Secondary,
                    badgeText = "+12% vs ayer",
                    badgeColor = Secondary
                )
                HeatingMetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Consumo",
                    value = "%.2f".format(energyToday),
                    unit = "kWh",
                    icon = Icons.Default.Bolt,
                    iconColor = GreenGood,
                    badgeText = "Eficiente",
                    badgeColor = GreenGood
                )
                HeatingMetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Coste",
                    value = "%.2f".format(costToday),
                    unit = "€",
                    icon = Icons.Default.Euro,
                    iconColor = Primary,
                    badgeText = "Tarifa fija",
                    badgeColor = TextSecondary
                )
            }

            // Gradient progress bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Calefacción activa hoy",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        "${(heatingFraction * 100).toInt()}% del día",
                        style = MaterialTheme.typography.labelSmall,
                        color = Secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50.dp))
                        .background(SurfaceContainerHighest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(heatingFraction)
                            .fillMaxHeight()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                        Secondary
                                    )
                                )
                            )
                    )
                }
            }

            // Rich info note with "horas valle" savings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceContainerLow)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Eco,
                    contentDescription = null,
                    tint = GreenGood,
                    modifier = Modifier.size(16.dp).padding(top = 1.dp)
                )
                Text(
                    buildAnnotatedString {
                        append("Aprovecha las ")
                        withStyle(SpanStyle(color = Primary, fontWeight = FontWeight.SemiBold)) {
                            append("horas valle")
                        }
                        append(" para ahorrar hasta ")
                        withStyle(SpanStyle(color = GreenGood, fontWeight = FontWeight.SemiBold)) {
                            append("18% en coste")
                        }
                        append(". Tarifa activa: %.2f €/kWh".format(kwhPrice))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
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
    iconColor: Color,
    badgeText: String,
    badgeColor: Color
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
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(14.dp))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                unit,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        // Trend badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(badgeColor.copy(alpha = 0.12f))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                fontSize = 9.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card C — Stats summary (3 tiles with sublabels, no border)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StatsSummaryRow(
    avgTemp: Double?,
    maxTemp: Double?,
    minTemp: Double?,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Temp. media",
            value = avgTemp?.let { "%.1f°C".format(it) } ?: "--",
            sublabel = "24 horas",
            sublabelColor = TextSecondary,
            icon = Icons.Default.Thermostat,
            iconBgColor = Primary.copy(alpha = 0.15f),
            iconColor = Primary
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Temp. máx.",
            value = maxTemp?.let { "%.1f°C".format(it) } ?: "--",
            sublabel = maxTemp?.let { "15:20 h" } ?: "",
            sublabelColor = Secondary,
            icon = Icons.Default.ThermostatAuto,
            iconBgColor = Secondary.copy(alpha = 0.15f),
            iconColor = Secondary
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Temp. mín.",
            value = minTemp?.let { "%.1f°C".format(it) } ?: "--",
            sublabel = minTemp?.let { "06:10 h" } ?: "",
            sublabelColor = Primary,
            icon = Icons.Default.AcUnit,
            iconBgColor = Primary.copy(alpha = 0.1f),
            iconColor = Primary
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    sublabel: String,
    sublabelColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    iconColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
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
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (sublabel.isNotEmpty()) {
                Text(sublabel, style = MaterialTheme.typography.labelSmall, color = sublabelColor)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card D — Monthly heating with daily bar chart
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MonthlyHeatingCard(
    monthlyHeatingHours: Float,
    monthlyEnergyKwh: Float,
    monthlyCost: Float,
    modifier: Modifier = Modifier
) {
    val cal = Calendar.getInstance()
    val monthNames = arrayOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )
    val monthName = monthNames[cal.get(Calendar.MONTH)]
    val todayDay = cal.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val targetHoursPerDay = 2.5f

    // Synthesize daily data from monthly total
    val dailyHours = remember(monthlyHeatingHours, todayDay) {
        if (monthlyHeatingHours <= 0f || todayDay == 0) {
            List(daysInMonth) { 0f }
        } else {
            val avg = monthlyHeatingHours / todayDay
            List(daysInMonth) { i ->
                if (i < todayDay) (avg * (0.7f + (i % 5) * 0.12f)).coerceIn(0f, 8f) else 0f
            }
        }
    }

    val vsObjective = monthlyHeatingHours - targetHoursPerDay * todayDay

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
                        tint = Primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            "Horas calefacción mes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        if (vsObjective != 0f) {
                            Text(
                                "%+.0fh vs objetivo".format(vsObjective),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (vsObjective < 0f) GreenGood else AmberWarn
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = Secondary.copy(alpha = 0.15f)
                ) {
                    Text(
                        monthName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Secondary,
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.HourglassBottom,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "%.1f h acumuladas este mes".format(monthlyHeatingHours),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                Text(
                    "%.1f kWh · %.2f€".format(monthlyEnergyKwh, monthlyCost),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            // Daily bar chart
            val maxBarH = dailyHours.maxOrNull()?.coerceAtLeast(0.1f) ?: 1f
            val targetLineRatio = (targetHoursPerDay / maxBarH).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                // Bars
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    dailyHours.forEachIndexed { i, hours ->
                        val fraction = (hours / maxBarH).coerceIn(0f, 1f)
                        val isToday = i + 1 == todayDay
                        val barColor = if (isToday) Primary else Secondary.copy(alpha = 0.7f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(fraction.coerceAtLeast(if (i < todayDay) 0.04f else 0f))
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            barColor,
                                            barColor.copy(alpha = 0.4f)
                                        )
                                    )
                                )
                        )
                    }
                }

                // Target line
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val y = size.height * (1f - targetLineRatio)
                    drawLine(
                        color = GreenGood.copy(alpha = 0.6f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                    )
                }
            }

            // Day labels row (abbreviated)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(1, 7, 14, 21, daysInMonth).forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            "$day",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (day == todayDay) Primary else TextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card E — Monthly comparison (tiles + progress bars + AI insight)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MonthlyComparisonCard(
    data: List<MonthlyHeatingData>,
    modifier: Modifier = Modifier
) {
    val maxHours = data.maxOfOrNull { it.heatingHours }?.coerceAtLeast(0.1f) ?: 1f

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
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CompareArrows,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        "Comparativa mensual",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                // Trending indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = GreenGood,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "-12% este mes",
                        style = MaterialTheme.typography.labelSmall,
                        color = GreenGood,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Month tiles (last 3 or all)
            val displayData = if (data.size > 3) data.takeLast(3) else data
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                displayData.forEach { item ->
                    val fraction = (item.heatingHours / maxHours).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "%.1f h".format(item.heatingHours),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        // Mini horizontal progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(50.dp))
                                .background(SurfaceContainerHighest)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(Secondary.copy(alpha = 0.5f), Secondary)
                                        )
                                    )
                            )
                        }
                    }
                }
            }

            // AI insight box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Primary.copy(alpha = 0.07f))
                    .border(1.dp, Primary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Optimización inteligente activa",
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        buildAnnotatedString {
                            append("Has ahorrado un ")
                            withStyle(SpanStyle(color = GreenGood, fontWeight = FontWeight.SemiBold)) {
                                append("12%")
                            }
                            append(" respecto al mes anterior gracias a la programación inteligente.")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
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
                color = TextPrimary
            )
            TemperatureChart(
                data = data,
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem(color = Primary, label = "Temperatura")
                LegendItem(color = Secondary, label = "Objetivo", dashed = true)
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
                color = TextPrimary
            )
            HeatingChart(
                data = data,
                modifier = Modifier.fillMaxWidth().height(80.dp)
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

        repeat(5) { i ->
            val y = size.height * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        val path = Path()
        data.forEachIndexed { idx, pt ->
            val x = xOf(pt.timestamp)
            val y = yOf(pt.temperature)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Primary, style = Stroke(width = 3f, cap = StrokeCap.Round))

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
                spPath, Secondary,
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
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
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
                color = TextPrimary
            )
            Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}
