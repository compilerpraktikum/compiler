package edu.kit.compiler.backend.register.allocator

import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.backend.register.EnumRegister
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
    private val freeRegisters = mutableMapOf(
        EnumRegister.RBX to true,
        EnumRegister.R10 to true,
        EnumRegister.R11 to true,
        EnumRegister.R12 to true,
        EnumRegister.R13 to true,
        EnumRegister.R14 to true,
        EnumRegister.R15 to true,

        // reserved for `div` operation
        EnumRegister.RAX to true,

        // reserved for x64 ABI
        EnumRegister.RSI to true,
        EnumRegister.RDI to true,
        EnumRegister.RDX to true,
        EnumRegister.RCX to true,
        EnumRegister.R9 to true,
        EnumRegister.R8 to true,
    )

    /**
     * A map of currently allocated handles to their factory, so they can be associated when being freed
     */
    private val allocatedRegisters = mutableSetOf<EnumRegister>()

    /**
     * Allocate the next available register from the register list and return a handle to it
     */
    override fun allocateRegister(): PlatformTarget.Register {
        val selectedRegister = freeRegisters.entries.first { (_, free) -> free }.key
        return forceAllocate(selectedRegister)
    }

    override fun hasMoreRegisters(): Boolean {
        return freeRegisters.any { (_, free) -> free }
    }

    /**
     * Free an allocated register so it can be allocated again
     */
    override fun freeRegister(registerHandle: PlatformTarget.Register) {
        freeRegister(registerHandle.register)
    }

    override fun freeRegister(register: EnumRegister) {
        this.allocatedRegisters.remove(register)
        this.freeRegisters[register] = true
    }

    /**
     * Free all currently allocated registers
     */
    override fun freeAll() {
        // copy the list to avoid comodification
        val handles = this.allocatedRegisters.toList()
        handles.forEach(::freeRegister)
    }

    override fun isAllocated(register: EnumRegister): Boolean {
        return this.allocatedRegisters.contains(register)
    }

    override fun forceAllocate(register: EnumRegister): PlatformTarget.Register {
        require(!isAllocated(register)) { "register ${register.getFullRegisterName(Width.QUAD)} is already allocated" }

        freeRegisters[register] = false

        val registerHandle = PlatformTarget.Register(register)
        this.allocatedRegisters.add(register)
        return registerHandle
    }
}
