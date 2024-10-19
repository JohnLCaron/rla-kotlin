package org.cryptobiotic.rla.asm

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.Serializable
import java.lang.IllegalStateException
import java.util.HashSet

interface ASMState {}

interface ASMEvent {}

interface ASMTransitionFunction {
    fun value(): ASMTransition
}

/**
 * A single transition of an abstract state machine.
 *
 * @author Daniel M. Zimmerman <dmz></dmz>@freeandfair.us>
 * @version 1.0.0
 */
data class ASMTransition(
    val startStates: Set<ASMState>,
    val events: Set<ASMEvent>,
    val endState: ASMState,
) {

    constructor(the_start_state: ASMState, the_event: ASMEvent, the_end_state: ASMState) :
            this(setOf(the_start_state), setOf(the_event), the_end_state)

    constructor(the_start_states: Set<ASMState>, the_event: ASMEvent, the_end_state: ASMState) :
            this(the_start_states, setOf(the_event), the_end_state)

    constructor(the_start_state: ASMState, the_events: Set<ASMEvent>, the_end_state: ASMState) :
            this(setOf(the_start_state), the_events, the_end_state)

    /**
     * @return the start state.
     */
    fun startStates(): Set<ASMState> {
        return startStates
    }

    /**
     * @return the events.
     */
    fun events(): Set<ASMEvent> {
        return events
    }

    /**
     * @return the end state.
     */
    fun endState(): ASMState {
        return endState
    }

    /**
     * @return a String representation of this ASMTransition
     */
    override fun toString(): String {
        return "ASMTransition [start=" + startStates +
                ", events=" + events + ", end=" +
                endState + "]"
    }
}


/**
 * Constructs an ASM. This constructor takes ownership of all the
 * Collections passed to it.
 *
 * @param states the states of the new ASM.
 * @param events the events of the new ASM.
 * @param transitionFunction the transition function of the new ASM.
 *  This function, represented as a set of ASMTransitionFunction elements, need only specify legal transitions;
 *  all unspecified transitions are considered illegal.
 * @param initialState The initial state of the new ASM.
 * @param finalStates The final states of the new ASM.
 * @param identity The identity of the new ASM.
 */
abstract class AbstractStateMachine(
    val states: Set<ASMState>,
    val events: Set<ASMEvent>,
    val transitionFunction: Set<ASMTransition>,
    val initialState: ASMState,
    val finalStates: Set<ASMState>,
    val identity: String,
) {
    private var currentState: ASMState = initialState
    private var my_identity: String = identity

    init {
        currentState = initialState
    }

    /**
     * @return are we in the initial state
     */
    fun isInInitialState(): Boolean {
        return currentState == initialState
    }

    /**
     * @return are we in a final state
     */
    fun isInFinalState(): Boolean {
        return finalStates.contains(currentState)
    }

    /**
     * @return the current state of this ASM.
     * @trace asm.current_state
     */
    fun currentState(): ASMState {
        return currentState
    }

    /**
     * Sets the current state. This method ignores any constraints
     * imposed by the current state, and should only be used as part of
     * reconstructing an ASM at a particular state.
     *
     * @param the_state The new state.
     */
    protected fun setCurrentState(the_state: ASMState) {
        currentState = the_state
    }

    /** This sets the state machine back to the beginning. It should be used
     * carefully. It is a shortcut.  */
    fun reinitialize() {
        currentState = initialState
    }

    /**
     * @return the ASM's identity, or null if this ASM is a singleton.
     */
    fun identity(): String {
        return identity
    }

    /**
     * Sets the ASM's identity. This method should only be used as part
     * of reconstructing an ASM at a particular state.
     *
     * @param the_identity The new identity.
     */
    protected fun setIdentity(the_identity: String) {
        my_identity = the_identity
    }

    /**
     * @return the transitions of this ASM that are enabled. I.e., which
     * states are reachable from the current state, given any possible event
     */
    fun enabledASMEvents(): Set<ASMEvent> {
        val result = mutableSetOf<ASMEvent>()
        for (t in transitionFunction) {
            if (t.startStates().contains(currentState)) {
                result.addAll(t.events())
            }
        }
        return result
    }

    /**
     * Transition to the next state of this ASM given the provided
     * transition and its current state.
     * @param the_transition the transition that is triggered.
     * @return the new current state of the ASM after the transition.
     * @throws IllegalStateException if this ASM cannot take a step
     * given the provided transition.
     */
    @Throws(IllegalStateException::class)
    fun stepTransition(the_transition: ASMTransition): ASMState {
        // If we are in the right state then transition to the new state.
        if (the_transition.startStates().contains(currentState)) {
            currentState = the_transition.endState()
            LOGGER.debug(
                "ASM transition " + the_transition + " succeeded from state " +
                        currentState + " for " + javaClass.getSimpleName() + "/" +
                        my_identity
            )
        } else {
            LOGGER.error(
                "ASM transition " + the_transition +
                        " failed from state " + currentState
            )
            throw IllegalStateException(
                "Attempted to transition ASM " +
                        javaClass.getName() + "/" + my_identity +
                        " from " + currentState +
                        " using transition " +
                        the_transition
            )
        }
        return currentState
    }

    @Throws(IllegalStateException::class)
    fun checkEvent(the_event: ASMEvent): Boolean {
        var result: ASMState? = null
        for (t in transitionFunction) {
            if (t.startStates().contains(currentState) &&
                t.events().contains(the_event)
            ) {
                result = t.endState()
                break
            }
        }
        if (result == null) {
            return false
        }
        return true
    }

    /**
     * Transition to the next state of this ASM given the provided event
     * and its current state.
     * @return the next state given the specified event and input.
     * @throws IllegalStateException is this ASM cannot transition given
     * the provided event.
     */
    @Throws(IllegalStateException::class)
    fun stepEvent(the_event: ASMEvent): ASMState {
        var result: ASMState? = null
        for (t in transitionFunction) {
            if (t.startStates().contains(currentState) &&
                t.events().contains(the_event)
            ) {
                result = t.endState()
                break
            }
        }
        if (result == null) {
            LOGGER.error(
                "ASM event " + the_event +
                        " failed from state " + currentState
            )
            throw IllegalStateException(
                "Illegal transition on ASM " +
                        javaClass.getSimpleName() + "/" + my_identity +
                        ": (" + currentState + ", " +
                        the_event + ")"
            )
        } else {
            currentState = result
            LOGGER.debug(
                "ASM event " + the_event + " caused transition to " +
                        currentState + " for " + javaClass.getSimpleName() +
                        "/" + my_identity
            )
            return result
        }
    }

    /**
     * @return a String representation of this ASM.
     */
    override fun toString(): String {
        return javaClass.getSimpleName() + ", identity=" + my_identity +
                ", current_state=" + currentState
    }

    companion object {
        val LOGGER = KotlinLogging.logger("AbstractStateMachine")

        /**
         * Converts a list of ASMTransitionFunctions to a set of
         * ASMTransitions.
         *
         * @param the set of ASMTransitionFunctions.
         * @return the set of ASMTransitions for the specified list of
         * ASMTransitionFunctions.
         */
        fun transitionsFor(the_list: List<ASMTransitionFunction>): Set<ASMTransition> {
            return the_list.map { it.value() }.toSet()
        }
    }
}

