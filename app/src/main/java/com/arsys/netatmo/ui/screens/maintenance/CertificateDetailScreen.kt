package com.arsys.netatmo.ui.screens.maintenance

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arsys.netatmo.data.local.entities.MaintenanceRecordEntity
import com.arsys.netatmo.domain.model.MaintenanceType
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Design tokens ---
private val BgColor               = Color(0xFF101419)
private val SurfaceLowest         = Color(0xFF0A0E13)
private val SurfaceCont           = Color(0xFF1C2025)
private val OutlineVar            = Color(0xFF3F4850)
private val TextPrimary           = Color(0xFFE0E2EA)
private val TextSecondary         = Color(0xFFBFC7D2)
private val TextOnSurfaceVariant  = Color(0xFF8D95A0)
private val BluePrimary           = Color(0xFF93CCFF)
private val BlueOnPrimary         = Color(0xFF001D31)

private val dateLong = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es", "ES"))
private val numberFormat = DecimalFormat("0.##", DecimalFormatSymbols(Locale("es", "ES")))

private const val DISCLAIMER = "Resumen generado por Netatmo Smart, sin validez oficial"

private fun Float?.withUnit(unit: String): String? =
    this?.let { "${numberFormat.format(it)} $unit" }

private fun equipmentRows(r: MaintenanceRecordEntity): List<Pair<String, String>> = listOfNotNull(
    r.modelo?.takeIf { it.isNotBlank() }?.let { "Modelo" to it },
    r.version?.takeIf { it.isNotBlank() }?.let { "Versión" to it },
    r.numSerie?.takeIf { it.isNotBlank() }?.let { "Nº de serie" to it },
    r.combustible?.takeIf { it.isNotBlank() }?.let { "Combustible" to it },
    r.visitTimeRange?.takeIf { it.isNotBlank() }?.let { "Franja horaria de la visita" to it }
)

private fun measurementRows(r: MaintenanceRecordEntity): List<Pair<String, String>> = listOfNotNull(
    r.rendimiento.withUnit("%")?.let { "Rendimiento" to it },
    r.co.withUnit("ppm")?.let { "CO" to it },
    r.coCorrected.withUnit("ppm")?.let { "CO corregido" to it },
    r.coAmbiente.withUnit("ppm")?.let { "CO ambiente" to it },
    r.co2.withUnit("%")?.let { "CO₂" to it },
    r.o2.withUnit("%")?.let { "O₂" to it },
    r.o2Ref.withUnit("%")?.let { "O₂ de referencia" to it },
    r.temperaturaHumos.withUnit("°C")?.let { "Temperatura de humos" to it },
    r.temperaturaAmbiente.withUnit("°C")?.let { "Temperatura ambiente" to it },
    r.temperaturaACS.withUnit("°C")?.let { "Temperatura ACS" to it },
    r.temperaturaAguaFria.withUnit("°C")?.let { "Temperatura agua fría" to it },
    r.caudalAgua.withUnit("L/min")?.let { "Caudal de agua" to it },
    r.potenciaACS.withUnit("kW")?.let { "Potencia ACS" to it },
    r.presionDinamica.withUnit("mbar")?.let { "Presión dinámica" to it },
    r.presionEstatica.withUnit("mbar")?.let { "Presión estática" to it },
    r.tiro.withUnit("mbar")?.let { "Tiro" to it },
    r.lambda.withUnit("%")?.let { "Lambda" to it },
    r.perdidasChimenea.withUnit("%")?.let { "Pérdidas por chimenea" to it }
)

private fun recordFields(r: MaintenanceRecordEntity, type: MaintenanceType): List<Pair<String, String>> = listOfNotNull(
    "Fecha" to dateLong.format(Date(r.date)),
    "Tipo" to type.displayName,
    r.technicianName.takeIf { it.isNotBlank() }?.let { "Técnico" to it },
    r.certificateRef.takeIf { it.isNotBlank() }?.let { "Referencia" to it },
    r.description.takeIf { it.isNotBlank() }?.let { "Descripción" to it },
    r.warrantyExtendedUntil?.let { "Garantía extendida hasta" to dateLong.format(Date(it)) }
)

private fun buildShareText(r: MaintenanceRecordEntity, type: MaintenanceType): String = buildString {
    appendLine("REGISTRO DE MANTENIMIENTO")
    appendLine()
    recordFields(r, type).forEach { (label, value) -> appendLine("$label: $value") }
    equipmentRows(r).takeIf { it.isNotEmpty() }?.let { rows ->
        appendLine()
        appendLine("Equipo")
        rows.forEach { (label, value) -> appendLine("$label: $value") }
    }
    measurementRows(r).takeIf { it.isNotEmpty() }?.let { rows ->
        appendLine()
        appendLine("Mediciones")
        rows.forEach { (label, value) -> appendLine("$label: $value") }
    }
    appendLine()
    append(DISCLAIMER)
}

private fun shareRecord(context: Context, text: String, record: MaintenanceRecordEntity) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, "Registro de mantenimiento Netatmo · ${dateLong.format(Date(record.date))}")
    }
    context.startActivity(Intent.createChooser(intent, "Compartir registro"))
}

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
    val record  by viewModel.selectedRecord.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val r = record
    val maintenanceType = remember(r?.type) {
        r?.let { rec ->
            try { MaintenanceType.valueOf(rec.type) } catch (_: Exception) { MaintenanceType.OTHER }
        } ?: MaintenanceType.OTHER
    }
    val shareText = r?.let { buildShareText(it, maintenanceType) } ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Registro de mantenimiento",
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
                    if (r != null) {
                        IconButton(onClick = { shareRecord(context, shareText, r) }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Compartir", tint = TextPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceCont.copy(alpha = 0.8f))
            )
        },
        containerColor = BgColor
    ) { padding ->
        if (r == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = BluePrimary) }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RecordCard(
                title = "Datos del registro",
                rows = recordFields(r, maintenanceType)
            )

            equipmentRows(r).takeIf { it.isNotEmpty() }?.let { rows ->
                RecordCard(title = "Equipo", rows = rows)
            }

            measurementRows(r).takeIf { it.isNotEmpty() }?.let { rows ->
                RecordCard(title = "Mediciones", rows = rows)
            }

            Text(
                DISCLAIMER,
                color = TextOnSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { shareRecord(context, shareText, r) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary, contentColor = BlueOnPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Compartir registro", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextOnSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Volver al historial", color = TextOnSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Helpers
// ---------------------------------------------------------------------------
@Composable
private fun RecordCard(title: String, rows: List<Pair<String, String>>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceLowest,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineVar)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, color = BluePrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = OutlineVar.copy(alpha = 0.6f))
            rows.forEach { (label, value) -> RecordField(label, value) }
        }
    }
}

@Composable
private fun RecordField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label.uppercase(Locale("es", "ES")), color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}
