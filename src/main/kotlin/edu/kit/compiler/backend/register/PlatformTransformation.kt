package edu.kit.compiler.backend.register

import edu.kit.compiler.backend.register.calls.CallingConvention
import edu.kit.compiler.backend.register.calls.SimpleCallingConvention
import edu.kit.compiler.backend.register.calls.X64ABICallingConvention
import edu.kit.compiler.backend.register.trivial.TrivialFunctionTransformer
import edu.kit.compiler.backend.molkir.Instruction as MolkiInstruction

/**
 * Transform MolkIR intermediate code into x86 platform code. This allocates platform registers for the virtual
 * registers used in MolkIR code, and adds platform-specific code like function prologues and epilogues.
 */
object PlatformTransformation {

    /**
     * Allocation strategy. Initialize with different instance to change strategy
     */
    private val allocatorFactory: (CallingConvention, FunctionSignature) -> FunctionTransformer =
        ::TrivialFunctionTransformer

    /**
     * Allocate registers for a function
     *
     * @param function instruction list of the function
     * @param signature the function signature
     * @param callingConvention the calling convention this function must adhere to when called
     */
    fun transformFunction(
        function: List<MolkiInstruction>,
        signature: FunctionSignature,
        callingConvention: CallingConvention
    ): List<PlatformInstruction> {
        val allocationStrategy = allocatorFactory.invoke(callingConvention, signature)
        allocationStrategy.transformCode(function)
        return allocationStrategy.getPlatformCode()
    }

    /**
     * @return calling convention used internally for functions that are not visible outside the module
     */
    fun getInternalCallingConvention(): CallingConvention {
        return SimpleCallingConvention
    }

    /**
     * @return x64 ABI calling convention implementation
     */
    fun getExternalCallingConvention(): CallingConvention {
        return X64ABICallingConvention
    }
}
