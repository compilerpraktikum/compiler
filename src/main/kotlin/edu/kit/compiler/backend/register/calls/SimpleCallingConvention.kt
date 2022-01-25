package edu.kit.compiler.backend.register.calls

import edu.kit.compiler.backend.molkir.Width
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
            PlatformInstruction.unOp("push", PlatformTarget.Register.RBP()),
            PlatformInstruction.binOp("movq", PlatformTarget.Register.RSP(), PlatformTarget.Register.RBP()),
            PlatformInstruction.binOp(
                "subq",
                PlatformTarget.Register.RSP(),
                PlatformTarget.Constant(reservedSpace.toString())
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
                    PlatformTarget.Register.RAX().width(returnWidth),
                    returnWidth
                )
            )
        }

        epilogue.add(PlatformInstruction.op("leave"))
        epilogue.add(PlatformInstruction.op("ret"))

        return epilogue
    }

    override fun getReturnValueTarget(width: Width): PlatformTarget {
        return PlatformTarget.Register.RAX().width(width)
    }

    override fun taintRegister(register: PlatformTarget.Register) {
        // do nothing, because we needn't save registers
    }

    override fun generateFunctionCall(
        allocator: RegisterAllocator,
        init: CallingConvention.FunctionCallBuilder.() -> Unit
    ) {
        SimpleFunctionCallBuilder(allocator).apply(init)
    }

    /**
     * A very simple call builder that just pushes all arguments onto the stack
     */
    class SimpleFunctionCallBuilder(allocator: RegisterAllocator) : CallingConvention.FunctionCallBuilder(allocator) {
        private var parameterZoneWidth = 0

        override fun prepareArgument(
            source: PlatformTarget,
            width: Width,
            instructionAppender: (PlatformInstruction) -> Unit
        ) {
            parameterZoneWidth += width.inBytes
            instructionAppender(PlatformInstruction.push(source, width))
        }

        override fun generateCall(name: String, instructionAppender: (PlatformInstruction) -> Unit) {
            instructionAppender(PlatformInstruction.Call(name))
        }

        override fun cleanupStack(instructionAppender: (PlatformInstruction) -> Unit) {
            instructionAppender(
                PlatformInstruction.add(
                    PlatformTarget.Register.RSP(),
                    PlatformTarget.Constant(parameterZoneWidth.toString()),
                    Width.QUAD
                )
            )
        }
    }
}
