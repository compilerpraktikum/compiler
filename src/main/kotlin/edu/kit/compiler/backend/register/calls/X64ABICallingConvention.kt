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

    private val parameterRegisters = mapOf(
        0 to EnumRegister.RDI,
        1 to EnumRegister.RSI,
        2 to EnumRegister.RDX,
        3 to EnumRegister.RCX,
        4 to EnumRegister.R8,
        5 to EnumRegister.R9,
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
                        PlatformTarget.Register(nonVolatileRegister, Width.QUAD)
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

    override fun getParameterLocation(virtualRegisterId: Int): PlatformTarget {
        return when (virtualRegisterId) {
            in 0..5 -> PlatformTarget.Register(parameterRegisters[virtualRegisterId]!!, Width.QUAD)
            else -> {
                val parameterStackLocation = (virtualRegisterId - 6 + 2) * 8 // skip 2 quadwords (pushed EBP and RIP)
                PlatformTarget.Memory.of(
                    parameterStackLocation.toString(),
                    PlatformTarget.Register(EnumRegister.RBP),
                    null,
                    null
                )
            }
        }
    }

    override fun taintRegister(register: PlatformTarget.Register) {
        this.taintedRegisters.add(register.register)
    }

    override fun generateFunctionCall(
        allocator: RegisterAllocator,
        arguments: Int,
        instructionAppender: (PlatformInstruction) -> Unit,
        init: CallingConvention.FunctionCallBuilder.() -> Unit
    ) {
        X64FunctionCallBuilder(allocator, arguments, instructionAppender).apply(init)
    }

    class X64FunctionCallBuilder(
        allocator: RegisterAllocator,
        arguments: Int,
        instructionAppenderCallback: (PlatformInstruction) -> Unit
    ) :
        CallingConvention.FunctionCallBuilder(allocator, arguments, instructionAppenderCallback) {
        var argumentNumber = 0

        init {
            // WARNING: we can reliably force-allocate the necessary registers here as long as we use a trivial register
            // allocation. If we use something more sophisticated, we might need to generate spill code here.
        }

        override fun prepareArgument(
            source: PlatformTarget,
            width: Width
        ) {
            when (val n = argumentNumber++) {
                in 0..5 -> allocator.forceAllocate(parameterRegisters[n]!!)
                else -> TODO()
            }
        }

        override fun generateCall(name: String) {
            TODO("not implemented")
        }

        override fun cleanupStack() {
            TODO("not implemented")
        }
    }
}
