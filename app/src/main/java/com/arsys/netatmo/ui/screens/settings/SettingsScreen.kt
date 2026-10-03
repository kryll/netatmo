package com.arsys.netatmo.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arsys.netatmo.data.api.models.Home

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadHomes()
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Desconectar de tu cuenta Netatmo?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                    onLogout()
                }) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hogar seleccionado
            item {
                Text("Hogar", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
            }

            if (uiState.isLoadingHomes) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(uiState.homes) { home ->
                    HomeItem(
                        home = home,
                        isSelected = uiState.selectedHomeId == home.id,
                        onSelect = { viewModel.selectHome(home.id) }
                    )
                }
                if (uiState.homes.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("No se encontraron hogares. Verifica tu cuenta Netatmo.")
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Sync settings
            item {
                Text("Sincronización", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingRow(
                            icon = Icons.Default.Sync,
                            title = "Actualización automática",
                            subtitle = "Actualizar datos cada 5 minutos",
                            trailing = {
                                Switch(
                                    checked = uiState.autoRefresh,
                                    onCheckedChange = { viewModel.setAutoRefresh(it) }
                                )
                            }
                        )
                        HorizontalDivider()
                        SettingRow(
                            icon = Icons.Default.NotificationsActive,
                            title = "Notificaciones",
                            subtitle = "Avisos de automatizaciones",
                            trailing = {
                                Switch(
                                    checked = uiState.notificationsEnabled,
                                    onCheckedChange = { viewModel.setNotifications(it) }
                                )
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // About
            item {
                Text("Acerca de", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingRow(
                            icon = Icons.Default.Info,
                            title = "Versión",
                            subtitle = "1.0.0"
                        )
                        HorizontalDivider()
                        SettingRow(
                            icon = Icons.Default.Code,
                            title = "API Netatmo",
                            subtitle = "Connect API v3"
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Logout
            item {
                OutlinedButton(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cerrar sesión")
                }
            }

            uiState.error?.let { error ->
                item {
                    Text(error, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun HomeItem(
    home: Home,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Home,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(home.name, fontWeight = FontWeight.Medium)
                home.country?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}
