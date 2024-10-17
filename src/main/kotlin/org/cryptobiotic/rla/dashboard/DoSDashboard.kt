/*
 * Free & Fair Colorado RLA System
 *
 * @title ColoradoRLA
 * @created Jul 27, 2017
 * @copyright 2017 Colorado Department of State
 * @license SPDX-License-Identifier: AGPL-3.0-or-later
 * @creator Daniel M. Zimmerman <dmz@freeandfair.us>
 * @model_review Joseph R. Kiniry <kiniry@freeandfair.us>
 * @description A system to assist in conducting statewide risk-limiting audits.
 */
package org.cryptobiotic.rla.dashboard

import org.cryptobiotic.rla.model.AuditType
import org.cryptobiotic.rla.model.Contest
import org.cryptobiotic.rla.model.County
import java.util.*

/**
 * The Department of State dashboard.
 *
 * @author Daniel M. Zimmerman <dmz></dmz>@freeandfair.us>
 * @version 1.0.0
 */
// this is an unusual entity, in that it is a singleton; it thus has only one possible id (0).
object DoSDashboard {
    /**
     * The DoS dashboard ID (it is a singleton).
     */
    const val ID: Long = 0

    /**
     * The minimum number of random seed characters.
     */
    const val MIN_SEED_LENGTH: Int = 20

    /**
     * The ID. This is always 0, because this object is a singleton.
     */
    private var my_id = ID

    /**
     * The version (for optimistic locking).
     */
    private val my_version: Long? = null

    /**
     * The contests to be audited and the reasons for auditing.
     */
    private val my_contests_to_audit = mutableSetOf<ContestToAudit>()

    /**
     * The election info.
     */
    private val my_audit_info = AuditInfo()

    /**
     * @return the database ID for this dashboard, which is the same as
     * its county ID.
     */
    fun id(): Long {
        return my_id
    }

    /**
     * @return the version for this dashboard.
     */
    fun version(): Long? {
        return my_version
    }

    /**
     * @return the audit info.
     */
    fun auditInfo(): AuditInfo {
        return my_audit_info
    }

    /**
     * Updates the audit info, using the non-null fields of the specified
     * AuditInfo. This method does not do any sanity checks on the fields;
     * it is assumed that they are checked by the caller.
     *
     * @param the_new_info The new info.
     */
    fun combine(the_new_info: AuditInfo): AuditInfo {
        return my_audit_info.combine(the_new_info)
    }

    /**
     * Removes all contests to audit for the specified county. This is
     * typically done if the county re-uploads their CVRs (generating new
     * contest information).
     *
     * @param the_county The county.
     * @return true if any contests to audit were removed, false otherwise.
     */
    fun removeContestsToAuditForCounty(the_county: County?): Boolean {
        var result = false

        val contests_to_remove: MutableSet<ContestToAudit> = HashSet()
        for (c in my_contests_to_audit) {
            if (c.contest.county == the_county) {
                contests_to_remove.add(c)
                result = true
            }
        }
        my_contests_to_audit.removeAll(contests_to_remove)

        return result
    }

    /**
     * Remove all ContestsToAudit that are auditable from this dashboard because
     * they may have been unchecked in the ui. The checked ones should be added
     * back in a following step. Unaditable contests are not able to be checked in
     * the ui so they can stay.
     *
     * note: an alternative approach would be to set a hidden field for every
     * checkbox in the ui
     */
    fun removeAuditableContestsToAudit() {
        my_contests_to_audit.removeAll( my_contests_to_audit.filter { !it.isAuditable() })
        //my_contests_to_audit.removeAll(my_contests_to_audit.stream()
        //    .filter { c: ContestToAudit? -> c!!.isAuditable() }
        //    .collect(Collectors.toList()))
    }

    /** remove a contest by name, supports the hand count button  */
    fun removeContestToAuditByName(contestName: String?) {
        val contests_to_remove: MutableSet<ContestToAudit?> = HashSet()
        for (c in my_contests_to_audit) {
            if (c.contest.name == contestName) {
                contests_to_remove.add(c)
            }
        }
        my_contests_to_audit.removeAll(contests_to_remove)
    }

    /**
     * Update the audit status of a contest.
     *
     * @param the_contest_to_audit The new status of the contest to audit.
     * @return true if the contest was already being audited or hand counted,
     * false otherwise.
     */
    //@ requires the_contest_to_audit != null;
    fun updateContestToAudit(the_contest_to_audit: ContestToAudit): Boolean {
        var auditable = true

        // check to see if the contest is in our set
        var contest_to_remove: ContestToAudit? = null
        for (c in my_contests_to_audit) {
            if (c.contest == the_contest_to_audit.contest) {
                // check if the entry is auditable; if so, it will be removed later
                auditable = !c.audit.equals(AuditType.NOT_AUDITABLE)
                contest_to_remove = c
                break
            }
        }

        if (auditable) {
            my_contests_to_audit.remove(contest_to_remove)
            if (the_contest_to_audit.audit !== AuditType.NONE) {
                my_contests_to_audit.add(the_contest_to_audit)
            }
        }

        return auditable
    }

    /**
     * @return the current set of contests to audit. This is an unmodifiable
     * set; to update, use updateContestToAudit().
     */
    fun contestsToAudit(): Set<ContestToAudit> {
        return Collections.unmodifiableSet(my_contests_to_audit)
    }

    /**
     * data access helper
     * @return contests of contestsToAudit a.k.a selected contests, targeted contests
     */
    fun targetedContests(): List<Contest> {
        return my_contests_to_audit.map { it.contest }
    }

    /**
     * data access helper
     * @return contests names of contestsToAudit a.k.a selected contests, targeted
     * contests
     */
    fun targetedContestNames(): Set<String> {
        return my_contests_to_audit.map { it.contest.name }.toSet()
    }


    /**
     * Checks the validity of a random seed. To be valid, a random seed must
     * have at least MIN_SEED_CHARACTERS characters, and all characters must
     * be digits.
     *
     * @param the_seed The seed.
     * @return true if the seed meets the validity requirements, false otherwise.
     */
    fun isValidSeed(the_seed: String?): Boolean {
        var result = true

        if (the_seed != null && the_seed.length >= MIN_SEED_LENGTH) {
            for (c in the_seed.toCharArray()) {
                if (!Character.isDigit(c)) {
                    result = false
                    break
                }
            }
        } else {
            result = false
        }

        return result
    }
}
