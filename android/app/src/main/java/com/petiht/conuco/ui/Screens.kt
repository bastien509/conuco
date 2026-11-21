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

fun money(n: Double, cur: String, lang: String): String = runCatching {
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag(if (lang == "es") "es-DO" else "en-US")).apply {
        currency = Currency.getInstance(cur); maximumFractionDigits = if (n >= 100) 0 else 2
    }.format(n)
}.getOrDefault("$n $cur")

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)

// ---------- Auth ----------
@Composable
fun AuthScreen(s: AppState, a: Actions) {
    val c = cc
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(false) }
    Page {
        Spacer(Modifier.height(24.dp))
        LangSwitch(s)
        Text(tr("w_title"), style = MaterialTheme.typography.headlineMedium)
        Text(tr("w_sub"), color = c.muted, fontSize = 16.sp)
        BigButton(tr("w_start"), Icons.Outlined.Agriculture) { a.signInAnon() }
        OutlinedButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text(tr("w_signin"), fontWeight = FontWeight.Bold) }
        if (open) CCard {
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(tr("email")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text(tr("password")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { a.signInEmail(email.trim(), pass, false) }) { Text(tr("w_signin")) }
                TextButton(onClick = { a.signInEmail(email.trim(), pass, true) }) { Text(tr("w_create")) }
            }
        }
        Muted(tr("w_privacy"), 13)
    }
}

@Composable
fun LangSwitch(s: AppState) {
    val c = cc
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(c.surface)) {
        listOf("es" to "Español", "en" to "English").forEach { (k, label) ->
            val on = s.lang == k
            Text(label, Modifier.clickable { s.lang = k; Strings.lang = k }.background(if (on) c.green else Color.Transparent).padding(horizontal = 16.dp, vertical = 10.dp),
                color = if (on) c.onGreen else c.ink, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------- Onboarding ----------
@Composable
fun OnboardingScreen(s: AppState, a: Actions) {
    val c = cc
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<Weather.Place>()) }
    var place by remember { mutableStateOf<Weather.Place?>(null) }
    var crops by remember { mutableStateOf(setOf<String>()) }
    var name by remember { mutableStateOf("") }
    var sample by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf("") }
    Page {
        Text(tr("o_title"), style = MaterialTheme.typography.headlineMedium)
        LangSwitch(s)
        Title(tr("o_place"), Icons.Outlined.Place)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(q, { q = it; if (it.length >= 2) scope.launch { results = a.searchPlace(it) } }, Modifier.weight(1f), label = { Text(tr("o_search")) }, singleLine = true)
            IconButton(onClick = { a.useGps { p -> if (p != null) { place = p; results = emptyList() } } }) { Icon(Icons.Outlined.MyLocation, tr("o_gps"), tint = c.green) }
        }
        results.forEach { p -> Text("${p.name}, ${p.admin} · ${p.country}", Modifier.fillMaxWidth().clickable { place = p; results = emptyList(); q = "" }.padding(10.dp)) }
        place?.let { Text(tr("o_found", "p" to "${it.name}, ${it.admin}"), color = c.green, fontWeight = FontWeight.Bold) }
        Title(tr("o_crops"), Icons.Outlined.Grass)
        CropChips(Crops.ALL.map { it.key }, crops, s.lang) { k -> crops = if (k in crops) crops - k else crops + k }
        OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text(tr("o_name")) }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(sample, { sample = it }); Text(tr("o_sample")) }
        if (err.isNotBlank()) Text(err, color = c.red)
        BigButton(tr("o_done")) {
            val p = place
            if (p == null || crops.isEmpty()) { err = tr("o_need"); return@BigButton }
            a.finishOnboarding(Profile(name, s.lang, p.country, "${p.name}, ${p.admin}", p.lat, p.lng, crops.toList(), if (p.country == "DO") "tarea" else "ha", true), sample)
        }
    }
}

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

// ---------- Doctor ----------
@Composable
fun DoctorScreen(s: AppState, a: Actions) {
    val c = cc
    val p = s.profile ?: return
    val scope = rememberCoroutineScope()
    var crop by remember { mutableStateOf((p.crops + "other").first()) }
    val options = (listOf(crop) + p.crops + "other").distinct()
    var note by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("smart") }
    var busy by remember { mutableStateOf(false) }
    var res by remember { mutableStateOf<Ai.DoctorResult?>(null) }
    Page {
        Text(tr("d_title"), style = MaterialTheme.typography.headlineMedium)
        Muted(tr("d_sub"))
        CropChips(options, setOf(crop), s.lang) { crop = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(tr("camera"), Icons.Outlined.PhotoCamera, modifier = Modifier.weight(1f)) { res = null; a.openCamera() }
            OutlinedButton(onClick = { res = null; a.pickPhoto() }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(tr("gallery")) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Muted(tr("samples"), 13)
            listOf(Triple("sigatoka.jpg", "banana", if (s.lang == "es") "Guineo" else "Banana"), Triple("coffee-rust.jpg", "coffee", if (s.lang == "es") "Café" else "Coffee")).forEach { (f, cr, label) ->
                AssistChip(onClick = { res = null; crop = cr; a.loadSample(f) }, label = { Text(label) })
            }
        }
        s.photo?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit) }
        OutlinedTextField(note, { note = it.take(300) }, Modifier.fillMaxWidth(), label = { Text(tr("d_note")) },
            trailingIcon = { IconButton(onClick = { a.dictate { t -> note = (note + " " + t).trim() } }) { Icon(Icons.Outlined.Mic, tr("dictate"), tint = c.green) } })
        Title(tr("privacy"), Icons.Outlined.Shield)
        listOf("private", "smart", "cloud").forEach { m -> Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (mode == m) c.greenSoft else c.card).clickable { mode = m }.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { RadioButton(mode == m, { mode = m }); Column { Text(tr("m_$m"), fontWeight = FontWeight.Bold); Muted(tr("m_${m}_d"), 13) } } }
        BigButton(if (busy) tr("d_busy") else tr("diagnose"), Icons.Outlined.AutoAwesome, enabled = s.photo != null && !busy) {
            busy = true
            scope.launch { res = try { a.diagnose(crop, note, mode) } catch (e: Throwable) { s.toast = tr("err", "m" to (e.message ?: "")); null }; busy = false }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.green)
        res?.let { r ->
            r.first?.let { DiagCard(it, false, s, a) }
            r.second?.let { DiagCard(it, true, s, a) }
            if (r.first != null && r.second != null) Note(if (a.ai.agree(r.first, r.second)) tr("agree") else tr("disagree"), warn = !a.ai.agree(r.first, r.second))
            (r.second ?: r.first)?.takeIf { it.isPlant }?.let { best -> DiagActions(crop, best, r, s, a) }
            if (r.symptoms) SymptomCard(crop, s)
            TraceBox(r.trace)
        }
        CCard {
            Title(tr("history"), Icons.Outlined.History)
            s.diags.forEach { d -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dot(Color(Crops.CAT_COLOR[d.category] ?: 0xFF57534E))
                Text("${d.problem} · ${Crops.name(d.crop, s.lang)}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Muted("${(d.confidence * 100).toInt()}% ${if (d.source == "on_device") "⚙" else "☁"}", 12)
            } }
        }
    }
}

@Composable
private fun DiagCard(d: Ai.Diagnosis, second: Boolean, s: AppState, a: Actions) {
    val c = cc
    CCard(border = if (second) c.cloud else c.line) {
        Row(verticalAlignment = Alignment.CenterVertically) { SourceBadge(d.source, d.ms); Spacer(Modifier.weight(1f)); if (second) Muted(tr("second"), 12) }
        if (!d.isPlant) { Text(tr("not_plant")); return@CCard }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(Color(Crops.CAT_COLOR[d.category] ?: 0xFF57534E), 14); Text(d.problem.ifBlank { tr("cat_${d.category}") }, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(tr("cat_${d.category}"), c.ink, c.surface)
            if (!d.healthy) Pill(tr("sev_${d.severity}"), when (d.severity) { "high" -> c.red; "low" -> c.green; else -> c.amber }, when (d.severity) { "high" -> c.redSoft; "low" -> c.greenSoft; else -> c.sunSoft })
        }
        Text("${tr("confidence")} ${(d.confidence * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        LinearProgressIndicator(progress = { d.confidence.toFloat() }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)), color = c.green, trackColor = c.surface)
        if (d.signs.isNotEmpty()) { Text(tr("signs").uppercase(), color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold); d.signs.forEach { Text("• $it") } }
        if (d.actions.isNotEmpty()) { Text(tr("do_now").uppercase(), color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold); d.actions.forEachIndexed { i, x -> Text("${i + 1}. $x", fontWeight = FontWeight.SemiBold) } }
        if (d.prevent.isNotEmpty()) { Text(tr("prevent").uppercase(), color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold); d.prevent.forEach { Text("• $it") } }
        if (d.seeExpert) Note(tr("expert"), warn = true, icon = Icons.Outlined.Groups)
        if (d.filtered) Note(tr("filtered"), icon = Icons.Outlined.Shield)
        TextButton(onClick = { a.speak(d.problem + ". " + d.actions.joinToString(". ")) }) { Icon(Icons.Outlined.VolumeUp, null); Text(tr("listen")) }
    }
}

@Composable
private fun DiagActions(crop: String, d: Ai.Diagnosis, r: Ai.DoctorResult, s: AppState, a: Actions) {
    val c = cc
    var saved by remember(d) { mutableStateOf(false) }
    var tasked by remember(d) { mutableStateOf(false) }
    var warned by remember(d) { mutableStateOf(false) }
    CCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (!saved) { saved = true; a.saveDiagnosis(crop, d, if (r.second == null) "none" else if (r.first == null) "cloud_only" else if (a.ai.agree(r.first, r.second)) "agree" else "disagree") } }, enabled = !saved, modifier = Modifier.weight(1f)) {
                Text(if (saved) tr("saved") else tr("save_log")) }
            OutlinedButton(onClick = { if (!tasked) { tasked = true; a.addTask(d.actions.firstOrNull() ?: d.problem) } }, enabled = !tasked, modifier = Modifier.weight(1f)) { Text(if (tasked) tr("saved") else tr("add_task")) }
        }
        if (!d.healthy && d.category != "unknown" && s.profile?.shareOutbreaks != false) {
            Button(onClick = { if (!warned) { warned = true; a.warnNeighbours(crop, d) } }, enabled = !warned, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = c.redSoft, contentColor = c.red)) { Icon(Icons.Outlined.Campaign, null); Spacer(Modifier.width(6.dp)); Text(if (warned) tr("warned") else tr("warn"), fontWeight = FontWeight.Bold) }
            Muted(tr("warn_note"), 12)
        }
    }
}

@Composable
private fun SymptomCard(crop: String, s: AppState) {
    var ticked by remember { mutableStateOf(setOf<String>()) }
    var out by remember { mutableStateOf(listOf<String>()) }
    CCard {
        SourceBadge("rules"); Text(tr("symptoms"), fontWeight = FontWeight.SemiBold)
        TextChips(Rules.SYMPTOMS.map { it to tr("sym_$it") }, ticked) { k -> ticked = if (k in ticked) ticked - k else ticked + k }
        Button(onClick = { out = Rules.symptomGuide(crop, ticked) }, enabled = ticked.isNotEmpty()) { Text(tr("sym_go")) }
        out.forEachIndexed { i, k -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(Color(Crops.CAT_COLOR[Crops.problem(k).cat] ?: 0xFF57534E)); Text("${i + 1}. ${Crops.problemName(k, s.lang)} · ${tr("cat_${Crops.problem(k).cat}")}", fontWeight = FontWeight.SemiBold)
        } }
        if (out.isNotEmpty()) Note(tr("expert"), warn = true)
    }
}

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

// ---------- Nearby (outbreak radar, drawn offline) ----------
@Composable
fun NearbyScreen(s: AppState, a: Actions) {
    val c = cc
    val p = s.profile ?: return
    val radius = a.ai.outbreakRadiusKm
    val rows = s.outbreaks ?: emptyList()
    Page {
        Text(tr("mp_title"), style = MaterialTheme.typography.headlineMedium)
        Muted(tr("mp_sub"))
        val view = radius * 2.2
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(c.card)) {
            val cx = size.width / 2; val cy = size.height / 2
            val scale = min(size.width, size.height) / 2 / view.toFloat()
            listOf(0.5, 1.0, 1.5, 2.0).forEach { k -> drawCircle(c.line, (radius * k * scale).toFloat(), Offset(cx, cy), style = Stroke(2f, pathEffect = if (k == 1.0) null else PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))) }
            drawCircle(c.green.copy(alpha = 0.07f), (radius * scale).toFloat(), Offset(cx, cy))
            rows.forEach { o ->
                val dy = (o.lat - p.lat) * 111.0
                val dx = (o.lng - p.lng) * 111.0 * cos(Math.toRadians(p.lat))
                if (kotlin.math.abs(dx) < view && kotlin.math.abs(dy) < view) {
                    val col = Color(Crops.CAT_COLOR[o.category] ?: 0xFF57534E)
                    drawCircle(col.copy(alpha = 0.35f), 16f, Offset(cx + (dx * scale).toFloat(), cy - (dy * scale).toFloat()))
                    drawCircle(col, 16f, Offset(cx + (dx * scale).toFloat(), cy - (dy * scale).toFloat()), style = Stroke(3f))
                }
            }
            drawCircle(Color.White, 14f, Offset(cx, cy)); drawCircle(c.green, 10f, Offset(cx, cy))
            drawLine(c.muted, Offset(cx, 12f), Offset(cx, 40f), 3f)
        }
        Muted("N ↑ · ${radius.toInt()} km", 12)
        val groups = Repo.alerts(rows, p.lat, p.lng, radius, 1, null)
        CCard {
            if (groups.isEmpty()) Muted(tr("mp_none"))
            groups.forEach { g -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dot(Color(Crops.CAT_COLOR[g.category] ?: 0xFF57534E))
                Text("${Crops.problemName(g.problemKey, s.lang)} · ${Crops.name(g.crop, s.lang)}", Modifier.weight(1f), fontWeight = if (g.crop in p.crops) FontWeight.ExtraBold else FontWeight.Normal)
                Muted("${g.n}× · ${g.nearestKm} km", 12)
            } }
        }
    }
}

// ---------- Farm ----------
@Composable
fun FarmScreen(s: AppState, a: Actions) {
    val c = cc
    val p = s.profile ?: return
    val cur = Crops.currency(p.country)
    val unit = p.areaUnit
    var addPlot by remember { mutableStateOf(false) }
    var addLog by remember { mutableStateOf(false) }
    Page {
        Text(tr("f_title"), style = MaterialTheme.typography.headlineMedium)
        val month = Repo.today().take(7)
        val inMonth = s.logs.filter { it.date.startsWith(month) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CCard(Modifier.weight(1f)) { Muted(tr("spent"), 12); Text(money(inMonth.filter { it.kind != "sell" }.sumOf { it.money }, cur, s.lang), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
            CCard(Modifier.weight(1f)) { Muted(tr("earned"), 12); Text(money(inMonth.filter { it.kind == "sell" }.sumOf { it.money }, cur, s.lang), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = c.green) }
        }
        CCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Title(tr("plots"), Icons.Outlined.Grass); Spacer(Modifier.weight(1f)); TextButton(onClick = { addPlot = !addPlot }) { Text(tr("add_plot")) } }
            if (addPlot) PlotForm(s, unit, { n, cr, ar, pl -> a.addPlot(n, cr, ar * (Crops.AREA_UNITS[unit] ?: 1.0), pl); addPlot = false }) { addPlot = false }
            s.plots.forEach { pl ->
                val age = pl.planted.takeIf { it.isNotBlank() }?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.parse(it), LocalDate.now()) }.getOrNull() }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dot(Color(Crops.get(pl.crop).color)); Text("${pl.name} · ${Crops.name(pl.crop, s.lang)}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Muted(listOfNotNull(if (pl.area > 0) "%.1f %s".format(pl.area / (Crops.AREA_UNITS[unit] ?: 1.0), tr("area_$unit")) else null, age?.let { if (it >= 365) "${it / 365} ${if (s.lang == "es") "años" else "yr"}" else "$it d" }).joinToString(" · "), 12)
                }
            }
        }
        CCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Title(tr("log"), Icons.Outlined.MenuBook); Spacer(Modifier.weight(1f)); TextButton(onClick = { addLog = !addLog }) { Text(tr("add_log")) } }
            if (addLog) LogForm(s, a) { addLog = false }
            s.logs.forEach { l -> Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(tr("k_${l.kind}"), if (l.kind == "sell" || l.kind == "harvest") c.green else c.ink, if (l.kind == "sell" || l.kind == "harvest") c.greenSoft else c.surface)
                Column(Modifier.weight(1f)) {
                    Text(l.note, fontSize = 14.sp)
                    Muted(listOfNotNull(s.plots.firstOrNull { it.id == l.plotId }?.name, if (l.qty > 0) "${l.qty} ${l.unit}" else null, if (l.money > 0) money(l.money, cur, s.lang) else null, l.date).joinToString(" · "), 12)
                    if (l.pending) Text("⟳ ${tr("pending")}", color = c.amber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { a.deleteLog(l.id) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, null, tint = c.muted, modifier = Modifier.size(18.dp)) }
            } }
        }
    }
}

@Composable
private fun PlotForm(s: AppState, unit: String, onSave: (String, String, Double, String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var crop by remember { mutableStateOf(s.profile?.crops?.firstOrNull() ?: "other") }
    var area by remember { mutableStateOf("") }
    var planted by remember { mutableStateOf(LocalDate.now().toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text(tr("name")) }, singleLine = true)
        CropChips(((s.profile?.crops ?: emptyList()) + "other").distinct(), setOf(crop), s.lang) { crop = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(area, { area = it.filter { ch -> ch.isDigit() || ch == '.' } }, Modifier.weight(1f), label = { Text("${tr("area")} (${tr("area_$unit")})") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(planted, { planted = it.take(10) }, Modifier.weight(1f), label = { Text(tr("planted")) }, singleLine = true)
        }
        Row { Button(onClick = { if (name.isNotBlank()) onSave(name, crop, area.toDoubleOrNull() ?: 0.0, planted) }) { Text(tr("save")) }; TextButton(onClick = onCancel) { Text(tr("cancel")) } }
    }
}

@Composable
private fun LogForm(s: AppState, a: Actions, onDone: () -> Unit) {
    val c = cc
    var kind by remember { mutableStateOf("scout") }
    var note by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var moneyIn by remember { mutableStateOf("") }
    var plot by remember { mutableStateOf("") }
    var via by remember { mutableStateOf("text") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextChips(Repo.LOG_KINDS.map { it to tr("k_$it") }, setOf(kind)) { kind = it }
        OutlinedTextField(note, { note = it.take(300) }, Modifier.fillMaxWidth(), label = { Text(tr("note")) },
            trailingIcon = { IconButton(onClick = { a.dictate { t -> note = (note + " " + t).trim(); via = "voice" } }) { Icon(Icons.Outlined.Mic, tr("dictate"), tint = c.green) } })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(qty, { qty = it.filter { ch -> ch.isDigit() || ch == '.' } }, Modifier.weight(1f), label = { Text(tr("qty")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(moneyIn, { moneyIn = it.filter { ch -> ch.isDigit() || ch == '.' } }, Modifier.weight(1f), label = { Text(tr("money")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        TextChips(listOf("" to "—") + s.plots.map { it.id to it.name }, setOf(plot)) { plot = it }
        Button(onClick = {
            if (note.isBlank() && qty.isBlank() && moneyIn.isBlank()) return@Button
            a.addLog(kind, note, qty.toDoubleOrNull() ?: 0.0, moneyIn.toDoubleOrNull() ?: 0.0, plot, via); onDone()
        }) { Text(tr("save")) }
    }
}

// ---------- Ask ----------
data class ChatMsg(val me: Boolean, val text: String, val reply: Ai.Reply? = null, val error: Boolean = false)

@Composable
fun AskScreen(s: AppState, a: Actions, chat: MutableList<ChatMsg>) {
    val c = cc
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var fresh by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    fun send(text: String) {
        val question = text.trim(); if (question.isBlank() || busy) return
        chat += ChatMsg(true, question); q = ""; busy = true
        scope.launch {
            try { val r = a.ask(question, fresh); chat += ChatMsg(false, r.text, r) } catch (e: Throwable) { chat += ChatMsg(false, tr("err", "m" to (e.message ?: "")), error = true) }
            busy = false
        }
    }
    Page {
        Text(tr("a_title"), style = MaterialTheme.typography.headlineMedium)
        Muted(tr("a_sub"))
        if (chat.isEmpty()) listOf("ex1", "ex2", "ex3").forEach { k -> AssistChip(onClick = { if (k == "ex3") fresh = true; send(tr(k)) }, label = { Text(tr(k)) }) }
        chat.forEach { m ->
            if (m.me) Text(m.text, Modifier.fillMaxWidth(0.85f).align(Alignment.End).clip(RoundedCornerShape(16.dp)).background(c.green).padding(12.dp), color = c.onGreen, fontWeight = FontWeight.SemiBold)
            else CCard(border = if (m.error) c.red else null) {
                m.reply?.let { r -> Row(verticalAlignment = Alignment.CenterVertically) { SourceBadge(if (r.grounded) "grounded" else r.source, r.ms, text = true); Spacer(Modifier.weight(1f)); IconButton(onClick = { a.speak(r.text) }) { Icon(Icons.Outlined.VolumeUp, tr("listen"), tint = c.green) } } }
                Text(m.text, color = if (m.error) c.red else c.ink)
                m.reply?.let { r ->
                    if (r.filtered) Note(tr("filtered"), icon = Icons.Outlined.Shield)
                    if (r.sources.isNotEmpty()) { Muted(tr("sources"), 12); r.sources.forEach { (t, u) -> Text(t.ifBlank { u }, color = c.blue, fontSize = 13.sp) } }
                    TraceBox(r.trace)
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.green)
        OutlinedTextField(q, { q = it.take(600) }, Modifier.fillMaxWidth(), label = { Text(tr("a_hint")) },
            trailingIcon = { IconButton(onClick = { send(q) }) { Icon(Icons.Outlined.Send, tr("send"), tint = c.green) } })
        if (a.ai.grounding) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(fresh, { fresh = it }); Text(tr("fresh"), fontSize = 14.sp) }
        BigButton(tr("speak"), Icons.Outlined.Mic) { a.dictate { t -> send(t) } }
    }
}

// ---------- Settings ----------
@Composable
fun SettingsScreen(s: AppState, a: Actions) {
    val c = cc
    val p = s.profile ?: return
    val scope = rememberCoroutineScope()
    var crops by remember { mutableStateOf(p.crops.toSet()) }
    var share by remember { mutableStateOf(p.shareOutbreaks) }
    var unit by remember { mutableStateOf(p.areaUnit) }
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<Weather.Place>()) }
    var place by remember { mutableStateOf<Weather.Place?>(null) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Page {
        Text(tr("s_title"), style = MaterialTheme.typography.headlineMedium)
        CCard {
            Title(tr("language"), Icons.Outlined.Translate); LangSwitch(s)
            Title(tr("o_place"), Icons.Outlined.Place)
            Text(place?.let { "${it.name}, ${it.admin}" } ?: p.place, fontWeight = FontWeight.Bold)
            OutlinedTextField(q, { q = it; if (it.length >= 2) scope.launch { results = a.searchPlace(it) } }, Modifier.fillMaxWidth(), label = { Text(tr("o_search")) }, singleLine = true)
            results.forEach { r -> Text("${r.name}, ${r.admin} · ${r.country}", Modifier.fillMaxWidth().clickable { place = r; results = emptyList(); q = "" }.padding(8.dp)) }
            Title(tr("o_crops"), Icons.Outlined.Grass)
            CropChips(Crops.ALL.map { it.key }, crops, s.lang) { k -> crops = if (k in crops) crops - k else crops + k }
            TextChips(Crops.AREA_UNITS.keys.map { it to tr("area_$it") }, setOf(unit)) { unit = it }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(share, { share = it }); Text(tr("s_share")) }
            BigButton(tr("save")) {
                if (crops.isEmpty()) return@BigButton
                val pl = place
                a.saveProfile(p.copy(lang = s.lang, crops = crops.toList(), shareOutbreaks = share, areaUnit = unit,
                    place = pl?.let { "${it.name}, ${it.admin}" } ?: p.place, lat = pl?.lat ?: p.lat, lng = pl?.lng ?: p.lng, country = pl?.country ?: p.country))
            }
        }
        CCard {
            Title(tr("s_ai"), Icons.Outlined.Memory)
            when (s.nano) {
                "available" -> Text(tr("s_ready"), color = c.green, fontWeight = FontWeight.Bold)
                "downloadable", "downloading" -> BigButton(s.downloadPct?.let { "$it%" } ?: tr("s_download"), Icons.Outlined.Download, enabled = s.downloadPct == null) { a.downloadNano() }
                else -> Muted(tr("s_none"))
            }
            Muted(tr("s_rc", "src" to s.configSource), 12)
            Muted("model ${a.ai.cloudModel} · floor ${(a.ai.confidenceFloor * 100).toInt()}% · radius ${a.ai.outbreakRadiusKm.toInt()} km · Nano ${a.ai.deviceLanguages.joinToString(",")}", 12)
        }
        CCard {
            Title("Account", Icons.Outlined.Person)
            if (s.anonymous) {
                Muted(tr("s_anon"))
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(tr("email")) }, singleLine = true)
                OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text(tr("password")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedButton(onClick = { a.linkEmail(email.trim(), pass) }) { Text(tr("s_link")) }
            } else Text(s.email ?: "")
            OutlinedButton(onClick = { a.seedSample() }) { Text(tr("o_sample")) }
            TextButton(onClick = { a.signOut() }) { Text(tr("s_signout"), color = c.red) }
        }
    }
}
