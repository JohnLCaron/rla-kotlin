package org.cryptobiotic.rla.math

import kotlin.math.*
import kotlin.test.Test
import kotlin.test.assertEquals

class EstimateSampleSizeTest {
    val riskLimits = listOf(.01, .05, .10)

    @Test
    fun problem() {
        val it = table3s[7]
        val risk = .05
        assertEquals(optimistic(risk, it).toDouble(), estimateSampleSize(risk, it), 1.0)
    }

    @Test
    fun compareEstSampleSizeWithAudit() {
        var count = 0
        riskLimits.forEach { risk ->
            table3s.forEach {
                assertEquals(optimistic(risk, it).toDouble(), estimateSampleSize(risk, it), 1.0, "$count: $it")
                count++
            }
        }
    }

    //     fun optimistic(
    //        riskLimit: BigDecimal,
    //        dilutedMargin: BigDecimal,
    //        gamma: BigDecimal = GAMMA,
    //        twoUnder: Int = 0,
    //        oneUnder: Int = 0,
    //        oneOver: Int = 0,
    //        twoOver: Int = 0
    fun optimistic(riskLimit: Double, row: T3row) = Audit.optimistic(riskLimit.toBigDecimal(), row.mu.toBigDecimal(), row.gamma.toBigDecimal(), 0, 0, row.n1, row.n2)
    fun estimateSampleSize(riskLimit: Double, row: T3row) = estimateSampleSize(riskLimit, row.mu, row.gamma, 0, 0, row.n1, row.n2)

    fun estimateSampleSize(
        riskLimit: Double,
        dilutedMargin: Double,
        gamma: Double,
        twoUnder: Int = 0,
        oneUnder: Int = 0,
        oneOver: Int = 0,
        twoOver: Int = 0
    ): Double {
        val two_under_term = twoUnder * ln( 1 + 1 / gamma) // log or ln ?
        val one_under_term = oneUnder * ln( 1 + 1 / (2 * gamma)) // log or ln ?
        val one_over_term = oneOver * ln( 1 - 1 / (2 * gamma)) // log or ln ?
        val two_over_term = twoOver * ln( 1 - 1 / gamma) // log or ln ?

        //             twogamma.negate()
        //                .multiply(
        //                    log(riskLimit, MathContext.DECIMAL128)
        //                        .add(two_under.add(one_under).add(one_over).add(two_over))
        //                )
        val numerator: Double = -(2.0 * gamma) * (ln(riskLimit) + two_under_term + one_under_term + one_over_term + two_over_term)

        // val ceil = numerator.divide(dilutedMargin, MathContext.DECIMAL128).setScale(0, RoundingMode.CEILING)
        val ceil = ceil(numerator / dilutedMargin)  // org does a rounding up
        val over_under_sum = (twoUnder + oneUnder +  oneOver + twoOver).toDouble()
        val result = max(ceil, over_under_sum)

        return result
    }
}