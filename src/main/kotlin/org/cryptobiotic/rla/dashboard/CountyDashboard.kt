package org.cryptobiotic.rla.dashboard

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rla.model.*

import java.time.Instant
import java.util.*

data class CountyDashboard(
    val id: Long,
    val version: Long,
    val county: County,
    val crvFile: UploadedFile,
) {

    /**
     * The number of CVRs imported.
     */
    private var cvrs_imported = 0

    private var my_cvr_import_status: ImportStatus = ImportStatus(ImportStatus.ImportState.NOT_ATTEMPTED, null, null)

    /**
     * The timestamp of the most recent uploaded ballot manifest.
     */
    private var my_manifest_file: UploadedFile? = null

    /**
     * The number of ballots described in the ballot manifest.
     */
    private var my_ballots_in_manifest = 0

    /**
     * The timestamp for the start of the audit.
     */
    private var my_audit_timestamp: Instant? = null

    /**
     * The number of audit boards.
     */
    private var auditBoardCount: Int? = null

    /**
     * The audit boards.
     */
    private val my_audit_boards: MutableMap<Int, AuditBoard> = HashMap()

    /**
     * The audit rounds.
     */
    private val my_rounds = mutableListOf<Round>()

    /**
     * The current audit round.
     */
    private var my_current_round_index: Int? = null

    /**
     * The set of contests that drive our audits. Strings, not "fancy"
     * Abstract Data Types
     */
    private val drivingContestNames: MutableSet<String> = HashSet()


    /**
     * The audit data.
     */
    private val audits: MutableSet<ComparisonAudit> = HashSet()

    /**
     * The audit investigation reports.
     */
    private val my_investigation_reports: MutableList<AuditInvestigationReportInfo> = ArrayList()

    /**
     * The audit interim reports.
     */
    private val my_intermediate_reports: MutableList<IntermediateAuditReportInfo> =
        ArrayList<IntermediateAuditReportInfo>()

    /**
     * The number of ballots audited.
     */
    private var my_ballots_audited = 0

    /**
     * The length of the audited prefix of the list of samples to audit;
     * equivalent to the index of the CVR currently under audit.
     */
    private var my_audited_prefix_length: Int? = null

    /**
     * The number of samples that have been audited so far.
     */
    private var my_audited_sample_count: Int? = null

    /**
     * The number of discrepancies found in the audit so far.
     */
    private val my_discrepancies: MutableMap<AuditSelection, Int> = HashMap()

    /**
     * The number of disagreements found in the audit so far.
     */
    private val my_disagreements: MutableMap<AuditSelection, Int> = HashMap()

    /**
     * @return the audit timestamp. A return value of null means
     * that no audit has been started.
     */
    fun auditTimestamp(): Instant? {
        return my_audit_timestamp
    }

    /**
     * Sets a new audit timestamp, replacing the previous one.
     *
     * @param the_timestamp The new audit timestamp.
     */
    fun setAuditTimestamp(the_timestamp: Instant?) {
        my_audit_timestamp = the_timestamp
    }

    /**
     * @return the number of audit boards.
     */
    fun auditBoardCount(): Int? {
        return this.auditBoardCount
    }

    /**
     * Set the expected number of audit boards.
     *
     * @param count number of audit boards
     */
    fun setAuditBoardCount(count: Int?) {
        this.auditBoardCount = count
    }

    /**
     * @return the entire list of audit boards.
     */
    fun auditBoards(): Map<Int, AuditBoard?> {
        return Collections.unmodifiableMap(my_audit_boards)
    }

    /**
     * Signs in the specified audit board as of the present time;
     * the supplied set of electors must be the full set of electors on
     * the board. The previous audit board, if any, is signed out if it
     * had not yet been signed out.
     *
     * @param the_members The members.
     */
    fun signInAuditBoard(
        index: Int,
        the_members: List<Elector>
    ) {
        val currentBoard = my_audit_boards[index]
        val newBoard = AuditBoard(the_members, Instant.now())

        if (currentBoard != null) {
            this.signOutAuditBoard(index)
        }

        my_audit_boards[index] = newBoard
    }

    /**
     * Signs out the audit board at index.
     *
     * If no audit board is present at the given index, nothing is changed.
     */
    fun signOutAuditBoard(index: Int) {
        val currentBoard = my_audit_boards[index]

        if (currentBoard != null) {
            currentBoard.logoutTime = Instant.now()
            my_audit_boards.remove(index)
        }
    }

    /**
     * Signs out all audit boards.
     */
    fun signOutAllAuditBoards() {
        val ks: Set<Int> = HashSet(my_audit_boards.keys)

        for (i in ks) {
            this.signOutAuditBoard(i)
        }
    }

    /**
     * Test if the desired number of audit boards have signed in.
     *
     * Note: Only works properly for indexes less than the current audit board
     * count in case there are orphaned boards outside of the current expected
     * key range, because just counting the number of keys in the audit board map
     * might yield the wrong answer if there are orphaned audit boards.
     *
     * Use signOutAllAuditBoards to properly clear out the data structure holding
     * all audit boards, signing out audit boards as necessary.
     *
     * @return boolean
     */
    fun areAuditBoardsSignedIn(): Boolean {
        var result = true

        for (i in 0 until auditBoardCount()!!) {
            if (my_audit_boards[i] == null) {
                result = false
                break
            }
        }

        return result
    }

    /**
     * Test if all audit boards are signed out.
     *
     * @return boolean
     */
    fun areAuditBoardsSignedOut(): Boolean {
        var result = true

        for (i in 0 until auditBoardCount()!!) {
            if (my_audit_boards[i] != null) {
                result = false
                break
            }
        }

        return result
    }

    /**
     * @return all the audit rounds.
     */
    fun rounds(): List<Round> {
        return Collections.unmodifiableList(my_rounds)
    }

    /**
     * @return the current audit round, or null if no round is in progress.
     */
    fun currentRound(): Round? {
        return if (my_current_round_index == null) {
            null
        } else {
            my_rounds[my_current_round_index!!]
        }
    }

    /**
     * Begins a new round with the specified number of ballots to audit
     * and expected achieved prefix length, starting at the specified index
     * in the random audit sequence.
     *
     * @param numberOfBallots The number of ballots in this round
     * @param prefixLength The expected audited prefix length at the round's end.
     * @param startIndex The start index.
     * @param ballotSequence The ballots to audit in the round, in the order
     * in which they should be presented.
     * @param auditSubsequence The audit subsequence for the round.
     * @exception IllegalStateException if a round is currently ongoing.
     */
    fun startRound(
        numberOfBallots: Int,
        prefixLength: Int,
        startIndex: Int,
        ballotSequence: List<Long>,
        auditSubsequence: List<Long>
    ) {
        if (my_current_round_index == null) {
            my_current_round_index = my_rounds.size
        } else {
            throw IllegalStateException("cannot start a round while one is running")
        }

        // original code
        //     final Round round = new Round(my_current_round_index + 1,
        //                                  Instant.now(),
        //                                  numberOfBallots,
        //                                  my_ballots_audited,
        //                                  prefixLength,
        //                                  startIndex,
        //                                  ballotSequence,
        //                                  auditSubsequence);


        // note UI round indexing is from 1, not 0
        val round = Round(
            my_current_round_index!! + 1,
            Instant.now(),
            numberOfBallots,
            my_ballots_audited,
            prefixLength,
            startIndex,
            ballotSequence,
        )
        round.auditSubsequence = auditSubsequence
        my_rounds.add(round)
    }

    /**
     * Ends the current round.
     *
     * Signs out all audit boards, and performs any bookkeeping necessary to end
     * the round.
     *
     * @exception IllegalStateException if there is no current round.
     */
    fun endRound() {
        checkNotNull(my_current_round_index) { "no round to end" }
        this.setAuditBoardCount(null)
        this.signOutAllAuditBoards()

        val round: Round = my_rounds[my_current_round_index!!]
        round.endTime = Instant.now()
        my_current_round_index = NO_CONTENT
    }

    /**
     * @return the number of ballots remaining in the current round, or 0
     * if there is no current round.
     */
    fun ballotsRemainingInCurrentRound(): Int {
        val result: Int

        if (my_current_round_index == null) {
            result = 0
        } else {
            val round: Round = currentRound()!!

            result = round.ballotSequence.size - round.actualCount

            LOGGER.debug {
                java.lang.String.format(
                    "[ballotsRemainingInCurrentRound:"
                            + " index=%d, result=%d,"
                            + " ballotSequence=%s"
                            + " ballotSequence.size=%d"
                            + " cdb.auditedSampleCount()=%d]",
                    my_current_round_index,
                    result,
                    round.ballotSequence,
                    round.ballotSequence.size,
                    this.auditedSampleCount()
                )
            }
        }
        return result
    }

    /**
     * @return the set of comparison audits being performed.
     */
    fun getAudits(): Set<ComparisonAudit> {
        return Collections.unmodifiableSet(audits)
    }


    /**
     * @return the set of comparison audits being performed.
     */
    fun comparisonAudits(): Set<ComparisonAudit> {
        return Collections.unmodifiableSet(audits)
    }

    /**
     * Sets the comparison audits being performed.
     *
     * @param audits The comparison audits.
     */
    fun setAudits(audits: Set<ComparisonAudit>?) {
        this.audits.clear()
        this.audits.addAll(audits!!)
    }

    /**
     * @return the set of contest names driving the audit.
     */
    fun drivingContestNames(): Set<String> {
        return Collections.unmodifiableSet(drivingContestNames)
    }

    /**
     * Sets the contests driving the audit.
     *
     * @param the_driving_contests The contests.
     */
    fun setDrivingContestNames(the_driving_contests: Set<String>?) {
        drivingContestNames.clear()
        drivingContestNames.addAll(the_driving_contests!!)
    }

    /**
     * Submits an audit investigation report.
     *
     * @param the_report The audit investigation report.
     */
    fun submitInvestigationReport(the_report: AuditInvestigationReportInfo) {
        my_investigation_reports.add(the_report)
    }

    /**
     * @return the list of submitted audit investigation reports.
     */
    fun investigationReports(): List<AuditInvestigationReportInfo> {
        return Collections.unmodifiableList(my_investigation_reports)
    }

    /**
     * Submits an audit investigation report.
     *
     * @param the_report The audit investigation report.
     */
    fun submitIntermediateReport(the_report: IntermediateAuditReportInfo) {
        my_intermediate_reports.add(the_report)
    }

    /**
     * @return the list of submitted audit interim reports.
     */
    fun intermediateReports(): List<IntermediateAuditReportInfo> {
        return Collections.unmodifiableList(my_intermediate_reports)
    }

    /**
     * Returns the a list of CVR IDs under audit for the assigned audit boards.
     *
     * Delegates the actual calculation to the current Round, if one exists.
     *
     * @return a list of CVR IDs assigned to each audit board, where the list
     * offset matches the audit board offset.
     *
    fun cvrsUnderAudit(): List<Long>? {
        val round: Round = this.currentRound() ?: return null

        return round.cvrsUnderAudit
    } */

    /**
     * @return the number of ballots audited.
     */
    fun ballotsAudited(): Int {
        return my_ballots_audited
    }

    /**
     * Adds an audited ballot. This adds it both to the total and to
     * the current audit round. If no round is ongoing, this method
     * does nothing.
     */
    fun addAuditedBallot() {
        if (my_current_round_index != null) {
            my_ballots_audited = my_ballots_audited + 1
            my_rounds[my_current_round_index!!].addAuditedBallot()
        }
    }

    /**
     * Removes an audited ballot. This removes it both from the total and
     * from the current audit round, if one is ongoing.
     */
    fun removeAuditedBallot() {
        if (my_current_round_index != null) {
            my_ballots_audited = my_ballots_audited - 1
            my_rounds[my_current_round_index!!].removeAuditedBallot()
        }
    }

    /**
     * Sets the number of CVRs imported.
     *
     * @param the_cvrs_imported The number.
     *
    fun setCVRsImported(the_cvrs_imported: Int) {
        my_cvrs_imported = the_cvrs_imported
    }
    */

    /**
     * @return the CVR import status.
     */
    fun cvrImportStatus(): ImportStatus {
        return my_cvr_import_status
    }

    /**
     * Sets the CVR import status.
     *
     * @param the_cvr_import_status The new status.
     */
    fun setCVRImportStatus(the_cvr_import_status: ImportStatus) {
        my_cvr_import_status = the_cvr_import_status
    }

    /**
     * @return the number of ballots described in the ballot manifest.
     */
    fun ballotsInManifest(): Int {
        return my_ballots_in_manifest
    }

    /**
     * Sets the number of ballots described in the ballot manifest.
     *
     * @param the_ballots_in_manifest The number.
     */
    fun setBallotsInManifest(the_ballots_in_manifest: Int) {
        my_ballots_in_manifest = the_ballots_in_manifest
    }

    /**
     * @return the numbers of discrepancies found in the audit so far,
     * categorized by contest audit selection.
     */
    fun discrepancies(): Map<AuditSelection, Int> {
        return Collections.unmodifiableMap(my_discrepancies)
    }

    /**
     * Adds a discrepancy for the specified audit reasons. This adds it both to the
     * total and to the current audit round, if one is ongoing.
     *
     * @param the_reasons The reasons.
     */
    fun addDiscrepancy(the_reasons: Set<AuditReason>) {
        LOGGER.debug(
            java.lang.String.format(
                "[addDiscrepancy for %s County: the_reasons=%s",
                county.name,
                the_reasons
            )
        )
        val selections: MutableSet<AuditSelection> = HashSet()
        for (r in the_reasons) {
            selections.add(r.selection())
        }
        for (s in selections) {
            my_discrepancies[s] = my_discrepancies.getOrDefault(s, 0) + 1
        }
        if (my_current_round_index != null) {
            my_rounds[my_current_round_index!!].addDiscrepancy(the_reasons)
        }
    }

    /**
     * Removes a discrepancy for the specified audit reasons. This removes it
     * both from the total and from the current audit round, if one is ongoing.
     *
     *
     * @param the_reasons The reasons.
     */
    fun removeDiscrepancy(the_reasons: Set<AuditReason>) {
        val selections: MutableSet<AuditSelection> = HashSet()
        for (r in the_reasons) {
            selections.add(r.selection())
        }
        for (s in selections) {
            my_discrepancies[s] = my_discrepancies.getOrDefault(s, 0) - 1
        }
        if (my_current_round_index != null) {
            my_rounds[my_current_round_index!!].removeDiscrepancy(the_reasons)
        }
    }


    /**
     * @return the numbers of disagreements found in the audit so far,
     * categorized by contest audit selection.
     */
    fun disagreements(): Map<AuditSelection, Int> {
        return my_disagreements
    }

    /**
     * Adds a disagreement for the specified audit reasons. This adds it both to the
     * total and to the current audit round, if one is ongoing.
     *
     * @param the_reasons The reasons.
     */
    fun addDisagreement(the_reasons: Set<AuditReason>) {
        val selections: MutableSet<AuditSelection> = HashSet()
        for (r in the_reasons) {
            selections.add(r.selection())
        }
        for (s in selections) {
            my_disagreements[s] = my_disagreements.getOrDefault(s, 0) + 1
        }
        if (my_current_round_index != null) {
            my_rounds[my_current_round_index!!].addDisagreement(the_reasons)
        }
    }

    /**
     * Removes a disagreement for the specified audit reasons. This removes it
     * both from the total and from the current audit round, if one is ongoing.
     *
     *
     * @param the_reasons The reasons.
     */
    fun removeDisagreement(the_reasons: Set<AuditReason>) {
        val selections: MutableSet<AuditSelection> = HashSet()
        for (r in the_reasons) {
            selections.add(r.selection())
        }
        for (s in selections) {
            my_disagreements[s] = my_disagreements.getOrDefault(s, 0) - 1
        }
        if (my_current_round_index != null) {
            my_rounds[my_current_round_index!!].removeDisagreement(the_reasons)
        }
    }

    /**
     * takes the targeted contests/ComparisonAudits and checks them for
     * completion/RiskLimitAchieved
     */
    fun allAuditsComplete(): Boolean {
        return comparisonAudits().stream() // .filter(ca -> ca.isTargeted()) // FIXME This might be better?
            .filter { ca: ComparisonAudit -> ca.auditReason !== AuditReason.OPPORTUNISTIC_BENEFITS }
            .allMatch { ca: ComparisonAudit -> ca.isFinished }
    }

    /**
     * @return the estimated number of samples to audit.
     */
    fun estimatedSamplesToAudit(): Int {
        return comparisonAudits()
            .filter { ca: ComparisonAudit -> ca.auditReason !== AuditReason.OPPORTUNISTIC_BENEFITS }
            .map { ca: ComparisonAudit -> ca.estimatedRemaining() }
            .sum()
    }

    /**
     * @return the optimistic number of samples to audit.
     */
    fun optimisticSamplesToAudit(): Int {
        // NOTE: there could be race conditions between audit boards across counties
        val maybe = comparisonAudits().stream()
            .filter { ca: ComparisonAudit -> ca.auditReason !== AuditReason.OPPORTUNISTIC_BENEFITS }
            .map { ca: ComparisonAudit -> ca.optimisticSamplesToAudit() }
            .max(Comparator.naturalOrder())
        // NOTE: we may be asking for this when we don't need to; when there are no
        // audits setup yet
        return if (maybe.isPresent) maybe.get() else 0
    }

    /**
     * @return the length of the audited prefix of the sequence of
     * ballots to audit (i.e., the number of audited ballots that
     * "count").
     */
    fun auditedPrefixLength(): Int? {
        return my_audited_prefix_length
    }

    /**
     * Sets the length of the audited prefix of the sequence of
     * ballots to audit. If there is no active round, this method does
     * nothing.
     *
     * @param the_audited_prefix_length The audited prefix length.
     */
    fun setAuditedPrefixLength(the_audited_prefix_length: Int) {
        if (my_current_round_index != null) {
            my_audited_prefix_length = the_audited_prefix_length
            my_rounds[my_current_round_index!!].actualAuditedPrefixLength = the_audited_prefix_length
        }
    }

    /**
     * @return the number of samples that have been included in the
     * audit calculations so far.
     */
    fun auditedSampleCount(): Int {
        return my_audited_sample_count ?: 0
    }

    /**
     * Sets the number of samples that have been included in the
     * audit calculations so far.
     *
     * @param the_audited_sample_count The audited sample count.
     */
    fun setAuditedSampleCount(the_audited_sample_count: Int) {
        my_audited_sample_count = the_audited_sample_count
    }

    /**
     * Ends all audits in the county. This changes the status of any audits
     * that have not achieved their risk limit to ENDED.
     *
     * You should not use this lightly, some of the audits might be shared
     * with others!
     */
    fun endAudits() {
        for (ca in audits) {
            ca.endAudit()
        }
    }

    /**
     * End all audits that only belong to this county.
     */
    fun endSingleCountyAudits(): List<ComparisonAudit> {
        return comparisonAudits()
            .filter { a: ComparisonAudit -> a.isSingleCountyFor(county) }
            .map { a: ComparisonAudit ->
                a.endAudit()
                a
            }
    }

    /**
     * Updates the status for all audits in the county. This changes their statuses
     * based on whether they have achieved their risk limits.
     */
    fun updateAuditStatus() {
        for (ca in audits) {
            ca.updateAuditStatus()
        }
    }

    /**
     * @return a String representation of this contest.
     */
    override fun toString(): String {
        return "CountyDashboard [county=$id]"
    }

    /**
     * Compare this object with another for equivalence.
     *
     * @param the_other The other object.
     * @return true if the objects are equivalent, false otherwise.
     */
    override fun equals(the_other: Any?): Boolean {
        return if (the_other is CountyDashboard) {
            // there can only be one county dashboard in the system for each
            // ID, so we check their equivalence by ID
            the_other.id == id
        } else {
            false
        }
    }

    /**
     * @return a hash code for this object.
     */
    override fun hashCode(): Int {
        return id.hashCode()
    }

    companion object {
        /**
         * Class-wide logger
         */
        val LOGGER = KotlinLogging.logger("CountyDashboard")

        /**
         * The "text" constant.
         */
        private const val TEXT = "text"

        /**
         * The minimum number of members on an audit board.
         */
        const val MIN_AUDIT_BOARD_MEMBERS: Int = 2

        /**
         * The minimum number of members on an audit round sign-off.
         */
        const val MIN_ROUND_SIGN_OFF_MEMBERS: Int = 2

        /**
         * The "no content" constant.
         */
        private val NO_CONTENT: Int? = null

        /**
         * The "index" string.
         */
        private const val INDEX = "index"

        /**
         * The "my_id" string.
         */
        private const val MY_ID = "my_id"

        /**
         * The "dashboard_id" string.
         */
        private const val DASHBOARD_ID = "dashboard_id"
    }
}
