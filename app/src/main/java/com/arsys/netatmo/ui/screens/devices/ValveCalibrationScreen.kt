package com.arsys.netatmo.ui.screens.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- Design tokens ---
private val BgColor            = Color(0xFF101419)
private val SurfaceLowest      = Color(0xFF0A0E13)
private val SurfaceContLow     = Color(0xFF181C21)
private val SurfaceCont        = Color(0xFF1C2025)
private val SurfaceContHigh    = Color(0xFF262A30)
private val SurfaceContHighest = Color(0xFF31353B)
private val OutlineVar         = Color(0xFF3F4850)
private val OnSurface          = Color(0xFFE0E2EA)
private val OnSurfaceVar       = Color(0xFFBFC7D2)
private val Primary            = Color(0xFF93CCFF)
private val OnPrimary          = Color(0xFF003351)
private val PrimaryContainer   = Color(0xFF3198DC)
private val Secondary          = Color(0xFFFFB599)
private val SecondaryContainer = Color(0xFFF66018)
private val Tertiary           = Color(0xFF62DF7D)
private val OnTertiary         = Color(0xFF003914)
private val TertiaryContainer  = Color(0xFF1CA64D)
private val ErrorColor         = Color(0xFFFFB4AB)
private val ErrorContainer     = Color(0xFF93000A)

// ── Data model ──────────────────────────────────────────────────────────────

private data class Room(
    val id:   String,
    val name: String,
    val module: String?,      // null = no NRV paired
)

private val ROOMS = listOf(
    Room("r1", "Salón",               "NRV-01"),
    Room("r2", "Dormitorio principal", "NRV-02"),
    Room("r3", "Cocina",               null),
    Room("r4", "Dormitorio 2",         "NRV-03"),
    Room("r5", "Baño",                 null),
)

private val CALIBRATION_STEPS = listOf(
    "Cerrando vástago completamente (0 mm)...",
    "Detectando rango de movimiento mecánico...",
    "Estableciendo posición de apertura óptima...",
    "Verificando respuesta térmica del motor PID...",
    "Guardando calibración en memoria del dispositivo...",
)

private const val CALIBRATION_TOTAL_MS = 120_000L   // 2 min
private const val CALIBRATION_STEP_MS  = CALIBRATION_TOTAL_MS / 5

// ── Main screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValveCalibrationScreen(onBack: () -> Unit) {
    var selectedRoom    by remember { mutableStateOf<Room?>(null) }
    var calState        by remember { mutableStateOf(CalibrationState.IDLE) }
    var completedSteps  by remember { mutableIntStateOf(0) }
    var progressMs      by remember { mutableLongStateOf(0L) }
    var calResult       by remember { mutableStateOf<Boolean?>(null) }   // null = not done
    var valvePositionPct by remember { mutableFloatStateOf(38f) }
    var offsetDeg       by remember { mutableFloatStateOf(-1.8f) }
    val scope           = rememberCoroutineScope()

    val spinRotation by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "spin"
    )

    fun startCalibration() {
        if (selectedRoom?.module == null) return
        calState       = CalibrationState.RUNNING
        completedSteps = 0
        progressMs     = 0L
        calResult      = null

        scope.launch {
            repeat(5) { step ->
                delay(CALIBRATION_STEP_MS)
                completedSteps = step + 1
                progressMs     = (step + 1) * CALIBRATION_STEP_MS
                // Animate valve position through the calibration sequence
                valvePositionPct = when (step) {
                    0 -> 0f; 1 -> 25f; 2 -> 60f; 3 -> 70f; else -> 68f
                }
            }
            calState  = CalibrationState.DONE
            calResult = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Calibración de Válvula", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = OnSurface)
                    }
                },
                actions = {
                    TextButton(onClick = {}) {
                        Text("Guardar", color = Primary, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceLowest)
            )
        },
        containerColor = BgColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ── Diagnostic badge ──────────────────────────────────────────
            DiagnosticBadge()

            // ── Room selection ────────────────────────────────────────────
            SectionHeader(title = "Selecciona una habitación", subtitle = "Solo habitaciones con módulo NRV son calibrables")
            ROOMS.forEach { room ->
                RoomRow(
                    room       = room,
                    isSelected = room == selectedRoom,
                    disabled   = calState == CalibrationState.RUNNING,
                    onClick    = {
                        if (calState != CalibrationState.RUNNING) {
                            selectedRoom    = room
                            calState        = CalibrationState.IDLE
                            completedSteps  = 0
                            progressMs      = 0L
                            calResult       = null
                            valvePositionPct = 38f
                        }
                    }
                )
            }

            // ── Selected room detail ──────────────────────────────────────
            AnimatedVisibility(
                visible = selectedRoom != null,
                enter   = fadeIn() + expandVertically()
            ) {
                selectedRoom?.let { room ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Divider(color = OutlineVar, thickness = 1.dp)

                        // Device telemetry card
                        DeviceTelemetryCard(room = room, valvePositionPct = valvePositionPct)

                        // Calibration card
                        if (room.module != null) {
                            CalibrationCard(
                                room           = room,
                                calState       = calState,
                                completedSteps = completedSteps,
                                totalSteps     = CALIBRATION_STEPS.size,
                                progressMs     = progressMs,
                                totalMs        = CALIBRATION_TOTAL_MS,
                                spinRotation   = spinRotation,
                                onStart        = { startCalibration() }
                            )

                            // Result
                            AnimatedVisibility(visible = calResult != null, enter = fadeIn() + expandVertically()) {
                                calResult?.let { success ->
                                    CalibrationResultCard(success = success, onRetry = {
                                        calState = CalibrationState.IDLE
                                        calResult = null
                                    })
                                }
                            }

                            // Offset compensation
                            OffsetCompensationCard(
                                offset    = offsetDeg,
                                onOffsetChange = { offsetDeg = it.coerceIn(-5f, 5f) }
                            )

                            // Silent mode & advanced
                            SilentModeCard()
                        } else {
                            NoNrvCard()
                        }
                    }
                }
            }
        }
    }
}

// ── Enums ────────────────────────────────────────────────────────────────────

private enum class CalibrationState { IDLE, RUNNING, DONE }

// ── Subcomposables ────────────────────────────────────────────────────────────

@Composable
private fun DiagnosticBadge() {
    val infinite = rememberInfiniteTransition(label = "dot")
    val dotAlpha by infinite.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dot"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(SurfaceContLow)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Tertiary.copy(alpha = dotAlpha))
            )
            Text("Diagnóstico Óptimo", color = Tertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("·", color = OutlineVar)
            Text("Sincronizado hace 12s", color = OnSurfaceVar, fontSize = 11.sp)
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SurfaceContHigh)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Sensors, contentDescription = null, tint = Primary, modifier = Modifier.size(13.dp))
            Text("RF 868 MHz", color = Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title,    color = OnSurface,    fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(subtitle, color = OnSurfaceVar, fontSize = 12.sp)
    }
}

@Composable
private fun RoomRow(room: Room, isSelected: Boolean, disabled: Boolean, onClick: () -> Unit) {
    val hasNrv = room.module != null
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !disabled && hasNrv, onClick = onClick),
        shape  = RoundedCornerShape(12.dp),
        color  = if (isSelected) Primary.copy(alpha = 0.08f) else SurfaceContLow,
        border = BorderStroke(1.dp, if (isSelected) Primary.copy(alpha = 0.5f) else OutlineVar)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = null,
                tint     = if (isSelected) Primary else if (hasNrv) OnSurfaceVar else OutlineVar,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(room.name, color = if (hasNrv) OnSurface else OnSurfaceVar, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
                Text(room.module ?: "Sin módulo NRV", color = if (hasNrv) Primary else OutlineVar, fontSize = 11.sp)
            }
            if (isSelected) Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
            if (!hasNrv)    Icon(Icons.Filled.Lock,          contentDescription = null, tint = OutlineVar, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun DeviceTelemetryCard(room: Room, valvePositionPct: Float) {
    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = SurfaceCont,
        border = BorderStroke(1.dp, OutlineVar)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceContHigh,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Tune, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
                        }
                    }
                    Column {
                        Text(room.name,          color = OnSurface,    fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(room.module ?: "—", color = OnSurfaceVar, fontSize = 11.sp)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Primary.copy(alpha = 0.12f)
                ) {
                    Text("Netatmo Smart", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }

            // Telemetry 2×2 grid
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TelemetryTile("Batería",    "92%",  "Óptima",       Tertiary,  Icons.Filled.BatteryChargingFull, modifier = Modifier.weight(1f))
                TelemetryTile("Señal",      "-68 dBm", "Excelente", Primary,   Icons.Filled.NetworkWifi, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TelemetryTile("Firmware",   "v94.2", "Al día",      Tertiary,  Icons.Filled.Verified, modifier = Modifier.weight(1f))
                TelemetryTile("Vástago",    "${valvePositionPct.toInt()}%", "%.1f mm".format(valvePositionPct * 4.5f / 100f), Secondary, Icons.Filled.Speed, modifier = Modifier.weight(1f))
            }

            // Valve position bar
            ValvePositionBar(pct = valvePositionPct)
        }
    }
}

@Composable
private fun TelemetryTile(label: String, value: String, sub: String, accentColor: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = SurfaceContLow,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = OnSurfaceVar, fontSize = 10.sp)
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
            Text(value, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(sub,   color = accentColor, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ValvePositionBar(pct: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Cierre total (0 mm)", color = OnSurfaceVar, fontSize = 10.sp)
            Text("Posición: ${pct.toInt()}%", color = Secondary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            Text("Apertura máx (4,5 mm)", color = OnSurfaceVar, fontSize = 10.sp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SurfaceContHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(PrimaryContainer)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0% flujo",    color = OnSurfaceVar, fontSize = 10.sp)
            Text("Caudal: ${(pct * 1.8f).toInt()} L/h", color = Secondary, fontSize = 10.sp)
            Text("100% caudal", color = OnSurfaceVar, fontSize = 10.sp)
        }
    }
}

// ── Calibration card ─────────────────────────────────────────────────────────

@Composable
private fun CalibrationCard(
    room:           Room,
    calState:       CalibrationState,
    completedSteps: Int,
    totalSteps:     Int,
    progressMs:     Long,
    totalMs:        Long,
    spinRotation:   Float,
    onStart:        () -> Unit,
) {
    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = SurfaceCont,
        border = BorderStroke(1.dp, OutlineVar)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text("Calibración mecánica del recorrido", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                if (calState == CalibrationState.DONE) {
                    Surface(shape = RoundedCornerShape(50), color = TertiaryContainer.copy(alpha = 0.2f)) {
                        Text("Alineada", color = Tertiary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }

            // Explanation
            Surface(shape = RoundedCornerShape(10.dp), color = SurfaceContHigh.copy(alpha = 0.6f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                    Text("El motor moverá el pistón durante 2 minutos para localizar los topes de cierre estanco y desatascar impurezas mecánicas.", color = OnSurfaceVar, fontSize = 12.sp)
                }
            }

            // Running state
            if (calState == CalibrationState.RUNNING) {
                // Overall time progress
                val timeProgress = progressMs.toFloat() / totalMs.toFloat()
                val remainingS   = ((totalMs - progressMs) / 1000L).toInt()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Calibrando...", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("%02d:%02d restantes".format(remainingS / 60, remainingS % 60), color = OnSurfaceVar, fontSize = 11.sp)
                    }
                    LinearProgressIndicator(
                        progress   = { timeProgress },
                        modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color      = Primary,
                        trackColor = OutlineVar
                    )
                }

                // Step details
                CALIBRATION_STEPS.forEachIndexed { index, stepText ->
                    val done    = index < completedSteps
                    val current = index == completedSteps
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (done) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Tertiary, modifier = Modifier.size(16.dp))
                        } else if (current) {
                            Icon(Icons.Filled.Sync, contentDescription = null, tint = Primary,
                                modifier = Modifier.size(16.dp).rotate(spinRotation))
                        } else {
                            Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(OutlineVar))
                            }
                        }
                        Text(
                            stepText,
                            color    = if (done) Tertiary else if (current) Primary else OnSurfaceVar,
                            fontSize = 12.sp
                        )
                    }
                }

                // Feedback text
                val feedback = when {
                    completedSteps == 0 -> "Midiendo resistencia de aguja... (Paso 1/5)"
                    completedSteps == 1 -> "Recorriendo vástago a 0 mm... (Paso 2/5)"
                    completedSteps == 2 -> "Estableciendo topes mecánicos... (Paso 3/5)"
                    completedSteps == 3 -> "Verificando modulación PID... (Paso 4/5)"
                    else               -> "Guardando cotas de presión... (Paso 5/5)"
                }
                Text(feedback, color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }

            // Start button
            if (calState != CalibrationState.RUNNING) {
                Button(
                    onClick  = onStart,
                    enabled  = calState == CalibrationState.IDLE,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = PrimaryContainer, contentColor = OnPrimary),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    if (calState == CalibrationState.DONE) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Recalibrar vástago", fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Iniciar calibración del vástago", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── Result card ──────────────────────────────────────────────────────────────

@Composable
private fun CalibrationResultCard(success: Boolean, onRetry: () -> Unit) {
    Surface(
        shape  = RoundedCornerShape(14.dp),
        color  = if (success) TertiaryContainer.copy(alpha = 0.1f) else ErrorContainer.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, if (success) Tertiary.copy(alpha = 0.4f) else ErrorColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                tint     = if (success) Tertiary else ErrorColor,
                modifier = Modifier.size(30.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (success) "Calibración completada con éxito" else "Calibración fallida",
                    color      = if (success) Tertiary else ErrorColor,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp
                )
                Text(
                    if (success) "El vástago está correctamente alineado. Posición base: 0,0 mm."
                    else         "El motor no pudo detectar los topes mecánicos. Revisa que la válvula esté bien instalada e inténtalo de nuevo.",
                    color    = OnSurfaceVar,
                    fontSize = 12.sp
                )
            }
            if (!success) {
                TextButton(onClick = onRetry) {
                    Text("Reintentar", color = Primary, fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Offset compensation card ─────────────────────────────────────────────────

@Composable
private fun OffsetCompensationCard(offset: Float, onOffsetChange: (Float) -> Unit) {
    val valveTemp   = 22.8f
    val effectiveTemp = valveTemp + offset

    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = SurfaceCont,
        border = BorderStroke(1.dp, OutlineVar)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Thermostat, contentDescription = null, tint = SecondaryContainer, modifier = Modifier.size(20.dp))
                    Text("Compensación de sonda (offset)", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Surface(shape = RoundedCornerShape(50), color = SecondaryContainer.copy(alpha = 0.15f)) {
                    Text("Activo", color = Secondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Text("Corrige la sobrestimación por proximidad directa al radiador caliente.", color = OnSurfaceVar, fontSize = 12.sp)

            // Probe comparison
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceContLow, modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Válvula integrada", color = OutlineVar, fontSize = 10.sp)
                        Text("22.8 °C",           color = Secondary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Sensor en radiador", color = OnSurfaceVar, fontSize = 10.sp)
                    }
                }
                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceContLow, modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Sonda ambiente",  color = OutlineVar, fontSize = 10.sp)
                        Text("21.0 °C",         color = Primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Termostato Netatmo", color = Primary, fontSize = 10.sp)
                    }
                }
            }

            // Offset slider
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ajuste de compensación", color = OnSurfaceVar, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${if (offset >= 0) "+" else ""}${"%.1f".format(offset)} °C",
                        color      = Primary,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick  = { onOffsetChange(offset - 0.1f) },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContHigh)
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Reducir", tint = OnSurface)
                    }
                    Slider(
                        value         = offset,
                        onValueChange = onOffsetChange,
                        valueRange    = -5f..5f,
                        modifier      = Modifier.weight(1f),
                        colors        = SliderDefaults.colors(
                            activeTrackColor   = Primary,
                            thumbColor         = Primary,
                            inactiveTrackColor = OutlineVar
                        )
                    )
                    IconButton(
                        onClick  = { onOffsetChange(offset + 0.1f) },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContHigh)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Aumentar", tint = OnSurface)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("-5.0 °C", color = OutlineVar, fontSize = 10.sp)
                    Text("0.0 °C",  color = OutlineVar, fontSize = 10.sp)
                    Text("+5.0 °C", color = OutlineVar, fontSize = 10.sp)
                }
            }

            // Result chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceContHigh)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = Tertiary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Temperatura ambiental efectiva: ", color = OnSurface, fontSize = 12.sp)
                Text("%.1f °C".format(effectiveTemp), color = Tertiary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

// ── Silent mode card ─────────────────────────────────────────────────────────

@Composable
private fun SilentModeCard() {
    var silentNight    by remember { mutableStateOf(true) }
    var descalcWeekly  by remember { mutableStateOf(true) }

    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = SurfaceCont,
        border = BorderStroke(1.dp, OutlineVar)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bedtime, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                Text("Comportamiento silencioso y cuidado mecánico", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            SilentToggleRow(
                title   = "Velocidad reducida nocturna",
                badge   = "23:00 - 07:00",
                desc    = "El micro-motor modula el vástago a baja frecuencia evitando ruido de engranajes durante el descanso.",
                checked = silentNight,
                onCheckedChange = { silentNight = it }
            )
            SilentToggleRow(
                title   = "Descalcificación preventiva",
                badge   = "Semanal",
                desc    = "Ciclo de apertura completa cada miércoles a las 12:00 h para prevenir adherencia de cal y bloqueo de aguja.",
                checked = descalcWeekly,
                onCheckedChange = { descalcWeekly = it }
            )
        }
    }
}

@Composable
private fun SilentToggleRow(title: String, badge: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = SurfaceContLow) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Surface(shape = RoundedCornerShape(4.dp), color = Primary.copy(alpha = 0.1f)) {
                        Text(badge, color = Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                }
                Text(desc, color = OnSurfaceVar, fontSize = 11.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor        = OnPrimary,
                    checkedTrackColor        = PrimaryContainer,
                    uncheckedThumbColor      = OnSurfaceVar,
                    uncheckedTrackColor      = SurfaceContHighest
                )
            )
        }
    }
}

// ── No NRV card ──────────────────────────────────────────────────────────────

@Composable
private fun NoNrvCard() {
    Surface(
        shape  = RoundedCornerShape(14.dp),
        color  = SurfaceContHigh,
        border = BorderStroke(1.dp, OutlineVar)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = OnSurfaceVar, modifier = Modifier.size(24.dp))
            Text(
                "Esta habitación no tiene ningún módulo NRV (cabezal termostático) emparejado. La calibración no está disponible.",
                color    = OnSurfaceVar,
                fontSize = 13.sp
            )
        }
    }
}
