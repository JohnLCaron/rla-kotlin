package org.cryptobiotic.rla.dashboard

import java.time.Instant

/**
 * An audit investigation report.
 *
 * @author Daniel M. Zimmerman <dmz></dmz>@freeandfair.us>
 * @version 1.0.0
 */
data class IntermediateAuditReportInfo(
    val timestamp: Instant,
    val report: String,
)
