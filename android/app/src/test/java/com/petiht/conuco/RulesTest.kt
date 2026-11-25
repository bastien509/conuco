package com.petiht.conuco

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** The field decisions are deterministic rules, so they are tested with synthetic Open-Meteo forecasts. */
class RulesTest {
    private val start = LocalDateTime.of(2026, 11, 24, 0, 0)
    private val now = LocalDateTime.of(2026, 11, 25, 8, 0)

    data class Hour(val temp: Double = 25.0, val rh: Double = 60.0, val rain: Double = 0.0, val prob: Int = 0, val wind: Double = 5.0)

    /** 5 days of hourly data from [start], plus daily rows. [hour] decides each hour's weather. */
    private fun forecast(et0: Double = 5.0, dailyRain: (Int) -> Double = { 0.0 }, hour: (LocalDateTime) -> Hour = { Hour() }): JSONObject {
        val t = JSONArray(); val temp = JSONArray(); val rh = JSONArray(); val rain = JSONArray(); val prob = JSONArray(); val wind = JSONArray()
        for (i in 0 until 24 * 5) {
            val at = start.plusHours(i.toLong()); val h = hour(at)
            t.put(at.toString().take(16)); temp.put(h.temp); rh.put(h.rh); rain.put(h.rain); prob.put(h.prob); wind.put(h.wind)
        }
        val dt = JSONArray(); val tmax = JSONArray(); val tmin = JSONArray(); val dr = JSONArray(); val dp = JSONArray(); val e = JSONArray(); val code = JSONArray()
        for (d in 0 until 5) { dt.put(start.toLocalDate().plusDays(d.toLong()).toString()); tmax.put(31.0); tmin.put(22.0); dr.put(dailyRain(d)); dp.put(0); e.put(et0); code.put(1) }
        return JSONObject()
            .put("hourly", JSONObject().put("time", t).put("temperature_2m", temp).put("relative_humidity_2m", rh).put("precipitation", rain)
                .put("precipitation_probability", prob).put("wind_speed_10m", wind))
            .put("daily", JSONObject().put("time", dt).put("temperature_2m_max", tmax).put("temperature_2m_min", tmin).put("precipitation_sum", dr)
                .put("precipitation_probability_max", dp).put("et0_fao_evapotranspiration", e).put("weather_code", code))
    }

    @Test fun calmDryDayGivesDaylightSprayWindows() {
        val a = Rules.analyse(forecast(), listOf("banana"), 15.0, now)!!
        assertTrue(a.okNow)
        assertTrue(a.windows.isNotEmpty())
        a.windows.forEach { w ->
            val h0 = LocalDateTime.parse(w.start).hour; val h1 = LocalDateTime.parse(w.end).hour
            assertTrue("window inside daylight: $w", h0 >= 6 && h1 <= 18)
            assertTrue(w.hours >= 2)
        }
        assertEquals("low", a.disease)
    }

    @Test fun strongWindBlocksSprayingAllDay() {
        val a = Rules.analyse(forecast { Hour(wind = 25.0) }, listOf("banana"), 15.0, now)!!
        assertTrue(a.windows.isEmpty())
        assertTrue(a.blockers.any { it.code == "wind" && it.value == 25.0 && it.limit == 15.0 })
    }

    @Test fun rainInTheNextSixHoursBlocksSprayingNow() {
        val a = Rules.analyse(forecast { if (it.isAfter(now.plusHours(2)) && it.isBefore(now.plusHours(4))) Hour(rain = 2.0, prob = 90) else Hour() }, listOf("coffee"), 15.0, now)!!
        assertTrue(a.blockers.any { it.code == "rain" })
        // The shower is at 11:00, so 09:00 (rain within the following 6 h) is in no spray window.
        val nine = LocalDateTime.of(2026, 11, 25, 9, 0)
        assertTrue(a.windows.none { !nine.isBefore(LocalDateTime.parse(it.start)) && !nine.isAfter(LocalDateTime.parse(it.end)) })
    }

    @Test fun nightIsNeverASprayWindow() {
        val night = LocalDateTime.of(2026, 11, 25, 22, 0)
        val a = Rules.analyse(forecast(), listOf("banana"), 15.0, night)!!
        assertTrue(a.blockers.any { it.code == "dark" })
        assertTrue(!a.okNow)
    }

    @Test fun humidWarmHoursRaiseFungusRisk() {
        val a = Rules.analyse(forecast { Hour(rh = 95.0, temp = 22.0) }, listOf("banana"), 15.0, now)!!
        assertEquals("high", a.disease)
        assertTrue(a.wetHours >= 14)
    }

    @Test fun waterBalanceUsesCropCoefficientAndEffectiveRain() {
        val kc = Crops.get("banana").kc
        val dry = Rules.analyse(forecast(et0 = 5.0), listOf("banana"), 15.0, now)!!
        assertEquals(Math.round(3 * 5.0 * kc * 10) / 10.0, dry.cropUse, 0.11)
        assertEquals(0.0, dry.rainEff, 0.0)
        assertEquals(if (dry.deficit > 12) "irrigate" else if (dry.deficit > 5) "light" else "skip", dry.irrigation)

        val wet = Rules.analyse(forecast(et0 = 5.0, dailyRain = { 10.0 }), listOf("banana"), 15.0, now)!!
        assertEquals(24.0, wet.rainEff, 0.01) // 3 days × 10 mm × 0.8
        assertEquals("skip", wet.irrigation)
    }

    @Test fun heavyRainDayIsFlaggedAsStorm() {
        val a = Rules.analyse(forecast(dailyRain = { if (it == 3) 42.0 else 0.0 }), listOf("bean"), 15.0, now)!!
        assertNotNull(a.stormDate)
        assertEquals(42.0, a.stormMm, 0.01)
        val calm = Rules.analyse(forecast(), listOf("bean"), 15.0, now)!!
        assertNull(calm.stormDate)
    }

    @Test fun emptyForecastReturnsNull() {
        assertNull(Rules.analyse(JSONObject(), listOf("banana"), 15.0, now))
    }

    // ---------- safety filter ----------
    @Test fun dosesAreReplacedWithFollowTheLabel() {
        val (t, changed) = Rules.safe("Mix 20 ml per litro and spray.", "es")
        assertTrue(changed)
        assertTrue(t.contains("siga la etiqueta"))
        assertTrue(!t.contains("20 ml"))
    }

    @Test fun bannedProductsAreRemoved() {
        val (t, changed) = Rules.safe("Use paraquat on the weeds.", "en")
        assertTrue(changed)
        assertTrue(t.contains("[banned product]"))
    }

    @Test fun plainAdviceIsUntouched() {
        val text = "Cut the leaves with black streaks and burn them."
        assertEquals(text to false, Rules.safe(text, "en"))
    }

    // ---------- symptom guide (works with no AI) ----------
    @Test fun streaksOnBananaPointToBlackSigatoka() {
        val top = Rules.symptomGuide("banana", setOf("streaks"))
        assertEquals("black_sigatoka", top.first())
    }

    @Test fun symptomGuideReturnsAtMostThree() {
        assertTrue(Rules.symptomGuide("tomato", Rules.SYMPTOMS.toSet()).size <= 3)
        assertTrue(Rules.symptomGuide("tomato", emptySet()).isEmpty())
    }
}
