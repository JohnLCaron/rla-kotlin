package org.cryptobiotic.rla.controller

import kotlin.collections.mutableMapOf

import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ContestCounterTest private constructor() {
    @Test
    fun testAccumulateVoteTotals() {
        val voteTotals = exampleVoteTotals()

        val results = ContestCounter.accumulateVoteTotals(voteTotals)
        assertEquals(results.size, 2)
        assertEquals(results.get("Joe Frasier") as Int, 18)
        assertEquals(results.get("Muhammed Ali") as Int, 20)
    }

    @Test
    fun rankTotalsTest() {
        val results = ContestCounter.rankTotals(townTrustees())
        assertFalse(results.isEmpty())
        assertEquals(results.get(0).key, "Donnita")
    }

    @Test
    fun pairwiseMarginsTest() {
        val winners = Stream.of<String?>("Muhammed Ali").collect(Collectors.toSet())
        val losers = Stream.of<String?>("Joe Frasier").collect(Collectors.toSet())
        val expectedMargins = Stream.of<Int?>(5).collect(Collectors.toSet())

        assertEquals(
            ContestCounter.pairwiseMargins(winners, losers, exampleVoteTotal()),
            expectedMargins, "Vote for one of two"
        )
    }

    @Test
    fun multipleWinnerMargins() {
        val winners = setOf("Donnita", "Eva", "John", "Steve")
        val losers = setOf("William")
        val expectedMargins = setOf(15, 3, 1, 8)

        assertEquals(
            ContestCounter.pairwiseMargins(winners, losers, townTrustees()),
            expectedMargins, "Vote for four of five"
        )
    }

    private fun exampleVoteTotal(): Map<String, Int> {
        val vt1 = mutableMapOf<String, Int>()
        vt1.put("Joe Frasier", 15)
        vt1.put("Muhammed Ali", 20)
        return vt1
    }

    private fun exampleVoteTotals(): List<Map<String, Int>> {
        val voteTotals = mutableListOf<MutableMap<String, Int>>()
        val vt1 = mutableMapOf<String, Int>()
        val vt2 = mutableMapOf<String, Int>()
        vt1.put("Joe Frasier", 9)
        vt1.put("Muhammed Ali", 10)
        vt2.put("Joe Frasier", 9)
        vt2.put("Muhammed Ali", 10)
        voteTotals.add(vt1)
        voteTotals.add(vt2)
        return voteTotals
    }

    private fun townTrustees(): Map<String, Int> {
        val voteTotals = mutableMapOf<String, Int>()
        voteTotals.put("John", 5)
        voteTotals.put("Donnita", 19)
        voteTotals.put("William", 4)
        voteTotals.put("Steve", 7)
        voteTotals.put("Eva", 12)
        return voteTotals
    }
}
