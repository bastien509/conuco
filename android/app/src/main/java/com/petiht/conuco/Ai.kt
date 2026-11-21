package com.petiht.conuco

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.InferenceSource
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.ondevice.DownloadStatus
import com.google.firebase.ai.ondevice.FirebaseAIOnDevice
import com.google.firebase.ai.ondevice.OnDeviceModelStatus
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Conuco's hybrid AI engine on Android (Firebase AI Logic + Gemini Nano through ML Kit / AICore).
 * Device first. The cloud only when it adds something, and plant photos only after the farmer says yes.
 * Gemini Nano on Android is validated for English and short answers, so the on-device prompts ask for a
 * compact line format that a small model fills reliably; the cloud uses a JSON schema.
 */
@OptIn(PublicPreviewAPI::class)
class Ai(private val context: Context) {

    // Remote Config (in-app defaults first).
    var cloudModel = "gemini-3.5-flash-lite"
    var deviceLanguages = listOf("en")
    var confidenceFloor = 0.65
    var cloudPhotos = true
    var grounding = false // Google Search grounding needs a paid Gemini API tier; Remote Config turns it on
    var outbreakRadiusKm = 30.0
    var outbreakAlertMin = 2
    var priceWindowDays = 14
    var sprayMaxWind = 15.0
    var configSource = "app defaults"

    suspend fun loadRemoteConfig() {
        try {
            val rc = Firebase.remoteConfig
            rc.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = 1800 }).await()
            rc.setDefaultsAsync(mapOf(
                "conuco_cloud_model" to cloudModel, "conuco_device_languages_android" to "en", "conuco_confidence_floor" to "0.65",
                "conuco_cloud_photos_allowed" to "true", "conuco_grounding_enabled" to "false", "conuco_outbreak_radius_km" to "30",
                "conuco_outbreak_alert_min" to "2", "conuco_price_window_days" to "14", "conuco_spray_max_wind_kmh" to "15",
            )).await()
            rc.fetchAndActivate().await()
            rc.getString("conuco_cloud_model").takeIf { Regex("^gemini-[\\w.-]+$").matches(it) }?.let { cloudModel = it }
            deviceLanguages = rc.getString("conuco_device_languages_android").split(",").map { it.trim() }.filter { it.length == 2 }.ifEmpty { listOf("en") }
            rc.getDouble("conuco_confidence_floor").takeIf { it in 0.3..0.95 }?.let { confidenceFloor = it }
            cloudPhotos = rc.getBoolean("conuco_cloud_photos_allowed")
            grounding = rc.getBoolean("conuco_grounding_enabled")
            rc.getDouble("conuco_outbreak_radius_km").takeIf { it in 5.0..150.0 }?.let { outbreakRadiusKm = it }
            rc.getLong("conuco_outbreak_alert_min").toInt().takeIf { it in 1..20 }?.let { outbreakAlertMin = it }
            rc.getLong("conuco_price_window_days").toInt().takeIf { it in 3..60 }?.let { priceWindowDays = it }
            rc.getDouble("conuco_spray_max_wind_kmh").takeIf { it in 5.0..30.0 }?.let { sprayMaxWind = it }
            configSource = "Firebase Remote Config"
        } catch (e: Exception) {
            configSource = "app defaults (offline)"
        }
    }

    suspend fun modelStatus(): String = try {
        when (FirebaseAIOnDevice.checkStatus()) {
            OnDeviceModelStatus.AVAILABLE -> "available"
            OnDeviceModelStatus.DOWNLOADABLE -> "downloadable"
            OnDeviceModelStatus.DOWNLOADING -> "downloading"
            else -> "unavailable"
        }
    } catch (e: Throwable) {
        "unavailable"
    }

    fun download(): Flow<DownloadStatus> = FirebaseAIOnDevice.download()

    fun online(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // ---------- models ----------
    private val diagSchema = Schema.obj(
        mapOf(
            "is_plant" to Schema.boolean(), "healthy" to Schema.boolean(), "problem" to Schema.string(),
            "category" to Schema.enumeration(Crops.CATEGORIES), "confidence" to Schema.double(),
            "severity" to Schema.enumeration(listOf("low", "medium", "high")),
            "signs" to Schema.array(Schema.string()), "actions_now" to Schema.array(Schema.string()),
            "prevent" to Schema.array(Schema.string()), "see_expert" to Schema.boolean(),
        ),
        optionalProperties = listOf("prevent", "see_expert"),
    )
    private val briefSchema = Schema.obj(mapOf("headline" to Schema.string(), "steps" to Schema.array(Schema.string())))

    private fun model(mode: InferenceMode, schema: Schema?, grounded: Boolean = false): GenerativeModel =
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = cloudModel,
            generationConfig = generationConfig {
                if (schema != null) { responseMimeType = "application/json"; responseSchema = schema }
                temperature = 0.2f
                maxOutputTokens = 900
            },
            tools = if (grounded) listOf(com.google.firebase.ai.type.Tool.googleSearch()) else null,
            onDeviceConfig = OnDeviceConfig(mode = mode, maxOutputTokens = 256, temperature = 0.1f),
        )

    data class Answer(val text: String, val source: String, val ms: Long, val sources: List<Pair<String, String>> = emptyList())

    private suspend fun run(mode: InferenceMode, schema: Schema?, feature: String, grounded: Boolean = false, parts: Content.Builder.() -> Unit): Answer {
        val t0 = System.currentTimeMillis()
        val res = withTimeout(120_000) { model(mode, schema, grounded).generateContent(content { parts() }) }
        val ms = System.currentTimeMillis() - t0
        val src = if (res.inferenceSource == InferenceSource.ON_DEVICE) "on_device" else "in_cloud"
        runCatching { Firebase.analytics.logEvent("ai_call") { param("feature", feature); param("source", src); param("ms", ms); param("mode", mode.toString()) } }
        val sources = runCatching {
            res.candidates.firstOrNull()?.groundingMetadata?.groundingChunks?.mapNotNull { ch -> ch.web?.let { (it.title ?: it.uri ?: "") to (it.uri ?: "") } }?.filter { it.second.isNotBlank() }?.take(5)
        }.getOrNull() ?: emptyList()
        return Answer(res.text ?: "", src, ms, sources)
    }

    // ---------- crop doctor ----------
    data class Diagnosis(
        val isPlant: Boolean, val healthy: Boolean, val problem: String, val problemKey: String, val category: String,
        val confidence: Double, val severity: String, val signs: List<String>, val actions: List<String>, val prevent: List<String>,
        val seeExpert: Boolean, val filtered: Boolean, val source: String, val ms: Long,
    )
    data class DoctorResult(val first: Diagnosis?, val second: Diagnosis?, val trace: List<String>, val symptoms: Boolean)

    private fun devicePrompt(crop: String, note: String) = buildString {
        append("You are a plant doctor for small farmers. Look at the photo of a ${Crops.name(crop, "en").lowercase()} plant.\n")
        val watch = Crops.get(crop).watch.joinToString(", ") { Crops.problemName(it, "en") }
        if (watch.isNotBlank()) append("Common problems of this crop: $watch.\n")
        if (note.isNotBlank()) append("Farmer note: ${note.take(200)}\n")
        append("Answer ONLY with these lines, in English, short:\n")
        append("PROBLEM|<problem name or Healthy>|<fungus, bacteria, virus, pest, nutrient, water, healthy or unknown>|<confidence 0-100>|<low, medium or high>\n")
        append("SIGN|<what you see>\nSIGN|<what you see>\nDO|<free practical step>\nDO|<free practical step>\nDO|<free practical step>\n")
        append("If it is not a plant write PROBLEM|Not a plant|unknown|0|low. Never write chemical doses.")
    }
    private fun cloudPrompt(crop: String, note: String, lang: String) = buildString {
        append("You are Conuco, a careful plant-health assistant for small farmers. The farmer says this is ${Crops.name(crop, "en")}.\n")
        val watch = Crops.get(crop).watch.joinToString(", ") { Crops.problemName(it, "en") }
        if (watch.isNotBlank()) append("Common problems of this crop: $watch. Only name one if the photo shows its signs.\n")
        if (note.isNotBlank()) append("Farmer's note: \"${note.take(300)}\"\n")
        append("confidence is your honest probability 0..1 (under 0.5 when unsure). actions_now: free practical steps first. ")
        append("Never write chemical doses, mixing ratios or brand names; if a pesticide may be needed say to use a product registered for this crop and follow the label. ")
        append("see_expert true when severe or unsure. Write all text in ${if (lang == "es") "Spanish" else "English"}.")
    }

    private fun parseDevice(text: String, ms: Long): Diagnosis {
        var problem = ""; var cat = "unknown"; var conf = 0.0; var sev = "medium"
        val signs = mutableListOf<String>(); val acts = mutableListOf<String>()
        text.lines().map { it.trim().trim('`', '*', '-', ' ') }.forEach { line ->
            val p = line.split('|').map { it.trim() }
            when (p.firstOrNull()?.uppercase()) {
                "PROBLEM" -> {
                    problem = p.getOrNull(1).orEmpty().take(80)
                    cat = p.getOrNull(2)?.lowercase()?.takeIf { it in Crops.CATEGORIES } ?: "unknown"
                    conf = (p.getOrNull(3)?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull() ?: 0.0).let { if (it > 1) it / 100 else it }.coerceIn(0.0, 1.0)
                    sev = p.getOrNull(4)?.lowercase()?.takeIf { it in listOf("low", "medium", "high") } ?: "medium"
                }
                "SIGN" -> p.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { if (signs.size < 4) signs += it }
                "DO" -> p.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { if (acts.size < 4) acts += it }
            }
        }
        return finish(problem, cat, conf, sev, signs, acts, emptyList(), false, "en", "on_device", ms)
    }

    private fun parseCloud(text: String, lang: String, ms: Long): Diagnosis {
        val j = JSONObject(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```"))
        fun list(k: String) = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
        val healthy = j.optBoolean("healthy")
        val d = finish(j.optString("problem"), if (healthy) "healthy" else j.optString("category", "unknown"), j.optDouble("confidence", 0.0),
            j.optString("severity", "medium"), list("signs"), list("actions_now"), list("prevent"), j.optBoolean("see_expert"), lang, "in_cloud", ms)
        return if (!j.optBoolean("is_plant", true)) d.copy(isPlant = false) else d
    }

    private fun finish(problem: String, cat: String, conf: Double, sev: String, signs: List<String>, acts: List<String>, prevent: List<String>, expert: Boolean, lang: String, source: String, ms: Long): Diagnosis {
        var filtered = false
        fun s(x: String) = Rules.safe(x, lang).also { if (it.second) filtered = true }.first
        val healthy = cat == "healthy" || problem.equals("healthy", true)
        val c = conf.coerceIn(0.0, 1.0)
        val notPlant = problem.contains("not a plant", true)
        return Diagnosis(
            !notPlant, healthy, s(problem).ifBlank { if (healthy) "Healthy" else "" }, Crops.matchProblem(problem),
            if (healthy) "healthy" else if (cat in Crops.CATEGORIES) cat else "unknown", (c * 100).toInt() / 100.0, sev,
            signs.take(4).map(::s), acts.take(4).map(::s), prevent.take(3).map(::s), expert || sev == "high" || c < 0.5, filtered, source, ms,
        )
    }

    fun agree(a: Diagnosis?, b: Diagnosis?): Boolean {
        if (a == null || b == null) return false
        if (a.healthy && b.healthy) return true
        if (a.problemKey != "other" && a.problemKey == b.problemKey) return true
        return a.category == b.category && a.category != "unknown"
    }

    /** mode: private | smart | cloud. askConsent shows the dialog and returns the farmer's answer. */
    suspend fun diagnose(photo: Bitmap, crop: String, note: String, lang: String, mode: String, askConsent: suspend (String, Double) -> Boolean): DoctorResult {
        val trace = mutableListOf<String>()
        val es = lang == "es"
        trace += if (es) "Foto preparada en el teléfono: ${photo.width}×${photo.height}, sin GPS ni datos EXIF." else "Photo prepared on the phone: ${photo.width}×${photo.height}, no GPS or EXIF data."
        val status = modelStatus()
        var first: Diagnosis? = null
        if (mode != "cloud") {
            if (status == "available") {
                trace += if (es) "Gemini Nano está listo en este teléfono → ONLY_ON_DEVICE." else "Gemini Nano is ready on this phone → ONLY_ON_DEVICE."
                if (lang !in deviceLanguages) trace += if (es) "Gemini Nano en Android responde en inglés (Remote Config)." else "Gemini Nano on Android answers in English (Remote Config)."
                try {
                    val a = run(InferenceMode.ONLY_ON_DEVICE, null, "diagnose") { image(photo); text(devicePrompt(crop, note)) }
                    first = parseDevice(a.text, a.ms)
                    trace += if (es) "Respondió en el teléfono en ${a.ms / 1000.0} s (confianza ${(first.confidence * 100).toInt()}%)." else "Answered on the phone in ${a.ms / 1000.0} s (confidence ${(first.confidence * 100).toInt()}%)."
                } catch (e: Throwable) {
                    trace += (if (es) "Falló el modelo local: " else "On-device model failed: ") + (e.message ?: e.javaClass.simpleName).take(90)
                }
            } else trace += if (es) "No hay Gemini Nano en este teléfono ($status)." else "No Gemini Nano on this phone ($status)."
        }
        if (mode == "private") {
            trace += if (es) "Modo privado: nada sale del teléfono." else "Private mode: nothing leaves the phone."
            return DoctorResult(first, null, trace, first == null)
        }
        val unsure = first != null && (first.confidence < confidenceFloor || first.category == "unknown" || !first.isPlant)
        if (mode != "cloud" && first != null && !unsure) {
            trace += if (es) "Confianza suficiente (mínimo ${(confidenceFloor * 100).toInt()}% de Remote Config): no hace falta la nube." else "Confident enough (floor ${(confidenceFloor * 100).toInt()}% from Remote Config): no cloud needed."
            return DoctorResult(first, null, trace, false)
        }
        if (unsure) trace += if (es) "Confianza bajo el mínimo de ${(confidenceFloor * 100).toInt()}% → se sugiere segunda opinión." else "Confidence under the ${(confidenceFloor * 100).toInt()}% floor → second opinion suggested."
        if (!online()) { trace += if (es) "Sin conexión: se queda en el teléfono." else "No connection: staying on the phone."; return DoctorResult(first, null, trace, first == null) }
        if (!cloudPhotos) { trace += if (es) "Fotos a la nube apagadas (Remote Config)." else "Cloud photos switched off (Remote Config)."; return DoctorResult(first, null, trace, first == null) }
        val yes = askConsent(if (first != null) "second_opinion" else if (mode == "cloud") "cloud_first" else "no_device", first?.confidence ?: 0.0)
        runCatching { Firebase.analytics.logEvent("cloud_consent") { param("answer", if (yes) "yes" else "no") } }
        if (!yes) { trace += if (es) "Dijiste que no: no se envió nada." else "You said no: nothing was sent."; return DoctorResult(first, null, trace, first == null) }
        trace += if (es) "Dijiste que sí → ONLY_IN_CLOUD, verificado con App Check (Play Integrity)." else "You said yes → ONLY_IN_CLOUD, verified by App Check (Play Integrity)."
        return try {
            val a = run(InferenceMode.ONLY_IN_CLOUD, diagSchema, "diagnose_cloud") { image(photo); text(cloudPrompt(crop, note, lang)) }
            val second = parseCloud(a.text, lang, a.ms)
            trace += if (es) "La nube respondió en ${a.ms / 1000.0} s." else "Cloud answered in ${a.ms / 1000.0} s."
            if (first != null) trace += if (agree(first, second)) (if (es) "Teléfono y nube coinciden." else "Phone and cloud agree.") else (if (es) "Teléfono y nube no coinciden." else "Phone and cloud disagree.")
            DoctorResult(first, second, trace, false)
        } catch (e: Throwable) {
            trace += (if (es) "Falló la nube: " else "Cloud failed: ") + (e.message ?: e.javaClass.simpleName).take(120)
            DoctorResult(first, null, trace, first == null)
        }
    }

    // ---------- brief + ask ----------
    data class Brief(val headline: String, val steps: List<String>, val source: String, val ms: Long, val trace: List<String>)

    suspend fun brief(facts: List<String>, lang: String): Brief {
        val trace = mutableListOf<String>()
        val es = lang == "es"
        val status = modelStatus()
        val devOk = status == "available"
        val outEs = es && !(devOk && "es" !in deviceLanguages)
        trace += if (es) "La IA solo recibe ${facts.size} datos de las reglas: sin ubicación ni nombres." else "The AI gets only ${facts.size} rule facts: no location, no names."
        return if (devOk && online().not() || devOk) {
            trace += if (es) "Gemini Nano en el teléfono (PREFER_ON_DEVICE)." else "Gemini Nano on the phone (PREFER_ON_DEVICE)."
            val a = run(InferenceMode.PREFER_ON_DEVICE, null, "brief") {
                text("Facts about a small farm today:\n${facts.joinToString("\n") { "- $it" }}\nWrite one short headline line, then 3 lines starting with STEP| giving the most important actions. Use only these facts. English. No chemical doses.")
            }
            val lines = a.text.lines().map { it.trim() }.filter { it.isNotBlank() }
            val steps = lines.filter { it.startsWith("STEP", true) }.map { it.substringAfter('|').trim() }.take(3)
            val head = lines.firstOrNull { !it.startsWith("STEP", true) }?.trim('*', '#', ' ') ?: ""
            trace += (if (a.source == "on_device") (if (es) "En el teléfono" else "On the phone") else (if (es) "En la nube" else "In the cloud")) + " · ${a.ms / 1000.0} s"
            Brief(Rules.safe(head, "en").first, steps.map { Rules.safe(it, "en").first }, a.source, a.ms, trace)
        } else {
            if (!online()) throw IllegalStateException(if (es) "Necesita el modelo local o conexión." else "Needs the on-device model or a connection.")
            trace += if (es) "Sin Gemini Nano: nube (PREFER_ON_DEVICE cae a la nube)." else "No Gemini Nano: cloud (PREFER_ON_DEVICE falls back)."
            val a = run(InferenceMode.PREFER_ON_DEVICE, briefSchema, "brief") {
                text("You are Conuco, a farm assistant. Use ONLY these facts:\n${facts.joinToString("\n") { "- $it" }}\nAnswer in ${if (outEs) "Spanish" else "English"}: headline (max 12 words) and up to 3 short steps. No chemical doses.")
            }
            val j = JSONObject(a.text)
            val steps = j.optJSONArray("steps")?.let { s -> (0 until s.length()).map { s.optString(it) } } ?: emptyList()
            trace += (if (es) "Nube · " else "Cloud · ") + "${a.ms / 1000.0} s"
            Brief(Rules.safe(j.optString("headline"), lang).first, steps.take(3).map { Rules.safe(it, lang).first }, a.source, a.ms, trace)
        }
    }

    data class Reply(val text: String, val source: String, val ms: Long, val grounded: Boolean, val sources: List<Pair<String, String>>, val trace: List<String>, val filtered: Boolean)

    suspend fun ask(question: String, context: String, lang: String, fresh: Boolean): Reply {
        val trace = mutableListOf<String>()
        val es = lang == "es"
        val online = online()
        val groundedOk = fresh && online && grounding
        val devOk = modelStatus() == "available"
        val outLang = if (groundedOk || !devOk || lang in deviceLanguages) lang else "en"
        val prompt = "You are Conuco, a practical assistant for small farmers. Answer in plain words, at most 100 words. " +
            "Prefer low-cost safe practices. Never give pesticide doses; say to follow the label and local extension advice.\n" +
            (if (context.isNotBlank()) "Farmer context: $context\n" else "") +
            "Answer in ${if (outLang == "es") "Spanish" else "English"}.\nQuestion: ${question.take(500)}"
        var grounded = groundedOk
        val g = if (groundedOk) {
            trace += if (es) "Pediste respuesta actual → nube con Búsqueda de Google." else "Fresh answer requested → cloud with Google Search grounding."
            try { run(InferenceMode.ONLY_IN_CLOUD, null, "ask_grounded", grounded = true) { text(prompt) } } catch (e: Throwable) {
                trace += if (es) "La Búsqueda de Google no está disponible en este plan: se responde sin ella." else "Google Search grounding is not available on this plan: answering without it."
                grounded = false; null
            }
        } else null
        val a = g ?: kotlin.run {
            if (!online && !devOk) throw IllegalStateException(if (es) "Necesita el modelo local o conexión." else "Needs the on-device model or a connection.")
            trace += if (online) "PREFER_ON_DEVICE" else "ONLY_ON_DEVICE (offline)"
            if (devOk && outLang != lang) trace += if (es) "Gemini Nano en Android responde en inglés." else "Gemini Nano on Android answers in English."
            run(if (online) InferenceMode.PREFER_ON_DEVICE else InferenceMode.ONLY_ON_DEVICE, null, "ask") { text(prompt) }
        }
        trace += (if (a.source == "on_device") (if (es) "En el teléfono" else "On the phone") else (if (es) "En la nube" else "In the cloud")) + " · ${a.ms / 1000.0} s"
        val (txt, changed) = Rules.safe(a.text.trim(), outLang)
        return Reply(txt, a.source, a.ms, grounded, a.sources, trace, changed)
    }
}
