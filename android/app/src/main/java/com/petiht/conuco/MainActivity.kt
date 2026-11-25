package com.petiht.conuco

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.petiht.conuco.ui.AppShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Thin host. App logic and data live in [ConucoViewModel]; this class adds only what needs an Activity:
 * runtime permissions, the camera and photo picker, speech input, text-to-speech, the share sheet,
 * pinning the widget, and deep links (widget, shortcuts, notifications).
 */
class MainActivity : ComponentActivity(), Actions {
    private val vm: ConucoViewModel by viewModels()
    private val s get() = vm.s
    override val ai: Ai get() = vm.ai

    private var photoUri: Uri? = null
    private var onDictated: ((String) -> Unit)? = null
    private var gpsCb: ((Weather.Place?) -> Unit)? = null
    private var tts: TextToSpeech? = null

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) photoUri?.let { s.photo = Photos.load(this, it) } }
    private val pickPhotoL = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { s.photo = Photos.load(this, it) } }
    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { onDictated?.invoke(it) }
        onDictated = null
    }
    private val camPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) s.camera = true else legacyCamera() }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> Telemetry.event("notif_permission", "granted" to if (ok) "1" else "0") }
    private val locPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) readLocation() else gpsCb?.invoke(null) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tts = TextToSpeech(this) { }
        setContent { AppShell(s, this, vm.chat, onSignedIn = ::askNotifications) }
        if (savedInstanceState == null) handleIntent(intent)
    }

    /** Deep links from the widget, app shortcuts and notifications: conuco://tab/{today|doctor|camera|market|map|farm|ask}. */
    private fun handleIntent(i: Intent?) {
        val t = i?.data?.takeIf { it.scheme == "conuco" }?.lastPathSegment ?: i?.getStringExtra("tab") ?: return
        if (t == "camera") { s.tab = "doctor"; openCamera() }
        else if (t in setOf("today", "doctor", "market", "map", "farm", "ask")) s.tab = t
        Telemetry.event("deep_link", "tab" to t)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    // ---------- Activity-only actions ----------
    override fun useGps(cb: (Weather.Place?) -> Unit) {
        gpsCb = cb
        if (granted(Manifest.permission.ACCESS_COARSE_LOCATION)) readLocation() else locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
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
                JSONObject(withContext(Dispatchers.IO) { Weather.http("https://geocoding-api.open-meteo.com/v1/reverse?latitude=$lat&longitude=$lng&language=${s.lang}") })
                    .optJSONArray("results")?.optJSONObject(0)
            }.getOrNull()
            gpsCb?.invoke(Weather.Place(near?.optString("name") ?: "$lat, $lng", near?.optString("admin1") ?: "", near?.optString("country_code") ?: "", lat, lng))
        }
    }

    override fun openCamera() { if (granted(Manifest.permission.CAMERA)) s.camera = true else camPerm.launch(Manifest.permission.CAMERA) }
    /** Fallback when the camera permission is refused: the system camera app writes into our cache, no permission needed. */
    private fun legacyCamera() {
        val f = File(File(cacheDir, "photos").apply { mkdirs() }, "leaf.jpg")
        photoUri = FileProvider.getUriForFile(this, "com.petiht.conuco.files", f)
        takePhoto.launch(photoUri!!)
    }
    override fun pickPhoto() = pickPhotoL.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    override fun dictate(onText: (String) -> Unit) {
        onDictated = onText
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (s.lang == "es") "es-ES" else "en-US")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        runCatching { speech.launch(i); Telemetry.event("voice_input", "prefer_offline" to "1") }.onFailure { s.toast = tr("err", "m" to "speech") }
    }
    override fun speak(text: String) {
        tts?.language = Locale.forLanguageTag(if (s.lang == "es") "es-ES" else "en-US")
        tts?.speak(text.take(1200), TextToSpeech.QUEUE_FLUSH, null, "conuco"); Telemetry.event("speak")
    }
    override fun stopSpeaking() { tts?.stop() }

    private fun askNotifications() {
        val sp = getSharedPreferences("conuco_bg", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && !sp.getBoolean("asked_notif", false) && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            sp.edit().putBoolean("asked_notif", true).apply()
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    override fun setAlerts(on: Boolean) {
        vm.setAlerts(on)
        if (on && Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    override fun pinWidget() {
        val wm = getSystemService(android.appwidget.AppWidgetManager::class.java)
        if (wm?.isRequestPinAppWidgetSupported == true) { wm.requestPinAppWidget(ComponentName(this, TodayWidgetReceiver::class.java), null, null); Telemetry.event("widget_pin") }
        else s.toast = tr("s_widget")
    }

    /** Android share sheet: the redrawn photo (no EXIF, no GPS) plus a short text any farmer or agronomist can read. */
    override fun shareDiagnosis(crop: String, d: Ai.Diagnosis) {
        val text = tr("sh_text", "p" to d.problem.ifBlank { tr("cat_${d.category}") }, "c" to Crops.name(crop, s.lang), "pct" to (d.confidence * 100).toInt(),
            "src" to tr(if (d.source == "on_device") "sh_device" else "sh_cloud"), "a" to d.actions.take(3).joinToString("; ")).trim()
        val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, "Conuco")
        val bmp = s.photo
        if (bmp != null) runCatching {
            val f = File(File(cacheDir, "photos").apply { mkdirs() }, "share.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            send.setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, "com.petiht.conuco.files", f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { send.setType("text/plain") } else send.setType("text/plain")
        startActivity(Intent.createChooser(send, tr("share")))
        Telemetry.event("share", "content_type" to "diagnosis")
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    // ---------- Delegated to the ViewModel ----------
    override fun signInAnon() = vm.signInAnon()
    override fun signInEmail(email: String, pass: String, create: Boolean) = vm.signInEmail(email, pass, create)
    override suspend fun searchPlace(q: String) = vm.searchPlace(q)
    override fun finishOnboarding(p: Profile, sample: Boolean) = vm.finishOnboarding(p, sample)
    override fun refreshWeather(force: Boolean) = vm.refreshWeather(force)
    override fun loadSample(file: String) = vm.loadSample(file)
    override suspend fun diagnose(crop: String, note: String, mode: String) = vm.diagnose(crop, note, mode)
    override fun saveDiagnosis(crop: String, d: Ai.Diagnosis, second: String) = vm.saveDiagnosis(crop, d, second)
    override fun addTask(text: String) = vm.addTask(text)
    override fun warnNeighbours(crop: String, d: Ai.Diagnosis) = vm.warnNeighbours(crop, d)
    override suspend fun brief() = vm.brief()
    override fun addPrice(crop: String, unit: String, price: Double, market: String) = vm.addPrice(crop, unit, price, market)
    override fun addPlot(name: String, crop: String, area: Double, planted: String) = vm.addPlot(name, crop, area, planted)
    override fun addLog(kind: String, note: String, qty: Double, money: Double, plotId: String, via: String) = vm.addLog(kind, note, qty, money, plotId, via)
    override fun deleteLog(id: String) = vm.deleteLog(id)
    override fun setTaskDone(id: String, done: Boolean) = vm.setTaskDone(id, done)
    override suspend fun ask(q: String, fresh: Boolean) = vm.ask(q, fresh)
    override fun saveProfile(p: Profile) = vm.saveProfile(p)
    override fun downloadNano() = vm.downloadNano()
    override fun linkEmail(email: String, pass: String) = vm.linkEmail(email, pass)
    override fun signOut() = vm.signOut()
    override fun seedSample() = vm.seedSample()
    override fun testAlerts() = vm.testAlerts()

    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}
