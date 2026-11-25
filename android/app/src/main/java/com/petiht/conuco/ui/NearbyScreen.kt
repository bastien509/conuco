package com.petiht.conuco.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petiht.conuco.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---------- Nearby (real map online, offline radar without signal) ----------
/** Cerca: OpenStreetMap with outbreak circles sized by reports, a crop filter, and the list below. No signal → the radar drawn on the device. */
@Composable
fun NearbyScreen(s: AppState, a: Actions) {
    val c = cc
    val p = s.profile ?: return
    val radius = a.ai.outbreakRadiusKm
    var mine by remember { mutableStateOf(false) }
    val rows = (s.outbreaks ?: emptyList()).filter { !mine || it.crop in p.crops }
    Page {
        Text(tr("mp_title"), style = MaterialTheme.typography.headlineMedium)
        Muted(tr("mp_sub"))
        TextChips(listOf("all" to tr("map_all"), "mine" to tr("map_mine")), setOf(if (mine) "mine" else "all")) { mine = it == "mine" }
        if (s.online) {
            OutbreakMap(p.lat, p.lng, radius, rows)
            Muted(tr("map_attr"), 11)
        } else {
            Note(tr("map_offline"), icon = Icons.Outlined.WifiOff)
            Radar(p.lat, p.lng, radius, rows)
            Muted("N ↑ · ${radius.toInt()} km", 12)
        }
        val groups = Repo.alerts(rows, p.lat, p.lng, radius, 1, null)
        CCard {
            if (groups.isEmpty()) Muted(tr("mp_none"))
            groups.forEach { g -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Dot(Color(Crops.CAT_COLOR[g.category] ?: 0xFF57534E), 12)
                Column(Modifier.weight(1f)) {
                    Text(Crops.problemName(g.problemKey, s.lang), fontWeight = if (g.crop in p.crops) FontWeight.ExtraBold else FontWeight.SemiBold)
                    Muted(Crops.name(g.crop, s.lang) + " · " + tr("cat_${g.category}"), 12)
                }
                Column(horizontalAlignment = Alignment.End) { Text("${g.n}×", fontWeight = FontWeight.ExtraBold); Muted("${g.nearestKm} km", 12) }
            } }
        }
    }
}

@Composable
private fun OutbreakMap(lat: Double, lng: Double, radiusKm: Double, rows: List<Outbreak>) {
    val c = cc
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val map = remember {
        org.osmdroid.config.Configuration.getInstance().apply {
            load(ctx, ctx.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
            userAgentValue = "Conuco-Android (" + ctx.packageName + ")"
        }
        org.osmdroid.views.MapView(ctx).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(9.6)
            controller.setCenter(org.osmdroid.util.GeoPoint(lat, lng))
        }
    }
    DisposableEffect(Unit) { map.onResume(); onDispose { map.onPause(); map.onDetach() } }
    androidx.compose.ui.viewinterop.AndroidView({ map }, Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(18.dp)), update = { m ->
        m.overlays.clear()
        val home = org.osmdroid.util.GeoPoint(lat, lng)
        m.overlays += org.osmdroid.views.overlay.Polygon(m).apply {
            points = org.osmdroid.views.overlay.Polygon.pointsAsCircle(home, radiusKm * 1000)
            fillPaint.color = android.graphics.Color.argb(18, 31, 111, 63)
            outlinePaint.color = android.graphics.Color.argb(140, 31, 111, 63); outlinePaint.strokeWidth = 3f
            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 12f), 0f)
        }
        rows.groupBy { Triple(it.category, Weather.coarse(it.lat, 2), Weather.coarse(it.lng, 2)) }.forEach { (k, list) ->
            val col = (Crops.CAT_COLOR[k.first] ?: 0xFF57534E).toInt()
            m.overlays += org.osmdroid.views.overlay.Polygon(m).apply {
                points = org.osmdroid.views.overlay.Polygon.pointsAsCircle(org.osmdroid.util.GeoPoint(k.second, k.third), 1200.0 * kotlin.math.sqrt(list.size.toDouble()))
                fillPaint.color = (col and 0x00FFFFFF) or (0x66 shl 24)
                outlinePaint.color = col; outlinePaint.strokeWidth = 3f
                title = "${list.size}× " + Crops.problemName(list.first().problemKey, Strings.lang)
            }
        }
        m.overlays += org.osmdroid.views.overlay.Marker(m).apply {
            position = home; setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
            icon = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply {
                paint.color = c.green.toArgbInt(); intrinsicWidth = 36; intrinsicHeight = 36
            }
            setInfoWindow(null)
        }
        m.invalidate()
    })
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

/** Offline: the same outbreaks drawn on the device around the farm, north up. */
@Composable
private fun Radar(lat: Double, lng: Double, radius: Double, rows: List<Outbreak>) {
    val c = cc
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(c.card)) {
        val view = radius * 2.2
        val cx = size.width / 2; val cy = size.height / 2
        val scale = min(size.width, size.height) / 2 / view.toFloat()
        listOf(0.5, 1.0, 1.5, 2.0).forEach { k -> drawCircle(c.line, (radius * k * scale).toFloat(), Offset(cx, cy), style = Stroke(2f, pathEffect = if (k == 1.0) null else PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))) }
        drawCircle(c.green.copy(alpha = 0.07f), (radius * scale).toFloat(), Offset(cx, cy))
        rows.forEach { o ->
            val dy = (o.lat - lat) * 111.0
            val dx = (o.lng - lng) * 111.0 * cos(Math.toRadians(lat))
            if (kotlin.math.abs(dx) < view && kotlin.math.abs(dy) < view) {
                val col = Color(Crops.CAT_COLOR[o.category] ?: 0xFF57534E)
                val at = Offset(cx + (dx * scale).toFloat(), cy - (dy * scale).toFloat())
                drawCircle(col.copy(alpha = 0.35f), 16f, at); drawCircle(col, 16f, at, style = Stroke(3f))
            }
        }
        drawCircle(Color.White, 14f, Offset(cx, cy)); drawCircle(c.green, 10f, Offset(cx, cy))
        drawLine(c.muted, Offset(cx, 12f), Offset(cx, 40f), 3f)
    }
}
