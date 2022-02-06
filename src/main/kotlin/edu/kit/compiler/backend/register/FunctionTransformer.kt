package edu.kit.compiler.backend.register

import edu.kit.compiler.backend.molkir.Instruction as MolkiInstruction

// This interface is intended for a stateful implementation, which is why the ``transformCode`` function and the
// `getPlatformCode` function are not combined into a singular function

/**
 * Strategy pattern for transforming MolkIR into target platform representation.
 *
 * @see PlatformInstruction
 */
interface FunctionTransformer {

    /**
     * Transform a given block of code into [PlatformIR] by allocating registers and generating necessary platform-specific
     * code. This function can be called multiple times. It will just append the generated code to code that was generated
     * during previous function calls.
     */
    fun transformCode(codeBlock: List<MolkiInstruction>)

    /**
     * Get the transformed code.
     */
    fun getPlatformCode(): List<PlatformInstruction>
}
