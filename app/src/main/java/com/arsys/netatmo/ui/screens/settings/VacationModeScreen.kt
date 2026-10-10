@file:OptIn(ExperimentalMaterial3Api::class)

package com.arsys.netatmo.ui.screens.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arsys.netatmo.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// ─── Color tokens ─────────────────────────────────────────────────────────────
private val BgColor            = Color(0xFF101419)
private val SurfContainerColor = Color(0xFF1C2025)
private val SurfContLowColor   = Color(0xFF181C21)
private val SurfContHighColor  = Color(0xFF262A30)
private val SurfContLowestColor = Color(0xFF0A0E13)
private val PrimaryColor       = Color(0xFF93CCFF)
private val OnPrimaryColor     = Color(0xFF003351)
private val TextPrimaryColor   = Color(0xFFE0E2EA)
private val TextSecondaryColor = Color(0xFFBFC7D2)
private val GreenGoodColor     = Color(0xFF62DF7D)
private val RedBadColor        = Color(0xFFFFB4AB)
private val OrangeColor        = Color(0xFFF66018)
private val OutlineVariantColor = Color(0xFF3F4850)

// ─── Helpers ──────────────────────────────────────────────────────────────────
private fun formatDateFull(ms: Long): String {
    if (ms == 0L) return "Sin seleccionar"
    val sdf = SimpleDateFormat("d MMM yyyy", Locale("es", "ES"))
    return sdf.format(Date(ms))
}

private fun daysBetween(startMs: Long, endMs: Long): Int {
    if (startMs == 0L || endMs == 0L || endMs <= startMs) return 0
    return ((endMs - startMs) / (1000L * 60 * 60 * 24)).toInt()
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun VacationModeScreen(
    navController: NavController,
    viewModel: VacationModeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val vacationState by viewModel.vacationState.collectAsState()

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker   by remember { mutableStateOf(false) }

    val startPickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (uiState.startMs > 0L) uiState.startMs else null
    )
    val endPickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (uiState.endMs > 0L) uiState.endMs else null
    )

    // Local UI state for new design sections (not in ViewModel)
    var comfortOnReturn     by remember { mutableStateOf(true) }
    var comfortDuration     by remember { mutableStateOf("Auto") }
    var frozenAlerts        by remember { mutableStateOf(true) }
    var presenceDetection   by remember { mutableStateOf(false) }
    var dailyReport         by remember { mutableStateOf(true) }

    val daysCount = daysBetween(uiState.startMs, uiState.endMs)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Modo Vacaciones",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = TextPrimaryColor
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.activate() },
                        enabled = !uiState.isSaving
                    ) {
                        Text(
                            text = "Guardar",
                            color = PrimaryColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = SurfContHighColor,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = TextSecondaryColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgColor,
                    titleContentColor = TextPrimaryColor,
                    navigationIconContentColor = TextPrimaryColor
                )
            )
        },
        containerColor = BgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. STATUS PILL
            StatusPill(daysCount = daysCount)

            // 2. HERO CARD
            HeroCard(startMs = uiState.startMs, endMs = uiState.endMs)

            // 3. DATE PERIOD CARD
            DatePeriodCard(
                startMs = uiState.startMs,
                endMs = uiState.endMs,
                daysCount = daysCount,
                onStartClick = { showStartPicker = true },
                onEndClick   = { showEndPicker = true },
                onDurationSelected = { days ->
                    val now = System.currentTimeMillis()
                    viewModel.updateStart(now)
                    viewModel.updateEnd(now + days * 24L * 60 * 60 * 1000L)
                }
            )

            // 4. ANTIFREEZE TEMPERATURE CARD
            TemperatureCard(
                temperature = uiState.temperature,
                onIncrease  = { if (uiState.temperature < 16.0) viewModel.updateTemp(uiState.temperature + 1.0) },
                onDecrease  = { if (uiState.temperature > 7.0)  viewModel.updateTemp(uiState.temperature - 1.0) },
                onSliderChange = { viewModel.updateTemp(it) }
            )

            // 5. COMFORT ON RETURN CARD
            ComfortOnReturnCard(
                enabled          = comfortOnReturn,
                onToggle         = { comfortOnReturn = it },
                selectedDuration = comfortDuration,
                onDurationSelect = { comfortDuration = it }
            )

            // 6. ALERTS & SECURITY CARD
            AlertsSecurityCard(
                frozenAlerts             = frozenAlerts,
                onFrozenAlertsToggle     = { frozenAlerts = it },
                presenceDetection        = presenceDetection,
                onPresenceDetectionToggle = { presenceDetection = it },
                dailyReport              = dailyReport,
                onDailyReportToggle      = { dailyReport = it }
            )

            // Error message
            uiState.error?.let { errorMsg ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // 7. PRIMARY ACTION BUTTON
            Button(
                onClick = { viewModel.activate() },
                enabled = uiState.startMs > 0L && uiState.endMs > 0L && !uiState.isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor        = PrimaryColor,
                    contentColor          = OnPrimaryColor,
                    disabledContainerColor = SurfContHighColor,
                    disabledContentColor  = TextSecondaryColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = OnPrimaryColor
                    )
                } else {
                    Icon(
                        imageVector     = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier        = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text       = "Activar Vacaciones Ahora",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 8. SECONDARY DESTRUCTIVE BUTTON
            OutlinedButton(
                onClick = { viewModel.deactivate() },
                enabled = !uiState.isSaving,
                border  = BorderStroke(1.dp, OutlineVariantColor),
                colors  = ButtonDefaults.outlinedButtonColors(
                    containerColor    = SurfContainerColor,
                    contentColor      = RedBadColor,
                    disabledContentColor = RedBadColor.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector        = Icons.Filled.Delete,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text       = "Cancelar programacion vacacional",
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }

    // Start date picker dialog
    if (showStartPicker) {
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startPickerState.selectedDateMillis?.let { viewModel.updateStart(it) }
                    showStartPicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = startPickerState)
        }
    }

    // End date picker dialog
    if (showEndPicker) {
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endPickerState.selectedDateMillis?.let { viewModel.updateEnd(it) }
                    showEndPicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = endPickerState)
        }
    }
}

// ─── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun StatusPill(daysCount: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusPulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(50.dp),
        color = SurfContainerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(GreenGoodColor.copy(alpha = pulseAlpha))
            )
            Text(
                text       = "MODO PROTECCION Y AUSENCIA",
                style      = MaterialTheme.typography.labelSmall,
                color      = GreenGoodColor,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.weight(1f)
            )
            if (daysCount > 0) {
                Text(
                    text  = "$daysCount dias programados",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryColor
                )
            }
        }
    }
}

@Composable
private fun HeroCard(startMs: Long, endMs: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        color    = SurfContainerColor,
        border   = BorderStroke(1.dp, OutlineVariantColor),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape  = RoundedCornerShape(12.dp),
                    color  = PrimaryColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector        = Icons.Filled.FlightTakeoff,
                            contentDescription = null,
                            tint               = PrimaryColor,
                            modifier           = Modifier.size(28.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = "Modo Vacaciones",
                        style      = MaterialTheme.typography.titleMedium,
                        color      = TextPrimaryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text  = "Proteccion antihielo continua",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryColor
                    )
                }
            }

            // Date range pill (shown when dates are set)
            if (startMs > 0L && endMs > 0L) {
                Surface(
                    shape    = RoundedCornerShape(50.dp),
                    color    = PrimaryColor.copy(alpha = 0.15f),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text(
                        text     = "${formatDateFull(startMs)} – ${formatDateFull(endMs)}",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = PrimaryColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            // Savings sub-card
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SurfContLowColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Eco,
                        contentDescription = null,
                        tint               = GreenGoodColor,
                        modifier           = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text       = "~64% ahorro (~42.50€ previstos)",
                            style      = MaterialTheme.typography.labelMedium,
                            color      = GreenGoodColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text  = "Caldera: estimado 2.5h/dia activa",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DatePeriodCard(
    startMs: Long,
    endMs: Long,
    daysCount: Int,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    onDurationSelected: (Int) -> Unit
) {
    data class DurationChip(val label: String, val days: Int)
    val durationChips = listOf(
        DurationChip("Fin de semana 3d", 3),
        DurationChip("1 Semana 7d", 7),
        DurationChip("2 Semanas 14d", 14),
        DurationChip("Personalizado", -1)
    )
    var selectedChipDays by remember { mutableStateOf<Int?>(null) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        color    = SurfContainerColor,
        border   = BorderStroke(1.dp, OutlineVariantColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = "Periodo de Ausencia",
                    style      = MaterialTheme.typography.titleMedium,
                    color      = TextPrimaryColor,
                    fontWeight = FontWeight.SemiBold
                )
                if (daysCount > 0) {
                    Text(
                        text       = "$daysCount días",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = PrimaryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // SALIDA / REGRESO blocks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // SALIDA
                Surface(
                    onClick  = onStartClick,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                    color    = SurfContLowColor
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text       = "SALIDA",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = TextSecondaryColor,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text       = formatDateFull(startMs),
                            style      = MaterialTheme.typography.titleSmall,
                            color      = if (startMs > 0L) TextPrimaryColor else TextSecondaryColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text  = "08:00 h",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryColor
                        )
                    }
                }

                // REGRESO (with pulsing dot)
                val infiniteTransition = rememberInfiniteTransition(label = "regresoPulse")
                val dotAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue  = 1f,
                    animationSpec = infiniteRepeatable(
                        animation  = tween(900, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "regresoDotAlpha"
                )
                Surface(
                    onClick  = onEndClick,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                    color    = SurfContHighColor,
                    border   = BorderStroke(1.dp, PrimaryColor.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text       = "REGRESO",
                                style      = MaterialTheme.typography.labelSmall,
                                color      = PrimaryColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryColor.copy(alpha = dotAlpha))
                            )
                        }
                        Text(
                            text       = formatDateFull(endMs),
                            style      = MaterialTheme.typography.titleSmall,
                            color      = if (endMs > 0L) TextPrimaryColor else TextSecondaryColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text  = "19:30 h · Modificar",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Calendar strip
            CalendarStrip(startMs = startMs, endMs = endMs)

            // Quick duration chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding        = PaddingValues(0.dp)
            ) {
                items(durationChips) { chip ->
                    val isSelected = selectedChipDays == chip.days
                    Surface(
                        onClick  = {
                            selectedChipDays = chip.days
                            if (chip.days > 0) onDurationSelected(chip.days)
                        },
                        shape    = RoundedCornerShape(50.dp),
                        color    = if (isSelected) PrimaryColor else SurfContLowColor
                    ) {
                        Text(
                            text       = chip.label,
                            style      = MaterialTheme.typography.labelSmall,
                            color      = if (isSelected) OnPrimaryColor else TextSecondaryColor,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier   = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarStrip(startMs: Long, endMs: Long) {
    val days = (14..22).toList()
    val dayLetters = listOf("L", "M", "X", "J", "V", "S", "D", "L", "M")

    fun startDay(): Int {
        if (startMs == 0L) return -1
        val cal = Calendar.getInstance(); cal.timeInMillis = startMs
        return cal.get(Calendar.DAY_OF_MONTH)
    }
    fun endDay(): Int {
        if (endMs == 0L) return -1
        val cal = Calendar.getInstance(); cal.timeInMillis = endMs
        return cal.get(Calendar.DAY_OF_MONTH)
    }

    val sDay = startDay()
    val eDay = endDay()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text  = "Marzo 2025",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryColor
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            days.forEachIndexed { idx, day ->
                val isEndpoint = sDay > 0 && eDay > 0 && (day == sDay || day == eDay)
                val inRange    = sDay > 0 && eDay > 0 && day in sDay..eDay
                val bgColor = when {
                    isEndpoint -> PrimaryColor
                    inRange    -> PrimaryColor.copy(alpha = 0.2f)
                    else       -> Color.Transparent
                }
                val textColor = when {
                    isEndpoint -> OnPrimaryColor
                    inRange    -> PrimaryColor
                    else       -> TextSecondaryColor
                }
                Column(
                    modifier              = Modifier.weight(1f),
                    horizontalAlignment   = Alignment.CenterHorizontally,
                    verticalArrangement   = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text     = if (idx < dayLetters.size) dayLetters[idx] else "L",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = TextSecondaryColor,
                        fontSize = 9.sp
                    )
                    Box(
                        modifier          = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(bgColor),
                        contentAlignment  = Alignment.Center
                    ) {
                        Text(
                            text       = day.toString(),
                            style      = MaterialTheme.typography.labelSmall,
                            color      = textColor,
                            fontWeight = if (isEndpoint) FontWeight.Bold else FontWeight.Normal,
                            fontSize   = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemperatureCard(
    temperature: Double,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onSliderChange: (Double) -> Unit
) {
    val tempInt  = temperature.roundToInt()
    val fraction = ((temperature - 7.0) / (16.0 - 7.0)).coerceIn(0.0, 1.0).toFloat()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        color    = SurfContainerColor,
        border   = BorderStroke(1.dp, OutlineVariantColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = "Consigna Antihielo",
                    style      = MaterialTheme.typography.titleMedium,
                    color      = TextPrimaryColor,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = GreenGoodColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text     = "Ahorro Max",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = GreenGoodColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Large temp display with +/- buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Surface(
                    onClick  = onDecrease,
                    shape    = CircleShape,
                    color    = SurfContHighColor,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector        = Icons.Filled.Remove,
                            contentDescription = "Reducir temperatura",
                            tint               = TextPrimaryColor,
                            modifier           = Modifier.size(22.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment     = Alignment.Top,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text       = tempInt.toString(),
                        style      = MaterialTheme.typography.displayMedium,
                        color      = TextPrimaryColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text       = "°",
                        style      = MaterialTheme.typography.headlineLarge,
                        color      = PrimaryColor,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.padding(top = 6.dp)
                    )
                }

                Surface(
                    onClick  = onIncrease,
                    shape    = CircleShape,
                    color    = SurfContHighColor,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector        = Icons.Filled.Add,
                            contentDescription = "Aumentar temperatura",
                            tint               = TextPrimaryColor,
                            modifier           = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Slider track with labeled markers (range 7–16°C)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Slider(
                    value         = fraction,
                    onValueChange = { f ->
                        val newTemp = 7.0 + f * (16.0 - 7.0)
                        onSliderChange(newTemp.roundToInt().toDouble())
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor        = PrimaryColor,
                        activeTrackColor  = PrimaryColor,
                        inactiveTrackColor = SurfContLowestColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text      = "7°C\nEstricto",
                        style     = MaterialTheme.typography.labelSmall,
                        color     = TextSecondaryColor,
                        fontSize  = 10.sp,
                        textAlign = TextAlign.Start
                    )
                    Text(
                        text      = "12°C\nRecomendado",
                        style     = MaterialTheme.typography.labelSmall,
                        color     = TextSecondaryColor,
                        fontSize  = 10.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text      = "16°C\nEco",
                        style     = MaterialTheme.typography.labelSmall,
                        color     = TextSecondaryColor,
                        fontSize  = 10.sp,
                        textAlign = TextAlign.End
                    )
                }
            }

            // Shield info note
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SurfContLowColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Security,
                        contentDescription = null,
                        tint               = PrimaryColor.copy(alpha = 0.7f),
                        modifier           = Modifier.size(16.dp)
                    )
                    Text(
                        text     = "La caldera mantiene esta temperatura minima para proteger tuberias del hielo.",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = TextSecondaryColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ComfortOnReturnCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    selectedDuration: String,
    onDurationSelect: (String) -> Unit
) {
    val durationOptions = listOf("Auto", "2h", "4h", "6h")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        color    = SurfContainerColor,
        border   = BorderStroke(1.dp, OutlineVariantColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row with toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier              = Modifier.weight(1f)
                ) {
                    Surface(
                        shape    = RoundedCornerShape(8.dp),
                        color    = OrangeColor.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector        = Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint               = OrangeColor,
                                modifier           = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text       = "Confort al Regreso",
                            style      = MaterialTheme.typography.titleSmall,
                            color      = TextPrimaryColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text  = "Precalentamiento inteligente",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryColor
                        )
                    }
                }
                Switch(
                    checked         = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = Color.White,
                        checkedTrackColor   = PrimaryColor,
                        uncheckedThumbColor = TextSecondaryColor,
                        uncheckedTrackColor = OutlineVariantColor
                    )
                )
            }

            if (enabled) {
                Text(
                    text  = "La caldera arrancara automaticamente antes de tu llegada para que encuentres la temperatura de confort deseada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryColor
                )

                // 4-segment duration control
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfContLowColor
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        durationOptions.forEach { option ->
                            val isSelected = option == selectedDuration
                            Surface(
                                onClick  = { onDurationSelect(option) },
                                shape    = RoundedCornerShape(8.dp),
                                color    = if (isSelected) PrimaryColor else Color.Transparent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier         = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text       = option,
                                        style      = MaterialTheme.typography.labelSmall,
                                        color      = if (isSelected) OnPrimaryColor else TextSecondaryColor,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // Predictive algorithm micro-card
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfContLowColor
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text       = "Algoritmo predictivo",
                                style      = MaterialTheme.typography.labelSmall,
                                color      = TextSecondaryColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text  = "Tª exterior: 8°C · Arranque: 17:30 h",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondaryColor
                            )
                        }
                        Icon(
                            imageVector        = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint               = PrimaryColor.copy(alpha = 0.6f),
                            modifier           = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertsSecurityCard(
    frozenAlerts: Boolean,
    onFrozenAlertsToggle: (Boolean) -> Unit,
    presenceDetection: Boolean,
    onPresenceDetectionToggle: (Boolean) -> Unit,
    dailyReport: Boolean,
    onDailyReportToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        color    = SurfContainerColor,
        border   = BorderStroke(1.dp, OutlineVariantColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                text       = "Alertas y Seguridad",
                style      = MaterialTheme.typography.titleMedium,
                color      = TextPrimaryColor,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(bottom = 8.dp)
            )
            AlertToggleRow(
                title          = "Vigilancia de helada extrema",
                subtitle       = "Alerta si la temperatura cae por debajo de 2°C",
                checked        = frozenAlerts,
                onCheckedChange = onFrozenAlertsToggle
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(OutlineVariantColor.copy(alpha = 0.5f))
            )
            AlertToggleRow(
                title          = "Deteccion de presencia",
                subtitle       = "Activa modo confort si detecta movimiento",
                checked        = presenceDetection,
                onCheckedChange = onPresenceDetectionToggle
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(OutlineVariantColor.copy(alpha = 0.5f))
            )
            AlertToggleRow(
                title          = "Informe diario de consumo",
                subtitle       = "Recibe un resumen cada dia por notificacion",
                checked        = dailyReport,
                onCheckedChange = onDailyReportToggle
            )
        }
    }
}

@Composable
private fun AlertToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.bodyMedium,
                color      = TextPrimaryColor,
                fontWeight = FontWeight.Medium
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryColor
            )
        }
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = PrimaryColor,
                uncheckedThumbColor = TextSecondaryColor,
                uncheckedTrackColor = OutlineVariantColor
            )
        )
    }
}
