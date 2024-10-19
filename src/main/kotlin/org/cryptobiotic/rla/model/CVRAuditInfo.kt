/*
 * Free & Fair Colorado RLA System
 *
 * @title ColoradoRLA
 * @created Aug 10, 2017
 * @copyright 2017 Colorado Department of State
 * @license SPDX-License-Identifier: AGPL-3.0-or-later
 * @creator Daniel M. Zimmerman <dmz@galois.com>
 * @description A system to assist in conducting statewide risk-limiting audits.
 */
package org.cryptobiotic.rla.model

import org.cryptobiotic.rla.persistence.HasId
import java.util.*

/**
 * A class representing a contest to audit or hand count.
 *
 * @author Daniel M. Zimmerman <dmz></dmz>@freeandfair.us>
 * @version 1.0.0
 */
data class CVRAuditInfo(
    val id: Long,
    // val county: County,
    val cvr: CastVoteRecord,
    val acvr: CastVoteRecord?, // submitted audit CVR
) : Comparable<CVRAuditInfo>, HasId {

    //   public CVRAuditInfo(final CastVoteRecord the_cvr) {
    //    super();
    //    my_id = the_cvr.id();
    //    my_cvr = the_cvr;
    //  }

    constructor(cvr: CastVoteRecord, acvr: CastVoteRecord? = null): this(cvr.id, cvr, acvr)
    
    /**
     * The number of times this auditInfo's CVR appears in the selections of
     * ComparisonAudits
     * {Long ComparisonAuditId: Integer count}
     */
    private val multiplicity_by_contest: MutableMap<Long, Int> = HashMap()

    /**
     * The number of times this CVRAuditInfo has been counted/sampled in each
     * ComparisonAudit
     */
    private val count_by_contest: MutableMap<Long, Int> = HashMap()

    /**
     * The number of discrepancies found in the audit so far.
     */
    private val my_discrepancy: MutableSet<AuditReason> = HashSet()

    /**
     * The number of disagreements found in the audit so far.
     */
    private val my_disagreement: MutableSet<AuditReason> = HashSet()

    /**
     * how many times has this been counted over all contests?
     */
    fun totalCounts(): Int {
        return count_by_contest.values.sum()
    }

    /**
     * clear record of counts per contest, for unauditing.
     */
    fun resetCounted() {
        count_by_contest.clear()
    }

    /**
     * @return a map from audit reason to whether this record was marked
     * as a discrepancy in a contest audited for that reason.
     */
    fun discrepancy(): Set<AuditReason> {
        return Collections.unmodifiableSet(my_discrepancy)
    }

    /**
     * Sets the audit reasons for which the record is marked as a discrepancy.
     *
     * @param the_reasons The reasons.
     */
    fun setDiscrepancy(the_reasons: Set<AuditReason>?) {
        my_discrepancy.clear()
        if (the_reasons != null) {
            my_discrepancy.addAll(the_reasons)
        }
    }

    /**
     * @return a map from audit reason to whether this record was marked
     * as a disagreement in a contest audited for that reason.
     */
    fun disagreement(): Set<AuditReason> {
        return Collections.unmodifiableSet(my_disagreement)
    }

    /**
     * Sets the audit reasons for which the record is marked as a disagreement.
     *
     * @param the_reasons The reasons.
     */
    fun setDisagreement(the_reasons: Set<AuditReason>?) {
        my_disagreement.clear()
        if (the_reasons != null) {
            my_disagreement.addAll(the_reasons)
        }
    }

    fun setMultiplicityByContest (comparisonAuditId: Long, count: Int) {
        this.multiplicity_by_contest.put(comparisonAuditId, count);
    }

    fun getCountByContest(comparisonAuditId: Long): Int {
        return this.count_by_contest[comparisonAuditId] ?: 0
    }

    fun setCountByContest(comparisonAuditId: Long, count: Int) {
        this.count_by_contest[comparisonAuditId]  = count
    }

    /**
     * @return a String representation of this contest to audit.
     */
    override fun toString(): String {
        val cvr = if (cvr == null) {
            "null"
        } else {
            cvr!!.id.toString()
        }
        val acvr = if (acvr == null) {
            "null"
        } else {
            acvr!!.id.toString()
        }
        return "CVRAuditInfo [cvr=$cvr, acvr=$acvr]"
    }

    /**
     * Compares this CVRAuditInfo to another.
     *
     * Uses the underlying CVR to provide the sorting behavior.
     *
     * @return int
     */
    override fun compareTo(other: CVRAuditInfo): Int {
        return cvr.compareTo(other.cvr)
    }

    override fun id() = id
}
