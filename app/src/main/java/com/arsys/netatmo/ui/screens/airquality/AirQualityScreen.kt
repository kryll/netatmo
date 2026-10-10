package com.arsys.netatmo.ui.screens.airquality

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Co2
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val BgColor = Color(0xFF101419)
private val SurfaceColor = Color(0xFF1C2025)
private val BorderColor = Color(0xFF3F4850)
private val PrimaryText = Color(0xFFE0E2EA)
private val SecondaryText = Color(0xFFBFC7D2)
private val GreenGood = Color(0xFF4CAF50)
private val OrangeModerate = Color(0xFFFF9800)
private val RedBad = Color(0xFFF44336)
private val AccentBlue = Color(0xFF4FC3F7)

private enum class Co2Quality(val label: String, val color: Color) {
    GOOD("Buena", GreenGood),
    MODERATE("Moderada", OrangeModerate),
    BAD("Mala", RedBad)
}

private fun co2Quality(ppm: Int?): Co2Quality = when {
    ppm == null -> Co2Quality.GOOD
    ppm < 800 -> Co2Quality.GOOD
    ppm <= 1200 -> Co2Quality.MODERATE
    else -> Co2Quality.BAD
}

private fun overallQuality(state: AirQualityUiState): Co2Quality {
    val co2 = co2Quality(state.co2Ppm)
    return if (co2 == Co2Quality.BAD) Co2Quality.BAD
    else if (co2 == Co2Quality.MODERATE) Co2Quality.MODERATE
    else Co2Quality.GOOD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirQualityScreen(
    onBack: () -> Unit,
    viewModel: AirQualityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Calidad del Aire",
                        color = PrimaryText,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = PrimaryText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgColor
                )
            )
        },
        containerColor = BgColor
    ) { innerPadding ->

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentBlue)
            }
            return@Scaffold
        }

        if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Error: ${uiState.error}",
                    color = RedBad,
                    fontSize = 14.sp
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 2x2 grid of metric cards
            val co2Quality = co2Quality(uiState.co2Ppm)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Air,
                    label = "CO2",
                    value = uiState.co2Ppm?.toString() ?: "--",
                    unit = "ppm",
                    accentColor = co2Quality.color,
                    badge = co2Quality.label
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.WaterDrop,
                    label = "Humedad",
                    value = uiState.humidity?.toString() ?: "--",
                    unit = "%",
                    accentColor = AccentBlue
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.VolumeUp,
                    label = "Ruido",
                    value = uiState.noiseDb?.toString() ?: "--",
                    unit = "dB",
                    accentColor = OrangeModerate
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Compress,
                    label = "Presión",
                    value = uiState.pressureMbar?.let { "%.1f".format(it) } ?: "--",
                    unit = "mbar",
                    accentColor = Color(0xFFCE93D8)
                )
            }

            // Overall quality section
            val overall = overallQuality(uiState)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Calidad general",
                        color = SecondaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QualityChip(label = overall.label, color = overall.color)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (overall) {
                                Co2Quality.GOOD -> "El aire interior es saludable."
                                Co2Quality.MODERATE -> "Niveles de CO2 moderados. Ventile."
                                Co2Quality.BAD -> "CO2 elevado. Ventile urgentemente."
                            },
                            color = SecondaryText,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
    badge: String? = null
) {
    Card(
        modifier = modifier
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                if (badge != null) {
                    QualityChip(label = badge, color = accentColor, small = true)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = PrimaryText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 30.sp
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    color = SecondaryText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Text(
                text = label,
                color = SecondaryText,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun QualityChip(
    label: String,
    color: Color,
    small: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = if (small) 10.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                horizontal = if (small) 6.dp else 10.dp,
                vertical = if (small) 2.dp else 4.dp
            )
        )
    }
}
