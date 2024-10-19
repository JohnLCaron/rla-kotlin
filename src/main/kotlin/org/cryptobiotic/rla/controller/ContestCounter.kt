package org.cryptobiotic.rla.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rla.math.Audit
import org.cryptobiotic.rla.model.AuditReason

import org.cryptobiotic.rla.model.ContestResult
import org.cryptobiotic.rla.model.CountyContestResult
import org.cryptobiotic.rla.persistence.BallotManifestInfoQueries
import org.cryptobiotic.rla.persistence.ContestResultQueries
import org.cryptobiotic.rla.persistence.Persistence


object ContestCounter {
    val LOGGER = KotlinLogging.logger("ContestCounter")

    /**
     * Group all CountyContestResults by contest name and tally the votes
     * across all counties that have reported results.
     * This only works for plurality - not valid, and not needed, for IRV.
     *
     * @return List<ContestResult> A high level view of contests and their
     * participants.
    </ContestResult> */
    //     return
    //      Persistence.getAll(CountyContestResult.class)
    //      .stream()
    //      .collect(Collectors.groupingBy(x -> x.contest().name()))
    //      .entrySet()
    //      .stream()
    //      .map(ContestCounter::countContest)
    //      .collect(Collectors.toList());
    fun countAllContests(tcrs: Map<String, AuditReason>): List<ContestResult> {
        val allCcr: List<CountyContestResult> = Persistence.getAll(CountyContestResult::class.java)
        val groupByName = mutableMapOf<String, MutableList<CountyContestResult>>()
        allCcr.forEach{ ccr ->
            val group = groupByName.getOrPut(ccr.contest.name) { mutableListOf() }
            group.add(ccr)
        }
        val result = groupByName.map { (k, v) -> countContest(k, v, tcrs[k]!!)}
        return result
    }

    /**
     * Set voteTotals on CONTEST based on all counties that have that
     * Contest name in their uploaded CVRs
     * Not valid for IRV.
     */
    fun countContest(contestName:String, countyContestResults: List<CountyContestResult>, reason: AuditReason): ContestResult {
        // the only thing not set is auditReason
        // val contestResult: ContestResult = ContestResultQueries.findOrCreate(contestName) // wtf?

        //     final Map<String,Integer> voteTotals =
        //      accumulateVoteTotals(countyContestResults.getValue().stream()
        //                           .map((cr) -> cr.voteTotals())
        //                           .collect(Collectors.toList()));
        val allVoteTotals: Map<String, Int> = accumulateVoteTotals(countyContestResults.map { it.voteTotals })

        //// doing some elaborate check that all the countyContestResults have the same winnersAllowed
        // take the largest if theres more than one
        //     int numWinners;
        //    final Set<Integer> winnersAllowed = countyContestResults.getValue().stream()
        //      .map(x -> x.winnersAllowed())
        //      .collect(Collectors.toSet());
        val winnersAllowed: Set<Int> = countyContestResults.map { it.winnersAllowed }.toSet()
        val numWinners = if (winnersAllowed.isEmpty()) {
            LOGGER.error(
                String.format(
                    "[countContest: %s doesn't have any winners allowed."
                            + " Assuming 1 allowed! Check the CVRS!", contestName
                )
            )
            1
        } else {
            if (winnersAllowed.size > 1) {
                LOGGER.error(
                    String.format(
                        "[countContest: County results for %s contain different"
                                + " numbers of winners allowed: %s. Check the CVRS!",
                        contestName, winnersAllowed
                    )
                )
            }
            winnersAllowed.max()
        }

        // why would there be more than one contest ?
        val contests = countyContestResults.map { it.contest }.toSet()
        val counties = countyContestResults.map { it.county }.toSet()
        val countyIds = countyContestResults.map { it.id }.toSet()

        val ballotCount: Long = BallotManifestInfoQueries.totalBallots(countyIds)
        if (ballotCount == 0L) {
            LOGGER.error(
                String.format(
                    "[countContest: %s has no ballot manifests for"
                            + " countyIDs: %s", contestName, countyIds
                )
            )
        }

        val winners = winners(allVoteTotals, numWinners)
        val losers = losers(allVoteTotals, winners)
        val margins: Set<Int> = pairwiseMargins(winners, losers, allVoteTotals)
        val minMargin = margins.min()
        val maxMargin = margins.max()
        val dilutedMargin = Audit.dilutedMargin(minMargin, ballotCount) // LOOK

        // dilutedMargin of zero is ok here, it means the contest is uncontested
        // and the contest will not be auditable, so samples should not be selected for it

        //     val id: Long,
        //    val version: Long,
        //    val contestId: String,
        //    val winnersAllowed: Int,
        //    val winners: Set<String>,
        //    val losers: Set<String>,
        //    val counties: Set<County>,
        //    val contests: Set<Contest>,
        //    val voteTotals: Map<String, Int>,
        //    val contestName: String,
        //    val dilutedMargin: BigDecimal,
        //    val minMargin: Int,
        //    val maxMargin: Int,
        //    val ballotCount: Long,
        //    val auditReason: AuditReason,


        val contestResult = ContestResult(
            0L, 0L, // TODO
            contestName,
            numWinners,
            winners,
            losers,
            counties,
            contests,
            allVoteTotals,
            contestName,
            dilutedMargin,
            minMargin,
            maxMargin,
            ballotCount,
            auditReason = reason,
            )
        return contestResult
    }

    /** add em up  */
    fun accumulateVoteTotals(ccrVoteTotals: List<Map<String, Int>>): Map<String, Int> {
        val acc = mutableMapOf<String, Int>()
        ccrVoteTotals.forEach { votes: Map<String, Int> ->
            votes.entries.forEach { (k, v) ->
                val candVotes = acc.getOrPut(k) { 0 }
                acc[k] = candVotes + v
            }
        }
        return acc.toMap()
    }

    /** add one vote total to another
    fun addVoteTotal(
        acc: MutableMap<kotlin.String?, Int?>,
        vt: MutableMap<kotlin.String?, Int?>
    ): MutableMap<kotlin.String?, Int?> {
        // we iterate over vt because it may have a key that the accumulator has not
        // seen yet
        vt.forEach { (k: kotlin.String?, v: Int?) ->
            acc.merge(
                k, v!!
            ) { v1: Int?, v2: Int? -> if ((null == v1)) v2 else v1 + v2 }
        }
        return acc
    }
    */

    /**
     * Calculates all the pairwise margins - like a cross product - using
     * the vote totals. When there are no losers, all margins are zero.
     * Not valid for IRV.
     *
     * @param winners those who won the contest
     * @param losers those who did not win the contest
     * @param voteTotals a map of choice name to number of votes received
     * @return a Set of Integers representing all margins between winners and losers
     */
    fun pairwiseMargins(winners: Set<String>, losers: Set<String>, voteTotals: Map<String, Int>): Set<Int> {
        val margins = mutableSetOf<Int>()

        if (losers.isEmpty()) {
            margins.add(0)
        } else {
            for (w in winners) {
                for (l in losers) {
                    margins.add(voteTotals[w]!! - voteTotals[l]!!)
                }
            }
        }
        return margins
    }

    fun winners(voteTotals: Map<String, Int>, winnersAllowed: Int = 1): Set<String> {
        return rankTotals(voteTotals).map { it.key }.subList(0, winnersAllowed).toSet()
    }

    fun losers(voteTotals: Map<String, Int>, winners: Set<String>): Set<String> {
        val l = mutableSetOf<String>()
        l.addAll(voteTotals.keys)
        l.removeAll(winners)
        return l
    }

    /**
     * Ranks a list of the choices in descending order by number of votes
     * received. Not relevant to IRV; not related to ranked-choice voting.
     */
    fun rankTotals(voteTotals: Map<String, Int>): List<Map.Entry<String, Int>> {
        return voteTotals.entries
            .sortedBy { it.value }
            .reversed()
    }
}
