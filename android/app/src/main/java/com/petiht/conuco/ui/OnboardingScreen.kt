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
