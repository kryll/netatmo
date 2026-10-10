package com.arsys.netatmo.ui.screens.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
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

private val CalBgColor = Color(0xFF101419)
private val CalCardColor = Color(0xFF1C2025)
private val CalPrimaryColor = Color(0xFF93CCFF)
private val CalTextPrimary = Color(0xFFE8EDF2)
private val CalTextSecondary = Color(0xFF8A9BB0)
private val CalSuccessColor = Color(0xFF4CAF50)

private val rooms = listOf("Salón", "Dormitorio", "Cocina", "Baño")

private val calibrationSteps = listOf(
    "Cerrando válvula...",
    "Detectando rango de movimiento...",
    "Estableciendo posición óptima...",
    "Calibración completada"
)

private const val CAL_STEP_DURATION_MS = 3000L

enum class CalibrationState { IDLE, CALIBRATING, COMPLETE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValveCalibrationScreen(onBack: () -> Unit = {}) {
    var selectedRoom by remember { mutableStateOf<String?>(null) }
    var calibrationState by remember { mutableStateOf(CalibrationState.IDLE) }
    var currentStepIndex by remember { mutableStateOf(-1) }
    var completedSteps by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var valvePosition by remember { mutableStateOf(45f) }
    var calibrationProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(calibrationState) {
        if (calibrationState == CalibrationState.CALIBRATING) {
            completedSteps = emptySet()
            currentStepIndex = -1
            calibrationProgress = 0f

            for (i in calibrationSteps.indices) {
                currentStepIndex = i
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < CAL_STEP_DURATION_MS) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val stepFraction = elapsed.toFloat() / CAL_STEP_DURATION_MS
                    calibrationProgress = (i + stepFraction) / calibrationSteps.size
                    val targetPosition = 75f
                    valvePosition = 45f + (targetPosition - 45f) * calibrationProgress
                    delay(50)
                }

                completedSteps = completedSteps + i
                calibrationProgress = (i + 1f) / calibrationSteps.size
            }

            valvePosition = 75f
            calibrationState = CalibrationState.COMPLETE
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Calibración de Válvula",
                        color = CalTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = CalPrimaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CalCardColor
                )
            )
        },
        containerColor = CalBgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RoomSelectorCard(
                selectedRoom = selectedRoom,
                onRoomSelected = { room ->
                    if (room != selectedRoom) {
                        selectedRoom = room
                        calibrationState = CalibrationState.IDLE
                        completedSteps = emptySet()
                        currentStepIndex = -1
                        valvePosition = 45f
                        calibrationProgress = 0f
                    }
                }
            )

            if (selectedRoom != null) {
                ValveStatusCard(
                    roomName = selectedRoom!!,
                    valvePosition = valvePosition,
                    calibrationState = calibrationState,
                    currentStepIndex = currentStepIndex,
                    completedSteps = completedSteps,
                    calibrationProgress = calibrationProgress,
                    onCalibrate = {
                        if (calibrationState == CalibrationState.IDLE) {
                            calibrationState = CalibrationState.CALIBRATING
                        }
                    }
                )

                if (calibrationState == CalibrationState.COMPLETE) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically()
                    ) {
                        CalibrationSuccessCard()
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RoomSelectorCard(
    selectedRoom: String?,
    onRoomSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CalCardColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Seleccionar habitación",
                color = CalTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Elige la habitación cuya válvula deseas calibrar",
                color = CalTextSecondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rooms.forEach { room ->
                    RoomItem(
                        name = room,
                        isSelected = room == selectedRoom,
                        onClick = { onRoomSelected(room) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomItem(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) CalPrimaryColor.copy(alpha = 0.15f)
                else Color(0xFF252D35)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isSelected) CalPrimaryColor else CalTextSecondary)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = name,
            color = if (isSelected) CalPrimaryColor else CalTextPrimary,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = if (isSelected) CalPrimaryColor else CalTextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ValveStatusCard(
    roomName: String,
    valvePosition: Float,
    calibrationState: CalibrationState,
    currentStepIndex: Int,
    completedSteps: Set<Int>,
    calibrationProgress: Float,
    onCalibrate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CalCardColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Válvula - $roomName",
                    color = CalTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (calibrationState) {
                                CalibrationState.IDLE -> Color(0xFF252D35)
                                CalibrationState.CALIBRATING -> CalPrimaryColor.copy(alpha = 0.2f)
                                CalibrationState.COMPLETE -> CalSuccessColor.copy(alpha = 0.2f)
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (calibrationState) {
                            CalibrationState.IDLE -> "Sin calibrar"
                            CalibrationState.CALIBRATING -> "Calibrando"
                            CalibrationState.COMPLETE -> "Calibrada"
                        },
                        color = when (calibrationState) {
                            CalibrationState.IDLE -> CalTextSecondary
                            CalibrationState.CALIBRATING -> CalPrimaryColor
                            CalibrationState.COMPLETE -> CalSuccessColor
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Posición actual: ${valvePosition.toInt()}%",
                color = CalTextSecondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = valvePosition,
                onValueChange = {},
                valueRange = 0f..100f,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    disabledThumbColor = CalPrimaryColor,
                    disabledActiveTrackColor = CalPrimaryColor,
                    disabledInactiveTrackColor = Color(0xFF252D35)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Cerrada (0%)", color = CalTextSecondary, fontSize = 11.sp)
                Text(text = "Abierta (100%)", color = CalTextSecondary, fontSize = 11.sp)
            }

            if (calibrationState == CalibrationState.CALIBRATING) {
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Progreso",
                        color = CalTextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${(calibrationProgress * 100).toInt()}%",
                        color = CalPrimaryColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { calibrationProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = CalPrimaryColor,
                    trackColor = Color(0xFF252D35),
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    calibrationSteps.forEachIndexed { index, step ->
                        AnimatedVisibility(
                            visible = index <= currentStepIndex,
                            enter = fadeIn() + slideInVertically()
                        ) {
                            CalibrationStepRow(
                                text = step,
                                isComplete = index in completedSteps,
                                isActive = index == currentStepIndex && index !in completedSteps
                            )
                        }
                    }
                }
            }

            if (calibrationState == CalibrationState.IDLE) {
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onCalibrate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CalPrimaryColor)
                ) {
                    Text(
                        text = "CALIBRAR",
                        color = Color(0xFF101419),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CalibrationStepRow(text: String, isComplete: Boolean, isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "cal_step_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cal_step_alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isComplete -> CalSuccessColor
                        isActive -> CalPrimaryColor.copy(alpha = alpha)
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
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = text,
            color = when {
                isComplete -> CalTextPrimary
                isActive -> CalPrimaryColor
                else -> CalTextSecondary
            },
            fontSize = 13.sp,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun CalibrationSuccessCard() {
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
                tint = CalSuccessColor,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "Válvula calibrada correctamente",
                    color = CalSuccessColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "La válvula ha sido calibrada y configurada en su posición óptima de funcionamiento.",
                    color = Color(0xFF81C784),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}
