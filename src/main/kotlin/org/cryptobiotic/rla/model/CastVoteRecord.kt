/*
 * Free & Fair Colorado RLA System
 *
 * @title ColoradoRLA
 * @created Jul 25, 2017
 * @copyright 2017 Colorado Department of State
 * @license SPDX-License-Identifier: AGPL-3.0-or-later
 * @creator Daniel M. Zimmerman <dmz@freeandfair.us>
 * @description A system to assist in conducting statewide risk-limiting audits.
 */
package org.cryptobiotic.rla.model

import org.cryptobiotic.rla.persistence.HasId
import java.time.Instant

/**
 * A cast vote record contains information about a single ballot, either
 * imported from a tabulator export file or generated by auditors.
 *
 * @author Daniel M. Zimmerman <dmz></dmz>@freeandfair.us>
 * @version 1.0.0
 */
data class CastVoteRecord(
    val id: Long,
    var recordType: RecordType,
    val timestamp: Instant,
    val countyId: Long,
    val cvrNumber: Int,
    val sequenceNumber: Int,
    val scannerId: Int,
    val batchId: String,
    val recordId: Int,
    val imprintedId: String,
    val ballotType: String,
    val contestInfo: List<CVRContestInfo>,
) : Comparable<CastVoteRecord>, HasId {
    val version: Long = 0 // for optimistic locking for hibernate
    private var revision: Long = 0 // used to store the order of edits made to a ballot submission

    /**
     * ACVR level comments, used for explaining why reaudit is happening
     */
    private var comment: String? = null

    /**
     * who is submitting this ACVR, used for reporting
     */
    var auditBoardIndex: Int? = null

    /**
     * A transient flag that indicates whether this CVR was audited; this is only
     * used for passing information around within the RLA tool and is not serialized
     * in the database; the authoritative source of information about whether a CVR
     * has been audited, and in what audit, is the responsible audit information
     * object.
     */
    private var my_audit_flag = false

    /**
     * A transient flag that indicates whether this CVR was audited in a "previous
     * round"; this is only used for passing information around and is not
     * serialized in the database.
     */
    private var my_previously_audited = false

    /**
     * keep a record of what this ACVR was submitted to audit, which is lost when
     * reauditing because the CVRAuditInfo join is broke when reauditing.
     * The CVR to audit, for ACVRs only
     */
    var cvrId: Long? = null

    /**
     * The round that the submission happened in, for ACVRs only
     */
    var roundNumber: Int? = null

    /**
     * The generated random number that selected/resolves to this cvr
     */
    var rand: Int? = null

    /** set the uri for fast selection  */
    fun getUri(): String {
        val cvrOrAcvr: String
        var rev = ""
        if (recordType == RecordType.UPLOADED
            || recordType == RecordType.PHANTOM_RECORD
        ) {
            // phantoms play the role of uploaded cvrs
            cvrOrAcvr = "cvr"
        } else if (recordType == RecordType.REAUDITED) {
            rev = "?rev=" + revision.toString()
            cvrOrAcvr = "rcvr"
        } else {
            // auditor entered (or ballot not found)
            cvrOrAcvr = "acvr"
        }
        return String.format(
            "%s:%s:%s-%s-%s%s",
            cvrOrAcvr,
            countyId,
            scannerId,
            batchId,
            recordId,
            rev
        )
    }

    /** link to a bmi for fast selection  */
    fun bmiUri(): String {
        return String.format(
            "%s:%s:%s-%s",
            "bmi",
            countyId,
            scannerId,
            batchId
        )
    }

    /**
     * Gets the choices for the specified contest.
     *
     * @param the_contest The contest.
     * @return the choices made in this cast vote record for the specified contest,
     * or null if none were made for the specified contest.
     */
    fun contestInfoForContest(the_contest: Contest?): CVRContestInfo? {
        for (info in contestInfo) {
            if (info.contest.equals(the_contest)) {
                return info
            }
        }
        return null
    }

    /**
     * Get info about a CVR by way of a ContestResult, matching on contest
     * name.
     * @param cr
     * @return maybe the first CVRContestInfo found, maybe nothing.
     */
    fun contestInfoForContestResult(cr: ContestResult): CVRContestInfo? {
        return contestInfo.find { it.contest.name.equals(cr.contestName) }
    }

    /**
     * @return the audit flag. This flag is meaningless unless it was explicitly set
     * when this record was loaded. It is useful only for communicating information
     * about a CVR within a specific computation of the tool, and is not serialized
     * in the database; the authoritative source of information about whether a CVR
     * has been audited, and in what audit, is the responsible audit information
     * object.
     */
    fun auditFlag(): Boolean {
        return my_audit_flag
    }

    /**
     * Sets the audit flag.
     *
     * @param the_audit_flag The new flag.
     */
    fun setAuditFlag(the_audit_flag: Boolean) {
        my_audit_flag = the_audit_flag
    }

    fun setRevision(the_revision: Long) {
        revision = the_revision
    }

    fun setToReaudited() {
        this.recordType = RecordType.REAUDITED
    }

    fun setComment(the_comment: String?) {
        this.comment = the_comment
    }

    /**
     * Whether or not the ballot was previously audited
     *
     * Like auditFlag(), this is not persisted to the database, and is only used
     * during a single run of the tool.
     */
    fun previouslyAudited(): Boolean {
        return my_previously_audited
    }

    /**
     * Set whether or not the ballot was previously audited
     */
    fun setPreviouslyAudited(the_previously_audited: Boolean) {
        my_previously_audited = the_previously_audited
    }

    /**
     * Compares this CVR with another to determine whether
     * one is an audit CVR for the other - that is, whether they have
     * the same county ID, scanner ID, batch ID, record ID,
     * imprinted ID, and ballot type, and exactly one of them is an
     * auditor uploaded CVR.
     *
     * @param the_other The other CVR.
     * @return true if one CVR is an audit CVR for the other; false
     * otherwise.
     */
    fun isAuditPairWith(the_other: CastVoteRecord): Boolean {
        var result = true

        if (the_other == null) {
            result = false
        } else {
            result = result && the_other.countyId == countyId
            result = result && the_other.cvrNumber == cvrNumber
            result = result && the_other.scannerId == scannerId
            result = result && the_other.batchId == batchId
            result = result && the_other.recordId == recordId
            result = result && the_other.imprintedId == imprintedId
            result = result && the_other.ballotType == ballotType
            // if PHANTOM_RECORD, neither are auditorGenerated
            // result &= recordType().isAuditorGenerated() ^
            //           the_other.recordType().isAuditorGenerated();
        }

        return result
    }

    /**
     * An enumeration used to select cast vote record types.
     * REAUDITED are the previous revisions, AUDITOR_ENTERED is the latest revision
     */
    enum class RecordType {
        UPLOADED, AUDITOR_ENTERED, REAUDITED, PHANTOM_RECORD, PHANTOM_RECORD_ACVR, PHANTOM_BALLOT;

        val isAuditorGenerated: Boolean
            /**
             * @return true if this record was generated by an auditor,
             * false otherwise.
             */
            get() = this == AUDITOR_ENTERED || (this == REAUDITED) || (this == PHANTOM_BALLOT)

        val isSystemGenerated: Boolean
            /**
             * the cvr data did not contain a cvr we looked for so we generate a
             * discrepancy automatically, at least for PHANTOM_RECORD
             */
            get() = this == PHANTOM_RECORD || this == PHANTOM_RECORD_ACVR
    }

    /**
     * Compares this object to another, using (scannerId, batchId, recordId)
     */
    override fun compareTo(other: CastVoteRecord): Int {
        val scanner = scannerId - other.scannerId

        if (scanner != 0) {
            return scanner
        }
        // TODO original uses "NaturalOrderComparator"
        val batch: Int = this.batchId.compareTo(other.batchId)

        if (batch != 0) {
            return batch
        }

        return recordId - other.recordId
    }

    override fun id() = id

    companion object {

        /**
         * both a performance optimization and work around for a feature lacking from
         * hibernate: on delete cascade in the ddl
         * so we tag the ContestInfo with a county so we can delete them all quickly
         *
        fun claim(contestInfos: List<CVRContestInfo>, countyId: Long?): List<CVRContestInfo> {
            return contestInfos.stream()
                .map<Any>(Function<CVRContestInfo, Any> { ci: CVRContestInfo ->
                    ci.setCountyId(countyId)
                    ci
                })
                .collect(Collectors.toList<Any>())
        }
        */
    }
}
