package edu.kit.compiler.backend.register

import edu.kit.compiler.backend.molkir.Instruction as MolkirInstruction
import edu.kit.compiler.backend.register.Instruction as PlatformInstruction

/**
 * Strategy pattern for register allocation
 */
interface PlatformTransformer {

    /**
     * Transform a given block of code into [PlatformIR] by allocating registers.
     * TODO: somehow allow implementation-dependent handling of either whole functions or just code blocks opaque to the
     *  caller
     */
    fun transformCode(codeBlock: List<MolkirInstruction>): List<PlatformInstruction>
}
