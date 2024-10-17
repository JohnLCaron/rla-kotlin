package org.cryptobiotic.rla.controller

import org.cryptobiotic.rla.model.CVRAuditInfo
import org.cryptobiotic.rla.model.CastVoteRecord
import org.cryptobiotic.rla.persistence.Persistence
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class BallotSelectionTest {

    private var return_cvr = true

    @Test
    fun testAuditedPrefixLengthWithNone() {
        val cvrIds = mutableListOf<Long>()
        val result = BallotSelection.auditedPrefixLength(cvrIds)
        assertEquals(0, result)
    }

    @Test
    fun testAuditedPrefixLengthWithSome() {
        val cvrs = listOf(
            fakeAuditedCVR(1),
            fakeAuditedCVR(2),
            fakeAuditedCVR(3),
            fakeAuditedCVR(4),
            fakeAuditedCVR(5),
            fakeCVR(6),
            fakeAuditedCVR(7),
            fakeAuditedCVR(8)
        )
        val cvrIds = mutableListOf<Long>()
        cvrIds.addAll( cvrs.map { it.id })
        val result = BallotSelection.auditedPrefixLength(cvrIds)
        assertEquals(5, result)
    }

    @Test
    fun testCombineSegmentsWorksWhenEmpty() {
        val segments = mutableListOf<BallotSelection.Segment>()
        segments.add(BallotSelection.Segment())
        val result = BallotSelection.combineSegments(segments)
        assertTrue(result.cvrsInBallotSequence().isEmpty())
    }

    @Test
    fun testCombineSegmentsAuditSequence() {
        // ... skipped for brevity
    }

    // ... rest of the test cases skipped for brevity


    //   public CastVoteRecord fakeAuditedCVR(final Integer recordId) {
    //    final CastVoteRecord cvr = fakeCVR(recordId);
    //    Persistence.saveOrUpdate(cvr);
    //
    //    final CastVoteRecord acvr = new CastVoteRecord(CastVoteRecord.RecordType.AUDITOR_ENTERED, Instant.now(),
    //                                                   64L, 1, null, 1,
    //                                                   "Batch1", recordId, "1-Batch1-1",
    //                                                   "paper", null);
    //    acvr.setID(null);
    //    Persistence.saveOrUpdate(acvr);
    //
    //    final CVRAuditInfo cai = new CVRAuditInfo(cvr);
    //    cai.setACVR(acvr);
    //    Persistence.saveOrUpdate(cai);
    //    return cvr;
    //  }

    fun fakeAuditedCVR(recordId: Int): CastVoteRecord {
        val cvr = fakeCVR(recordId)
        Persistence.saveOrUpdate(cvr)
        val acvr = CastVoteRecord(
            0,
            CastVoteRecord.RecordType.AUDITOR_ENTERED,
            Instant.now(),
            64L, 1, 0, 1,
            "Batch1", recordId, "1-Batch1-1",
            "paper", emptyList()
        )
        Persistence.saveOrUpdate(acvr)
        val cai = CVRAuditInfo(cvr, acvr)
        Persistence.saveOrUpdate(cai)
        return cvr
    }

    //    val id: Long,
    //    val version: Long, // for optimistic locking for hibernate
    //    val revision: Long, // used to store the order of edits made to a ballot submission
    //    val recordType: RecordType,
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
    fun fakeCVR(recordId: Int): CastVoteRecord {
        return if (return_cvr) {
            CastVoteRecord(
                recordId.toLong(),
                CastVoteRecord.RecordType.UPLOADED,
                Instant.now(),
                64L,         // county_id
                1,            // cvr_number
                45,           // sequence_number
                1,            // scanner_id
                "Batch1",     // batch_id
                recordId,  // record_id
                "1-Batch1-1", // imprinted_id
                "paper",       // ballot_type
                emptyList(),
            )
        } else {
            throw RuntimeException("fakeCVR nulls")
        }
    }
}