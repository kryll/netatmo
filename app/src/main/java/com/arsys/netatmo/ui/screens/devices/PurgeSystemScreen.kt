package com.arsys.netatmo.ui.screens.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- Design tokens ---
private val BgColor           = Color(0xFF101419)
private val SurfaceLowest     = Color(0xFF0A0E13)
private val SurfaceContLow    = Color(0xFF181C21)
private val SurfaceCont       = Color(0xFF1C2025)
private val SurfaceContHigh   = Color(0xFF262A30)
private val SurfaceContHighest = Color(0xFF31353B)
private val OutlineVar        = Color(0xFF3F4850)
private val OnSurface         = Color(0xFFE0E2EA)
private val OnSurfaceVar      = Color(0xFFBFC7D2)
private val Primary           = Color(0xFF93CCFF)
private val OnPrimary         = Color(0xFF003351)
private val PrimaryContainer  = Color(0xFF3198DC)
private val Secondary         = Color(0xFFFFB599)
private val SecondaryContainer = Color(0xFFF66018)
private val Tertiary          = Color(0xFF62DF7D)
private val OnTertiary        = Color(0xFF003914)
private val TertiaryContainer = Color(0xFF1CA64D)
private val ErrorColor        = Color(0xFFFFB4AB)
private val ErrorContainer    = Color(0xFF93000A)

// ── Data model ──────────────────────────────────────────────────────────────

private data class PurgeStep(
    val icon:        ImageVector,
    val title:       String,
    val description: String,
    val durationMs:  Long = 4000L,
)

private val AIR_PURGE_STEPS = listOf(
    PurgeStep(Icons.Filled.PowerOff,      "Cortar suministro",   "Apaga la caldera y espera a que el sistema se enfríe. La bomba de circulación quedará en pausa."),
    PurgeStep(Icons.Filled.VolumeUp,      "Abrir llave de purga","Localiza el purgador en la parte superior del radiador. Gira la llave ¼ vuelta antihorario hasta oír el silbido de aire."),
    PurgeStep(Icons.Filled.Air,           "Esperar salida de aire","Mantén abierto hasta que el sonido cese y comience a brotar un chorro continuo de agua sin burbujas."),
    PurgeStep(Icons.Filled.LockReset,     "Cerrar llave de purga","Cierra girando en sentido horario hasta que la válvula quede estanca. Seca con un paño cualquier gota residual."),
    PurgeStep(Icons.Filled.Speed,         "Verificar presión",   "Comprueba el manómetro de la caldera. La presión debe estar entre 1,2 y 1,5 bar. Añade agua si es necesario."),
)

private val RADIATOR_PURGE_STEPS = listOf(
    PurgeStep(Icons.Filled.HeatPump,      "Abrir válvula de entrada","Abre completamente la válvula de entrada del radiador (gira sentido antihorario hasta el tope)."),
    PurgeStep(Icons.Filled.WaterDrop,     "Comprobar temperatura",   "Enciende la caldera y espera 5 minutos. Verifica que el cuerpo del radiador se caliente de forma uniforme."),
    PurgeStep(Icons.Filled.VolumeUp,      "Purgar por la parte alta", "Abre el purgador superior del radiador y espera a que el aire salga completamente."),
    PurgeStep(Icons.Filled.LockReset,     "Cerrar y estabilizar",    "Cierra el purgador, comprueba la presión del circuito (1,2-1,5 bar) y reactiva la caldera."),
    PurgeStep(Icons.Filled.Thermostat,    "Verificar temperatura",   "Espera 10 minutos. La cresta superior del radiador debe alcanzar la misma temperatura que la parte baja."),
)

// ── Main screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurgeSystemScreen(onBack: () -> Unit, onNavigateToCalibration: () -> Unit = {}) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Purga del Sistema", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = OnSurface)
                    }
                },
                actions = {
                    // Emergency stop button
                    TextButton(onClick = {}) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = ErrorColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Abortar", color = ErrorColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceLowest)
            )
        },
        containerColor = BgColor
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // System status bar
            SystemStatusBar()

            // Tab row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = SurfaceContLow,
                contentColor     = Primary
            ) {
                listOf("Purga de Aire", "Purga de Radiador", "Calibración").forEachIndexed { i, label ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text = {
                            Text(
                                label,
                                fontWeight = if (selectedTab == i) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize   = 13.sp
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> PurgeTab(
                    steps       = AIR_PURGE_STEPS,
                    tabLabel    = "Purga de Aire",
                    showTimer   = false
                )
                1 -> PurgeTab(
                    steps       = RADIATOR_PURGE_STEPS,
                    tabLabel    = "Purga de Radiador",
                    showTimer   = true
                )
                2 -> CalibrationLaunchTab(onNavigateToCalibration = onNavigateToCalibration)
            }
        }
    }
}

// ── Status bar ──────────────────────────────────────────────────────────────

@Composable
private fun SystemStatusBar() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContLow)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(SecondaryContainer.copy(alpha = alpha))
            )
            Text("Modo Mantenimiento Activo", color = Secondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SurfaceContHigh)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Sensors, contentDescription = null, tint = Tertiary, modifier = Modifier.size(13.dp))
            Text("868 MHz · 98%", color = Tertiary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Purge tab content ────────────────────────────────────────────────────────

@Composable
private fun PurgeTab(
    steps:    List<PurgeStep>,
    tabLabel: String,
    showTimer: Boolean
) {
    var activeStep      by remember { mutableIntStateOf(-1) }
    var completedSteps  by remember { mutableStateOf(setOf<Int>()) }
    var isRunning       by remember { mutableStateOf(false) }
    var isComplete      by remember { mutableStateOf(false) }
    var timerSeconds    by remember { mutableIntStateOf(0) }
    var timerRunning    by remember { mutableStateOf(false) }
    val scope           = rememberCoroutineScope()

    LaunchedEffect(isRunning) {
        if (isRunning) {
            steps.forEachIndexed { index, step ->
                activeStep = index
                delay(step.durationMs)
                completedSteps = completedSteps + index
            }
            activeStep = -1
            isRunning  = false
            isComplete = true
        }
    }

    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            while (timerRunning) {
                delay(1000)
                timerSeconds++
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Overall progress bar
        if (isRunning || isComplete) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Progreso general", color = OnSurfaceVar, fontSize = 12.sp)
                    Text(
                        if (isComplete) "Completado" else "${completedSteps.size} / ${steps.size}",
                        color = if (isComplete) Tertiary else Primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                LinearProgressIndicator(
                    progress = { completedSteps.size.toFloat() / steps.size },
                    modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color     = if (isComplete) Tertiary else Primary,
                    trackColor = OutlineVar
                )
            }
        }

        // Optional timer (radiator tab)
        if (showTimer) {
            TimerCard(
                seconds  = timerSeconds,
                running  = timerRunning,
                onToggle = { timerRunning = !timerRunning }
            )
        }

        // Steps
        steps.forEachIndexed { index, step ->
            PurgeStepCard(
                number      = index + 1,
                step        = step,
                isActive    = activeStep == index,
                isCompleted = completedSteps.contains(index),
                isPending   = activeStep < index && !completedSteps.contains(index)
            )
        }

        // Completion card
        AnimatedVisibility(
            visible = isComplete,
            enter   = fadeIn() + expandVertically()
        ) {
            CompletionCard(onRecordMaintenance = {})
        }

        // Action button
        if (!isComplete) {
            Button(
                onClick = {
                    if (!isRunning) {
                        completedSteps = setOf()
                        activeStep     = -1
                        isRunning      = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isRunning,
                colors  = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) OutlineVar else PrimaryContainer,
                    contentColor   = OnPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = OnSurface
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Proceso en curso...", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar $tabLabel", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Step card ────────────────────────────────────────────────────────────────

@Composable
private fun PurgeStepCard(
    number:      Int,
    step:        PurgeStep,
    isActive:    Boolean,
    isCompleted: Boolean,
    isPending:   Boolean,
) {
    val borderColor = when {
        isCompleted -> Tertiary.copy(alpha = 0.5f)
        isActive    -> Primary.copy(alpha = 0.5f)
        else        -> OutlineVar
    }
    val containerColor = when {
        isCompleted -> TertiaryContainer.copy(alpha = 0.08f)
        isActive    -> Primary.copy(alpha = 0.06f)
        else        -> SurfaceContLow
    }

    Surface(
        shape  = RoundedCornerShape(14.dp),
        color  = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Step number / status icon
                Surface(
                    shape = CircleShape,
                    color = when {
                        isCompleted -> TertiaryContainer
                        isActive    -> PrimaryContainer
                        else        -> SurfaceContHighest
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isCompleted) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = OnTertiary, modifier = Modifier.size(18.dp))
                        } else if (isActive) {
                            Icon(step.icon, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(18.dp))
                        } else {
                            Text("$number", color = OnSurfaceVar, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        step.title,
                        color      = if (isPending) OnSurfaceVar else OnSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 14.sp
                    )
                    Text(
                        step.description,
                        color    = OnSurfaceVar,
                        fontSize = 12.sp
                    )
                }
            }

            // Active step progress bar
            if (isActive) {
                val infiniteTransition = rememberInfiniteTransition(label = "progress")
                val progress by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(step.durationMs.toInt(), easing = LinearEasing),
                        RepeatMode.Restart
                    ),
                    label = "stepProgress"
                )
                LinearProgressIndicator(
                    progress  = { progress },
                    modifier  = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color     = Primary,
                    trackColor = OutlineVar
                )
            }
        }
    }
}

// ── Timer card ───────────────────────────────────────────────────────────────

@Composable
private fun TimerCard(seconds: Int, running: Boolean, onToggle: () -> Unit) {
    val minutes  = seconds / 60
    val secs     = seconds % 60
    val timeText = "%02d:%02d".format(minutes, secs)

    Surface(
        shape  = RoundedCornerShape(14.dp),
        color  = SurfaceContHigh,
        border = BorderStroke(1.dp, OutlineVar)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, contentDescription = null, tint = Secondary, modifier = Modifier.size(22.dp))
                Column {
                    Text("Temporizador", color = OnSurfaceVar, fontSize = 11.sp)
                    Text(timeText, color = OnSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
            TextButton(
                onClick = onToggle,
                colors  = ButtonDefaults.textButtonColors(contentColor = Primary)
            ) {
                Icon(
                    if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (running) "Pausar" else "Iniciar", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Completion card ──────────────────────────────────────────────────────────

@Composable
private fun CompletionCard(onRecordMaintenance: () -> Unit) {
    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = TertiaryContainer.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, Tertiary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Tertiary, modifier = Modifier.size(32.dp))
                Column {
                    Text("Purga completada", color = Tertiary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("El sistema ha sido purgado correctamente.", color = OnSurfaceVar, fontSize = 12.sp)
                }
            }

            // Safety checklist reminder
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SurfaceContHigh
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Verificaciones post-purga", color = OnSurfaceVar, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    ChecklistItem("Presión entre 1,2 y 1,5 bar")
                    ChecklistItem("Sin goteos en purgadores ni válvulas")
                    ChecklistItem("Caldera encendida y funcionando")
                }
            }

            Button(
                onClick  = onRecordMaintenance,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = TertiaryContainer, contentColor = OnTertiary),
                shape    = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.Assignment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Registrar en mantenimiento", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ChecklistItem(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Tertiary, modifier = Modifier.size(16.dp))
        Text(text, color = OnSurface, fontSize = 12.sp)
    }
}

@Composable
private fun CalibrationLaunchTab(onNavigateToCalibration: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Icon(Icons.Filled.Tune, contentDescription = null, tint = Primary, modifier = Modifier.size(56.dp))
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Calibración de Válvulas",
                color = OnSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Text(
                "Calibra las válvulas termostáticas de cada radiador para garantizar un control preciso de la temperatura por zona.",
                color = OnSurfaceVar,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Button(
            onClick = onNavigateToCalibration,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color(0xFF003351)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Iniciar calibración", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}
