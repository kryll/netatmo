package com.arsys.netatmo.ui.screens.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Design tokens ---
private val BgColor          = Color(0xFF101419)
private val SurfaceLowest    = Color(0xFF0A0E13)
private val SurfaceContLow   = Color(0xFF181C21)
private val SurfaceCont      = Color(0xFF1C2025)
private val SurfaceContHigh  = Color(0xFF262A30)
private val SurfaceContHighest = Color(0xFF31353B)
private val OutlineVar       = Color(0xFF3F4850)
private val OnSurface        = Color(0xFFE0E2EA)
private val OnSurfaceVar     = Color(0xFFBFC7D2)
private val Primary          = Color(0xFF93CCFF)
private val OnPrimary        = Color(0xFF003351)
private val PrimaryContainer = Color(0xFF3198DC)
private val Tertiary         = Color(0xFF62DF7D)
private val OnTertiary       = Color(0xFF003914)
private val TertiaryContainer = Color(0xFF1CA64D)
private val ErrorColor       = Color(0xFFFFB4AB)

private data class DeviceType(
    val apiType: String,
    val displayName: String,
    val subtitle: String,
    val icon: ImageVector,
)

private val DEVICE_TYPES = listOf(
    DeviceType("NATherm1", "Termostato",       "Control central calefacción OpenTherm",  Icons.Filled.Thermostat),
    DeviceType("NRV",      "Cabezal (NRV)",    "Válvula termostática inteligente",        Icons.Filled.Tune),
    DeviceType("NAPlug",   "Relé",             "Puente RF para la caldera",              Icons.Filled.Router),
    DeviceType("NAMain",   "Estación Interior","CO₂, humedad, temperatura, ruido",        Icons.Filled.SensorsOff),
)

private val PAIRING_STEPS = listOf(
    Triple(Icons.Filled.PhoneAndroid,   "Descarga Netatmo Home",      "Instala la app oficial desde Google Play Store."),
    Triple(Icons.Filled.Settings,       "Configura el dispositivo",   "Empareja el accesorio en Netatmo Home siguiendo el asistente en pantalla."),
    Triple(Icons.Filled.Sync,           "Vuelve aquí",                "Regresa a esta pantalla para confirmar la detección automática."),
)

private val STEP_LABELS = listOf("Seleccionar", "Instrucciones", "Confirmación")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePairingScreen(onBack: () -> Unit) {
    var step            by remember { mutableIntStateOf(0) }
    var selectedType    by remember { mutableStateOf<DeviceType?>(null) }
    var confirmed       by remember { mutableStateOf(false) }

    // Radar pulse animation for step 1
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Añadir Dispositivo",
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = OnSurface)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.Info, contentDescription = "Información", tint = OnSurfaceVar)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceLowest)
            )
        },
        containerColor = BgColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── 3-step progress indicator ──────────────────────────────────
            StepProgressBar(current = step, labels = STEP_LABELS)

            // ── Step content ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = step == 0,
                enter = fadeIn() + slideInVertically(),
                exit  = fadeOut()
            ) {
                DeviceTypeGrid(
                    types    = DEVICE_TYPES,
                    onSelect = { type ->
                        selectedType = type
                        step = 1
                    }
                )
            }

            AnimatedVisibility(
                visible = step == 1,
                enter = fadeIn() + slideInVertically(),
                exit  = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    selectedType?.let { type ->
                        // Selected device chip
                        Surface(
                            shape  = RoundedCornerShape(12.dp),
                            color  = PrimaryContainer.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, PrimaryContainer.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(type.icon, contentDescription = null, tint = Primary, modifier = Modifier.size(28.dp))
                                Column {
                                    Text(type.displayName, color = OnSurface,    fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(type.apiType,     color = OnSurfaceVar, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Radar / scanning card
                    Surface(
                        shape  = RoundedCornerShape(16.dp),
                        color  = SurfaceContHigh,
                        border = BorderStroke(1.dp, OutlineVar)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                                Surface(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .scale(pulse),
                                    shape = CircleShape,
                                    color = Primary.copy(alpha = 0.07f),
                                    border = BorderStroke(1.5.dp, Primary.copy(alpha = 0.25f))
                                ) {}
                                Surface(
                                    modifier = Modifier.size(72.dp),
                                    shape    = CircleShape,
                                    color    = Primary.copy(alpha = 0.13f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.Bluetooth,
                                            contentDescription = null,
                                            tint     = Primary,
                                            modifier = Modifier.size(38.dp)
                                        )
                                    }
                                }
                            }

                            Text("Modo emparejamiento activo", color = Primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }

                    // Numbered steps
                    Text("Sigue estos pasos", color = OnSurfaceVar, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    PAIRING_STEPS.forEachIndexed { index, (icon, title, desc) ->
                        PairingStepRow(number = index + 1, icon = icon, title = title, description = desc)
                    }

                    // Play Store link card
                    Surface(
                        shape  = RoundedCornerShape(12.dp),
                        color  = SurfaceContLow,
                        border = BorderStroke(1.dp, OutlineVar),
                        modifier = Modifier.clickable { }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Netatmo Home en Play Store", color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text("Abre la tienda para descargar la app", color = OnSurfaceVar, fontSize = 12.sp)
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = OnSurfaceVar)
                        }
                    }

                    Button(
                        onClick = { step = 2 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer, contentColor = OnPrimary),
                        shape  = RoundedCornerShape(12.dp)
                    ) {
                        Text("Ya configuré el dispositivo", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }

            AnimatedVisibility(
                visible = step == 2,
                enter = fadeIn() + slideInVertically(),
                exit  = fadeOut()
            ) {
                Column(
                    verticalArrangement    = Arrangement.spacedBy(16.dp),
                    horizontalAlignment    = Alignment.CenterHorizontally,
                    modifier               = Modifier.fillMaxWidth()
                ) {
                    if (!confirmed) {
                        Surface(
                            shape  = RoundedCornerShape(16.dp),
                            color  = SurfaceCont,
                            border = BorderStroke(1.dp, OutlineVar)
                        ) {
                            Column(
                                modifier               = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment    = Alignment.CenterHorizontally,
                                verticalArrangement    = Arrangement.spacedBy(14.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = TertiaryContainer.copy(alpha = 0.2f),
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.Devices,
                                            contentDescription = null,
                                            tint     = Tertiary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                                Text("¿Ya emparejaste el dispositivo?", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = TextAlign.Center)
                                Text(
                                    "Confirma que el ${selectedType?.displayName ?: "dispositivo"} aparece en la app Netatmo Home como activo.",
                                    color     = OnSurfaceVar,
                                    fontSize  = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Button(
                            onClick = { confirmed = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Tertiary.copy(alpha = 0.85f), contentColor = OnTertiary),
                            shape  = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Dispositivo añadido", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }

                        TextButton(onClick = { step = 0; selectedType = null }) {
                            Text("Elegir otro tipo", color = OnSurfaceVar, fontSize = 13.sp)
                        }
                    } else {
                        ConfirmationSuccess(deviceName = selectedType?.displayName ?: "Dispositivo", onBack = onBack)
                    }
                }
            }
        }
    }
}

// ── Subcomposables ──────────────────────────────────────────────────────────

@Composable
private fun StepProgressBar(current: Int, labels: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val done    = index < current
            val active  = index == current
            Column(
                modifier            = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = when {
                            done   -> Tertiary
                            active -> Primary
                            else   -> OutlineVar
                        }
                    ) {}
                }
                Text(
                    label,
                    color     = if (active) Primary else if (done) Tertiary else OnSurfaceVar,
                    fontSize  = 10.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun DeviceTypeGrid(types: List<DeviceType>, onSelect: (DeviceType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("¿Qué dispositivo quieres añadir?", color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text("Selecciona el tipo para comenzar el asistente de emparejamiento.", color = OnSurfaceVar, fontSize = 13.sp)

        // 2-column grid
        types.chunked(2).forEach { rowItems ->
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { type ->
                    DeviceTypeCard(type = type, onClick = { onSelect(type) }, modifier = Modifier.weight(1f))
                }
                // Pad odd last row
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DeviceTypeCard(type: DeviceType, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape  = RoundedCornerShape(16.dp),
        color  = SurfaceContLow,
        border = BorderStroke(1.dp, OutlineVar)
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Primary.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(type.icon, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
                }
            }
            Text(type.displayName, color = OnSurface,    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(type.subtitle,    color = OnSurfaceVar, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PairingStepRow(number: Int, icon: ImageVector, title: String, description: String) {
    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = SurfaceContLow,
        border = BorderStroke(1.dp, OutlineVar)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = PrimaryContainer.copy(alpha = 0.25f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$number", color = Primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                    Text(title, color = OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Text(description, color = OnSurfaceVar, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ConfirmationSuccess(deviceName: String, onBack: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier            = Modifier.fillMaxWidth().padding(vertical = 24.dp)
    ) {
        Surface(
            shape    = CircleShape,
            color    = TertiaryContainer.copy(alpha = 0.2f),
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Tertiary, modifier = Modifier.size(52.dp))
            }
        }
        Text("¡$deviceName añadido!", color = Tertiary, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
        Text(
            "El dispositivo está vinculado al sistema. Puedes configurarlo desde la pantalla de dispositivos.",
            color     = OnSurfaceVar,
            fontSize  = 14.sp,
            textAlign = TextAlign.Center
        )
        Button(
            onClick  = onBack,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = PrimaryContainer, contentColor = OnPrimary),
            shape    = RoundedCornerShape(12.dp)
        ) {
            Text("Volver al inicio", fontWeight = FontWeight.SemiBold)
        }
    }
}
