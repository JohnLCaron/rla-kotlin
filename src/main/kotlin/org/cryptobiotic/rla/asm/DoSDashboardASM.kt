package org.cryptobiotic.rla.asm

import org.cryptobiotic.rla.asm.DoSDashboardEvent.*
import org.cryptobiotic.rla.asm.DoSDashboardState.*

object DoSDashboardASM : AbstractStateMachine(
    DoSDashboardState.values().toSet(),
    DoSDashboardEvent.values().toSet(),
    transitionsFor(DoSDashboardTransitionFunction.entries.toTypedArray().toList()),
    DoSDashboardState.DOS_INITIAL_STATE,
    setOf<ASMState>(DoSDashboardState.AUDIT_RESULTS_PUBLISHED),
    "DoS",
)

/**
 * The Department of State Dashboard's states.
 * @trace asm.department_of_state_dashboard_state
 */
enum class DoSDashboardState : ASMState {
    DOS_INITIAL_STATE,
    PARTIAL_AUDIT_INFO_SET,
    COMPLETE_AUDIT_INFO_SET,
    RANDOM_SEED_PUBLISHED,
    DOS_AUDIT_ONGOING,
    DOS_ROUND_COMPLETE,
    DOS_AUDIT_COMPLETE,
    AUDIT_RESULTS_PUBLISHED
}

/**
 * The Department of State Dashboard's events.
 * @trace asm.department_of_state_dashboard_event
 */
enum class DoSDashboardEvent : ASMEvent {
    PARTIAL_AUDIT_INFO_EVENT,  // public inbound event
    COMPLETE_AUDIT_INFO_EVENT,  // public inbound event
    DOS_START_ROUND_EVENT,  // public inbound event
    DOS_ROUND_COMPLETE_EVENT,  // private internal event
    AUDIT_EVENT,  // private internal event
    DOS_COUNTY_AUDIT_COMPLETE_EVENT,  // private internal event
    DOS_AUDIT_COMPLETE_EVENT,  // private internal event
    PUBLISH_AUDIT_REPORT_EVENT // public inbound event
}

/**
 * The Department of State Dashboard's transition function.
 * @trace asm.dos_dashboard_next_state
 */
enum class DoSDashboardTransitionFunction(val transition: ASMTransition) : ASMTransitionFunction {
    A(
        ASMTransition(
            setOf(DOS_INITIAL_STATE, PARTIAL_AUDIT_INFO_SET, COMPLETE_AUDIT_INFO_SET),
            PARTIAL_AUDIT_INFO_EVENT,
            PARTIAL_AUDIT_INFO_SET
        )
    ),
    B(
        ASMTransition(
            setOf(
                DOS_INITIAL_STATE,
                PARTIAL_AUDIT_INFO_SET,
                COMPLETE_AUDIT_INFO_SET
            ),
            COMPLETE_AUDIT_INFO_EVENT,
            COMPLETE_AUDIT_INFO_SET
        )
    ),
    D(
        ASMTransition(
            COMPLETE_AUDIT_INFO_SET,
            DOS_START_ROUND_EVENT,
            DOS_AUDIT_ONGOING
        )
    ),
    E(
        ASMTransition(
            DOS_AUDIT_ONGOING,
            setOf(
                AUDIT_EVENT,
                DOS_COUNTY_AUDIT_COMPLETE_EVENT,
                DOS_START_ROUND_EVENT
            ),
            DOS_AUDIT_ONGOING
        )
    ),
    F(
        ASMTransition(
            DOS_AUDIT_ONGOING,
            DOS_ROUND_COMPLETE_EVENT,
            DOS_ROUND_COMPLETE
        )
    ),
    G(
        ASMTransition(
            DOS_ROUND_COMPLETE,
            DOS_START_ROUND_EVENT,
            DOS_AUDIT_ONGOING
        )
    ),
    H(
        ASMTransition(
            setOf(
                DOS_AUDIT_ONGOING,
                DOS_ROUND_COMPLETE
            ),
            DOS_AUDIT_COMPLETE_EVENT,
            DOS_AUDIT_COMPLETE
        )
    ),
    I(
        ASMTransition(
            DOS_AUDIT_COMPLETE,
            PUBLISH_AUDIT_REPORT_EVENT,
            AUDIT_RESULTS_PUBLISHED
        )
    );

    override fun value(): ASMTransition {
        return transition
    }
}