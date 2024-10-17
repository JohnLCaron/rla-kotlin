/*
 * Colorado RLA System
 *
 * @title ColoradoRLA
 * @copyright 2018 Colorado Department of State
 * @license SPDX-License-Identifier: AGPL-3.0-or-later
 * @description A system to assist in conducting statewide risk-limiting audits.
 */

package org.cryptobiotic.rla.controller

import org.cryptobiotic.rla.model.CastVoteRecord

/**
 * Ballot sequencing functionality, such as converting a list of CVRs into
 * a sorted, deduplicated list of CVRs.
 *
 * @author Democracy Works, Inc. <democracy.works>
 */
object BallotSequencer {

    /**
     * Returns a sorted, deduplicated list of CVRs given a list of CVRs.
     *
     * The sort order must match the order of the ballots in the "pull list" that
     * counties use to fetch ballots. By storing the sorted, deduplicated list of
     * ballots (CVRs) to audit consistently, we can avoid having to sort them
     * again and reap other benefits like easier partitioning to support multiple
     * audit boards.
     *
     * @param cvrs input CVRs
     * @return sorted, deduplicated list of CVRs
     */
    fun sortAndDeduplicateCVRs(cvrs: List<CastVoteRecord>): List<CastVoteRecord> {

        // Deduplicate CVRs, creating a mapping for use later on.
        val cvrIdToCvrs: Map<Long, CastVoteRecord> = cvrs.distinct().associateBy{ it.id }

        // Join with ballot manifest for the purposes of sorting by location, then sort it.
        //
        // TOOD: Abusing the CVRToAuditResponse class for sorting is wrong; we
        // should reify the "joined CVR / Ballot Manifest" concept.
        val sortedAuditResponses: List<CVRToAuditResponse> = BallotSelection.toResponseList(ArrayList(cvrIdToCvrs.values))
        sortedAuditResponses.sorted()

        // Walk the now-sorted list, pulling CVRs back out of the map.
        //     return sortedAuditResponses.stream()
        //        .map(cvrar -> cvrIdToCvrs.get(cvrar.dbID()))
        //        .collect(Collectors.toList());
        return sortedAuditResponses.map { cvrIdToCvrs[it.dbId]!! }
    }
}