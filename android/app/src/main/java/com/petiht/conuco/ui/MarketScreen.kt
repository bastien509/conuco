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

// ---------- Market ----------
/** Mercado: crop filter, big median with trend and a 14-day sparkline, best buyer in sun yellow, report form behind one button. */
@Composable
fun MarketScreen(s: AppState, a: Actions) {
    val c = cc
    val p = s.profile ?: return
    val cur = Crops.currency(p.country)
    val radius = maxOf(a.ai.outbreakRadiusKm, 60.0)
    var showForm by remember { mutableStateOf(false) }
    val rows = s.prices?.let { Repo.board(it, p.lat, p.lng, radius, cur, a.ai.priceWindowDays) }?.sortedByDescending { it.crop in p.crops }
    var pick by remember { mutableStateOf<String?>(null) }
    Page {
        Text(tr("mk_title"), style = MaterialTheme.typography.headlineMedium)
        when {
            rows == null -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.green)
            rows.isEmpty() -> CCard { Muted(tr("mk_none")) }
            else -> {
                val crops = rows.map { it.crop }.distinct()
                val sel = pick?.takeIf { it in crops } ?: crops.first()
                CropChips(crops, setOf(sel), s.lang) { pick = it }
                rows.filter { it.crop == sel }.forEach { r ->
                    CCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Muted(tr("mk_median", "km" to radius.toInt(), "d" to a.ai.priceWindowDays), 12)
                            Spacer(Modifier.weight(1f)); Muted("/ ${tr("unit_${r.unit}")}", 12)
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(money(r.median, cur, s.lang), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                            r.trend?.let { t -> Text(if (t > 2) "▲ $t%" else if (t < -2) "▼ ${-t}%" else tr("same"), color = if (t > 2) c.green else if (t < -2) c.red else c.muted, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
                        }
                        Sparkline(dailyMedians(s.prices ?: emptyList(), r.crop, r.unit, cur, p.lat, p.lng, radius), if ((r.trend ?: 0) < -2) c.red else c.green)
                        Muted("${tr("reports", "n" to r.n)} · ${money(r.min, cur, s.lang)}–${money(r.max, cur, s.lang)}", 12)
                    }
                    if (r.bestMarket != null && r.n > 1) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.sunSoft).border(1.5.dp, c.sun, RoundedCornerShape(16.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.EmojiEvents, null, tint = c.amber)
                        Column(Modifier.weight(1f)) { Muted(tr("mk_best_title"), 12); Text(r.bestMarket, fontWeight = FontWeight.ExtraBold) }
                        Text(money(r.bestPrice ?: 0.0, cur, s.lang), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                }
                Muted(tr("mk_sub", "d" to a.ai.priceWindowDays, "km" to radius.toInt()), 12)
                // Other crops, compact
                rows.filter { it.crop != sel }.forEach { r ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card).clickable { pick = r.crop }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Dot(Color(Crops.get(r.crop).color)); Text(Crops.name(r.crop, s.lang), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(money(r.median, cur, s.lang), fontWeight = FontWeight.Bold); Muted("/ ${tr("unit_${r.unit}")}", 12)
                    }
                }
            }
        }
        if (showForm) PriceForm(s, a, cur) { showForm = false }
        else BigButton(tr("report_price"), Icons.Outlined.Sell) { showForm = true }
    }
}

/** Median price per day for one crop and unit near the farmer, oldest first (input for the sparkline). */
fun dailyMedians(rows: List<PriceRow>, crop: String, unit: String, cur: String, lat: Double, lng: Double, radiusKm: Double): List<Double> =
    rows.filter { it.crop == crop && it.unit == unit && it.currency == cur && Repo.km(lat to lng, Repo.cellCenter(it.cell)) <= radiusKm }
        .groupBy { it.day }.toSortedMap().values.map { v -> v.map { it.price }.sorted().let { l -> if (l.size % 2 == 1) l[l.size / 2] else (l[l.size / 2 - 1] + l[l.size / 2]) / 2 } }
        .takeLast(14)

@Composable
private fun Sparkline(points: List<Double>, color: Color) {
    if (points.size < 2) return
    val lo = points.min(); val hi = points.max(); val span = (hi - lo).takeIf { it > 0 } ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val dx = size.width / (points.size - 1)
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { i, v ->
            val x = i * dx; val y = size.height - ((v - lo) / span * (size.height - 8f) + 4f).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        val lastY = size.height - ((points.last() - lo) / span * (size.height - 8f) + 4f).toFloat()
        drawCircle(color, 4.dp.toPx(), Offset(size.width, lastY))
    }
}

@Composable
private fun PriceForm(s: AppState, a: Actions, cur: String, onDone: () -> Unit) {
    val p = s.profile ?: return
    var crop by remember { mutableStateOf(p.crops.firstOrNull() ?: "plantain") }
    var unit by remember { mutableStateOf("lb") }
    var price by remember { mutableStateOf("") }
    var market by remember { mutableStateOf("") }
    CCard {
        Row(verticalAlignment = Alignment.CenterVertically) { Title(tr("report_price"), Icons.Outlined.Sell); Spacer(Modifier.weight(1f)); IconButton(onClick = onDone) { Icon(Icons.Outlined.Close, tr("cancel")) } }
        CropChips((p.crops + listOf("plantain", "beans", "cacao", "coffee", "cassava", "avocado", "tomato")).distinct().take(10), setOf(crop), s.lang) { crop = it }
        TextChips(Crops.PRICE_UNITS.map { it to tr("unit_$it") }, setOf(unit)) { unit = it }
        OutlinedTextField(price, { price = it.filter { ch -> ch.isDigit() || ch == '.' } }, Modifier.fillMaxWidth(), label = { Text("${tr("price")} ($cur)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(market, { market = it.take(40) }, Modifier.fillMaxWidth(), label = { Text(tr("market_name")) }, singleLine = true)
        BigButton(tr("share"), Icons.Outlined.Send, enabled = (price.toDoubleOrNull() ?: 0.0) > 0) { a.addPrice(crop, unit, price.toDouble(), market); onDone() }
    }
}
