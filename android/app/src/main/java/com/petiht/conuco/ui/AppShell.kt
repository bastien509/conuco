package com.petiht.conuco.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petiht.conuco.Actions
import com.petiht.conuco.AppState
import com.petiht.conuco.Consent
import com.petiht.conuco.Photos
import com.petiht.conuco.R
import com.petiht.conuco.Telemetry
import com.petiht.conuco.tr
import kotlinx.coroutines.delay

/** The whole app UI: sign-in, onboarding, or the main shell (top bar, screens, camera-center nav, leaf camera, consent, toasts). */
@Composable
fun AppShell(s: AppState, a: Actions, chat: MutableList<ChatMsg>, onSignedIn: () -> Unit) {
    val ctx = LocalContext.current
    ConucoTheme {
        val c = cc
        var tab by s::tab
        key(s.lang) {
            Surface(Modifier.fillMaxSize(), color = c.surface) {
                when {
                    s.uid == null -> Box(Modifier.systemBarsPadding()) { AuthScreen(s, a) }
                    !s.profileLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.green) }
                    s.profile == null -> Box(Modifier.systemBarsPadding()) { OnboardingScreen(s, a) }
                    else -> {
                        LaunchedEffect(s.uid) { onSignedIn() }
                        BackHandler(enabled = tab != "today") { tab = "today" }
                        Scaffold(
                            containerColor = c.surface,
                            topBar = { TopBar(s) { tab = "settings" } },
                            bottomBar = { NavBar(tab) { k -> if (k == "doctor" && tab == "doctor") a.openCamera() else tab = k; a.stopSpeaking() } },
                            floatingActionButton = {
                                if (tab != "ask") SmallFloatingActionButton(onClick = { tab = "ask"; a.stopSpeaking() }, containerColor = c.card, contentColor = c.green,
                                    shape = CircleShape, elevation = FloatingActionButtonDefaults.elevation(2.dp, 4.dp)) { Icon(Icons.Outlined.Mic, tr("ask")) }
                            },
                        ) { pad ->
                            Box(Modifier.padding(pad)) {
                                when (tab) {
                                    "doctor" -> DoctorScreen(s, a)
                                    "market" -> MarketScreen(s, a)
                                    "map" -> NearbyScreen(s, a)
                                    "farm" -> FarmScreen(s, a)
                                    "ask" -> AskScreen(s, a, chat)
                                    "settings" -> SettingsScreen(s, a)
                                    else -> TodayScreen(s, a)
                                }
                            }
                        }
                        if (s.camera) {
                            BackHandler { s.camera = false }
                            CameraScreen(
                                onPhoto = { uri -> s.photo = Photos.load(ctx, uri); s.camera = false; Telemetry.event("camera_shot", "via" to "camerax") },
                                onGallery = { s.camera = false; a.pickPhoto() },
                                onClose = { s.camera = false },
                            )
                        }
                    }
                }
            }
            s.consent?.let { k -> ConsentDialog(s, k) }
            s.toast?.let { msg -> LaunchedEffect(msg) { delay(3200); s.toast = null }
                Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
                    Snackbar(containerColor = c.ink, contentColor = c.bg) { Text(msg) }
                }
            }
        }
    }
}

/** Hoy · Mercado · [Doctor camera] · Cerca · Finca. Pregunta lives in the floating mic. */
@Composable
fun NavBar(tab: String, go: (String) -> Unit) {
    val c = cc
    @Composable
    fun RowScope.Item(k: String, ic: ImageVector) {
        val on = tab == k
        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { go(k) }.padding(vertical = 6.dp).semantics { selected = on; role = Role.Tab },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(ic, null, tint = if (on) c.green else c.muted, modifier = Modifier.size(24.dp))
            Text(tr(k), fontSize = 11.sp, maxLines = 1, color = if (on) c.green else c.muted, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
        }
    }
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(top = 26.dp).fillMaxWidth().background(c.card)) {
            HorizontalDivider(color = c.line)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
                Item("today", Icons.Outlined.WbSunny)
                Item("market", Icons.Outlined.Storefront)
                val on = tab == "doctor"
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { go("doctor") }.padding(vertical = 6.dp).semantics { selected = on; role = Role.Tab },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(26.dp))
                    Text(tr("doctor"), fontSize = 11.sp, maxLines = 1, color = if (on) c.green else c.muted, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                }
                Item("map", Icons.Outlined.Radar)
                Item("farm", Icons.Outlined.MenuBook)
            }
        }
        Box(Modifier.align(Alignment.TopCenter).size(64.dp).background(c.surface, CircleShape).padding(5.dp)
            .background(c.green, CircleShape).clip(CircleShape).clickable { go("doctor") }
            .semantics { contentDescription = tr(if (tab == "doctor") "camera" else "doctor") }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.PhotoCamera, null, tint = c.onGreen, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun TopBar(s: AppState, onSettings: () -> Unit) {
    val c = cc
    Row(Modifier.fillMaxWidth().background(c.card).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(36.dp).background(c.green, RoundedCornerShape(10.dp)))
        Text("Conuco", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1, softWrap = false)
        Spacer(Modifier.weight(1f))
        Icon(if (s.online) Icons.Outlined.Wifi else Icons.Outlined.WifiOff, tr(if (s.online) "online" else "offline"), tint = if (s.online) c.green else c.amber, modifier = Modifier.size(20.dp))
        Pill(if (s.nano == "available") "Nano ✓" else "Nano –", if (s.nano == "available") c.green else c.muted, if (s.nano == "available") c.greenSoft else c.surface, Icons.Outlined.Memory)
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, tr("settings")) }
    }
}

/** Asked before any photo or question leaves the phone. */
@Composable
fun ConsentDialog(s: AppState, k: Consent) {
    val c = cc
    AlertDialog(
        onDismissRequest = { s.consent = null; k.answer.complete(false) },
        icon = { Icon(Icons.Outlined.Cloud, null, tint = c.cloud) },
        title = { Text(tr("c_title"), fontWeight = FontWeight.ExtraBold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(when (k.reason) { "second_opinion" -> tr("c_second", "c" to (k.confidence * 100).toInt()); "cloud_first" -> tr("c_cloud_first"); else -> tr("c_no_device") })
            Text(tr("c_what"), color = c.muted, fontSize = 13.sp)
        } },
        confirmButton = { Button(onClick = { s.consent = null; k.answer.complete(true) }, colors = ButtonDefaults.buttonColors(containerColor = c.green, contentColor = c.onGreen)) { Text(tr("yes_send")) } },
        dismissButton = { TextButton(onClick = { s.consent = null; k.answer.complete(false) }) { Text(tr("no_keep")) } },
        containerColor = c.card,
    )
}
