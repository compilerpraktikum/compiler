package edu.kit.compiler.backend.register

import edu.kit.compiler.backend.molkir.Instruction

/**
 * Strategy pattern for register allocation
 */
interface AllocationStrategy {

    /**
     * Allocate registers for the given independent block of code.
     * TODO: somehow allow implementation-dependent handling of either whole functions or just code blocks opaque to the
     *  caller
     */
    fun allocateRegisters(codeBlock: List<Instruction>)
}
