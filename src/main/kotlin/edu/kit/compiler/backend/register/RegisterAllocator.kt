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
     * Free an allocated register so it can be allocated again
     */
    fun freeRegister(register: EnumRegister)

    /**
     * Free all currently allocated registers
     */
    fun freeAll()

    /**
     * Query whether a register is already allocated
     */
    fun isAllocated(register: EnumRegister): Boolean

    /**
     * Forcibly allocate a register by its definition. Throws an [IllegalArgumentException] if the requested register
     * is already allocated
     */
    fun forceAllocate(register: EnumRegister): PlatformTarget.Register
}
