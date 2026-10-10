package com.arsys.netatmo.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arsys.netatmo.R
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainer
import com.arsys.netatmo.ui.theme.SurfaceContainerHigh
import com.arsys.netatmo.ui.theme.WarmColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

// Design tokens — Stitch dark palette
private val Background       = Color(0xFF101419)
private val Primary          = Color(0xFF93CCFF)
private val PrimaryContainer = Color(0xFF3198DC)
private val Secondary        = Color(0xFFFFB599)   // warm / heating
private val SecondaryActive  = Color(0xFFF66018)   // active orange
private val Tertiary         = Color(0xFF62DF7D)   // eco / comfort green
private val TertiaryContainer = Color(0xFF1CA64D)
private val OnSurface        = Color(0xFFE0E2EA)
private val OnSurfaceVariant = Color(0xFFBFC7D2)
private val Warm             = WarmColor            // 0xFFEA580C orange accent

private data class AutoFlow(val whenLabel: String, val thenLabel: String, val color: Color)
private data class SceneItem(val icon: String, val label: String, val temp: String, val color: Color, val bgColor: Color)
private data class ScheduleBlock(val dayCol: Int, val startRow: Int, val endRow: Int, val color: Color)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val totalPages = 9
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == totalPages - 1
    var authDone by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 56.dp)
            ) { page ->
                when (page) {
                    0 -> Slide0Welcome()
                    1 -> Slide1Thermostat()
                    2 -> Slide2Geofence()
                    3 -> Slide3Stats()
                    4 -> Slide4Automations()
                    5 -> Slide5Scenes()
                    6 -> Slide6Schedules()
                    7 -> Slide7Permissions()
                    8 -> Slide8Login(
                        authDone = authDone,
                        onAuthDone = { authDone = true },
                        onComplete = { viewModel.markOnboardingCompleted(); onComplete() }
                    )
                    else -> Slide0Welcome()
                }
            }

            // Navigation bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page indicator: active = primary pill 22×6dp, inactive = outlineVariant 6dp circle
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(totalPages) { index ->
                        val selected = currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (selected) 22.dp else 6.dp, 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (selected) Primary else OutlineVariant)
                        )
                    }
                }

                if (!isLastPage) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentPage > 0) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, OutlineVariant),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = OnSurfaceVariant
                                )
                            ) {
                                Text("Atrás")
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                            },
                            modifier = Modifier.weight(if (currentPage > 0) 2f else 1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryContainer,
                                contentColor = OnSurface
                            )
                        ) {
                            Text(
                                text = when (currentPage) {
                                    0              -> "Empezar"
                                    totalPages - 2 -> "Continuar"
                                    else           -> "Siguiente"
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Skip button
        if (!isLastPage) {
            TextButton(
                onClick = { viewModel.markOnboardingCompleted(); onSkip() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
            ) {
                Text("Saltar", color = OnSurfaceVariant, fontSize = 14.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared slide container
// ---------------------------------------------------------------------------

@Composable
private fun SlideContainer(
    eyebrowText: String,
    eyebrowColor: Color,
    title: String,
    subtitle: String,
    visual: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = eyebrowText.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = eyebrowColor,
            letterSpacing = 1.5.sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        visual()
    }
}

// ---------------------------------------------------------------------------
// Page 0 — Welcome
// ---------------------------------------------------------------------------

@Composable
private fun Slide0Welcome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo in circular surfaceContainerHigh
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = SurfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp)
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        Text(
            text = "Netatmo Smart",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Tu hogar, a la temperatura perfecta",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------
// Page 1 — Thermostat dial
// ---------------------------------------------------------------------------

@Composable
private fun Slide1Thermostat() {
    SlideContainer(
        eyebrowText = "Control",
        eyebrowColor = Primary,
        title = "Temperatura perfecta",
        subtitle = "Ajusta cada zona desde cualquier lugar"
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val strokeWidth = 24f
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val arcTopLeft = Offset(inset, inset)
            val startAngle = 135f
            val totalSweep = 270f
            val fillSweep = totalSweep * 0.55f
            val cx = size.width / 2f
            val cy = size.height / 2f

            // Track — dark outlineVariant
            drawArc(
                color = Color(0xFF3F4850),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            // Fill — primary
            drawArc(
                color = Primary,
                startAngle = startAngle,
                sweepAngle = fillSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawIntoCanvas { canvas ->
                val tempPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#E0E2EA")
                    textSize = 80f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                canvas.nativeCanvas.drawText("21°", cx, cy + 28f, tempPaint)

                val subPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#BFC7D2")
                    textSize = 26f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                canvas.nativeCanvas.drawText("Salón · Confort", cx, cy + 64f, subPaint)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page 2 — Geofence
// ---------------------------------------------------------------------------

@Composable
private fun Slide2Geofence() {
    SlideContainer(
        eyebrowText = "Geovalla",
        eyebrowColor = Tertiary,
        title = "Llega y enciende",
        subtitle = "Tu termostato te detecta y prepara el hogar"
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            // Geofence rings (tertiary)
            listOf(0.42f, 0.62f, 0.80f).forEach { fraction ->
                drawCircle(
                    color = Tertiary.copy(alpha = 0.18f),
                    radius = size.width * fraction / 2f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f)
                )
            }
            // Home zone
            drawCircle(
                color = Tertiary.copy(alpha = 0.12f),
                radius = size.width * 0.18f,
                center = Offset(cx, cy)
            )
            // Phone dot (primary)
            val phoneCx = cx + size.width * 0.28f
            val phoneCy = cy - size.height * 0.24f
            drawCircle(color = Primary, radius = 14f, center = Offset(phoneCx, phoneCy))
            drawCircle(color = Background, radius = 6f, center = Offset(phoneCx, phoneCy))

            // Dashed line to home
            val dx = cx - phoneCx
            val dy = cy - phoneCy
            val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            val ux = dx / dist
            val uy = dy / dist
            val dashLen = 12f
            val gapLen = 8f
            var t = 20f
            while (t < dist - 20f) {
                val end = minOf(t + dashLen, dist - 20f)
                drawLine(
                    color = Primary.copy(alpha = 0.55f),
                    start = Offset(phoneCx + ux * t, phoneCy + uy * t),
                    end = Offset(phoneCx + ux * end, phoneCy + uy * end),
                    strokeWidth = 2f
                )
                t += dashLen + gapLen
            }

            drawIntoCanvas { canvas ->
                val homePaint = android.graphics.Paint().apply {
                    textSize = 40f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                canvas.nativeCanvas.drawText("🏠", cx, cy + 16f, homePaint)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page 3 — Stats / bar chart
// ---------------------------------------------------------------------------

@Composable
private fun Slide3Stats() {
    SlideContainer(
        eyebrowText = "Calendario · Estadísticas",
        eyebrowColor = Secondary,
        title = "Ahorra energía",
        subtitle = "Visualiza el consumo y reduce tu factura"
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 4.dp)
        ) {
            val days = listOf("L", "M", "X", "J", "V", "S", "D")
            val fractions = listOf(0.55f, 0.72f, 0.60f, 0.88f, 0.70f, 0.45f, 0.38f)
            val highlightIndex = 3
            val labelZone = 28.dp.toPx()
            val barZone = size.height - labelZone
            val totalBarW = size.width / days.size
            val barPad = 6.dp.toPx()
            val barW = totalBarW - barPad * 2
            val cornerR = 4.dp.toPx()

            fractions.forEachIndexed { i, fraction ->
                val barH = (barZone - 4.dp.toPx()) * fraction
                val left = i * totalBarW + barPad
                val top = barZone - barH
                drawRoundRect(
                    color = if (i == highlightIndex) SecondaryActive
                            else Primary.copy(alpha = 0.35f),
                    topLeft = Offset(left, top),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(cornerR)
                )
            }

            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#BFC7D2")
                    textSize = 22f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                days.forEachIndexed { i, day ->
                    val x = i * totalBarW + totalBarW / 2f
                    canvas.nativeCanvas.drawText(day, x, size.height - 4f, paint)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page 4 — Automations
// ---------------------------------------------------------------------------

@Composable
private fun Slide4Automations() {
    val flows = listOf(
        AutoFlow("Llego a casa",  "21°C Confort",  Primary),
        AutoFlow("Reunión en casa", "Precalentar", Warm),
        AutoFlow("23:00",         "17°C Noche",    Secondary),
        AutoFlow("Salgo de casa", "15°C Eco",      Tertiary)
    )

    SlideContainer(
        eyebrowText = "Automatizaciones",
        eyebrowColor = Secondary,
        title = "Que funcione solo",
        subtitle = "Escenarios que se adaptan a tu vida"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            flows.forEach { flow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Text(
                                "CUANDO",
                                fontSize = 9.sp,
                                color = OnSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                flow.whenLabel,
                                fontSize = 13.sp,
                                color = OnSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = flow.color,
                        modifier = Modifier.size(20.dp)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(flow.color.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Text(
                                "ENTONCES",
                                fontSize = 9.sp,
                                color = flow.color,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                flow.thenLabel,
                                fontSize = 13.sp,
                                color = flow.color,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page 5 — Scenes
// ---------------------------------------------------------------------------

@Composable
private fun Slide5Scenes() {
    val scenes = listOf(
        SceneItem("☀️", "Confort",  "21°", Warm,     Warm.copy(alpha = 0.10f)),
        SceneItem("🌿", "Eco",      "18°", Tertiary, Tertiary.copy(alpha = 0.10f)),
        SceneItem("🚗", "Ausente",  "15°", Primary,  Primary.copy(alpha = 0.10f)),
        SceneItem("🌙", "Noche",    "17°", Secondary, Secondary.copy(alpha = 0.10f))
    )
    var selected by remember { mutableStateOf(0) }

    SlideContainer(
        eyebrowText = "Escenas",
        eyebrowColor = Warm,
        title = "Un toque, listo",
        subtitle = "Activa el modo perfecto para cada momento"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            scenes.chunked(2).forEachIndexed { rowIdx, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEachIndexed { colIdx, scene ->
                        val globalIdx = rowIdx * 2 + colIdx
                        val isSelected = selected == globalIdx
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selected = globalIdx },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) scene.bgColor else SurfaceContainer,
                            border = if (isSelected)
                                BorderStroke(2.dp, scene.color)
                            else
                                BorderStroke(1.dp, OutlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(scene.icon, fontSize = 28.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    scene.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scene.color
                                )
                                Text(
                                    scene.temp,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page 6 — Weekly schedule grid
// ---------------------------------------------------------------------------

@Composable
private fun Slide6Schedules() {
    val nightColor   = Secondary.copy(alpha = 0.85f)
    val morningColor = Color(0xFFFFB44C)
    val ecoColor     = Tertiary
    val comfortColor = Warm

    val blocks = listOf(
        ScheduleBlock(0, 0, 2, nightColor),   ScheduleBlock(0, 2, 4, morningColor), ScheduleBlock(0, 4, 6, comfortColor),
        ScheduleBlock(1, 0, 2, nightColor),   ScheduleBlock(1, 2, 4, morningColor), ScheduleBlock(1, 4, 6, ecoColor),
        ScheduleBlock(2, 0, 2, nightColor),   ScheduleBlock(2, 2, 5, comfortColor), ScheduleBlock(2, 5, 6, nightColor),
        ScheduleBlock(3, 0, 2, nightColor),   ScheduleBlock(3, 2, 4, morningColor), ScheduleBlock(3, 4, 6, comfortColor),
        ScheduleBlock(4, 0, 1, nightColor),   ScheduleBlock(4, 1, 4, morningColor), ScheduleBlock(4, 4, 6, comfortColor),
        ScheduleBlock(5, 0, 2, nightColor),   ScheduleBlock(5, 2, 6, ecoColor),
        ScheduleBlock(6, 0, 2, nightColor),   ScheduleBlock(6, 2, 6, ecoColor)
    )

    SlideContainer(
        eyebrowText = "Programaciones",
        eyebrowColor = Primary,
        title = "Siempre en horario",
        subtitle = "Configura la semana y olvídate del resto"
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val cols = 7
            val rows = 6
            val cellW = size.width / cols
            val cellH = size.height / rows
            val gridColor = Color(0xFF3F4850)

            for (c in 0..cols) {
                drawLine(gridColor, Offset(c * cellW, 0f), Offset(c * cellW, size.height), 1f)
            }
            for (r in 0..rows) {
                drawLine(gridColor, Offset(0f, r * cellH), Offset(size.width, r * cellH), 1f)
            }

            blocks.forEach { b ->
                val left = b.dayCol * cellW + 2f
                val top  = b.startRow * cellH + 2f
                val bW   = cellW - 4f
                val bH   = (b.endRow - b.startRow) * cellH - 4f
                drawRoundRect(
                    color = b.color.copy(alpha = 0.82f),
                    topLeft = Offset(left, top),
                    size = Size(bW, bH),
                    cornerRadius = CornerRadius(4f)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Secondary           to "Noche",
                Color(0xFFFFB44C)   to "Mañana",
                Tertiary            to "Eco",
                Warm                to "Confort"
            ).forEach { (c, l) ->
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(c)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(l, fontSize = 11.sp, color = OnSurfaceVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page 7 — Permissions
// ---------------------------------------------------------------------------

@Composable
private fun Slide7Permissions() {
    val items = listOf(
        Triple(Icons.Default.LocationOn,   "Ubicación en segundo plano", Primary),
        Triple(Icons.Default.DateRange,    "Calendario",                 Secondary),
        Triple(Icons.Default.Notifications,"Notificaciones",             Tertiary)
    )

    SlideContainer(
        eyebrowText = "Casi listo",
        eyebrowColor = Primary,
        title = "Permisos necesarios",
        subtitle = "Para que todo funcione correctamente"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { (icon, label, color) ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceContainerHigh,
                    border = BorderStroke(1.dp, OutlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = OnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page 8 — Login
// ---------------------------------------------------------------------------

@Composable
private fun Slide8Login(
    authDone: Boolean,
    onAuthDone: () -> Unit,
    onComplete: () -> Unit
) {
    val scopes = listOf("read_station", "read_thermostat", "write_thermostat")
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(loading) {
        if (loading) {
            delay(1500)
            loading = false
            onAuthDone()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo in surfaceContainerHigh rounded
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(18.dp),
            color = SurfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Conecta tu cuenta Netatmo",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Necesitamos acceso a tu cuenta para controlar tu termostato",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // OAuth card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceContainer,
            border = BorderStroke(1.dp, OutlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = OnSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "dev.netatmo.com",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurface
                        )
                        Text(
                            "OAuth 2.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = OutlineVariant)
                Spacer(Modifier.height(12.dp))

                Text(
                    "Permisos solicitados:",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                scopes.forEach { scope ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            scope,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = OnSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Secure connection badge
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Tertiary.copy(alpha = 0.10f)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = Tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Conexión segura. No almacenamos tu contraseña.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Tertiary
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        if (authDone) {
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TertiaryContainer,
                    contentColor = OnSurface
                )
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Entrar a la app",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Button(
                onClick = { loading = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryContainer,
                    contentColor = OnSurface
                ),
                enabled = !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = OnSurface,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Conectando...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Conectar con Netatmo",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
