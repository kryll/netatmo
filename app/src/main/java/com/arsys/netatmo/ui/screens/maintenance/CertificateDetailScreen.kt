package com.arsys.netatmo.ui.screens.maintenance

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arsys.netatmo.data.local.entities.MaintenanceRecordEntity
import com.arsys.netatmo.domain.model.MaintenanceType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificateDetailScreen(
    recordId: Long,
    onBack: () -> Unit,
    viewModel: MaintenanceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val record by viewModel.selectedRecord.collectAsStateWithLifecycle()

    LaunchedEffect(recordId) {
        viewModel.selectRecord(recordId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Certificado Oficial") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            record?.let { rec ->
                                val shareText = buildCertificateText(rec)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Certificado de Mantenimiento - ${rec.certificateRef ?: rec.id}")
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(intent, "Compartir certificado"))
                            }
                        },
                        enabled = record != null
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (record == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            record?.let { rec ->
                CertificateCard(
                    record = rec,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CertificateCard(
    record: MaintenanceRecordEntity,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val formattedDate = remember(record.date) { dateFormat.format(Date(record.date)) }
    val validUntilDate = remember(record.date) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = record.date
            add(Calendar.YEAR, 1)
        }
        dateFormat.format(cal.time)
    }
    val type = remember(record.type) {
        runCatching { MaintenanceType.valueOf(record.type) }.getOrNull()
    }
    val typeLabel = when (type) {
        MaintenanceType.PREVENTIVE -> "Mantenimiento Preventivo"
        MaintenanceType.CORRECTIVE -> "Mantenimiento Correctivo"
        MaintenanceType.INSPECTION -> "Inspección Técnica"
        MaintenanceType.CLEANING -> "Limpieza y Ajuste"
        else -> record.type
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo placeholder
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 60.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NETATMO",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(
                        3f,
                        androidx.compose.ui.unit.TextUnitType.Sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "CERTIFICADO DE MANTENIMIENTO",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Spacer(modifier = Modifier.height(20.dp))

            // Certificate fields
            CertificateField(label = "Número de certificado", value = record.certificateRef ?: "CERT-${record.id}")
            CertificateField(label = "Fecha", value = formattedDate)
            CertificateField(label = "Tipo de revisión", value = typeLabel)
            CertificateField(
                label = "Técnico",
                value = record.technicianName?.takeIf { it.isNotBlank() } ?: "No especificado"
            )
            CertificateField(label = "Descripción", value = record.description)
            CertificateField(label = "Válido hasta", value = validUntilDate)

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp)

            Spacer(modifier = Modifier.height(16.dp))

            // Seal placeholder
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OFICIAL",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Documento generado por Netatmo Smart Control",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CertificateField(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }
}

private fun buildCertificateText(record: MaintenanceRecordEntity): String {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(record.date))
    val cal = Calendar.getInstance().apply {
        timeInMillis = record.date
        add(Calendar.YEAR, 1)
    }
    val validUntil = dateFormat.format(cal.time)

    return buildString {
        appendLine("=== CERTIFICADO DE MANTENIMIENTO ===")
        appendLine("Generado por Netatmo Smart Control")
        appendLine()
        appendLine("Número de certificado: ${record.certificateRef ?: "CERT-${record.id}"}")
        appendLine("Fecha: $formattedDate")
        appendLine("Tipo de revisión: ${record.type}")
        appendLine("Técnico: ${record.technicianName?.takeIf { it.isNotBlank() } ?: "No especificado"}")
        appendLine("Descripción: ${record.description}")
        appendLine("Válido hasta: $validUntil")
        appendLine()
        appendLine("Documento generado por Netatmo Smart Control")
    }
}
