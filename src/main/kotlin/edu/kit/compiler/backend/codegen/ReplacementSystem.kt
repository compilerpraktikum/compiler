package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.utils.ReplacementBuilderScope

data class Replacement(
    val node: CodeGenIR,
    val instructions: LazyInstructionList,
    val cost: Int,
)

class ReplacementScope(
    private val registerTable: VirtualRegisterTable
) : ReplacementBuilderScope {
    fun newRegister(width: Width): Register = registerTable.newRegister(width)
}

class LazyInstructionList
private constructor(private val instructions: () -> MutableList<Instruction>) {
    constructor() : this({ mutableListOf() })

    fun append(instruction: Instruction) = LazyInstructionList {
        instructions().apply { add(instruction) }
    }

    fun append(instructions: Array<out Instruction>) = LazyInstructionList {
        instructions().apply { addAll(instructions) }
    }

    fun append(instructions: List<Instruction>) = LazyInstructionList {
        instructions().apply { addAll(instructions) }
    }

    fun append(list: LazyInstructionList) = append(list.buildList())

    fun append(block: AppenderScope.() -> Unit) = append(
        AppenderScope().apply(block).list
    )

    class AppenderScope {
        val list = mutableListOf<Instruction>()
        operator fun Instruction.unaryPlus() {
            list.add(this)
        }
    }

    private fun buildList() = instructions()
    fun build(): List<Instruction> = buildList()
}

operator fun LazyInstructionList.plus(other: LazyInstructionList) = append(other)

fun instructionListOf(vararg instructions: Instruction) = LazyInstructionList().append(instructions)

fun CodeGenIR.transform(registerTable: VirtualRegisterTable): Replacement {
    val scope = ReplacementScope(registerTable)
    walkDepthFirst { node ->
        println("VISIT: ${node.display()}")
        /*
         * The code below assumes that all rules matching the node have the same result type (most likely register).
         * In this case, we can simply select the rule with the minimal cost and use this as the replacement.
         * Otherwise, we would need to store the best replacement for each possible result type.
         */
        node.replacement = replacementRules.map { rule ->
            with(scope) {
                rule.match(node)
            }
        }.filterNotNull().minByOrNull { it.cost }
        println("-> REPLACEMENT: ${node.replacement}")
    }
    return replacement ?: error("no matching replacement found for root node ${this.display()}")
}
