package org.cryptobiotic.rla.dashboard

import java.math.BigDecimal
import java.time.Instant

class AuditInfo(val electionType: String,
                val electionDate: Instant,
                val publicMeetingDate: Instant,
                val seed: String,
                val riskLimit: BigDecimal,
                val canonicalContests: Map<String, Set<String>>,
                val canonicalChoices: Map<String, Set<String>>,
) {
    //  TODO get rid of
    constructor(): this("", Instant.ofEpochSecond(0), Instant.ofEpochSecond(0), "seed", BigDecimal.ZERO,
        emptyMap(), emptyMap())

    /**
     * was called: updateFrom
     * Updates this AuditInfo with information from the specified one.
     * Any non-null fields in the specified AuditInfo replace the
     * corresponding fields of this AuditInfo; any null fields in the
     * specified AuditInfo are ignored. It is not possible to nullify
     * a field of an AuditInfo once it has been set, only to replace
     * it with a new non-null value.
     *
     * @param other The other ElectionInfo.
     */
    fun combine(other: AuditInfo): AuditInfo {
        val electionType = if (other.electionType.isEmpty()) this.electionType else other.electionType
        val electionDate = if (other.electionDate.epochSecond == 0L) this.electionDate else other.electionDate
        val publicMeetingDate = if (other.publicMeetingDate.epochSecond == 0L) this.publicMeetingDate else other.publicMeetingDate
        val seed = if (other.seed.isEmpty()) this.seed else other.seed
        val riskLimit = if (other.riskLimit == BigDecimal.ZERO) this.riskLimit else other.riskLimit
        val canonicalContests = if (other.canonicalContests.isEmpty()) this.canonicalContests else other.canonicalContests
        val canonicalChoices = if (other.canonicalChoices.isEmpty()) this.canonicalChoices else other.canonicalChoices

        return AuditInfo(
            electionType,
            electionDate,
            publicMeetingDate,
            seed,
            riskLimit,
            canonicalContests,
            canonicalChoices,
        )

    }

    companion object {
        /**
         * The database stored precision for decimal types.
         */
        const val PRECISION: Int = 10

        /**
         * The database stored scale for decimal types.
         */
        const val SCALE: Int = 8
    }
}
