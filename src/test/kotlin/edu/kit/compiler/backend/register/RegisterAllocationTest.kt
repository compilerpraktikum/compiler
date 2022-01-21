package edu.kit.compiler.backend.register

import edu.kit.compiler.backend.molkir.Constant
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.backend.register.trivial.TrivialTransformer
import org.junit.jupiter.api.Test
import edu.kit.compiler.backend.molkir.Instruction.Companion as MolkInstruction

internal class RegisterAllocationTest {

    @Test
    fun testSimpleRegisterAllocation() {
        val allocator = TrivialTransformer()
        val code = listOf(
            MolkInstruction.movq(
                Constant("1"),
                Register(RegisterId(0), Width.DOUBLE)
            ),
            MolkInstruction.movq(
                Constant("2"),
                Register(RegisterId(1), Width.DOUBLE)
            ),
            MolkInstruction.addq(
                Register(RegisterId(0), Width.DOUBLE),
                Register(RegisterId(1), Width.DOUBLE),
                Register(RegisterId(1), Width.DOUBLE)
            )
        )
        allocator.transformCode(code)
    }
}
