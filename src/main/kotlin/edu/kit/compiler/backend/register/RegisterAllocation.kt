package edu.kit.compiler.backend.register

import edu.kit.compiler.backend.register.trivial.TrivialTransformer

object RegisterAllocation {

    /**
     * Allocation strategy. Initialize with different instance to change strategy
     */
    private val allocatorFactory: () -> PlatformTransformer = ::TrivialTransformer

    fun allocate(program: Unit) {
        TODO("allocate registers")
    }
}
