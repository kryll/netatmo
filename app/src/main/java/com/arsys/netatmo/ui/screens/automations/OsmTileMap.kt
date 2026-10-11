package com.arsys.netatmo.ui.screens.automations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arsys.netatmo.BuildConfig
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh

private const val MIN_ZOOM = 3f
private const val MAX_ZOOM = 19f
private const val EARTH_CIRCUMFERENCE_M = 40_075_016.686
// OSM tile policy requires an identifying User-Agent; generic/browser UAs get blocked.
private val TILE_USER_AGENT =
    "NetatmoSmart/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.APPLICATION_ID})"

/** Web Mercator in normalized world coordinates: x and y both in [0, 1]. */
internal object WebMercator {
    fun x(lng: Double): Double = (lng + 180.0) / 360.0
    fun y(lat: Double): Double {
        val s = sin(Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878)))
        return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
    }
    fun lng(x: Double): Double = x * 360.0 - 180.0
    fun lat(y: Double): Double = Math.toDegrees(atan(sinh(PI * (1 - 2 * y))))
}

@Stable
class TileMapState internal constructor(
    lat: Double,
    lng: Double,
    zoom: Float,
    private val tileSizePx: Float
) {
    var centerX by mutableDoubleStateOf(WebMercator.x(lng))
        private set
    var centerY by mutableDoubleStateOf(WebMercator.y(lat))
        private set
    var zoom by mutableFloatStateOf(zoom)
        private set
    internal var viewSize by mutableStateOf(IntSize.Zero)

    internal fun worldPx(): Double = tileSizePx * 2.0.pow(zoom.toDouble())

    fun centerOn(lat: Double, lng: Double) {
        centerX = WebMercator.x(lng)
        centerY = WebMercator.y(lat)
    }

    fun zoomBy(delta: Float, focus: Offset? = null) {
        val f = focus ?: Offset(viewSize.width / 2f, viewSize.height / 2f)
        val (wx, wy) = screenToWorld(f)
        zoom = (zoom + delta).coerceIn(MIN_ZOOM, MAX_ZOOM)
        placeWorldAt(wx, wy, f)
    }

    fun isComfortablyVisible(lat: Double, lng: Double): Boolean {
        if (viewSize.width == 0 || viewSize.height == 0) return false
        val p = worldToScreen(WebMercator.x(lng), WebMercator.y(lat))
        return p.x in viewSize.width * 0.15f..viewSize.width * 0.85f &&
            p.y in viewSize.height * 0.15f..viewSize.height * 0.85f
    }

    internal fun applyGesture(centroid: Offset, pan: Offset, zoomChange: Float) {
        val (wx, wy) = screenToWorld(centroid - pan)
        zoom = (zoom + log2(zoomChange)).coerceIn(MIN_ZOOM, MAX_ZOOM)
        placeWorldAt(wx, wy, centroid)
    }

    internal fun screenToWorld(p: Offset): Pair<Double, Double> {
        val wp = worldPx()
        return Pair(
            centerX + (p.x - viewSize.width / 2.0) / wp,
            centerY + (p.y - viewSize.height / 2.0) / wp
        )
    }

    internal fun worldToScreen(wx: Double, wy: Double): Offset {
        val wp = worldPx()
        return Offset(
            ((wx - centerX) * wp + viewSize.width / 2.0).toFloat(),
            ((wy - centerY) * wp + viewSize.height / 2.0).toFloat()
        )
    }

    private fun placeWorldAt(wx: Double, wy: Double, screen: Offset) {
        val wp = worldPx()
        centerX = (wx - (screen.x - viewSize.width / 2.0) / wp).coerceIn(0.0, 1.0)
        centerY = (wy - (screen.y - viewSize.height / 2.0) / wp).coerceIn(0.0, 1.0)
    }
}

@Composable
fun rememberTileMapState(lat: Double, lng: Double, zoom: Float = 15f): TileMapState {
    val tileSizePx = with(LocalDensity.current) { 256.dp.toPx() }
    return remember { TileMapState(lat, lng, zoom, tileSizePx) }
}

/**
 * Native OpenStreetMap tile map: tiles via Coil, geofence circle and marker via Canvas.
 * Pan / pinch to move, tap to place the marker, double tap to zoom in.
 */
@Composable
fun OsmTileMap(
    state: TileMapState,
    markerLat: Double,
    markerLng: Double,
    radiusMeters: Float,
    onLocationPicked: (lat: Double, lng: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnPicked by rememberUpdatedState(onLocationPicked)
    var anyTileLoaded by remember { mutableStateOf(false) }
    var tileError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color(0xFF1C2229))
            .onSizeChanged { state.viewSize = it }
            .pointerInput(state) {
                detectTransformGestures { centroid, pan, zoomChange, _ ->
                    state.applyGesture(centroid, pan, zoomChange)
                }
            }
            .pointerInput(state) {
                detectTapGestures(
                    onDoubleTap = { state.zoomBy(1f, it) },
                    onTap = {
                        val (wx, wy) = state.screenToWorld(it)
                        currentOnPicked(WebMercator.lat(wy), WebMercator.lng(wx))
                    }
                )
            }
    ) {
        val size = state.viewSize
        if (size.width > 0 && size.height > 0) {
            val z = state.zoom.roundToInt().coerceIn(MIN_ZOOM.toInt(), MAX_ZOOM.toInt())
            val n = 1 shl z
            val worldPx = state.worldPx()
            val tilePx = worldPx / n
            val left = state.centerX * worldPx - size.width / 2.0
            val top = state.centerY * worldPx - size.height / 2.0
            val tx0 = floor(left / tilePx).toInt().coerceIn(0, n - 1)
            val tx1 = floor((left + size.width) / tilePx).toInt().coerceIn(0, n - 1)
            val ty0 = floor(top / tilePx).toInt().coerceIn(0, n - 1)
            val ty1 = floor((top + size.height) / tilePx).toInt().coerceIn(0, n - 1)

            for (ty in ty0..ty1) {
                for (tx in tx0..tx1) {
                    key(z, tx, ty) {
                        val l = floor(tx * tilePx - left).toInt()
                        val t = floor(ty * tilePx - top).toInt()
                        val w = floor((tx + 1) * tilePx - left).toInt() - l
                        val h = floor((ty + 1) * tilePx - top).toInt() - t
                        val request = remember(z, tx, ty) {
                            ImageRequest.Builder(context)
                                .data("https://tile.openstreetmap.org/$z/$tx/$ty.png")
                                .addHeader("User-Agent", TILE_USER_AGENT)
                                .build()
                        }
                        AsyncImage(
                            model = request,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            onSuccess = { anyTileLoaded = true },
                            onError = {
                                val e = it.result.throwable
                                tileError = e.message ?: e.javaClass.simpleName
                            },
                            modifier = Modifier.placeAt(l, t, w, h)
                        )
                    }
                }
            }
        }

        Canvas(modifier = Modifier.matchParentSize()) {
            if (state.viewSize.width == 0) return@Canvas
            val p = state.worldToScreen(WebMercator.x(markerLng), WebMercator.y(markerLat))
            val metersPerPx = EARTH_CIRCUMFERENCE_M * cos(Math.toRadians(markerLat)) / state.worldPx()
            val r = (radiusMeters / metersPerPx).toFloat()
            drawCircle(Color(0xFF93CCFF).copy(alpha = 0.18f), r, p)
            drawCircle(Color(0xFF93CCFF), r, p, style = Stroke(2.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = 0.35f), 11.dp.toPx(), p + Offset(0f, 1.5.dp.toPx()))
            drawCircle(Color.White, 10.dp.toPx(), p)
            drawCircle(Color(0xFF1A73E8), 7.dp.toPx(), p)
        }

        if (!anyTileLoaded) {
            Text(
                text = tileError?.let { "No se pudo cargar el mapa\n$it" } ?: "Cargando mapa…",
                color = Color(0xFFC2C7CF),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 56.dp, end = 56.dp)
            )
        }

        Text(
            text = "© OpenStreetMap",
            color = Color(0xFF333333),
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.White.copy(alpha = 0.7f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

private fun Modifier.placeAt(x: Int, y: Int, w: Int, h: Int): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0)))
    layout(0, 0) { placeable.place(x, y) }
}
