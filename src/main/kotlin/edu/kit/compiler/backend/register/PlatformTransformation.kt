package edu.kit.compiler.backend.register

import edu.kit.compiler.backend.register.calls.CallingConvention
import edu.kit.compiler.backend.register.calls.SimpleCallingConvention
import edu.kit.compiler.backend.register.calls.X64ABICallingConvention
import edu.kit.compiler.backend.register.trivial.TrivialFunctionTransformer

/**
 * Transform MolkIR intermediate code into x86 platform code. This allocates platform registers for the virtual
 * registers used in MolkIR code, and adds platform-specific code like function prologues and epilogues.
 */
object PlatformTransformation {

    /**
     * Allocation strategy. Initialize with different instance to change strategy
     */
    private val allocatorFactory: (CallingConvention, Int) -> FunctionTransformer =
        ::TrivialFunctionTransformer

    /**
     * Allocate registers for a function
     *
     * @param function instruction list of the function
     * @param parameters number of parameters
     * @param callingConvention the calling convention this function must adhere to when called
     */
    fun transformFunction(
        function: List<List<edu.kit.compiler.backend.molkir.Instruction>>,
        parameters: Int,
        callingConvention: CallingConvention
    ): List<PlatformInstruction> {
        val allocationStrategy = allocatorFactory.invoke(callingConvention, parameters)
        allocationStrategy.transformCode(function.flatten())
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
