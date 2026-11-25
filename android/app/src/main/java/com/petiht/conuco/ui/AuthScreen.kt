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
