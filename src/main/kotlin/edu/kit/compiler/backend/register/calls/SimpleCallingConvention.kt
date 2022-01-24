package edu.kit.compiler.backend.register.calls

import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.backend.register.PlatformInstruction
import edu.kit.compiler.backend.register.PlatformTarget

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
            when (returnWidth) {
                Width.BYTE -> epilogue.add(
                    PlatformInstruction.movb(
                        returnValue,
                        PlatformTarget.Register.RAX().halfWordWidth()
                    )
                )
                Width.WORD -> TODO()
                Width.DOUBLE -> epilogue.add(
                    PlatformInstruction.movl(
                        returnValue,
                        PlatformTarget.Register.RAX().doubleWidth()
                    )
                )
                Width.QUAD -> epilogue.add(
                    PlatformInstruction.movq(
                        returnValue,
                        PlatformTarget.Register.RAX().quadWidth()
                    )
                )
            }
        }

        epilogue.add(PlatformInstruction.op("leave"))
        epilogue.add(PlatformInstruction.op("ret"))

        return epilogue
    }

    override fun getReturnValueTarget(): PlatformTarget {
        return PlatformTarget.Register.RAX()
    }

    override fun taintRegister(register: PlatformTarget.Register) {
        // do nothing, because we needn't save registers
    }
}
