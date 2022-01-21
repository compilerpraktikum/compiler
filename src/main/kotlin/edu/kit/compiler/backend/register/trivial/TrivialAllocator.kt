package edu.kit.compiler.backend.register.trivial

import edu.kit.compiler.backend.register.PlatformTarget

/**
 * Trivial allocator for registers that just takes the next available register from a list of free registers
 */
class TrivialAllocator {

    /**
     * List of all general purpose registers and whether they are currently allocated or free
     */
    private val freeRegisters = mutableMapOf<() -> PlatformTarget.GeneralPurposeRegister, Boolean>(
        PlatformTarget.GeneralPurposeRegister::RAX to true,
        PlatformTarget.GeneralPurposeRegister::RBX to true,
        PlatformTarget.GeneralPurposeRegister::RCX to true,
        PlatformTarget.GeneralPurposeRegister::RDX to true,
        PlatformTarget.GeneralPurposeRegister::RSI to true,
        PlatformTarget.GeneralPurposeRegister::RDI to true,
        PlatformTarget.GeneralPurposeRegister::R8 to true,
        PlatformTarget.GeneralPurposeRegister::R9 to true,
        PlatformTarget.GeneralPurposeRegister::R10 to true,
        PlatformTarget.GeneralPurposeRegister::R11 to true,
        PlatformTarget.GeneralPurposeRegister::R12 to true,
        PlatformTarget.GeneralPurposeRegister::R13 to true,
        PlatformTarget.GeneralPurposeRegister::R14 to true,
        PlatformTarget.GeneralPurposeRegister::R15 to true
    )

    /**
     * A map of currently allocated handles to their factory, so they can be associated when being freed
     */
    private val allocatedRegisterHandles =
        mutableMapOf<PlatformTarget.GeneralPurposeRegister, () -> PlatformTarget.GeneralPurposeRegister>()

    /**
     * Allocate the next available register from the register list and return a handle to it
     */
    fun allocateRegister(): PlatformTarget.GeneralPurposeRegister {
        val selectedRegisterFactory = freeRegisters.entries.first { (_, free) -> free }.key
        freeRegisters[selectedRegisterFactory] = false

        val registerHandle = selectedRegisterFactory.invoke()
        this.allocatedRegisterHandles[registerHandle] = selectedRegisterFactory
        return registerHandle
    }

    /**
     * Free an allocated register so it can be allocated again
     */
    fun freeRegister(registerHandle: PlatformTarget.GeneralPurposeRegister) {
        this.freeRegisters[this.allocatedRegisterHandles.remove(registerHandle)!!] = true
    }
}
