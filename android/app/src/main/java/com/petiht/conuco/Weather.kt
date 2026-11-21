package com.petiht.conuco

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/** Open-Meteo forecast (free, no key). Only a ~1 km area is sent. The last forecast is kept for offline use. */
class Weather(context: Context) {
    private val prefs = context.getSharedPreferences("conuco_wx", Context.MODE_PRIVATE)

    data class Result(val json: JSONObject, val from: String)
    data class Place(val name: String, val admin: String, val country: String, val lat: Double, val lng: Double)

    fun cached(lat: Double, lng: Double): JSONObject? {
        val k = key(lat, lng)
        return prefs.getString(k, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    suspend fun get(lat: Double, lng: Double, online: Boolean, force: Boolean = false): Result? = withContext(Dispatchers.IO) {
        val cached = cached(lat, lng)
        if (cached != null && !force && System.currentTimeMillis() - cached.optLong("_fetchedAt") < 3_600_000) return@withContext Result(cached, "cache")
        if (!online) return@withContext cached?.let { Result(it, "offline-cache") }
        val q = "latitude=${coarse(lat)}&longitude=${coarse(lng)}&timezone=auto&past_days=1&forecast_days=5&wind_speed_unit=kmh" +
            "&hourly=temperature_2m,relative_humidity_2m,precipitation,precipitation_probability,wind_speed_10m,et0_fao_evapotranspiration" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,et0_fao_evapotranspiration,wind_speed_10m_max,weather_code"
        try {
            val j = JSONObject(http("https://api.open-meteo.com/v1/forecast?$q"))
            j.put("_fetchedAt", System.currentTimeMillis())
            prefs.edit().putString(key(lat, lng), j.toString()).apply()
            Result(j, "network")
        } catch (e: Exception) {
            cached?.let { Result(it, "offline-cache") }
        }
    }

    suspend fun search(q: String, lang: String): List<Place> = withContext(Dispatchers.IO) {
        runCatching {
            val j = JSONObject(http("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(q, "UTF-8")}&count=6&language=${if (lang == "es") "es" else "en"}&format=json"))
            val a = j.optJSONArray("results") ?: return@runCatching emptyList()
            (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                Place(p.optString("name"), p.optString("admin1"), p.optString("country_code"), coarse(p.getDouble("latitude")), coarse(p.getDouble("longitude")))
            }
        }.getOrDefault(emptyList())
    }

    private fun key(lat: Double, lng: Double) = "wx_${coarse(lat)}_${coarse(lng)}"

    companion object {
        fun coarse(n: Double, d: Int = 2): Double { val f = Math.pow(10.0, d.toDouble()); return (n * f).roundToInt() / f }
        fun http(url: String): String {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 15000
            c.setRequestProperty("User-Agent", "Conuco-Android")
            return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
        }
        fun sky(code: Int) = when {
            code == 0 -> "clear"; code <= 3 -> "cloudy"; code <= 48 -> "fog"
            code <= 67 || code in 80..82 -> "rain"; code >= 95 -> "storm"; else -> "cloudy"
        }
    }
}
