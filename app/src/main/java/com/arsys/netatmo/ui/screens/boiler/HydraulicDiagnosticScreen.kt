package com.arsys.netatmo.ui.screens.boiler

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

private val BgColor = Color(0xFF101419)
private val CardColor = Color(0xFF1C2028)
private val Primary = Color(0xFF93CCFF)
private val OnSurface = Color(0xFFE0E2EA)
private val OnSurfaceVariant = Color(0xFFBFC7D2)
private val SuccessGreen = Color(0xFF62DF7D)
private val OutlineVariant = Color(0xFF3F4850)

private val TOTAL_SECONDS = 60
private val CHECK_INTERVAL_SECONDS = 8

private val diagnosticChecks = listOf(
    "Verificando presión...",
    "Comprobando modulación...",
    "Analizando circuito primario...",
    "Verificando válvulas de zona...",
    "Comprobando bomba de circulación...",
    "Analizando retorno...",
    "Generando informe..."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydraulicDiagnosticScreen(
    navController: NavController
) {
    var secondsElapsed by remember { mutableIntStateOf(0) }
    var isComplete by remember { mutableStateOf(false) }
    val visibleChecks = remember { mutableStateListOf<String>() }

    // Countdown timer
    LaunchedEffect(Unit) {
        while (secondsElapsed < TOTAL_SECONDS) {
            delay(1000L)
            secondsElapsed++

            // Reveal checks every ~8 seconds
            val checkIndex = (secondsElapsed / CHECK_INTERVAL_SECONDS)
                .coerceAtMost(diagnosticChecks.size - 1)
            if (checkIndex >= visibleChecks.size && checkIndex < diagnosticChecks.size) {
                visibleChecks.add(diagnosticChecks[checkIndex])
            }

            if (secondsElapsed >= TOTAL_SECONDS) {
                // Ensure all checks are visible at completion
                diagnosticChecks.forEach { check ->
                    if (!visibleChecks.contains(check)) visibleChecks.add(check)
                }
                isComplete = true
            }
        }
    }

    val secondsRemaining = (TOTAL_SECONDS - secondsElapsed).coerceAtLeast(0)
    val progressFraction = secondsElapsed / TOTAL_SECONDS.toFloat()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Diagnóstico Hidráulico",
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = OnSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgColor)
            )
        },
        containerColor = BgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Circular countdown timer
            CircularCountdownTimer(
                secondsRemaining = secondsRemaining,
                progressFraction = progressFraction,
                isComplete = isComplete
            )

            // Linear progress bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isComplete) "Completado" else "En progreso",
                        fontSize = 13.sp,
                        color = if (isComplete) SuccessGreen else OnSurfaceVariant
                    )
                    Text(
                        text = "${(progressFraction * 100).toInt()}%",
                        fontSize = 13.sp,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (isComplete) SuccessGreen else Primary,
                    trackColor = OutlineVariant
                )
            }

            // Live checks list
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Verificaciones",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    visibleChecks.forEachIndexed { index, check ->
                        val isDone = isComplete || index < visibleChecks.size - 1
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically { it / 2 }
                        ) {
                            CheckItem(
                                text = check,
                                isDone = isDone,
                                isActive = !isDone && !isComplete
                            )
                        }
                        if (index < visibleChecks.size - 1) {
                            Divider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = OutlineVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                    if (visibleChecks.isEmpty()) {
                        Text(
                            text = "Iniciando diagnóstico...",
                            fontSize = 14.sp,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            // Completion card
            AnimatedVisibility(
                visible = isComplete,
                enter = fadeIn(animationSpec = tween(600)) +
                        expandVertically(animationSpec = tween(600))
            ) {
                CompletionCard()
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CircularCountdownTimer(
    secondsRemaining: Int,
    progressFraction: Float,
    isComplete: Boolean
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 800, easing = LinearEasing),
        label = "progress_anim"
    )

    val arcColor = if (isComplete) SuccessGreen else Primary

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(180.dp)
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.size(180.dp)
        ) {
            val strokeWidth = 12.dp.toPx()
            val inset = strokeWidth / 2f

            // Track
            drawArc(
                color = OutlineVariant,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    size.width - strokeWidth,
                    size.height - strokeWidth
                )
            )

            // Progress arc
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    size.width - strokeWidth,
                    size.height - strokeWidth
                )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isComplete) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Completado",
                    tint = SuccessGreen,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Text(
                    text = "$secondsRemaining",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                Text(
                    text = "seg",
                    fontSize = 14.sp,
                    color = OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CheckItem(
    text: String,
    isDone: Boolean,
    isActive: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isDone) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "OK",
                tint = SuccessGreen,
                modifier = Modifier.size(20.dp)
            )
        } else if (isActive) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Primary
            )
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(OutlineVariant)
            )
        }
        Text(
            text = text,
            fontSize = 14.sp,
            color = when {
                isDone -> OnSurface
                isActive -> Primary
                else -> OnSurfaceVariant
            },
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun CompletionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Diagnóstico completado",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = SuccessGreen
                )
            }

            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f))

            SummaryRow(label = "Sistema", value = "OK", valueColor = SuccessGreen)
            SummaryRow(label = "Presión", value = "Normal", valueColor = SuccessGreen)
            SummaryRow(label = "Circulación", value = "Correcta", valueColor = SuccessGreen)
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = OnSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(valueColor)
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        }
    }
}
