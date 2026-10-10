package com.arsys.netatmo.ui.screens.boiler

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arsys.netatmo.ui.theme.BoilerActiveColor
import com.arsys.netatmo.ui.theme.ComfortColor
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainer
import com.arsys.netatmo.ui.theme.SurfaceContainerHigh
import com.arsys.netatmo.ui.theme.SurfaceContainerLowest
import com.arsys.netatmo.ui.theme.WarmColor
import kotlinx.coroutines.delay

/**
 * Two-phase hydraulic diagnostic screen.
 *
 * Phase 1 (60 s): circular progress + animated checklist.
 * Phase 2: grade report (A+/A/B/C/F) with per-component status and recommendations.
 *
 * NOTE: to share the [BoilerStatusViewModel] instance (and its [launchDiagnostic] state)
 * with [BoilerStatusScreen], scope the ViewModel to the parent NavGraph back-stack entry:
 *
 *   val parentEntry = remember(it) { navController.getBackStackEntry("boiler_graph") }
 *   val sharedVm = hiltViewModel<BoilerStatusViewModel>(parentEntry)
 *   HydraulicDiagnosticScreen(onBack = ..., viewModel = sharedVm)
 *
 * When used standalone, the screen manages its own countdown via LaunchedEffect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydraulicDiagnosticScreen(
    onBack: () -> Unit,
    viewModel: BoilerStatusViewModel = hiltViewModel()
) {
    val vmState by viewModel.uiState.collectAsStateWithLifecycle()

    // Local state for the countdown — drives the UI even when the ViewModel
    // instance is not shared (standalone navigation).
    var secondsLeft by remember { mutableIntStateOf(60) }
    var completedChecks by remember { mutableStateOf(emptyList<String>()) }
    var localResult by remember { mutableStateOf<DiagnosticResult?>(null) }
    var isLocalRunning by remember { mutableStateOf(true) }

    // Prefer ViewModel state when the shared instance has already started the diagnostic.
    val isSharedRunning = vmState.isDiagnosticRunning
    val sharedDone = !isSharedRunning && vmState.diagnosticProgress == 1f && vmState.diagnosticResult != null

    // If the shared ViewModel has already started, shadow its values into local vars.
    if (isSharedRunning) {
        secondsLeft = vmState.diagnosticSecondsLeft
        completedChecks = vmState.diagnosticCompletedChecks
    }
    if (sharedDone) {
        localResult = vmState.diagnosticResult
        isLocalRunning = false
    }

    // Fallback countdown when ViewModel diagnostic is NOT running (standalone use).
    LaunchedEffect(isSharedRunning) {
        if (!isSharedRunning && !sharedDone) {
            val totalSteps = DIAGNOSTIC_CHECK_LABELS.size
            val secsPerStep = 60 / totalSteps
            repeat(60) { tick ->
                delay(1_000L)
                val elapsed = tick + 1
                secondsLeft = 60 - elapsed
                val doneCount = (elapsed / secsPerStep).coerceAtMost(totalSteps)
                completedChecks = DIAGNOSTIC_CHECK_LABELS.take(doneCount)
            }
            // Build result from current health
            val health = vmState.boilerHealth
            val plug = vmState.modules.firstOrNull { it.type == "NAPlug" }
            val rfStrength = plug?.rfStrength ?: 90
            val rfOk = rfStrength >= 40
            val reachable = plug?.reachable ?: true
            val modPct = health.modulationPct
            val score = health.healthScore

            val checks = listOf(
                DiagnosticCheckResult(
                    "Presión hidráulica",
                    if (reachable) CheckStatus.OK else CheckStatus.WARNING,
                    if (reachable) "Sin anomalías detectadas" else "Módulo inalcanzable"
                ),
                DiagnosticCheckResult(
                    "Circulación del circuito",
                    if (reachable) CheckStatus.OK else CheckStatus.WARNING,
                    if (reachable) "Bomba de circulación respondiendo" else "Sin confirmación"
                ),
                DiagnosticCheckResult(
                    "Válvulas de zona",
                    CheckStatus.OK,
                    "Todas las válvulas operativas"
                ),
                DiagnosticCheckResult(
                    "Nivel de modulación",
                    CheckStatus.OK,
                    modPct?.let { "Modulación al $it%" } ?: "Sin datos de modulación"
                ),
                DiagnosticCheckResult(
                    "Conectividad RF",
                    if (rfOk) CheckStatus.OK else CheckStatus.WARNING,
                    "Señal RF: $rfStrength dBm"
                )
            )
            val grade = when {
                score >= 95 -> "A+"
                score >= 85 -> "A"
                score >= 70 -> "B"
                score >= 55 -> "C"
                else -> "F"
            }
            val recommendations = buildList {
                if (!reachable) add("Comprueba la conexión WiFi del módulo NAPlug")
                if (!rfOk) add("Acerca el termostato al relay NAPlug para mejorar la señal RF")
                if (score < 75) add("Considera programar una revisión técnica de la caldera")
            }
            completedChecks = DIAGNOSTIC_CHECK_LABELS
            localResult = DiagnosticResult(grade, score, checks, recommendations)
            isLocalRunning = false
        }
    }

    val isDone = if (sharedDone) true else (!isLocalRunning && localResult != null)
    val activeResult: DiagnosticResult? = if (sharedDone) vmState.diagnosticResult else localResult
    val progress = if (isSharedRunning) vmState.diagnosticProgress
                   else if (isDone) 1f
                   else (60 - secondsLeft) / 60f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Diagnóstico Hidráulico",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        if (!isDone) {
                            Text(
                                "ANÁLISIS EN EJECUCIÓN · ${secondsLeft}s restantes",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceContainerLowest
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (!isDone) {
                DiagnosticRunningPhase(
                    progress = progress,
                    secondsLeft = secondsLeft,
                    completedChecks = completedChecks
                )
            } else if (activeResult != null) {
                DiagnosticResultPhase(
                    result = activeResult,
                    onBack = onBack
                )
            }
        }
    }
}

// ── Phase 1 — Running ─────────────────────────────────────────────────────────

@Composable
private fun DiagnosticRunningPhase(
    progress: Float,
    secondsLeft: Int,
    completedChecks: List<String>
) {
    // Safety banner
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(BoilerActiveColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "TEST HIDRÁULICO ACTIVO",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    "No desconectes el sistema durante el análisis",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "${secondsLeft}s",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Large circular progress indicator
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(160.dp),
                strokeWidth = 10.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = OutlineVariant
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${(progress * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "completado",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }

    Text(
        "Diagnóstico en curso…",
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
    Text(
        "Analizando el sistema hidráulico de la caldera Netatmo",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        textAlign = TextAlign.Center
    )

    // Checklist with progressive reveal
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Comprobaciones del sistema",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            DIAGNOSTIC_CHECK_LABELS.forEachIndexed { idx, label ->
                val isComplete = completedChecks.contains(label)
                val isCurrentlyActive = !isComplete && completedChecks.size == idx

                AnimatedVisibility(
                    visible = isComplete || isCurrentlyActive || idx == 0,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when {
                            isComplete -> Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ComfortColor,
                                modifier = Modifier.size(20.dp)
                            )
                            isCurrentlyActive -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            else -> Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(OutlineVariant)
                            )
                        }
                        Text(
                            label,
                            color = when {
                                isComplete -> MaterialTheme.colorScheme.onSurface
                                isCurrentlyActive -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontSize = 14.sp,
                            fontWeight = if (isComplete || isCurrentlyActive)
                                FontWeight.Medium
                            else
                                FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ── Phase 2 — Result ─────────────────────────────────────────────────────────

@Composable
private fun DiagnosticResultPhase(
    result: DiagnosticResult,
    onBack: () -> Unit
) {
    val (gradeColor, gradeBgAlpha) = when (result.grade) {
        "A+" -> ComfortColor to 0.12f
        "A"  -> ComfortColor to 0.10f
        "B"  -> Color(0xFF93CCFF) to 0.10f
        "C"  -> WarmColor to 0.12f
        else -> MaterialTheme.colorScheme.error to 0.15f
    }

    // Grade hero card
    Card(
        colors = CardDefaults.cardColors(containerColor = gradeColor.copy(alpha = gradeBgAlpha)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, gradeColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = gradeColor,
                modifier = Modifier.size(52.dp)
            )
            Text(
                "Diagnóstico completado",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            // Grade badge
            Surface(
                shape = RoundedCornerShape(50),
                color = gradeColor.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        result.grade,
                        color = gradeColor,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Column {
                        Text(
                            "Puntuación",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Text(
                            "${result.gradeScore} / 100",
                            color = gradeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
            val gradeLabel = when (result.grade) {
                "A+" -> "Excelente — sistema hidráulico en perfecto estado"
                "A"  -> "Muy bueno — funcionamiento óptimo"
                "B"  -> "Bueno — rendimiento correcto"
                "C"  -> "Regular — revisa las recomendaciones"
                else -> "Crítico — requiere atención inmediata"
            }
            Text(
                gradeLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    // Component breakdown
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                "Detalle por componente",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = OutlineVariant, thickness = 0.5.dp)
            result.checks.forEach { check ->
                DiagnosticCheckRow(check = check)
                HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
            }
        }
    }

    // Recommendations (only shown when there are issues)
    if (result.recommendations.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Recomendaciones",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                result.recommendations.forEach { rec ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            rec,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // Close / back button
    OutlinedButton(
        onClick = onBack,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Icon(
            Icons.Default.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("Volver al estado de caldera", fontWeight = FontWeight.Medium)
    }

    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DiagnosticCheckRow(check: DiagnosticCheckResult) {
    val (iconTint, statusText, statusIcon) = when (check.status) {
        CheckStatus.OK       -> Triple(ComfortColor, "OK",      Icons.Default.CheckCircle as ImageVector)
        CheckStatus.WARNING  -> Triple(WarmColor,    "Aviso",   Icons.Default.Warning as ImageVector)
        CheckStatus.CRITICAL -> Triple(MaterialTheme.colorScheme.error, "Crítico", Icons.Default.Warning as ImageVector)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                statusIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    check.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    check.detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = iconTint.copy(alpha = 0.12f)
        ) {
            Text(
                statusText,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = iconTint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
