package edu.kit.compiler.backend.register.calls

import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.backend.register.EnumRegister
import edu.kit.compiler.backend.register.PlatformInstruction
import edu.kit.compiler.backend.register.PlatformTarget
import edu.kit.compiler.backend.register.RegisterAllocator

/**
 * Very simplistic calling convention used only for non-ABI functions of a MiniJava program (i.e. everything other than
 * the main function and intrinsic functions).
 */
object SimpleCallingConvention : CallingConvention {

    override fun generateFunctionPrologue(reservedSpace: Int): List<PlatformInstruction> {
        return listOf(
            PlatformInstruction.push(PlatformTarget.Register(EnumRegister.RBP)),
            PlatformInstruction.mov(
                PlatformTarget.Register(EnumRegister.RSP),
                PlatformTarget.Register(EnumRegister.RBP),
                Width.QUAD
            ),
            PlatformInstruction.sub(
                PlatformTarget.Constant(reservedSpace.toString()),
                PlatformTarget.Register(EnumRegister.RSP),
                Width.QUAD
            ),
        )
    }

    override fun generateFunctionEpilogue(
        returnValue: PlatformTarget?,
        returnWidth: Width?
    ): List<PlatformInstruction> {
        val epilogue = mutableListOf<PlatformInstruction>()

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
        val parameterStackLocation = (virtualRegisterId + 2) * 8 // skip 2 quadwords (pushed EBP and RIP)
        return PlatformTarget.Memory.of(
            parameterStackLocation.toString(),
            PlatformTarget.Register(EnumRegister.RBP),
            null,
            null
        )
    }

    override fun taintRegister(register: PlatformTarget.Register) {
        // do nothing, because we needn't save registers
    }

    override fun generateFunctionCall(
        allocator: RegisterAllocator,
        arguments: Int,
        instructionAppender: (PlatformInstruction) -> Unit,
        init: CallingConvention.FunctionCallBuilder.() -> Unit
    ) {
        SimpleFunctionCallBuilder(allocator, arguments, instructionAppender).apply(init)
    }

    /**
     * A very simple call builder that just pushes all arguments onto the stack
     */
    class SimpleFunctionCallBuilder(
        allocator: RegisterAllocator,
        arguments: Int,
        instructionAppenderCallback: (PlatformInstruction) -> Unit
    ) : CallingConvention.FunctionCallBuilder(allocator, arguments, instructionAppenderCallback) {
        override fun prepareArgument(
            source: PlatformTarget,
            width: Width
        ) {
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

        override fun generateCall(name: String) {
            instructionAppenderCallback(PlatformInstruction.Call(name))
        }

        override fun cleanupStack() {
            instructionAppenderCallback(
                PlatformInstruction.add(
                    PlatformTarget.Constant((arguments * 8).toString()),
                    PlatformTarget.Register(EnumRegister.RSP),
                    Width.QUAD
                )
            )
        }
    }
}
