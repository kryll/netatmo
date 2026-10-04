package com.arsys.netatmo.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.arsys.netatmo.BuildConfig
import com.arsys.netatmo.data.api.models.Home
import com.arsys.netatmo.data.model.GitHubRelease
import com.arsys.netatmo.data.repository.UpdateStatus

private val Accent = Color(0xFF0284C7)
private val TextPrimary = Color(0xFF1E293B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onNavigateToCredentials: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var pendingInstallRelease by remember { mutableStateOf<GitHubRelease?>(null) }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val canInstall = context.packageManager.canRequestPackageInstalls()
        Log.d("UpdateDebug", "installPermissionLauncher: regresó de ajustes, canRequestPackageInstalls=$canInstall, pendingRelease=${pendingInstallRelease?.tagName}")
        pendingInstallRelease?.let { release ->
            if (canInstall) {
                Log.d("UpdateDebug", "installPermissionLauncher: permiso concedido, iniciando descarga")
                viewModel.downloadAndInstall(release)
                pendingInstallRelease = null
            } else {
                Log.w("UpdateDebug", "installPermissionLauncher: permiso DENEGADO — el usuario no habilitó la instalación de fuentes desconocidas")
            }
        }
    }

    fun requestInstall(release: GitHubRelease) {
        val canInstall = context.packageManager.canRequestPackageInstalls()
        Log.d("UpdateDebug", "requestInstall: release=${release.tagName}, canRequestPackageInstalls=$canInstall")
        if (canInstall) {
            Log.d("UpdateDebug", "requestInstall: permiso OK, llamando downloadAndInstall")
            viewModel.downloadAndInstall(release)
        } else {
            Log.w("UpdateDebug", "requestInstall: SIN permiso — abriendo ACTION_MANAGE_UNKNOWN_APP_SOURCES para package=${context.packageName}")
            pendingInstallRelease = release
            installPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }

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

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Text(
                    text = "Ajustes",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider(color = Color(0xFFE2E8F0))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hogar seleccionado
            item {
                Text(
                    "Hogar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Accent,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
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
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Accent)
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
                Text(
                    "Sincronización",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Accent,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column {
                        SettingRow(
                            icon = Icons.Default.Sync,
                            title = "Actualización automática",
                            subtitle = "Actualizar datos cada 5 minutos",
                            trailing = {
                                Switch(
                                    checked = uiState.autoRefresh,
                                    onCheckedChange = { viewModel.setAutoRefresh(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Accent
                                    )
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
                                    onCheckedChange = { viewModel.setNotifications(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Accent
                                    )
                                )
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Netatmo credentials
            item {
                Text(
                    "Cuenta de desarrollador",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Accent,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    onClick = onNavigateToCredentials
                ) {
                    SettingRow(
                        icon = Icons.Default.Key,
                        title = "Credenciales Netatmo",
                        subtitle = "Client ID y Client Secret para OAuth",
                        trailing = {
                            Icon(Icons.Default.ChevronRight, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // About
            item {
                Text(
                    "Acerca de",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Accent,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column {
                        SettingRow(
                            icon = Icons.Default.Info,
                            title = "Versión instalada",
                            subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
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

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Updates
            item {
                Text(
                    "Actualización",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Accent,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                UpdateSection(
                    updateStatus = uiState.updateStatus,
                    onCheckUpdates = { viewModel.checkForUpdates() },
                    onDownloadAndInstall = { release -> requestInstall(release) },
                    onDismissError = { viewModel.dismissUpdateError() }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Logout
            item {
                OutlinedButton(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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
        } // end Column
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
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Accent.copy(alpha = 0.12f)
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
                tint = if (isSelected) Accent else MaterialTheme.colorScheme.onSurfaceVariant
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
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Accent)
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
        Icon(icon, contentDescription = null, tint = Accent)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
fun UpdateSection(
    updateStatus: UpdateStatus,
    onCheckUpdates: () -> Unit,
    onDownloadAndInstall: (com.arsys.netatmo.data.model.GitHubRelease) -> Unit,
    onDismissError: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (updateStatus) {
                is UpdateStatus.Idle -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Accent)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Buscar actualizaciones",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                        }
                        TextButton(
                            onClick = onCheckUpdates,
                            colors = ButtonDefaults.textButtonColors(contentColor = Accent)
                        ) { Text("Verificar") }
                    }
                }

                is UpdateStatus.Checking -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Accent
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Buscando actualizaciones...",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is UpdateStatus.UpToDate -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Accent)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("App actualizada",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium)
                                Text("Tienes la última versión",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(
                            onClick = onCheckUpdates,
                            colors = ButtonDefaults.textButtonColors(contentColor = Accent)
                        ) { Text("Revisar") }
                    }
                }

                is UpdateStatus.UpdateAvailable -> {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NewReleases, contentDescription = null, tint = Accent)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Nueva versión disponible",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(buildString {
                                    append(updateStatus.release.displayName)
                                    if (updateStatus.release.apkSizeMb.isNotEmpty())
                                        append(" · ${updateStatus.release.apkSizeMb}")
                                },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onDownloadAndInstall(updateStatus.release) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Descargar e instalar")
                        }
                    }
                }

                is UpdateStatus.Downloading -> {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Downloading, contentDescription = null, tint = Accent)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Descargando actualización...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { updateStatus.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = Accent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${(updateStatus.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is UpdateStatus.Installing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Accent
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Iniciando instalación...",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is UpdateStatus.Error -> {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null,
                                tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Error al actualizar",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error)
                                Text(updateStatus.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onDismissError) { Text("Cerrar") }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = onCheckUpdates,
                                colors = ButtonDefaults.textButtonColors(contentColor = Accent)
                            ) { Text("Reintentar") }
                        }
                    }
                }
            }
        }
    }
}
