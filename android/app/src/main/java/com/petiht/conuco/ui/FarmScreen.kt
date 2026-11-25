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

// ---------- Farm ----------
@Composable
fun FarmScreen(s: AppState, a: Actions) {
    val c = cc
    val p = s.profile ?: return
    val cur = Crops.currency(p.country)
    val unit = p.areaUnit
    var addPlot by remember { mutableStateOf(false) }
    var addLog by remember { mutableStateOf(false) }
    var dictated by remember { mutableStateOf("") }
    Page {
        Text(tr("f_title"), style = MaterialTheme.typography.headlineMedium)
        val month = Repo.today().take(7)
        val inMonth = s.logs.filter { it.date.startsWith(month) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CCard(Modifier.weight(1f)) { Muted(tr("spent"), 12); Text(money(inMonth.filter { it.kind != "sell" }.sumOf { it.money }, cur, s.lang), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
            CCard(Modifier.weight(1f)) { Muted(tr("earned"), 12); Text(money(inMonth.filter { it.kind == "sell" }.sumOf { it.money }, cur, s.lang), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = c.green) }
        }
        val profit = inMonth.filter { it.kind == "sell" }.sumOf { it.money } - inMonth.filter { it.kind != "sell" }.sumOf { it.money }
        Text(tr("f_profit", "m" to money(profit, cur, s.lang)), fontWeight = FontWeight.Bold, color = if (profit >= 0) c.green else c.red)
        BigButton(tr("f_dictate"), Icons.Outlined.Mic) { a.dictate { t -> dictated = t; addLog = true } }
        CCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Title(tr("plots"), Icons.Outlined.Grass); Spacer(Modifier.weight(1f)); TextButton(onClick = { addPlot = !addPlot }) { Text(tr("add_plot")) } }
            if (addPlot) PlotForm(s, unit, { n, cr, ar, pl -> a.addPlot(n, cr, ar * (Crops.AREA_UNITS[unit] ?: 1.0), pl); addPlot = false }) { addPlot = false }
            s.plots.forEach { pl ->
                val age = pl.planted.takeIf { it.isNotBlank() }?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.parse(it), LocalDate.now()) }.getOrNull() }
                val cycle = Crops.CYCLE_DAYS[pl.crop]
                val area = if (pl.area > 0) "%.1f %s".format(pl.area / (Crops.AREA_UNITS[unit] ?: 1.0), tr("area_$unit")) else null
                val ageText = age?.let { if (it >= 365) "${it / 365} ${if (s.lang == "es") "años" else "yr"}" else "$it d" }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val frac = if (cycle != null && age != null) (age.toFloat() / cycle).coerceIn(0f, 1f) else null
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { frac ?: 1f }, modifier = Modifier.fillMaxSize(), strokeWidth = 5.dp,
                            color = if (frac == null) Color(Crops.get(pl.crop).color) else if (frac >= 1f) c.sun else c.green, trackColor = c.line)
                        Text(if (frac != null) "${(frac * 100).toInt()}%" else "∞", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(pl.name, fontWeight = FontWeight.ExtraBold)
                        Muted(listOfNotNull(Crops.name(pl.crop, s.lang), area).joinToString(" · "), 12)
                        val line = when {
                            age == null -> null
                            cycle == null -> tr("f_perennial", "age" to (ageText ?: ""))
                            age >= cycle -> tr("f_ready")
                            else -> tr("f_progress", "d" to age, "t" to cycle) + " · " + tr("f_harvest", "w" to ((cycle - age + 6) / 7))
                        }
                        line?.let { Text(it, fontSize = 12.sp, color = if (cycle != null && age != null && age >= cycle) c.amber else c.green, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        CCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Title(tr("log"), Icons.Outlined.MenuBook); Spacer(Modifier.weight(1f)); TextButton(onClick = { addLog = !addLog }) { Text(tr("add_log")) } }
            if (addLog) LogForm(s, a, dictated) { addLog = false; dictated = "" }
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
private fun LogForm(s: AppState, a: Actions, initialNote: String = "", onDone: () -> Unit) {
    val c = cc
    var kind by remember { mutableStateOf("scout") }
    var note by remember(initialNote) { mutableStateOf(initialNote) }
    var qty by remember { mutableStateOf("") }
    var moneyIn by remember { mutableStateOf("") }
    var plot by remember { mutableStateOf("") }
    var via by remember(initialNote) { mutableStateOf(if (initialNote.isNotBlank()) "voice" else "text") }
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
