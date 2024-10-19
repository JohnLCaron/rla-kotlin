package org.cryptobiotic.rla.math

import ch.obermuhlner.math.big.BigDecimalMath.log
import ch.obermuhlner.math.big.BigDecimalMath.pow
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * A static class that should grow to contain audit related mathematical
 * functions that do not belong in models, controllers, or endpoint
 * bodies.
 */
object Audit {
    /**
     * Stark's gamma from the literature. As seen in a controller.
     */
    val GAMMA: BigDecimal = BigDecimal.valueOf(1.03905)

    /**
     * μ = V / N
     * @param margin the smallest margin of winning, V votes.
     * @param ballotCount N, the number of ballots cast in a contest.
     * @return BigDecimal the diluted margin
     */
    fun dilutedMargin(margin: Int, ballotCount: Long): BigDecimal {
        return dilutedMargin(
            BigDecimal.valueOf(margin.toLong()),
            BigDecimal.valueOf(ballotCount)
        )
    }

    /**
     * μ = V / N
     * @param margin the smallest margin of winning, V votes.
     * @param ballotCount N, the number of ballots cast in a contest.
     * @return BigDecimal the diluted margin
     */
    fun dilutedMargin(margin: BigDecimal, ballotCount: BigDecimal): BigDecimal {
        return if (margin === BigDecimal.ZERO || ballotCount === BigDecimal.ZERO) {
            BigDecimal.ZERO
        } else {
            margin.divide(ballotCount, MathContext.DECIMAL128)
        }
    }

    /**
     * The "total error bound" defined in the literature.
     *
     * Usually represented as `U`, this can be found in equation (8) in Stark's
     * Super-Simple Simultaneous Single-Ballot Risk Limiting Audits paper.
     *
     * @param dilutedMargin the diluted margin of the contest
     * @param gamma the "error inflator" parameter from the literature
     *
     * @return the total error bound
     */
    fun totalErrorBound(dilutedMargin: BigDecimal, gamma: BigDecimal): BigDecimal {
        return gamma.multiply(BigDecimal.valueOf(2), MathContext.DECIMAL128)
            .divide(dilutedMargin, MathContext.DECIMAL128)
    }

    /**
     * Computes the expected number of ballots to audit overall given the
     * specified numbers of over- and understatements.
     *
     * @param the_two_under The two-vote understatements.
     * @param the_one_under The one-vote understatements.
     * @param the_one_over The one-vote overstatements.
     * @param the_two_over The two-vote overstatements.
     *
     * @return the expected number of ballots remaining to audit.
     * This is the stopping sample size as defined in the literature:
     * https://www.stat.berkeley.edu/~stark/Preprints/gentle12.pdf
     */
    @JvmOverloads
    fun optimistic(
        riskLimit: BigDecimal,
        dilutedMargin: BigDecimal,
        gamma: BigDecimal = GAMMA,
        twoUnder: Int = 0,
        oneUnder: Int = 0,
        oneOver: Int = 0,
        twoOver: Int = 0
    ): BigDecimal {
        if (dilutedMargin.compareTo(BigDecimal.ZERO) == 0) { //hilarious
            // nothing to do here, no samples will need to be audited because the
            // contest is uncontested
            return BigDecimal.ZERO
        }

        val result: BigDecimal
        val invgamma: BigDecimal = BigDecimal.ONE.divide(gamma, MathContext.DECIMAL128)
        val twogamma: BigDecimal = BigDecimal.valueOf(2).multiply(gamma)
        val invtwogamma: BigDecimal =
            BigDecimal.ONE.divide(twogamma, MathContext.DECIMAL128)
        val two_under_bd: BigDecimal = BigDecimal.valueOf(twoUnder.toLong())
        val one_under_bd: BigDecimal = BigDecimal.valueOf(oneUnder.toLong())
        val one_over_bd: BigDecimal = BigDecimal.valueOf(oneOver.toLong())
        val two_over_bd: BigDecimal = BigDecimal.valueOf(twoOver.toLong())

        val over_under_sum: BigDecimal =
            two_under_bd.add(one_under_bd).add(one_over_bd).add(two_over_bd)
        val two_under: BigDecimal =
            two_under_bd.multiply(
                log(
                    BigDecimal.ONE.add(invgamma),
                    MathContext.DECIMAL128
                )
            )
        val one_under: BigDecimal =
            one_under_bd.multiply(
                log(
                    BigDecimal.ONE.add(invtwogamma),
                    MathContext.DECIMAL128
                )
            )
        val one_over: BigDecimal =
            one_over_bd.multiply(
                log(
                    BigDecimal.ONE.subtract(invtwogamma),
                    MathContext.DECIMAL128
                )
            )
        val two_over: BigDecimal =
            two_over_bd.multiply(
                log(
                    BigDecimal.ONE.subtract(invgamma),
                    MathContext.DECIMAL128
                )
            )

        val numerator: BigDecimal =
            twogamma.negate()
                .multiply(
                    log(riskLimit, MathContext.DECIMAL128)
                        .add(two_under.add(one_under).add(one_over).add(two_over))
                )
        val ceil: BigDecimal =
            numerator.divide(dilutedMargin, MathContext.DECIMAL128).setScale(0, RoundingMode.CEILING)
        result = ceil.max(over_under_sum)

        return result
    }

    /**
     * Conservative approximation of the Kaplan-Markov P-value.
     *
     * The audit can stop when the P-value drops to or below the defined risk
     * limit. The output of this method will never estimate a P-value that is too
     * low, it will always be at or above the (more complicated to calculate)
     * Kaplan-Markov P-value, but usually not by much. Therefore this method is
     * safe to use as the stopping condition for the audit, even though it may be
     * possible to stop the audit "a ballot or two" earlier if calculated using
     * the Kaplan-Markov method.
     *
     * Implements equation (10) of Philip B. Stark's paper, Super-Simple
     * Simultaneous Single-Ballot Risk-Limiting Audits.
     *
     * Translated from Stark's implementation under the heading "A simple
     * approximation" at the following URL:
     *
     * https://github.com/pbstark/S157F17/blob/master/audit.ipynb
     *
     * NOTE: The ordering of the under and overstatement parameters is different
     * from its cousin method `optimistic`.
     *
     * @param auditedBallots the number of ballots audited so far
     * @param dilutedMargin the diluted margin of the contest
     * @param gamma the "error inflator" parameter from the literature
     * @param twoUnder the number of two-vote understatements
     * @param oneUnder the number of one-vote understatements
     * @param oneOver the number of one-vote overstatements
     * @param twoOver the number of two-vote overstatements
     *
     * @return approximation of the Kaplan-Markov P-value
     */
    fun pValueApproximation(
        auditedBallots: Int,
        dilutedMargin: BigDecimal,
        gamma: BigDecimal,
        oneUnder: Int,  // n3
        twoUnder: Int,  // n4
        oneOver: Int,  // n1
        twoOver: Int   // n2
    ): BigDecimal {
        val totalErrorBound: BigDecimal = totalErrorBound(dilutedMargin, gamma)

        // min(1, (1-1/U)^n * (1-1/(2*gamma))^(-n1) * (1-1/gamma)^(-n2) * (1+1/(2*gamma))^(-n3) * (1+1/(gamma))^(-n4))
        return BigDecimal.ONE.min(
            pow(
                BigDecimal.ONE.subtract(
                    BigDecimal.ONE.divide(totalErrorBound, MathContext.DECIMAL128)
                ),
                auditedBallots.toLong(),
                MathContext.DECIMAL128
            )
                .multiply(
                    pow(
                        BigDecimal.ONE.subtract(
                            BigDecimal.ONE.divide(
                                gamma.multiply(BigDecimal.valueOf(2), MathContext.DECIMAL128),
                                MathContext.DECIMAL128
                            )
                        ),
                        (-1 * oneOver).toLong(),
                        MathContext.DECIMAL128
                    ),
                    MathContext.DECIMAL128
                )
                .multiply(
                    pow(
                        BigDecimal.ONE.subtract(
                            BigDecimal.ONE.divide(gamma, MathContext.DECIMAL128)
                        ),
                        (-1 * twoOver).toLong(),
                        MathContext.DECIMAL128
                    ),
                    MathContext.DECIMAL128
                )
                .multiply(
                    pow(
                        BigDecimal.ONE.add(
                            BigDecimal.ONE.divide(
                                gamma.multiply(BigDecimal.valueOf(2), MathContext.DECIMAL128),
                                MathContext.DECIMAL128
                            )
                        ),
                        (-1 * oneUnder).toLong(),
                        MathContext.DECIMAL128
                    ),
                    MathContext.DECIMAL128
                )
                .multiply(
                    pow(
                        BigDecimal.ONE.add(
                            BigDecimal.ONE.divide(gamma, MathContext.DECIMAL128)
                        ),
                        (-1 * twoUnder).toLong(),
                        MathContext.DECIMAL128
                    ),
                    MathContext.DECIMAL128
                )
        )
    }
}
