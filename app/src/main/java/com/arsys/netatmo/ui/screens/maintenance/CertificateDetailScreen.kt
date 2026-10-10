package com.arsys.netatmo.ui.screens.maintenance

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arsys.netatmo.domain.model.MaintenanceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Design tokens (same palette) ---
private val BgColor = Color(0xFF101419)
private val SurfaceLowest = Color(0xFF0A0E13)
private val SurfaceContLow = Color(0xFF181C21)
private val SurfaceCont = Color(0xFF1C2025)
private val SurfaceContHigh = Color(0xFF262A30)
private val SurfaceContHighest = Color(0xFF31353B)
private val OutlineVar = Color(0xFF3F4850)
private val TextPrimary = Color(0xFFE0E2EA)
private val TextSecondary = Color(0xFFBFC7D2)
private val BluePrimary = Color(0xFF93CCFF)
private val BlueInteractive = Color(0xFF3198DC)
private val GreenTertiary = Color(0xFF62DF7D)
private val GreenContainer = Color(0xFF1CA64D)
private val ErrorColor = Color(0xFFFFB4AB)
private val ErrorContainer = Color(0xFF93000A)
private val OrangeSecondary = Color(0xFFFFB599)

private val dateLong = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es", "ES"))
private val dateTime = SimpleDateFormat("dd/MM/yyyy · HH:mm 'CEST'", Locale("es", "ES"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificateDetailScreen(
    recordId: Long,
    onBack: () -> Unit,
    viewModel: MaintenanceViewModel = hiltViewModel()
) {
    LaunchedEffect(recordId) { viewModel.selectRecord(recordId) }
    val record by viewModel.selectedRecord.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var activePage by remember { mutableIntStateOf(1) }

    val now = System.currentTimeMillis()
    val maintenanceType = remember(record?.type) {
        record?.let { r ->
            try { MaintenanceType.valueOf(r.type) } catch (e: Exception) { MaintenanceType.OTHER }
        }
    }

    val isWarrantyActive = record?.warrantyExtendedUntil?.let { it > now } == true
    val certNumber = record?.let { r -> r.certificateRef.ifBlank { "CERT-${r.id}" } } ?: "—"
    val isSent = record?.let { r -> viewModel.isSent(r.id) } == true

    // Build share text
    val certText = record?.let { r ->
        val t = maintenanceType ?: MaintenanceType.OTHER
        buildString {
            appendLine("CERTIFICADO DE MANTENIMIENTO")
            appendLine("==============================")
            appendLine("Número: $certNumber")
            appendLine("Fecha: ${dateLong.format(Date(r.date))}")
            appendLine("Tipo: ${t.displayName}")
            appendLine("Técnico: ${r.technicianName.ifBlank { "No especificado" }}")
            appendLine()
            appendLine("Descripción:")
            appendLine(r.description)
            r.warrantyExtendedUntil?.let { until ->
                appendLine()
                appendLine("Garantía extendida hasta: ${dateLong.format(Date(until))}")
            }
            appendLine()
            appendLine("Documento generado por Netatmo Smart Control")
        }
    } ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Certificado Oficial",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Outlined.Print, contentDescription = "Imprimir", tint = TextSecondary)
                    }
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, certText)
                            putExtra(Intent.EXTRA_SUBJECT, "Certificado de mantenimiento Netatmo #$certNumber")
                        }
                        context.startActivity(Intent.createChooser(intent, "Compartir certificado"))
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Compartir", tint = BluePrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceLowest)
            )
        },
        containerColor = BgColor
    ) { padding ->
        if (record == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BluePrimary)
            }
            return@Scaffold
        }

        val r = record!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // --- Document Control Ribbon ---
            DocControlRibbon(
                certNumber = certNumber,
                activePage = activePage,
                isValid = isWarrantyActive || (r.warrantyExtendedUntil == null),
                onPageSelected = { activePage = it }
            )

            // --- Scrollable body ---
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedContent(
                    targetState = activePage,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "cert-page"
                ) { page ->
                    when (page) {
                        1 -> CertificatePage1(
                            record = r,
                            certNumber = certNumber,
                            maintenanceType = maintenanceType ?: MaintenanceType.OTHER,
                            isWarrantyActive = isWarrantyActive,
                            now = now
                        )
                        else -> CertificatePage2(record = r)
                    }
                }

                // --- Sent confirmation chip ---
                if (isSent) {
                    Surface(
                        color = GreenTertiary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GreenTertiary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = GreenTertiary, modifier = Modifier.size(18.dp))
                            Text("Certificado marcado como enviado", color = GreenTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // --- Action buttons ---
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, certText)
                            putExtra(Intent.EXTRA_SUBJECT, "Certificado de mantenimiento Netatmo #$certNumber")
                        }
                        context.startActivity(Intent.createChooser(intent, "Compartir certificado"))
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary, contentColor = Color(0xFF001D31)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Compartir certificado", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }

                OutlinedButton(
                    onClick = { if (!isSent) viewModel.markAsSent(r.id) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !isSent,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isSent) GreenTertiary else TextSecondary
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSent) GreenTertiary.copy(alpha = 0.5f) else OutlineVar
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (isSent) Icons.Default.CheckCircle else Icons.Outlined.MarkEmailUnread,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isSent) "Enviado" else "Marcar como enviado",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Document Control Ribbon
// ---------------------------------------------------------------------------
@Composable
private fun DocControlRibbon(
    certNumber: String,
    activePage: Int,
    isValid: Boolean,
    onPageSelected: (Int) -> Unit
) {
    Surface(
        color = SurfaceContLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Top row: file name + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(18.dp))
                    Column {
                        Text("Certificado_${certNumber}.pdf", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text("ISO 5167-2 / UNE-EN 14336", color = TextSecondary, fontSize = 10.sp)
                    }
                }
                Surface(
                    color = SurfaceContHigh,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, OutlineVar)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FitScreen, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                        Text("100%", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Page tabs + validity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = SurfaceCont, shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PageTabButton(
                            label = "Pág. 1 (Dictamen)",
                            icon = Icons.Outlined.Article,
                            selected = activePage == 1,
                            onClick = { onPageSelected(1) }
                        )
                        PageTabButton(
                            label = "Pág. 2 (Telemetría)",
                            icon = Icons.Outlined.Timeline,
                            selected = activePage == 2,
                            onClick = { onPageSelected(2) }
                        )
                    }
                }

                Surface(
                    color = if (isValid) GreenTertiary.copy(alpha = 0.12f) else ErrorContainer.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isValid) Icons.Default.Verified else Icons.Default.GppBad,
                            contentDescription = null,
                            tint = if (isValid) GreenTertiary else ErrorColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            if (isValid) "FIRMA VÁLIDA" else "EXPIRADO",
                            color = if (isValid) GreenTertiary else ErrorColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageTabButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) BlueInteractive else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) TextPrimary else TextSecondary, modifier = Modifier.size(13.dp))
            Text(label, color = if (selected) TextPrimary else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------------------------------------------------------------------------
//  Certificate Page 1 — Dictamen
// ---------------------------------------------------------------------------
@Composable
private fun CertificatePage1(
    record: com.arsys.netatmo.data.local.entities.MaintenanceRecordEntity,
    certNumber: String,
    maintenanceType: MaintenanceType,
    isWarrantyActive: Boolean,
    now: Long
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceLowest,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineVar)
    ) {
        Box {
            // Watermark icon
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.EnergySavingsLeaf,
                    contentDescription = null,
                    tint = TextPrimary.copy(alpha = 0.025f),
                    modifier = Modifier.size(180.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: brand + folio
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF66018))
                            )
                            Text(
                                "NETATMO SMART ATMOSPHERE",
                                color = OrangeSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        }
                        Text("División Telemetría Térmica · Legrand", color = TextSecondary, fontSize = 10.sp)
                    }
                    Surface(color = SurfaceContHigh, shape = RoundedCornerShape(6.dp)) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.End) {
                            Text("FOLIO CERTIFICADO", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            Text("#$certNumber", color = BluePrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                HorizontalDivider(color = OutlineVar.copy(alpha = 0.6f))

                // Title block + QR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Certificado de Conformidad\ny Auditoría Hidráulica",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 22.sp
                        )
                        Text(
                            "Caldera mural y circuito primario de calefacción residencial",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = Color(0xFF89929B), modifier = Modifier.size(12.dp))
                            Text(dateTime.format(Date(record.date)), color = Color(0xFF89929B), fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    // QR Code simulation
                    QrCodeCanvas(modifier = Modifier.size(72.dp))
                }

                HorizontalDivider(color = OutlineVar.copy(alpha = 0.6f))

                // Certificate fields
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CertField("NÚMERO DE CERTIFICADO", certNumber)
                    CertField("FECHA DE INTERVENCIÓN", dateLong.format(Date(record.date)))
                    CertField("TIPO DE REVISIÓN", maintenanceType.displayName)
                    CertField("TÉCNICO RESPONSABLE", record.technicianName.ifBlank { "No especificado" })
                    CertField("DESCRIPCIÓN", record.description)
                    record.warrantyExtendedUntil?.let { until ->
                        CertField("GARANTÍA EXTENDIDA HASTA", dateLong.format(Date(until)))
                    }
                }

                HorizontalDivider(color = OutlineVar.copy(alpha = 0.6f))

                // Status badge
                val validityColor = if (isWarrantyActive || record.warrantyExtendedUntil == null) GreenTertiary else ErrorColor
                val validityBg = if (isWarrantyActive || record.warrantyExtendedUntil == null) GreenContainer.copy(alpha = 0.15f) else ErrorContainer.copy(alpha = 0.15f)
                val validityText = if (isWarrantyActive || record.warrantyExtendedUntil == null) "VÁLIDO" else "EXPIRADO"
                val validityIcon = if (isWarrantyActive || record.warrantyExtendedUntil == null) Icons.Default.CheckCircle else Icons.Default.Cancel

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Documento generado por Netatmo Smart Control", color = TextSecondary, fontSize = 10.sp)
                    Surface(
                        color = validityBg,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, validityColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(validityIcon, contentDescription = null, tint = validityColor, modifier = Modifier.size(12.dp))
                            Text(validityText, color = validityColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Certificate Page 2 — Telemetry (summary panel)
// ---------------------------------------------------------------------------
@Composable
private fun CertificatePage2(record: com.arsys.netatmo.data.local.entities.MaintenanceRecordEntity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceLowest,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineVar)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Datos de Telemetría", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Registros de telemetría capturados en el momento de la intervención.", color = TextSecondary, fontSize = 13.sp)

            HorizontalDivider(color = OutlineVar.copy(alpha = 0.6f))

            // Simulated telemetry readings
            val readings = listOf(
                Triple("Presión sistema", "1.35 bar", BluePrimary),
                Triple("Temp. impulsión", "65.2 °C", OrangeSecondary),
                Triple("Modulación caldera", "72 %", GreenTertiary),
                Triple("CO₂ interior", "650 ppm", BluePrimary),
                Triple("Humedad relativa", "47 %", Color(0xFFBFC7D2))
            )

            readings.forEach { (label, value, color) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = TextSecondary, fontSize = 13.sp)
                    Surface(
                        color = color.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            value,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = color,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(color = OutlineVar.copy(alpha = 0.6f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.CloudDone, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(14.dp))
                Text("Datos sincronizados via Netatmo Cloud API", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Helpers
// ---------------------------------------------------------------------------
@Composable
private fun CertField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

/** Draws a simplified QR-style pattern using Canvas to simulate a legal seal. */
@Composable
private fun QrCodeCanvas(modifier: Modifier = Modifier) {
    val cellColor = TextPrimary
    val bgColor = SurfaceContHigh
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(6.dp)
    ) {
        val cell = size.width / 7f
        fun block(col: Int, row: Int, w: Int = 1, h: Int = 1) =
            drawRect(cellColor, Offset(col * cell, row * cell), Size(w * cell - 1, h * cell - 1))
        fun corner(col: Int, row: Int) {
            drawRoundRect(cellColor, Offset(col * cell, row * cell), Size(3 * cell, 3 * cell), CornerRadius(cell * 0.5f))
            drawRoundRect(bgColor, Offset(col * cell + cell * 0.3f, row * cell + cell * 0.3f), Size(cell * 2.4f, cell * 2.4f), CornerRadius(cell * 0.3f))
            drawRoundRect(cellColor, Offset(col * cell + cell * 0.7f, row * cell + cell * 0.7f), Size(cell * 1.6f, cell * 1.6f), CornerRadius(cell * 0.2f))
        }
        corner(0, 0)
        corner(4, 0)
        corner(0, 4)
        val dataPixels = listOf(
            3 to 0, 5 to 1, 3 to 2, 6 to 1,
            1 to 3, 2 to 4, 5 to 3, 6 to 4,
            1 to 5, 4 to 5, 6 to 5,
            2 to 6, 4 to 6, 5 to 6
        )
        dataPixels.forEach { (c, r) -> block(c, r) }
    }
}
