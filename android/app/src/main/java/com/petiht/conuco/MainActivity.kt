package com.petiht.conuco

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ondevice.DownloadStatus
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.petiht.conuco.ui.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity(), Actions {
    private val s = AppState()
    override lateinit var ai: Ai
    private lateinit var weather: Weather
    private var repo: Repo? = null
    private val listeners = mutableListOf<ListenerRegistration>()
    private var photoUri: Uri? = null
    private var onDictated: ((String) -> Unit)? = null
    private var gpsCb: ((Weather.Place?) -> Unit)? = null
    private var tts: TextToSpeech? = null
    private val chat = mutableStateListOf<ChatMsg>()

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) photoUri?.let { s.photo = Photos.load(this, it) } }
    private val pickPhotoL = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { s.photo = Photos.load(this, it) } }
    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { onDictated?.invoke(it) }
        onDictated = null
    }
    private val camPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) s.camera = true else legacyCamera() }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> log("notif_permission", "granted" to if (ok) "1" else "0") }
    private val locPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) readLocation() else gpsCb?.invoke(null) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ai = Ai(applicationContext)
        weather = Weather(applicationContext)
        s.lang = if (Locale.getDefault().language == "es") "es" else "en"; Strings.lang = s.lang
        lifecycleScope.launch { ai.loadRemoteConfig(); s.configSource = ai.configSource }
        tts = TextToSpeech(this) { }

        Firebase.auth.addAuthStateListener { fa ->
            val u = fa.currentUser
            if (u?.uid == s.uid && s.profileLoaded) return@addAuthStateListener
            stopWatching()
            s.uid = u?.uid; s.anonymous = u?.isAnonymous == true; s.email = u?.email
            s.profile = null; s.profileLoaded = false
            if (u != null) lifecycleScope.launch {
                val r = Repo(u.uid); repo = r
                s.profile = r.loadProfile()
                s.profile?.let { s.lang = it.lang; Strings.lang = it.lang }
                s.profileLoaded = true
                if (s.profile != null) startWatching()
            }
        }
        lifecycleScope.launch { while (true) { s.online = ai.online(); s.nano = ai.modelStatus(); delay(6000) } }

        setContent {
            ConucoTheme {
                val c = cc
                var tab by s::tab
                key(s.lang) {
                    Surface(Modifier.fillMaxSize(), color = c.surface) {
                        when {
                            s.uid == null -> Box(Modifier.systemBarsPadding()) { AuthScreen(s, this@MainActivity) }
                            !s.profileLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.green) }
                            s.profile == null -> Box(Modifier.systemBarsPadding()) { OnboardingScreen(s, this@MainActivity) }
                            else -> {
                                BackHandler(enabled = tab != "today") { tab = "today" }
                                Scaffold(
                                    containerColor = c.surface,
                                    topBar = { TopBar { tab = "settings" } },
                                    bottomBar = { NavBar(tab) { k -> if (k == "doctor" && tab == "doctor") openCamera() else tab = k; stopSpeaking() } },
                                    floatingActionButton = {
                                        if (tab != "ask") SmallFloatingActionButton(onClick = { tab = "ask"; stopSpeaking() }, containerColor = c.card, contentColor = c.green,
                                            shape = CircleShape, elevation = FloatingActionButtonDefaults.elevation(2.dp, 4.dp)) { Icon(Icons.Outlined.Mic, tr("ask")) }
                                    },
                                ) { pad ->
                                    Box(Modifier.padding(pad)) {
                                        when (tab) {
                                            "doctor" -> DoctorScreen(s, this@MainActivity)
                                            "market" -> MarketScreen(s, this@MainActivity)
                                            "map" -> NearbyScreen(s, this@MainActivity)
                                            "farm" -> FarmScreen(s, this@MainActivity)
                                            "ask" -> AskScreen(s, this@MainActivity, chat)
                                            "settings" -> SettingsScreen(s, this@MainActivity)
                                            else -> TodayScreen(s, this@MainActivity)
                                        }
                                    }
                                }
                                if (s.camera) {
                                    BackHandler { s.camera = false }
                                    CameraScreen(
                                        onPhoto = { uri -> s.photo = Photos.load(this@MainActivity, uri); s.camera = false; log("camera_shot", "via" to "camerax") },
                                        onGallery = { s.camera = false; pickPhoto() },
                                        onClose = { s.camera = false },
                                    )
                                }
                            }
                        }
                    }
                    s.consent?.let { k -> ConsentDialog(k) }
                    s.toast?.let { msg -> LaunchedEffect(msg) { delay(3200); s.toast = null }
                        Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
                            Snackbar(containerColor = c.ink, contentColor = c.bg) { Text(msg) }
                        }
                    }
                }
            }
        }
        handleIntent(intent)
        s.alerts = Bg.alertsOn(this)
    }

    /** Deep links from the widget, app shortcuts and notifications: conuco://tab/{today|doctor|camera|market|map|farm|ask}. */
    private fun handleIntent(i: Intent?) {
        val t = i?.data?.takeIf { it.scheme == "conuco" }?.lastPathSegment ?: i?.getStringExtra("tab") ?: return
        if (t == "camera") { s.tab = "doctor"; openCamera() }
        else if (t in setOf("today", "doctor", "market", "map", "farm", "ask")) s.tab = t
        log("deep_link", "tab" to t)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    @Composable
    private fun NavBar(tab: String, go: (String) -> Unit) {
        val c = cc
        @Composable
        fun RowScope.Item(k: String, ic: androidx.compose.ui.graphics.vector.ImageVector) {
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
    private fun TopBar(onSettings: () -> Unit) {
        val c = cc
        Row(Modifier.fillMaxWidth().background(c.card).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(36.dp).background(c.green, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)))
            Text("Conuco", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1, softWrap = false)
            Spacer(Modifier.weight(1f))
            Icon(if (s.online) Icons.Outlined.Wifi else Icons.Outlined.WifiOff, tr(if (s.online) "online" else "offline"), tint = if (s.online) c.green else c.amber, modifier = Modifier.size(20.dp))
            Pill(if (s.nano == "available") "Nano ✓" else "Nano –", if (s.nano == "available") c.green else c.muted, if (s.nano == "available") c.greenSoft else c.surface, Icons.Outlined.Memory)
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, tr("settings")) }
        }
    }

    @Composable
    private fun ConsentDialog(k: Consent) {
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

    // ---------- data ----------
    private fun startWatching() {
        val r = repo ?: return
        listeners += r.watchPlots { s.plots = it }
        listeners += r.watchLog { s.logs = it }
        listeners += r.watchDiagnoses { s.diags = it }
        listeners += r.watchTasks { s.tasks = it; s.tasksLoaded = true; addWeatherTasks() }
        val p = s.profile ?: return
        Bg.saveSnapshot(this, p, ai.sprayMaxWind, ai.outbreakRadiusKm, ai.outbreakAlertMin)
        askNotifications()
        weather.cached(p.lat, p.lng)?.let { s.analysis = Rules.analyse(it, p.crops, ai.sprayMaxWind); s.wxFrom = "cache" }
        refreshWeather(false)
        lifecycleScope.launch { loadCommunity() }
    }
    private fun stopWatching() { listeners.forEach { it.remove() }; listeners.clear() }

    private suspend fun loadCommunity() {
        val r = repo ?: return
        runCatching { s.prices = r.loadPrices(maxOf(ai.priceWindowDays * 2, 14)) }.onFailure { if (s.prices == null) s.prices = emptyList() }
        runCatching { s.outbreaks = r.loadOutbreaks(21) }.onFailure { if (s.outbreaks == null) s.outbreaks = emptyList() }
    }

    override fun refreshWeather(force: Boolean) {
        val p = s.profile ?: return
        lifecycleScope.launch {
            weather.get(p.lat, p.lng, ai.online(), force)?.let { r -> s.analysis = Rules.analyse(r.json, p.crops, ai.sprayMaxWind); s.wxFrom = r.from; addWeatherTasks() }
            s.analysis?.let { Bg.updateWidget(applicationContext, it, p.place.substringBefore(',').trim(), s.lang) }
            if (force) loadCommunity()
        }
    }

    private fun addWeatherTasks() {
        val r = repo ?: return
        val an = s.analysis ?: return
        if (!s.tasksLoaded) return
        val day = Repo.today()
        val have = s.tasks.map { it.id }.toSet()
        val list = mutableListOf<Triple<String, String, String>>()
        an.windows.firstOrNull()?.let { list += Triple("w-spray-$day", "spray_window", tr("t_spray", "w" to windowText(it, s.lang))) }
        if (an.irrigation == "irrigate") list += Triple("w-irr-$day", "irrigate", tr("t_irrigate", "mm" to an.deficit.toInt()))
        if (an.disease == "high") list += Triple("w-scout-$day", "scout_fungus", tr("t_scout"))
        if (an.stormDate != null) list += Triple("w-storm-$day", "drainage", tr("t_drain"))
        an.heatMax?.let { list += Triple("w-heat-$day", "heat", tr("t_heat", "t" to it)) }
        list.filter { it.first !in have }.forEach { (id, code, text) -> r.upsertTask(id, code, text, "weather") }
    }

    // ---------- Actions ----------
    override fun signInAnon() { lifecycleScope.launch { runCatching { Firebase.auth.signInAnonymously().await(); log("sign_up", "method" to "anonymous") }.onFailure { s.toast = tr("err", "m" to (it.message ?: "")) } } }
    override fun signInEmail(email: String, pass: String, create: Boolean) {
        if (email.isBlank() || pass.length < 6) return
        lifecycleScope.launch { runCatching {
            if (create) Firebase.auth.createUserWithEmailAndPassword(email, pass).await() else Firebase.auth.signInWithEmailAndPassword(email, pass).await()
        }.onFailure { s.toast = tr("err", "m" to (it.message ?: "")) } }
    }
    override suspend fun searchPlace(q: String) = weather.search(q, s.lang)
    override fun useGps(cb: (Weather.Place?) -> Unit) {
        gpsCb = cb
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) readLocation()
        else locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    @SuppressLint("MissingPermission")
    private fun readLocation() {
        val lm = getSystemService(LocationManager::class.java)
        val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (loc == null) { gpsCb?.invoke(null); s.toast = tr("err", "m" to "location"); return }
        val lat = Weather.coarse(loc.latitude); val lng = Weather.coarse(loc.longitude)
        lifecycleScope.launch {
            val near = runCatching {
                val j = org.json.JSONObject(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Weather.http("https://geocoding-api.open-meteo.com/v1/reverse?latitude=$lat&longitude=$lng&language=${s.lang}") })
                j.optJSONArray("results")?.optJSONObject(0)
            }.getOrNull()
            gpsCb?.invoke(Weather.Place(near?.optString("name") ?: "$lat, $lng", near?.optString("admin1") ?: "", near?.optString("country_code") ?: "", lat, lng))
        }
    }
    override fun finishOnboarding(p: Profile, sample: Boolean) {
        val r = repo ?: return
        r.saveProfile(p)
        s.profile = p
        startWatching()
        log("onboarding_done", "crops" to p.crops.size.toString())
        if (sample) lifecycleScope.launch { runCatching { r.seedSample() } }
        Bg.runNow(this)
    }
    override fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) s.camera = true
        else camPerm.launch(Manifest.permission.CAMERA)
    }
    /** Fallback when the camera permission is refused: the system camera app writes into our cache, no permission needed. */
    private fun legacyCamera() {
        val dir = File(cacheDir, "photos").apply { mkdirs() }
        val f = File(dir, "leaf.jpg")
        photoUri = FileProvider.getUriForFile(this, "com.petiht.conuco.files", f)
        takePhoto.launch(photoUri!!)
    }
    override fun loadSample(file: String) {
        s.photo = runCatching { assets.open("samples/$file").use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()
    }
    override fun pickPhoto() = pickPhotoL.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    override fun dictate(onText: (String) -> Unit) {
        onDictated = onText
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (s.lang == "es") "es-ES" else "en-US")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        runCatching { speech.launch(i); log("voice_input", "prefer_offline" to "1") }.onFailure { s.toast = tr("err", "m" to "speech") }
    }
    override suspend fun diagnose(crop: String, note: String, mode: String): Ai.DoctorResult {
        val bmp = s.photo ?: throw IllegalStateException("no photo")
        val r = ai.diagnose(bmp, crop, note, s.lang, mode) { reason, conf ->
            val d = CompletableDeferred<Boolean>(); s.consent = Consent(reason, conf, d); d.await()
        }
        log("diagnose", "mode" to mode, "source" to (if (r.second != null) "both" else r.first?.source ?: "none"))
        return r
    }
    override fun saveDiagnosis(crop: String, d: Ai.Diagnosis, second: String) {
        val r = repo ?: return
        r.addDiagnosis(crop, d, second)
        r.addLog("scout", "${d.problem} (${(d.confidence * 100).toInt()}%) — ${Crops.name(crop, s.lang)}", 0.0, "", 0.0, "", "doctor")
    }
    override fun addTask(text: String) { repo?.upsertTask("doc-${System.currentTimeMillis()}", "doctor", text, "doctor") }
    override fun warnNeighbours(crop: String, d: Ai.Diagnosis) {
        val p = s.profile ?: return
        repo?.addOutbreak(crop, d.problemKey, d.category, Repo.cellOf(p.lat, p.lng), p.country)
        log("outbreak_report", "category" to d.category)
        lifecycleScope.launch { delay(1500); loadCommunity() }
    }
    override suspend fun brief(): Ai.Brief {
        val p = s.profile!!; val an = s.analysis ?: throw IllegalStateException("no weather")
        val f = mutableListOf<String>()
        f += "Crops: ${p.crops.joinToString(", ") { Crops.name(it, "en") }}."
        an.now?.let { f += "Now: ${it.temp} C, humidity ${it.rh}%, wind ${it.wind} km/h, rain chance ${it.prob}%." }
        f += if (an.okNow) "Spraying conditions are good now." else an.windows.firstOrNull()?.let { "Do not spray now. Next safe spray window: ${windowText(it, "en")}." } ?: "No safe spray window in 48 hours."
        f += "Irrigation: ${an.irrigation} (crops need ${an.cropUse} mm, rain gives ${an.rainEff} mm in 3 days)."
        f += "Fungal disease risk: ${an.disease} (${an.wetHours} very humid hours)."
        an.heatMax?.let { f += "Heat up to $it C." }
        s.outbreaks?.let { Repo.alerts(it, p.lat, p.lng, ai.outbreakRadiusKm, ai.outbreakAlertMin, p.crops).firstOrNull() }?.let { f += "Neighbours reported ${Crops.problemName(it.problemKey, "en")} on ${Crops.name(it.crop, "en")} (${it.n} reports nearby)." }
        return ai.brief(f, s.lang).also { log("brief", "source" to it.source) }
    }
    override fun speak(text: String) { tts?.language = Locale.forLanguageTag(if (s.lang == "es") "es-ES" else "en-US"); tts?.speak(text.take(1200), TextToSpeech.QUEUE_FLUSH, null, "conuco"); log("speak") }
    override fun stopSpeaking() { tts?.stop() }
    override fun addPrice(crop: String, unit: String, price: Double, market: String) {
        val p = s.profile ?: return
        val cur = Crops.currency(p.country); val cell = Repo.cellOf(p.lat, p.lng)
        repo?.addPrice(crop, unit, price, cur, market, p.country, cell)
        s.prices = listOf(PriceRow(crop, unit, price, cur, market, cell, Repo.today())) + (s.prices ?: emptyList())
        s.toast = tr("thanks"); log("price_report", "crop" to crop)
    }
    override fun addPlot(name: String, crop: String, area: Double, planted: String) { repo?.addPlot(name, crop, area, planted); log("plot_add", "crop" to crop) }
    override fun addLog(kind: String, note: String, qty: Double, money: Double, plotId: String, via: String) { repo?.addLog(kind, note, qty, "", money, plotId, via); log("log_add", "kind" to kind, "via" to via) }
    override fun deleteLog(id: String) { repo?.deleteLog(id) }
    override fun setTaskDone(id: String, done: Boolean) { repo?.setTaskDone(id, done) }
    override suspend fun ask(q: String, fresh: Boolean): Ai.Reply {
        val p = s.profile!!
        val an = s.analysis
        val ctx = "Crops: ${p.crops.joinToString(", ") { Crops.name(it, "en") }}. Region: ${p.place.substringAfterLast(',').trim()}, ${p.country}." +
            (an?.now?.let { " Weather now: ${it.temp} C, humidity ${it.rh}%." } ?: "")
        return ai.ask(q, ctx, s.lang, fresh).also { log("ask", "source" to it.source, "grounded" to if (it.grounded) "1" else "0") }
    }
    override fun saveProfile(p: Profile) {
        repo?.saveProfile(p); s.profile = p; s.lang = p.lang; Strings.lang = p.lang
        Bg.saveSnapshot(this, p, ai.sprayMaxWind, ai.outbreakRadiusKm, ai.outbreakAlertMin)
        refreshWeather(true); s.toast = tr("saved")
    }
    override fun downloadNano() {
        lifecycleScope.launch {
            s.downloadPct = 0
            runCatching {
                ai.download().collect { st ->
                    when (st) {
                        is DownloadStatus.DownloadInProgress -> s.downloadPct = (st.totalBytesDownloaded / 1_000_000).toInt()
                        is DownloadStatus.DownloadCompleted -> s.downloadPct = null
                        is DownloadStatus.DownloadFailed -> { s.downloadPct = null; s.toast = tr("err", "m" to "download") }
                        else -> {}
                    }
                }
            }.onFailure { s.downloadPct = null; s.toast = tr("err", "m" to (it.message ?: "download")) }
            s.nano = ai.modelStatus()
        }
    }
    override fun linkEmail(email: String, pass: String) {
        val u = Firebase.auth.currentUser ?: return
        lifecycleScope.launch { runCatching { u.linkWithCredential(EmailAuthProvider.getCredential(email, pass)).await(); s.anonymous = false; s.email = email; s.toast = tr("saved") }
            .onFailure { s.toast = tr("err", "m" to (it.message ?: "")) } }
    }
    private fun askNotifications() {
        val sp = getSharedPreferences("conuco_bg", MODE_PRIVATE)
        if (android.os.Build.VERSION.SDK_INT >= 33 && !sp.getBoolean("asked_notif", false) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            sp.edit().putBoolean("asked_notif", true).apply()
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    override fun setAlerts(on: Boolean) {
        Bg.setAlertsOn(this, on); s.alerts = on
        if (on && android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        log("alerts_toggle", "on" to if (on) "1" else "0")
    }
    override fun pinWidget() {
        val wm = getSystemService(android.appwidget.AppWidgetManager::class.java)
        if (wm?.isRequestPinAppWidgetSupported == true) { wm.requestPinAppWidget(android.content.ComponentName(this, TodayWidgetReceiver::class.java), null, null); log("widget_pin") }
        else s.toast = tr("s_widget")
    }
    override fun testAlerts() {
        getSharedPreferences("conuco_bg", MODE_PRIVATE).edit().remove("n_spray").remove("n_fungus").remove("n_seen").apply()
        Bg.runNow(this); s.toast = tr("s_alerts_test_ok")
    }
    /** Android share sheet: the redrawn photo (no EXIF, no GPS) plus a short text any farmer or agronomist can read. */
    override fun shareDiagnosis(crop: String, d: Ai.Diagnosis) {
        val text = tr("sh_text", "p" to d.problem.ifBlank { tr("cat_${d.category}") }, "c" to Crops.name(crop, s.lang), "pct" to (d.confidence * 100).toInt(),
            "src" to tr(if (d.source == "on_device") "sh_device" else "sh_cloud"), "a" to d.actions.take(3).joinToString("; ")).trim()
        val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, "Conuco")
        val bmp = s.photo
        if (bmp != null) runCatching {
            val f = File(File(cacheDir, "photos").apply { mkdirs() }, "share.jpg")
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
            send.setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, "com.petiht.conuco.files", f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { send.setType("text/plain") } else send.setType("text/plain")
        startActivity(Intent.createChooser(send, tr("share")))
        log("share", "content_type" to "diagnosis")
    }
    override fun signOut() { stopWatching(); Firebase.auth.signOut() }
    override fun seedSample() { lifecycleScope.launch { runCatching { repo?.seedSample(); s.toast = tr("saved") } } }

    private fun log(name: String, vararg params: Pair<String, String>) = runCatching { Firebase.analytics.logEvent(name) { params.forEach { (k, v) -> param(k, v) } } }

    override fun onDestroy() { tts?.shutdown(); stopWatching(); super.onDestroy() }
}
