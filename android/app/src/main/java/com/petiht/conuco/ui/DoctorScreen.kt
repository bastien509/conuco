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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
            r.second?.let { DiagCard(it, r.first != null, s, a) }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val ring = when { d.confidence >= 0.75 -> c.green; d.confidence >= 0.5 -> c.amber; else -> c.red }
            Box(Modifier.size(68.dp).semantics { contentDescription = "${tr("confidence")} ${(d.confidence * 100).toInt()}%" }, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { d.confidence.toFloat() }, modifier = Modifier.fillMaxSize(), strokeWidth = 7.dp, color = ring, trackColor = c.surface)
                Text("${(d.confidence * 100).toInt()}%", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(d.problem.ifBlank { tr("cat_${d.category}") }, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(tr("cat_${d.category}"), Color(Crops.CAT_COLOR[d.category] ?: 0xFF57534E), c.surface)
                    if (!d.healthy) Pill(tr("sev_${d.severity}"), when (d.severity) { "high" -> c.red; "low" -> c.green; else -> c.amber }, when (d.severity) { "high" -> c.redSoft; "low" -> c.greenSoft; else -> c.sunSoft })
                }
            }
        }
        if (d.signs.isNotEmpty()) { Text(tr("signs").uppercase(), color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold); d.signs.forEach { Text("• $it") } }
        if (d.actions.isNotEmpty()) {
            Text(tr("do_now").uppercase(), color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            var added by remember(d) { mutableStateOf(setOf<Int>()) }
            d.actions.forEachIndexed { i, x ->
                val on = i in added
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) c.greenSoft else c.surface)
                    .clickable(enabled = !on) { added = added + i; a.addTask(x) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (on) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = c.green, modifier = Modifier.size(22.dp))
                    Text(x, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (on) Muted(tr("dx_added"), 11)
                }
            }
            Muted(tr("dx_tap"), 12)
        }
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
        OutlinedButton(onClick = { a.shareDiagnosis(crop, d) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(6.dp)); Text(tr("share")) }
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
