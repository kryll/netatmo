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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arsys.netatmo.domain.model.MaintenanceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Design tokens ---
private val BgColor               = Color(0xFF101419)
private val SurfaceLowest         = Color(0xFF0A0E13)
private val SurfaceContLowest     = Color(0xFF0D1117)
private val SurfaceContLow        = Color(0xFF181C21)
private val SurfaceCont           = Color(0xFF1C2025)
private val SurfaceContHigh       = Color(0xFF262A30)
private val SurfaceContHighest    = Color(0xFF31353B)
private val OutlineVar            = Color(0xFF3F4850)
private val TextPrimary           = Color(0xFFE0E2EA)
private val TextSecondary         = Color(0xFFBFC7D2)
private val TextOnSurfaceVariant  = Color(0xFF8D95A0)
private val BluePrimary           = Color(0xFF93CCFF)
private val BlueOnPrimary         = Color(0xFF001D31)
private val GreenTertiary         = Color(0xFF62DF7D)
private val ErrorColor            = Color(0xFFFFB4AB)
private val ErrorContainer        = Color(0xFF93000A)
private val OrangeSecondary       = Color(0xFFFFB599)

private val dateLong = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es", "ES"))
private val dateTime = SimpleDateFormat("dd/MM/yyyy · HH:mm 'CEST'", Locale("es", "ES"))

// ---------------------------------------------------------------------------
//  Root screen
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificateDetailScreen(
    recordId: Long,
    onBack: () -> Unit,
    viewModel: MaintenanceViewModel = hiltViewModel()
) {
    LaunchedEffect(recordId) { viewModel.selectRecord(recordId) }
    val record   by viewModel.selectedRecord.collectAsStateWithLifecycle()
    val uiState  by viewModel.uiState.collectAsStateWithLifecycle()
    val context  = LocalContext.current
    var activePage by remember { mutableIntStateOf(1) }

    val now = System.currentTimeMillis()
    val maintenanceType = remember(record?.type) {
        record?.let { r ->
            try { MaintenanceType.valueOf(r.type) } catch (_: Exception) { MaintenanceType.OTHER }
        }
    }

    val isWarrantyActive = record?.warrantyExtendedUntil?.let { it > now } == true
    val certNumber       = record?.let { r -> r.certificateRef.ifBlank { "CERT-${r.id}" } } ?: "—"
    val isSent           = record?.let { r -> viewModel.isSent(r.id) } == true

    // Build share text (keep existing ViewModel connection)
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
                        "Certificado Oficial #Nd 84920 Ok",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Outlined.Print, contentDescription = "Imprimir", tint = TextPrimary)
                    }
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, certText)
                            putExtra(Intent.EXTRA_SUBJECT, "Certificado de mantenimiento Netatmo #$certNumber")
                        }
                        context.startActivity(Intent.createChooser(intent, "Compartir certificado"))
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Compartir", tint = TextPrimary)
                    }
                    // Filled circular avatar
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(BluePrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = "Usuario",
                            tint = BlueOnPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceCont.copy(alpha = 0.8f))
            )
        },
        containerColor = BgColor
    ) { padding ->
        if (record == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = BluePrimary) }
            return@Scaffold
        }

        val r = record!!

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Doc control ribbon
            DocControlRibbon(
                certNumber   = certNumber,
                activePage   = activePage,
                isValid      = isWarrantyActive || (r.warrantyExtendedUntil == null),
                onPageSelected = { activePage = it }
            )

            // Scrollable body
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
                        1    -> CertificatePage1(
                            record           = r,
                            certNumber       = certNumber,
                            maintenanceType  = maintenanceType ?: MaintenanceType.OTHER,
                            isWarrantyActive = isWarrantyActive,
                            now              = now
                        )
                        else -> CertificatePage2(record = r, certNumber = certNumber)
                    }
                }

                // --- Bottom actions (design-matched) ---
                BottomActions(
                    certText      = certText,
                    certNumber    = certNumber,
                    context       = context,
                    isSent        = isSent,
                    onMarkSent    = { viewModel.markAsSent(r.id) },
                    onBack        = onBack
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Bottom actions section
// ---------------------------------------------------------------------------
@Composable
private fun BottomActions(
    certText: String,
    certNumber: String,
    context: android.content.Context,
    isSent: Boolean,
    onMarkSent: () -> Unit,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Primary download button
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, certText)
                    putExtra(Intent.EXTRA_SUBJECT, "Certificado de mantenimiento Netatmo #$certNumber")
                }
                context.startActivity(Intent.createChooser(intent, "Descargar/Compartir certificado"))
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary, contentColor = BlueOnPrimary),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Descargar Documento PDF Completo (Págs. 1 y 2) · 244 KB",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 3-column secondary grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple(Icons.Default.ChevronLeft, "Pág. Anterior") { onBack() },
                Triple(Icons.Outlined.Build, "Servicio Téc.") { },
                Triple(Icons.Outlined.Print, "Imprimir") { }
            ).forEach { (icon, label, action) ->
                Surface(
                    onClick = action,
                    modifier = Modifier.weight(1f),
                    color = SurfaceContHigh,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(icon, contentDescription = label, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // Back text link
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextOnSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Volver al Informe de Diagnóstico", color = TextOnSurfaceVariant, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------------------
//  Document Control Ribbon  (3-row layout)
// ---------------------------------------------------------------------------
@Composable
private fun DocControlRibbon(
    certNumber: String,
    activePage: Int,
    isValid: Boolean,
    onPageSelected: (Int) -> Unit
) {
    Surface(
        color = SurfaceContHigh,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(0.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: pdf icon + filename + "FIRMA VÁLIDA" badge + "100%" chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    tint = BluePrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Certificado_${certNumber}.pdf",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // FIRMA VÁLIDA / EXPIRADO inline badge
                Surface(
                    color = if (isValid) GreenTertiary.copy(alpha = 0.15f) else ErrorContainer.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isValid) Icons.Filled.Verified else Icons.Filled.GppBad,
                            contentDescription = null,
                            tint = if (isValid) GreenTertiary else ErrorColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            if (isValid) "FIRMA VÁLIDA" else "EXPIRADO",
                            color = if (isValid) GreenTertiary else ErrorColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
                // 100% chip
                Surface(color = SurfaceCont, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "100%",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        color = TextOnSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Row 2: page subtitle
            Text(
                "Pág. 2 de 2 · Anexo Técnico de Telemetría · ISO 5167-2 / UNE-EN 14336",
                color = TextOnSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Row 3: page tabs (2-column grid)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tab 1 — Dictamen (inactive)
                Surface(
                    onClick = { onPageSelected(1) },
                    modifier = Modifier.weight(1f),
                    color = if (activePage == 1) BluePrimary.copy(alpha = 0.20f) else SurfaceCont,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (activePage == 1) Icons.Filled.Description else Icons.Outlined.Description,
                            contentDescription = null,
                            tint = if (activePage == 1) BluePrimary else TextOnSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "Pág. 1 · Dictamen",
                            color = if (activePage == 1) BluePrimary else TextOnSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                    }
                }

                // Tab 2 — Telemetría (active) with "ANEXO EN VIVO" sub-badge
                Surface(
                    onClick = { onPageSelected(2) },
                    modifier = Modifier.weight(1f),
                    color = if (activePage == 2) BluePrimary.copy(alpha = 0.20f) else SurfaceCont,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (activePage == 2) Icons.Filled.Analytics else Icons.Outlined.Analytics,
                            contentDescription = null,
                            tint = if (activePage == 2) BluePrimary else TextOnSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "Pág. 2 · Telemetría",
                            color = if (activePage == 2) BluePrimary else TextOnSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        if (activePage == 2) {
                            Surface(
                                color = BluePrimary,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "ANEXO EN VIVO",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    color = BlueOnPrimary,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
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
//  Certificate Page 1 — Dictamen (unchanged logic, minor style polish)
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
            // Watermark
            Box(
                modifier = Modifier.align(Alignment.Center).size(200.dp),
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
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Brand header
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
                                modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF66018))
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
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text("FOLIO CERTIFICADO", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            Text("#$certNumber", color = BluePrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                HorizontalDivider(color = OutlineVar.copy(alpha = 0.6f))

                // Title + QR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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

                val validityColor = if (isWarrantyActive || record.warrantyExtendedUntil == null) GreenTertiary else ErrorColor
                val validityBg    = if (isWarrantyActive || record.warrantyExtendedUntil == null) GreenTertiary.copy(alpha = 0.12f) else ErrorContainer.copy(alpha = 0.15f)
                val validityText  = if (isWarrantyActive || record.warrantyExtendedUntil == null) "VÁLIDO" else "EXPIRADO"
                val validityIcon  = if (isWarrantyActive || record.warrantyExtendedUntil == null) Icons.Default.CheckCircle else Icons.Default.Cancel

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
//  Certificate Page 2 — Telemetría (full Stitch redesign)
// ---------------------------------------------------------------------------
@Composable
private fun CertificatePage2(
    record: com.arsys.netatmo.data.local.entities.MaintenanceRecordEntity,
    certNumber: String
) {
    // Outer card: bg-surface-container, rounded-xl, shadow-xl, no visible border
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp)),
        color = SurfaceCont,
        shape = RoundedCornerShape(16.dp)
    ) {
        Box {
            // Security watermark
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Security,
                    contentDescription = null,
                    tint = TextPrimary.copy(alpha = 0.05f),
                    modifier = Modifier.size(200.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // (a) Technical document header
                TelemetryDocHeader(certNumber = certNumber, record = record)

                // (b) 3-column calibration grid
                CalibrationGrid()

                // (c) Chart 1 — Presión Hidrostática
                PressureChart()

                // (d) Chart 2 — Caudal y Salto Térmico
                FlowDeltaTChart()

                // (e) Chart 3 — FFT/THD Espectral
                FFTChart()

                // (f) Audit Log
                AuditLog()

                // (g) Digital Seal footer
                DigitalSealFooter(certNumber = certNumber)
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  (a) Technical document header
// ---------------------------------------------------------------------------
@Composable
private fun TelemetryDocHeader(
    certNumber: String,
    record: com.arsys.netatmo.data.local.entities.MaintenanceRecordEntity
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Brand label row + LAB chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Thermostat, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(14.dp))
                Text(
                    "NETATMO SMART ATMOSPHERE",
                    color = BluePrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )
            }
            Surface(color = SurfaceContHigh, shape = RoundedCornerShape(6.dp)) {
                Text(
                    "LAB FLUIDOS V-3",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // H2 title
        Text(
            "Anexo de Telemetría en Tiempo Real\n(Muestreo 100 Hz / 60s)",
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 24.sp
        )

        // Subtitle: folio · time · sensor note
        Text(
            "Folio #$certNumber · ${dateTime.format(Date(record.date))} · Sensor Pt100 calibrado ±0.05°C",
            color = TextOnSurfaceVariant,
            fontSize = 10.sp,
            lineHeight = 15.sp
        )
    }
}

// ---------------------------------------------------------------------------
//  (b) 3-column calibration grid
// ---------------------------------------------------------------------------
@Composable
private fun CalibrationGrid() {
    Surface(color = SurfaceContLow, shape = RoundedCornerShape(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CalibrationCell(
                title = "SONDA TÉRMICA",
                color = GreenTertiary,
                model = "PT1000",
                note = "±0.05 °C",
                modifier = Modifier.weight(1f)
            )
            CalibrationCell(
                title = "PRESOSTATO",
                color = BluePrimary,
                model = "Honeywell",
                note = "0–6 bar",
                modifier = Modifier.weight(1f)
            )
            CalibrationCell(
                title = "TACÓMETRO ECM",
                color = OrangeSecondary,
                model = "Rotor Síncrono",
                note = "±1 rpm",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CalibrationCell(
    title: String,
    color: Color,
    model: String,
    note: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = 0.5.sp)
        Text(model, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(note, color = TextOnSurfaceVariant, fontSize = 9.sp, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------------------
//  (c) Chart 1 — Presión Hidrostática
// ---------------------------------------------------------------------------
@Composable
private fun PressureChart() {
    Surface(color = SurfaceContHigh, shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(16.dp))
                    Text("Presión Hidrostática", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Surface(color = BluePrimary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "1.35 bar NOM",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        color = BluePrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // SVG-style canvas chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceContLowest)
            ) {
                PressureLineChart()
            }

            // Floating label row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("0s" to "10s", "20s" to "30s", "40s" to "60s").forEach { (a, b) ->
                    Text("$a – $b", color = TextOnSurfaceVariant, fontSize = 9.sp)
                }
            }

            // 4-column stats grid
            Surface(color = SurfaceCont, shape = RoundedCornerShape(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        "P_min" to "1.28 bar",
                        "P_max" to "1.42 bar",
                        "Desv. σ" to "0.034",
                        "Rizado" to "2.51%"
                    ).forEach { (label, value) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, color = TextOnSurfaceVariant, fontSize = 9.sp)
                            Text(value, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PressureLineChart() {
    val blue = BluePrimary
    val blueAlpha = blue.copy(alpha = 0.25f)
    val toleranceFill = blue.copy(alpha = 0.06f)
    val gridColor = OutlineVar.copy(alpha = 0.30f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val padL = 0f; val padR = 0f; val padT = 12f; val padB = 20f
        val chartW = w - padL - padR
        val chartH = h - padT - padB

        // dashed grid lines
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        for (i in 0..3) {
            val y = padT + chartH * i / 3f
            drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), strokeWidth = 1f, pathEffect = dashEffect)
        }

        // tolerance band (1.25–1.45 bar mapped to chart)
        val nomMin = 0.25f; val nomMax = 0.75f  // normalized
        drawRect(
            color = toleranceFill,
            topLeft = Offset(padL, padT + chartH * (1f - nomMax)),
            size = Size(chartW, chartH * (nomMax - nomMin))
        )

        // pressure curve points (normalized 0..1)
        val pts = listOf(0.5f, 0.52f, 0.48f, 0.55f, 0.58f, 0.54f, 0.60f, 0.57f, 0.53f, 0.56f, 0.59f, 0.55f)
        val xs = pts.indices.map { padL + chartW * it / (pts.size - 1f) }
        val ys = pts.map { padT + chartH * (1f - it) }

        // gradient fill under curve
        val fillPath = Path().apply {
            moveTo(xs.first(), h - padB)
            xs.zip(ys).forEachIndexed { i, (x, y) ->
                if (i == 0) lineTo(x, y) else {
                    val prevX = xs[i - 1]; val prevY = ys[i - 1]
                    val cx = (prevX + x) / 2f
                    cubicTo(cx, prevY, cx, y, x, y)
                }
            }
            lineTo(xs.last(), h - padB)
            close()
        }
        drawPath(fillPath, Brush.verticalGradient(listOf(blueAlpha, Color.Transparent), startY = padT, endY = h - padB))

        // stroke
        val strokePath = Path().apply {
            xs.zip(ys).forEachIndexed { i, (x, y) ->
                if (i == 0) moveTo(x, y)
                else {
                    val prevX = xs[i - 1]; val prevY = ys[i - 1]
                    val cx = (prevX + x) / 2f
                    cubicTo(cx, prevY, cx, y, x, y)
                }
            }
        }
        drawPath(strokePath, color = blue, style = Stroke(width = 2f, cap = StrokeCap.Round))

        // event circles at anomaly points
        val eventPoints = listOf(3 to Color(0xFFFFB599), 7 to Color(0xFF62DF7D))
        eventPoints.forEach { (idx, col) ->
            drawCircle(col, radius = 5f, center = Offset(xs[idx], ys[idx]))
            drawCircle(Color.Transparent, radius = 5f, center = Offset(xs[idx], ys[idx]), style = Stroke(1.5f))
        }
    }
}

// ---------------------------------------------------------------------------
//  (d) Chart 2 — Caudal y Salto Térmico
// ---------------------------------------------------------------------------
@Composable
private fun FlowDeltaTChart() {
    Surface(color = SurfaceContHigh, shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(16.dp))
                Text("Caudal y Salto Térmico", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem(color = BluePrimary, label = "Caudal (l/h)")
                LegendItem(color = OrangeSecondary, label = "ΔT (°C)", dashed = true)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceContLowest)
            ) {
                DualLineChart()
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, dashed: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 20.dp, height = 10.dp)) {
            if (dashed) {
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))
                drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 2f, pathEffect = dashEffect)
            } else {
                drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 2f)
            }
        }
        Text(label, color = TextOnSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
private fun DualLineChart() {
    val blue   = BluePrimary
    val orange = OrangeSecondary
    val grid   = OutlineVar.copy(alpha = 0.30f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val pad = 12f
        val chartW = w - pad * 2f; val chartH = h - pad * 2f

        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
        for (i in 0..3) {
            val y = pad + chartH * i / 3f
            drawLine(grid, Offset(pad, y), Offset(w - pad, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
        }

        // Caudal (solid blue)
        val flowPts = listOf(0.45f, 0.50f, 0.55f, 0.52f, 0.58f, 0.60f, 0.57f, 0.62f, 0.58f, 0.55f)
        drawChartLine(flowPts, blue, false, pad, chartW, chartH)

        // ΔT (dashed orange)
        val dtPts = listOf(0.30f, 0.35f, 0.40f, 0.38f, 0.43f, 0.47f, 0.44f, 0.50f, 0.46f, 0.42f)
        drawChartLine(dtPts, orange, true, pad, chartW, chartH)
    }
}

private fun DrawScope.drawChartLine(
    pts: List<Float>,
    color: Color,
    dashed: Boolean,
    pad: Float,
    chartW: Float,
    chartH: Float
) {
    val xs = pts.indices.map { pad + chartW * it / (pts.size - 1f) }
    val ys = pts.map { pad + chartH * (1f - it) }
    val path = Path().apply {
        xs.zip(ys).forEachIndexed { i, (x, y) ->
            if (i == 0) moveTo(x, y)
            else {
                val prevX = xs[i - 1]; val prevY = ys[i - 1]
                val cx = (prevX + x) / 2f
                cubicTo(cx, prevY, cx, y, x, y)
            }
        }
    }
    val pe = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 5f)) else null
    drawPath(path, color = color, style = Stroke(width = 2f, cap = StrokeCap.Round, pathEffect = pe))
}

// ---------------------------------------------------------------------------
//  (e) Chart 3 — FFT/THD Espectral
// ---------------------------------------------------------------------------
@Composable
private fun FFTChart() {
    Surface(color = SurfaceContHigh, shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = GreenTertiary, modifier = Modifier.size(16.dp))
                    Text("FFT/THD Espectral", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Surface(color = GreenTertiary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "0.0 dB MICROBURBUJAS",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        color = GreenTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bar chart (12 bars)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceContLowest)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                val barHeights = listOf(0.90f, 0.30f, 0.20f, 0.45f, 0.15f, 0.10f, 0.25f, 0.18f, 0.08f, 0.12f, 0.07f, 0.05f)
                barHeights.forEachIndexed { i, h ->
                    val color = if (i == 0) GreenTertiary else BluePrimary.copy(alpha = 0.5f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .fillMaxHeight(h)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(color)
                    )
                }
            }

            // Freq/THD annotation
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("50 Hz (fundamental)", color = GreenTertiary, fontSize = 9.sp)
                Text("THD: 0.8%", color = TextOnSurfaceVariant, fontSize = 9.sp)
                Text("1 kHz", color = TextOnSurfaceVariant, fontSize = 9.sp)
            }

            // Acoustic verdict
            Surface(color = SurfaceCont, shape = RoundedCornerShape(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GreenTertiary, modifier = Modifier.size(14.dp))
                    Text(
                        "Sin microburbujas detectadas · Nivel acústico dentro de rango UNE-EN 14336",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  (f) Chronological Audit Log
// ---------------------------------------------------------------------------
private data class AuditEvent(
    val timestamp: String,
    val description: String,
    val badge: String,
    val badgeColor: Color,
    val badgeBg: Color
)

@Composable
private fun AuditLog() {
    val events = remember {
        listOf(
            AuditEvent("09:02:14", "Inspección de estanqueidad del colector: sin fugas detectadas en ningún ramal", "ESTANCO", GreenTertiary, GreenTertiary.copy(0.15f)),
            AuditEvent("09:14:37", "Lectura de presión nominal confirmada en 1.35 bar dentro de tolerancia ±0.10 bar", "CONFORME", BluePrimary, BluePrimary.copy(0.15f)),
            AuditEvent("09:28:55", "Análisis espectral FFT: flujo laminar verificado Re < 2300 sin turbulencias", "LAMINAR", GreenTertiary, GreenTertiary.copy(0.15f)),
            AuditEvent("09:41:20", "Eficiencia energética caldera calculada en 94.7% — clasificación A+", "A+", GreenTertiary, GreenTertiary.copy(0.15f)),
            AuditEvent("09:53:08", "Purga y sellado definitivo del circuito hidráulico completado correctamente", "SELLADO", BluePrimary, BluePrimary.copy(0.20f)),
            AuditEvent("10:05:33", "Sincronización de telemetría con Netatmo Cloud API: 6/6 registros validados", "CONFORME", BluePrimary, BluePrimary.copy(0.15f))
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.History, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(16.dp))
                Text("Registro de Auditoría", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Surface(color = GreenTertiary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                Text(
                    "6/6 Hitos Superados",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    color = GreenTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Event rows
        events.forEach { event ->
            Surface(color = SurfaceContHigh, shape = RoundedCornerShape(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        event.timestamp,
                        color = BluePrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        event.description,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        lineHeight = 15.sp
                    )
                    Surface(color = event.badgeBg, shape = RoundedCornerShape(5.dp)) {
                        Text(
                            event.badge,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            color = event.badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  (g) Digital Seal footer
// ---------------------------------------------------------------------------
@Composable
private fun DigitalSealFooter(certNumber: String) {
    Surface(color = SurfaceContLow, shape = RoundedCornerShape(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // QR box
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceContHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.QrCode2, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(32.dp))
            }

            // Text column
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "Sello Digital Certificado",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "TSA: ${dateTime.format(Date())} · RFC 3161",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = GreenTertiary, modifier = Modifier.size(10.dp))
                    Text(
                        "Documento completo (Páginas 1 y 2 validadas)",
                        color = GreenTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Helpers (shared)
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
    val bgColor   = SurfaceContHigh
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(6.dp)
    ) {
        val cell = size.width / 7f
        fun block(col: Int, row: Int) =
            drawRect(cellColor, Offset(col * cell, row * cell), Size(cell - 1f, cell - 1f))
        fun corner(col: Int, row: Int) {
            drawRoundRect(cellColor, Offset(col * cell, row * cell), Size(3 * cell, 3 * cell), CornerRadius(cell * 0.5f))
            drawRoundRect(bgColor, Offset(col * cell + cell * 0.3f, row * cell + cell * 0.3f), Size(cell * 2.4f, cell * 2.4f), CornerRadius(cell * 0.3f))
            drawRoundRect(cellColor, Offset(col * cell + cell * 0.7f, row * cell + cell * 0.7f), Size(cell * 1.6f, cell * 1.6f), CornerRadius(cell * 0.2f))
        }
        corner(0, 0); corner(4, 0); corner(0, 4)
        listOf(3 to 0, 5 to 1, 3 to 2, 6 to 1, 1 to 3, 2 to 4, 5 to 3, 6 to 4, 1 to 5, 4 to 5, 6 to 5, 2 to 6, 4 to 6, 5 to 6)
            .forEach { (c, r) -> block(c, r) }
    }
}
