package edu.kit.compiler.backend.register.trivial

import edu.kit.compiler.backend.register.PlatformTarget
import edu.kit.compiler.backend.register.RegisterAllocator

/**
 * Trivial allocator for registers that just takes the next available register from a list of free registers
 */
class TrivialAllocator : RegisterAllocator {

    /**
     * List of all general purpose registers and whether they are currently allocated or free.
     * Sorted in a way to prefer allocating registers that are not required by the 64 bit ABI calling convention or are
     * reserved for division. This way, we hopefully need less spilling during function calls.
     */
    private val freeRegisters = mutableMapOf<() -> PlatformTarget.Register, Boolean>(
        PlatformTarget.Register::RBX to true,
        PlatformTarget.Register::RSI to true,
        PlatformTarget.Register::RDI to true,
        PlatformTarget.Register::R10 to true,
        PlatformTarget.Register::R11 to true,
        PlatformTarget.Register::R12 to true,
        PlatformTarget.Register::R13 to true,
        PlatformTarget.Register::R14 to true,
        PlatformTarget.Register::R15 to true,

        // reserved for `div` operation
        PlatformTarget.Register::RAX to true,

        // reserved for x64 ABI
        PlatformTarget.Register::R9 to true,
        PlatformTarget.Register::R8 to true,
        PlatformTarget.Register::RDX to true,
        PlatformTarget.Register::RCX to true,
    )

    /**
     * A map of currently allocated handles to their factory, so they can be associated when being freed
     */
    private val allocatedRegisterHandles =
        mutableMapOf<PlatformTarget.Register, () -> PlatformTarget.Register>()

    /**
     * Allocate the next available register from the register list and return a handle to it
     */
    override fun allocateRegister(): PlatformTarget.Register {
        val selectedRegisterFactory = freeRegisters.entries.first { (_, free) -> free }.key
        freeRegisters[selectedRegisterFactory] = false

        val registerHandle = selectedRegisterFactory.invoke()
        this.allocatedRegisterHandles[registerHandle] = selectedRegisterFactory
        return registerHandle
    }

    /**
     * Free an allocated register so it can be allocated again
     */
    override fun freeRegister(registerHandle: PlatformTarget.Register) {
        this.freeRegisters[this.allocatedRegisterHandles.remove(registerHandle)!!] = true
    }

    /**
     * Free all currently allocated registers
     */
    override fun freeAll() {
        val allocated = this.allocatedRegisterHandles.keys.toList()
        allocated.forEach(::freeRegister)
    }
}
