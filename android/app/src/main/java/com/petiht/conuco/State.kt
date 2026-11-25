package com.petiht.conuco

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** App state observed by Compose. */
class AppState {
    var uid by mutableStateOf<String?>(null)
    var anonymous by mutableStateOf(false)
    var email by mutableStateOf<String?>(null)
    var profile by mutableStateOf<Profile?>(null)
    var profileLoaded by mutableStateOf(false)
    var online by mutableStateOf(true)
    var nano by mutableStateOf("unavailable")
    var plots by mutableStateOf(listOf<Plot>())
    var logs by mutableStateOf(listOf<LogEntry>())
    var diags by mutableStateOf(listOf<DiagRow>())
    var tasks by mutableStateOf(listOf<TaskRow>())
    var tasksLoaded by mutableStateOf(false)
    var analysis by mutableStateOf<Rules.Analysis?>(null)
    var wxFrom by mutableStateOf("")
    var prices by mutableStateOf<List<PriceRow>?>(null)
    var outbreaks by mutableStateOf<List<Outbreak>?>(null)
    var photo by mutableStateOf<Bitmap?>(null)
    var consent by mutableStateOf<Consent?>(null)
    var toast by mutableStateOf<String?>(null)
    var downloadPct by mutableStateOf<Int?>(null)
    var configSource by mutableStateOf("")
    var lang by mutableStateOf("es")
    var tab by mutableStateOf("today")
    var camera by mutableStateOf(false)
    var alerts by mutableStateOf(true)
}

class Consent(val reason: String, val confidence: Double, val answer: kotlinx.coroutines.CompletableDeferred<Boolean>)

/** Everything a screen can ask the activity to do. */
interface Actions {
    fun signInAnon()
    fun signInEmail(email: String, pass: String, create: Boolean)
    suspend fun searchPlace(q: String): List<Weather.Place>
    fun useGps(cb: (Weather.Place?) -> Unit)
    fun finishOnboarding(p: Profile, sample: Boolean)
    fun refreshWeather(force: Boolean)
    fun openCamera()
    fun pickPhoto()
    fun loadSample(file: String)
    fun dictate(onText: (String) -> Unit)
    suspend fun diagnose(crop: String, note: String, mode: String): Ai.DoctorResult
    fun saveDiagnosis(crop: String, d: Ai.Diagnosis, second: String)
    fun addTask(text: String)
    fun warnNeighbours(crop: String, d: Ai.Diagnosis)
    suspend fun brief(): Ai.Brief
    fun speak(text: String)
    fun stopSpeaking()
    fun addPrice(crop: String, unit: String, price: Double, market: String)
    fun addPlot(name: String, crop: String, area: Double, planted: String)
    fun addLog(kind: String, note: String, qty: Double, money: Double, plotId: String, via: String)
    fun deleteLog(id: String)
    fun setTaskDone(id: String, done: Boolean)
    suspend fun ask(q: String, fresh: Boolean): Ai.Reply
    fun saveProfile(p: Profile)
    fun downloadNano()
    fun linkEmail(email: String, pass: String)
    fun signOut()
    fun shareDiagnosis(crop: String, d: Ai.Diagnosis)
    fun setAlerts(on: Boolean)
    fun testAlerts()
    fun pinWidget()
    fun seedSample()
    val ai: Ai
}
