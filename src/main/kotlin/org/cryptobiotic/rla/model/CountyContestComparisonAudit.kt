/*
 * Free & Fair Colorado RLA System
 *
 * @title ColoradoRLA
 * @created Aug 19, 2017
 * @copyright 2017 Colorado Department of State
 * @license SPDX-License-Identifier: AGPL-3.0-or-later
 * @creator Daniel M. Zimmerman <dmz@freeandfair.us>
 * @description A system to assist in conducting statewide risk-limiting audits.
 */
package org.cryptobiotic.rla.model

import org.cryptobiotic.rla.dashboard.CountyDashboard
import org.cryptobiotic.rla.math.Audit
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.max

/**
 * A class representing the state of a single audited contest for
 * a single county.
 *
 * @author Daniel M. Zimmerman <dmz></dmz>@freeandfair.us>
 * @version 1.0.0
 */

class CountyContestComparisonAudit(
    val id: Long,
    val version: Long,
    val dashboard: CountyDashboard,
    val contest: Contest,
    val contestResult: ContestResult,
    startingAuditStatus: AuditStatus,
    val gamma: BigDecimal = GAMMA,
    val dilutedMargin: BigDecimal = BigDecimal.ONE,
    val riskLimit: BigDecimal = BigDecimal.ONE,

    ) {
    private var auditStatus = startingAuditStatus
    
    /**
     * The number of samples audited.
     */
    private var my_audited_sample_count = 0

    /**
     * The number of samples to audit overall assuming no further overstatements.
     */
    private var my_optimistic_samples_to_audit = 0

    /**
     * The expected number of samples to audit overall assuming overstatements
     * continue at the current rate.
     */
    private var my_estimated_samples_to_audit = 0

    /**
     * The number of two-vote understatements recorded so far.
     */
    private var my_two_vote_under_count = 0

    /**
     * The number of one-vote understatements recorded so far.
     */
    private var my_one_vote_under_count = 0

    /**
     * The number of one-vote overstatements recorded so far.
     */
    private var my_one_vote_over_count = 0

    /**
     * The number of two-vote overstatements recorded so far.
     */
    private var my_two_vote_over_count = 0

    /**
     * The number of discrepancies recorded so far that are neither
     * understatements nor overstatements.
     */
    private var my_other_count = 0

    /**
     * The number of disagreements.
     */
    private var my_disagreement_count = 0

    /**
     * A flag that indicates whether the optimistic ballots to audit
     * estimate needs to be recalculated.
     */
    private var my_optimistic_recalculate_needed = true

    /**
     * A flag that indicates whether the non-optimistic ballots to
     * audit estimate needs to be recalculated
     */
    private var my_estimated_recalculate_needed = true

    /**
     * A map from CVRAuditInfo objects to their discrepancy values for this
     * audited contest.
     */
    private val my_discrepancies: MutableMap<CVRAuditInfo, Int> = HashMap<CVRAuditInfo, Int>()

    /**
     * A map from CVRAuditInfo objects to their discrepancy values for this
     * audited contest.
     */
    private val my_disagreements: MutableSet<CVRAuditInfo> = HashSet<CVRAuditInfo>()


    /**
     * Updates the audit status based on the current risk limit. If the audit
     * has already been ended or the contest is not auditable, this method has
     * no effect on its status.
     */
    fun updateAuditStatus() {
        if (auditStatus == AuditStatus.ENDED ||
            auditStatus == AuditStatus.NOT_AUDITABLE
        ) {
            return
        }

        auditStatus = if (my_optimistic_samples_to_audit - my_audited_sample_count <= 0) {
            AuditStatus.RISK_LIMIT_ACHIEVED
        } else {
            // risk limit has not been achieved
            // note that it _is_ possible to go from RISK_LIMIT_ACHIEVED to
            // IN_PROGRESS if a sample or set of samples is "unaudited"
            AuditStatus.IN_PROGRESS
        }
    }

    /**
     * Ends this audit; if the audit has already reached its risk limit,
     * or the contest is not auditable, this call has no effect on its status.
     */
    fun endAudit() {
        if (auditStatus != AuditStatus.RISK_LIMIT_ACHIEVED &&
            auditStatus != AuditStatus.NOT_AUDITABLE
        ) {
            auditStatus = AuditStatus.ENDED
        }
    }

    /**
     * @return the initial expected number of samples to audit.
     */
    fun initialSamplesToAudit(): Int {
        return computeOptimisticSamplesToAudit(0, 0, 0, 0)
            .setScale(0, RoundingMode.CEILING).toInt()
    }

    /**
     * @return the expected overall number of ballots to audit, assuming no
     * further overstatements occur.
     */
    fun optimisticSamplesToAudit(): Int {
        if (my_optimistic_recalculate_needed) {
            recalculateSamplesToAudit()
        }

        return my_optimistic_samples_to_audit
    }

    /**
     * @return the expected overall number of ballots to audit, assuming
     * overstatements continue to occur at the current rate.
     */
    fun estimatedSamplesToAudit(): Int {
        if (my_estimated_recalculate_needed) {
            recalculateSamplesToAudit()
        }
        return my_estimated_samples_to_audit
    }

    /**
     * Recalculates the overall numbers of ballots to audit.
     */
    private fun recalculateSamplesToAudit() {
        if (my_optimistic_recalculate_needed) {
            val optimistic: BigDecimal = computeOptimisticSamplesToAudit(
                my_two_vote_under_count,
                my_one_vote_under_count,
                my_one_vote_over_count,
                my_two_vote_over_count
            )
            my_optimistic_samples_to_audit = optimistic.toInt()
            my_optimistic_recalculate_needed = false
        }

        if (my_one_vote_over_count + my_two_vote_over_count == 0) {
            my_estimated_samples_to_audit = my_optimistic_samples_to_audit
        } else {
            // compute the "fudge factor" for the estimate
            val audited_samples: BigDecimal = BigDecimal.valueOf(dashboard.auditedSampleCount()!!.toLong())
            val overstatements: BigDecimal =
                BigDecimal.valueOf((my_one_vote_over_count + my_two_vote_over_count).toLong())
            val fudge_factor: BigDecimal
            fudge_factor = if (audited_samples == BigDecimal.ZERO) {
                BigDecimal.ONE
            } else {
                BigDecimal.ONE.add(overstatements.divide(audited_samples, MathContext.DECIMAL128))
            }
            val estimated: BigDecimal =
                BigDecimal.valueOf(my_optimistic_samples_to_audit.toLong()).multiply(fudge_factor)
            my_estimated_samples_to_audit = estimated.setScale(0, RoundingMode.CEILING).toInt()
        }
        my_estimated_recalculate_needed = false
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
    private fun computeOptimisticSamplesToAudit(
        twoUnder: Int,
        oneUnder: Int,
        oneOver: Int,
        twoOver: Int
    ): BigDecimal {
        return Audit.optimistic(
            riskLimit, dilutedMargin, gamma,
            twoUnder, oneUnder, oneOver, twoOver
        )
    }

    /**
     * Signals that a sample has been audited. This ensures that estimates
     * are recalculated correctly and states are updated.
     *
     * @param the_count The count of samples that have been audited simultaneously
     * (for duplicates).
     */
    fun signalSampleAudited(the_count: Int) {
        my_estimated_recalculate_needed = true
        my_audited_sample_count = my_audited_sample_count + the_count

        if (auditStatus != AuditStatus.ENDED &&
            auditStatus != AuditStatus.NOT_AUDITABLE
        ) {
            auditStatus = AuditStatus.IN_PROGRESS
        }
    }

    /**
     * Signals that a sample has been unaudited. This ensures that estimates
     * are recalculated correctly and states are updated.
     *
     * @param the_count The count of samples that have been unaudited simultaneously
     * (for duplicates).
     */
    fun signalSampleUnaudited(the_count: Int) {
        my_estimated_recalculate_needed = true
        my_audited_sample_count = my_audited_sample_count - the_count

        if (auditStatus != AuditStatus.ENDED &&
            auditStatus != AuditStatus.NOT_AUDITABLE
        ) {
            auditStatus = AuditStatus.IN_PROGRESS
        }
    }

    /**
     * Records a disagreement with the specified CVRAuditInfo.
     *
     * @param the_record The CVRAuditInfo record that generated the disagreement.
     */
    fun recordDisagreement(the_record: CVRAuditInfo) {
        my_disagreements.add(the_record)
        my_disagreement_count = my_disagreement_count + 1
    }

    /**
     * Removes a disagreement with the specified CVRAuditInfo.
     *
     * @param the_record The CVRAuditInfo record that generated the disagreement.
     */
    fun removeDisagreement(the_record: CVRAuditInfo) {
        my_disagreements.remove(the_record)
        my_disagreement_count = my_disagreement_count - 1
    }

    /**
     * @return the disagreement count.
     */
    fun disagreementCount(): Int {
        return my_disagreement_count
    }

    /**
     * Records the specified discrepancy (the valid range is -2 .. 2: -2 and -1 are
     * understatements, 0 is a discrepancy that doesn't affect the RLA calculations,
     * and 1 and 2 are overstatements).
     *
     * @param the_record The CVRAuditInfo record that generated the discrepancy.
     * @param the_type The type of discrepancy to add.
     * @exception IllegalArgumentException if an invalid discrepancy type is
     * specified.
     */
    fun recordDiscrepancy(
        the_record: CVRAuditInfo,
        the_type: Int
    ) {
        // we never trigger an estimated recalculate here; it is
        // triggered by signalBallotAudited() regardless of whether there is
        // a discrepancy or not
        when (the_type) {
            -2 -> {
                my_two_vote_under_count = my_two_vote_under_count + 1
                my_optimistic_recalculate_needed = true
            }

            -1 -> {
                my_one_vote_under_count = my_one_vote_under_count + 1
                my_optimistic_recalculate_needed = true
            }

            0 -> my_other_count = my_other_count + 1
            1 -> {
                my_one_vote_over_count = my_one_vote_over_count + 1
                my_optimistic_recalculate_needed = true
            }

            2 -> {
                my_two_vote_over_count = my_two_vote_over_count + 1
                my_optimistic_recalculate_needed = true
            }

            else -> throw IllegalArgumentException("invalid discrepancy type: $the_type")
        }
        my_discrepancies[the_record] = the_type
    }

    /**
     * Removes the specified over/understatement (the valid range is -2 .. 2:
     * -2 and -1 are understatements, 0 is a discrepancy that doesn't affect the
     * RLA calculations, and 1 and 2 are overstatements). This is typically done
     * when a new interpretation is submitted for a ballot that had already been
     * interpreted.
     *
     * @param the_record The CVRAuditInfo record that generated the discrepancy.
     * @param the_type The type of discrepancy to remove.
     * @exception IllegalArgumentException if an invalid discrepancy type is
     * specified.
     */
    fun removeDiscrepancy(the_record: CVRAuditInfo, the_type: Int) {
        // we never trigger an estimated recalculate here; it is
        // triggered by signalBallotAudited() regardless of whether there is
        // a discrepancy or not
        when (the_type) {
            -2 -> {
                my_two_vote_under_count = my_two_vote_under_count - 1
                my_optimistic_recalculate_needed = true
            }

            -1 -> {
                my_one_vote_under_count = my_one_vote_under_count - 1
                my_optimistic_recalculate_needed = true
            }

            0 -> my_other_count = my_other_count - 1
            1 -> {
                my_one_vote_over_count = my_one_vote_over_count - 1
                my_optimistic_recalculate_needed = true
            }

            2 -> {
                my_two_vote_over_count = my_two_vote_over_count - 1
                my_optimistic_recalculate_needed = true
            }

            else -> throw IllegalArgumentException("invalid discrepancy type: $the_type")
        }
        my_discrepancies.remove(the_record)
    }

    /**
     * Returns the count of the specified type of discrepancy. -2 and -1 represent
     * understatements, 0 represents a discrepancy that doesn't affect the RLA
     * calculations, and 1 and 2 represent overstatements.
     *
     * @param the_type The type of discrepancy.
     * @exception IllegalArgumentException if an invalid discrepancy type is
     * specified.
     */
    fun discrepancyCount(the_type: Int): Int {
        val result: Int

        result = when (the_type) {
            -2 -> my_two_vote_under_count
            -1 -> my_one_vote_under_count
            0 -> my_other_count
            1 -> my_one_vote_over_count
            2 -> my_two_vote_over_count
            else -> throw IllegalArgumentException("invalid discrepancy type: $the_type")
        }
        return result
    }

    /**
     * Computes the over/understatement represented by the CVR/ACVR pair stored in
     * the specified CVRAuditInfo. This method returns an optional int that, if
     * present, indicates a discrepancy. There are 5 possible types of
     * discrepancy: -1 and -2 indicate 1- and 2-vote understatements; 1 and 2
     * indicate 1- and 2- vote overstatements; and 0 indicates a discrepancy that
     * does not count as either an under- or overstatement for the RLA algorithm,
     * but nonetheless indicates a difference between ballot interpretations.
     *
     * @param the_info The CVRAuditInfo.
     * @return an optional int that is present if there is a discrepancy and absent
     * otherwise.
     */
    fun computeDiscrepancy(the_info: CVRAuditInfo): Int? {
        return computeDiscrepancy(the_info.cvr, the_info.acvr!!)
    }

    /**
     * Computes the over/understatement represented by the specified CVR and ACVR.
     * This method returns an optional int that, if present, indicates a discrepancy.
     * There are 5 possible types of discrepancy: -1 and -2 indicate 1- and 2-vote
     * understatements; 1 and 2 indicate 1- and 2- vote overstatements; and 0
     * indicates a discrepancy that does not count as either an under- or
     * overstatement for the RLA algorithm, but nonetheless indicates a difference
     * between ballot interpretations.
     *
     * @param cvr The CVR that the machine saw
     * @param auditedCVR The ACVR that the human audit board saw
     * @return an optional int that is present if there is a discrepancy and absent
     * otherwise.
     */
    // FIXME Should we point to the ContestResult instead?
    fun computeDiscrepancy(
        cvr: CastVoteRecord,
        auditedCVR: CastVoteRecord
    ): Int? {
        var result: Int? = null
        val cvr_info = cvr.contestInfoForContest(this.contest)
        val acvr_info = auditedCVR.contestInfoForContest(this.contest)

        if (auditedCVR.recordType == CastVoteRecord.RecordType.PHANTOM_BALLOT) {
            result = computePhantomBallotDiscrepancy(cvr_info)
        } else if (cvr.recordType == CastVoteRecord.RecordType.PHANTOM_RECORD) {
            // similar to the phantom ballot, we use the worst case scenario, a 2-vote
            // overstatement, except here, we don't have a CVR to check anything on.
            result = 2
        } else if (cvr_info != null && acvr_info != null) {
            result = if (acvr_info.consensus == CVRContestInfo.ConsensusValue.NO) {
                // a lack of consensus for this contest is treated
                // identically to a phantom ballot
                computePhantomBallotDiscrepancy(cvr_info)
            } else {
                computeAuditedBallotDiscrepancy(cvr_info, acvr_info)
            }
        }

        return result
    }

    /**
     * Computes the discrepancy between two ballots. This method returns an optional
     * int that, if present, indicates a discrepancy. There are 5 possible types of
     * discrepancy: -1 and -2 indicate 1- and 2-vote understatements; 1 and 2 indicate
     * 1- and 2- vote overstatements; and 0 indicates a discrepancy that does not
     * count as either an under- or overstatement for the RLA algorithm, but
     * nonetheless indicates a difference between ballot interpretations.
     *
     * @param the_cvr_info The CVR info.
     * @param the_acvr_info The ACVR info.
     * @return an optional int that is present if there is a discrepancy and absent
     * otherwise.
     */
    private fun computeAuditedBallotDiscrepancy(
        the_cvr_info: CVRContestInfo,
        the_acvr_info: CVRContestInfo
    ): Int? {
        // Check for overvotes.
        //
        // See the ComparisonAudit class for more details. In short, if we have an
        // overvoted ACVR we record no selections for that contest, matching the
        // CVR format.
        val acvr_choices: MutableSet<String> = HashSet()
        if (the_acvr_info.choices.size <= contestResult.winnersAllowed) {
            acvr_choices.addAll(the_acvr_info.choices)
        }

        // avoid linear searches on CVR choices
        val cvr_choices  = the_cvr_info.choices.toSet()

        // if the choices in the CVR and ACVR are identical now, we can simply return the
        // fact that there's no discrepancy
        if (cvr_choices == acvr_choices) {
            return null
        }

        // we want to get the maximum pairwise update delta, because that's the "worst"
        // change in a pairwise margin, and the discrepancy we record; we start with
        // Integer.MIN_VALUE so our maximization algorithm works. it is also the case
        // that _every_ pairwise margin must be increased for an understatement to be
        // reported
        var raw_result = Int.MIN_VALUE

        var possible_understatement = true

        for (winner in contestResult.winners) {
            val winner_change: Int
            winner_change = if (!cvr_choices.contains(winner) && acvr_choices.contains(winner)) {
                // this winner gained a vote
                1
            } else if (cvr_choices.contains(winner) && !acvr_choices.contains(winner)) {
                // this winner lost a vote
                -1
            } else {
                // this winner's votes didn't change
                0
            }
            if (contestResult.losers.isEmpty()) {
                // if there are no losers, we'll just negate this number - even though in
                // real life, we wouldn't be auditing the contest at all
                raw_result = max(raw_result.toDouble(), -winner_change.toDouble()).toInt()
            } else {
                for (loser in contestResult.losers) {
                    val loser_change: Int
                    loser_change = if (!cvr_choices.contains(loser) && acvr_choices.contains(loser)) {
                        // this loser gained a vote
                        1
                    } else if (cvr_choices.contains(loser) && !acvr_choices.contains(loser)) {
                        // this loser lost a vote
                        -1
                    } else {
                        // this loser's votes didn't change
                        0
                    }
                    // the discrepancy is the loser change minus the winner change (i.e., if this
                    // loser lost a vote (-1) and this winner gained a vote (1), that's a 2-vote
                    // understatement (-1 - 1 = -2). Overstatements are worse than understatements,
                    // as far as the audit is concerned, so we keep the highest discrepancy
                    val discrepancy = loser_change - winner_change

                    // taking the max here does not cause a loss of information even if the
                    // discrepancy is 0; if the discrepancy is 0 we can no longer report an
                    // understatement, and we still know there was a discrepancy because we
                    // didn't short circuit earlier
                    raw_result = max(raw_result.toDouble(), discrepancy.toDouble()).toInt()

                    // if this discrepancy indicates a narrowing of, or no change in, this pairwise
                    // margin, then an understatement is no longer possible because that would require
                    // widening _every_ pairwise margin
                    if (discrepancy >= 0) {
                        possible_understatement = false
                    }
                }
            }
        }

        check(raw_result != Int.MIN_VALUE) {
            // this should only be possible if something went horribly wrong (like the contest
            // has no winners)
            "unable to compute discrepancy in contest " + contest.name
        }


        return if (possible_understatement) {
            // we return the raw result unmodified
            raw_result
        } else {
            // we return the raw result with a floor of 0, because we can't report an understatement
            max(0, raw_result)
        }

    }

    /**
     * Computes the discrepancy between a phantom ballot and the specified
     * CVRContestInfo.
     *
     * @param the_info The CVRContestInfo.
     * @return the discrepancy.
     */
    private fun computePhantomBallotDiscrepancy(the_info: CVRContestInfo?): Int {
        val result: Int

        // if the ACVR is a phantom ballot, we need to assume that it was a vote
        // for all the losers; so if any winners had votes on the original CVR
        // it's a 2-vote overstatement, otherwise a 1-vote overstatement
        if (the_info == null) {
            // this contest doesn't appear in the CVR, so we assume the worst
            result = 2
        } else {
            // this contest does appear in the CVR, so we can actually check
            val winner_votes = the_info.choices.toMutableSet()
            winner_votes.removeAll(contestResult.losers)
            result = if (winner_votes.isEmpty()) 1 else 2
        }

        return result
    }

    companion object {
        /**
         * The database stored precision for decimal types.
         */
        const val PRECISION: Int = 10

        /**
         * The database stored scale for decimal types.
         */
        const val SCALE: Int = 8

        /**
         * Gamma, as presented in the literature:
         * https://www.stat.berkeley.edu/~stark/Preprints/gentle12.pdf
         */
        val STARK_GAMMA: BigDecimal = BigDecimal.valueOf(1.03905)

        /**
         * Gamma, as recommended by Neal McBurnett for use in Colorado.
         */
        val COLORADO_GAMMA: BigDecimal = BigDecimal.valueOf(1.1)

        /**
         * Conservative estimate of error rates for one-vote over- and understatements.
         */
        val CONSERVATIVE_ONES_RATE: BigDecimal = BigDecimal.valueOf(0.01)

        /**
         * Conservative estimate of error rates for two-vote over- and understatements.
         */
        val CONSERVATIVE_TWOS_RATE: BigDecimal = BigDecimal.valueOf(0.01)

        /**
         * Conservative rounding up of 1-vote over/understatements for the initial
         * estimate of error rates.
         */
        const val CONSERVATIVE_ROUND_ONES_UP: Boolean = true

        /**
         * Conservative rounding up of 2-vote over/understatements for the initial
         * estimate of  error rates.
         */
        const val CONSERVATIVE_ROUND_TWOS_UP: Boolean = true

        /**
         * The gamma to use.
         */
        val GAMMA: BigDecimal = STARK_GAMMA

        /**
         * The initial estimate of error rates for one-vote over- and understatements.
         */
        val ONES_RATE: BigDecimal = BigDecimal.ZERO

        /**
         * The initial estimate of error rates for two-vote over- and understatements.
         */
        val TWOS_RATE: BigDecimal = BigDecimal.ZERO

        /**
         * The initial rounding up of 1-vote over/understatements.
         */
        const val ROUND_ONES_UP: Boolean = false

        /**
         * The initial rounding up of 2-vote over/understatements.
         */
        const val ROUND_TWOS_UP: Boolean = false
    }
}
