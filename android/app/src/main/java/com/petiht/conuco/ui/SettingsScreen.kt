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
            Title(tr("s_alerts"), Icons.Outlined.NotificationsActive)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(tr("s_alerts_on"), fontWeight = FontWeight.Bold); Muted(tr("s_alerts_d"), 13) }
                Switch(s.alerts, { a.setAlerts(it) }, colors = SwitchDefaults.colors(checkedTrackColor = c.green))
            }
            OutlinedButton(onClick = { a.testAlerts() }, enabled = s.alerts) { Icon(Icons.Outlined.NotificationsNone, null); Spacer(Modifier.width(6.dp)); Text(tr("s_alerts_test")) }
            Muted(tr("s_widget"), 12)
            TextButton(onClick = { a.pinWidget() }) { Icon(Icons.Outlined.Widgets, null); Spacer(Modifier.width(6.dp)); Text(tr("s_widget_add")) }
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
