package org.cryptobiotic.rla.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rla.dashboard.CountyDashboard
import org.cryptobiotic.rla.dashboard.Round
import org.cryptobiotic.rla.math.Audit
import org.cryptobiotic.rla.model.AuditReason
import org.cryptobiotic.rla.model.CVRAuditInfo
import org.cryptobiotic.rla.model.CVRContestInfo.ConsensusValue
import org.cryptobiotic.rla.model.CastVoteRecord
import org.cryptobiotic.rla.model.ComparisonAudit
import org.cryptobiotic.rla.model.ContestResult
import org.cryptobiotic.rla.persistence.Persistence
import java.lang.IndexOutOfBoundsException
import java.lang.String
import java.math.BigDecimal
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import kotlin.math.max

/**
 * Controller methods relevant to comparison audits.
 *
 * @author Daniel M. Zimmerman <dmz></dmz>@freeandfair.us>
 * @version 1.0.0
 */
object ComparisonAuditController {
        val LOGGER = KotlinLogging.logger("ComparisonAuditController")

    /**
     * Gets all CVRs to audit in the specified round for the specified county
     * dashboard. This returns a list in audit random sequence order.
     *
     * @param the_dashboard The dashboard.
     * @param the_round_number The round number (indexed from 1).
     * @return the CVRs to audit in the specified round.
     * @exception IllegalArgumentException if the specified round doesn't exist.
     */
    fun cvrsToAuditInRound(
        the_cdb: CountyDashboard,
        the_round_number: Int
    ): MutableList<CVRAuditInfo?> {
        require(!(the_round_number < 1 || the_cdb.rounds().size < the_round_number)) { "invalid round specified" }
        val round: Round = the_cdb.rounds().get(the_round_number - 1)
        val id_set: MutableSet<Long?> = HashSet<Long?>()
        val result: MutableList<CVRAuditInfo?> = ArrayList<CVRAuditInfo?>()

        for (cvr_id in round.auditSubsequence) {
            if (!id_set.contains(cvr_id)) {
                id_set.add(cvr_id)
                result.add(Persistence.getByID(cvr_id, CVRAuditInfo::class.java))
            }
        }

        return result
    }

    /**
     * @return the CVR IDs remaining to audit in the current round, or an empty
     * list if there are no CVRs remaining to audit or if no round is in progress.
     */
    fun cvrIDsRemainingInCurrentRound(the_cdb: CountyDashboard): MutableList<Long?> {
        val result: MutableList<Long?> = ArrayList<Long?>()
        val round: Round? = the_cdb.currentRound()
        if (round != null) {
            var i = 0
            while (i + round.actualAuditedPrefixLength < round.expectedAuditedPrefixLength
            ) {
                result.add(round.auditSubsequence.get(i + round.actualAuditedPrefixLength))
                i++
            }
        }
        return result
    }

    /**
     * Return the ballot cards to audit for a particular county and round.
     *
     * The returned list will not have duplicates and is in an undefined order.
     *
     * @param countyDashboard county dashboard owning the rounds
     * @param roundNumber 1-based round number
     *
     * @return the list of ballot cards for audit. If the query does not result in
     * any ballot cards, for instance when the round number is invalid,
     * the returned list is empty.
     */
    fun ballotsToAudit(
        countyDashboard: CountyDashboard,
        roundNumber: Int
    ): List<CastVoteRecord> {
        val rounds: List<Round> = countyDashboard.rounds()
        var round: Round

        try {
            // roundNumber is 1-based
            round = rounds.get(roundNumber - 1)
        } catch (e: IndexOutOfBoundsException) {
            return ArrayList<CastVoteRecord>()
        }

        LOGGER.debug(
            String.format(
                "Ballot cards to audit: "
                        + "[round=%s, round.ballotSequence.size()=%d, round.ballotSequence()=%s]",
                round,
                round.ballotSequence.size,
                round.ballotSequence
            )
        )

        // Get all ballot cards for the target round
        val cvrs: List<CastVoteRecord> = CastVoteRecordQueries.get(round.ballotSequence)

        // Fetch the CVRs from previous rounds in order to set a flag determining
        // whether they had been audited previously.
        val previousCvrs: MutableSet<CastVoteRecord?> = HashSet<CastVoteRecord?>()
        for (i in 1 until roundNumber) {
            // i is 1-based
            val r: Round = rounds.get(i - 1)
            previousCvrs.addAll(CastVoteRecordQueries.get(r.ballotSequence))
        }

        // PERF TODO: We may be able to replace calls to `audited` with a query that
        // determines the audit status of all the CVRs when they are fetched.
        for (cvr in cvrs) {
            cvr.setAuditFlag(audited(countyDashboard, cvr))
            cvr.setPreviouslyAudited(previousCvrs.contains(cvr))
        }

        return cvrs
    }

    /**
     * Creates a ComparisonAudit (of the appropriate type - either IRV or plurality) for the given contest and risk limit.
     * No data is persisted. Used both for auditing and sample size estimation.
     *
     * @param contestResult   Contest result for the contest.
     * @param riskLimit       Risk limit for the audit.
     * @return ComparisonAudit of the appropriate type for the given contest.
     */
    fun createAuditOfCorrectType(
        contestResult: ContestResult,
        riskLimit: BigDecimal
    ): ComparisonAudit {
        val prefix = "[createAuditOfCorrectType]"

        // If it is all plurality, make a (plurality) ComparisonAudit. This will also be true if the contestResult has no contests.
        //if (contestResult.contests.map{ it.description }
        //        .allMatch({ d -> d.equals(ContestType.PLURALITY.toString()) })
        //) {
            return ComparisonAudit(
                contestResult,
                riskLimit,
                contestResult.dilutedMargin,
                Audit.GAMMA,
                contestResult.auditReason
            )
        //}

        /* If it is all IRV, make an IRVComparisonAudit.
        if (contestResult.getContests().stream().map(Contest::description)
                .allMatch({ d -> d.equals(ContestType.IRV.toString()) })
        ) {
            return IRVComparisonAudit(contestResult, riskLimit, contestResult.getAuditReason())
        }

        // If it is a mix of different types of contests, or a contest type that we don't recognize, that is an error.
        val msg = "$prefix Contest ${contestResult.contestName} has inconsistent or unrecognized contest types."
        LOGGER.error(msg)
        throw RuntimeException(msg)

         */
    }

    /**
     * Creates a ComparisonAudit object for the given contest, and persists the object in the database.
     *
     * @param contestResult  Contest result for the contest.
     * @param riskLimit      Risk limit for the audit.
     * @return ComparisonAudit for the contest.
     */
    fun createAudit(
        contestResult: ContestResult,
        riskLimit: BigDecimal
    ): ComparisonAudit {
        val ca: ComparisonAudit = createAuditOfCorrectType(contestResult, riskLimit)

        Persistence.save(ca)
        LOGGER.debug(String.format("[createAudit: contestResult=%s, ComparisonAudit=%s]", contestResult, ca))

        return ca
    }

    /**
     * Do the part of setup for a county dashboard to start their round.
     * - updateRound
     * - updateCVRUnderAudit
     */
    fun startRound(
        cdb: CountyDashboard,
        audits: Set<ComparisonAudit>,
        auditSequence: List<Long>,
        ballotSequence: List<Long>
    ): Boolean {
        LOGGER.info(
            String.format(
                "Starting a round for %s, drivingContests=%s",
                cdb.county, cdb.drivingContestNames()
            )
        )
        cdb.startRound(
            ballotSequence.size, auditSequence.size,
            0, ballotSequence, auditSequence
        )
        // FIXME it appears these two must happen in this order.
        updateRound(cdb, cdb.currentRound()!!)
        updateCVRUnderAudit(cdb)

        // if the round was started there will be ballots to count
        return cdb.ballotsRemainingInCurrentRound() > 0
    }


    /** unaudit and audit a submitted ACVR  */
    fun reaudit(
        cdb: CountyDashboard,
        cvr: CastVoteRecord,
        newAcvr: CastVoteRecord,
        comment: kotlin.String? = null
    ): Boolean {
        LOGGER.info("[reaudit] cvr: " + cvr.toString())

        // DemocracyDevelopers: If this cvr has not been audited before, cai will be null.
        val cai: CVRAuditInfo? = Persistence.getByID(cvr.id, CVRAuditInfo::class.java)
        if (cai == null || cai.acvr == null) {
            LOGGER.error("can't reaudit a cvr that hasn't been audited")
            return false
        }
        val oldAcvr: CastVoteRecord = cai.acvr

        val former_count = unaudit(cdb, cai)
        LOGGER.debug("[reaudit] former_count: " + former_count.toString())

        var revision: Long = CastVoteRecordQueries.maxRevision(cvr)
        // sets revision to 1 if this is the original(revision is zero)
        if (0L == revision) {
            revision = 1L
            oldAcvr.setRevision(revision)
        }
        oldAcvr.setToReaudited()
        CastVoteRecordQueries.forceUpdate(oldAcvr)

        // the original will not have a re-audit comment
        newAcvr.setComment(comment)

        // sets revision to 2 if this is the first revision(revision is zero)
        newAcvr.setRevision(revision + 1L)
        val newcai = cai.copy(acvr = newAcvr)
        Persistence.save(newAcvr)
        Persistence.save(newcai) // TODO make sure this replaces old one

        val new_count = audit(cdb, newcai, true)
        LOGGER.debug("[reaudit] new_count: " + new_count.toString())
        cdb.updateAuditStatus()

        return true
    }


    /**
     * Submit an audit CVR for a CVR under audit to the specified county dashboard.
     *
     * @param cdb The dashboard.
     * @param the_cvr_under_audit The CVR under audit.
     * @param the_audit_cvr The corresponding audit CVR.
     * @return true if the audit CVR is submitted successfully, false if it doesn't
     * correspond to the CVR under audit, or the specified CVR under audit was
     * not in fact under audit.
     */
    //@ require the_cvr_under_audit != null;
    //@ require the_acvr != null;
    fun submitAuditCVR(
        cdb: CountyDashboard,
        the_cvr_under_audit: CastVoteRecord,
        the_audit_cvr: CastVoteRecord
    ): Boolean {
        // performs a sanity check to make sure the CVR under audit and the ACVR
        // are the same card
        var result = false

        val info: CVRAuditInfo? =
            Persistence.getByID(the_cvr_under_audit.id, CVRAuditInfo::class.java)

        if (info == null) {
            LOGGER.warn(
                (("attempt to submit ACVR for county " +
                        cdb.id).toString() + ", cvr " +
                        the_cvr_under_audit.id).toString() + " not under audit"
            )
        } else if (checkACVRSanity(the_cvr_under_audit, the_audit_cvr)) {
            LOGGER.trace("[submitAuditCVR: ACVR seems sane]")
            // if the record is the current CVR under audit, or if it hasn't been
            // audited yet, we can just process it
            if (info.acvr == null) {
                // this audits all instances of the ballot in our current sequence;
                // they might be out of order, but that's OK because we have strong
                // requirements about finishing rounds before looking at results as
                // final and valid
                LOGGER.trace("[submitAuditCVR: ACVR is null, creating]")
                val newinfo = info.copy(acvr = the_audit_cvr) // LOOK update Persistence?
                // info.setACVR(the_audit_cvr)
                val new_count = audit(cdb, newinfo, true)
                cdb.addAuditedBallot()
                // there could be a problem here, maybe the cdb counts for all contests
                // and that is good enough??
                cdb.setAuditedSampleCount(cdb.auditedSampleCount() + new_count)
            } else {
                // the record has been audited before, so we need to "unaudit" it
                LOGGER.trace("[submitAuditCVR: ACVR is seen, un/reauditing]")
                val former_count = unaudit(cdb, info)
                val newinfo = info.copy(acvr = the_audit_cvr) // TODO update Persistence?
                // info.setACVR(the_audit_cvr)
                val new_count = audit(cdb, newinfo, true)
                cdb.setAuditedSampleCount(cdb.auditedSampleCount() - former_count + new_count)
            }
            result = true
        } else {
            LOGGER.warn(
                (("attempt to submit non-corresponding ACVR " +
                        the_audit_cvr.id).toString() + " for county " + cdb.id).toString() +
                        ", cvr " + the_cvr_under_audit.id
            )
        }
        Persistence.flush()

        LOGGER.trace(
            String.format(
                "[Before recalc: auditedSampleCount=%d, estimatedSamples=%d, optimisticSamples=%d",
                cdb.auditedSampleCount(),
                cdb.estimatedSamplesToAudit(),
                cdb.optimisticSamplesToAudit()
            )
        )
        updateCVRUnderAudit(cdb)
        LOGGER.trace(
            String.format(
                "[After recalc: auditedSampleCount=%d, estimatedSamples=%d, optimisticSamples=%d",
                cdb.auditedSampleCount(),
                cdb.estimatedSamplesToAudit(),
                cdb.optimisticSamplesToAudit()
            )
        )
        cdb.updateAuditStatus()
        return result
    }

    /**
     * Computes the estimated total number of samples to audit on the specified
     * county dashboard. This uses the minimum samples to audit calculation,
     * increased by the percentage of discrepancies seen in the audited ballots
     * so far.
     *
     * @param cdb The dashboard.
     */
    fun estimatedSamplesToAudit(cdb: CountyDashboard): Int {
        var to_audit = Int.Companion.MIN_VALUE
        val drivingContests = cdb.drivingContestNames()

        // FIXME might look better as a stream().filter().
        for (ca in cdb.comparisonAudits()) { // to_audit = cdb.comparisonAudits.stream()
            val contestName: kotlin.String? = ca.contestResult.contestName // strike
            if (drivingContests.contains(contestName)) { // .filter(ca -> drivingContests.contains(ca.contestResult.contestName))
                val bta: Int = ca.estimatedSamplesToAudit() // .map(ComparisonAudit::estimatedSamplesToAudit)
                to_audit = max(to_audit, bta) // .max() gets the biggest of all driving contest estimated samples
                LOGGER.debug(
                    String.format(
                        "[estimatedSamplesToAudit: "
                                + "driving contest=%s, bta=%d, to_audit=%d]",
                        ca.contestResult.contestName, bta, to_audit
                    )
                )
            }
        }
        return max(0, to_audit)
    }

    /**
     * Checks to see if the specified CVR has been audited on the specified county
     * dashboard. This check sets the audit flag on the CVR record in memory,
     * so its result can be accessed later without an expensive database hit.
     *
     * @param the_cdb The county dashboard.
     * @param the_cvr The CVR.
     * @return true if the specified CVR has been audited, false otherwise.
     */
    fun audited(
        the_cdb: CountyDashboard?,
        the_cvr: CastVoteRecord
    ): Boolean {
        val info: CVRAuditInfo? = Persistence.getByID(the_cvr.id, CVRAuditInfo::class.java)
        val result: Boolean
        if (info == null || info.acvr == null) {
            result = false
        } else {
            result = true
        }
        return result
    }

    /**
     * Updates a round object with the disagreements and discrepancies
     * that already exist for CVRs in its audit subsequence, creates
     * any CVRAuditInfo objects that don't exist but need to, and
     * increases the multiplicity of any CVRAuditInfo objects that already
     * exist and are duplicated in this round.
     *
     * @param cdb The county dashboard to update.
     * @param round The round to update.
     */
    private fun updateRound(
        cdb: CountyDashboard,
        round: Round
    ) {
        // org for (cvrID in HashSet<Any?>(round.auditSubsequence())) {
        for (cvrID in round.auditSubsequence) {
            val auditReasons: MutableMap<kotlin.String?, AuditReason?> = HashMap<kotlin.String?, AuditReason?>()
            val discrepancies = mutableSetOf<AuditReason>()
            val disagreements = mutableSetOf<AuditReason>()

            var cvrai: CVRAuditInfo? = Persistence.getByID(cvrID, CVRAuditInfo::class.java)
            if (cvrai == null) {
                val wtf: CastVoteRecord? = Persistence.getByID(cvrID, CastVoteRecord::class.java)
                cvrai = CVRAuditInfo(wtf!!)
            }

            if (cvrai.acvr != null) {
                val acvrLocal = cvrai.acvr!!

                // do the thing
                // update the round statistics as necessary
                for (ca in cdb.comparisonAudits()) {
                    val contestName: kotlin.String? = ca.contestResult.contestName
                    var auditReason: AuditReason = ca.auditReason

                    if (ca.isCovering(cvrID) && auditReason.isTargeted()) {
                        // If this CVR is interesting to this audit, the discrepancy
                        // should be in the audited contests part of the dashboard.
                        LOGGER.debug(
                            String.format(
                                "[updateRound: CVR %d is covered in a targeted audit."
                                        + " contestName=%s, auditReason=%s]",
                                cvrID, contestName, auditReason
                            )
                        )
                        auditReasons.put(contestName, auditReason)
                    } else {
                        // Otherwise, let's put it in the unaudited contest bucket.
                        auditReason = AuditReason.OPPORTUNISTIC_BENEFITS
                        LOGGER.debug(
                            String.format(
                                "[updateRound: CVR %d has a discrepancy; not covered by"
                                        + " contestName=%s, auditReason=%s]",
                                cvrID, contestName, auditReason
                            )
                        )
                        auditReasons.put(contestName, auditReason)
                    }

                    val discrepancy: Int? = ca.computeDiscrepancy(cvrai.cvr, acvrLocal)
                    if (!discrepancies.contains(auditReason) && discrepancy != null) {
                        discrepancies.add(auditReason)
                    }


                    val multiplicity: Int = ca.multiplicity(cvrID)
                    for (i in 0 until multiplicity) {
                        round.addDiscrepancy(discrepancies)
                        round.addDisagreement(disagreements)
                    }

                    cvrai.setMultiplicityByContest(ca.id, multiplicity)
                }

                for (ci in acvrLocal.contestInfo) {
                    val reason: AuditReason? = auditReasons.get(ci.contest.name)
                    if (ci.consensus === ConsensusValue.NO) {
                        // TODO check to see if we have disagreement problems. this is being added in the other loop.
                        if (reason != null) disagreements.add(reason)
                    }
                }
            }

            Persistence.saveOrUpdate(cvrai)
        }
    }

    /**
     * Audits a CVR/ACVR pair by adding it to all the audits in progress.
     * This also updates the local audit counters, as appropriate.
     *
     * @param cdb The dashboard.
     * @param auditInfo The CVRAuditInfo to audit.
     * @param updateCounters true to update the county dashboard
     * counters, false otherwise; false is used when this ballot
     * has already been audited once.
     * @return the number of times the record was audited.
     */
    private fun audit(
        cdb: CountyDashboard,
        auditInfo: CVRAuditInfo,
        updateCounters: Boolean
    ): Int {
        val contestDisagreements: MutableSet<kotlin.String> = HashSet<kotlin.String>()
        val discrepancies: MutableSet<AuditReason> = HashSet<AuditReason>()
        val disagreements: MutableSet<AuditReason> = HashSet<AuditReason>()
        val cvrUnderAudit: CastVoteRecord = auditInfo.cvr
        val cvrID: Long = cvrUnderAudit.id
        val auditCvr: CastVoteRecord = auditInfo.acvr!!
        var totalCount = 0

        // discrepancies
        for (ca in cdb.comparisonAudits()) {
            var auditReason: AuditReason = ca.auditReason
            val contestName: kotlin.String? = ca.contestResult.contestName

            // how many times does this cvr appear in the audit samples; how many dups?
            val multiplicity: Int = ca.multiplicity(cvrID)

            // how many times does a discrepancy need to be recorded, while counting
            // each sample(or occurance) only once - across rounds
            val auditCount: Int = multiplicity - auditInfo.getCountByContest(ca.id)

            // to report something to the caller
            totalCount += auditCount

            auditInfo.setMultiplicityByContest(ca.id, multiplicity)
            auditInfo.setCountByContest(ca.id, multiplicity)

            val discrepancy: Int? = ca.computeDiscrepancy(cvrUnderAudit, auditCvr)
            if (discrepancy != null) {
                for (i in 0 until auditCount) {
                    ca.recordDiscrepancy(auditInfo, discrepancy)
                }

                if (ca.isCovering(cvrID) && auditReason.isTargeted()) {
                    LOGGER.debug(
                        String.format(
                            "[audit: CVR %d is covered in a targeted audit."
                                    + " contestName=%s, auditReason=%s]",
                            cvrID, contestName, auditReason
                        )
                    )
                    discrepancies.add(auditReason)
                } else {
                    auditReason = AuditReason.OPPORTUNISTIC_BENEFITS
                    LOGGER.debug(
                        String.format(
                            "[audit: CVR %d has a discrepancy, but isn't covered by"
                                    + " contestName=%s, auditReason=%s]",
                            cvrID, contestName, auditReason
                        )
                    )
                    discrepancies.add(auditReason)
                }
            }

            // disagreements
            for (ci in auditCvr.contestInfo) {
                if (ci.consensus === ConsensusValue.NO) {
                    contestDisagreements.add(ci.contest.name)
                }
            }

            // NOTE: this may or may not be correct, we're not sure
            if (contestDisagreements.contains(contestName)) {
                for (i in 0 until auditCount) {
                    ca.recordDisagreement(auditInfo)
                }
                if (ca.isCovering(cvrID) && auditReason.isTargeted()) {
                    LOGGER.debug(
                        String.format(
                            "[audit: CVR %d is covered in a targeted audit."
                                    + " contestName=%s, auditReason=%s]",
                            cvrID, contestName, auditReason
                        )
                    )
                    disagreements.add(auditReason)
                } else {
                    auditReason = AuditReason.OPPORTUNISTIC_BENEFITS
                    LOGGER.debug(
                        String.format(
                            "[audit: CVR %d has a disagreement, but isn't covered by"
                                    + " contestName=%s, auditReason=%s]",
                            cvrID, contestName, auditReason
                        )
                    )
                    disagreements.add(auditReason)
                }
            }

            ca.signalSampleAudited(auditCount, cvrID)
            Persistence.saveOrUpdate(ca)
        }

        // todo does this need to be in the loop?
        auditInfo.setDiscrepancy(discrepancies)
        auditInfo.setDisagreement(disagreements)
        Persistence.saveOrUpdate(auditInfo)

        if (updateCounters) {
            cdb.addDiscrepancy(discrepancies)
            cdb.addDisagreement(disagreements)
            LOGGER.debug(
                String.format(
                    "[audit: %s County discrepancies=%s, disagreements=%s]",
                    cdb.county.name, discrepancies, disagreements
                )
            )
        }

        return totalCount
    }

    /**
     * "Unaudits" a CVR/ACVR pair by removing it from all the audits in
     * progress in the specified county dashboard. This also updates the
     * dashboard's counters as appropriate.
     *
     * @param the_cdb The county dashboard.
     * @param the_info The CVRAuditInfo to unaudit.
     */
    private fun unaudit(the_cdb: CountyDashboard, the_info: CVRAuditInfo): Int {
        val contest_disagreements: MutableSet<kotlin.String?> = HashSet<kotlin.String?>()
        val discrepancies = mutableSetOf<AuditReason>()
        val disagreements = mutableSetOf<AuditReason>()
        val cvr_under_audit: CastVoteRecord = the_info.cvr
        val cvrID: Long = cvr_under_audit.id
        val audit_cvr: CastVoteRecord = the_info.acvr!!
        var totalCount = 0

        for (ci in audit_cvr.contestInfo) {
            if (ci.consensus === ConsensusValue.NO) {
                contest_disagreements.add(ci.contest.name)
            }
        }

        for (ca in the_cdb.comparisonAudits()) {
            var auditReason: AuditReason = ca.auditReason
            val contestName: kotlin.String? = ca.contestResult.contestName

            // how many times does this cvr appear in the audit samples; how many dups?
            val multiplicity: Int = ca.multiplicity(cvr_under_audit.id)

            // if the cvr has been audited, which is must have been to be here, then
            val auditCount = multiplicity

            // to report something to the caller
            totalCount += auditCount

            val discrepancy: Int? = ca.computeDiscrepancy(cvr_under_audit, audit_cvr)
            if (discrepancy != null) {
                for (i in 0 until auditCount) {
                    ca.removeDiscrepancy(the_info, discrepancy) // TODO wtf??
                }

                if (ca.isCovering(cvrID) && auditReason.isTargeted()) {
                    LOGGER.debug(
                        String.format(
                            "[audit: CVR %d is covered in a targeted audit."
                                    + " contestName=%s, auditReason=%s]",
                            cvrID, contestName, auditReason
                        )
                    )
                    discrepancies.add(auditReason)
                } else {
                    auditReason = AuditReason.OPPORTUNISTIC_BENEFITS
                    LOGGER.debug(
                        String.format(
                            "[audit: CVR %d has a discrepancy, but isn't covered by"
                                    + " contestName=%s, auditReason=%s]",
                            cvrID, contestName, auditReason
                        )
                    )
                    discrepancies.add(auditReason)
                }
            }
            if (contest_disagreements.contains(ca.contestResult.contestName)) {
                for (i in 0 until auditCount) {
                    ca.removeDisagreement(the_info) // TODO wtf??
                }
                if (ca.isCovering(cvrID) && auditReason.isTargeted()) {
                    LOGGER.debug(
                        String.format(
                            "[audit: CVR %d is covered in a targeted audit."
                                    + " contestName=%s, auditReason=%s]",
                            cvrID, contestName, auditReason
                        )
                    )
                    disagreements.add(auditReason)
                } else {
                    auditReason = AuditReason.OPPORTUNISTIC_BENEFITS
                    LOGGER.debug(
                        String.format(
                            "[audit: CVR %d has a disagreement, but isn't covered by"
                                    + " contestName=%s, auditReason=%s]",
                            cvrID, contestName, auditReason
                        )
                    )
                    disagreements.add(auditReason)
                }
            }
            ca.signalSampleUnaudited(auditCount, cvr_under_audit.id)
            Persistence.saveOrUpdate(ca)
        }

        the_info.setDisagreement(null)
        the_info.setDiscrepancy(null)
        the_info.resetCounted()
        Persistence.saveOrUpdate(the_info)

        the_cdb.removeDiscrepancy(discrepancies)
        the_cdb.removeDisagreement(disagreements)

        return totalCount
    }

    /**
     * Updates the current CVR to audit index of the specified county
     * dashboard to the first CVR after the current CVR under audit that
     * lacks an ACVR. This "audits" all the CVR/ACVR pairs it finds
     * in between, and extends the sequence of ballots to audit if it
     * reaches the end and the audit is not concluded.
     *
     * @param cdb The dashboard.
     */
    fun updateCVRUnderAudit(cdb: CountyDashboard) {
        // start from where we are in the current round
        val round: Round? = cdb.currentRound()

        if (round != null) {
            val checked_ids: MutableSet<Long?> = HashSet<Long?>()
            var index: Int = round.actualAuditedPrefixLength - round.startAuditedPrefixLength

            while (index < round.auditSubsequence.size) {
                val cvr_id: Long? = round.auditSubsequence.get(index)
                if (cvr_id != null && !checked_ids.contains(cvr_id)) {
                    checked_ids.add(cvr_id)

                    val cai: CVRAuditInfo? = Persistence.getByID(cvr_id, CVRAuditInfo::class.java)

                    if (cai == null || cai.acvr == null) {
                        break // ok, so this hasn't been audited yet.
                    } else {
                        val audit_count = audit(cdb, cai, false)
                        cdb.setAuditedSampleCount(cdb.auditedSampleCount() + audit_count)
                    }
                }
                index = index + 1
            }
            // FIXME audited prefix length might not mean the same things that
            // it once meant.
            cdb.setAuditedPrefixLength(index + round.startAuditedPrefixLength)
            cdb.updateAuditStatus()
        }
    }

    /**
     * Checks that the specified CVR and ACVR are an audit pair, and that
     * the specified ACVR is auditor generated.
     *
     * @param the_cvr The CVR.
     * @param the_acvr The ACVR.
     */
    private fun checkACVRSanity(
        the_cvr: CastVoteRecord,
        the_acvr: CastVoteRecord
    ): Boolean {
        return the_cvr.isAuditPairWith(the_acvr) &&
                (the_acvr.recordType.isAuditorGenerated
                        || the_acvr.recordType.isSystemGenerated)
    }
}

