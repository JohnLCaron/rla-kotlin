package org.cryptobiotic.rla.util

import org.cryptobiotic.rla.controller.ComparisonAuditController
import org.cryptobiotic.rla.dashboard.CountyDashboard
import org.cryptobiotic.rla.model.CVRAuditInfo
import org.cryptobiotic.rla.model.CVRContestInfo
import org.cryptobiotic.rla.model.CastVoteRecord
import org.cryptobiotic.rla.model.Contest
import org.cryptobiotic.rla.persistence.ContestQueries
import org.cryptobiotic.rla.persistence.Persistence
import java.time.Instant
import java.util.ArrayList
import java.util.stream.Collectors

/**
 * Phantom ballot handling.
 */
object PhantomBallots {
    /**
     * Audit phantom records as if by an audit board.
     */
    fun auditPhantomRecords(cdb: CountyDashboard, cvrs: List<CastVoteRecord?>): List<CastVoteRecord> {
        return cvrs.stream()
            .map<CastVoteRecord?> { cvr: CastVoteRecord? ->
                if (isPhantomRecord(cvr!!))
                    auditPhantomRecord(cdb, cvr)
                else
                    cvr
            }
            .collect(Collectors.toList())
    }

    /**
     * Returns a list of CastVoteRecords with phantom records removed.
     */
    fun removePhantomRecords(cvrs: List<CastVoteRecord>): List<CastVoteRecord> {
        return cvrs.filter { cvr: CastVoteRecord? -> !isPhantomRecord(cvr!!) }
    }

    /**
     * Tests if the CVR is a phantom record.
     */
    fun isPhantomRecord(cvr: CastVoteRecord): Boolean {
        return cvr.recordType === CastVoteRecord.RecordType.PHANTOM_RECORD
    }

    /**
     * Audit a phantom record as if by an audit board.
     */
    private fun auditPhantomRecord(
        cdb: CountyDashboard,
        cvr: CastVoteRecord
    ): CastVoteRecord {
        var cvrAuditInfo: CVRAuditInfo? = Persistence.getByID(cvr.id, CVRAuditInfo::class.java)

        if (null != cvrAuditInfo && null != cvrAuditInfo.acvr) {
            // CVR has already been audited.
            return cvr
        }

        // we need to create a discrepancy for every contest that COULD have
        // appeared on the ballot, which we take to mean all the contests that occur
        // in the county
        val contests = ContestQueries.forCounty(cdb.county)

        //     val contest: Contest,
        //    val comment: String,
        //    val countyId: Long,
        //    val choices: List<String>,
        val phantomContestInfos = contests.map{ c: Contest ->
                CVRContestInfo(
                    c,
                    "PHANTOM_RECORD - CVR not found",
                    0L,
                    emptyList()
                )
            }

        // TODO cvr.setContestInfo(phantomContestInfos)
        Persistence.saveOrUpdate(cvr)

        if (null == cvrAuditInfo) {
            cvrAuditInfo = CVRAuditInfo(cvr)
            Persistence.save(cvrAuditInfo)
        }

        // data class CastVoteRecord(
        //    val id: Long,
        //    var recordType: RecordType,
        //    val timestamp: Instant,
        //    val countyId: Long,
        //    val cvrNumber: Int,
        //    val sequenceNumber: Int,
        //    val scannerId: Int,
        //    val batchId: String,
        //    val recordId: Int,
        //    val imprintedId: String,
        //    val ballotType: String,
        //    val contestInfo: List<CVRContestInfo>,
        //) : Comparable<CastVoteRecord> {
        val acvr = CastVoteRecord(0L,
            CastVoteRecord.RecordType.PHANTOM_RECORD_ACVR,
            Instant.now(),
            cvr.countyId,
            cvr.cvrNumber,
            0,
            cvr.scannerId,
            cvr.batchId,
            cvr.recordId,
            cvr.imprintedId,
            cvr.ballotType,
            phantomContestInfos
        )
        Persistence.save(acvr)

        ComparisonAuditController.submitAuditCVR(cdb, cvr, acvr)

        return cvr
    }
}
