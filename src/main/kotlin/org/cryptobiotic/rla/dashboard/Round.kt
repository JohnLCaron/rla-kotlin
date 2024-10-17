/*
 * Free & Fair Colorado RLA System
 *
 * @title ColoradoRLA
 * @created Jul 25, 2017
 * @copyright 2017 Colorado Department of State
 * @license SPDX-License-Identifier: AGPL-3.0-or-later
 * @creator Joey Dodds <jdodds@galois.com>
 * @model_review Joseph R. Kiniry <kiniry@freeandfair.us>
 * @description A system to assist in conducting statewide risk-limiting audits.
 */
package org.cryptobiotic.rla.dashboard

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rla.model.AuditReason
import org.cryptobiotic.rla.model.AuditSelection
import org.cryptobiotic.rla.model.Elector
import java.time.Instant
import java.util.*

/**
 * Information about an audit round.
 *
 * @author Daniel M. Zimmerman <dmz></dmz>@freeandfair.us>
 * @version 1.0.0
 */
data class Round(
    val number: Int,
    val startTime: Instant,
    var endTime: Instant? = null,
    val expectedCount: Int,
    var actualCount: Int,
    val expectedAuditedPrefixLength: Int,
    var actualAuditedPrefixLength: Int,
    val startAuditedPrefixLength: Int,
    val previousBallotsAudited: Int,
    val ballotSequence: List<Long>,
) {

    //
    // may be a secondary constructor
    //   public Round(final Integer the_number,
    //               final Instant the_start_time,
    //               final Integer the_expected_count,
    //               final Integer the_previous_ballots_audited,
    //               final Integer the_expected_audited_prefix_length,
    //               final Integer the_start_audited_prefix_length,
    //               final List<Long> the_ballot_sequence,
    //               final List<Long> the_audit_subsequence) {
    //    super();
    //    my_number = the_number;
    //    my_start_time = the_start_time;
    //    my_expected_count = the_expected_count;
    //    my_expected_audited_prefix_length = the_expected_audited_prefix_length;
    //    my_actual_count = 0;
    //    my_start_audited_prefix_length = the_start_audited_prefix_length;
    //    my_actual_audited_prefix_length = the_start_audited_prefix_length;
    //    my_previous_ballots_audited = the_previous_ballots_audited;
    //    my_ballot_sequence = the_ballot_sequence;
    //    my_audit_subsequence = the_audit_subsequence;
    //  }

    // original code
    //     final Round round = new Round(my_current_round_index + 1,
    //                                  Instant.now(),
    //                                  numberOfBallots,
    //                                  my_ballots_audited,
    //                                  prefixLength,
    //                                  startIndex,
    //                                  ballotSequence,
    //                                  auditSubsequence);

    constructor(
        round_number: Int,
        startTime: Instant,
        expectedCount: Int, // numberOfBallots
        previousBallotsAudited: Int, // my_ballots_audited
        expectedAuditedPrefixLength: Int, // prefixLength
        startAuditedPrefixLength: Int, // startIndex
        ballotSequence: List<Long>, // the_ballot_sequence
    ) : this(
        round_number,
            startTime,
            null,
            expectedCount,
            0,
            expectedAuditedPrefixLength,
            startAuditedPrefixLength,
            startAuditedPrefixLength,
            previousBallotsAudited,
            ballotSequence
        )

    /**
     * The assignment of work from the ballot sequence to each audit board.
     *
     * Audit boards are represented by the indices of the list, and each entry in
     * the list is a data structure as follows:
     *
     * ({"index": 0, "count": 5}, {"index": 5, "count": 6} ...)
     *
     * where "index" represents the index into the ballot sequence list, and
     * "count" represents the number of ballots assigned to that audit board.
     */
    private var ballotSequenceAssignment: List<Map<String, Int>>? = null

    /**
     * The CVR IDs for the audit subsequence to audit in this
     * round, in audit sequence order.
     */
    var auditSubsequence: List<Long> = emptyList()

    /**
     * The number of discrepancies found in the audit so far.
     */
    private var my_discrepancies: MutableMap<AuditSelection, Int> = HashMap()

    /**
     * The number of disagreements found in the audit so far.
     */
    private var my_disagreements: MutableMap<AuditSelection, Int> = HashMap()

    /**
     * The signatories for round sign-off.
     *
     * This is a map from audit board index to list of signatories that were part
     * of that audit board.
     */
    private var my_signatories: MutableMap<Int, List<Elector>> = HashMap()

    /**
     * Returns the list of CVRs under audit in this round.
     *
     * @return a list whose indices correspond to audit board indices and values
     * being the next CVR for the given audit board to audit.
     *
    // TODO: Extract into query class
    // FIXME did we duplicate this ever?
    fun cvrsUnderAudit(): List<Long?> {
        val bsa = this.ballotSequenceAssignment ?: return ArrayList()

        val bs = this.ballotSequence

        if (bs!!.isEmpty()) {
            // avoid psql exception
            return ArrayList()
        }

        // All CVR IDs that have no corresponding ACVR
        val s: Session = Persistence.currentSession()
        val q: Query = s.createQuery(
            "select cvrai.my_cvr.my_id from CVRAuditInfo cvrai " +
                    "where cvrai.my_cvr.my_id in (:ids) " +
                    "and cvrai.my_acvr is null"
        )
        q.setParameterList("ids", bs)
        // Put them in a set for quick membership testing
        val unauditedIds: Set<Long> = HashSet<Long>(q.getResultList())

        // Walk the sequence assignments getting the audit boards' index and count
        // values, finding the first CVR with no corresponding ACVR *in ballot audit
        // sequence order*. Any board that has finished the audit will get a null
        // instead of a CVR ID.
        val result: MutableList<Long?> = ArrayList()
        for (i in bsa.indices) {
            val m = bsa[i]

            val index = m["index"]
            val count = m["count"]

            result.add(null)
            for (j in index!! until index + count!!) {
                val cvrId = bs[j]

                if (unauditedIds.contains(cvrId)) {
                    result[i] = cvrId
                    break
                }
            }
        }

        return result
    }
    */

    /**
     * Adds an audited ballot.
     */
    public fun addAuditedBallot() { actualCount++ }

    /**
     * Removes an audited ballot.
     */
    public fun removeAuditedBallot() { actualCount-- }

    /**
     * Adds a discrepancy for the specified audit reasons. This adds it both to the
     * total and to the current audit round, if one is ongoing.
     *
     * @param the_reasons The reasons.
     */
    fun addDiscrepancy(the_reasons: Set<AuditReason>) {
        val selections: MutableSet<AuditSelection> = HashSet()
        for (r in the_reasons) {
            selections.add(r.selection())
        }
        for (s in selections) {
            my_discrepancies[s] = my_discrepancies.getOrDefault(s, 0) + 1
        }

        LOGGER.info(
            String.format(
                "[addDiscrepancy: the_reasons= %s, my_discrepancies=%s]",
                the_reasons, my_discrepancies
            )
        )
    }

    /**
     * Removes a discrepancy for the specified audit reasons. This removes it
     * both from the total and from the current audit round, if one is ongoing.
     *
     * @param the_reasons The reasons.
     */
    fun removeDiscrepancy(the_reasons: Set<AuditReason>) {
        val selections: MutableSet<AuditSelection> = HashSet()
        for (r in the_reasons) {
            selections.add(r.selection())
        }
        for (s in selections) {
            my_discrepancies[s] = my_discrepancies.getOrDefault(s, 0) - 1
        }
    }

    /**
     * @return the numbers of disagreements found in the audit so far,
     * categorized by contest audit reason.
     */
    fun disagreements(): Map<AuditSelection, Int> {
        return my_disagreements
    }

    /**
     * Adds a disagreement for the specified audit reasons. This adds it both to the
     * total and to the current audit round, if one is ongoing.
     *
     * @param the_reasons The reasons.
     */
    fun addDisagreement(the_reasons: Set<AuditReason>) {
        val selections: MutableSet<AuditSelection> = HashSet()
        for (r in the_reasons) {
            selections.add(r.selection())
        }
        for (s in selections) {
            my_disagreements[s] = my_disagreements.getOrDefault(s, 0) + 1
        }
    }

    /**
     * Removes a disagreement for the specified audit reasons. This removes it
     * both from the total and from the current audit round, if one is ongoing.
     *
     * @param the_reasons The reasons.
     */
    fun removeDisagreement(the_reasons: Set<AuditReason>) {
        val selections: MutableSet<AuditSelection> = HashSet()
        for (r in the_reasons) {
            selections.add(r.selection())
        }
        for (s in selections) {
            my_disagreements[s] = my_disagreements.getOrDefault(s, 0) - 1
        }
    }

    /**
     * @return the signatories.
     */
    fun signatories(): Map<Int, List<Elector>> {
        return Collections.unmodifiableMap(my_signatories)
    }

    /**
     * Sets the signatories for a particular audit board.
     */
    fun setSignatories(
        auditBoardIndex: Int,
        signatories: List<Elector>
    ) {
        my_signatories[auditBoardIndex] = signatories
    }

    /**
     * @return a version of this Round with no ballot/cvr sequences
     *
    // data class Round(
    //    val number: Int,
    //    val startTime: Instant,
    //    val endTime: Instant,
    //    val expectedCount: Int,
    //    val actualCount: Int,
    //    val expectedAuditedPrefixLength: Int,
    //    val actualAuditedPrefixLength: Int,
    //    val startAuditedPrefixLength: Int,
    //    val previousBallotsAudited: Int,
    //    val ballotSequence: List<Long>,
    //) {
    fun withoutSequences(): Round {
        val result =
            Round(
                my_number,
                my_start_time,
                my_expected_count,
                my_previous_ballots_audited,
                my_expected_audited_prefix_length,
                my_start_audited_prefix_length,
                null, null
            )
        result.my_actual_count = my_actual_count
        result.my_actual_audited_prefix_length = my_actual_audited_prefix_length
        result.my_discrepancies = my_discrepancies
        result.my_disagreements = my_disagreements
        result.my_signatories = my_signatories
        result.my_end_time = my_end_time

        return result
    }
    */

    companion object {
        val LOGGER = KotlinLogging.logger("Round")
    }
}
