
package org.cryptobiotic.rla.math

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.Test
import kotlin.test.assertEquals

class AuditTest {

    @Test
    fun testOptimistic() {
        val riskLimit = BigDecimal.valueOf(0.5)
        val dilutedMargin = BigDecimal.valueOf(0.05)
        val gamma = BigDecimal.valueOf(1.2)
        val twoUnder = 0
        val oneUnder = 0
        val oneOver = 0
        val twoOver = 0

        val result = Audit.optimistic(
            riskLimit,
            dilutedMargin,
            gamma,
            twoUnder,
            oneUnder,
            oneOver,
            twoOver
        )
        assertEquals(34, result.toInt())
    }

    @Test
    fun testPValueApproximation() {
        var auditedBallots = 500
        var dilutedMargin = BigDecimal.valueOf(0.02)
        var gamma = BigDecimal.valueOf(1.01)
        var oneUnder = 0
        var twoUnder = 0
        var oneOver = 0
        var twoOver = 0

        var result =
            Audit.pValueApproximation(
                auditedBallots,
                dilutedMargin,
                gamma,
                oneUnder,
                twoUnder,
                oneOver,
                twoOver
            )

        assertEquals(result.setScale(3, RoundingMode.HALF_UP), BigDecimal.valueOf(0.007))

        oneOver = 3
        twoOver = 0

        result = Audit.pValueApproximation(
            auditedBallots,
            dilutedMargin,
            gamma,
            oneUnder,
            twoUnder,
            oneOver,
            twoOver
        )

        assertEquals(result.setScale(3, RoundingMode.HALF_UP), BigDecimal.valueOf(0.054))

        oneOver = 0
        twoOver = 1

        result = Audit.pValueApproximation(
            auditedBallots,
            dilutedMargin,
            gamma,
            oneUnder,
            twoUnder,
            oneOver,
            twoOver
        )

        assertEquals(result.setScale(3, RoundingMode.HALF_UP), BigDecimal.valueOf(0.698))

        oneOver = 0
        twoOver = 2

        result = Audit.pValueApproximation(
            auditedBallots,
            dilutedMargin,
            gamma,
            oneUnder,
            twoUnder,
            oneOver,
            twoOver
        )

        assertEquals(
            result.setScale(3, RoundingMode.HALF_UP),
            BigDecimal.valueOf(1.000).setScale(3)
        )
    }

}