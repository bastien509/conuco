package com.petiht.conuco

import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Deterministic farm rules (same as the web app). They run offline, instantly, and are the source of every
 * number Conuco shows. The AI explains these facts; it never invents them.
 */
object Rules {
    data class Window(val start: String, val end: String, val hours: Int)
    data class Blocker(val code: String, val value: Double = 0.0, val limit: Double = 0.0)
    data class Day(val date: String, val tmax: Double, val tmin: Double, val rain: Double, val prob: Int, val code: Int)
    data class Now(val temp: Double, val rh: Int, val wind: Double, val prob: Int)
    data class Analysis(
        val now: Now?, val windows: List<Window>, val blockers: List<Blocker>, val okNow: Boolean,
        val cropUse: Double, val rainEff: Double, val deficit: Double, val irrigation: String,
        val wetHours: Int, val disease: String, val heatMax: Double?, val heatDays: Int, val stormDate: String?, val stormMm: Double,
        val days: List<Day>, val fetchedAt: Long,
    )

    private fun r1(d: Double) = (d * 10).roundToInt() / 10.0

    fun analyse(wx: JSONObject, crops: List<String>, maxWind: Double, now: LocalDateTime = LocalDateTime.now()): Analysis? {
        val h = wx.optJSONObject("hourly") ?: return null
        val time = h.getJSONArray("time")
        if (time.length() == 0) return null
        val key = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00"))
        var i0 = (0 until time.length()).firstOrNull { time.getString(it) >= key } ?: 0
        if (i0 < 0) i0 = 0
        data class H(val t: String, val hour: Int, val temp: Double, val rh: Double, val rain: Double, val prob: Int, val wind: Double)
        fun arr(n: String) = h.optJSONArray(n)
        val hrs = (0 until time.length()).map { i ->
            val t = time.getString(i)
            H(t, t.substring(11, 13).toInt(), arr("temperature_2m")?.optDouble(i, 0.0) ?: 0.0, arr("relative_humidity_2m")?.optDouble(i, 0.0) ?: 0.0,
                arr("precipitation")?.optDouble(i, 0.0) ?: 0.0, arr("precipitation_probability")?.optInt(i, 0) ?: 0, arr("wind_speed_10m")?.optDouble(i, 0.0) ?: 0.0)
        }
        val next48 = hrs.drop(i0).take(48)
        val past24 = hrs.subList(max(0, i0 - 24), i0)

        fun okHour(j: Int): Boolean {
            val x = next48[j]
            val after = next48.drop(j).take(6)
            return x.hour in 6..18 && x.wind <= maxWind && x.temp in 10.0..30.0 && x.rh < 92 &&
                after.sumOf { it.rain } < 0.3 && (after.maxOfOrNull { it.prob } ?: 0) < 40
        }
        val windows = mutableListOf<Window>()
        var start: String? = null
        var end = ""
        var n = 0
        next48.indices.forEach { j ->
            if (okHour(j)) { if (start == null) { start = next48[j].t; n = 0 }; end = next48[j].t; n++ }
            else if (start != null) { if (n >= 2) windows += Window(start!!, end, n); start = null }
        }
        if (start != null && n >= 2) windows += Window(start!!, end, n)

        val blockers = mutableListOf<Blocker>()
        val nowH = next48.firstOrNull()
        if (nowH != null) {
            if (nowH.wind > maxWind) blockers += Blocker("wind", r1(nowH.wind), maxWind)
            val rain6 = next48.take(6).sumOf { it.rain }
            if (rain6 >= 0.3) blockers += Blocker("rain", r1(rain6))
            if (nowH.temp > 30) blockers += Blocker("heat", r1(nowH.temp))
            if (nowH.hour < 6 || nowH.hour > 18) blockers += Blocker("dark")
        }

        val d = wx.optJSONObject("daily")
        val dTime = d?.optJSONArray("time")
        val dayList = if (d != null && dTime != null) (0 until dTime.length()).map { i ->
            Day(dTime.getString(i), d.optJSONArray("temperature_2m_max")?.optDouble(i, 0.0) ?: 0.0, d.optJSONArray("temperature_2m_min")?.optDouble(i, 0.0) ?: 0.0,
                d.optJSONArray("precipitation_sum")?.optDouble(i, 0.0) ?: 0.0, d.optJSONArray("precipitation_probability_max")?.optInt(i, 0) ?: 0,
                d.optJSONArray("weather_code")?.optInt(i, 0) ?: 0)
        } else emptyList()
        val et0 = d?.optJSONArray("et0_fao_evapotranspiration")
        val di0 = dayList.indexOfFirst { it.date >= key.substring(0, 10) }.coerceAtLeast(0)
        val days = dayList.drop(di0).take(5)
        val kc = if (crops.isEmpty()) 1.0 else crops.maxOf { Crops.get(it).kc }
        val cropUse = r1((di0 until minOf(di0 + 3, dayList.size)).sumOf { et0?.optDouble(it, 0.0) ?: 0.0 } * kc)
        val rainEff = r1(days.take(3).sumOf { it.rain } * 0.8)
        val deficit = r1(cropUse - rainEff)
        val wet = { x: H -> x.rh >= 90 && x.temp in 15.0..30.0 }
        val wetHours = past24.count(wet) + next48.count(wet)
        val hot = days.filter { it.tmax >= 35 }
        val storm = days.firstOrNull { it.rain >= 30 }
        return Analysis(
            nowH?.let { Now(r1(it.temp), it.rh.roundToInt(), r1(it.wind), it.prob) }, windows.take(3), blockers, blockers.isEmpty(),
            cropUse, rainEff, deficit, if (deficit > 12) "irrigate" else if (deficit > 5) "light" else "skip",
            wetHours, if (wetHours >= 14) "high" else if (wetHours >= 6) "medium" else "low",
            hot.maxOfOrNull { it.tmax }?.let { r1(it) }, hot.size, storm?.date, storm?.rain?.let { r1(it) } ?: 0.0,
            days, wx.optLong("_fetchedAt", System.currentTimeMillis()),
        )
    }

    // ---------- Symptom guide (no AI) ----------
    val SYMPTOMS = listOf("yellow", "spots", "powder", "streaks", "wilt", "holes", "curl", "mosaic", "rot", "insects")
    private val HINTS = mapOf(
        "yellow" to listOf("nutrient", "water_stress", "mosaic_virus"),
        "spots" to listOf("early_blight", "anthracnose", "angular_leaf_spot", "bacterial_spot", "brown_spot"),
        "powder" to listOf("powdery_mildew", "rust", "coffee_rust"),
        "streaks" to listOf("black_sigatoka", "blast"),
        "wilt" to listOf("fusarium_wilt", "wilt", "water_stress", "root_rot"),
        "holes" to listOf("fall_armyworm", "leaf_miner", "pod_borer"),
        "curl" to listOf("leaf_curl", "whitefly", "mites", "thrips"),
        "mosaic" to listOf("mosaic_virus", "golden_mosaic"),
        "rot" to listOf("late_blight", "black_pod", "heart_rot", "root_rot", "monilia"),
        "insects" to listOf("whitefly", "thrips", "mites", "fall_armyworm", "weevil", "berry_borer"),
    )
    fun symptomGuide(crop: String, ticked: Set<String>): List<String> {
        val watch = Crops.get(crop).watch.toSet()
        val score = mutableMapOf<String, Int>()
        ticked.forEach { s -> HINTS[s]?.forEachIndexed { i, p -> score[p] = (score[p] ?: 0) + (3 - minOf(i, 2)) + if (p in watch) 3 else 0 } }
        return score.entries.sortedByDescending { it.value }.take(3).map { it.key }
    }

    // ---------- Safety: no chemical doses, no banned products ----------
    private val DOSE = Regex("""\b\d+(?:[.,]\d+)?\s*(?:ml|mL|cc|cm3|g|gr|gramos?|grams?|kg|l|lt|litros?|liters?|litres?|oz|onzas?|cucharad[ao]s?|tbsp|tsp|spoons?)\b\s*(?:/|por|per|en|in|de|of|each|cada)\s*[\wáéíóú]+""", RegexOption.IGNORE_CASE)
    private val BANNED = Regex("""\b(paraquat|endosulf[aá]n|metamidof[oó]s|methamidophos|carbofur[aá]n|monocrotof[oó]s|monocrotophos|aldicarb|DDT|lindan[oe])\b""", RegexOption.IGNORE_CASE)
    fun safe(text: String, lang: String): Pair<String, Boolean> {
        var changed = false
        var t = DOSE.replace(text) { changed = true; if (lang == "es") "(dosis: siga la etiqueta)" else "(dose: follow the label)" }
        t = BANNED.replace(t) { changed = true; if (lang == "es") "[producto prohibido]" else "[banned product]" }
        return t to changed
    }
}
