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
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

// Design tokens
private val Accent = Color(0xFF0284C7)
private val Warm = Color(0xFFEA580C)
private val Green = Color(0xFF16A34A)
private val Violet = Color(0xFF7C3AED)
private val Warn = Color(0xFFD97706)
private val BgSurface = Color(0xFFF4F6F9)
private val TextPrimary = Color(0xFF1E293B)
private val TextSecondary = Color(0xFF64748B)

private data class AutoFlow(val whenLabel: String, val thenLabel: String, val color: Color)
private data class SceneItem(val emoji: String, val label: String, val temp: String, val color: Color, val bg: Color)
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
            .background(BgSurface)
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                                .background(if (selected) Accent else Color(0xFFCBD5E1))
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
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
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
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            Text(
                                text = when (currentPage) {
                                    0 -> "Empezar"
                                    totalPages - 2 -> "Continuar"
                                    else -> "Siguiente"
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

        if (!isLastPage) {
            TextButton(
                onClick = { viewModel.markOnboardingCompleted(); onSkip() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
            ) {
                Text("Saltar", color = TextSecondary, fontSize = 14.sp)
            }
        }
    }
}

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
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = subtitle,
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        visual()
    }
}

@Composable
private fun Slide0Welcome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(28.dp))
        )

        Spacer(Modifier.height(36.dp))

        Text(
            text = "Netatmo Smart",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Tu hogar, a la temperatura perfecta",
            fontSize = 18.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Slide1Thermostat() {
    SlideContainer(
        eyebrowText = "Control",
        eyebrowColor = Accent,
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

            drawArc(
                color = Color(0xFFE2E8F0),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                color = Accent,
                startAngle = startAngle,
                sweepAngle = fillSweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawIntoCanvas { canvas ->
                val tempPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#1E293B")
                    textSize = 80f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                canvas.nativeCanvas.drawText("21°", cx, cy + 28f, tempPaint)

                val subPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#64748B")
                    textSize = 26f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                canvas.nativeCanvas.drawText("Salón · Confort", cx, cy + 64f, subPaint)
            }
        }
    }
}

@Composable
private fun Slide2Geofence() {
    SlideContainer(
        eyebrowText = "Geovalla",
        eyebrowColor = Green,
        title = "Llega y enciende",
        subtitle = "Tu termostato te detecta y prepara el hogar"
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            listOf(0.42f, 0.62f, 0.80f).forEach { fraction ->
                drawCircle(
                    color = Green.copy(alpha = 0.14f),
                    radius = size.width * fraction / 2f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f)
                )
            }

            drawCircle(
                color = Green.copy(alpha = 0.18f),
                radius = size.width * 0.18f,
                center = Offset(cx, cy)
            )

            val phoneCx = cx + size.width * 0.28f
            val phoneCy = cy - size.height * 0.24f
            drawCircle(color = Accent, radius = 14f, center = Offset(phoneCx, phoneCy))
            drawCircle(color = Color.White, radius = 6f, center = Offset(phoneCx, phoneCy))

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
                    color = Accent.copy(alpha = 0.55f),
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

@Composable
private fun Slide3Stats() {
    SlideContainer(
        eyebrowText = "Calendario · Estadísticas",
        eyebrowColor = Warn,
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
                    color = if (i == highlightIndex) Warn else Color(0xFFBAE6FD),
                    topLeft = Offset(left, top),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(cornerR)
                )
            }

            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#64748B")
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

@Composable
private fun Slide4Automations() {
    val flows = listOf(
        AutoFlow("Llego a casa", "21°C Confort", Accent),
        AutoFlow("Reunión en casa", "Precalentar", Warm),
        AutoFlow("23:00", "17°C Noche", Violet),
        AutoFlow("Salgo de casa", "15°C Eco", Green)
    )

    SlideContainer(
        eyebrowText = "Automatizaciones",
        eyebrowColor = Violet,
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
                            .background(Color(0xFFE2E8F0))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Text("CUANDO", fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                            Text(flow.whenLabel, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
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
                            .background(flow.color.copy(alpha = 0.10f))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Text("ENTONCES", fontSize = 9.sp, color = flow.color, fontWeight = FontWeight.SemiBold)
                            Text(flow.thenLabel, fontSize = 13.sp, color = flow.color, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Slide5Scenes() {
    val scenes = listOf(
        SceneItem("☀️", "Confort", "21°", Warm, Color(0xFFFFF7ED)),
        SceneItem("🌿", "Eco", "18°", Green, Color(0xFFF0FDF4)),
        SceneItem("🚗", "Ausente", "15°", Accent, Color(0xFFF0F9FF)),
        SceneItem("🌙", "Noche", "17°", Violet, Color(0xFFF5F3FF))
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
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selected = globalIdx },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = scene.bg),
                            border = if (isSelected) BorderStroke(2.dp, scene.color) else null,
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (isSelected) 4.dp else 0.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(scene.emoji, fontSize = 28.sp)
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
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Slide6Schedules() {
    val amber = Color(0xFFF59E0B)
    val blocks = listOf(
        ScheduleBlock(0, 0, 2, Violet), ScheduleBlock(0, 2, 4, amber), ScheduleBlock(0, 4, 6, Warm),
        ScheduleBlock(1, 0, 2, Violet), ScheduleBlock(1, 2, 4, amber), ScheduleBlock(1, 4, 6, Green),
        ScheduleBlock(2, 0, 2, Violet), ScheduleBlock(2, 2, 5, Warm),  ScheduleBlock(2, 5, 6, Violet),
        ScheduleBlock(3, 0, 2, Violet), ScheduleBlock(3, 2, 4, amber), ScheduleBlock(3, 4, 6, Warm),
        ScheduleBlock(4, 0, 1, Violet), ScheduleBlock(4, 1, 4, amber), ScheduleBlock(4, 4, 6, Warm),
        ScheduleBlock(5, 0, 2, Violet), ScheduleBlock(5, 2, 6, Green),
        ScheduleBlock(6, 0, 2, Violet), ScheduleBlock(6, 2, 6, Green)
    )

    SlideContainer(
        eyebrowText = "Programaciones",
        eyebrowColor = Accent,
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

            for (c in 0..cols) {
                drawLine(Color(0xFFE2E8F0), Offset(c * cellW, 0f), Offset(c * cellW, size.height), 1f)
            }
            for (r in 0..rows) {
                drawLine(Color(0xFFE2E8F0), Offset(0f, r * cellH), Offset(size.width, r * cellH), 1f)
            }

            blocks.forEach { b ->
                val left = b.dayCol * cellW + 2f
                val top = b.startRow * cellH + 2f
                val bW = cellW - 4f
                val bH = (b.endRow - b.startRow) * cellH - 4f
                drawRoundRect(
                    color = b.color.copy(alpha = 0.78f),
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
            listOf(Violet to "Noche", amber to "Mañana", Green to "Eco", Warm to "Confort").forEach { (c, l) ->
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
                    Text(l, fontSize = 11.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun Slide7Permissions() {
    val items = listOf(
        Triple(Icons.Default.LocationOn, "Ubicación en segundo plano", Accent),
        Triple(Icons.Default.DateRange, "Calendario", Warn),
        Triple(Icons.Default.Notifications, "Notificaciones", Green)
    )

    SlideContainer(
        eyebrowText = "Casi listo",
        eyebrowColor = Accent,
        title = "Permisos necesarios",
        subtitle = "Para que todo funcione correctamente"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { (icon, label, color) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(label, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }
        }
    }
}

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
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Conecta tu cuenta Netatmo",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Necesitamos acceso a tu cuenta para controlar tu termostato",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0369A1)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "dev.netatmo.com",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text("OAuth 2.0", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(Modifier.height(12.dp))

                Text("Permisos solicitados:", fontSize = 12.sp, color = TextSecondary)
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
                                .background(Accent)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            scope,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Green.copy(alpha = 0.08f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Green,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Conexión segura. No almacenamos tu contraseña.",
                fontSize = 12.sp,
                color = Green
            )
        }

        Spacer(Modifier.height(24.dp))

        if (authDone) {
            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Entrar a la app", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        } else {
            Button(
                onClick = { loading = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                enabled = !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Conectando...", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                } else {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Conectar con Netatmo", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}
