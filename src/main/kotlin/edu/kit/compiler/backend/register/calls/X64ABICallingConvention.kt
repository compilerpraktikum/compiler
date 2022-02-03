@file:Suppress("DuplicatedCode")

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
                PlatformTarget.Constant(reservedSpace.toString()),
                PlatformTarget.Register(EnumRegister.RSP),
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

        // TODO: technically, a function generated by us called with this convention has to save the parameters onto
        //  the stack. However, we currently do not compile any functions with this calling convention that access
        //  parameters

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

    override fun getReturnValueTarget(width: Width): PlatformTarget.Register {
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
        var argumentNumber = arguments

        private val allocatedRegisters = mutableMapOf<Int, PlatformTarget.Register>()

        init {
            // WARNING: we can reliably force-allocate the necessary registers here as long as we use a trivial register
            // allocation. If we use something more sophisticated, we might need to generate spill code here.

            // forcibly allocate all required registers for parameters
            (0 until arguments.coerceAtMost(5)).forEach { index ->
                allocatedRegisters[index] = allocator.forceAllocate(parameterRegisters[index]!!)
            }

            // align RSP:
            instructionAppenderCallback(
                PlatformInstruction.push(PlatformTarget.Register(EnumRegister.RSP))
            )
            instructionAppenderCallback(
                PlatformInstruction.push(PlatformTarget.Memory.of(base = PlatformTarget.Register(EnumRegister.RSP)))
            )
            instructionAppenderCallback(
                PlatformInstruction.binOp(
                    "andq",
                    PlatformTarget.Constant("-0x10"),
                    PlatformTarget.Register(EnumRegister.RSP)
                )
            )

            // prepare stack layout: push dummy argument iff RSP wouldn't be 16-byte-aligned after pushing arguments
            val stackArguments = (arguments - 6).coerceAtLeast(0)
            if (stackArguments % 2 != 0) {
                instructionAppenderCallback(
                    PlatformInstruction.push(PlatformTarget.Constant("0"))
                )
            }
        }

        override fun prepareArgument(
            source: PlatformTarget,
            width: Width
        ) {
            when (val index = --argumentNumber) {
                in 0..5 -> {
                    // move source to the correct register
                    instructionAppenderCallback(
                        PlatformInstruction.mov(
                            source,
                            allocatedRegisters[index]!!.width(width),
                            width
                        )
                    )
                }
                else -> {
                    // generate push instruction with correct size and extend memory with zeros, if necessary
                    when (width) {
                        Width.BYTE, Width.WORD -> {
                            val intermediate = allocator.allocateRegister()
                            instructionAppenderCallback(
                                PlatformInstruction.movzx(
                                    source,
                                    intermediate.quadWidth(),
                                    width,
                                    Width.QUAD
                                )
                            )
                            instructionAppenderCallback(PlatformInstruction.push(intermediate.quadWidth()))
                            allocator.freeRegister(intermediate)
                        }
                        Width.DOUBLE -> {
                            val intermediate = allocator.allocateRegister()
                            instructionAppenderCallback(
                                PlatformInstruction.mov(
                                    source,
                                    intermediate.doubleWidth(),
                                    Width.DOUBLE
                                )
                            )
                            instructionAppenderCallback(PlatformInstruction.push(intermediate.quadWidth()))
                            allocator.freeRegister(intermediate)
                        }
                        Width.QUAD -> {
                            instructionAppenderCallback(PlatformInstruction.push(source))
                        }
                    }
                }
            }
        }

        override fun generateCall(name: String) {
            instructionAppenderCallback(PlatformInstruction.Call(name))
        }

        override fun cleanupStack() {
            // calculate how many arguments are pushed onto the stack
            var stackArguments = (arguments - 6).coerceAtLeast(0)
            if (stackArguments % 2 != 0) {
                stackArguments += 1
            }

            if (stackArguments > 0) {
                // free stack space allocated for arguments
                instructionAppenderCallback(
                    PlatformInstruction.add(
                        PlatformTarget.Constant((stackArguments * 8).toString()),
                        PlatformTarget.Register(EnumRegister.RSP),
                        Width.QUAD
                    )
                )
            }

            // restore unaligned stack pointer
            instructionAppenderCallback(
                PlatformInstruction.mov(
                    PlatformTarget.Memory.of(
                        const = "8",
                        base = PlatformTarget.Register(EnumRegister.RSP)
                    ),
                    PlatformTarget.Register(EnumRegister.RSP),
                    Width.QUAD
                )
            )

            // free forcibly allocated registers
            allocatedRegisters.forEach { (_, register) -> allocator.freeRegister(register) }
        }
    }
}
