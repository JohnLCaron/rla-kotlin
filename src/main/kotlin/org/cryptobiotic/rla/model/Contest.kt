package org.cryptobiotic.rla.model

/**
 * The definition of a contest; comprises a contest name and a set of
 * possible choices.
 *
 * @author Daniel M. Zimmerman <dmz></dmz>@freeandfair.us>
 * @version 1.0.0
 */
data class Contest(
    val id: Long,
    val version: Long,
    val name: String,
    val county: County,
    val description: String,
    val choices: List<Choice>,
    val votesAllowed: Int,
    val winnersAllowed: Int,
    val sequenceNumber: Int,
)

data class Choice(
    val name: String,
    val description: String,
    val qualifiedWriteIn: Boolean,
    val fictitious: Boolean, // whether votes should be counted and it should be displayed.
    // This is to handle cases where specific "fake" choice names are used to delineate sections of a ballot,
    // as with Dominion and qualified write-ins.
)

enum class ContestType {
    /**
     * Single- and multi-winner plurality elections. Voters select their favourite candidates(s), and
     * the winner is the one with the most votes.
     */
    PLURALITY,

    /**
     * Instant-runoff voting (IRV). Voters select candidates with ranks (preferences). The winner is
     * determined by a process of eliminating the candidate with the lowest tally and redistributing
     * their votes according to the next preference, until a candidate has a majority.
     */
    IRV
}
