package com.arsys.netatmo.ui.screens.automations

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainer
import com.arsys.netatmo.ui.theme.SurfaceContainerHigh
import com.arsys.netatmo.ui.theme.SurfaceContainerHighest
import com.arsys.netatmo.ui.theme.SurfaceContainerLow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceDetailScreen(
    automationId: Long,
    navController: NavController,
    viewModel: GeofenceDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Local UI states
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = arrive, 1 = leave
    var isAutomationActive by remember { mutableStateOf(true) }
    var notificationsEnabled by remember { mutableStateOf(true) }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — geofence registered either way */ }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.useCurrentLocation()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val bgGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (!bgGranted) backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless — notifications are optional */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fineGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val bgGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (fineGranted && !bgGranted) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    LaunchedEffect(automationId) { viewModel.load(automationId) }
    LaunchedEffect(uiState.saved) { if (uiState.saved) navController.popBackStack() }

    // Pulsing animation for active status dot
    val infiniteTransition = rememberInfiniteTransition(label = "activePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Geovalla Detalle",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { navController.popBackStack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                "Guardar",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Usuario",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceContainer)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- CONTEXT SUMMARY ROW ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AUTOMATIZACIÓN DE PRESENCIA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Llegada a casa inteligente · Modo Confort",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Active status pill with pulsing dot
                Surface(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary)
                        )
                        Text(
                            text = "Activa",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // --- MAP SECTION ---
            val webViewRef = remember { mutableStateOf<WebView?>(null) }
            val mapLat = uiState.latitude.toDoubleOrNull() ?: 40.4168
            val mapLng = uiState.longitude.toDoubleOrNull() ?: -3.7038
            val mapRadius = uiState.radius

            // key() only on lat/lng: radius changes are handled via evaluateJavascript
            // without destroying the WebView (which would restart the CDN download)
            key(uiState.latitude, uiState.longitude) {
                val initialHtml = buildMapHtml(mapLat, mapLng, mapRadius)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, OutlineVariant, RoundedCornerShape(16.dp))
                ) {
                    AndroidView(
                        modifier = Modifier.matchParentSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                @SuppressLint("SetJavaScriptEnabled")
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                isScrollContainer = false
                                webViewClient = object : WebViewClient() {
                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: android.webkit.WebResourceRequest?,
                                        error: android.webkit.WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        android.util.Log.e("MapWebView",
                                            "Resource error: ${request?.url} - ${error?.description}")
                                    }
                                    override fun onReceivedSslError(
                                        view: WebView?,
                                        handler: android.webkit.SslErrorHandler?,
                                        error: android.net.http.SslError?
                                    ) {
                                        android.util.Log.e("MapWebView", "SSL error: $error")
                                        handler?.cancel()
                                    }
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        view?.evaluateJavascript(
                                            "if(typeof map!=='undefined'){map.invalidateSize();}",
                                            null
                                        )
                                    }
                                }
                                webChromeClient = object : android.webkit.WebChromeClient() {
                                    override fun onConsoleMessage(
                                        msg: android.webkit.ConsoleMessage?
                                    ): Boolean {
                                        android.util.Log.d("MapJS",
                                            "[${msg?.messageLevel()}] ${msg?.message()} (line ${msg?.lineNumber()})")
                                        return true
                                    }
                                }
                                addJavascriptInterface(
                                    MapBridge { newLat, newLng ->
                                        viewModel.updateLatitude(newLat)
                                        viewModel.updateLongitude(newLng)
                                    },
                                    "Android"
                                )
                                loadDataWithBaseURL(
                                    "https://openstreetmap.org",
                                    initialHtml,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                                webViewRef.value = this
                            }
                        },
                        update = { webView ->
                            webView.evaluateJavascript(
                                "if(typeof updateRadius==='function'){updateRadius($mapRadius);}",
                                null
                            )
                        }
                    )

                    // Floating controls top-right
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceContainer.copy(alpha = 0.9f))
                                .clickable {
                                    val fineGranted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    val coarseGranted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_COARSE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (fineGranted || coarseGranted) {
                                        viewModel.useCurrentLocation()
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isLoadingLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = "Mi ubicación",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceContainer.copy(alpha = 0.9f))
                                .clickable { webViewRef.value?.evaluateJavascript("zoomIn()", null) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Acercar",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceContainer.copy(alpha = 0.9f))
                                .clickable { webViewRef.value?.evaluateJavascript("zoomOut()", null) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = "Alejar",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // GPS badge bottom-left
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp),
                        color = Color(0xCC0A0E13),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.NearMe,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${uiState.radius.toInt()} m",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // --- SEGMENTED CONTROL ---
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = SurfaceContainerLow,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabLabels = listOf("Al llegar a casa", "Al salir de casa")
                        val tabIcons = listOf(Icons.Default.Home, Icons.Default.DirectionsCar)
                        tabLabels.forEachIndexed { index, label ->
                            val isSelected = selectedTab == index
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedTab = index },
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        tabIcons[index],
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = if (selectedTab == 0)
                        "Configura la temperatura cuando llegues a casa"
                    else
                        "Configura la temperatura cuando salgas de casa",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- RADIUS CONFIG CARD ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(1.dp, OutlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "RADIO DE ACTIVACIÓN",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${uiState.radius.toInt()}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "metros",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // ETA chip
                        Surface(
                            color = SurfaceContainerHigh,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.DirectionsWalk,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "~${(uiState.radius / 80f).roundToInt()} min",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Slider(
                        value = uiState.radius,
                        onValueChange = { viewModel.updateRadius(it) },
                        valueRange = 100f..2000f,
                        steps = ((2000f - 100f) / 25f).toInt() - 1,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = OutlineVariant
                        )
                    )
                    // Reference marks
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("100 m", "500 m", "1.000 m", "2.000 m").forEach { mark ->
                            Text(
                                text = mark,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // --- RULE NAME FIELD ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(1.dp, OutlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "NOMBRE DE LA REGLA",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BasicTextField(
                            value = uiState.name,
                            onValueChange = { viewModel.updateName(it) },
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box {
                                    if (uiState.name.isEmpty()) {
                                        Text(
                                            "Nombre de la automatización",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar nombre",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // --- TARGET ROOM CARD ---
            val selectedRoom = uiState.rooms.find { it.id == uiState.selectedRoomId }
            var roomDropdownExpanded by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(1.dp, OutlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Weekend,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedRoom?.name ?: "Selecciona estancia",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "20.5°C · 45% HR",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Modo Zona",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { roomDropdownExpanded = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = "Seleccionar estancia",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // Quick-apply chip
                    if (uiState.rooms.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { /* apply to all zones */ },
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Aplicar a toda la vivienda (${uiState.rooms.size} zonas)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = roomDropdownExpanded,
                        onDismissRequest = { roomDropdownExpanded = false }
                    ) {
                        uiState.rooms.forEach { room ->
                            DropdownMenuItem(
                                text = { Text(room.name) },
                                onClick = {
                                    viewModel.updateRoomId(room.id)
                                    roomDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // --- TEMPERATURE CARD ---
            val currentTemp = if (selectedTab == 0) uiState.tempOnEnter else uiState.tempOnExit
            val onTempChange: (Double) -> Unit = if (selectedTab == 0)
                viewModel::updateTempOnEnter
            else
                viewModel::updateTempOnExit

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(1.dp, OutlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TEMPERATURA OBJETIVO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Calefacción activa",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    // Large temperature display + +/- buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(SurfaceContainerHigh)
                                .clickable {
                                    val newVal = (currentTemp - 0.5).coerceAtLeast(14.0)
                                    onTempChange(newVal)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = "Bajar temperatura",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = "${String.format("%.1f", currentTemp)}°",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(SurfaceContainerHigh)
                                .clickable {
                                    val newVal = (currentTemp + 0.5).coerceAtMost(26.0)
                                    onTempChange(newVal)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Subir temperatura",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    // Temperature slider
                    Slider(
                        value = currentTemp.toFloat(),
                        onValueChange = { onTempChange(it.toDouble()) },
                        valueRange = 14f..26f,
                        steps = ((26f - 14f) / 0.5f).toInt() - 1,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.secondary,
                            activeTrackColor = MaterialTheme.colorScheme.secondary,
                            inactiveTrackColor = OutlineVariant
                        )
                    )
                    // Scale labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Eco\n14°C",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Confort\n21°C",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Max\n26°C",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    // Smart return timer chip
                    Surface(
                        color = SurfaceContainerLow,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Retorno inteligente: precalentará 30 min antes de tu llegada estimada",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // --- TOGGLE ROWS CARD ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                border = BorderStroke(1.dp, OutlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    // Automatización activa
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Automatización activa",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "La regla se ejecutará automáticamente",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAutomationActive,
                            onCheckedChange = { isAutomationActive = it },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                uncheckedTrackColor = SurfaceContainerHigh,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.dp,
                        color = SurfaceContainerHighest.copy(alpha = 0.6f)
                    )
                    // Notificaciones push
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Notificaciones push",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Recibe alertas cuando se activa la geovalla",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { notificationsEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                uncheckedTrackColor = SurfaceContainerHigh,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            }

            // --- ERROR ---
            if (uiState.error != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // --- DELETE BUTTON (existing automations only) ---
            if (automationId != -1L) {
                Button(
                    onClick = { /* TODO: show delete confirmation dialog */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Eliminar automatización",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private class MapBridge(private val onLocationChanged: (String, String) -> Unit) {
    @JavascriptInterface
    fun onLocationChanged(lat: String, lng: String) {
        onLocationChanged.invoke(lat, lng)
    }
}

private fun buildMapHtml(lat: Double, lng: Double, radiusMeters: Float): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
*{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent}
html,body{width:100%;height:100vh;overflow:hidden;background:#1a2332;touch-action:none}
#map{position:relative;width:100%;height:100%;overflow:hidden;cursor:grab}
#map:active{cursor:grabbing}
#tiles{position:absolute;top:0;left:0;width:100%;height:100%}
.tile{position:absolute;width:256px;height:256px;image-rendering:auto}
.tile img{width:256px;height:256px;display:block;border:none}
#overlay{position:absolute;top:0;left:0;width:100%;height:100%;pointer-events:none;overflow:visible}
</style>
</head>
<body>
<div id="map">
  <div id="tiles"></div>
  <svg id="overlay" xmlns="http://www.w3.org/2000/svg">
    <circle id="cir" fill="#93CCFF" fill-opacity="0.15" stroke="#93CCFF" stroke-width="2"/>
    <circle id="mkr" r="10" fill="#1A73E8" stroke="#fff" stroke-width="2" style="pointer-events:all;cursor:move"/>
  </svg>
</div>
<script>
(function(){
var T=256;
var lat=$lat,lng=$lng,radius=$radiusMeters;
var zoom=15;
var cx=0,cy=0; // canvas offset in world pixels

function toWorld(la,lo,z){
  var n=1<<z;
  var x=(lo+180)/360*n*T;
  var s=Math.sin(la*Math.PI/180);
  var y=(0.5-Math.log((1+s)/(1-s))/(4*Math.PI))*n*T;
  return{x:x,y:y};
}
function fromWorld(wx,wy,z){
  var n=1<<z;
  var lo=wx/(n*T)*360-180;
  var lat2=Math.atan(Math.sinh(Math.PI*(1-2*wy/(n*T))))*180/Math.PI;
  return{lat:lat2,lng:lo};
}
function metersToPixels(m,la,z){
  return m*Math.pow(2,z)*T/(40075016.686*Math.cos(la*Math.PI/180));
}

var mapDiv=document.getElementById('map');
var tilesDiv=document.getElementById('tiles');
var svg=document.getElementById('overlay');
var cirEl=document.getElementById('cir');
var mkrEl=document.getElementById('mkr');
var W=mapDiv.offsetWidth||window.innerWidth;
var H=mapDiv.offsetHeight||window.innerHeight;

var mla=lat,mlo=lng; // marker lat/lng

var tileCache={};

function tileKey(z,tx,ty){return z+'/'+tx+'/'+ty;}

function loadTiles(){
  var wp=toWorld(mla,mlo,zoom);
  // center of viewport in world pixels
  // cx,cy are the top-left of the viewport in world pixels
  var n=1<<zoom;
  var maxPx=n*T;
  var tx0=Math.floor(cx/T);
  var ty0=Math.floor(cy/T);
  var tx1=Math.ceil((cx+W)/T);
  var ty1=Math.ceil((cy+H)/T);

  // remove tiles outside range
  var keys={};
  for(var tx=tx0;tx<=tx1;tx++){
    for(var ty=ty0;ty<=ty1;ty++){
      var rtx=((tx%n)+n)%n;
      var rty=((ty%n)+n)%n;
      var k=tileKey(zoom,rtx,rty);
      var dk=tileKey(zoom,tx,ty);
      keys[dk]=true;
      if(!tileCache[dk]){
        (function(tx2,ty2,rtx2,rty2,dk2){
          var d=document.createElement('div');
          d.className='tile';
          d.style.left=(tx2*T-cx)+'px';
          d.style.top=(ty2*T-cy)+'px';
          var img=new Image();
          var sub=['a','b','c'][Math.abs(rtx2+rty2)%3];
          img.src='https://'+sub+'.tile.openstreetmap.org/'+zoom+'/'+rtx2+'/'+rty2+'.png';
          img.crossOrigin='anonymous';
          img.onerror=function(){img.style.opacity='0';};
          d.appendChild(img);
          tilesDiv.appendChild(d);
          tileCache[dk2]={el:d};
        })(tx,ty,rtx,rty,dk);
      } else {
        tileCache[dk].el.style.left=(tx*T-cx)+'px';
        tileCache[dk].el.style.top=(ty*T-cy)+'px';
      }
    }
  }
  // clean up old tiles
  for(var k2 in tileCache){
    if(!keys[k2]){
      if(tileCache[k2].el.parentNode) tileCache[k2].el.parentNode.removeChild(tileCache[k2].el);
      delete tileCache[k2];
    }
  }
}

function updateOverlay(){
  var wp=toWorld(mla,mlo,zoom);
  var sx=wp.x-cx;
  var sy=wp.y-cy;
  var rPx=metersToPixels(radius,mla,zoom);
  svg.setAttribute('viewBox','0 0 '+W+' '+H);
  svg.setAttribute('width',W);
  svg.setAttribute('height',H);
  cirEl.setAttribute('cx',sx);
  cirEl.setAttribute('cy',sy);
  cirEl.setAttribute('r',rPx);
  mkrEl.setAttribute('cx',sx);
  mkrEl.setAttribute('cy',sy);
}

function centerOn(la,lo){
  var wp=toWorld(la,lo,zoom);
  cx=wp.x-W/2;
  cy=wp.y-H/2;
}

function render(){
  loadTiles();
  updateOverlay();
}

// init
centerOn(mla,mlo);
render();

// resize
window.addEventListener('resize',function(){
  W=mapDiv.offsetWidth||window.innerWidth;
  H=mapDiv.offsetHeight||window.innerHeight;
  render();
});

// ---- Touch / Mouse handling ----
var drag={active:false,startX:0,startY:0,startCx:0,startCy:0};
var markerDrag={active:false};
var pinch={active:false,dist:0,startZoom:0,midX:0,midY:0};

function getMarkerScreenPos(){
  var wp=toWorld(mla,mlo,zoom);
  return{x:wp.x-cx,y:wp.y-cy};
}
function dist2D(a,b){return Math.hypot(a.x-b.x,a.y-b.y);}

// Mouse
mapDiv.addEventListener('mousedown',function(e){
  var mp=getMarkerScreenPos();
  if(dist2D({x:e.clientX,y:e.clientY},mp)<16){
    markerDrag.active=true;
  } else {
    drag.active=true;
    drag.startX=e.clientX;drag.startY=e.clientY;
    drag.startCx=cx;drag.startCy=cy;
  }
  e.preventDefault();
});
window.addEventListener('mousemove',function(e){
  if(markerDrag.active){
    var ll=fromWorld(cx+e.clientX,cy+e.clientY,zoom);
    mla=ll.lat;mlo=ll.lng;
    render();
  } else if(drag.active){
    cx=drag.startCx-(e.clientX-drag.startX);
    cy=drag.startCy-(e.clientY-drag.startY);
    render();
  }
});
window.addEventListener('mouseup',function(e){
  if(markerDrag.active){
    Android.onLocationChanged(mla.toString(),mlo.toString());
  }
  markerDrag.active=false;drag.active=false;
});
mapDiv.addEventListener('click',function(e){
  if(Math.abs(e.clientX-drag.startX)<5&&Math.abs(e.clientY-drag.startY)<5&&!markerDrag.active){
    var ll=fromWorld(cx+e.clientX,cy+e.clientY,zoom);
    mla=ll.lat;mlo=ll.lng;
    render();
    Android.onLocationChanged(mla.toString(),mlo.toString());
  }
});

// Touch
var lastTapTime=0;
mapDiv.addEventListener('touchstart',function(e){
  e.preventDefault();
  if(e.touches.length===1){
    var t=e.touches[0];
    var mp=getMarkerScreenPos();
    if(dist2D({x:t.clientX,y:t.clientY},mp)<22){
      markerDrag.active=true;
    } else {
      drag.active=true;
      drag.startX=t.clientX;drag.startY=t.clientY;
      drag.startCx=cx;drag.startCy=cy;
    }
  } else if(e.touches.length===2){
    drag.active=false;markerDrag.active=false;
    pinch.active=true;
    var dx=e.touches[0].clientX-e.touches[1].clientX;
    var dy=e.touches[0].clientY-e.touches[1].clientY;
    pinch.dist=Math.hypot(dx,dy);
    pinch.startZoom=zoom;
    pinch.midX=(e.touches[0].clientX+e.touches[1].clientX)/2;
    pinch.midY=(e.touches[0].clientY+e.touches[1].clientY)/2;
  }
},{passive:false});

mapDiv.addEventListener('touchmove',function(e){
  e.preventDefault();
  if(e.touches.length===1&&!pinch.active){
    var t=e.touches[0];
    if(markerDrag.active){
      var ll=fromWorld(cx+t.clientX,cy+t.clientY,zoom);
      mla=ll.lat;mlo=ll.lng;
      render();
    } else if(drag.active){
      cx=drag.startCx-(t.clientX-drag.startX);
      cy=drag.startCy-(t.clientY-drag.startY);
      render();
    }
  } else if(e.touches.length===2&&pinch.active){
    var dx=e.touches[0].clientX-e.touches[1].clientX;
    var dy=e.touches[0].clientY-e.touches[1].clientY;
    var newDist=Math.hypot(dx,dy);
    var ratio=newDist/pinch.dist;
    var newZoom=Math.max(2,Math.min(19,Math.round(pinch.startZoom+Math.log2(ratio))));
    if(newZoom!==zoom){
      // keep midpoint fixed
      var wMidX=cx+pinch.midX;
      var wMidY=cy+pinch.midY;
      var scale=Math.pow(2,newZoom-zoom);
      zoom=newZoom;
      tileCache={};
      while(tilesDiv.firstChild) tilesDiv.removeChild(tilesDiv.firstChild);
      cx=wMidX*scale-pinch.midX;
      cy=wMidY*scale-pinch.midY;
      render();
    }
  }
},{passive:false});

mapDiv.addEventListener('touchend',function(e){
  e.preventDefault();
  if(markerDrag.active){
    Android.onLocationChanged(mla.toString(),mlo.toString());
  } else if(drag.active&&e.changedTouches.length===1){
    var t=e.changedTouches[0];
    if(Math.abs(t.clientX-drag.startX)<8&&Math.abs(t.clientY-drag.startY)<8){
      // tap
      var now=Date.now();
      if(now-lastTapTime<350){
        // double tap = zoom in
        zoom=Math.min(19,zoom+1);
        tileCache={};
        while(tilesDiv.firstChild) tilesDiv.removeChild(tilesDiv.firstChild);
        cx=cx+t.clientX/2;cy=cy+t.clientY/2; // rough zoom-to-tap
        centerOn(mla,mlo);
        render();
        lastTapTime=0;
      } else {
        lastTapTime=now;
        var ll=fromWorld(cx+t.clientX,cy+t.clientY,zoom);
        mla=ll.lat;mlo=ll.lng;
        render();
        Android.onLocationChanged(mla.toString(),mlo.toString());
      }
    }
  }
  markerDrag.active=false;drag.active=false;pinch.active=false;
},{passive:false});

// Wheel zoom (desktop testing)
mapDiv.addEventListener('wheel',function(e){
  e.preventDefault();
  var newZoom=Math.max(2,Math.min(19,zoom+(e.deltaY<0?1:-1)));
  if(newZoom!==zoom){
    var scale=Math.pow(2,newZoom-zoom);
    cx=(cx+e.clientX)*scale-e.clientX;
    cy=(cy+e.clientY)*scale-e.clientY;
    zoom=newZoom;
    tileCache={};
    while(tilesDiv.firstChild) tilesDiv.removeChild(tilesDiv.firstChild);
    render();
  }
},{passive:false});

// Public API
window.zoomIn=function(){
  if(zoom>=19)return;
  var scale=2;
  cx=(cx+W/2)*scale-W/2;
  cy=(cy+H/2)*scale-H/2;
  zoom++;
  tileCache={};
  while(tilesDiv.firstChild) tilesDiv.removeChild(tilesDiv.firstChild);
  render();
};
window.zoomOut=function(){
  if(zoom<=2)return;
  var scale=0.5;
  cx=(cx+W/2)*scale-W/2;
  cy=(cy+H/2)*scale-H/2;
  zoom--;
  tileCache={};
  while(tilesDiv.firstChild) tilesDiv.removeChild(tilesDiv.firstChild);
  render();
};
window.updateRadius=function(r){
  radius=r;
  updateOverlay();
};

})();
</script>
</body>
</html>
""".trimIndent()
