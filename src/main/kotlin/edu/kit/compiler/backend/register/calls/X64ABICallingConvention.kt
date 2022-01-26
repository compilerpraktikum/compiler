package edu.kit.compiler.backend.register.calls

import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.backend.register.EnumRegister
import edu.kit.compiler.backend.register.PlatformInstruction
import edu.kit.compiler.backend.register.PlatformTarget
import edu.kit.compiler.backend.register.RegisterAllocator

/**
 * Implementation of the x64 calling convention
 */
object X64ABICallingConvention : CallingConvention {

    /**
     * All registers that are modified by this function
     */
    private val taintedRegisters = mutableSetOf<EnumRegister>()

    /**
     * Registers that must be restored if used
     */
    private val nonVolatileRegisters = mutableListOf(
        EnumRegister.RBX,
        EnumRegister.RDI,
        EnumRegister.RSI,
        EnumRegister.R12,
        EnumRegister.R13,
        EnumRegister.R14,
        EnumRegister.R15,
    )

    override fun generateFunctionPrologue(reservedSpace: Int): List<PlatformInstruction> {
        val prologue = mutableListOf<PlatformInstruction>()

        prologue.add(PlatformInstruction.unOp("push", PlatformTarget.Register(EnumRegister.RBP)))
        prologue.add(
            PlatformInstruction.mov(
                PlatformTarget.Register(EnumRegister.RSP),
                PlatformTarget.Register(EnumRegister.RBP),
                Width.QUAD
            )
        )
        prologue.add(
            PlatformInstruction.sub(
                PlatformTarget.Register(EnumRegister.RSP),
                PlatformTarget.Constant(reservedSpace.toString()),
                Width.QUAD
            )
        )

        // push tainted register to save them
        for (nonVolatileRegister in nonVolatileRegisters) {
            if (taintedRegisters.contains(nonVolatileRegister)) {
                prologue.add(
                    PlatformInstruction.push(
                        PlatformTarget.Register(nonVolatileRegister, Width.QUAD),
                        Width.QUAD
                    )
                )
            }
        }

        return prologue
    }

    override fun generateFunctionEpilogue(
        returnValue: PlatformTarget?,
        returnWidth: Width?
    ): List<PlatformInstruction> {
        val epilogue = mutableListOf<PlatformInstruction>()

        // pop tainted registers in reverse order
        for (nonVolatileRegister in nonVolatileRegisters.reversed()) {
            if (taintedRegisters.contains(nonVolatileRegister)) {
                epilogue.add(
                    PlatformInstruction.pop(
                        PlatformTarget.Register(nonVolatileRegister, Width.QUAD),
                        Width.QUAD
                    )
                )
            }
        }

        if (returnValue != null) {
            require(returnWidth != null) { "returnWidth cannot be null if returnValue is not null" }
            epilogue.add(
                PlatformInstruction.mov(
                    returnValue,
                    PlatformTarget.Register(EnumRegister.RAX).width(returnWidth),
                    returnWidth
                )
            )
        }

        epilogue.add(PlatformInstruction.op("leave"))
        epilogue.add(PlatformInstruction.op("ret"))

        return epilogue
    }

    override fun getReturnValueTarget(width: Width): PlatformTarget {
        return PlatformTarget.Register(EnumRegister.RAX).width(width)
    }

    override fun taintRegister(register: PlatformTarget.Register) {
        this.taintedRegisters.add(register.register)
    }

    override fun generateFunctionCall(
        allocator: RegisterAllocator,
        init: CallingConvention.FunctionCallBuilder.() -> Unit
    ) {
        X64FunctionCallBuilder(allocator).apply(init)
    }

    class X64FunctionCallBuilder(allocator: RegisterAllocator) : CallingConvention.FunctionCallBuilder(allocator) {
        override fun prepareArgument(
            source: PlatformTarget,
            width: Width,
            instructionAppender: (PlatformInstruction) -> Unit
        ) {
            TODO("not implemented")
        }

        override fun generateCall(name: String, instructionAppender: (PlatformInstruction) -> Unit) {
            TODO("not implemented")
        }

        override fun cleanupStack(instructionAppender: (PlatformInstruction) -> Unit) {
            TODO("not implemented")
        }
    }
}
