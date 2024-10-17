package org.cryptobiotic.rla.model

data class BallotManifestInfo(
    val countyId: Long,
    val scannerId: Int,
    val batchId: Int,
    val batchSize: Int,
    val storageLocation: String,
    val sequenceStart: Long, // The first sequence number (of all ballots) in this batch
    val sequenceEnd: Long, // The last sequence number (of all ballots) in this batch
) : Comparator<BallotManifestInfo> {
    // The unique properties as a string for fast selection
    val uri = "bmi:$countyId$scannerId-$batchId"
    var version: Long = 0 // for optimistic locking

    /**
     * The projected start of this manifest chunk
     */
    var ultimateSequenceStart: Long? = null

    /**
     * The projected end of this manifest chunk
     */
    var ultimateSequenceEnd: Long? = null

    /**
     * @param start the adjusted sequence start
     * this is like offset and limit of pages of a contest (multiple counties)
     */
    fun setUltimate(start: Long) {
        this.ultimateSequenceStart = start
        this.ultimateSequenceEnd = start + rangeSize()
    }

    /** from contest scope to county scope  */
    fun sequencePosition(rand: Int): Int {
        // subtraction gives a 0-based offset, adding one gives us the 1-based
        // ballot position in the file, row number of the file, a.k.a.: cvr.cvrNumber()
        return rand - ultimateSequenceStart!!.toInt() + 1
    }

    /**
     * translate a generated random number from contest to county scope, then
     * from county to batch scope
     */
    fun translateRand(rand: Int): Int {
        return sequencePosition(rand)
    }

    /**
     * @return Long the number of ballots in my chunk of a manifest
     */
    fun rangeSize(): Long {
        return sequenceEnd - sequenceStart // TODO off by one?
    }

    /**
     * @return Boolean whether this manifest section would hold the random
     * nth selection
     */
    fun isHolding(rand: Long): Boolean {
        // setUltimate(last + 1L) means the start and end is inclusive
        return (ultimateSequenceStart!!
                <= rand && rand <= ultimateSequenceEnd!!)
    }

    /**
     * computed value based on scannerId,batchID, and ballotPosition (in the bin)
     * This is what the auditors will use to confirm they have the correct card.
     */
    fun imprintedID(rand: Long): String {
        return "$scannerId-$batchId=${ballotPosition(rand.toInt())}" // TODO rand ??
    }

    /**
     * where the ballot sits in its storage bin
     */
    fun ballotPosition(sequencePosition: Int): Int {
        // position is the nth (1 based)
        return sequencePosition - sequenceStart.toInt() + 1
    }


    override fun compare(bmi1: BallotManifestInfo, bmi2: BallotManifestInfo): Int {
        return if (bmi1.countyId == bmi2.countyId) {
            bmi1.sequenceEnd.compareTo(bmi2.sequenceEnd)
        } else {
            bmi1.countyId.compareTo(bmi2.countyId)
        }
    }
}
