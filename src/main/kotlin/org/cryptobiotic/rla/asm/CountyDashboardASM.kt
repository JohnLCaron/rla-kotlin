package org.cryptobiotic.rla.asm


import org.cryptobiotic.rla.asm.CountyDashboardEvent.COUNTY_AUDIT_COMPLETE_EVENT
import org.cryptobiotic.rla.asm.CountyDashboardEvent.COUNTY_START_AUDIT_EVENT
import org.cryptobiotic.rla.asm.CountyDashboardEvent.CVR_IMPORT_FAILURE_EVENT
import org.cryptobiotic.rla.asm.CountyDashboardEvent.CVR_IMPORT_SUCCESS_EVENT
import org.cryptobiotic.rla.asm.CountyDashboardEvent.DELETE_BALLOT_MANIFEST_EVENT
import org.cryptobiotic.rla.asm.CountyDashboardEvent.DELETE_CVRS_EVENT
import org.cryptobiotic.rla.asm.CountyDashboardEvent.IMPORT_BALLOT_MANIFEST_EVENT
import org.cryptobiotic.rla.asm.CountyDashboardEvent.IMPORT_CVRS_EVENT
import org.cryptobiotic.rla.asm.CountyDashboardState.BALLOT_MANIFEST_AND_CVRS_OK
import org.cryptobiotic.rla.asm.CountyDashboardState.BALLOT_MANIFEST_OK
import org.cryptobiotic.rla.asm.CountyDashboardState.BALLOT_MANIFEST_OK_AND_CVRS_IMPORTING
import org.cryptobiotic.rla.asm.CountyDashboardState.COUNTY_AUDIT_COMPLETE
import org.cryptobiotic.rla.asm.CountyDashboardState.COUNTY_AUDIT_UNDERWAY
import org.cryptobiotic.rla.asm.CountyDashboardState.COUNTY_INITIAL_STATE
import org.cryptobiotic.rla.asm.CountyDashboardState.CVRS_IMPORTING
import org.cryptobiotic.rla.asm.CountyDashboardState.CVRS_OK
import org.cryptobiotic.rla.asm.CountyDashboardState.DEADLINE_MISSED

class CountyDashboardASM(the_county_id: String) :
    AbstractStateMachine(
        CountyDashboardState.values().toSet(),
        CountyDashboardEvent.values().toSet(),
        transitionsFor(TRANSITIONS),
        CountyDashboardState.COUNTY_INITIAL_STATE,
        FINAL_STATES,
        the_county_id,
) {
    companion object {
        val TRANSITIONS: List<ASMTransitionFunction> = CountyDashboardTransitionFunction.entries.toTypedArray().toList()
        val FINAL_STATES = setOf<ASMState>(
            CountyDashboardState.DEADLINE_MISSED,
            CountyDashboardState.COUNTY_AUDIT_COMPLETE
        )
    }
}

enum class CountyDashboardState : ASMState {
    COUNTY_INITIAL_STATE,
    BALLOT_MANIFEST_OK,
    CVRS_IMPORTING,
    CVRS_OK,
    BALLOT_MANIFEST_OK_AND_CVRS_IMPORTING,
    BALLOT_MANIFEST_AND_CVRS_OK,
    COUNTY_AUDIT_UNDERWAY,
    COUNTY_AUDIT_COMPLETE,
    DEADLINE_MISSED
}

enum class CountyDashboardEvent : ASMEvent {
    IMPORT_BALLOT_MANIFEST_EVENT,  // public inbound event
    IMPORT_CVRS_EVENT,  // public inbound event
    DELETE_BALLOT_MANIFEST_EVENT,  // public inbound event
    DELETE_CVRS_EVENT,  // public inbound event
    CVR_IMPORT_SUCCESS_EVENT,  // private internal event
    CVR_IMPORT_FAILURE_EVENT,  // private internal event
    COUNTY_START_AUDIT_EVENT,  // private internal event
    COUNTY_AUDIT_COMPLETE_EVENT // private internal event
}

/**
 * The County Board Dashboard's transition function.
 */
enum class CountyDashboardTransitionFunction(val transition: ASMTransition) : ASMTransitionFunction {
    A(
        ASMTransition(
            COUNTY_INITIAL_STATE,
            IMPORT_BALLOT_MANIFEST_EVENT,
            BALLOT_MANIFEST_OK
        )
    ),
    B(
        ASMTransition(
            COUNTY_INITIAL_STATE,
            IMPORT_CVRS_EVENT,
            CVRS_IMPORTING
        )
    ),
    C(
        ASMTransition(
            CVRS_IMPORTING,
            CVR_IMPORT_SUCCESS_EVENT,
            CVRS_OK
        )
    ),
    D(
        ASMTransition(
            CVRS_IMPORTING,
            CVR_IMPORT_FAILURE_EVENT,
            COUNTY_INITIAL_STATE
        )
    ),
    E(
        ASMTransition(
            BALLOT_MANIFEST_OK,
            IMPORT_CVRS_EVENT,
            BALLOT_MANIFEST_OK_AND_CVRS_IMPORTING
        )
    ),
    F(
        ASMTransition(
            BALLOT_MANIFEST_OK_AND_CVRS_IMPORTING,
            CVR_IMPORT_SUCCESS_EVENT,
            BALLOT_MANIFEST_AND_CVRS_OK
        )
    ),
    G(
        ASMTransition(
            BALLOT_MANIFEST_OK_AND_CVRS_IMPORTING,
            CVR_IMPORT_FAILURE_EVENT,
            BALLOT_MANIFEST_OK
        )
    ),
    H(
        ASMTransition(
            BALLOT_MANIFEST_OK,
            IMPORT_BALLOT_MANIFEST_EVENT,
            BALLOT_MANIFEST_OK
        )
    ),
    H2(
        ASMTransition(
            BALLOT_MANIFEST_OK,
            DELETE_CVRS_EVENT,
            BALLOT_MANIFEST_OK
        )
    ),
    I(
        ASMTransition(
            CVRS_OK,
            IMPORT_BALLOT_MANIFEST_EVENT,
            BALLOT_MANIFEST_AND_CVRS_OK
        )
    ),
    J(
        ASMTransition(
            CVRS_OK,
            IMPORT_CVRS_EVENT,
            CVRS_IMPORTING
        )
    ),
    K(
        ASMTransition(
            BALLOT_MANIFEST_AND_CVRS_OK,
            IMPORT_BALLOT_MANIFEST_EVENT,
            BALLOT_MANIFEST_AND_CVRS_OK
        )
    ),
    K1(
        ASMTransition(
            BALLOT_MANIFEST_AND_CVRS_OK,
            DELETE_BALLOT_MANIFEST_EVENT,
            CVRS_OK
        )
    ),
    K2(
        ASMTransition(
            BALLOT_MANIFEST_AND_CVRS_OK,
            DELETE_CVRS_EVENT,
            BALLOT_MANIFEST_OK
        )
    ),
    L(
        ASMTransition(
            BALLOT_MANIFEST_AND_CVRS_OK,
            IMPORT_CVRS_EVENT,
            BALLOT_MANIFEST_OK_AND_CVRS_IMPORTING
        )
    ),
    M(
        ASMTransition(
            BALLOT_MANIFEST_AND_CVRS_OK,
            COUNTY_START_AUDIT_EVENT,
            COUNTY_AUDIT_UNDERWAY
        )
    ),
    N(
        ASMTransition(
            COUNTY_AUDIT_UNDERWAY,
            COUNTY_AUDIT_COMPLETE_EVENT,
            COUNTY_AUDIT_COMPLETE
        )
    ),
    O(
        ASMTransition(
            setOf(
                COUNTY_INITIAL_STATE,
                BALLOT_MANIFEST_OK,
                CVRS_OK,
                CVRS_IMPORTING,
                BALLOT_MANIFEST_OK_AND_CVRS_IMPORTING
            ),
            COUNTY_START_AUDIT_EVENT,
            DEADLINE_MISSED
        )
    );

    override fun value(): ASMTransition {
        return transition
    }
}
