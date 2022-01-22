package edu.kit.compiler.backend.register

import edu.kit.compiler.backend.register.calls.CallingConvention
import edu.kit.compiler.backend.register.trivial.TrivialFunctionTransformer

object RegisterAllocation {

    /**
     * Allocation strategy. Initialize with different instance to change strategy
     */
    private val allocatorFactory: (CallingConvention) -> FunctionTransformer = ::TrivialFunctionTransformer

    fun allocate(program: Unit) {
        TODO("allocate registers")
    }
}
