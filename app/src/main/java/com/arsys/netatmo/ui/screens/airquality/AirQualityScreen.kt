package com.arsys.netatmo.ui.screens.airquality

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainer
import com.arsys.netatmo.ui.theme.SurfaceContainerLow
import com.arsys.netatmo.ui.theme.SurfaceContainerLowest

// ── Semantic colour aliases (local to this screen) ───────────────────────────
private val BgColor         = Color(0xFF101419)
private val TextPrimary     = Color(0xFFE0E2EA)
private val TextSecondary   = Color(0xFFBFC7D2)
private val BlueAccent      = Color(0xFF93CCFF)       // primary
private val GreenGood       = Color(0xFF62DF7D)        // tertiary
private val AmberWarn       = Color(0xFFF59E0B)
private val RedBad          = Color(0xFFFFB4AB)        // error
private val GaugeTrack      = Color(0xFF3F4850)        // outlineVariant

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirQualityScreen(
    navController: NavController,
    viewModel: AirQualityViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Calidad del Aire",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Actualizar",
                            tint = BlueAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceContainerLowest
                )
            )
        },
        containerColor = BgColor
    ) { padding ->
        if (state.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BlueAccent)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Error banner ─────────────────────────────────────────────────
            state.error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF93000A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        err,
                        modifier = Modifier.padding(16.dp),
                        color = Color(0xFFFFDAD6),
                        fontSize = 13.sp
                    )
                }
            }

            // ── Circular AQI gauge ────────────────────────────────────────
            AqiGauge(score = state.airQualityScore)

            // ── 2×2 metric grid ───────────────────────────────────────────
            val co2 = state.co2Level
            val co2Color = co2Color(co2)
            val co2Label = co2Label(co2)

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // CO2
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Air,
                    iconTint = co2Color,
                    value = co2?.toString() ?: "--",
                    unit = "ppm",
                    label = "CO₂",
                    sublabel = co2Label,
                    sublabelColor = co2Color,
                    progressValue = co2?.let { it.toFloat().coerceIn(0f, 2000f) / 2000f },
                    progressColor = co2Color
                )
                // Humidity
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.WaterDrop,
                    iconTint = BlueAccent,
                    value = state.humidity?.toString() ?: "--",
                    unit = "%",
                    label = "Humedad",
                    sublabel = humidityLabel(state.humidity),
                    sublabelColor = TextSecondary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Noise
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.VolumeUp,
                    iconTint = AmberWarn,
                    value = state.noise?.toString() ?: "--",
                    unit = "dB",
                    label = "Ruido",
                    sublabel = noiseLabel(state.noise),
                    sublabelColor = TextSecondary
                )
                // Pressure
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Compress,
                    iconTint = TextSecondary,
                    value = state.pressure?.let { "%.0f".format(it) } ?: "--",
                    unit = "mbar",
                    label = "Presión"
                )
            }

            // ── Outdoor section ──────────────────────────────────────────
            if (state.outdoorTemp != null) {
                OutdoorCard(
                    temp = state.outdoorTemp,
                    condition = state.weatherCondition
                )
            }

            // ── No sensor warning ────────────────────────────────────────
            if (co2 == null && state.humidity == null && state.noise == null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, OutlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = BlueAccent)
                        Text(
                            "Estación meteorológica necesaria",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Los datos de CO₂, humedad y ruido requieren una estación Netatmo (NAMain) conectada al sistema.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Circular AQI gauge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AqiGauge(score: Int) {
    val sweepDeg = 270f
    val startAngle = 135f                // starts bottom-left, sweeps clockwise

    val gaugeColor = when {
        score == 0  -> GaugeTrack
        score >= 80 -> GreenGood
        score >= 50 -> AmberWarn
        else        -> RedBad
    }
    val aqiLabel = when {
        score == 0  -> "Sin datos"
        score >= 80 -> "Excelente"
        score >= 60 -> "Buena"
        score >= 40 -> "Moderada"
        else        -> "Mala"
    }
    val fillFraction = (score / 100f).coerceIn(0f, 1f)

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val strokeWidth = 20.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            // Track
            drawArc(
                color = GaugeTrack,
                startAngle = startAngle,
                sweepAngle = sweepDeg,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            // Fill
            if (fillFraction > 0f) {
                drawArc(
                    color = gaugeColor,
                    startAngle = startAngle,
                    sweepAngle = sweepDeg * fillFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (score == 0) "--" else score.toString(),
                color = TextPrimary,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 52.sp
            )
            Text(
                text = "AQI",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = aqiLabel,
                color = gaugeColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Metric card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    value: String,
    unit: String,
    label: String,
    sublabel: String? = null,
    sublabelColor: Color = TextSecondary,
    progressValue: Float? = null,        // 0f..1f — shown only for CO2
    progressColor: Color = GreenGood
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    value,
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    unit,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            sublabel?.let {
                Text(it, color = sublabelColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            progressValue?.let { frac ->
                Spacer(Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = progressColor,
                    trackColor = OutlineVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Outdoor weather card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OutdoorCard(temp: Double, condition: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Exterior",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "%.1f °C".format(temp),
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                if (condition.isNotEmpty()) {
                    Text(condition, color = TextSecondary, fontSize = 13.sp)
                }
            }
            Icon(
                Icons.Default.WbSunny,
                contentDescription = null,
                tint = AmberWarn,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper label functions
// ─────────────────────────────────────────────────────────────────────────────

private fun co2Color(ppm: Int?): Color = when {
    ppm == null  -> Color(0xFFBFC7D2)
    ppm < 800    -> GreenGood
    ppm < 1200   -> AmberWarn
    else          -> RedBad
}

private fun co2Label(ppm: Int?): String = when {
    ppm == null  -> "Sin datos"
    ppm < 800    -> "Óptimo"
    ppm < 1200   -> "Moderado"
    else          -> "Ventila"
}

private fun humidityLabel(h: Int?): String = when {
    h == null      -> ""
    h in 40..60    -> "Óptima"
    h < 30         -> "Muy seco"
    h < 40         -> "Seco"
    h in 61..70    -> "Húmedo"
    else            -> "Muy húmedo"
}

private fun noiseLabel(db: Int?): String = when {
    db == null -> ""
    db < 40    -> "Silencioso"
    db < 55    -> "Normal"
    db < 70    -> "Ruidoso"
    else        -> "Muy ruidoso"
}
