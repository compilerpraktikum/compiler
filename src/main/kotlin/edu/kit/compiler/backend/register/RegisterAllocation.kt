package edu.kit.compiler.backend.register

import edu.kit.compiler.backend.register.calls.CallingConvention
import edu.kit.compiler.backend.register.calls.SimpleCallingConvention
import edu.kit.compiler.backend.register.trivial.TrivialFunctionTransformer
import edu.kit.compiler.backend.molkir.Instruction as MolkiInstruction

object RegisterAllocation {

    /**
     * Allocation strategy. Initialize with different instance to change strategy
     */
    private val allocatorFactory: (CallingConvention) -> FunctionTransformer = ::TrivialFunctionTransformer

    /**
     * Allocate registers for a program (made up of a list of functions)
     */
    fun allocate(program: List<List<MolkiInstruction>>): List<List<PlatformInstruction>> {
        val transformedFunctions = mutableListOf<List<PlatformInstruction>>()

        for (function in program) {
            // TODO: somehow handle main function with another calling convention
            val allocationStrategy = allocatorFactory.invoke(getInternalCallingConvention())
            allocationStrategy.transformCode(function)
            transformedFunctions.add(allocationStrategy.getPlatformCode())
        }

        return transformedFunctions
    }

    /**
     * @return calling convention used internally for functions that are not visible outside the module
     */
    fun getInternalCallingConvention(): CallingConvention {
        return SimpleCallingConvention
    }
}
