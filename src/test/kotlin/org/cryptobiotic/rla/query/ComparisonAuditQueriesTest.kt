package org.cryptobiotic.rla.query

import org.cryptobiotic.rla.csv.ContestNameParser.ParseError
import org.cryptobiotic.rla.math.Audit
import org.cryptobiotic.rla.model.AuditReason
import org.cryptobiotic.rla.model.ComparisonAudit
import org.cryptobiotic.rla.model.ContestResult
import org.cryptobiotic.rla.persistence.Persistence
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class ComparisonAuditQueriesTest {

    /*
    @Test
    fun testSortedList() {
        val riskLimit = BigDecimal(0.05)
        val dilutedMargin = BigDecimal(.2)

        val contestResult = ContestResult("Test Contest") // TODO dont allow this
        contestResult.setDilutedMargin(dilutedMargin)

        Persistence.saveOrUpdate(contestResult)
        val ca = ComparisonAudit(contestResult, riskLimit, dilutedMargin, Audit.GAMMA, AuditReason.STATE_WIDE_CONTEST)

        Persistence.saveOrUpdate(ca)
        assertEquals(emptyList<Any>(), sortedComparisonAuditList()) // you suck
    }
}

fun sortedComparisonAuditList(): List<ComparisonAudit>  {
    // sorting by db doesn't stick for some reason
    val results: List<ComparisonAudit> = Persistence.getAll(ComparisonAudit.class);
    return results.sortedBy { TargetedSort() }
}

class TargetedSort: Comparator<ComparisonAudit> {

    /** sort by targeted, then contest name **/
    @Override
    fun  compare(final ComparisonAudit a, final ComparisonAudit b): Int {
        // negative to put true first
        final int t = -Boolean.compare(a.isTargeted(), b.isTargeted());
        if (0 == t) {
            return a.contestResult().getContestName().compareTo(b.contestResult().getContestName());
        } else {
            return t;
        }
    }

    override fun compare(o1: ComparisonAudit?, o2: ComparisonAudit?): Int {
        return compareBy(ComparisonAudit::isTargeted).thenComparing(ComparisonAudit::contestResult.contestName).compare(o1, o2)
    }

     */
}