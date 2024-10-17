package org.cryptobiotic.rla.model

import java.math.BigDecimal

/**
 * A class representing the results for a contest across counties.
 * A roll-up  of CountyContestResults
 */
// you complain if we import x.y.z.*, so....
data class ContestResult(
    val id: Long,
    val version: Long,
    val contestId: String,
    val winnersAllowed: Int,
    val winners: Set<String>,
    val losers: Set<String>,
    val counties: Set<County>,
    val contests: Set<Contest>,
    val voteTotals: Map<String, Int>,
    val contestName: String,
    val dilutedMargin: BigDecimal,
    val minMargin: Int,
    val maxMargin: Int,
    val ballotCount: Long,
    val auditReason: AuditReason,
) {
    /**
     * @param county the county owning the contest you want
     * @return the contest belonging to county
     */
    fun contestFor(county: County): Contest? {
        return contests.find { it.id == county.id }
    }

    /** sum the voteTotals  */
    fun totalVotes(): Int {
        return voteTotals.values.sum()
    }

    /**
     * The set of county ids related to this ContestResult
     */
    fun countyIDs(): Set<Long> {
        return counties.map { it.id }.toSet()
    }

    /**
     * The set of contest ids related to this ContestResult
     */
    fun contestIDs(): Set<Long> {
        return contests.map { it.id }.toSet()
    }

    /**
     * @return a String representation of this contest.
     */
    override fun toString(): String {
        return "ContestResult [id=" + id + " contestName=" + contestName + "]"
    }

    companion object {
        private const val TEXT = "text"
        private const val ID = "id"
        private const val RESULT_ID = "result_id"
    }
}
