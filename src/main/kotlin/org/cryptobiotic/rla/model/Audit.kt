package org.cryptobiotic.rla.model

import java.time.Instant

class AuditBoard(
    val members: List<Elector>,
    var loginTime: Instant? = null,
    var logoutTime: Instant? = null
)

data class AuditInvestigationReportInfo(
    val name: String,
    val report: String,
    val timestamp: Instant,
)

enum class AuditSelection  {
    AUDITED_CONTEST,
    UNAUDITED_CONTEST
}

enum class AuditStatus {
    NOT_STARTED,
    NOT_AUDITABLE,
    IN_PROGRESS,
    RISK_LIMIT_ACHIEVED,
    ENDED,
    HAND_COUNT
}

enum class AuditReason {
    STATE_WIDE_CONTEST,
    COUNTY_WIDE_CONTEST,
    CLOSE_CONTEST,
    TIED_CONTEST,
    CONCERN_REGARDING_ACCURACY,
    OPPORTUNISTIC_BENEFITS,
    COUNTY_CLERK_ABILITY;

    fun selection(): AuditSelection {
        return if (this == TIED_CONTEST || (this == OPPORTUNISTIC_BENEFITS)) {
            AuditSelection.UNAUDITED_CONTEST
        } else {
            AuditSelection.AUDITED_CONTEST
        }
    }

    /** we are thinking that this was selected by DOS as a targeted contest */
    fun isTargeted(): Boolean {
        return selection() === AuditSelection.AUDITED_CONTEST
    }
}

enum class AuditType {
    COMPARISON, HAND_COUNT, NOT_AUDITABLE, NONE
}