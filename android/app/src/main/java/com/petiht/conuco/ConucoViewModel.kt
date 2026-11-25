package com.petiht.conuco

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ondevice.DownloadStatus
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.petiht.conuco.ui.ChatMsg
import com.petiht.conuco.ui.windowText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * App logic and data, kept apart from the Activity so it survives rotation and process-light config changes.
 * Owns the observable [AppState], the Firestore listeners, weather, community data and every AI call.
 * The Activity only adds what needs an Activity: permissions, camera, speech, share sheet, text-to-speech.
 */
class ConucoViewModel(app: Application) : AndroidViewModel(app) {
    val s = AppState()
    val ai = Ai(app)
    val chat = mutableStateListOf<ChatMsg>()
    private val weather = Weather(app)
    private var repo: Repo? = null
    private val listeners = mutableListOf<ListenerRegistration>()
    private val ctx get() = getApplication<Application>()

    private val authListener = FirebaseAuth.AuthStateListener { fa ->
        val u = fa.currentUser
        if (u?.uid == s.uid && s.profileLoaded) return@AuthStateListener
        stopWatching()
        s.uid = u?.uid; s.anonymous = u?.isAnonymous == true; s.email = u?.email
        s.profile = null; s.profileLoaded = false
        Telemetry.user(u?.uid)
        if (u != null) viewModelScope.launch {
            val r = Repo(u.uid); repo = r
            s.profile = runCatching { r.loadProfile() }.onFailure { Telemetry.error(it, "load_profile") }.getOrNull()
            s.profile?.let { s.lang = it.lang; Strings.lang = it.lang }
            s.profileLoaded = true
            if (s.profile != null) startWatching()
        }
    }

    init {
        s.lang = if (Locale.getDefault().language == "es") "es" else "en"; Strings.lang = s.lang
        s.alerts = Bg.alertsOn(app)
        viewModelScope.launch { ai.loadRemoteConfig(); s.configSource = ai.configSource }
        Firebase.auth.addAuthStateListener(authListener)
        viewModelScope.launch {
            while (true) { s.online = ai.online(); s.nano = ai.modelStatus(); Telemetry.key("nano", s.nano); delay(6000) }
        }
    }

    // ---------- data ----------
    private fun startWatching() {
        val r = repo ?: return
        listeners += r.watchPlots { s.plots = it }
        listeners += r.watchLog { s.logs = it }
        listeners += r.watchDiagnoses { s.diags = it }
        listeners += r.watchTasks { s.tasks = it; s.tasksLoaded = true; addWeatherTasks() }
        val p = s.profile ?: return
        Bg.saveSnapshot(ctx, p, ai.sprayMaxWind, ai.outbreakRadiusKm, ai.outbreakAlertMin)
        weather.cached(p.lat, p.lng)?.let { s.analysis = Rules.analyse(it, p.crops, ai.sprayMaxWind); s.wxFrom = "cache" }
        refreshWeather(false)
        viewModelScope.launch { loadCommunity() }
    }
    private fun stopWatching() { listeners.forEach { it.remove() }; listeners.clear() }

    private suspend fun loadCommunity() {
        val r = repo ?: return
        runCatching { s.prices = r.loadPrices(maxOf(ai.priceWindowDays * 2, 14)) }.onFailure { if (s.prices == null) s.prices = emptyList() }
        runCatching { s.outbreaks = r.loadOutbreaks(21) }.onFailure { if (s.outbreaks == null) s.outbreaks = emptyList() }
    }

    fun refreshWeather(force: Boolean) {
        val p = s.profile ?: return
        viewModelScope.launch {
            Telemetry.trace("weather_refresh", "force" to force.toString()) {
                weather.get(p.lat, p.lng, ai.online(), force)?.let { r -> s.analysis = Rules.analyse(r.json, p.crops, ai.sprayMaxWind); s.wxFrom = r.from; it.attr("from", r.from); addWeatherTasks() }
            }
            s.analysis?.let { Bg.updateWidget(ctx, it, p.place.substringBefore(',').trim(), s.lang) }
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

    private fun fail(e: Throwable, where: String) { Telemetry.error(e, where); s.toast = tr("err", "m" to (e.message ?: where)) }

    // ---------- account ----------
    fun signInAnon() { viewModelScope.launch { runCatching { Firebase.auth.signInAnonymously().await(); Telemetry.event("sign_up", "method" to "anonymous") }.onFailure { fail(it, "sign_in_anon") } } }
    fun signInEmail(email: String, pass: String, create: Boolean) {
        if (email.isBlank() || pass.length < 6) return
        viewModelScope.launch { runCatching {
            if (create) Firebase.auth.createUserWithEmailAndPassword(email, pass).await() else Firebase.auth.signInWithEmailAndPassword(email, pass).await()
        }.onFailure { fail(it, "sign_in_email") } }
    }
    fun linkEmail(email: String, pass: String) {
        val u = Firebase.auth.currentUser ?: return
        viewModelScope.launch { runCatching { u.linkWithCredential(EmailAuthProvider.getCredential(email, pass)).await(); s.anonymous = false; s.email = email; s.toast = tr("saved") }
            .onFailure { fail(it, "link_email") } }
    }
    fun signOut() { stopWatching(); Firebase.auth.signOut() }

    suspend fun searchPlace(q: String) = weather.search(q, s.lang)

    fun finishOnboarding(p: Profile, sample: Boolean) {
        val r = repo ?: return
        r.saveProfile(p)
        s.profile = p
        startWatching()
        Telemetry.event("onboarding_done", "crops" to p.crops.size.toString())
        if (sample) viewModelScope.launch { runCatching { r.seedSample() } }
        Bg.runNow(ctx)
    }
    fun saveProfile(p: Profile) {
        repo?.saveProfile(p); s.profile = p; s.lang = p.lang; Strings.lang = p.lang
        Bg.saveSnapshot(ctx, p, ai.sprayMaxWind, ai.outbreakRadiusKm, ai.outbreakAlertMin)
        refreshWeather(true); s.toast = tr("saved")
    }
    fun seedSample() { viewModelScope.launch { runCatching { repo?.seedSample(); s.toast = tr("saved") }.onFailure { fail(it, "seed") } } }

    // ---------- doctor ----------
    fun loadSample(file: String) {
        s.photo = runCatching { ctx.assets.open("samples/$file").use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()
    }
    suspend fun diagnose(crop: String, note: String, mode: String): Ai.DoctorResult {
        val bmp = s.photo ?: throw IllegalStateException("no photo")
        return Telemetry.trace("diagnose", "mode" to mode, "crop" to crop) { t ->
            val r = ai.diagnose(bmp, crop, note, s.lang, mode) { reason, conf ->
                val d = CompletableDeferred<Boolean>(); s.consent = Consent(reason, conf, d); d.await()
            }
            val source = if (r.second != null) "both" else r.first?.source ?: "none"
            t.attr("source", source)
            (r.second ?: r.first)?.let { t.metric("confidence_pct", (it.confidence * 100).toLong()); t.metric("model_ms", it.ms) }
            Telemetry.event("diagnose", "mode" to mode, "source" to source)
            r
        }
    }
    fun saveDiagnosis(crop: String, d: Ai.Diagnosis, second: String) {
        val r = repo ?: return
        r.addDiagnosis(crop, d, second)
        r.addLog("scout", "${d.problem} (${(d.confidence * 100).toInt()}%) — ${Crops.name(crop, s.lang)}", 0.0, "", 0.0, "", "doctor")
    }
    fun addTask(text: String) { repo?.upsertTask("doc-${System.currentTimeMillis()}", "doctor", text, "doctor") }
    fun warnNeighbours(crop: String, d: Ai.Diagnosis) {
        val p = s.profile ?: return
        repo?.addOutbreak(crop, d.problemKey, d.category, Repo.cellOf(p.lat, p.lng), p.country)
        Telemetry.event("outbreak_report", "category" to d.category)
        viewModelScope.launch { delay(1500); loadCommunity() }
    }

    // ---------- today / ask ----------
    suspend fun brief(): Ai.Brief {
        val p = s.profile!!; val an = s.analysis ?: throw IllegalStateException("no weather")
        val f = mutableListOf<String>()
        f += "Crops: ${p.crops.joinToString(", ") { Crops.name(it, "en") }}."
        an.now?.let { f += "Now: ${it.temp} C, humidity ${it.rh}%, wind ${it.wind} km/h, rain chance ${it.prob}%." }
        f += if (an.okNow) "Spraying conditions are good now." else an.windows.firstOrNull()?.let { "Do not spray now. Next safe spray window: ${windowText(it, "en")}." } ?: "No safe spray window in 48 hours."
        f += "Irrigation: ${an.irrigation} (crops need ${an.cropUse} mm, rain gives ${an.rainEff} mm in 3 days)."
        f += "Fungal disease risk: ${an.disease} (${an.wetHours} very humid hours)."
        an.heatMax?.let { f += "Heat up to $it C." }
        s.outbreaks?.let { Repo.alerts(it, p.lat, p.lng, ai.outbreakRadiusKm, ai.outbreakAlertMin, p.crops).firstOrNull() }?.let { f += "Neighbours reported ${Crops.problemName(it.problemKey, "en")} on ${Crops.name(it.crop, "en")} (${it.n} reports nearby)." }
        return Telemetry.trace("brief") { t -> ai.brief(f, s.lang).also { t.attr("source", it.source); Telemetry.event("brief", "source" to it.source) } }
    }
    suspend fun ask(q: String, fresh: Boolean): Ai.Reply {
        val p = s.profile!!
        val an = s.analysis
        val c = "Crops: ${p.crops.joinToString(", ") { Crops.name(it, "en") }}. Region: ${p.place.substringAfterLast(',').trim()}, ${p.country}." +
            (an?.now?.let { " Weather now: ${it.temp} C, humidity ${it.rh}%." } ?: "")
        return Telemetry.trace("ask", "fresh" to fresh.toString()) { t ->
            ai.ask(q, c, s.lang, fresh).also { t.attr("source", it.source); Telemetry.event("ask", "source" to it.source, "grounded" to if (it.grounded) "1" else "0") }
        }
    }

    // ---------- market / farm ----------
    fun addPrice(crop: String, unit: String, price: Double, market: String) {
        val p = s.profile ?: return
        val cur = Crops.currency(p.country); val cell = Repo.cellOf(p.lat, p.lng)
        repo?.addPrice(crop, unit, price, cur, market, p.country, cell)
        s.prices = listOf(PriceRow(crop, unit, price, cur, market, cell, Repo.today())) + (s.prices ?: emptyList())
        s.toast = tr("thanks"); Telemetry.event("price_report", "crop" to crop)
    }
    fun addPlot(name: String, crop: String, area: Double, planted: String) { repo?.addPlot(name, crop, area, planted); Telemetry.event("plot_add", "crop" to crop) }
    fun addLog(kind: String, note: String, qty: Double, money: Double, plotId: String, via: String) { repo?.addLog(kind, note, qty, "", money, plotId, via); Telemetry.event("log_add", "kind" to kind, "via" to via) }
    fun deleteLog(id: String) { repo?.deleteLog(id) }
    fun setTaskDone(id: String, done: Boolean) { repo?.setTaskDone(id, done) }

    // ---------- settings ----------
    fun downloadNano() {
        viewModelScope.launch {
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
            }.onFailure { s.downloadPct = null; fail(it, "download_nano") }
            s.nano = ai.modelStatus()
        }
    }
    fun setAlerts(on: Boolean) { Bg.setAlertsOn(ctx, on); s.alerts = on; Telemetry.event("alerts_toggle", "on" to if (on) "1" else "0") }
    fun testAlerts() {
        ctx.getSharedPreferences("conuco_bg", Application.MODE_PRIVATE).edit().remove("n_spray").remove("n_fungus").remove("n_seen").apply()
        Bg.runNow(ctx); s.toast = tr("s_alerts_test_ok")
    }

    override fun onCleared() { Firebase.auth.removeAuthStateListener(authListener); stopWatching(); super.onCleared() }
}
