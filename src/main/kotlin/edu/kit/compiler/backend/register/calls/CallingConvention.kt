package edu.kit.compiler.backend.register.calls

import edu.kit.compiler.backend.register.PlatformInstruction
import edu.kit.compiler.backend.register.PlatformTarget

/**
 * Implement the necessary behavior for different calling conventions (like mapping arguments to [PlatformTarget]s,
 * saving and restoring registers, etc.)
 */
interface CallingConvention {

    // TODO the calling convention must somehow know which registers are being used by the function

    /**
     * Generate the function prologue for a function called by this convention
     */
    fun generateFunctionPrologue(reservedSpace: Int): List<PlatformInstruction>

    /**
     * Generate the function epilogue for a function called by this convention
     */
    fun generateFunctionEpilogue(): List<PlatformInstruction>

    /**
     * Mark a register as used by the function, so it can be saved if required by the calling convention
     *
     * @param register that is being used by the function
     */
    fun taintRegister(register: PlatformTarget.Register)
}
