package com.petiht.conuco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Shared data math: privacy cells, distances, the market price board and neighbour outbreak alerts. */
class CommunityTest {
    private val santiago = 19.45 to -70.70
    private fun day(ago: Long) = LocalDate.now().minusDays(ago).toString()

    @Test fun cellRoundsToOneDecimalSoNoExactFarmIsShared() {
        assertEquals("19.5_-70.7", Repo.cellOf(19.4512, -70.6977))
        assertEquals(19.5 to -70.7, Repo.cellCenter("19.5_-70.7"))
    }

    @Test fun distanceSantiagoToSantoDomingo() {
        val km = Repo.km(santiago, 18.48 to -69.90)
        assertTrue("got $km", km in 120.0..150.0)
        assertEquals(0.0, Repo.km(santiago, santiago), 1e-9)
    }

    @Test fun priceBoardUsesMedianTrendAndBestBuyer() {
        val cell = Repo.cellOf(santiago.first, santiago.second)
        val rows = listOf(
            PriceRow("banana", "unit", 16.0, "DOP", "Mercado Nuevo", cell, day(1)),
            PriceRow("banana", "unit", 20.0, "DOP", "Hospidaje", cell, day(2)),
            PriceRow("banana", "unit", 18.0, "DOP", "", cell, day(3)),
            PriceRow("banana", "unit", 15.0, "DOP", "", cell, day(20)),  // last period
            PriceRow("banana", "unit", 99.0, "USD", "", cell, day(1)),   // other currency: ignored
            PriceRow("banana", "unit", 50.0, "DOP", "", "18.5_-69.9", day(1)), // too far: ignored
        )
        val b = Repo.board(rows, santiago.first, santiago.second, 30.0, "DOP", 14).single()
        assertEquals(18.0, b.median, 0.001)
        assertEquals(3, b.n)
        assertEquals(16.0, b.min, 0.0); assertEquals(20.0, b.max, 0.0)
        assertEquals(20, b.trend) // 18 vs 15 = +20 %
        assertEquals("Hospidaje", b.bestMarket)
    }

    @Test fun priceBoardSkipsCropsWithNoRecentReports() {
        val cell = Repo.cellOf(santiago.first, santiago.second)
        val rows = listOf(PriceRow("coffee", "lb", 200.0, "DOP", "", cell, day(30)))
        assertTrue(Repo.board(rows, santiago.first, santiago.second, 30.0, "DOP", 14).isEmpty())
    }

    @Test fun outbreakAlertsGroupFilterAndSort() {
        val near = santiago.first + 0.05 to santiago.second
        val rows = listOf(
            Outbreak("banana", "black_sigatoka", "fungus", near.first, near.second, day(1)),
            Outbreak("banana", "black_sigatoka", "fungus", near.first, near.second, day(2)),
            Outbreak("banana", "black_sigatoka", "fungus", santiago.first, santiago.second, day(3)),
            Outbreak("coffee", "coffee_rust", "fungus", near.first, near.second, day(1)),
            Outbreak("coffee", "coffee_rust", "fungus", near.first, near.second, day(1)),
            Outbreak("bean", "golden_mosaic", "virus", near.first, near.second, day(1)),       // only 1 report: below min
            Outbreak("cacao", "black_pod", "fungus", 18.48, -69.90, day(1)),                   // 130 km away
        )
        val all = Repo.alerts(rows, santiago.first, santiago.second, 30.0, 2, null)
        assertEquals(listOf("black_sigatoka", "coffee_rust"), all.map { it.problemKey })
        assertEquals(3, all.first().n)
        assertEquals(0, all.first().nearestKm)
        assertEquals(day(1), all.first().last)

        val mine = Repo.alerts(rows, santiago.first, santiago.second, 30.0, 2, listOf("coffee"))
        assertEquals(listOf("coffee_rust"), mine.map { it.problemKey })
    }

    @Test fun currencyFallsBackToUsd() {
        assertEquals("DOP", Crops.currency("DO"))
        assertEquals("HTG", Crops.currency("HT"))
        assertEquals("USD", Crops.currency("ZZ"))
        assertEquals("USD", Crops.currency(null))
    }

    @Test fun unknownCropIsOther() {
        assertEquals("other", Crops.get("not-a-crop").key)
        assertEquals("other", Crops.get(null).key)
    }

    @Test fun freeTextProblemMatchesKnownKey() {
        assertEquals("black_sigatoka", Crops.matchProblem("Sigatoka negra"))
        assertEquals("other", Crops.matchProblem(""))
    }

    @Test fun boardRowWithoutOlderReportsHasNoTrend() {
        val cell = Repo.cellOf(santiago.first, santiago.second)
        val b = Repo.board(listOf(PriceRow("banana", "unit", 18.0, "DOP", "", cell, day(1))), santiago.first, santiago.second, 30.0, "DOP", 14).single()
        assertNull(b.trend)
        assertNull(b.bestMarket)
    }
}
