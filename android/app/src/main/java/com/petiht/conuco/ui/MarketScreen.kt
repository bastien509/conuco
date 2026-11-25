package com.petiht.conuco.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
@Composable
fun MarketScreen(s: AppState, a: Actions) {
    val c = cc
    val p = s.profile ?: return
    val cur = Crops.currency(p.country)
    val radius = maxOf(a.ai.outbreakRadiusKm, 60.0)
    var crop by remember { mutableStateOf(p.crops.firstOrNull() ?: "plantain") }
    var unit by remember { mutableStateOf("lb") }
    var price by remember { mutableStateOf("") }
    var market by remember { mutableStateOf("") }
    Page {
        Text(tr("mk_title"), style = MaterialTheme.typography.headlineMedium)
        Muted(tr("mk_sub", "d" to a.ai.priceWindowDays, "km" to radius.toInt()))
        val rows = s.prices?.let { Repo.board(it, p.lat, p.lng, radius, cur, a.ai.priceWindowDays) }?.sortedByDescending { it.crop in p.crops }
        when {
            rows == null -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.green)
            rows.isEmpty() -> CCard { Muted(tr("mk_none")) }
            else -> rows.forEach { r -> CCard(border = if (r.crop in p.crops) c.green else null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dot(Color(Crops.get(r.crop).color)); Text(Crops.name(r.crop, s.lang), fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); Muted("/ ${tr("unit_${r.unit}")}", 13)
                }
                Text(money(r.median, cur, s.lang), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Muted("${tr("reports", "n" to r.n)} · ${money(r.min, cur, s.lang)}–${money(r.max, cur, s.lang)}", 13)
                r.trend?.let { t -> Text(if (t > 2) "▲ $t%" else if (t < -2) "▼ ${-t}%" else tr("same"), color = if (t > 2) c.green else if (t < -2) c.red else c.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                if (r.bestMarket != null && r.n > 1) Text(tr("best", "m" to "${r.bestMarket} (${money(r.bestPrice ?: 0.0, cur, s.lang)})"), color = c.green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            } }
        }
        CCard {
            Title(tr("report_price"), Icons.Outlined.Sell)
            CropChips((p.crops + listOf("plantain", "beans", "cacao", "coffee", "cassava", "avocado", "tomato")).distinct().take(10), setOf(crop), s.lang) { crop = it }
            TextChips(Crops.PRICE_UNITS.map { it to tr("unit_$it") }, setOf(unit)) { unit = it }
            OutlinedTextField(price, { price = it.filter { ch -> ch.isDigit() || ch == '.' } }, Modifier.fillMaxWidth(), label = { Text("${tr("price")} ($cur)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(market, { market = it.take(40) }, Modifier.fillMaxWidth(), label = { Text(tr("market_name")) }, singleLine = true)
            BigButton(tr("share"), Icons.Outlined.Send, enabled = (price.toDoubleOrNull() ?: 0.0) > 0) { a.addPrice(crop, unit, price.toDouble(), market); price = ""; market = "" }
        }
    }
}
