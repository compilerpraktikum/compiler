package edu.kit.compiler.backend.register

import edu.kit.compiler.backend.molkir.Constant
import edu.kit.compiler.backend.molkir.Memory
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.backend.register.calls.SimpleCallingConvention
import edu.kit.compiler.backend.register.trivial.TrivialFunctionTransformer
import org.junit.jupiter.api.Test
import edu.kit.compiler.backend.molkir.Instruction as MolkiInstruction

internal class RegisterAllocationTest {

    @Test
    fun testSimpleRegisterAllocation() {
        val code = listOf(
            MolkiInstruction.movl(
                Constant("1", Width.DOUBLE),
                Register(RegisterId(0), Width.DOUBLE)
            ),
            MolkiInstruction.movl(
                Constant("2", Width.DOUBLE),
                Register(RegisterId(1), Width.DOUBLE)
            ),
            MolkiInstruction.addl(
                Register(RegisterId(0), Width.DOUBLE),
                Register(RegisterId(1), Width.DOUBLE),
                Register(RegisterId(1), Width.DOUBLE)
            )
        )
        val platformCode = transformCode(code)
        println(platformCode.joinToString("\n") { it.toAssembler() })
    }

    @Test
    fun testNoSpill() {
        val code = listOf(
            MolkiInstruction.movl(
                Constant("1", Width.DOUBLE),
                Register(RegisterId(0), Width.DOUBLE)
            ),
            MolkiInstruction.movl(
                Constant("2", Width.DOUBLE),
                Register(RegisterId(1), Width.DOUBLE)
            ),
            MolkiInstruction.addl(
                Register(RegisterId(0), Width.DOUBLE),
                Register(RegisterId(1), Width.DOUBLE),
                Register(RegisterId(1), Width.DOUBLE)
            ),
            MolkiInstruction.movl(
                Register(RegisterId(1), Width.DOUBLE),
                Memory.of("0xffff7f34", index = Register(RegisterId(1), Width.DOUBLE), scale = 4, width=Width.DOUBLE)
            )
        )

        val platformCode = transformCode(code)
        println(platformCode.joinToString("\n") { it.toAssembler() })
    }

    /**
     * Call the trivial register allocator with the trivial calling convention and generate the requested code.
     */
    private fun transformCode(code: List<MolkiInstruction>): List<PlatformInstruction> {
        val allocator = TrivialFunctionTransformer(SimpleCallingConvention)
        allocator.transformCode(code)
        return allocator.getPlatformCode()
    }
}
