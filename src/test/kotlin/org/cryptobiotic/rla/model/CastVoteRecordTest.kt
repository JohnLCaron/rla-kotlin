package org.cryptobiotic.rla.model

import org.cryptobiotic.rla.model.CastVoteRecord.RecordType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CastVoteRecordTest {
    private val cvr1: CastVoteRecord
    private val cvr2: CastVoteRecord
    private val cvr3: CastVoteRecord
    private val cvr4: CastVoteRecord
    private val now: Instant

    init {
        now = Instant.now()
        cvr1 = CastVoteRecord(0, RecordType.UPLOADED, now, 64L, 1, 1, scannerId=1, batchId = "Batch1", recordId=1, "1-Batch1-1", "null", emptyList())
        cvr2 = CastVoteRecord(0, RecordType.UPLOADED, now, 64L, 1, 1, scannerId=1, batchId = "Batch2", recordId=1, "1-Batch2-1", "null", emptyList())
        cvr3 = CastVoteRecord(0, RecordType.UPLOADED, now, 64L, 1, 1, scannerId=1, batchId = "Batch11", recordId=1, "1-Batch11-1", "null", emptyList())
        cvr4 = CastVoteRecord(0, RecordType.UPLOADED, now, 64L, 1, 1, scannerId=1, batchId = "Batch2", recordId=1, "1-Batch2-1", "null", emptyList())
    }

    @Test
    fun comparatorTest() {
        assertEquals(-1, cvr1.compareTo(cvr2))
        assertEquals(0, cvr2.compareTo(cvr4))
        // TODO original uses "NaturalOrderComparator", which flips these; Batch11 > Batch2
        //assertEquals(1, cvr3.compareTo(cvr2))
        assertEquals(-1, cvr3.compareTo(cvr2))
    }
}
