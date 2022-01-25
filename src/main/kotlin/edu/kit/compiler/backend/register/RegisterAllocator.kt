package edu.kit.compiler.backend.register

interface RegisterAllocator {

    /**
     * Allocate a register from the list of available registers
     */
    fun allocateRegister(): PlatformTarget.Register

    /**
     * Free an allocated register so it can be allocated again
     */
    fun freeRegister(registerHandle: PlatformTarget.Register)

    /**
     * Free all currently allocated registers
     */
    fun freeAll()
}
