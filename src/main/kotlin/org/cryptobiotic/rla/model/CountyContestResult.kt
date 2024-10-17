/*
 * Free & Fair Colorado RLA System
 * 
 * @title ColoradoRLA
 * @created Aug 19, 2017
 * @copyright 2017 Colorado Department of State
 * @license SPDX-License-Identifier: AGPL-3.0-or-later
 * @creator Daniel M. Zimmerman <dmz@freeandfair.us>
 * @description A system to assist in conducting statewide risk-limiting audits.
 */
package org.cryptobiotic.rla.model

import java.io.Serializable
import java.math.BigDecimal
import java.math.MathContext
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * A class representing the results for a single contest for a single county.
 *
 * @author Daniel M. Zimmerman <dmz></dmz>@freeandfair.us>
 * @version 1.0.0
 */
data class CountyContestResult(
    val id: Long,
    val version: Long,
    val county: County,
    val contest: Contest,
    val winnersAllowed: Int,
) {
    val winners = mutableSetOf<String>()
    val losers = mutableSetOf<String>()
    val voteTotals = mutableMapOf<String, Int>()

    /**
     * The minimum pairwise margin between a winner and a loser.
     */
    private var my_min_margin: Int? = null

    /**
     * The maximum pairwise margin between a winner and a loser.
     */
    private var my_max_margin: Int? = null

    /**
     * The total number of ballots cast in this county.
     */
    private var my_county_ballot_count = 0

    /**
     * The total number of ballots cast in this county that contain this contest.
     */
    private var my_contest_ballot_count = 0

    /**
     * @return a list of the choices in descending order by number of votes received.
     */
    fun rankedChoices(): List<String> {
        val result: MutableList<String> = ArrayList()

        val sorted_totals: SortedMap<Int, MutableList<String>> = TreeMap(ReverseIntegerComparator())
        for ((key, value) in voteTotals) {
            val list: List<String>? = sorted_totals[value]
            if (list == null) {
                sorted_totals[value] = ArrayList()
            }
            sorted_totals[value]!!.add(key)
        }

        val iterator: Iterator<Map.Entry<Int?, List<String>>> =
            sorted_totals.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            result.addAll(entry.value)
        }
        return result
    }

    /**
     * Change a choice name as part of Canonicalization.
     */
    fun updateChoiceName(
        oldName: String,
        newName: String
    ) {
        val vote_total = voteTotals.remove(oldName)
        voteTotals[newName] = vote_total!!
    }

    /**
     * Compute the pairwise margin between the specified choices.
     * If the first choice has more votes than the second, the
     * result will be positive; if the second choie has more
     * votes than the first, the result will be negative; if they
     * have the same number of votes, the result will be 0.
     *
     * @param the_first_choice The first choice.
     * @param the_second_choice The second choice.
     * @return the pairwise margin between the two choices, as
     * an OptionalInt (empty if the margin cannot be calculated).
     */
    fun pairwiseMargin(
        the_first_choice: String,
        the_second_choice: String
    ): OptionalInt {
        val first_votes = voteTotals[the_first_choice]
        val second_votes = voteTotals[the_second_choice]

        val result: OptionalInt = if (first_votes == null || second_votes == null) {
            OptionalInt.empty()
        } else {
            OptionalInt.of(first_votes - second_votes)
        }

        return result
    }

    /**
     * Computes the margin between the specified choice and the next choice.
     * If the specified choice is the last choice, or is not a valid choice,
     * the margin is empty.
     *
     * @param the_choice The choice.
     * @return the margin.
     */
    fun marginToNearestLoser(the_choice: String): Int? {
        var result: Int?
        val choices = rankedChoices()
        var index = choices.indexOf(the_choice)

        if (index < 0 || index == choices.size - 1) {
            result = null
        } else {
            // find the nearest loser
            var loser = ""
            index = index + 1
            while (index < choices.size && !losers.contains(loser)) {
                loser = choices[index]
                index = index + 1
            }
            result = if (losers.contains(loser)) {
                    voteTotals[the_choice]!! - voteTotals[loser]!!
            } else {
                // there was no nearest loser, maybe there are only winners
                null
            }
        }

        return result
    }

    /**
     * Computes the diluted margin between the specified choice and the nearest
     * loser. If the specified choice is the last choice or is not a valid
     * choice, or the margin is undefined, the result is null.
     *
     * @param the_choice The choice.
     * @return the margin.
     */
    fun countyDilutedMarginToNearestLoser(the_choice: String): BigDecimal? {
        var result: BigDecimal? = null
        val margin: Int? = marginToNearestLoser(the_choice)

        if (margin != null && my_county_ballot_count > 0) {
            result = BigDecimal.valueOf(margin.toLong()).divide(
                BigDecimal.valueOf(my_county_ballot_count.toLong()), MathContext.DECIMAL128)
        }

        return result
    }

    /**
     * Computes the diluted margin between the specified choice and the nearest
     * loser. If the specified choice is the last choice or is not a valid
     * choice, or the margin is undefined, the result is null.
     *
     * @param the_choice The choice.
     * @return the margin.
     */
    fun contestDilutedMarginToNearestLoser(the_choice: String): BigDecimal? {
        var result: BigDecimal? = null
        val margin: Int? = marginToNearestLoser(the_choice)

        if (margin != null && my_contest_ballot_count > 0) {
            result = BigDecimal.valueOf(margin.toLong()).divide(
                BigDecimal.valueOf(my_contest_ballot_count.toLong()), MathContext.DECIMAL128)
        }

        return result
    }

    /**
     * @return the county diluted margin for this contest, defined as the
     * minimum margin divided by the number of ballots cast in the county.
     * @exception IllegalStateException if no ballots have been counted.
     */
    fun countyDilutedMargin(): BigDecimal {
        var result: BigDecimal
        if (my_county_ballot_count > 0) {
            result = BigDecimal.valueOf(my_min_margin!!.toLong()).divide(
                BigDecimal.valueOf(my_county_ballot_count.toLong()), MathContext.DECIMAL128
            )
            if (losers.isEmpty()) {
                // if we only have winners, there is no margin
                result = BigDecimal.ONE
            }

            // TODO: how do we handle a tie?
        } else {
            throw IllegalStateException("attempted to calculate diluted margin with no ballots")
        }

        return result
    }

    /**
     * @return the diluted margin for this contest, defined as the
     * minimum margin divided by the number of ballots cast in this county
     * that contain this contest.
     * @exception IllegalStateException if no ballots have been counted.
     */
    fun contestDilutedMargin(): BigDecimal {
        var result: BigDecimal
        if (my_contest_ballot_count > 0) {
            result = BigDecimal.valueOf(my_min_margin!!.toLong()).divide(
                BigDecimal.valueOf(my_contest_ballot_count.toLong()),
                MathContext.DECIMAL128
            )
            if (losers.isEmpty()) {
                // if we only have winners, there is no margin
                result = BigDecimal.ONE
            }

            // TODO: how do we handle a tie?
        } else {
            throw IllegalStateException("attempted to calculate diluted margin with no ballots")
        }

        return result
    }

    /**
     * Update the vote totals using the data from the specified CVR.
     *
     * @param the_cvr The CVR.
     */
    fun addCVR(the_cvr: CastVoteRecord) {
        val ci = the_cvr.contestInfoForContest(contest)
        if (ci != null) {
            for (s in ci.choices) {
                voteTotals[s] = voteTotals[s]!! + 1
            }
            my_contest_ballot_count += 1
        }
        my_county_ballot_count += 1
    }

    /**
     * Updates the stored results.
     */
    fun updateResults() {
        // first, sort the vote totals
        val sorted_totals: SortedMap<Int, MutableList<String>> = TreeMap(ReverseIntegerComparator())
        for ((key, value) in voteTotals) {
            val list: List<String>? = sorted_totals[value]
            if (list == null) {
                sorted_totals[value] = ArrayList()
            }
            sorted_totals[value]!!.add(key)
        }
        // next, get the winners and losers
        val vote_total_iterator: Iterator<Map.Entry<Int?, List<String>>> =
            sorted_totals.entries.iterator()
        var entry: Map.Entry<Int?, List<String>>? = null
        while (vote_total_iterator.hasNext() && winners.size < winnersAllowed) {
            entry = vote_total_iterator.next()
            val choices = entry.value
            if (choices.size + winners.size <= winnersAllowed) {
                winners.addAll(choices)
            } else {
                // we are arbitrarily making the first choices in the list "winners" and
                // the last choices in the list "losers", but since it's a tie, it really
                // doesn't matter
                val to_add = winnersAllowed!! - winners.size
                winners.addAll(choices.subList(0, to_add))
                losers.addAll(choices.subList(to_add, choices.size))
            }
        }
        while (vote_total_iterator.hasNext()) {
            // all the other choices count as losers
            losers.addAll(vote_total_iterator.next().value)
        }

        calculateMargins()
    }

    /**
     * Calculates all the pairwise margins using the vote totals.
     */
    private fun calculateMargins() {
        my_min_margin = Int.MAX_VALUE
        my_max_margin = Int.MIN_VALUE
        for (w in winners) {
            if (losers.isEmpty()) {
                // this could be either uncontested or tied (I think) and it means that the
                // ContestToAudit will have an AuditType of NOT_AUDITABLE
                my_min_margin = 0
                my_max_margin = 0
            } else {
                for (l in losers) {
                    val margin = voteTotals[w]!! - voteTotals[l]!!
                    my_min_margin = min(my_min_margin!!, margin)
                    my_max_margin = max(my_max_margin!!, margin)
                }
            }
        }
    }

    /**
     * @return a String representation of this contest.
     */
    override fun toString(): String {
        return "CountyContestResult [id=$id]"
    }

    /**
     * A reverse integer comparator, for sorting lists of integers in reverse.
     */
    class ReverseIntegerComparator

        : Comparator<Int>, Serializable {
        /**
         * Compares two integers. Returns the negation of the regular integer
         * comparator result.
         *
         * @param the_first The first integer.
         * @param the_second The second integer.
         */
        override fun compare(the_first: Int, the_second: Int): Int {
            return -(the_first.compareTo(the_second))
        }

        companion object {
            /**
             * The serialVersionUID.
             */
            private const val serialVersionUID = 1L
        }
    }

    companion object {
        /**
         * The "my_id" string.
         */
        private const val MY_ID = "my_id"

        /**
         * The "result_id" string.
         */
        private const val RESULT_ID = "result_id"
    }
}
