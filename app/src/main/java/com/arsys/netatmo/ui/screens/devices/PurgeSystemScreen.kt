package com.arsys.netatmo.ui.screens.devices

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsys.netatmo.ui.theme.InterFamily
import com.arsys.netatmo.ui.theme.PlusJakartaSansFamily
import kotlinx.coroutines.delay

// ── Design tokens ─────────────────────────────────────────────────────────────
private val BgColor             = Color(0xFF101419)
private val SurfaceLowest       = Color(0xFF0A0E13)
private val SurfaceContLow      = Color(0xFF181C21)
private val SurfaceCont         = Color(0xFF1C2025)
private val SurfaceContHigh     = Color(0xFF262A30)
private val SurfaceContHighest  = Color(0xFF31353B)
private val OutlineVar          = Color(0xFF3F4850)
private val OnSurface           = Color(0xFFE0E2EA)
private val OnSurfaceVar        = Color(0xFFBFC7D2)
private val Primary             = Color(0xFF93CCFF)
private val OnPrimary           = Color(0xFF003351)
private val PrimaryContainer    = Color(0xFF3198DC)
private val Secondary           = Color(0xFFFFB599)
private val SecondaryContainer  = Color(0xFFF66018)
private val Tertiary            = Color(0xFF62DF7D)
private val OnTertiary          = Color(0xFF003914)
private val TertiaryContainer   = Color(0xFF1CA64D)
private val ErrorColor          = Color(0xFFFFB4AB)
private val ErrorContainer      = Color(0xFF93000A)

// ── Step constants ────────────────────────────────────────────────────────────
private val WIZARD_STEPS = listOf("Preparar", "Purgar", "Estabilizar", "Verificar")
private const val ACTIVE_STEP = 1  // Fase 2 de 4

private data class Instruction(val number: Int, val text: String, val boldKey: String)

private val ACTIVE_INSTRUCTIONS = listOf(
    Instruction(1, "Localiza el purgador en la parte superior del radiador.", "purgador"),
    Instruction(2, "Gira la llave ¼ vuelta antihorario hasta oír el silbido de aire.", "¼ vuelta antihorario"),
    Instruction(3, "Mantén abierto hasta que el sonido cese y comience a brotar agua sin burbujas.", "agua sin burbujas"),
)

// ── Main screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurgeSystemScreen(onBack: () -> Unit, onNavigateToCalibration: () -> Unit = {}) {
    var timerSeconds by remember { mutableIntStateOf(0) }
    var check1 by remember { mutableStateOf(false) }
    var check2 by remember { mutableStateOf(false) }
    val allChecked = check1 && check2

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            timerSeconds++
        }
    }

    Scaffold(containerColor = BgColor) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PurgeHeader(onClose = onBack, onAbort = {})

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SystemStatusCard()
                HorizontalStepperCard()
                TelemetryCard(timerSeconds = timerSeconds)
                ActiveInstructionCard()
                SafetyChecklistCard(
                    check1 = check1,
                    check2 = check2,
                    onCheck1 = { check1 = it },
                    onCheck2 = { check2 = it }
                )
                BottomActionsSection(
                    allChecked = allChecked,
                    onContinue = {},
                    onValveClose = {},
                    onCancel = onBack
                )
            }
        }
    }
}

// ── 1. Header ─────────────────────────────────────────────────────────────────

@Composable
private fun PurgeHeader(onClose: () -> Unit, onAbort: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "headerPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(SurfaceLowest)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: close button + two-line title
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SurfaceContHigh.copy(alpha = 0.6f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Cerrar",
                    tint = OnSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Secondary.copy(alpha = pulseAlpha))
                    )
                    Text(
                        text = "RADIADOR SUITE PRINCIPAL",
                        color = Secondary,
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp
                    )
                }
                Text(
                    text = "Purga De Aire En Tiempo Real",
                    color = OnSurface,
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        // Right: Abortar pill + user avatar
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ErrorContainer.copy(alpha = 0.2f))
                    .clickable { onAbort() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = ErrorColor,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    "Abortar",
                    color = ErrorColor,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = OnPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ── 2. System status card ─────────────────────────────────────────────────────

@Composable
private fun SystemStatusCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "statusPing")
    val pingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "pingScale"
    )
    val pingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "pingAlpha"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContLow,
        border = BorderStroke(1.dp, OutlineVar),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Animated ping dot
                Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size((8.dp.value * pingScale.coerceAtMost(2f)).dp)
                            .clip(CircleShape)
                            .background(SecondaryContainer.copy(alpha = pingAlpha))
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Secondary)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "MODO MANTENIMIENTO ACTIVO",
                        color = Secondary,
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = "Bomba de calefacción en pausa preventiva",
                        color = OnSurfaceVar,
                        fontFamily = InterFamily,
                        fontSize = 11.sp
                    )
                }
            }

            // Frequency pill
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SurfaceContHigh)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Sensors, contentDescription = null, tint = Tertiary, modifier = Modifier.size(13.dp))
                Text(
                    "868 MHz · 98%",
                    color = Tertiary,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// ── 3. Horizontal stepper ─────────────────────────────────────────────────────

@Composable
private fun HorizontalStepperCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "stepperPing")
    val pingProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "pingProgress"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContLow,
        border = BorderStroke(1.dp, OutlineVar),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Secuencia Guiada Netatmo",
                    color = OnSurfaceVar,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Primary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "Fase ${ACTIVE_STEP + 1} de ${WIZARD_STEPS.size}",
                        color = Primary,
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            // Steps row with connectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                WIZARD_STEPS.forEachIndexed { index, label ->
                    val isCompleted = index < ACTIVE_STEP
                    val isActive = index == ACTIVE_STEP
                    val isPending = index > ACTIVE_STEP

                    if (index > 0) {
                        // Connector bar aligned to circle center (top 18dp = center of 32dp circle at top + 4dp box padding)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 18.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (index <= ACTIVE_STEP) TertiaryContainer else SurfaceContHighest
                                )
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.alpha(if (isPending) 0.6f else 1f)
                    ) {
                        // Ping ring + circle
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(40.dp)
                        ) {
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp + 16.dp * pingProgress)
                                        .clip(CircleShape)
                                        .background(
                                            PrimaryContainer.copy(alpha = 0.4f * (1f - pingProgress))
                                        )
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isCompleted -> TertiaryContainer
                                            isActive -> Primary
                                            else -> SurfaceContHighest
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    isCompleted -> Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = OnTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    isActive -> Icon(
                                        Icons.Filled.Air,
                                        contentDescription = null,
                                        tint = OnPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    else -> Text(
                                        "${index + 1}",
                                        color = OnSurfaceVar,
                                        fontFamily = InterFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Text(
                            label,
                            color = if (isActive) Primary else OnSurfaceVar,
                            fontFamily = InterFamily,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 60.dp)
                        )
                        if (isActive) {
                            Text(
                                "En Vivo",
                                color = Primary,
                                fontFamily = InterFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 4. Telemetry card ─────────────────────────────────────────────────────────

@Composable
private fun TelemetryCard(timerSeconds: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "telemetry")
    val purgePingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Restart),
        label = "purgePing"
    )
    val purgePingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Restart),
        label = "purgeAlpha"
    )
    val bubbleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "bubble"
    )
    val timerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "timerRot"
    )

    val minutes = timerSeconds / 60
    val secs = timerSeconds % 60
    val timeText = "%02d:%02d".format(minutes, secs)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCont,
        border = BorderStroke(1.dp, OutlineVar),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with inline timer badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                    Text(
                        "Telemetría Válvula Inteligente",
                        color = OnSurface,
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SurfaceContHigh)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier
                            .size(13.dp)
                            .rotate(timerRotation)
                    )
                    Text(
                        timeText,
                        color = OnSurfaceVar,
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                }
            }

            // Radiator schematic
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceLowest),
                contentAlignment = Alignment.Center
            ) {
                RadiatorSchematic(
                    pingScale = purgePingScale,
                    pingAlpha = purgePingAlpha,
                    bubbleProgress = bubbleProgress
                )
            }

            // Valve labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Llave Apertura",
                    color = Secondary,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                Text(
                    "Llave Purga Superior",
                    color = Secondary,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }

            // 3-column metric grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCell(
                    modifier = Modifier.weight(1f),
                    value = "4.5 mm",
                    subLabel = "Vástago",
                    valueColor = Primary
                )
                MetricCell(
                    modifier = Modifier.weight(1f),
                    value = "1.35 bar",
                    subLabel = "Presión Red",
                    valueColor = Tertiary
                )
                MetricCell(
                    modifier = Modifier.weight(1f),
                    value = "21.4°C",
                    subLabel = "Cuerpo Rad.",
                    valueColor = OnSurface
                )
            }
        }
    }
}

@Composable
private fun RadiatorSchematic(pingScale: Float, pingAlpha: Float, bubbleProgress: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Body bounds
        val bodyLeft = w * 0.18f
        val bodyRight = w * 0.78f
        val bodyTop = h * 0.14f
        val bodyBottom = h * 0.86f
        val bodyWidth = bodyRight - bodyLeft
        val bodyHeight = bodyBottom - bodyTop

        // Radiator base
        drawRoundRect(
            color = Color(0xFF1C2025),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(6.dp.toPx())
        )

        // Top and bottom headers
        drawRect(
            color = Color(0xFF262A30),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyWidth, 10.dp.toPx())
        )
        drawRect(
            color = Color(0xFF262A30),
            topLeft = Offset(bodyLeft, bodyBottom - 10.dp.toPx()),
            size = Size(bodyWidth, 10.dp.toPx())
        )

        // Radiator columns (4 vertical sections)
        val colCount = 4
        val colW = bodyWidth / colCount
        for (i in 0 until colCount) {
            val cx = bodyLeft + i * colW + colW * 0.5f
            val colActualW = colW * 0.52f
            val colLeft = cx - colActualW / 2f
            drawRect(
                color = Color(0xFF262A30),
                topLeft = Offset(colLeft, bodyTop + 10.dp.toPx()),
                size = Size(colActualW, bodyHeight - 20.dp.toPx())
            )
        }

        // Left valve (open – primary/blue)
        val leftValveX = bodyLeft - 20.dp.toPx()
        val valveY = h * 0.50f
        drawLine(
            color = Primary.copy(alpha = 0.8f),
            start = Offset(leftValveX + 10.dp.toPx(), valveY),
            end = Offset(bodyLeft, valveY),
            strokeWidth = 3.dp.toPx()
        )
        drawCircle(
            color = Primary.copy(alpha = 0.3f),
            radius = 13.dp.toPx(),
            center = Offset(leftValveX, valveY)
        )
        drawCircle(
            color = Primary,
            radius = 6.dp.toPx(),
            center = Offset(leftValveX, valveY)
        )

        // Right purge point (secondary/orange) with animated ping
        val purgeX = bodyRight + 18.dp.toPx()
        val purgeY = bodyTop + bodyHeight * 0.28f

        drawLine(
            color = Secondary.copy(alpha = 0.6f),
            start = Offset(bodyRight, purgeY),
            end = Offset(purgeX - 8.dp.toPx(), purgeY),
            strokeWidth = 2.dp.toPx()
        )

        // Ping ring
        drawCircle(
            color = SecondaryContainer.copy(alpha = pingAlpha * 0.5f),
            radius = 11.dp.toPx() * pingScale,
            center = Offset(purgeX, purgeY),
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = SecondaryContainer.copy(alpha = 0.25f),
            radius = 11.dp.toPx(),
            center = Offset(purgeX, purgeY)
        )
        drawCircle(
            color = Secondary,
            radius = 5.5.dp.toPx(),
            center = Offset(purgeX, purgeY)
        )

        // Floating air bubbles (animate upward, reset at bottom)
        val bubbleYShift = bubbleProgress * 28.dp.toPx()
        val bubbles = listOf(
            Triple(bodyLeft + bodyWidth * 0.22f, bodyTop + bodyHeight * 0.62f, 3.5.dp.toPx()),
            Triple(bodyLeft + bodyWidth * 0.48f, bodyTop + bodyHeight * 0.50f, 4.dp.toPx()),
            Triple(bodyLeft + bodyWidth * 0.70f, bodyTop + bodyHeight * 0.68f, 3.dp.toPx()),
        )
        bubbles.forEachIndexed { i, (bx, by, br) ->
            val shift = bubbleYShift * (1f + i * 0.3f)
            val bAlpha = (0.5f - bubbleProgress * 0.5f).coerceAtLeast(0f)
            drawCircle(
                color = Primary.copy(alpha = bAlpha),
                radius = br,
                center = Offset(bx, by - shift)
            )
        }
    }
}

@Composable
private fun MetricCell(modifier: Modifier, value: String, subLabel: String, valueColor: Color) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceContHigh)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            value,
            color = valueColor,
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Text(
            subLabel,
            color = Tertiary,
            fontFamily = InterFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp
        )
    }
}

// ── 5. Active instruction card ────────────────────────────────────────────────

@Composable
private fun ActiveInstructionCard() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCont,
        border = BorderStroke(1.dp, OutlineVar),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card header
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SecondaryContainer.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Build,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        "Paso 2: Abrir Purgador Manual",
                        color = OnSurface,
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = OnSurfaceVar,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            "Radiador Suite Principal · Planta 2",
                            color = OnSurfaceVar,
                            fontFamily = InterFamily,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Numbered instruction list
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SurfaceContLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ACTIVE_INSTRUCTIONS.forEach { instruction ->
                        InstructionItem(instruction)
                    }
                }
            }

            // Acoustic sensor row
            AcousticSensorRow()

            // Hydraulic warning
            HydraulicWarningStrip()
        }
    }
}

@Composable
private fun InstructionItem(instruction: Instruction) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(SurfaceContHighest),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${instruction.number}",
                color = Primary,
                fontFamily = InterFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
        Text(
            buildAnnotatedString {
                val parts = instruction.text.split(instruction.boldKey, limit = 2)
                if (parts.size == 2) {
                    append(parts[0])
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = OnSurface)) {
                        append(instruction.boldKey)
                    }
                    append(parts[1])
                } else {
                    append(instruction.text)
                }
            },
            color = OnSurfaceVar,
            fontFamily = InterFamily,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AcousticSensorRow() {
    val infiniteTransition = rememberInfiniteTransition(label = "acoustic")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse"
    )
    val bar0 by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, delayMillis = 0), RepeatMode.Reverse),
        label = "b0"
    )
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, delayMillis = 90), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, delayMillis = 180), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, delayMillis = 120), RepeatMode.Reverse),
        label = "b3"
    )
    val bar4 by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, delayMillis = 40), RepeatMode.Reverse),
        label = "b4"
    )
    val barFractions = listOf(bar0, bar1, bar2, bar3, bar4)
    val barMaxHeights = listOf(16.dp, 26.dp, 36.dp, 22.dp, 13.dp)
    val barColors = listOf(Primary, Primary, Secondary, Primary, Secondary)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceContHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = null,
                    tint = Primary.copy(alpha = pulseAlpha),
                    modifier = Modifier.size(18.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Sensor Acústico Netatmo",
                        color = OnSurface,
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                    Text(
                        "Analizando flujo de aire...",
                        color = OnSurfaceVar,
                        fontFamily = InterFamily,
                        fontSize = 10.sp
                    )
                }
            }

            // Animated volume bars
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.height(38.dp)
            ) {
                barFractions.forEachIndexed { i, fraction ->
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(barMaxHeights[i] * fraction)
                            .clip(RoundedCornerShape(2.dp))
                            .background(barColors[i])
                    )
                }
            }
        }
    }
}

@Composable
private fun HydraulicWarningStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SecondaryContainer.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = Secondary,
            modifier = Modifier.size(16.dp).padding(top = 1.dp)
        )
        Text(
            buildAnnotatedString {
                append("Si la presión desciende por debajo de ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Secondary)) {
                    append("1.0 bar")
                }
                append(", cierra el purgador y añade agua al circuito antes de continuar.")
            },
            color = Secondary,
            fontFamily = InterFamily,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// ── 6. Safety checklist ───────────────────────────────────────────────────────

@Composable
private fun SafetyChecklistCard(
    check1: Boolean,
    check2: Boolean,
    onCheck1: (Boolean) -> Unit,
    onCheck2: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContLow,
        border = BorderStroke(1.dp, OutlineVar),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "VERIFICACIÓN DE CAMPO REQUERIDA",
                color = OnSurfaceVar,
                fontFamily = InterFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp
            )

            // Auto-verified item
            ChecklistItemRow(
                text = "Sistema en modo mantenimiento activo",
                isChecked = true,
                isAutoVerified = true,
                onChecked = {}
            )

            // User interactive items
            ChecklistItemRow(
                text = "Purgador localizado y accesible",
                isChecked = check1,
                isAutoVerified = false,
                onChecked = onCheck1
            )

            ChecklistItemRow(
                text = "Recipiente colocado bajo el purgador",
                isChecked = check2,
                isAutoVerified = false,
                onChecked = onCheck2
            )
        }
    }
}

@Composable
private fun ChecklistItemRow(
    text: String,
    isChecked: Boolean,
    isAutoVerified: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when {
                        isAutoVerified -> TertiaryContainer
                        isChecked -> Primary
                        else -> SurfaceContHighest
                    }
                )
                .clickable(enabled = !isAutoVerified) { onChecked(!isChecked) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = when {
                    isAutoVerified -> OnTertiary
                    isChecked -> OnPrimary
                    else -> Color.Transparent
                },
                modifier = Modifier.size(14.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text,
                color = OnSurface,
                fontFamily = InterFamily,
                fontSize = 13.sp
            )
            if (isAutoVerified) {
                Text(
                    "Comprobado automáticamente",
                    color = Tertiary,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// ── 7. Bottom actions ─────────────────────────────────────────────────────────

@Composable
private fun BottomActionsSection(
    allChecked: Boolean,
    onContinue: () -> Unit,
    onValveClose: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Primary advance button (color changes when checklist complete)
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allChecked) TertiaryContainer else PrimaryContainer,
                contentColor = if (allChecked) OnTertiary else OnPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Hecho, Continuar a Estabilización",
                fontFamily = PlusJakartaSansFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
        }

        // Secondary safety action
        Button(
            onClick = onValveClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceContHigh,
                contentColor = Secondary
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Cerrar Vástago por Precaución",
                fontFamily = InterFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }

        // Danger cancel link
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onCancel() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Cancel,
                contentDescription = null,
                tint = ErrorColor.copy(alpha = 0.9f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                "Reanudar caldera y cancelar purga",
                color = ErrorColor.copy(alpha = 0.9f),
                fontFamily = InterFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
    }
}
