package org.cryptobiotic.rla.asm

import org.cryptobiotic.rla.asm.AuditBoardDashboardEvent.ABORT_AUDIT_EVENT
import org.cryptobiotic.rla.asm.AuditBoardDashboardEvent.BALLOTS_EXHAUSTED_EVENT
import org.cryptobiotic.rla.asm.AuditBoardDashboardEvent.COUNTY_DEADLINE_MISSED_EVENT
import org.cryptobiotic.rla.asm.AuditBoardDashboardEvent.NO_CONTESTS_TO_AUDIT_EVENT
import org.cryptobiotic.rla.asm.AuditBoardDashboardEvent.REPORT_BALLOT_NOT_FOUND_EVENT
import org.cryptobiotic.rla.asm.AuditBoardDashboardEvent.REPORT_MARKINGS_EVENT
import org.cryptobiotic.rla.asm.AuditBoardDashboardEvent.RISK_LIMIT_ACHIEVED_EVENT
import org.cryptobiotic.rla.asm.AuditBoardDashboardEvent.ROUND_COMPLETE_EVENT
import org.cryptobiotic.rla.asm.AuditBoardDashboardEvent.ROUND_SIGN_OFF_EVENT
import org.cryptobiotic.rla.asm.AuditBoardDashboardEvent.ROUND_START_EVENT
import org.cryptobiotic.rla.asm.AuditBoardDashboardEvent.SIGN_IN_AUDIT_BOARD_EVENT
import org.cryptobiotic.rla.asm.AuditBoardDashboardEvent.SIGN_OUT_AUDIT_BOARD_EVENT
import org.cryptobiotic.rla.asm.AuditBoardDashboardEvent.SUBMIT_AUDIT_INVESTIGATION_REPORT_EVENT
import org.cryptobiotic.rla.asm.AuditBoardDashboardState.AUDIT_ABORTED
import org.cryptobiotic.rla.asm.AuditBoardDashboardState.AUDIT_COMPLETE
import org.cryptobiotic.rla.asm.AuditBoardDashboardState.AUDIT_INITIAL_STATE
import org.cryptobiotic.rla.asm.AuditBoardDashboardState.ROUND_IN_PROGRESS
import org.cryptobiotic.rla.asm.AuditBoardDashboardState.ROUND_IN_PROGRESS_NO_AUDIT_BOARD
import org.cryptobiotic.rla.asm.AuditBoardDashboardState.UNABLE_TO_AUDIT
import org.cryptobiotic.rla.asm.AuditBoardDashboardState.WAITING_FOR_ROUND_SIGN_OFF
import org.cryptobiotic.rla.asm.AuditBoardDashboardState.WAITING_FOR_ROUND_SIGN_OFF_NO_AUDIT_BOARD
import org.cryptobiotic.rla.asm.AuditBoardDashboardState.WAITING_FOR_ROUND_START
import org.cryptobiotic.rla.asm.AuditBoardDashboardState.WAITING_FOR_ROUND_START_NO_AUDIT_BOARD

class AuditBoardDashboardASM(the_county_id: String) : AbstractStateMachine(
    AuditBoardDashboardState.values().toSet(),
    AuditBoardDashboardEvent.values().toSet(),
    transitionsFor(TRANSITIONS),
    AuditBoardDashboardState.AUDIT_INITIAL_STATE,
    FINAL_STATES,
    the_county_id,
) {
    companion object {
        val TRANSITIONS: List<ASMTransitionFunction> = AuditBoardDashboardTransitionFunction.entries.toTypedArray().toList()
        private val FINAL_STATES = setOf<ASMState>(
            AuditBoardDashboardState.AUDIT_COMPLETE,
            AuditBoardDashboardState.UNABLE_TO_AUDIT,
            AuditBoardDashboardState.AUDIT_ABORTED
        )
    }
}

/**
 * The Audit Board Dashboard's states.
 * @trace asm.audit_board_dashboard_state
 */
enum class AuditBoardDashboardState : ASMState {
    AUDIT_INITIAL_STATE,
    WAITING_FOR_ROUND_START,
    WAITING_FOR_ROUND_START_NO_AUDIT_BOARD,
    ROUND_IN_PROGRESS,
    ROUND_IN_PROGRESS_NO_AUDIT_BOARD,
    WAITING_FOR_ROUND_SIGN_OFF,
    WAITING_FOR_ROUND_SIGN_OFF_NO_AUDIT_BOARD,
    AUDIT_COMPLETE,
    UNABLE_TO_AUDIT,
    AUDIT_ABORTED
}

/**
 * The Audit Board Dashboard's events.
 * @trace asm.audit_board_dashboard_event
 */
enum class AuditBoardDashboardEvent : ASMEvent {
    COUNTY_DEADLINE_MISSED_EVENT,  // private internal event
    NO_CONTESTS_TO_AUDIT_EVENT,  // private internal event
    REPORT_MARKINGS_EVENT,  // public inbound event
    REPORT_BALLOT_NOT_FOUND_EVENT,  // public inbound event
    SUBMIT_AUDIT_INVESTIGATION_REPORT_EVENT,  // public inbound event
    SUBMIT_INTERMEDIATE_AUDIT_REPORT_EVENT,  // public inbound event
    SIGN_OUT_AUDIT_BOARD_EVENT,  // public inbound event
    SIGN_IN_AUDIT_BOARD_EVENT,  // public inbound event
    ROUND_START_EVENT,  // private internal event
    ROUND_COMPLETE_EVENT,  // private internal event
    ROUND_SIGN_OFF_EVENT,  // public inbound event
    RISK_LIMIT_ACHIEVED_EVENT,  // private internal event
    ABORT_AUDIT_EVENT,  // public inbound event
    BALLOTS_EXHAUSTED_EVENT // private internal event
}

/**
 * The Audit Board Dashboard's transition function.
 * @trace asm.audit_board_dashboard_next_state
 */
enum class AuditBoardDashboardTransitionFunction(val transition: ASMTransition) : ASMTransitionFunction {
    A(
        ASMTransition(
            setOf(
                AUDIT_INITIAL_STATE,
                WAITING_FOR_ROUND_START_NO_AUDIT_BOARD
            ),
            ROUND_START_EVENT,
            ROUND_IN_PROGRESS_NO_AUDIT_BOARD
        )
    ),
    B(
        ASMTransition(
            setOf(
                AUDIT_INITIAL_STATE,
                WAITING_FOR_ROUND_START_NO_AUDIT_BOARD
            ),
            SIGN_IN_AUDIT_BOARD_EVENT,
            WAITING_FOR_ROUND_START
        )
    ),
    C(
        ASMTransition(
            AUDIT_INITIAL_STATE,
            setOf(
                NO_CONTESTS_TO_AUDIT_EVENT,
                RISK_LIMIT_ACHIEVED_EVENT
            ),
            AUDIT_COMPLETE
        )
    ),
    D(
        ASMTransition(
            AUDIT_INITIAL_STATE,
            COUNTY_DEADLINE_MISSED_EVENT,
            UNABLE_TO_AUDIT
        )
    ),
    F(
        ASMTransition(
            WAITING_FOR_ROUND_START,
            ROUND_START_EVENT,
            ROUND_IN_PROGRESS
        )
    ),
    G(
        ASMTransition(
            WAITING_FOR_ROUND_START,
            SIGN_OUT_AUDIT_BOARD_EVENT,
            WAITING_FOR_ROUND_START_NO_AUDIT_BOARD
        )
    ),
    H(
        ASMTransition(
            WAITING_FOR_ROUND_START,
            RISK_LIMIT_ACHIEVED_EVENT,
            AUDIT_COMPLETE
        )
    ),
    M(
        ASMTransition(
            ROUND_IN_PROGRESS,
            setOf(
                REPORT_MARKINGS_EVENT,
                REPORT_BALLOT_NOT_FOUND_EVENT,
                SUBMIT_AUDIT_INVESTIGATION_REPORT_EVENT
            ),
            ROUND_IN_PROGRESS
        )
    ),
    N(
        ASMTransition(
            ROUND_IN_PROGRESS,
            SIGN_OUT_AUDIT_BOARD_EVENT,
            ROUND_IN_PROGRESS_NO_AUDIT_BOARD
        )
    ),
    O(
        ASMTransition(
            ROUND_IN_PROGRESS,
            ROUND_COMPLETE_EVENT,
            WAITING_FOR_ROUND_SIGN_OFF
        )
    ),

    // this can happen if there are no ballots to audit in the first round
    O1(
        ASMTransition(
            AUDIT_INITIAL_STATE,
            ROUND_COMPLETE_EVENT,
            WAITING_FOR_ROUND_SIGN_OFF
        )
    ),

    // this can happen if there are no ballots to audit in subsequent rounds
    O2(
        ASMTransition(
            WAITING_FOR_ROUND_START,
            ROUND_COMPLETE_EVENT,
            WAITING_FOR_ROUND_SIGN_OFF
        )
    ),

    // this can happen if there are no ballots for an audit board
    O3(
        ASMTransition(
            WAITING_FOR_ROUND_START,
            ROUND_SIGN_OFF_EVENT,
            WAITING_FOR_ROUND_START
        )
    ),

    /* We probably want this transition eventually, but not for CDOS
    EARLY(new ASMTransition(ROUND_IN_PROGRESS,
                            RISK_LIMIT_ACHIEVED_EVENT,
                            AUDIT_COMPLETE)), */
    P(
        ASMTransition(
            ROUND_IN_PROGRESS_NO_AUDIT_BOARD,
            SIGN_IN_AUDIT_BOARD_EVENT,
            ROUND_IN_PROGRESS
        )
    ),
    Q(
        ASMTransition(
            WAITING_FOR_ROUND_SIGN_OFF,
            SIGN_OUT_AUDIT_BOARD_EVENT,
            WAITING_FOR_ROUND_SIGN_OFF_NO_AUDIT_BOARD
        )
    ),
    R(
        ASMTransition(
            WAITING_FOR_ROUND_SIGN_OFF,
            ROUND_SIGN_OFF_EVENT,
            WAITING_FOR_ROUND_START
        )
    ),
    S(
        ASMTransition(
            WAITING_FOR_ROUND_SIGN_OFF,
            setOf(
                RISK_LIMIT_ACHIEVED_EVENT,
                BALLOTS_EXHAUSTED_EVENT
            ),
            AUDIT_COMPLETE
        )
    ),
    T(
        ASMTransition(
            WAITING_FOR_ROUND_SIGN_OFF_NO_AUDIT_BOARD,
            SIGN_IN_AUDIT_BOARD_EVENT,
            WAITING_FOR_ROUND_SIGN_OFF
        )
    ),
    U(
        ASMTransition(
            setOf(
                AUDIT_INITIAL_STATE,
                WAITING_FOR_ROUND_START,
                WAITING_FOR_ROUND_START_NO_AUDIT_BOARD,
                ROUND_IN_PROGRESS,
                ROUND_IN_PROGRESS_NO_AUDIT_BOARD,
                WAITING_FOR_ROUND_SIGN_OFF,
                WAITING_FOR_ROUND_SIGN_OFF_NO_AUDIT_BOARD
            ),
            ABORT_AUDIT_EVENT,
            AUDIT_ABORTED
        )
    );

    override fun value(): ASMTransition {
        return transition
    }
}

