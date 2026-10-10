package com.arsys.netatmo.ui.screens.maintenance

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private val BgColor = Color(0xFF101419)
private val CardColor = Color(0xFF1C2025)
private val BorderColor = Color(0xFF3F4850)
private val TextPrimary = Color(0xFFE0E2EA)
private val TextSecondary = Color(0xFFBFC7D2)
private val BlueAccent = Color(0xFF93CCFF)

private val dateFormatter = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es", "ES"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertificateDetailScreen(
    recordId: Long,
    onBack: () -> Unit,
    viewModel: MaintenanceViewModel = hiltViewModel()
) {
    LaunchedEffect(recordId) { viewModel.selectRecord(recordId) }
    val record by viewModel.selectedRecord.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val certText = record?.let { r ->
        val type = try { MaintenanceType.valueOf(r.type) } catch (e: Exception) { MaintenanceType.OTHER }
        """
CERTIFICADO DE MANTENIMIENTO
==============================
Número: ${r.certificateRef.ifBlank { "CERT-${r.id}" }}
Fecha: ${dateFormatter.format(Date(r.date))}
Tipo: ${type.displayName}
Técnico: ${r.technicianName.ifBlank { "No especificado" }}

Descripción:
${r.description}

Documento generado por Netatmo Smart Control
        """.trimIndent()
    } ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Certificado Oficial", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, certText)
                            putExtra(Intent.EXTRA_SUBJECT, "Certificado de mantenimiento Netatmo")
                        }
                        context.startActivity(Intent.createChooser(intent, "Compartir certificado"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir", tint = BlueAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E13))
            )
        },
        containerColor = BgColor
    ) { padding ->
        if (record == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BlueAccent)
            }
            return@Scaffold
        }

        val r = record!!
        val type = try { MaintenanceType.valueOf(r.type) } catch (e: Exception) { MaintenanceType.OTHER }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardColor),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BlueAccent.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Header
                    Surface(
                        color = Color(0xFF0A0E13),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BlueAccent.copy(alpha = 0.2f))
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                            Text("NETATMO", color = BlueAccent, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = 4.sp)
                        }
                    }

                    HorizontalDivider(color = BorderColor)

                    Text("CERTIFICADO DE MANTENIMIENTO", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp)

                    HorizontalDivider(color = BorderColor)

                    // Fields
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CertField("Número de certificado", r.certificateRef.ifBlank { "CERT-${r.id}" })
                        CertField("Fecha", dateFormatter.format(Date(r.date)))
                        CertField("Tipo de revisión", type.displayName)
                        CertField("Técnico", r.technicianName.ifBlank { "No especificado" })
                        CertField("Descripción", r.description)
                        r.warrantyExtendedUntil?.let {
                            CertField("Garantía válida hasta", dateFormatter.format(Date(it)))
                        }
                    }

                    HorizontalDivider(color = BorderColor)
                    Text("Documento generado por Netatmo Smart Control", color = TextSecondary, fontSize = 11.sp)
                }
            }

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, certText)
                        putExtra(Intent.EXTRA_SUBJECT, "Certificado de mantenimiento Netatmo")
                    }
                    context.startActivity(Intent.createChooser(intent, "Compartir certificado"))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent, contentColor = Color(0xFF001D31)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Compartir certificado", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CertField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = TextPrimary, fontSize = 14.sp)
    }
}
