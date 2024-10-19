package org.cryptobiotic.rla.persistence

import org.cryptobiotic.rla.asm.ASMEvent
import org.cryptobiotic.rla.asm.AbstractStateMachine
import kotlin.collections.set

object PersistentASMQuery {
    val db = mutableMapOf<String, AbstractStateMachine>()

    fun <T : AbstractStateMachine?> asmFor(the_class: Class<T>, the_identity: String): AbstractStateMachine? {
        val key = the_class.name + "#" + the_identity
        return db[key]
    }

    fun save(asm: AbstractStateMachine): Boolean {
        val key = asm.javaClass.name + "#" + asm.identity
        db[key] = asm
        return true
    }

    fun step(
        the_event: ASMEvent,
        the_asm_class: Class<out AbstractStateMachine?>,
        the_asm_identity: String
    ): Boolean {
        var result = false
        val asm: AbstractStateMachine? = asmFor(the_asm_class, the_asm_identity)

        if (asm != null) {
            asm.stepEvent(the_event)
            result = save(asm)
        }

        return result
    }
}