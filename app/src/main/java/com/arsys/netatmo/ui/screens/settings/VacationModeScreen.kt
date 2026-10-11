@file:OptIn(ExperimentalMaterial3Api::class)

package com.arsys.netatmo.ui.screens.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arsys.netatmo.ui.theme.*
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.*

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
private val SPAIN_ZONE: ZoneId = ZoneId.of("Europe/Madrid")

// DatePicker works with the selected day as midnight UTC; the vacation must start/end at
// midnight in Spain (UTC+1 / UTC+2 with daylight saving).
private fun pickerToSpainMidnight(pickerUtcMs: Long): Long =
    Instant.ofEpochMilli(pickerUtcMs).atZone(ZoneOffset.UTC).toLocalDate()
        .atStartOfDay(SPAIN_ZONE).toInstant().toEpochMilli()

private fun spainToPickerUtc(ms: Long): Long =
    Instant.ofEpochMilli(ms).atZone(SPAIN_ZONE).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun formatDateFull(ms: Long): String {
    if (ms == 0L) return "Sin seleccionar"
    val sdf = SimpleDateFormat("d MMM yyyy", Locale("es", "ES"))
    sdf.timeZone = TimeZone.getTimeZone(SPAIN_ZONE)
    return sdf.format(Date(ms))
}

private fun daysBetween(startMs: Long, endMs: Long): Int {
    if (startMs == 0L || endMs == 0L || endMs <= startMs) return 0
    val start = Instant.ofEpochMilli(startMs).atZone(SPAIN_ZONE).toLocalDate()
    val end = Instant.ofEpochMilli(endMs).atZone(SPAIN_ZONE).toLocalDate()
    return ChronoUnit.DAYS.between(start, end).toInt()
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
        initialSelectedDateMillis = if (uiState.startMs > 0L) spainToPickerUtc(uiState.startMs) else null
    )
    val endPickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (uiState.endMs > 0L) spainToPickerUtc(uiState.endMs) else null
    )

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
            StatusPill(daysCount = daysCount, active = vacationState.enabled)

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

            // 4. ANTIFREEZE INFO CARD
            FrostGuardInfoCard()

            // 5. ALERTS CARD
            AlertsSecurityCard(
                frozenAlerts         = vacationState.frozenAlerts,
                onFrozenAlertsToggle = { viewModel.setFrozenAlerts(it) }
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
                    startPickerState.selectedDateMillis?.let { viewModel.updateStart(pickerToSpainMidnight(it)) }
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
                    endPickerState.selectedDateMillis?.let { viewModel.updateEnd(pickerToSpainMidnight(it)) }
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
private fun StatusPill(daysCount: Int, active: Boolean) {
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
                    .background(if (active) GreenGoodColor.copy(alpha = pulseAlpha) else TextSecondaryColor)
            )
            Text(
                text       = if (active) "MODO VACACIONES ACTIVO" else "MODO VACACIONES INACTIVO",
                style      = MaterialTheme.typography.labelSmall,
                color      = if (active) GreenGoodColor else TextSecondaryColor,
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
    val locale = Locale("es", "ES")
    val today = LocalDate.now(SPAIN_ZONE)
    val start = if (startMs > 0L) Instant.ofEpochMilli(startMs).atZone(SPAIN_ZONE).toLocalDate() else null
    val end = if (endMs > 0L) Instant.ofEpochMilli(endMs).atZone(SPAIN_ZONE).toLocalDate() else null

    val first = if (start != null && start.isBefore(today)) start else today
    val minLast = first.plusDays(13)
    val wantedLast = end?.plusDays(2) ?: minLast
    val last = minOf(maxOf(wantedLast, minLast), first.plusDays(180))
    val days = generateSequence(first) { it.plusDays(1) }
        .takeWhile { !it.isAfter(last) }
        .toList()

    val cellWidth = 36.dp
    val scrollState = rememberScrollState()
    val cellPx = with(LocalDensity.current) { cellWidth.roundToPx() }
    LaunchedEffect(start) {
        val index = if (start != null) ChronoUnit.DAYS.between(first, start).toInt() else 0
        scrollState.scrollTo(((index - 1).coerceAtLeast(0)) * cellPx)
    }

    val monthLabel = days.first().month.getDisplayName(TextStyle.FULL, locale) + " " + days.first().year
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text  = monthLabel.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryColor
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState)
        ) {
            days.forEachIndexed { idx, day ->
                val isEndpoint = day == start || day == end
                val inRange = start != null && end != null && !day.isBefore(start) && !day.isAfter(end)
                val isToday = day == today
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
                    modifier            = Modifier.width(cellWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text     = if (idx == 0 || day.dayOfMonth == 1)
                            day.month.getDisplayName(TextStyle.SHORT, locale).trimEnd('.') else " ",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = TextSecondaryColor,
                        fontSize = 9.sp
                    )
                    Text(
                        text     = day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = TextSecondaryColor,
                        fontSize = 9.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                            .then(if (isToday) Modifier.border(1.dp, PrimaryColor, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = day.dayOfMonth.toString(),
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
private fun FrostGuardInfoCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        color    = SurfContainerColor,
        border   = BorderStroke(1.dp, OutlineVariantColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(
                imageVector        = Icons.Filled.Security,
                contentDescription = null,
                tint               = PrimaryColor,
                modifier           = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text       = "Temperatura antihielo",
                    style      = MaterialTheme.typography.titleMedium,
                    color      = TextPrimaryColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text  = "Netatmo mantiene la temperatura antiheladas configurada en tu instalacion. " +
                        "No se puede cambiar desde esta pantalla. Al terminar el periodo se restaura " +
                        "la programacion normal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryColor
                )
            }
        }
    }
}

@Composable
private fun AlertsSecurityCard(
    frozenAlerts: Boolean,
    onFrozenAlertsToggle: (Boolean) -> Unit
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
