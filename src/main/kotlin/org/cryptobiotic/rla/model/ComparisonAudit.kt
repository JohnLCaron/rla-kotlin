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

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rla.math.Audit
import org.cryptobiotic.rla.persistence.HasId
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.*
import kotlin.math.max

/**
 * A class representing the state of a single audited contest for
 * across multiple counties
 */
data class ComparisonAudit(
    val id: Long,
    val version: Long,
    val contestResult: ContestResult,
    val auditReason: AuditReason,
): HasId {

    /**
     * Constructs a ComparisonAudit for the given params
     *
     * @param contestResult The contest result.
     * @param riskLimit The risk limit.
     * @param dilutedMargin μ
     * @param gamma γ
     * @param auditReason The audit reason.
     */
    // FIXME estimatedSamplesToAudit / optimisticSamplesToAudit have side effects, so we should call that out

    constructor(
        contestResult: ContestResult,
        riskLimit: BigDecimal,
        dilutedMargin: BigDecimal,
        gamma: BigDecimal,
        auditReason: AuditReason
    ): this(0L, 0L, contestResult, auditReason ) { // TODO no id or version
        this.myRiskLimit = riskLimit
        this.myDilutedMargin = dilutedMargin
        this.gamma = gamma

        // compute initial sample size
        optimisticSamplesToAudit()
        estimatedSamplesToAudit()

        if (contestResult.dilutedMargin.equals(BigDecimal.ZERO)) {
            // the diluted margin is 0, so this contest is not auditable
            auditStatus = AuditStatus.NOT_AUDITABLE
        }
    }

    private var auditStatus = AuditStatus.NOT_STARTED

    var gamma: BigDecimal = Audit.GAMMA

    var myDilutedMargin = BigDecimal.ONE

    var myRiskLimit = BigDecimal.ONE

    /** the number of ballots audited   */
    var auditedSampleCount: Int = 0

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
    private var my_disagreement_count = 0// FIXME

    /**
     *
     * The number of one-vote and two-vote overstatements across the set
     * of counties participating in this audit.
     *
     * TODO collect the number of 1 and 2 vote overstatements across
     * participating counties.
     */
    val overstatements: BigDecimal = BigDecimal.ZERO

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
     * The sequence of CastVoteRecord ids for this contest ordered by County id
     */
    private val contestCVRIds: MutableList<Long> = ArrayList()

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

    /** see if the county is participating in this audit(contest)  */
    fun isForCounty(countyId: Long): Boolean {
        return contestResult.counties.find { it.id == countyId } != null
    }

    /**
     * Does this audit belong to only a single county?
     */
    fun isSingleCountyFor(c: County): Boolean {
        return (contestResult.counties.size == 1) && isForCounty(c.id)
    }

    /**
     * Updates the audit status based on the current risk limit. If the audit
     * has already been ended or the contest is not auditable, this method has
     * no effect on its status.
     * Fix: RLA-00450
     */
    fun updateAuditStatus() {
        LOGGER.debug(
            java.lang.String.format(
                "[updateAuditStatus: %s for contest=%s "
                        + "my_optimistic_samples_to_audit=%d my_audited_sample_count=%d my_optimistic_recalculate_needed=%s my_estimated_recalculate_needed=%s]",
                auditStatus, contestResult.contestName,
                my_optimistic_samples_to_audit, auditedSampleCount,
                my_optimistic_recalculate_needed, my_estimated_recalculate_needed
            )
        )

        if (((auditStatus == AuditStatus.ENDED) || auditStatus == AuditStatus.HAND_COUNT) || auditStatus == AuditStatus.NOT_AUDITABLE) {
            return
        }

        if ((java.lang.Boolean.TRUE == my_optimistic_recalculate_needed) || java.lang.Boolean.TRUE == my_estimated_recalculate_needed) {
            recalculateSamplesToAudit()
        } //below calculation needs recalculate RLA-00450


        if (my_optimistic_samples_to_audit - auditedSampleCount <= 0) {
            LOGGER.debug(
                java.lang.String.format(
                    "[updateAuditStatus: RISK_LIMIT_ACHIEVED for contest=%s]",
                    contestResult.contestName
                )
            )
            auditStatus = AuditStatus.RISK_LIMIT_ACHIEVED
        } else {
            // risk limit has not been achieved
            // note that it _is_ possible to go from RISK_LIMIT_ACHIEVED to
            // IN_PROGRESS if a sample or set of samples is "unaudited"
            if (auditStatus == AuditStatus.RISK_LIMIT_ACHIEVED) {
                LOGGER.warn("[updateAuditStatus: Moving from RISK_LIMIT_ACHIEVED -> IN_PROGRESS!]")
            }

            auditStatus = AuditStatus.IN_PROGRESS
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

    /** estimatedSamplesToAudit minus getAuditedSampleCount  */
    fun estimatedRemaining(): Int {
        return max(0.0, (estimatedSamplesToAudit() - auditedSampleCount).toDouble()).toInt()
    }

    /** optimisticSamplesToAudit minus getAuditedSampleCount  */
    fun optimisticRemaining(): Int {
        return max(0.0, (optimisticSamplesToAudit() - auditedSampleCount).toDouble()).toInt()
    }

    /**
     * @return the expected overall number of ballots to audit, assuming
     * overstatements continue to occur at the current rate.
     */
    fun estimatedSamplesToAudit(): Int {
        if (my_estimated_recalculate_needed) {
            LOGGER.debug("[estimatedSampleToAudit: recalculate needed]")
            recalculateSamplesToAudit()
        }
        return my_estimated_samples_to_audit
    }

    /*
     * Risk limit achieved according to math.Audit.
     * This has a fallback to 1.0 (max risk) when ``nothing is known''.
     */
    fun riskMeasurement(): BigDecimal {
        if (auditedSampleCount > 0 && myDilutedMargin.compareTo(BigDecimal.ZERO) > 0) {
            val result =  Audit.pValueApproximation(auditedSampleCount,
                myDilutedMargin,
                gamma,
                my_one_vote_under_count,
                my_two_vote_under_count,
                my_one_vote_over_count,
                my_two_vote_over_count);
            return result.setScale(3, BigDecimal.ROUND_HALF_UP);
        } else {
            // full risk (100%) when nothing is known
            return BigDecimal.ONE;
        }
    }

    /**
     * A scaling factor for the estimate, from 1 (when no samples have
     * been audited) upward.41
     * The scaling factor grows as the ratio of
     * overstatements to samples increases.
     */
    private fun scalingFactor(): BigDecimal {
        val auditedSamples: BigDecimal = BigDecimal.valueOf(auditedSampleCount.toLong())
        return if (auditedSamples == BigDecimal.ZERO) {
            BigDecimal.ONE
        } else {
            BigDecimal.ONE.add(
                overstatements
                    .divide(auditedSamples, MathContext.DECIMAL128)
            )
        }
    }

    /**
     * Recalculates the overall numbers of ballots to audit, setting this
     * object's `my_optimistic_samples_to_audit` and
     * `my_estimates_samples_to_audit` fields.
     */
    private fun recalculateSamplesToAudit() {
        LOGGER.debug(
            java.lang.String.format(
                "[recalculateSamplestoAudit start contestName=%s, "
                        + "twoUnder=%d, oneUnder=%d, oneOver=%d, twoOver=%d"
                        + " optimistic=%d, estimated=%d]",
                contestResult.contestName,
                my_two_vote_under_count, my_one_vote_under_count,
                my_one_vote_over_count, my_two_vote_over_count,
                my_optimistic_samples_to_audit, my_estimated_samples_to_audit
            )
        )

        if (my_optimistic_recalculate_needed) {
            LOGGER.debug("[recalculateSamplesToAudit: calling computeOptimisticSamplesToAudit]")
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
            LOGGER.debug("[recalculateSamplesToAudit: zero overcounts]")
            my_estimated_samples_to_audit = my_optimistic_samples_to_audit
        } else {
            LOGGER.debug(
                String.format(
                    "[recalculateSamplesToAudit: non-zero overcounts, using scaling factor %s]",
                    scalingFactor()
                )
            )
            my_estimated_samples_to_audit =
                BigDecimal.valueOf(my_optimistic_samples_to_audit.toLong())
                    .multiply(scalingFactor())
                    .setScale(0, RoundingMode.CEILING)
                    .toInt()
        }

        LOGGER.debug(
            java.lang.String.format(
                "[recalculateSamplestoAudit end contestName=%s, "
                        + "twoUnder=%d, oneUnder=%d, oneOver=%d, twoOver=%d"
                        + " optimistic=%d, estimated=%d]",
                contestResult.contestName,
                my_two_vote_under_count, my_one_vote_under_count,
                my_one_vote_over_count, my_two_vote_over_count,
                my_optimistic_samples_to_audit, my_estimated_samples_to_audit
            )
        )
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
            myRiskLimit, myDilutedMargin, gamma,
            twoUnder, oneUnder, oneOver, twoOver
        )
    }

    /**
     * Signals that a sample has been audited. This ensures that estimates
     * are recalculated correctly and states are updated.
     *
     * @param count The count of samples that have been audited simultaneously
     * (for duplicates).
     */
    fun signalSampleAudited(count: Int) {
        my_estimated_recalculate_needed = true
        auditedSampleCount = auditedSampleCount + count

        // this may not be needed, but I'm not sure
        if (auditStatus == AuditStatus.RISK_LIMIT_ACHIEVED) {
            LOGGER.warn("RESETTING AuditStatus from RISK_LIMIT_ACHIEVED to IN_PROGRESS")
            auditStatus = AuditStatus.IN_PROGRESS
        }
    }

    /**
     * Signals that a sample has been audited, if the CVR was selected for
     * this audit and this audit is targeted (i.e., not for opportunistic
     * benefits.)
     *
     * @param count The count of samples that have been audited simultaneously
     * @param cvrID ID of the CVR being audited
     */
    fun signalSampleAudited(count: Int, cvrID: Long) {
        val covered = isCovering(cvrID)
        val targeted = isTargeted

        if (targeted && !covered) {
            LOGGER.debug(
                java.lang.String.format(
                    "[signalSampleAudited: %s is targeted, but cvrID (%d) not selected for audit.]",
                    contestResult.contestName, cvrID
                )
            )
        }

        if (targeted && covered) {
            LOGGER.debug(
                java.lang.String.format(
                    "[signalSampleAudited: targeted and covered! "
                            + "contestName=%s, cvrID=%d, auditedSamples=%d, count=%d]",
                    contestResult.contestName, cvrID, auditedSampleCount, count
                )
            )
            signalSampleAudited(count)
        }
    }

    /**
     * Signals that a sample has been unaudited. This ensures that estimates
     * are recalculated correctly and states are updated.
     *
     * @param the_count The count of samples that have been unaudited simultaneously
     * (for duplicates).
     */
    fun signalSampleUnaudited(count: Int) {
        my_estimated_recalculate_needed = true
        auditedSampleCount = auditedSampleCount - count

        // this may not be needed, but I'm not sure
        if (auditStatus == AuditStatus.RISK_LIMIT_ACHIEVED) {
            LOGGER.warn("RESETTING AuditStatus from RISK_LIMIT_ACHIEVED to IN_PROGRESS")
            auditStatus = AuditStatus.IN_PROGRESS
        }
    }


    /**
     * Signals that a sample has been unaudited, if the CVR was selected
     * for this audit.
     *
     * @param count The count of samples that have been unaudited simultaneously
     * (for duplicates).
     * @parma cvrID The ID of the CVR to unaudit
     */
    fun signalSampleUnaudited(count: Int, cvrID: Long) {
        LOGGER.debug(
            java.lang.String.format(
                "[signalSampleUnaudited: start "
                        + "contestName=%s, cvrID=%d, auditedSamples=%d, count=%d]",
                contestResult.contestName, cvrID, auditedSampleCount, count
            )
        )

        val covered = isCovering(cvrID)
        val targeted = isTargeted

        if (targeted && !covered) {
            LOGGER.debug(
                String.format(
                    "[signalSampleUnaudited: Targeted contest, but cvrID (%d) not selected.]",
                    cvrID
                )
            )
        }

        if (targeted && covered) {
            LOGGER.debug(
                java.lang.String.format(
                    "[signalSampleUnaudited: CVR ID [%d] is interesting to %s]",
                    cvrID, contestResult.contestName
                )
            )
            signalSampleUnaudited(count)
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

    /** was the given cvrid selected for this contest?  */
    fun isCovering(cvrId: Long): Boolean {
        return getContestCVRIds().contains(cvrId)
    }

    /**
     * Adds to the current collection of Contest CVR IDs
     * @param contestCVRIds a list
     */
    fun addContestCVRIds(contestCVRIds: List<Long>?) {
        this.contestCVRIds.addAll(contestCVRIds!!)
    }

    /**
     * getter
     */
    fun getContestCVRIds(): List<Long> {
        return this.contestCVRIds
    }

    val isTargeted: Boolean
        /**
         * Is this audit because of a targeted contest?
         */
        get() = (contestResult.auditReason.isTargeted()
                && !isHandCount)

    val isFinished: Boolean
        /**
         * Is an audit finished, or should we find more samples to compare?
         *
         */
        get() = ((this.auditStatus == AuditStatus.NOT_AUDITABLE) || (this.auditStatus == AuditStatus.RISK_LIMIT_ACHIEVED) || (this.auditStatus == AuditStatus.HAND_COUNT) || (this.auditStatus == AuditStatus.ENDED))

    val isHandCount: Boolean
        get() = (this.auditStatus == AuditStatus.HAND_COUNT)

    /** calculate the number of times the given cvrId appears in the selection
     * (across all rounds)
     */
    fun multiplicity(cvrId: Long?): Int {
        return Collections.frequency(getContestCVRIds(), cvrId)
    }

    /**
     * Records the specified discrepancy. If the discrepancy is for this Contest
     * but from a CVR/ballot that was not selected for this Contest (selected for
     * another Contest), is does not contribute to the counts and calculations. It
     * is still recorded, though, for informational purposes. The valid range is
     * -2 .. 2: -2 and -1 are understatements, 0 is a discrepancy that doesn't
     * affect the RLA calculations, and 1 and 2 are overstatements).
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

        if (isCovering(the_record.cvr.id)) {
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
        }

        LOGGER.info(
            java.lang.String.format(
                "[recordDiscrepancy type=%s, record=%s]",
                the_type, the_record
            )
        )
        my_discrepancies[the_record] = the_type
    }

    /**
     * get the discrepancy value that was recorded for this
     * ComparisonAudit(contest) on the given CVRAuditInfo(ballot). used for
     * reporting.
     */
    fun getDiscrepancy(cai: CVRAuditInfo): Int? {
        return my_discrepancies[cai]
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
     * @param info The CVRAuditInfo.
     * @return an optional int that is present if there is a discrepancy and absent
     * otherwise.
     */
    fun computeDiscrepancy(info: CVRAuditInfo): Int? {
        return computeDiscrepancy(info.cvr, info.acvr!!)
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

        // FIXME this needs to get this stuff from the ContestResult
        // - a CastVoteRecord belongs to a county.
        // - a CVRContestInfo belongs to a Contest, which belongs to a county.
        // - should we change the CVRContestInfo to belong to a ContestResult instead?
        //
        // The CVRContestInfo has teh list of choices. we need this for
        // winners and loser of the contest......BUT the ContestResult also
        // has a set of winners and losers, which is now the MOST ACCURATE
        // version of this, since we're now out of the county context...
        val cvr_info = cvr.contestInfoForContestResult(contestResult)
        val acvr_info = auditedCVR.contestInfoForContestResult(contestResult)

        if (auditedCVR.recordType == CastVoteRecord.RecordType.PHANTOM_BALLOT) {
            result = if (cvr_info != null) {
                computePhantomBallotDiscrepancy(cvr_info, contestResult)
            } else {
                //not sure why exactly, but that is what computePhantomBallotDiscrepancy
                //returns if winner_votes is empty, which it is, in this case, if it is
                //not present
                1
            }
        } else if (cvr.recordType == CastVoteRecord.RecordType.PHANTOM_RECORD) {
            // similar to the phantom ballot, we use the worst case scenario, a 2-vote
            // overstatement, except here, we don't have a CVR to check anything on.
            result = 2
        } else if (cvr_info != null && acvr_info != null) {
            result = if (acvr_info.consensus == CVRContestInfo.ConsensusValue.NO) {
                // a lack of consensus for this contest is treated
                // identically to a phantom ballot
                computePhantomBallotDiscrepancy(cvr_info, contestResult)
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
        // Overvotes are represented, perhaps confusingly, in the CVR as "all
        // zeroes" for the given contest - it will look indistinguishable from a
        // contest in which no selections were made. We therefore have to check if
        // the number of selections the audit board found is less than or equal to
        // the allowed votes for the given contest. If it is, then the audit board
        // found a valid selection and we can proceed with the rest of the math as
        // usual. If not, then the audit board recorded an overvote which we must
        // now make match the way the CVR format records overvotes: we must record
        // *no* selections. The code below does that by excluding the selections
        // submitted by the audit board.
        //
        // If the CVR does show an overvote (no selections counted) then our
        // zero-selection ACVR will match it and we will find no discrepancies. If,
        // however, the CVR *did* show a selection but the audit board recorded an
        // overvote, then we will be able to calculate the discrepancy - the CVR
        // will have a choice (or choices) marked as selected, but the ACVR will
        // not. The converse is also true: if the CVR shows an overvote but the
        // audit board records a valid selection, we will calculate an expected
        // discrepancy.
        val acvr_choices: MutableSet<String> = HashSet()
        if (the_acvr_info.choices.size <= contestResult.winnersAllowed) {
            acvr_choices.addAll(the_acvr_info.choices)
        }

        // avoid linear searches on CVR choices
        val cvr_choices = the_cvr_info.choices.toSet()
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
        // FIXME contestResult is global to this object. I'd rather it
        // be an argument to this function.
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
            "unable to compute discrepancy in contest " +
                    contestResult.contestName
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
     * Computes the discrepancy between a phantom ballot and the specified CVRContestInfo.
     * @return The number of discrepancies
     */
    private fun computePhantomBallotDiscrepancy(
        cvrInfo: CVRContestInfo,
        contestResult: ContestResult
    ): Int {
        var result = 2
        // the second predicate means "no contest winners had votes on the
        // original CVR"
        val winner_votes: MutableSet<String> = cvrInfo.choices.toMutableSet()
        winner_votes.removeAll(contestResult.losers)
        if (winner_votes.isEmpty()) {
            result = 1
        }
        return result
    }

    /**
     * a good idea
     */
    override fun toString(): String {
        return java.lang.String.format(
            "[ComparisonAudit for %s: counties=%s, auditedSampleCount=%d, overstatements=%f,"
                    + " contestResult.contestCvrIds=%s, status=%s, reason=%s]",
            contestResult.contestName,
            contestResult.counties,
            this.auditedSampleCount,
            this.overstatements,
            this.getContestCVRIds(),
            auditStatus,
            this.auditReason,
        )
    }

    override fun id() = id

    companion object {
        /**
         * Class-wide logger
         */
        val LOGGER = KotlinLogging.logger("ComparisonAudit")

        /**
         * The database stored precision for decimal types.
         */
        const val PRECISION: Int = 10

        /**
         * The database stored scale for decimal types.
         */
        const val SCALE: Int = 8
    }
}
