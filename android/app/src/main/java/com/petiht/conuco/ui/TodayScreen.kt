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

// ---------- Today ----------
/** Hoy: one big decision first (spray or not), three risk tiles, alerts in one tappable line, then weather, brief and tasks. */
@Composable
fun TodayScreen(s: AppState, a: Actions) {
    val c = cc
    val p = s.profile ?: return
    val scope = rememberCoroutineScope()
    var brief by remember { mutableStateOf<Ai.Brief?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val hr = LocalDateTime.now().hour
    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("hello", "part" to tr(if (hr < 12) "morning" else if (hr < 19) "afternoon" else "evening")) + if (p.name.isNotBlank()) ", ${p.name}" else "",
                style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { a.refreshWeather(true) }) { Icon(Icons.Outlined.Refresh, tr("refresh"), tint = c.green) }
        }
        val alerts = s.outbreaks?.let { Repo.alerts(it, p.lat, p.lng, a.ai.outbreakRadiusKm, a.ai.outbreakAlertMin, p.crops) } ?: emptyList()
        val an = s.analysis
        if (an == null) CCard { Muted(tr("wx_none")) } else {
            // 1. The decision
            val w = an.windows.firstOrNull()
            val tone = if (an.okNow) c.green else if (w != null) c.green else c.amber
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(tone).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Sanitizer, null, tint = Color.White.copy(alpha = .85f), modifier = Modifier.size(16.dp))
                    Text(tr("today_title"), color = Color.White.copy(alpha = .85f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(if (an.okNow) tr("d_spray_now") else if (w != null) tr("d_spray_at", "w" to windowText(w, s.lang)) else tr("d_no_spray"),
                    color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)
                val why = an.blockers.joinToString(" · ") { tr("b_${it.code}", "v" to it.value) }
                Text(if (an.okNow || (w != null && why.isBlank())) tr("d_why_ok") else if (w != null) tr("d_not_now", "b" to why) else why.ifBlank { tr("spray_none") },
                    color = Color.White.copy(alpha = .9f), fontSize = 14.sp)
            }
            // 2. Three risk tiles
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                RiskTile(Icons.Outlined.WaterDrop, tr("tile_water"), tr("w_${an.irrigation}"), when (an.irrigation) { "skip" -> c.green; "light" -> c.amber; else -> c.red }, Modifier.weight(1f))
                RiskTile(Icons.Outlined.Coronavirus, tr("tile_fungus"), tr(an.disease), when (an.disease) { "low" -> c.green; "medium" -> c.amber; else -> c.red }, Modifier.weight(1f))
                RiskTile(Icons.Outlined.Radar, tr("tile_near"), if (alerts.isEmpty()) tr("near_none") else tr("near_n", "n" to alerts.size), if (alerts.isEmpty()) c.green else c.red, Modifier.weight(1f)) { s.tab = "map" }
            }
            Muted(tr("water_d", "u" to an.cropUse, "r" to an.rainEff) + " · " + tr("fungus_d", "h" to an.wetHours), 12)
        }
        // 3. Alerts in one line
        alerts.firstOrNull()?.let { top ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.redSoft).clickable { s.tab = "map" }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Warning, null, tint = c.red, modifier = Modifier.size(20.dp))
                Text(tr("alerts_line", "n" to alerts.size, "km" to a.ai.outbreakRadiusKm.toInt(), "top" to Crops.problemName(top.problemKey, s.lang), "c" to top.n),
                    color = c.red, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = c.red)
            }
        }
        // 4. Weather, compact
        if (an != null) CCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(an.now?.let { "${it.temp.toInt()}°" } ?: "–", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Column(Modifier.weight(1f)) {
                    Text(p.place.substringBefore(','), fontWeight = FontWeight.Bold, maxLines = 1)
                    Muted("${tr("humidity")} ${an.now?.rh ?: "–"}% · ${tr("wind")} ${an.now?.wind ?: "–"} km/h", 12)
                    Muted(if (s.wxFrom == "offline-cache") tr("cached") else tr("updated", "ago" to ago(an.fetchedAt, s.lang)), 11)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                an.days.forEach { d -> Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.surface).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(LocalDate.parse(d.date).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.forLanguageTag(s.lang)), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(when (Weather.sky(d.code)) { "clear" -> "☀"; "rain" -> "🌧"; "storm" -> "⛈"; "fog" -> "🌫"; else -> "⛅" }, fontSize = 16.sp)
                    Text("${d.tmax.toInt()}°", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (d.rain >= 1) Text("${d.rain.toInt()} mm", fontSize = 10.sp, color = c.blue, fontWeight = FontWeight.Bold)
                } }
            }
            an.heatMax?.let { Note(tr("heat", "t" to it), warn = true, icon = Icons.Outlined.Thermostat) }
            an.stormDate?.let { Note(tr("storm", "mm" to an.stormMm, "d" to it), warn = true, icon = Icons.Outlined.Thunderstorm) }
        }
        // 5. AI brief
        if (an != null) {
            val b = brief
            if (b == null) BigButton(if (busy) tr("thinking") else tr("brief"), Icons.Outlined.AutoAwesome, enabled = !busy) {
                busy = true; err = ""
                scope.launch { try { brief = a.brief() } catch (e: Throwable) { err = tr("err", "m" to (e.message ?: "")) }; busy = false }
            } else CCard(border = c.green, bg = c.greenSoft) {
                Row(verticalAlignment = Alignment.CenterVertically) { SourceBadge(b.source, b.ms, text = true); Spacer(Modifier.weight(1f)); TextButton(onClick = { a.speak(b.headline + ". " + b.steps.joinToString(". ")) }) { Icon(Icons.Outlined.VolumeUp, null); Text(tr("listen")) } }
                Text(b.headline, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                b.steps.forEachIndexed { i, st -> Text("${i + 1}. $st", fontWeight = FontWeight.SemiBold) }
                TraceBox(b.trace)
            }
            if (err.isNotBlank()) Text(err, color = c.red)
        }
        // 6. Tasks
        CCard {
            Title(tr("tasks"), Icons.Outlined.TaskAlt)
            val list = s.tasks.filter { it.day == Repo.today() || !it.done }.sortedBy { it.done }
            if (list.isEmpty()) Muted(tr("no_tasks"))
            list.forEach { t -> Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(t.done, { a.setTaskDone(t.id, it) }, colors = CheckboxDefaults.colors(checkedColor = c.green))
                Text(t.text, color = if (t.done) c.muted else c.ink, modifier = Modifier.weight(1f))
                if (t.from == "weather") Icon(Icons.Outlined.Cloud, null, tint = c.muted, modifier = Modifier.size(16.dp))
                if (t.from == "doctor") Icon(Icons.Outlined.Spa, null, tint = c.muted, modifier = Modifier.size(16.dp))
            } }
        }
    }
}

@Composable
private fun RiskTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, tone: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = cc
    Column(modifier.fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(c.card).border(1.5.dp, if (tone == c.green) c.line else tone, RoundedCornerShape(16.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, null, tint = tone, modifier = Modifier.size(22.dp))
        Text(title, color = c.muted, fontSize = 12.sp, maxLines = 1)
        Text(value, color = if (tone == c.green) c.ink else tone, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1)
    }
}

fun windowText(w: Rules.Window, lang: String): String {
    val st = LocalDateTime.parse(w.start)
    val en = LocalDateTime.parse(w.end).plusHours(1)
    return st.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.forLanguageTag(lang)) + " %02d:00–%02d:00".format(st.hour, en.hour)
}
fun ago(ms: Long, lang: String): String {
    val m = ((System.currentTimeMillis() - ms) / 60000).toInt()
    return when {
        m < 2 -> if (lang == "es") "de ahora" else "just now"
        m < 60 -> if (lang == "es") "de hace $m min" else "from $m min ago"
        else -> if (lang == "es") "de hace ${m / 60} h" else "from ${m / 60} h ago"
    }
}
