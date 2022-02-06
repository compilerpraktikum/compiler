package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Instruction
import kotlin.test.Test
import kotlin.test.assertEquals

internal class LazyInstructionListTest {
    @Test
    fun test() {
        val list = instructionListOf(Instruction.jmp("1"))
            .append(Instruction.jmp("2"))
            .append(Instruction.jmp("3"))
        val anotherList = instructionListOf(Instruction.jmp("4"))
            .append(Instruction.jmp("5"))
        val finalList = list.append(anotherList)
            .append(Instruction.jmp("6"))

        assertEquals(
            """
                jmp 1
                jmp 2
                jmp 3
                jmp 4
                jmp 5
                jmp 6
            """.trimIndent(),
            finalList.build().joinToString(separator = "\n") { it.toMolki() }
        )
    }
}
