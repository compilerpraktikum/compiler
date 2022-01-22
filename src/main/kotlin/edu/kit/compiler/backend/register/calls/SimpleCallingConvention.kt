package edu.kit.compiler.backend.register.calls

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

    override fun generateFunctionEpilogue(): List<PlatformInstruction> {
        return listOf(
            PlatformInstruction.op("leave"),
            PlatformInstruction.op("ret"),
        )
    }
}
