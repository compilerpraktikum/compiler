package edu.kit.compiler.backend.register.calls

import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.backend.register.PlatformInstruction
import edu.kit.compiler.backend.register.PlatformTarget

/**
 * Implement the necessary behavior for different calling conventions (like mapping arguments to [PlatformTarget]s,
 * saving and restoring registers, etc.)
 */
interface CallingConvention {

    // TODO the calling convention must somehow know which registers are being used by the function

    /**
     * Generate the function prologue for a function called by this convention+
     *
     * @param reservedSpace how much space the function should reserve in the stack frame statically
     */
    fun generateFunctionPrologue(reservedSpace: Int): List<PlatformInstruction>

    /**
     * Generate the function epilogue for a function called by this convention
     *
     * @param returnValue location of the return value, if any. `null` otherwise
     * @param returnWidth width of the return value. `null` iff [returnValue] is `null` too.
     */
    fun generateFunctionEpilogue(returnValue: PlatformTarget?, returnWidth: Width?): List<PlatformInstruction>

    /**
     * Mark a register as used by the function, so it can be saved if required by the calling convention
     *
     * @param register that is being used by the function
     */
    fun taintRegister(register: PlatformTarget.Register)
}
