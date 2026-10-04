package com.arsys.netatmo.ui.screens.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == 4

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> SlideWelcome()
                1 -> SlideControl()
                2 -> SlideAutomate()
                3 -> SlideStats()
                4 -> SlideStart(onComplete = { viewModel.markOnboardingCompleted(); onComplete() })
                else -> SlideWelcome()
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(5) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (selected) 24.dp else 8.dp, 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = if (selected) 1f else 0.4f))
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isLastPage) {
                TextButton(onClick = { viewModel.markOnboardingCompleted(); onSkip() }) {
                    Text("Saltar", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                }
                Button(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Continuar", color = Color(0xFF0369A1), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SlideWelcome() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0EA5E9), Color(0xFF0369A1))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Thermostat,
                contentDescription = null,
                modifier = Modifier.size(128.dp),
                tint = Color.White
            )

            Spacer(Modifier.height(8.dp))

            Canvas(modifier = Modifier.size(80.dp, 40.dp)) {
                val tubeWidth = size.width * 0.18f
                val tubeHeight = size.height * 0.6f
                val tubeLeft = (size.width - tubeWidth) / 2f
                val tubeTop = 0f
                val bulbRadius = tubeWidth * 0.9f
                val bulbCx = size.width / 2f
                val bulbCy = tubeTop + tubeHeight + bulbRadius * 0.6f

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.35f),
                    topLeft = Offset(tubeLeft, tubeTop),
                    size = Size(tubeWidth, tubeHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(tubeWidth / 2f)
                )

                drawCircle(
                    color = Color.White.copy(alpha = 0.5f),
                    radius = bulbRadius,
                    center = Offset(bulbCx, bulbCy)
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Bienvenido a Netatmo",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Tu termostato inteligente, siempre a mano",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SlideControl() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Control total",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Ajusta la temperatura de cada zona desde cualquier lugar",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            Canvas(modifier = Modifier.size(260.dp)) {
                val strokeWidth = 24f
                val arcInset = strokeWidth / 2f
                val arcTopLeft = Offset(arcInset, arcInset)
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)

                drawArc(
                    color = Color.White.copy(alpha = 0.2f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                drawArc(
                    color = Color(0xFFBAE6FD),
                    startAngle = -90f,
                    sweepAngle = 216f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                drawIntoCanvas { canvas ->
                    val tempPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 96f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                    canvas.nativeCanvas.drawText("21°", size.width / 2f, size.height / 2f + 34f, tempPaint)
                }
            }
        }
    }
}

@Composable
internal fun SlideAutomate() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF7C3AED), Color(0xFF4C1D95))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Automatiza tu rutina",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Geovallas, horarios y escenarios que se adaptan a ti",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            val automateCards = listOf(
                Pair(Icons.Default.LocationOn, "Geovallas"),
                Pair(Icons.Default.Schedule, "Horarios"),
                Pair(Icons.Default.AutoAwesome, "Escenarios"),
                Pair(Icons.Default.Bolt, "Acciones")
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                automateCards.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { (icon, label) ->
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.15f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = label,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SlideStats() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF059669), Color(0xFF064E3B))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ahorra energía",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Visualiza el consumo y encuentra formas de reducir tu factura",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val barFractions = listOf(0.55f, 0.72f, 0.60f, 0.88f, 0.70f)
                val highlightIndex = 2
                val barWidthPx = 32.dp.toPx()
                val spacingPx = 12.dp.toPx()
                val totalWidth = barFractions.size * barWidthPx + (barFractions.size - 1) * spacingPx
                val startX = (size.width - totalWidth) / 2f
                val baselineY = size.height - 4.dp.toPx()
                val maxBarHeightPx = baselineY - 4.dp.toPx()
                val cornerRadiusPx = 6.dp.toPx()

                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(startX, baselineY),
                    end = Offset(startX + totalWidth, baselineY),
                    strokeWidth = 1.dp.toPx()
                )

                barFractions.forEachIndexed { index, fraction ->
                    val barHeight = maxBarHeightPx * fraction
                    val left = startX + index * (barWidthPx + spacingPx)
                    val top = baselineY - barHeight
                    val isHighlighted = index == highlightIndex

                    drawRoundRect(
                        color = if (isHighlighted) Color.White else Color.White.copy(alpha = 0.4f),
                        topLeft = Offset(left, top),
                        size = Size(barWidthPx, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SlideStart(onComplete: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0EA5E9), Color(0xFF0369A1))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(color = Color.White, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Thermostat,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = Color(0xFF0369A1)
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Todo listo",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Conecta tu cuenta Netatmo y empieza a controlar tu hogar",
                fontSize = 17.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(0.75f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = "Comenzar",
                    color = Color(0xFF0369A1),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
