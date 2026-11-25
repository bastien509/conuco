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

// ---------- Today ----------
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
            IconButton(onClick = { a.refreshWeather(true) }) { Icon(Icons.Outlined.Refresh, null, tint = c.green) }
        }
        val alerts = s.outbreaks?.let { Repo.alerts(it, p.lat, p.lng, a.ai.outbreakRadiusKm, a.ai.outbreakAlertMin, p.crops) } ?: emptyList()
        if (alerts.isNotEmpty()) CCard(border = c.red, bg = c.redSoft) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Outlined.Warning, null, tint = c.red); Text(tr("alerts"), color = c.red, fontWeight = FontWeight.ExtraBold) }
            alerts.take(3).forEach { al -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dot(Color(Crops.CAT_COLOR[al.category] ?: 0xFF57534E)); Text(tr("alert_line", "p" to Crops.problemName(al.problemKey, s.lang), "c" to Crops.name(al.crop, s.lang), "n" to al.n, "km" to a.ai.outbreakRadiusKm.toInt()), fontSize = 14.sp) } }
        }
        val an = s.analysis
        if (an == null) CCard { Muted(tr("wx_none")) } else {
            CCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(an.now?.let { "${it.temp.toInt()}°C" } ?: "–", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Muted("${tr("humidity")} ${an.now?.rh ?: "–"}%"); Muted("${tr("wind")} ${an.now?.wind ?: "–"} km/h"); Muted("${tr("rain")} ${an.now?.prob ?: "–"}%")
                    }
                }
                Muted("${p.place} · " + if (s.wxFrom == "offline-cache") tr("cached") else tr("updated", "ago" to ago(an.fetchedAt, s.lang)), 12)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    an.days.forEach { d -> Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.surface).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(LocalDate.parse(d.date).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.forLanguageTag(s.lang)), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(when (Weather.sky(d.code)) { "clear" -> "☀"; "rain" -> "🌧"; "storm" -> "⛈"; "fog" -> "🌫"; else -> "⛅" }, fontSize = 18.sp)
                        Text("${d.tmax.toInt()}° ${d.tmin.toInt()}°", fontSize = 12.sp)
                        Text("${d.rain.toInt()} mm", fontSize = 11.sp, color = c.blue, fontWeight = FontWeight.Bold)
                    } }
                }
            }
            val w = an.windows.firstOrNull()
            Decision(Icons.Outlined.Sanitizer, tr("spray"), if (an.okNow) tr("spray_ok") else if (w != null) tr("spray_next", "when" to windowText(w, s.lang), "h" to w.hours) else tr("spray_none"),
                an.blockers.joinToString(" · ") { tr("b_${it.code}", "v" to it.value) }, if (an.okNow) "good" else if (w != null) "mid" else "bad", Modifier.fillMaxWidth())
            Decision(Icons.Outlined.WaterDrop, tr("water"), tr(an.irrigation), tr("water_d", "u" to an.cropUse, "r" to an.rainEff),
                when (an.irrigation) { "skip" -> "good"; "light" -> "mid"; else -> "bad" }, Modifier.fillMaxWidth())
            Decision(Icons.Outlined.Coronavirus, tr("fungus"), tr(an.disease), tr("fungus_d", "h" to an.wetHours),
                when (an.disease) { "low" -> "good"; "medium" -> "mid"; else -> "bad" }, Modifier.fillMaxWidth())
            an.heatMax?.let { Note(tr("heat", "t" to it), warn = true, icon = Icons.Outlined.Thermostat) }
            an.stormDate?.let { Note(tr("storm", "mm" to an.stormMm, "d" to it), warn = true, icon = Icons.Outlined.Thunderstorm) }
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
        CCard {
            Title(tr("tasks"), Icons.Outlined.TaskAlt)
            val list = s.tasks.filter { it.day == Repo.today() || !it.done }.sortedBy { it.done }
            if (list.isEmpty()) Muted(tr("no_tasks"))
            list.forEach { t -> Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(t.done, { a.setTaskDone(t.id, it) })
                Text(t.text, color = if (t.done) c.muted else c.ink, modifier = Modifier.weight(1f))
                if (t.from == "weather") Icon(Icons.Outlined.Cloud, null, tint = c.muted, modifier = Modifier.size(16.dp))
            } }
        }
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
