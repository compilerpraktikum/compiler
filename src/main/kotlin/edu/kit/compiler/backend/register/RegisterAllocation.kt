package edu.kit.compiler.backend.register

object RegisterAllocation {

    /**
     * Allocation strategy. Initialize with different instance to change strategy
     */
    private val allocatorFactory: () -> AllocationStrategy = ::TrivialAllocator

    fun allocate(program: Unit) {
        TODO("allocate registers")
    }
}
