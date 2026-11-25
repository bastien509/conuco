package com.petiht.conuco.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
        if (chat.isEmpty()) listOf("ex1", "ex2", "ex3").forEach { k -> AssistChip(onClick = { if (k == "ex3") fresh = true; send(tr(k)) }, label = { Text(tr(k)) }, shape = RoundedCornerShape(14.dp)) }
        chat.forEach { m ->
            if (m.me) Text(m.text, Modifier.fillMaxWidth(0.85f).align(Alignment.End).clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).background(c.green).padding(12.dp), color = c.onGreen, fontWeight = FontWeight.SemiBold)
            else Column(Modifier.fillMaxWidth(0.94f).clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)).background(c.card).border(1.dp, if (m.error) c.red else c.line, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(c.greenSoft).padding(8.dp).clip(CircleShape).background(c.green)
                .clickable(enabled = !busy) { a.dictate { t -> send(t) } }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Mic, tr("speak"), tint = c.onGreen, modifier = Modifier.size(34.dp))
            }
            Muted(tr("a_tap"), 13)
        }
    }
}
