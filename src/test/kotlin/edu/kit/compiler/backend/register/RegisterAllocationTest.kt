package edu.kit.compiler.backend.register

import edu.kit.compiler.backend.molkir.Constant
import edu.kit.compiler.backend.molkir.Memory
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.ReturnRegister
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.backend.register.calls.SimpleCallingConvention
import edu.kit.compiler.backend.register.trivial.TrivialFunctionTransformer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import edu.kit.compiler.backend.molkir.Instruction as MolkiInstruction

/**
 * These are some regression tests for the trivial register allocation.
 * These tests are extremely volatile, as they just compare the generated output against a string of expected output.
 * Any (valid) change in register allocation likely triggers a red test-case here, because this is not a sensible way to
 * test for correctness. But we want to have at least somewhat of a fail-safe against accidental regression and doing it
 * correctly would require extensive mocking, which requires disproportional effort compared to the benefit.
 */
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
        val platformCode = transformCode(code).joinToString("\n") { it.toAssembler() }
        println(platformCode)

        assertEquals(
            """
                push %rbp
                movq %rsp, %rbp
                subq %rsp, ${'$'}16
                movl ${'$'}1, %ebx
                movl %ebx, 0(%rbp)
                movl ${'$'}2, %ebx
                movl %ebx, -8(%rbp)
                movl 0(%rbp), %ebx
                movl -8(%rbp), %esi
                addl %ebx, %esi
                movl %esi, -8(%rbp)
                leave
                ret
            """.trimIndent(),
            platformCode.trimIndent()
        )
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
                Memory.of("0xffff7f34", index = Register(RegisterId(1), Width.DOUBLE), scale = 4, width = Width.DOUBLE)
            )
        )

        val platformCode = transformCode(code).joinToString("\n") { it.toAssembler() }
        println(platformCode)

        assertEquals(
            """
                push %rbp
                movq %rsp, %rbp
                subq %rsp, ${'$'}16
                movl ${'$'}1, %ebx
                movl %ebx, 0(%rbp)
                movl ${'$'}2, %ebx
                movl %ebx, -8(%rbp)
                movl 0(%rbp), %ebx
                movl -8(%rbp), %esi
                addl %ebx, %esi
                movl %esi, -8(%rbp)
                movl -8(%rbp), %ebx
                movl -8(%rbp), %esi
                movl %ebx, 0xffff7f34(,%esi,4)
                leave
                ret
            """.trimIndent(),
            platformCode.trimIndent()
        )
    }

    @Test
    fun testCall() {
        val code = listOf(
            MolkiInstruction.movl(
                Constant("1", Width.DOUBLE),
                Register(RegisterId(0), Width.DOUBLE)
            ),
            MolkiInstruction.movl(
                Constant("2", Width.DOUBLE),
                Register(RegisterId(1), Width.DOUBLE)
            ),
            MolkiInstruction.call(
                "foo",
                listOf(
                    Register(RegisterId(0), Width.DOUBLE),
                    Register(RegisterId(1), Width.DOUBLE),
                ),
                Register(RegisterId(2), Width.DOUBLE),
                false
            ),
        )

        val platformCode = transformCode(code).joinToString("\n") { it.toAssembler() }
        println(platformCode)

        assertEquals(
            """
                push %rbp
                movq %rsp, %rbp
                subq %rsp, ${'$'}24
                movl ${'$'}1, %ebx
                movl %ebx, 0(%rbp)
                movl ${'$'}2, %ebx
                movl %ebx, -8(%rbp)
                movl -8(%rbp), %ebx
                pushq %ebx
                movl 0(%rbp), %ebx
                pushq %ebx
                call foo
                movl %eax, -16(%rbp)
                addq %rsp, ${'$'}16
                leave
                ret
            """.trimIndent(),
            platformCode.trimIndent()
        )
    }

    @Test
    fun testDivision() {
        val code = listOf(
            MolkiInstruction.idivq(
                Constant("8", Width.DOUBLE),
                Constant("2", Width.DOUBLE),
                Register(RegisterId(0), Width.DOUBLE),
                Register(RegisterId(1), Width.DOUBLE)
            ),
        )

        val platformCode = transformCode(code).joinToString("\n") { it.toAssembler() }
        println(platformCode)

        assertEquals(
            """
                push %rbp
                movq %rsp, %rbp
                subq %rsp, ${'$'}16
                xorq %rdx, %rdx
                movq ${'$'}8, %rax
                movq ${'$'}2, %rbx
                idivq %rbx
                movl %edx, %esi
                movl %eax, %edi
                movl %esi, 0(%rbp)
                movl %edi, -8(%rbp)
                leave
                ret
            """.trimIndent(),
            platformCode.trimIndent()
        )
    }

    @Test
    fun testParameters() {
        val code = listOf(
            MolkiInstruction.addl(
                Register(RegisterId(0), Width.DOUBLE),
                Register(RegisterId(1), Width.DOUBLE),
                ReturnRegister(Width.DOUBLE)
            )
        )

        val platformCode =
            transformCode(code, 2).joinToString("\n") { it.toAssembler() }
        println(platformCode)

        assertEquals(
            """
                push %rbp
                movq %rsp, %rbp
                subq %rsp, ${'$'}8
                movl 16(%rbp), %ebx
                movl 24(%rbp), %esi
                addl %ebx, %esi
                movl %esi, 0(%rbp)
                movl 0(%rbp), %eax
                leave
                ret
            """.trimIndent(),
            platformCode.trimIndent()
        )
    }

    /**
     * Call the trivial register allocator with the trivial calling convention and generate the requested code.
     *
     * @param code list of molki instructions
     * @param signature signature of the transformed function
     */
    private fun transformCode(
        code: List<MolkiInstruction>,
        parameters: Int = 0
    ): List<PlatformInstruction> {
        val allocator = TrivialFunctionTransformer(SimpleCallingConvention, parameters)
        allocator.transformCode(code)
        return allocator.getPlatformCode()
    }
}
