package org.cryptobiotic.rla.model

/**
 * I volunteer as tribute,
 * to be randomly selected and audited.
 * A tribute is a theoretical cvr that may or may not exist.
 */
data class Tribute(
    val countyId: Long,
    val scannerId: Int,
    val batchId: Int,
    val ballotPosition: Int,
    val rand: Int,
    val randSequencePosition: Int, // to preserve the order of randomly selected cvrs
    val contestName: String,
) {
    val uri = "cvr:$countyId:$scannerId-$batchId-$ballotPosition"
}
