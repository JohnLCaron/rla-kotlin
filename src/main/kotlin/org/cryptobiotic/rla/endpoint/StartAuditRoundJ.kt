package org.cryptobiotic.rla.endpoint


import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rla.asm.AuditBoardDashboardASM
import org.cryptobiotic.rla.asm.AuditBoardDashboardEvent
import org.cryptobiotic.rla.asm.AuditBoardDashboardEvent.*
import org.cryptobiotic.rla.asm.CountyDashboardASM
import org.cryptobiotic.rla.asm.CountyDashboardEvent
import org.cryptobiotic.rla.asm.CountyDashboardEvent.*
import org.cryptobiotic.rla.asm.CountyDashboardState
import org.cryptobiotic.rla.controller.BallotSelection
import org.cryptobiotic.rla.controller.BallotSelection.Segment
import org.cryptobiotic.rla.controller.BallotSelection.Selection
import org.cryptobiotic.rla.controller.ComparisonAuditController
import org.cryptobiotic.rla.controller.ContestCounter
import org.cryptobiotic.rla.dashboard.ContestToAudit
import org.cryptobiotic.rla.dashboard.CountyDashboard
import org.cryptobiotic.rla.dashboard.DoSDashboard
import org.cryptobiotic.rla.model.AuditReason
import org.cryptobiotic.rla.model.AuditSelection
import org.cryptobiotic.rla.model.CastVoteRecord
import org.cryptobiotic.rla.model.ComparisonAudit
import org.cryptobiotic.rla.model.ContestResult
import org.cryptobiotic.rla.persistence.Persistence
import org.cryptobiotic.rla.persistence.PersistentASMQuery
import org.cryptobiotic.rla.util.PhantomBallots
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.math.BigDecimal

/**
 * Starts a new audit round for one or more counties.
 *
 * @author Daniel M. Zimmerman <dmz></dmz>@freeandfair.us>
 * @version 1.0.0
 */
class StartAuditRoundJ {

    /**
     * Count ContestResults, create ComparisonAudits and assign them to CountyDashboards.
     */
    fun initializeAuditData(dosdb: DoSDashboard) {
        val contestResults = initializeContests(dosdb.contestsToAudit())
        val comparisonAudits = initializeAudits(contestResults, dosdb.auditInfo().riskLimit)
        val cdbs: List<CountyDashboard> = Persistence.getAll(CountyDashboard::class.java)
        for (cdb in cdbs) {
            initializeCountyDashboard(cdb, comparisonAudits)
        }
    }

    fun initializeContests(cta: Set<ContestToAudit>): List<ContestResult> {
        val countedCRs = countAndSaveContests(cta)
        LOGGER.debug(String.format("[initializeContests: cta=%s, countedCRs=%s]", cta, countedCRs))
        return countedCRs
    }

    fun initializeAudits(contestResults: List<ContestResult>, riskLimit: BigDecimal): List<ComparisonAudit> {
        return contestResults.map { cr -> ComparisonAuditController.createAudit(cr, riskLimit) }
    }

    /**
     * Setup a county dashboard. Puts the dashboard into the
     * COUNTY_START_AUDIT_EVENT state.
     *
     * Puts the right set of comparison audits on the cdb.
     *
     * Builds comparison audits for the driving contests.
     */
    fun initializeCountyDashboard(
        cdb: CountyDashboard,
        comparisonAudits: List<ComparisonAudit>,
    ) {
        // FIXME extract-fn
        val drivingContestNames = comparisonAudits
            .filter { ca -> ca.contestResult.auditReason !== AuditReason.OPPORTUNISTIC_BENEFITS }
            .map { ca -> ca.contestResult.contestName }.toSet()

        // OK.
        cdb.setAuditedSampleCount(0)
        cdb.setAuditedPrefixLength(0)
        cdb.setDrivingContestNames(drivingContestNames)

        // FIXME extract-fn
        var countyAudits: Set<ComparisonAudit>? = null
        if (cdb.getAudits().isEmpty()) {
            countyAudits = comparisonAudits.filter { ca -> ca.isForCounty(cdb.county.id) }.toSet()
            cdb.setAudits(countyAudits)
        }

        // FIXME extract-fn
        // The county missed its deadline, nothing to start, so let's mark it so
        val countyDashboardASM: CountyDashboardASM =
            PersistentASMQuery.asmFor(CountyDashboardASM::class.java, cdb.id.toString())!! as CountyDashboardASM
        val auditDashboardASM: AuditBoardDashboardASM =
            PersistentASMQuery.asmFor(AuditBoardDashboardASM::class.java, cdb.id.toString())!! as AuditBoardDashboardASM

        if (countyDashboardASM.currentState() !== CountyDashboardState.BALLOT_MANIFEST_AND_CVRS_OK) {
            LOGGER.info(
                String.format(
                    "[%s County missed the file upload deadline]",
                    cdb.county.name
                )
            )
            auditDashboardASM.stepEvent(AuditBoardDashboardEvent.NO_CONTESTS_TO_AUDIT_EVENT)
        }
        countyDashboardASM.stepEvent(CountyDashboardEvent.COUNTY_START_AUDIT_EVENT)
        PersistentASMQuery.save(countyDashboardASM)
        PersistentASMQuery.save(auditDashboardASM)

        if (!countyDashboardASM.isInInitialState() && !countyDashboardASM.isInFinalState()) {
            LOGGER.debug(
                String
                    .format(
                        "[initializeCountyDashboard: " + " cdb=%s, comparisonAudits=%s, " +
                                " drivingContestNames=%s, countyAudits=%s]", cdb, comparisonAudits,
                        drivingContestNames, countyAudits
                    )
            )
        }
    }

    /**
     * {@inheritDoc}
     *
    public override fun endpointBody(the_request: Request?, the_response: Response?): kotlin.String {
    val dosdb: DoSDashboard = Persistence.getByID(DoSDashboard.ID, DoSDashboard::class.java)
    if (my_asm.get().currentState() === COMPLETE_AUDIT_INFO_SET) {
    // this is the first round
    // this needs to happen after uploading is done but before the audit is started
    initializeAuditData(dosdb)
    }

    my_event.set(DOS_START_ROUND_EVENT)
    return startRound(the_request, the_response)
    }
     */

    /**
     * Provide the reasons for auditing each targeted contest
     *
     * @return a map of contest name to audit reason
     */
    fun targetedContestReasons(ctas: Set<ContestToAudit>): Map<String, AuditReason> {
        val contestToAudits: Map<String, List<ContestToAudit>> = ctas.groupBy { it.contest.name }

        return contestToAudits.entries.map { (k, v) ->
            val reason = v.first().reason // just taking the first one, dont really need groupBy
            Pair(k, reason)
        }.toMap()
    }

    /**
     * Update every - targeted and opportunistic both - contest's voteTotals from
     * the counties. This needs to happen between all counties uploading their
     * data and before the ballot selection happens
     */
    fun countAndSaveContests(cta: Set<ContestToAudit>): List<ContestResult> {
        LOGGER.debug { "[countAndSaveContests: cta=$cta]" }
        val tcr: Map<String, AuditReason> = targetedContestReasons(cta)
        return ContestCounter.countAllContests(tcr)
    }

    /** sets selection on each contestResult, the results of BallotSelection.randomSelection */
    fun makeSelections(
        comparisonAudits: List<ComparisonAudit>,
        seed: String,
        riskLimit: BigDecimal,
    ): List<Selection> {
        val selections = mutableListOf<Selection>()

        for (comparisonAudit in comparisonAudits) {
            val contestResult: ContestResult = comparisonAudit.contestResult
            // only make selection for targeted contests
            if (contestResult.auditReason.isTargeted()) {
                val startIndex: Int =
                    BallotSelection.auditedPrefixLength(comparisonAudit.getContestCVRIds())
                val endIndex: Int = comparisonAudit.optimisticSamplesToAudit()

                val selection: Selection =
                    BallotSelection.randomSelection(contestResult, seed, startIndex, endIndex)

                LOGGER.debug(
                    String.format(
                        "[makeSelections for ContestResult: contestName=%s, " +
                                "contestResult.contestCVRIds=%s, selection=%s, " +
                                "selection.contestCVRIds=%s, startIndex=%d, endIndex=%d]",
                        contestResult.contestName,
                        comparisonAudit.getContestCVRIds(), selection,
                        selection.contestCVRIds(), startIndex, endIndex
                    )
                )

                LOGGER.info(
                    String.format(
                        "[makeSelections for ContestResult: contestName=%s, " +
                                "contestResult.contestCVRIds=%s, selection=%s, " +
                                "selection.contestCVRIds=%s, startIndex=%d, endIndex=%d]",
                        contestResult.contestName,
                        comparisonAudit.getContestCVRIds(), selection,
                        selection.contestCVRIds(), startIndex, endIndex
                    )
                )

                comparisonAudit.addContestCVRIds(selection.contestCVRIds())

                selections.add(selection)
            }
        }
        return selections
    }

    /**
     * Starts the first audit round.
     *
     * @param the_request The HTTP request.
     * @param the_response The HTTP response.
     * @return the result for endpoint.
     */
    // FIXME With some refactoring, we won't have excessive method length.
    fun startRound(): String {
        val dosdb: DoSDashboard = Persistence.getByID(DoSDashboard.ID, DoSDashboard::class.java)!! // wtf
        val riskLimit: BigDecimal = dosdb.auditInfo().riskLimit
        val seed: String = dosdb.auditInfo().seed

        val comparisonAudits: List<ComparisonAudit> = Persistence.getAll(ComparisonAudit::class.java)
            .filter { ca -> ca.isTargeted && !ca.isFinished }

        // [[ComparisonAudit for Adams COUNTY COMMISSIONER DISTRICT 3:
        // counties=[County [name=Adams, id=1]], auditedSampleCount=4,
        // overstatements=0.000000, contestResult.contestCvrIds=[559, 422, 537,
        // 411], status=IN_PROGRESS, reason=COUNTY_WIDE_CONTEST]]
        val selections: List<Selection> = makeSelections(comparisonAudits, seed, riskLimit)

        // Nothing in this try-block should know about HTTP requests / responses
        try {
            // this flag starts off true if we're going to conjoin it with all
            // the ASM states, and false otherwise as we just assume audit
            // reasonableness in the absence of ASMs. We'll remind you about
            // it at the end.
            // FIXME map a function over a collection of dashboardsToStart
            // FIXME extract-fn (for days): update every county dashboard with
            // a list of ballots to audit
            for (cdb in dashboardsToStart()) {
                try {
                    val countyDashboardASM = PersistentASMQuery.asmFor(CountyDashboardASM::class.java, cdb.id.toString())!! as CountyDashboardASM

                    var isRisk = true // was false
                    /*
                    val audits = cdb.getAudits()
                    val contestStrs = audits.map { it.contestResult.contestName }
                    val resultReports = ReportRows.genSumResultsReport()
                    for (i in resultReports.indices) {
                        val name = resultReports.get(i)
                        if (contestStrs.contains(name.get(0))) {
                            if (name.get(3).equals("No", ignoreCase = true)) {
                                isRisk = true
                                break
                            }
                        }
                    } */
                    // If a county still has an audit underway, check to see if
                    // they've achieved their risk limit before starting anything
                    // else. A county that has met the risk limit is done.
                    if (countyDashboardASM.currentState() == CountyDashboardState.COUNTY_AUDIT_UNDERWAY &&
                        cdb.allAuditsComplete() &&
                        (!isRisk)
                    ) {
                        LOGGER
                            .info(
                                String.format(
                                    "[startRound: allAuditsComplete! %s County is FINISHED.]",
                                    cdb.county.name
                                )
                            )
                        PersistentASMQuery.step(
                            RISK_LIMIT_ACHIEVED_EVENT, AuditBoardDashboardASM::class.java,
                            cdb.id.toString()
                        )
                        countyDashboardASM.stepEvent(COUNTY_AUDIT_COMPLETE_EVENT)

                        PersistentASMQuery.save(countyDashboardASM)
                        continue
                    }
                    // Round 2 start
                    // No discrepencies
                    // Go to next round
                    //
                    var disagreements = 0
                    if (cdb.discrepancies().size > 0) {
                        if (cdb.discrepancies().get(AuditSelection.AUDITED_CONTEST) != null) {
                            disagreements = cdb.discrepancies().get(AuditSelection.AUDITED_CONTEST)!!
                        }
                    }

                    if (countyDashboardASM.currentState()
                            .equals(CountyDashboardState.COUNTY_AUDIT_UNDERWAY) &&
                        !cdb.allAuditsComplete() &&
                        (cdb.rounds().size > 0) && (disagreements == 0)
                    ) {
                        cdb.getAudits().filter { ca -> !ca.isFinished }.forEach { ca ->
                            ca.updateAuditStatus()
                            Persistence.save(ca)
                        }
                    } // sometimes audit status is left in air... and make sure we are good


                    if (countyDashboardASM.currentState()
                            .equals(CountyDashboardState.COUNTY_AUDIT_UNDERWAY) && cdb.allAuditsComplete() &&
                        (cdb.rounds().size > 0) && (disagreements == 0)
                    ) {
                        LOGGER
                            .info(
                                String.format(
                                    "[startRound: allAuditsComplete! %s County is FINISHED. Audited Disagreements == 0]",
                                    cdb.county.name
                                )
                            )
                        PersistentASMQuery.step(
                            RISK_LIMIT_ACHIEVED_EVENT, AuditBoardDashboardASM::class.java,
                            cdb.id.toString()
                        )
                        countyDashboardASM.stepEvent(COUNTY_AUDIT_COMPLETE_EVENT)

                        PersistentASMQuery.save(countyDashboardASM)
                        continue
                    }

                    // Risk limit hasn't been achieved and we were never given any
                    // audits to work on.
                    if (cdb.comparisonAudits().isEmpty()) {
                        LOGGER
                            .info("[startRound: county made its deadline but was assigned no contests to audit]")
                        PersistentASMQuery.step(
                            NO_CONTESTS_TO_AUDIT_EVENT, AuditBoardDashboardASM::class.java,
                            cdb.id.toString()
                        )
                        countyDashboardASM.stepEvent(COUNTY_AUDIT_COMPLETE_EVENT)
                        PersistentASMQuery.save(countyDashboardASM)
                        continue  // this county is completely finished.
                    }

                    // Find the ballot selections for all contests that this county is participating in.
                    //           final Segment segment = Selection.combineSegments(selections.stream()
                    //              .map(s -> s.forCounty(cdb.county().id())).collect(Collectors.toList()));
                    val selectionsForCounty = selections.map { it.forCounty( cdb.county.id) }.filterNotNull()
                    val accumSegment: Segment = combineSegments(selectionsForCounty)

                    // Obtain all de-duplicated, ordered CVRs, then audit phantom ballots,
                    // removing them from the sequence to audit so the boards don’t have to.
                    val ballotSequenceCVRs: List<CastVoteRecord> =
                        PhantomBallots.removePhantomRecords(
                            PhantomBallots.auditPhantomRecords(cdb, accumSegment.cvrsInBallotSequence())
                        )

                    // ballotSequence is *just* the CVR IDs, as expected.
                    val ballotSequence: List<Long> = ballotSequenceCVRs.map { it.id }

                    // similar message also sent to info below, this could be a big line
                    LOGGER.trace(
                        String
                            .format(
                                "[startRound:" + " county=%s, round=%s, segment.auditSequence()=%s," +
                                        " segment.ballotSequence()=%s, cdb.comparisonAudits=%s,", cdb.county,
                                cdb.currentRound(), accumSegment.auditSequence(), ballotSequence,
                                cdb.comparisonAudits()
                            )
                    )
                    LOGGER.info(
                        String
                            .format(
                                "[startRound:" + " county=%s, round=%s, segment.auditSequence()=%s," +
                                        " segment.ballotSequence()=%s, cdb.comparisonAudits=%s,", cdb.county,
                                cdb.currentRound(), accumSegment.auditSequence(), ballotSequence,
                                cdb.comparisonAudits()
                            )
                    )
                    // Risk limit hasn't been achieved. We were given some audits
                    // to work on, but have nothing to do in this round. Please
                    // wait patiently.
                    if (ballotSequence.isEmpty()) {
                        LOGGER.info(
                            String
                                .format(
                                    "[startRound: no ballots to audit in %s County, skipping round]",
                                    cdb.county
                                )
                        )
                        cdb.startRound(0, 0, 0, emptyList(), emptyList()) // fishy
                        Persistence.saveOrUpdate(cdb)
                        PersistentASMQuery.step(
                            ROUND_COMPLETE_EVENT, AuditBoardDashboardASM::class.java,
                            cdb.id.toString()
                        )
                        continue
                    }

                    // Risk limit hasn't been achieved and we finally have something to do in this round!
                    //     fun startRound(
                    //        cdb: CountyDashboard,
                    //        audits: MutableSet<ComparisonAudit>,
                    //        auditSequence: MutableList<Long>,
                    //        ballotSequence: MutableList<Long>
                    ComparisonAuditController.startRound(
                        cdb,
                        cdb.comparisonAudits(),
                        accumSegment.auditSequence(),
                        ballotSequence
                    )
                    Persistence.saveOrUpdate(cdb)

                    LOGGER.info(
                        String.format(
                            "[startRound: Round %d for %s County started normally." +
                                    " Estimated to audit %d ballots.]",
                            cdb.currentRound()?.number, cdb.county.name,
                            cdb.estimatedSamplesToAudit()
                        )
                    )

                    PersistentASMQuery.step(
                        ROUND_START_EVENT, AuditBoardDashboardASM::class.java,
                        cdb.id.toString()
                    )
                    PersistentASMQuery.save(countyDashboardASM)

                    // FIXME hoist me; we don't need to know about HTTP requests or
                    // responses at this level.
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace(System.out)
                    val msg = String.format("could not start round for %s County", cdb.county.name)
                    //serverError(the_response, msg)
                    LOGGER.error(msg)
                } catch (e: IllegalStateException) {
                    LOGGER.error("IllegalStateException " + e)
                    //illegalTransition(the_response, e.message)
                }
            } // end of dashboard twiddling

            // ok(the_response, "round started")
            // end of extraction. Now we can talk about HTTP requests / responses
            // again!
        } catch (e: Exception) {
            LOGGER.error("Exception " + e)
            // serverError(the_response, "could not start round")
        }

        return "status"
        // return my_endpoint_result.get() // this lives in the superclass, and is set in lots of places.
    }

    /**
     *
     * @return true if a county should be started
     */
    fun isReadyToStartAudit(cdb: CountyDashboard): Boolean {
        val countyDashboardASM =
            PersistentASMQuery.asmFor(CountyDashboardASM::class.java, cdb.id.toString())!! as CountyDashboardASM
        return !(countyDashboardASM.isInInitialState() || countyDashboardASM.isInFinalState())
    }

    /**
     * A dashboard is ready to start if it isn't in an initial or final state.
     *
     * @return a list of the dashboards to start.
     */
    fun dashboardsToStart(): List<CountyDashboard> {
        val cdbs: List<CountyDashboard> = Persistence.getAll(CountyDashboard::class.java)
        val result = cdbs.filter { cdb -> isReadyToStartAudit(cdb) }
        LOGGER.debug("[dashboardsToStart: " + result)
        return result
    }

    companion object {
        val LOGGER = KotlinLogging.logger("StartAuditRound")
    }
}

//     public static Segment combineSegments(final Collection<Segment> segments) {
//      return segments.stream()
//        .filter(s -> null != s)
//        .reduce(new Segment(),
//                (acc,s) -> {
//                  // can't ask segment.cvrs for raw data because it is a set
//                  // so we get the cvrIds
//                  acc.addCvrIds(s.cvrIds);
//                  acc.addCvrs(s.cvrs); // TODO how come not tributes?
//                  return acc;});
//    }

fun combineSegments(segments: List<Segment>): Segment {
    val total = Segment()
    return segments.reduce { acc, s ->
        // can't ask segment.cvrs for raw data because it is a set so we get the cvrIds
        total.addCvrIds(s.cvrIds)
        total.addCvrs(s.cvrs)
        total
    }
}
