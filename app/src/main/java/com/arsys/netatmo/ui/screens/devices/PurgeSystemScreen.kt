package com.arsys.netatmo.ui.screens.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val PurgeBgColor = Color(0xFF101419)
private val PurgeCardColor = Color(0xFF1C2025)
private val PurgePrimaryColor = Color(0xFF93CCFF)
private val PurgeTextPrimary = Color(0xFFE8EDF2)
private val PurgeTextSecondary = Color(0xFF8A9BB0)
private val PurgeSuccessColor = Color(0xFF4CAF50)
private val PurgeWarningColor = Color(0xFFFF9800)

private val purgeSteps = listOf(
    "Abriendo válvula de purga...",
    "Expulsando aire...",
    "Comprobando presión...",
    "Cerrando válvula...",
    "Verificando estabilización..."
)

private const val STEP_DURATION_MS = 5000L

enum class PurgeState { IDLE, RUNNING, COMPLETE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurgeSystemScreen(onBack: () -> Unit = {}) {
    var purgeState by remember { mutableStateOf(PurgeState.IDLE) }
    var currentStepIndex by remember { mutableStateOf(-1) }
    var completedSteps by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var currentPressure by remember { mutableStateOf(1.2f) }
    var overallProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(purgeState) {
        if (purgeState == PurgeState.RUNNING) {
            val startPressure = 1.2f
            val endPressure = 1.8f
            val totalSteps = purgeSteps.size

            for (i in purgeSteps.indices) {
                currentStepIndex = i
                val stepStartTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - stepStartTime < STEP_DURATION_MS) {
                    val elapsed = System.currentTimeMillis() - stepStartTime
                    val stepFraction = elapsed.toFloat() / STEP_DURATION_MS
                    val totalFraction = (i + stepFraction) / totalSteps
                    overallProgress = totalFraction
                    currentPressure = startPressure + (endPressure - startPressure) * totalFraction
                    delay(50)
                }

                completedSteps = completedSteps + i
                overallProgress = (i + 1f) / totalSteps
            }

            currentPressure = endPressure
            overallProgress = 1f
            purgeState = PurgeState.COMPLETE
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Purga del Sistema",
                        color = PurgeTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = PurgePrimaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PurgeCardColor
                )
            )
        },
        containerColor = PurgeBgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IntroPurgeCard()

            PressureGaugeCard(
                pressure = currentPressure,
                isRunning = purgeState == PurgeState.RUNNING
            )

            if (purgeState != PurgeState.IDLE) {
                ProgressCard(
                    overallProgress = overallProgress,
                    currentStepIndex = currentStepIndex,
                    completedSteps = completedSteps,
                    isComplete = purgeState == PurgeState.COMPLETE
                )
            }

            if (purgeState == PurgeState.COMPLETE) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically()
                ) {
                    SuccessCard()
                }
            }

            if (purgeState == PurgeState.IDLE) {
                Button(
                    onClick = { purgeState = PurgeState.RUNNING },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurgeWarningColor)
                ) {
                    Text(
                        text = "INICIAR PURGA",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun IntroPurgeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PurgeCardColor)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = PurgePrimaryColor,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "¿Qué es la purga del sistema?",
                    color = PurgeTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "La purga elimina el aire atrapado en el circuito hidráulico de calefacción, " +
                            "mejorando la eficiencia del sistema y evitando ruidos. Se recomienda realizar " +
                            "esta operación al inicio de la temporada de calefacción o cuando detectes " +
                            "ruidos en los radiadores.\n\n" +
                            "El proceso tarda aproximadamente 25 segundos y el sistema se gestionará " +
                            "automáticamente. No es necesario ningún ajuste manual.",
                    color = PurgeTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun PressureGaugeCard(pressure: Float, isRunning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PurgeCardColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Presión del sistema",
                color = PurgeTextSecondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { ((pressure - 0f) / 3f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(120.dp),
                    color = when {
                        pressure < 1.0f -> Color(0xFFF44336)
                        pressure > 2.5f -> Color(0xFFF44336)
                        else -> PurgeSuccessColor
                    },
                    strokeWidth = 10.dp,
                    trackColor = Color(0xFF252D35),
                    strokeCap = StrokeCap.Round
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%.1f".format(pressure),
                        color = PurgeTextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "bar",
                        color = PurgeTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PressureIndicator("Mín.", "1.0 bar")
                PressureIndicator("Óptima", "1.5-2.0 bar")
                PressureIndicator("Máx.", "3.0 bar")
            }

            if (isRunning) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Ajustando presión...",
                    color = PurgePrimaryColor,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun PressureIndicator(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = PurgeTextSecondary, fontSize = 11.sp)
        Text(text = value, color = PurgeTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProgressCard(
    overallProgress: Float,
    currentStepIndex: Int,
    completedSteps: Set<Int>,
    isComplete: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PurgeCardColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Progreso de la purga",
                    color = PurgeTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${(overallProgress * 100).toInt()}%",
                    color = PurgePrimaryColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { overallProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = PurgePrimaryColor,
                trackColor = Color(0xFF252D35),
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(20.dp))

            purgeSteps.forEachIndexed { index, step ->
                AnimatedVisibility(
                    visible = index <= currentStepIndex || isComplete,
                    enter = fadeIn() + slideInVertically()
                ) {
                    PurgeStepRow(
                        text = step,
                        isComplete = index in completedSteps || isComplete,
                        isActive = index == currentStepIndex && !isComplete
                    )
                }
                if (index < purgeSteps.lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun PurgeStepRow(text: String, isComplete: Boolean, isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "step_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "step_alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isComplete -> PurgeSuccessColor
                        isActive -> PurgePrimaryColor.copy(alpha = alpha)
                        else -> Color(0xFF252D35)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isComplete) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = text,
            color = when {
                isComplete -> PurgeTextPrimary
                isActive -> PurgePrimaryColor
                else -> PurgeTextSecondary
            },
            fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun SuccessCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2E1A))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = PurgeSuccessColor,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "Purga completada",
                    color = PurgeSuccessColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Presión estabilizada en 1.8 bar",
                    color = Color(0xFF81C784),
                    fontSize = 13.sp
                )
            }
        }
    }
}
