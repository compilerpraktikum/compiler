package edu.kit.compiler.backend.register.trivial

import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.backend.register.EnumRegister
import edu.kit.compiler.backend.register.PlatformTarget
import edu.kit.compiler.backend.register.RegisterAllocator

/**
 * Trivial allocator for registers that just takes the next available register from a list of free registers
 */
class TrivialAllocator : RegisterAllocator {

    private val registerFactories = mutableMapOf<EnumRegister, () -> PlatformTarget.Register>(
        EnumRegister.RAX to PlatformTarget.Register::RAX,
        EnumRegister.RBX to PlatformTarget.Register::RBX,
        EnumRegister.RCX to PlatformTarget.Register::RCX,
        EnumRegister.RDX to PlatformTarget.Register::RDX,
        EnumRegister.RSI to PlatformTarget.Register::RSI,
        EnumRegister.RDI to PlatformTarget.Register::RDI,
        EnumRegister.RSP to PlatformTarget.Register::RSP,
        EnumRegister.RBP to PlatformTarget.Register::RBP,
        EnumRegister.R8 to PlatformTarget.Register::R8,
        EnumRegister.R9 to PlatformTarget.Register::R9,
        EnumRegister.R10 to PlatformTarget.Register::R10,
        EnumRegister.R11 to PlatformTarget.Register::R11,
        EnumRegister.R12 to PlatformTarget.Register::R12,
        EnumRegister.R13 to PlatformTarget.Register::R13,
        EnumRegister.R14 to PlatformTarget.Register::R14,
        EnumRegister.R15 to PlatformTarget.Register::R15
    )

    /**
     * List of all general purpose registers and whether they are currently allocated or free.
     * Sorted in a way to prefer allocating registers that are not required by the 64 bit ABI calling convention or are
     * reserved for division. This way, we hopefully need less spilling during function calls.
     */
    private val freeRegisters = mutableMapOf<EnumRegister, Boolean>(
        EnumRegister.RBX to true,
        EnumRegister.RSI to true,
        EnumRegister.RDI to true,
        EnumRegister.R10 to true,
        EnumRegister.R11 to true,
        EnumRegister.R12 to true,
        EnumRegister.R13 to true,
        EnumRegister.R14 to true,
        EnumRegister.R15 to true,

        // reserved for `div` operation
        EnumRegister.RAX to true,

        // reserved for x64 ABI
        EnumRegister.R9 to true,
        EnumRegister.R8 to true,
        EnumRegister.RDX to true,
        EnumRegister.RCX to true,
    )

    /**
     * A map of currently allocated handles to their factory, so they can be associated when being freed
     */
    private val allocatedRegisterHandles = mutableMapOf<EnumRegister, PlatformTarget.Register>()

    /**
     * Allocate the next available register from the register list and return a handle to it
     */
    override fun allocateRegister(): PlatformTarget.Register {
        val selectedRegister = freeRegisters.entries.first { (_, free) -> free }.key
        return forceAllocate(selectedRegister)
    }

    /**
     * Free an allocated register so it can be allocated again
     */
    override fun freeRegister(registerHandle: PlatformTarget.Register) {
        freeRegister(registerHandle.register)
    }

    override fun freeRegister(register: EnumRegister) {
        this.allocatedRegisterHandles.remove(register)
        this.freeRegisters[register] = true
    }

    /**
     * Free all currently allocated registers
     */
    override fun freeAll() {
        val allocated = this.allocatedRegisterHandles.keys.toList()
        allocated.forEach(::freeRegister)
    }

    override fun isAllocated(register: EnumRegister): Boolean {
        return this.allocatedRegisterHandles.containsKey(register)
    }

    override fun forceAllocate(register: EnumRegister): PlatformTarget.Register {
        require(!isAllocated(register)) { "register ${register.getFullRegisterName(Width.QUAD)} is already allocated" }

        freeRegisters[register] = false

        val registerHandle = registerFactories[register]!!.invoke()
        this.allocatedRegisterHandles[registerHandle.register] = registerHandle
        return registerHandle
    }
}
