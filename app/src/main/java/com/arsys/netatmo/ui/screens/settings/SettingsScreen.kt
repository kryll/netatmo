package com.arsys.netatmo.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arsys.netatmo.BuildConfig
import com.arsys.netatmo.data.api.models.Home
import com.arsys.netatmo.data.model.GitHubRelease
import com.arsys.netatmo.data.repository.UpdateStatus
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainer
import com.arsys.netatmo.ui.theme.SurfaceContainerHigh
import com.arsys.netatmo.ui.theme.SurfaceContainerLow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onNavigateToCredentials: () -> Unit = {},
    onNavigateToFamily: () -> Unit = {},
    onNavigateToAirQuality: () -> Unit = {},
    onNavigateToBoilerStatus: () -> Unit = {},
    onNavigateToMaintenance: () -> Unit = {},
    onNavigateToDevicePairing: () -> Unit = {},
    onNavigateToPurge: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var pendingInstallRelease by remember { mutableStateOf<GitHubRelease?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val exportedUri = uiState.exportedUri
    LaunchedEffect(exportedUri) {
        exportedUri?.let { uri ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Exportar backup").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            viewModel.clearExportedUri()
        }
    }

    val exportError = uiState.exportError
    LaunchedEffect(exportError) {
        exportError?.let { error ->
            snackbarHostState.showSnackbar(message = error, duration = SnackbarDuration.Long)
            viewModel.clearExportError()
        }
    }

    val importFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importBackup(context, it) } }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val canInstall = context.packageManager.canRequestPackageInstalls()
        if (BuildConfig.DEBUG) Log.d("UpdateDebug", "installPermissionLauncher: canRequestPackageInstalls=$canInstall")
        pendingInstallRelease?.let { release ->
            if (canInstall) {
                viewModel.downloadAndInstall(release)
                pendingInstallRelease = null
            } else {
                if (BuildConfig.DEBUG) Log.w("UpdateDebug", "installPermissionLauncher: permiso DENEGADO")
            }
        }
    }

    fun requestInstall(release: GitHubRelease) {
        val canInstall = context.packageManager.canRequestPackageInstalls()
        if (canInstall) {
            viewModel.downloadAndInstall(release)
        } else {
            if (BuildConfig.DEBUG) Log.w("UpdateDebug", "requestInstall: SIN permiso, abriendo ajustes")
            pendingInstallRelease = release
            installPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }

    LaunchedEffect(Unit) { viewModel.loadHomes() }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = SurfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Text(
                    text = "Ajustes",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider(color = OutlineVariant, thickness = 0.5.dp)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                // ── Hogar ──────────────────────────────────────────────
                item { SettingSectionHeader("Hogar") }

                if (uiState.isLoadingHomes) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = SurfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "No se encontraron hogares. Verifica tu cuenta Netatmo.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }

                // ── Sincronización ─────────────────────────────────────
                item { SettingSectionHeader("Sincronización") }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceContainer,
                        border = BorderStroke(1.dp, OutlineVariant)
                    ) {
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
                            HorizontalDivider(color = OutlineVariant, thickness = 0.5.dp)
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
                            HorizontalDivider(color = OutlineVariant, thickness = 0.5.dp)
                            // Anomaly threshold slider
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(SurfaceContainerHigh),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Umbral de alerta de temperatura",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "${"%.1f".format(uiState.anomalyThreshold)}°C de diferencia",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Slider(
                                    value = uiState.anomalyThreshold,
                                    onValueChange = { viewModel.setAnomalyThreshold(it) },
                                    valueRange = 0.5f..5.0f,
                                    steps = 8,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = OutlineVariant
                                    )
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }

                // ── Familia ────────────────────────────────────────────
                item { SettingSectionHeader("Familia") }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceContainerLow,
                        onClick = onNavigateToFamily
                    ) {
                        SettingRow(
                            icon = Icons.Default.Group,
                            title = "Miembros de familia",
                            subtitle = "Geovalla familiar",
                            trailing = {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }

                // ── Backup y restauración ──────────────────────────────
                item { SettingSectionHeader("Backup y restauración") }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceContainer,
                        border = BorderStroke(1.dp, OutlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.exportBackup(context) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !uiState.isExporting && !uiState.isImporting,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    border = BorderStroke(1.dp, OutlineVariant)
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Exportar")
                                }
                                OutlinedButton(
                                    onClick = { importFilePicker.launch("application/json") },
                                    modifier = Modifier.weight(1f),
                                    enabled = !uiState.isExporting && !uiState.isImporting,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    border = BorderStroke(1.dp, OutlineVariant)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Importar")
                                }
                            }
                            if (uiState.isExporting || uiState.isImporting) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = OutlineVariant
                                )
                            }
                            uiState.backupResult?.let { result ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = result,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }

                // ── Cuenta de desarrollador ────────────────────────────
                item { SettingSectionHeader("Cuenta de desarrollador") }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceContainerLow,
                        onClick = onNavigateToCredentials
                    ) {
                        SettingRow(
                            icon = Icons.Default.Key,
                            title = "Credenciales Netatmo",
                            subtitle = "Client ID y Client Secret para OAuth",
                            trailing = {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }

                // ── Acerca de ──────────────────────────────────────────
                item { SettingSectionHeader("Acerca de") }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = SurfaceContainer,
                        border = BorderStroke(1.dp, OutlineVariant)
                    ) {
                        Column {
                            SettingRow(
                                icon = Icons.Default.Info,
                                title = "Versión instalada",
                                subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                            )
                            HorizontalDivider(color = OutlineVariant, thickness = 0.5.dp)
                            SettingRow(
                                icon = Icons.Default.Code,
                                title = "API Netatmo",
                                subtitle = "Connect API v3"
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }

                // ── Actualización ──────────────────────────────────────
                item { SettingSectionHeader("Actualización") }

                item {
                    UpdateSection(
                        updateStatus = uiState.updateStatus,
                        onCheckUpdates = { viewModel.checkForUpdates() },
                        onDownloadAndInstall = { release -> requestInstall(release) },
                        onDismissError = { viewModel.dismissUpdateError() }
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                // ── Logout ─────────────────────────────────────────────
                item {
                    TextButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Cerrar sesión",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                uiState.error?.let { error ->
                    item {
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

// ── Shared composables ─────────────────────────────────────────────────────────

@Composable
fun SettingSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun HomeItem(
    home: Home,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
    else OutlineVariant

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) SurfaceContainer else SurfaceContainerLow,
        border = BorderStroke(1.dp, borderColor),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SurfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.tertiary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    home.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                home.country?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SurfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun UpdateSection(
    updateStatus: UpdateStatus,
    onCheckUpdates: () -> Unit,
    onDownloadAndInstall: (GitHubRelease) -> Unit,
    onDismissError: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainer,
        border = BorderStroke(1.dp, OutlineVariant)
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
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(SurfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Buscar actualizaciones", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                        TextButton(
                            onClick = onCheckUpdates,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) { Text("Verificar") }
                    }
                }

                is UpdateStatus.Checking -> {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Buscando actualizaciones...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                is UpdateStatus.UpToDate -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("App actualizada", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Tienes la última versión", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(
                            onClick = onCheckUpdates,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) { Text("Revisar") }
                    }
                }

                is UpdateStatus.UpdateAvailable -> {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Nueva versión disponible",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    buildString {
                                        append(updateStatus.release.displayName)
                                        if (updateStatus.release.apkSizeMb.isNotEmpty())
                                            append(" · ${updateStatus.release.apkSizeMb}")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onDownloadAndInstall(updateStatus.release) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Descargar e instalar", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                is UpdateStatus.Downloading -> {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Downloading, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Descargando actualización...", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { updateStatus.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = OutlineVariant
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
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Iniciando instalación...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                is UpdateStatus.Error -> {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Error al actualizar",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    updateStatus.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = onDismissError,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) { Text("Cerrar") }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = onCheckUpdates,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) { Text("Reintentar") }
                        }
                    }
                }
            }
        }
    }
}
