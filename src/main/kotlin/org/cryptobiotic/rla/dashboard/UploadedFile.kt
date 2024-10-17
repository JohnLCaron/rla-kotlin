package org.cryptobiotic.rla.dashboard

import java.sql.Blob // TODO
import org.cryptobiotic.rla.csv.Result
import org.cryptobiotic.rla.model.County

/**
 * An uploaded file, kept in persistent storage for archival.
 *
 * @author Daniel M. Zimmerman <dmz></dmz>@freeandfair.us>
 * @version 1.0.0
 */
data class UploadedFile(
    val id: Long,
    val version: Long,
    val county: County,
    val fileStatus: FileStatus,
    val filename: String,
    val hash: String,
    val submittedHash: String,
    val result: Result,
    val file: Blob,
    val size: Long,
    val my_approximate_record_count: Int,
) {

    enum class FileStatus {
        HASH_VERIFIED,
        HASH_MISMATCH,
        IMPORTING,
        IMPORTED,
        FAILED
    }
}
