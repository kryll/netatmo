package com.arsys.netatmo.ui.screens.statistics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.arsys.netatmo.data.local.entities.MaintenanceRecordEntity
import com.arsys.netatmo.data.local.entities.TemperatureHistoryEntity
import com.arsys.netatmo.domain.model.TemperatureDataPoint
import com.arsys.netatmo.domain.model.ThermostatType
import com.arsys.netatmo.ui.theme.BoilerActiveColor
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainer
import com.arsys.netatmo.ui.theme.SurfaceContainerHigh
import com.arsys.netatmo.ui.theme.SurfaceContainerHighest
import com.arsys.netatmo.ui.theme.SurfaceContainerLow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
            .background(BgColor)
    ) {
        // ── Fixed header (non-scrollable) ─────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgColor.copy(alpha = 0.95f))
                .statusBarsPadding()
        ) {
            // Main header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                IconButton(onClick = { viewModel.refresh(selectedPeriod) }, enabled = !uiState.isLoading) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Recargar estadísticas",
                        tint = TextSecondary
                    )
                }
                IconButton(onClick = { viewModel.toggleEditMode() }) {
                    Icon(
                        imageVector = if (uiState.isEditMode) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = if (uiState.isEditMode) "Terminar edición" else "Editar",
                        tint = if (uiState.isEditMode) Primary else TextSecondary
                    )
                }
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

            StatusSubHeader()

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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

            HorizontalDivider(color = OutlineVariant, thickness = 1.dp)
        }

        // ── Scrollable content ────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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

            val effectiveCardOrder = uiState.cardOrder.filter { key ->
                key != "tempChart" || selectedPeriod == 1
            }
            items(effectiveCardOrder) { cardKey ->
                DraggableCardWrapper(
                    cardKey = cardKey,
                    cardOrder = effectiveCardOrder,
                    isEditMode = uiState.isEditMode,
                    onMove = { from, to -> viewModel.moveCard(from, to) }
                ) {
                    when (cardKey) {
                        "24h" -> if (selectedPeriod == 1) {
                            TemperatureHistory24hCard(
                                points = uiState.historyPoints,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (uiState.temperatureData.isNotEmpty()) {
                            TemperatureChartCard(
                                data = uiState.temperatureData,
                                periodLabel = periods.firstOrNull { it.second == selectedPeriod }?.first ?: "${selectedPeriod}d",
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (!uiState.isLoading) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                                border = BorderStroke(1.dp, OutlineVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Sin datos para el período seleccionado",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
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
                            periodLabel = periods.firstOrNull { it.second == selectedPeriod }?.first ?: "${selectedPeriod}d",
                            maxTempTime = uiState.maxTempTime,
                            minTempTime = uiState.minTempTime,
                            modifier = Modifier.fillMaxWidth()
                        )
                        "monthly" -> MonthlyHeatingCard(
                            monthlyHeatingHours = uiState.monthlyHeatingHours,
                            monthlyEnergyKwh = uiState.monthlyEnergyKwh,
                            monthlyCost = uiState.monthlyCost,
                            dailyHours = uiState.dailyHeatingHours,
                            modifier = Modifier.fillMaxWidth()
                        )
                        "comparison" -> if (uiState.monthlyComparison.isNotEmpty()) {
                            MonthlyComparisonCard(
                                data = uiState.monthlyComparison,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "tempChart" -> if (selectedPeriod == 1 && uiState.temperatureData.isNotEmpty()) {
                            TemperatureChartCard(
                                data = uiState.temperatureData,
                                periodLabel = "24h",
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

            if (uiState.thermostatType == ThermostatType.OPENTHERM) {
                item {
                    OpenThermMetricsCard(modifier = Modifier.fillMaxWidth())
                }
            }

            item {
                BoilerRevisionSummaryCard(
                    revision = uiState.latestRevision,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Button(
                    onClick = {
                        scope.launch {
                            val uri = exportTemperatureCsv(context, uiState.temperatureData)
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Netatmo - temperaturas")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Exportar temperaturas"))
                            } else {
                                Toast.makeText(context, "No se pudo exportar el CSV", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = uiState.temperatureData.isNotEmpty(),
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
                        "Exportar temperaturas (CSV)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

private suspend fun exportTemperatureCsv(context: Context, data: List<TemperatureDataPoint>): Uri? =
    withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
            val file = File(dir, "netatmo_temperaturas_${System.currentTimeMillis()}.csv")
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            file.bufferedWriter().use { w ->
                w.write("timestamp_ms,fecha,temperatura_c,consigna_c,calefaccion_activa\n")
                data.forEach { p ->
                    w.write("${p.timestamp},${fmt.format(Date(p.timestamp))},${p.temperature},${p.setpoint ?: ""},${p.heatingActive}\n")
                }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            null
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

            val xAxisLabels = remember(points) {
                if (points.size < 2) emptyList<Pair<String, Long>>()
                else {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = points.first().timestamp
                    cal.set(Calendar.HOUR_OF_DAY, (cal.get(Calendar.HOUR_OF_DAY) / 4) * 4)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val endTs = points.last().timestamp
                    val result = mutableListOf<Pair<String, Long>>()
                    while (cal.timeInMillis <= endTs + 3_600_000L) {
                        result.add("%02d:00".format(cal.get(Calendar.HOUR_OF_DAY)) to cal.timeInMillis)
                        cal.add(Calendar.HOUR_OF_DAY, 4)
                    }
                    result
                }
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

                // X-axis labels — derived from actual data timestamps, every 4 hours
                val xPaint = android.graphics.Paint().apply {
                    color = labelArgb
                    textSize = 22f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                if (points.size >= 2) {
                    val startTs2 = points.first().timestamp
                    val durationMs2 = (points.last().timestamp - startTs2).coerceAtLeast(1L)
                    xAxisLabels.forEach { (lbl, ts) ->
                        val x = xOf(ts, startTs2, durationMs2)
                        drawContext.canvas.nativeCanvas.drawText(lbl, x, size.height - 6f, xPaint)
                    }
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
                    badgeText = if (heatingHoursToday > 0f) "%.0f%% del día".format((heatingHoursToday / 24f * 100f).coerceIn(0f, 100f)) else null,
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
    badgeText: String?,
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
        if (badgeText != null) {
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
}

// ─────────────────────────────────────────────────────────────────────────────
// Card C — Stats summary (3 tiles with sublabels, no border)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StatsSummaryRow(
    avgTemp: Double?,
    maxTemp: Double?,
    minTemp: Double?,
    periodLabel: String = "24 horas",
    maxTempTime: Long? = null,
    minTempTime: Long? = null,
    modifier: Modifier = Modifier
) {
    fun formatTime(ts: Long?): String {
        if (ts == null) return ""
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        return "%02d:%02d h".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Temp. media",
            value = avgTemp?.let { "%.1f°C".format(it) } ?: "--",
            sublabel = periodLabel,
            sublabelColor = TextSecondary,
            icon = Icons.Default.Thermostat,
            iconBgColor = Primary.copy(alpha = 0.15f),
            iconColor = Primary
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Temp. máx.",
            value = maxTemp?.let { "%.1f°C".format(it) } ?: "--",
            sublabel = formatTime(maxTempTime),
            sublabelColor = Secondary,
            icon = Icons.Default.ThermostatAuto,
            iconBgColor = Secondary.copy(alpha = 0.15f),
            iconColor = Secondary
        )
        StatTile(
            modifier = Modifier.weight(1f),
            label = "Temp. mín.",
            value = minTemp?.let { "%.1f°C".format(it) } ?: "--",
            sublabel = formatTime(minTempTime),
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
    dailyHours: List<Float>,
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

    // Real per-day hours measured from local samples; days without samples stay at 0
    val daily = List(daysInMonth) { i -> dailyHours.getOrElse(i) { 0f } }

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

            // Daily bar chart (with Y axis)
            val rawMax = maxOf(daily.maxOrNull() ?: 0f, targetHoursPerDay)
            val yMax = when {
                rawMax <= 1f -> 1f
                rawMax <= 2f -> 2f
                rawMax <= 4f -> 4f
                rawMax <= 8f -> 8f
                rawMax <= 12f -> 12f
                else -> kotlin.math.ceil((rawMax / 4f).toDouble()).toFloat() * 4f
            }
            val targetLineRatio = (targetHoursPerDay / yMax).coerceIn(0f, 1f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                // Y-axis labels (hours)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(26.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text("%.0fh".format(yMax), style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 9.sp)
                    Text("%.0fh".format(yMax / 2f), style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 9.sp)
                    Text("0h", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 9.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Bars
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        daily.forEachIndexed { i, hours ->
                            val fraction = (hours / yMax).coerceIn(0f, 1f)
                            val isToday = i + 1 == todayDay
                            val barColor = if (isToday) Primary else Secondary.copy(alpha = 0.7f)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(if (hours > 0f) fraction.coerceAtLeast(0.04f) else 0f)
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
            }

            // Day labels row (abbreviated, aligned with the bars)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp),
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
                // Trending indicator — computed from last two months
                if (data.size >= 2) {
                    val prev = data[data.size - 2].heatingHours
                    val curr = data.last().heatingHours
                    val pct = if (prev > 0f) ((curr - prev) / prev * 100f) else 0f
                    val trendDown = pct <= 0f
                    val trendColor = if (trendDown) GreenGood else Secondary
                    val trendIcon = if (trendDown) Icons.Default.TrendingDown else Icons.Default.TrendingUp
                    val sign = if (pct >= 0f) "+" else ""
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            trendIcon,
                            contentDescription = null,
                            tint = trendColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "$sign%.0f%% vs mes ant.".format(pct),
                            style = MaterialTheme.typography.labelSmall,
                            color = trendColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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
    periodLabel: String = "",
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
                if (periodLabel.isNotEmpty()) "Temperatura · $periodLabel" else "Temperatura",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            TemperatureChart(
                data = data,
                modifier = Modifier.fillMaxWidth().height(220.dp)
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
                modifier = Modifier.fillMaxWidth().height(110.dp)
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
    // Pre-compute X-axis tick labels from timestamps (outside Canvas, needs Compose context)
    val xLabels: List<Pair<Float, String>> = remember(data) {
        if (data.size < 2) return@remember emptyList()
        val minTs = data.first().timestamp
        val maxTs = data.last().timestamp
        val spanMs = (maxTs - minTs).coerceAtLeast(1L)
        val spanDays = spanMs / (24 * 60 * 60 * 1000L)
        val cal = Calendar.getInstance()
        val dayNames = listOf("dom", "lun", "mar", "mié", "jue", "vie", "sáb")
        val monthAbbr = listOf("ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic")
        val ticks = mutableListOf<Pair<Float, String>>()
        when {
            spanDays <= 8 -> {
                cal.timeInMillis = minTs
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                while (cal.timeInMillis <= maxTs) {
                    val fraction = (cal.timeInMillis - minTs).toFloat() / spanMs
                    ticks.add(fraction.coerceIn(0f, 1f) to dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1])
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            spanDays <= 60 -> {
                cal.timeInMillis = minTs
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                while (cal.timeInMillis <= maxTs) {
                    val fraction = (cal.timeInMillis - minTs).toFloat() / spanMs
                    val label = "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}"
                    ticks.add(fraction.coerceIn(0f, 1f) to label)
                    cal.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }
            else -> {
                cal.timeInMillis = minTs
                cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                while (cal.timeInMillis <= maxTs) {
                    val fraction = (cal.timeInMillis - minTs).toFloat() / spanMs
                    ticks.add(fraction.coerceIn(0f, 1f) to monthAbbr[cal.get(Calendar.MONTH)])
                    cal.add(Calendar.MONTH, 1)
                }
            }
        }
        ticks
    }

    val labelArgb = TextSecondary.copy(alpha = 0.75f).toArgb()

    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas

        val leftPad = 38f
        val bottomPad = 28f
        val chartLeft = leftPad
        val chartRight = size.width
        val chartTop = 4f
        val chartBottom = size.height - bottomPad
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        val minTs = data.first().timestamp
        val maxTs = data.last().timestamp
        val timeRange = (maxTs - minTs).toFloat().coerceAtLeast(1f)

        val rawMin = data.minOf { it.temperature }.toFloat()
        val rawMax = data.maxOf { it.temperature }.toFloat()
        val pad = ((rawMax - rawMin) * 0.1f).coerceAtLeast(1f)
        val tempMin = rawMin - pad
        val tempMax = rawMax + pad
        val tempRange = (tempMax - tempMin).coerceAtLeast(0.1f)

        fun xOf(ts: Long): Float = chartLeft + (ts - minTs).toFloat() / timeRange * chartWidth
        fun yOf(temp: Double): Float = chartBottom - ((temp.toFloat() - tempMin) / tempRange) * chartHeight

        // Nice Y ticks
        val rawStep = (tempMax - tempMin) / 4f
        val niceStep = when {
            rawStep <= 0.5f -> 0.5f
            rawStep <= 1f -> 1f
            rawStep <= 2f -> 2f
            rawStep <= 5f -> 5f
            else -> 10f
        }
        val yTicks = mutableListOf<Float>()
        var tickVal = ((tempMin / niceStep).toInt()) * niceStep
        while (tickVal <= tempMax + niceStep * 0.1f) {
            if (tickVal >= tempMin - niceStep * 0.1f) yTicks.add(tickVal)
            tickVal += niceStep
            if (yTicks.size > 8) break
        }

        // Grid lines
        yTicks.forEach { t ->
            drawLine(
                color = OutlineVariant.copy(alpha = 0.4f),
                start = Offset(chartLeft, yOf(t.toDouble())),
                end = Offset(chartRight, yOf(t.toDouble())),
                strokeWidth = 1f
            )
        }

        // Y-axis labels
        val yPaint = android.graphics.Paint().apply {
            color = labelArgb; textSize = 24f; isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        yTicks.forEach { t ->
            drawContext.canvas.nativeCanvas.drawText(
                if (niceStep < 1f) "%.1f°".format(t) else "${t.toInt()}°", chartLeft - 4f, yOf(t.toDouble()) + 8f, yPaint
            )
        }

        // Temperature spline + gradient fill
        val pts = data.map { pt -> Offset(xOf(pt.timestamp), yOf(pt.temperature)) }
        val splinePath = buildCatmullRomPath(pts)
        val areaPath = Path()
        areaPath.addPath(splinePath)
        areaPath.lineTo(pts.last().x, chartBottom)
        areaPath.lineTo(pts.first().x, chartBottom)
        areaPath.close()
        drawPath(
            areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(Primary.copy(alpha = 0.3f), Primary.copy(alpha = 0f)),
                startY = chartTop, endY = chartBottom
            )
        )
        drawPath(splinePath, Primary, style = Stroke(width = 3f, cap = StrokeCap.Round))

        // Setpoint dashed line
        val spPath = Path()
        var spStarted = false
        data.forEach { pt ->
            pt.setpoint?.let { sp ->
                val x = xOf(pt.timestamp); val y = yOf(sp)
                if (!spStarted) { spPath.moveTo(x, y); spStarted = true } else spPath.lineTo(x, y)
            }
        }
        if (spStarted) {
            drawPath(spPath, Secondary.copy(alpha = 0.85f),
                style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
        }

        // X-axis labels
        val xPaint = android.graphics.Paint().apply {
            color = labelArgb; textSize = 22f; isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        xLabels.forEach { (fraction, label) ->
            val x = chartLeft + fraction * chartWidth
            if (x in chartLeft..chartRight) {
                drawContext.canvas.nativeCanvas.drawText(label, x, size.height - 6f, xPaint)
            }
        }
    }
}

@Composable
fun HeatingChart(
    data: List<TemperatureDataPoint>,
    modifier: Modifier = Modifier
) {
    // Pre-compute X-axis tick labels from timestamps (same logic as TemperatureChart)
    val xLabels: List<Pair<Float, String>> = remember(data) {
        if (data.size < 2) return@remember emptyList()
        val minTs = data.first().timestamp
        val maxTs = data.last().timestamp
        val spanMs = (maxTs - minTs).coerceAtLeast(1L)
        val spanDays = spanMs / (24 * 60 * 60 * 1000L)
        val cal = Calendar.getInstance()
        val dayNames = listOf("dom", "lun", "mar", "mié", "jue", "vie", "sáb")
        val monthAbbr = listOf("ene","feb","mar","abr","may","jun","jul","ago","sep","oct","nov","dic")
        val ticks = mutableListOf<Pair<Float, String>>()
        when {
            spanDays <= 8 -> {
                cal.timeInMillis = minTs
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                while (cal.timeInMillis <= maxTs) {
                    val fraction = (cal.timeInMillis - minTs).toFloat() / spanMs
                    ticks.add(fraction.coerceIn(0f, 1f) to dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1])
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }
            spanDays <= 60 -> {
                cal.timeInMillis = minTs
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                while (cal.timeInMillis <= maxTs) {
                    val fraction = (cal.timeInMillis - minTs).toFloat() / spanMs
                    val label = "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}"
                    ticks.add(fraction.coerceIn(0f, 1f) to label)
                    cal.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }
            else -> {
                cal.timeInMillis = minTs
                cal.set(Calendar.DAY_OF_MONTH, 1); cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                while (cal.timeInMillis <= maxTs) {
                    val fraction = (cal.timeInMillis - minTs).toFloat() / spanMs
                    ticks.add(fraction.coerceIn(0f, 1f) to monthAbbr[cal.get(Calendar.MONTH)])
                    cal.add(Calendar.MONTH, 1)
                }
            }
        }
        ticks
    }

    val labelArgb = TextSecondary.copy(alpha = 0.75f).toArgb()
    val hasActivity = data.any { it.heatingActive }

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas
        val leftPad = 34f
        val bottomPad = 24f
        val chartLeft = leftPad
        val chartRight = size.width
        val chartTop = 4f
        val chartBottom = size.height - bottomPad
        val chartWidth = chartRight - chartLeft

        val minTs = data.first().timestamp
        val maxTs = data.last().timestamp
        val timeRange = (maxTs - minTs).toFloat().coerceAtLeast(1f)

        fun xOf(ts: Long): Float = chartLeft + (ts - minTs).toFloat() / timeRange * chartWidth

        // Y-axis labels (binary boiler state)
        val yPaint = android.graphics.Paint().apply {
            color = labelArgb; textSize = 22f; isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        drawContext.canvas.nativeCanvas.drawText("On", chartLeft - 6f, chartTop + 12f, yPaint)
        drawContext.canvas.nativeCanvas.drawText("Off", chartLeft - 6f, chartBottom, yPaint)

        // Baseline (boiler off)
        drawLine(
            color = OutlineVariant.copy(alpha = 0.5f),
            start = Offset(chartLeft, chartBottom - 2f),
            end = Offset(chartRight, chartBottom - 2f),
            strokeWidth = 2f
        )

        if (!hasActivity) {
            // No heating recorded in the period: say so instead of drawing fake bars
            val msgPaint = android.graphics.Paint().apply {
                color = labelArgb; textSize = 26f; isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                "Sin actividad registrada en el período",
                (chartLeft + chartRight) / 2f,
                (chartTop + chartBottom) / 2f + 8f,
                msgPaint
            )
        } else {
            // One rect per active sample, extended to the next sample and with a
            // minimum width so sparse data (few samples) stays visible.
            val minBarWidth = 4f
            val barHeight = chartBottom - chartTop - 4f
            val fallbackStep = (timeRange.toLong() / data.size.coerceAtLeast(1)).coerceAtLeast(1L)
            data.forEachIndexed { idx, pt ->
                if (pt.heatingActive) {
                    val x1 = xOf(pt.timestamp).coerceIn(chartLeft, chartRight)
                    val nextTs = data.getOrNull(idx + 1)?.timestamp ?: (pt.timestamp + fallbackStep)
                    val x2 = maxOf(xOf(nextTs), x1 + minBarWidth).coerceAtMost(chartRight)
                    drawRect(
                        color = BoilerActiveColor.copy(alpha = 0.7f),
                        topLeft = Offset(x1, chartTop),
                        size = Size(x2 - x1, barHeight)
                    )
                }
            }
        }

        // X-axis labels
        val xPaint = android.graphics.Paint().apply {
            color = labelArgb; textSize = 22f; isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        xLabels.forEach { (fraction, label) ->
            val x = chartLeft + fraction * chartWidth
            if (x in chartLeft..chartRight) {
                drawContext.canvas.nativeCanvas.drawText(label, x, size.height - 6f, xPaint)
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

// ─────────────────────────────────────────────────────────────────────────────
// OpenTherm Metrics Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OpenThermMetricsCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainer,
        border = BorderStroke(1.dp, Color(0xFF3F4850))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Datos OpenTherm",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            val metrics = listOf(
                Icons.Default.Speed to "Modulación" to "Nivel de modulación del quemador (%)",
                Icons.Default.Thermostat to "Temp. impulsión" to "Temperatura del agua de salida (°C)",
                Icons.Default.WaterDrop to "Temp. retorno" to "Temperatura del agua de vuelta (°C)",
                Icons.Default.Compress to "Presión" to "Presión hidráulica del circuito (bar)"
            )
            metrics.forEach { (iconLabel, description) ->
                val (icon, label) = iconLabel
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                    Column {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text(description, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}

// Boiler Revision Summary Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BoilerRevisionSummaryCard(
    revision: MaintenanceRecordEntity?,
    modifier: Modifier = Modifier
) {
    val dateShort = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("es", "ES")) }

    Surface(
        modifier = modifier,
        color = SurfaceContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Engineering, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text("Revisión caldera", style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        if (revision != null) {
                            Text(
                                "Última: ${dateShort.format(java.util.Date(revision.date))}${if (!revision.visitTimeRange.isNullOrBlank()) " · ${revision.visitTimeRange}" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        } else {
                            Text("Sin revisiones registradas", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
                if (revision != null) {
                    val rendEff = revision.rendimiento
                    val effColor = when {
                        rendEff == null -> TextSecondary
                        rendEff >= 95f -> GreenGood
                        rendEff >= 88f -> AmberWarn
                        else -> RedBad
                    }
                    Surface(color = effColor.copy(alpha = 0.12f), shape = RoundedCornerShape(50.dp)) {
                        Text(
                            if (rendEff != null) "η %.1f%%".format(rendEff) else "Sin datos",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = effColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (revision == null) {
                Text(
                    "Añade el parte de trabajo del técnico para ver el análisis de combustión.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                return@Column
            }

            // Combustion analysis grid
            val hasAnalysis = revision.rendimiento != null || revision.co != null || revision.temperaturaHumos != null
            if (hasAnalysis) {
                HorizontalDivider(color = OutlineVariant, thickness = 0.5.dp)
                Text(
                    "ANÁLISIS DE COMBUSTIÓN",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                // Row 1: Rendimiento + CO + T.Humos
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    revision.rendimiento?.let { eff ->
                        val c = if (eff >= 95f) GreenGood else if (eff >= 88f) AmberWarn else RedBad
                        RevisionMetricCell("Rendimiento", "%.1f%%".format(eff), c, modifier = Modifier.weight(1f))
                    }
                    revision.co?.let { co ->
                        val c = if (co < 50f) GreenGood else if (co < 100f) AmberWarn else RedBad
                        RevisionMetricCell("CO", "%.0f ppm".format(co), c, modifier = Modifier.weight(1f))
                    }
                    revision.temperaturaHumos?.let { th ->
                        RevisionMetricCell("T. Humos", "%.1f°C".format(th), TextSecondary, modifier = Modifier.weight(1f))
                    }
                }
                // Row 2: CO2 + O2 + T.ACS
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    revision.co2?.let { co2 ->
                        RevisionMetricCell("CO₂", "%.2f%%".format(co2), TextSecondary, modifier = Modifier.weight(1f))
                    }
                    revision.o2?.let { o2 ->
                        RevisionMetricCell("O₂", "%.1f%%".format(o2), TextSecondary, modifier = Modifier.weight(1f))
                    }
                    revision.temperaturaACS?.let { tacs ->
                        RevisionMetricCell("T. ACS", "%.1f°C".format(tacs), TextSecondary, modifier = Modifier.weight(1f))
                    }
                }
                // Row 3: Presiones + Caudal
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    revision.presionDinamica?.let { pd ->
                        RevisionMetricCell("P. Dinámica", "%.1f mbar".format(pd), TextSecondary, modifier = Modifier.weight(1f))
                    }
                    revision.presionEstatica?.let { pe ->
                        RevisionMetricCell("P. Estática", "%.1f mbar".format(pe), TextSecondary, modifier = Modifier.weight(1f))
                    }
                    revision.caudalAgua?.let { q ->
                        RevisionMetricCell("Caudal", "%.0f l/min".format(q), TextSecondary, modifier = Modifier.weight(1f))
                    }
                }
            }

            // Equipment info
            val hasEquipment = !revision.modelo.isNullOrBlank() || !revision.numSerie.isNullOrBlank()
            if (hasEquipment) {
                HorizontalDivider(color = OutlineVariant, thickness = 0.5.dp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    revision.modelo?.let { modelo ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Modelo", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(modelo, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                    revision.numSerie?.let { ns ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Nº Serie", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(ns, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                    revision.combustible?.let { fuel ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Combustible", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(fuel, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Technician observations
            if (revision.description.isNotBlank() || !revision.technicianName.isNullOrBlank()) {
                HorizontalDivider(color = OutlineVariant, thickness = 0.5.dp)
                if (!revision.technicianName.isNullOrBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                        Text("Téc. ${revision.technicianName}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
                if (revision.description.isNotBlank()) {
                    Text(revision.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun RevisionMetricCell(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = SurfaceContainerHigh, shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
            Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
