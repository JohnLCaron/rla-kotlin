package org.cryptobiotic.rla.asm

import io.github.oshai.kotlinlogging.KotlinLogging
import org.cryptobiotic.rla.persistence.PersistentASMQuery
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException


/**
 * Utility classes that are generally useful for working with ASMs.
 *
 * @author Daniel M. Zimmerman <dmz></dmz>@freeandfair.us>
 * @version 1.0.0
 *
object ASMUtilities {
    val LOGGER = KotlinLogging.logger("ASMUtilities")

    /**
     * Gets the ASM for the specified ASM class and identity, initialized to its
     * state on the database.
     *
     * @param the_class The class.
     * @param the_identity The identity.
     * @return the ASM, or null if the ASM cannot be instantiated.
     */
    fun <T : AbstractStateMachine?> asmFor(
        the_class: Class<T>,
        the_identity: String
    ): T? {
        var result: T? = null

        try {
            // first, check for a no-argument constructor
            val constructors: Array<Constructor<*>?> = the_class.getConstructors()
            for (c in constructors) {
                val constructor: Constructor<T?> = c as Constructor<T?>
                if (constructor.getParameterTypes().size === 0) {
                    // default constructor
                    result = constructor.newInstance()
                    break
                } else if (constructor.getParameterTypes().size === 1 &&
                    constructor.getParameterTypes()[0].equals(String::class.java)
                ) {
                    // 1-argument constructor that takes a String
                    result = constructor.newInstance(the_identity)
                    break
                }
            }
        } catch (e: IllegalAccessException) {
            LOGGER.error(
                "Unable to construct ASM of class " + the_class +
                        " with identity " + the_identity
            )
        } catch (e: InstantiationException) {
            LOGGER.error(
                "Unable to construct ASM of class " + the_class +
                        " with identity " + the_identity
            )
        } catch (e: InvocationTargetException) {
            LOGGER.error(
                "Unable to construct ASM of class " + the_class +
                        " with identity " + the_identity
            )
        }

        val asm: AbstractStateMachine? = PersistentASMQuery.asmFor(the_class, the_identity)

        if (asm == null) {
            LOGGER.error(
                "Unable to retrieve ASM state for class " + the_class +
                        " with identity " + the_identity
            )
        } else if (result != null) {
            return asm
        }

        return null
    }

    /**
     * Saves the state of the specified ASM to the database.
     *
     * @param the_asm The ASM.
     * @return true if the save was successful, false otherwise
     */
    fun save(the_asm: AbstractStateMachine): Boolean {
        PersistentASMQuery.save(the_asm)
        return true
       /* var result = false

        val asm_state: PersistentASMState? =
            PersistentASMStateQueries.get(the_asm.javaClass, the_asm.identity())

        if (asm_state == null) {
            LOGGER.error("Unable to retrieve ASM state for " + the_asm)
        } else {
            asm_state.updateFrom(the_asm)
            try {
                Persistence.saveOrUpdate(asm_state)
                result = true
            } catch (e: PersistenceException) {
                LOGGER.error("Could not save state for ASM " + the_asm)
            }
        }

        return result */
    }

    /**
     * Attempts to step with the specified event on the ASM of the specified
     * class and identity, and persist the resulting state.
     *
     * @param the_event The event.
     * @param the_asm_class The class.
     * @param the_asm_identity The identity.
     * @return true if the state transition succeeds, false if the state machine
     * could not be loaded or the resulting state could not be persisted.
     * @exception IllegalStateException if the state transition is illegal.
     *
    fun step(
        the_event: ASMEvent,
        the_asm_class: Class<out AbstractStateMachine?>,
        the_asm_identity: String?
    ): Boolean {
        var result = false
        val asm: AbstractStateMachine? = ASMUtilities.asmFor(the_asm_class, the_asm_identity)

        if (asm != null) {
            asm.stepEvent(the_event)
            result = save(asm)
        }

        return result
    }
    */
}
*/
