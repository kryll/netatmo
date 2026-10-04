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

        // Page dots — overlaid at bottom center above the button
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

        // Bottom row: Skip + Continue/Start
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

            // Abstract thermometer canvas illustration
            Canvas(modifier = Modifier.size(80.dp, 40.dp)) {
                val tubeWidth = size.width * 0.18f
                val tubeHeight = size.height * 0.6f
                val tubeLeft = (size.width - tubeWidth) / 2f
                val tubeTop = 0f
                val bulbRadius = tubeWidth * 0.9f
                val bulbCx = size.width / 2f
                val bulbCy = tubeTop + tubeHeight + bulbRadius * 0.6f

                // Tube
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.35f),
                    topLeft = Offset(tubeLeft, tubeTop),
                    size = Size(tubeWidth, tubeHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(tubeWidth / 2f)
                )

                // Bulb
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
                val diameter = size.width
                val strokeWidth = 24f
                val arcInset = strokeWidth / 2f
                val arcTopLeft = Offset(arcInset, arcInset)
                val arcSize = Size(diameter - strokeWidth, diameter - strokeWidth)

                // Background full circle stroke
                drawArc(
                    color = Color.White.copy(alpha = 0.2f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // 60% filled arc (216 degrees of 360)
                drawArc(
                    color = Color(0xFFBAE6FD),
                    startAngle = -90f,
                    sweepAngle = 216f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Center temperature text
                drawIntoCanvas { canvas ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f

                    val tempPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 96f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                    canvas.nativeCanvas.drawText("21°", cx, cy + 34f, tempPaint)
                }
            }
        }
    }
}

@Composable
internal fun SlideAutomate() {
    // Implemented by subagent — slide 3
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF818CF8), Color(0xFF4338CA))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
internal fun SlideStats() {
    // Implemented by subagent — slide 4
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF059669))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
internal fun SlideStart(onComplete: () -> Unit) {
    // Implemented by subagent — slide 5
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
        Button(
            onClick = onComplete,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .height(56.dp)
        ) {
            Text(
                text = "Empezar",
                color = Color(0xFF0369A1),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}
