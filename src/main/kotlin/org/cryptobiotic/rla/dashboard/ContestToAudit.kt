package org.cryptobiotic.rla.dashboard

import org.cryptobiotic.rla.model.AuditReason
import org.cryptobiotic.rla.model.AuditType
import org.cryptobiotic.rla.model.Contest

class ContestToAudit(
    val contest: Contest,
    val reason: AuditReason,
    val audit: AuditType,
    ) {

    fun isAuditable(): Boolean = (audit != AuditType.HAND_COUNT
                    && audit != AuditType.NOT_AUDITABLE)

}
