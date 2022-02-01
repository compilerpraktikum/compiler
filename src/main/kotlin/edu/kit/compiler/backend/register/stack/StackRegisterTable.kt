package edu.kit.compiler.backend.register.stack

import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.backend.register.EnumRegister
import edu.kit.compiler.backend.register.PlatformTarget

/**
 * A register table at the beginning of a function's stack frame
 */
class StackRegisterTable {
    /**
     * All virtual registers are saved in a table on the stack
     */
    data class StackRegister(val virtualRegisterId: RegisterId, val offset: Int, val width: Width) {
        fun generateMemoryAddress(): PlatformTarget.Memory =
            PlatformTarget.Memory.of((-offset).toString(), PlatformTarget.Register(EnumRegister.RBP), null, null)
    }

    /**
     * Stores the current offset where a new [StackRegister] is placed on the stack. It is initialized at `8`, to skip the
     * pushed RBP value, which is located at `0(%rbp)`
     */
    var registerTableSize = 8
        private set

    /**
     * Current stack layout of virtual registers
     */
    private val stackLayout = mutableMapOf<RegisterId, StackRegister>()

    /**
     * Whether the register table contains a slot for the given virtual register
     */
    fun containsRegister(id: RegisterId): Boolean {
        return stackLayout.containsKey(id)
    }

    /**
     * Get the [StackRegister] corresponding to the given virtual register
     */
    fun getRegisterSlot(id: RegisterId): StackRegister? {
        return stackLayout[id]
    }

    /**
     * Create a register slot in the table
     *
     * @param id virtual register id corresponding to this slot
     * @param width register width
     */
    fun createRegisterSlot(id: RegisterId, width: Width): StackRegister {
        val register = StackRegister(id, registerTableSize, width)
        stackLayout[id] = register
        registerTableSize += Width.QUAD.inBytes

        return register
    }
}
