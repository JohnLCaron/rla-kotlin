package org.cryptobiotic.rla.dashboard

import java.time.Instant

/**
 * Status information for a file import.
 *
 * @author Daniel M. Zimmerman <dmz></dmz>@freeandfair.us>
 * @version 1.0.0
 */
data class ImportStatus(
    val importState: ImportState,
    val errorMessage: String?,
    val timestamp: Instant?, // timestamp of the status update.
) {
    enum class ImportState {
        NOT_ATTEMPTED,
        IN_PROGRESS,
        SUCCESSFUL,
        FAILED
    }
}
