package org.cryptobiotic.rla.report

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rla.controller.CastVoteRecordQueries
import org.cryptobiotic.rla.controller.ContestCounter
import org.cryptobiotic.rla.model.*
import org.cryptobiotic.rla.persistence.ComparisonAuditQueries
import org.cryptobiotic.rla.persistence.CountyQueries
import org.cryptobiotic.rla.persistence.Persistence
import org.cryptobiotic.rla.persistence.TributeQueries
import java.lang.IndexOutOfBoundsException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone

/**
 * Contains the query-ing and processing of two report types:
 * activity and results
 */
object ReportRows {
    val LOGGER = KotlinLogging.logger("ReportRows")

    /** the union set of used by activity and results reports  */
    val ALL_HEADERS = arrayOf(
        "county",
        "imprinted id",
        "scanner id",
        "batch id",
        "record id",
        "db id",
        "round",
        "audit board",
        "record type",
        "discrepancy",
        "consensus",
        "comment",
        "random number",
        "random number sequence position",
        "multiplicity",
        "revision",
        "re-audit ballot comment",
        "time of submission"
    )

    /** US local date time  */
    private val MMDDYYYY: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm:ss a")

    /** cache of county id to county name  */
    private val countyNames = mutableMapOf<Long, String>()

    /** an empty error response  */
    private val NOT_FOUND_ROW = listOf("audit has not started or contest name not found")

    /**
     * query for the associated CVRAuditInfo object to access the disc data on the
     * Comparison object. If the acvr has been reaudited, recompute the value
     * because that process loses the association.
     */
    fun findDiscrepancy(audit: ComparisonAudit, acvr: CastVoteRecord): Int? {
        if (CastVoteRecord.RecordType.REAUDITED === acvr.recordType) {
            // we recompute here because we don't have cai.acvr_id = acvr.id
            val cvr = Persistence.getByID(acvr.cvrId!!, CastVoteRecord::class.java)!!
            val disc: Int? = audit.computeDiscrepancy(cvr, acvr)
            return disc
        } else {
            // not a revised/overwritten submission
            val cai = Persistence.getByID(acvr.cvrId!!, CVRAuditInfo::class.java)!!
            return audit.getDiscrepancy(cai)
        }
    }

    /** get the county name for the given county id  */
    fun findCountyName(countyId: Long): String? {
        var name: String? = countyNames.get(countyId)
        if (null == name) {
            name = CountyQueries.getName(countyId)
            countyNames.put(countyId, name)
            return name
        } else {
            return name
        }
    }

    /** render helper  */
    fun toString(o: Any?): String? {
        if (null == o) {
            return null
        } else {
            return o.toString()
        }
    }

    /** render helper  */
    fun renderAuditBoard(auditBoardIndex: Int?): String? {
        if (null == auditBoardIndex) {
            return null
        } else {
            val i = auditBoardIndex + 1
            return i.toString()
        }
    }

    /**
     * render helper
     * Prepend a plus sign on positive integers to make it clear that it is positive.
     * Negative numbers will have the negative sign.
     * These don't need to be integers because they are counted, not summed.
     */
    fun renderDiscrepancy(discrepancy: Int): String {
        if (discrepancy > 0) {
            return String.format("+%d", discrepancy)
        } else {
            return discrepancy.toString()
        }
    }

    /** render helper US local date time  */
    fun renderTimestamp(timestamp: Instant): String {
        return MMDDYYYY.format(
            LocalDateTime
                .ofInstant(
                    timestamp,
                    TimeZone.getDefault().toZoneId()
                )
        )
    }

    /** render consensus to yesNo  */
    fun renderConsensus(consensus: CVRContestInfo.ConsensusValue?): String {
        // consensus can be null if not sent in the request, so there was a
        // consensus, unless they said no.
        return yesNo(CVRContestInfo.ConsensusValue.NO !== consensus)
    }

    /** add fields common to both activity and results reports  */
    fun addBaseFields(row: Row, audit: ComparisonAudit, acvr: CastVoteRecord): Row {
        val discrepancy: Int? = findDiscrepancy(audit, acvr)
        val info: CVRContestInfo? = acvr.contestInfoForContestResult(audit.contestResult)

        if (info != null) {
            row.put("consensus", renderConsensus(info.consensus))
            row.put("comment", info.comment)
        }

        if (null == discrepancy || 0 == discrepancy) {
            row.put("discrepancy", null)
        } else {
            row.put("discrepancy", renderDiscrepancy(discrepancy))
        }
        row.put("db id", acvr.cvrId.toString())
        row.put("record type", acvr.recordType.toString())
        row.put("county", findCountyName(acvr.countyId)!!)
        row.put("audit board", renderAuditBoard(acvr.auditBoardIndex)!!)
        row.put("round", acvr.roundNumber.toString())
        row.put("imprinted id", acvr.imprintedId)
        row.put("scanner id", acvr.scannerId.toString())
        row.put("batch id", acvr.batchId)
        row.put("record id", acvr.recordId.toString())
        row.put("time of submission", renderTimestamp(acvr.timestamp))
        return row
    }

    /** add fields unique to activity report  */
    fun addActivityFields(row: Row, acvr: CastVoteRecord): Row {
        row.put("revision", acvr.revision.toString())
        row.put("re-audit ballot comment", acvr.comment)
        return row
    }

    /** add fields unique to results report  */
    fun addResultsFields(row: Row, tribute: Tribute, multiplicity: Int?): Row {
        row.put("multiplicity", multiplicity?.toString())
        return addResultsFields(row, tribute)
    }

    /** add fields unique to activity report, if the multiplicity is unknown  */
    fun addResultsFields(row: Row, tribute: Tribute): Row {
        row.put("random number", toString(tribute.rand))
        row.put("random number sequence position", toString(tribute.randSequencePosition))
        return row
    }

    /** compare risk sought vs measured  */
    fun riskLimitMet(sought: BigDecimal, measured: BigDecimal?): Boolean {
        return sought.compareTo(measured) > 0
    }

    /** yes/no instead of true/false  */
    fun yesNo(bool: Boolean): String {
        if (bool) {
            return "Yes"
        } else {
            return "No"
        }
    }

    /** significant figures  */
    fun sigFig(num: BigDecimal, digits: Int): BigDecimal? {
        return num.setScale(digits, BigDecimal.ROUND_HALF_UP)
    }

    /** * 100  */
    fun percentage(num: BigDecimal?): BigDecimal {
        return BigDecimal.valueOf(100).multiply(num)
    }

    /**
     * For each contest (per row), show all the variables that are interesting or
     * needed to perform the risk limit calculation
     * This checks whether the contest is IRV and makes two modifications:
     * 1. it takes the winner from the GenerateAssertionsSummary table, rather than the ComparisonAudit,
     * 2. it omits the candidate tallies (for winner and runner-up), which are not meaningful for IRV.
     */
    fun genSumResultsReport():  List<List<String?>?> {
        val rows = mutableListOf<List<String?>>()
        rows.add(SummaryReport.HEADERS)

        for (ca in ComparisonAuditQueries.sortedList()) {
            val row: Row = SummaryReport.newRow()

            val riskMsmnt = ca.riskMeasurement()

            // general info
            row.put("Contest", ca.contestResult.contestName)
            row.put("targeted", yesNo(ca.isTargeted))

            //if (ca is IRVComparisonAudit) {
            //    // If IRV, get the winner's name from the GenerateAssertionsSummary.
            //    row.put("Winner", GenerateAssertionsSummaryQueries.matchingWinner(ca.getContestName()))
            //} else {
                // Must be a plurality audit. ContestResult.winner is correct.
                if (ca.contestResult.winners == null || ca.contestResult.winners.isEmpty()) {
                    LOGGER.info("no winner!!! " + ca)
                }
                row.put("Winner", toString(ca.contestResult.winners.first())!!)
            //}

            // All this data makes sense for both IRV and plurality.
            row.put("Risk Limit met?", yesNo(riskLimitMet(ca.myRiskLimit, riskMsmnt)))
            row.put("Risk measurement %", sigFig(percentage(riskMsmnt), 1).toString())
            row.put("Audit Risk Limit %", sigFig(percentage(ca.myRiskLimit), 1).toString())
            row.put("diluted margin %", percentage(ca.myDilutedMargin).toString())
            row.put("disc +2", ca.discrepancyCount(2).toString())
            row.put("disc +1", ca.discrepancyCount(1).toString())
            row.put("disc -1", ca.discrepancyCount(-1).toString())
            row.put("disc -2", ca.discrepancyCount(-2).toString())
            row.put("gamma", ca.gamma.toString())
            row.put("audited sample count", ca.auditedSampleCount.toString())
            // For IRV, this is the total number of mentions among all valid interpretations.
            row.put("ballot count", ca.contestResult.ballotCount.toString())

            //if (ca is IRVComparisonAudit) {
            //    // For IRV, get the min margin direct from the IRVComparisonAudit.
            //    row.put("min margin", toString((ca as IRVComparisonAudit).getMinMargin()))
            //} else {
                // For plurality, get the min margin from the contestResult.
                row.put("min margin", ca.contestResult.minMargin.toString())

                // These totals make sense only for plurality. Omit entirely for IRV.
                val rankedTotals = ContestCounter.rankTotals(ca.contestResult.voteTotals)

                try {
                    row.put("votes for winner", rankedTotals.get(0).value.toString())
                } catch (e: IndexOutOfBoundsException) {
                    row.put("votes for winner", "")
                }

                try {
                    row.put("votes for runner up", rankedTotals.get(1).value.toString())
                } catch (e: IndexOutOfBoundsException) {
                    row.put("votes for runner up", "")
                }
            //}

            row.put("total votes", ca.contestResult.totalVotes().toString())
            row.put("disagreement count (included in +2 and +1)", ca.disagreementCount().toString())

            rows.add(row.toArray())
        }
        return rows
    }

    /** build a list of rows for a contest based on acvrs  */
    fun getContestActivity(contestName: String): List<List<String?>?> {
        val rows = mutableListOf<List<String?>>()

        val audit: ComparisonAudit? = ComparisonAuditQueries.matching(contestName)
        if (null == audit) {
            // return something in a response to explain the situation
            rows.add(NOT_FOUND_ROW)
            return rows
        }

        rows.add(ActivityReport.HEADERS)
        val contestCVRIds = audit.getContestCVRIds()
        if (contestCVRIds.isEmpty()) {
            // Something has gone wrong, it seems, because all targeted contests should
            // have contestCVRIds by the time the reports button can be clicked - at
            // least that is the intention.
            return rows
        }

        // now we can see if there is any activity
        val pacvrs = CastVoteRecordQueries.activityReport(contestCVRIds)
        val acvrs = pacvrs.sortedBy{ it.timestamp }

        acvrs.forEach { acvr: CastVoteRecord ->
            val row: Row = ActivityReport.newRow()
            rows.add(addActivityFields(addBaseFields(row, audit, acvr), acvr).toArray())
        }

        return rows
    }

    /** build a list of rows for a contest based on tributes  */
    fun getResultsReport(contestName: String): List<List<String?>?> {
        val rows = mutableListOf<List<String?>>()

        val ptributes: List<Tribute> = TributeQueries.forContest(contestName)
        val tributes = ptributes.sortedBy { it.randSequencePosition }

        val audit: ComparisonAudit? = ComparisonAuditQueries.matching(contestName)
        if (null == audit) {
            rows.add(NOT_FOUND_ROW)
            return rows
        }

        val contestCVRIds = audit.getContestCVRIds()

        val acvrs: List<CastVoteRecord> = CastVoteRecordQueries.resultsReport(contestCVRIds)

        rows.add(ResultsReport.HEADERS)

        for (tribute in tributes) {
            val row: Row = ResultsReport.newRow()
            // get the acvr that was submitted for this tribute
            val uri: String = tribute.uri
            val aUri = uri.replaceFirst("^cvr".toRegex(), "acvr")
            val acvr = acvrs
                .filter { it.getUri().equals(aUri) }
                .first()

            if (acvr != null) {
                val multiplicity = audit.multiplicity(acvr.cvrId)
                rows.add(addResultsFields(addBaseFields(row, audit, acvr), tribute, multiplicity).toArray())
            } else {
                // not yet audited, and we don't know the multiplicity
                rows.add(addResultsFields(row, tribute).toArray())
            }
        }

        return rows
    }
}

/**
 * One array to be part of an array of arrays, ie: a table or csv or xlsx.
 * It keeps the headers and fields in order.
 */
class Row(val headers: List<String>) {
    private val map = mutableMapOf<String, String?>() // TODO does this allow nulls ??

    fun get(key: String): String? {
        return this.map.get(key)
    }

    fun put(key: String, value: String?) {
        this.map.put(key, value)
    }

    /** loop over headers, spit out values, keeping them in sync  */
    fun toArray(): List<String?> {
        val a = mutableListOf<String?>()
        for (h in this.headers) {
            a.add(this.get(h))
        }
        return a
    }
}

object ActivityReport {
    val dontwant = listOf("random number sequence position",
        "random number",
        "multiplicity")
    val HEADERS: List<String> = ReportRows.ALL_HEADERS.toList().filter { !(it in dontwant) }
    /* val HEADERS: Array<String> = ArrayUtils.removeElements(
        ArrayUtils.clone(ReportRows.ALL_HEADERS),
        "random number sequence position",
        "random number",
        "multiplicity"
    ) */

    fun newRow(): Row {
        return Row(HEADERS)
    }
}

/** tie the headers to a row  */
object ResultsReport {
    val dontwant = listOf("revision", "re-audit ballot comment")
    val HEADERS: List<String> = ReportRows.ALL_HEADERS.toList().filter{ !(it in dontwant) }

    /* val HEADERS: Array<String?> = ArrayUtils.removeElements(
        ArrayUtils.clone(ReportRows.ALL_HEADERS),
        "revision",
        "re-audit ballot comment"
    ) */

    fun newRow(): Row {
        return Row(HEADERS)
    }
}

object SummaryReport {
    val HEADERS = listOf(
        "Contest",
        "targeted",
        "Winner",

        "Risk Limit met?",
        "Risk measurement %",
        "Audit Risk Limit %",
        "diluted margin %",
        "disc +2",
        "disc +1",
        "disc -1",
        "disc -2",
        "gamma",
        "audited sample count",

        "ballot count",
        "min margin",
        "votes for winner",
        "votes for runner up",
        "total votes",
        "disagreement count (included in +2 and +1)"
    )

    fun newRow(): Row {
        return Row(HEADERS)
    }
}
