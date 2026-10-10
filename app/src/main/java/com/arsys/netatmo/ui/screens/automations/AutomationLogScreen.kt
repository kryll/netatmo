package com.arsys.netatmo.ui.screens.automations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.arsys.netatmo.data.local.entities.AutomationLogEntity
import com.arsys.netatmo.data.repository.AutomationRepository
import com.arsys.netatmo.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AutomationLogViewModel @Inject constructor(
    automationRepository: AutomationRepository
) : ViewModel() {
    val logs: StateFlow<List<AutomationLogEntity>> = automationRepository.getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationLogScreen(
    navController: NavController,
    viewModel: AutomationLogViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsState()
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy · HH:mm:ss", Locale.getDefault()) }

    val bgColor = Color(0xFF101419)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // ── Top App Bar ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Volver",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Historial",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        HorizontalDivider(
            color = OutlineVariant,
            thickness = 1.dp
        )

        // ── Content ───────────────────────────────────────────────────────────
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                RoundedCornerShape(20.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = OutlineVariant,
                                shape = RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Sin ejecuciones registradas",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Las automatizaciones aparecerán aquí cuando se activen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    LogEntryCard(log = log, fmt = fmt)
                }
            }
        }
    }
}

// ── Log entry card ────────────────────────────────────────────────────────────

@Composable
private fun LogEntryCard(log: AutomationLogEntity, fmt: SimpleDateFormat) {
    val successColor = MaterialTheme.colorScheme.tertiary       // #62DF7D
    val errorColor = MaterialTheme.colorScheme.error            // #FFB4AB

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceContainerLow)
            .border(
                width = 1.dp,
                color = OutlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Status icon in small circular badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (log.success)
                            successColor.copy(alpha = 0.12f)
                        else
                            errorColor.copy(alpha = 0.12f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (log.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = if (log.success) "Éxito" else "Error",
                    tint = if (log.success) successColor else errorColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.automationName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                val triggerLabel = when (log.triggerType) {
                    "ENTER"    -> "Al llegar"
                    "EXIT"     -> "Al salir"
                    "CALENDAR" -> "Evento de calendario"
                    "SCHEDULE" -> "Horario programado"
                    else       -> log.triggerType
                }
                Text(
                    text = "$triggerLabel · ${fmt.format(Date(log.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!log.errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(errorColor.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = log.errorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = errorColor
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Status badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (log.success)
                            successColor.copy(alpha = 0.15f)
                        else
                            errorColor.copy(alpha = 0.15f)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (log.success) "OK" else "Error",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (log.success) successColor else errorColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
