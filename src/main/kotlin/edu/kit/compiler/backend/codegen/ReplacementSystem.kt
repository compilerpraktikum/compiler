package edu.kit.compiler.backend.codegen

import edu.kit.compiler.Compiler
import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.utils.Logger
import edu.kit.compiler.utils.ReplacementBuilderScope
import edu.kit.compiler.utils.Rule
import edu.kit.compiler.utils.RuleBuilderScope

data class Replacement(
    val node: CodeGenIR,
    val instructions: LazyInstructionList,
    val cost: Int,
)

class ReplacementScope(
    private val registerTable: VirtualRegisterTable,
    private val currentNode: CodeGenIR,
    private val ruleName: String,
) : ReplacementBuilderScope {
    fun newRegister(width: Width): Register = registerTable.newRegister(width)

    fun debugComment() =
        Instruction.comment("${currentNode.display()} (Rule: $ruleName)")
}

typealias ReplacementRule = Rule<CodeGenIR, Replacement, ReplacementScope>
typealias ReplacementRuleBuilderScope = RuleBuilderScope<CodeGenIR, Replacement, ReplacementScope>

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

    @JvmName("appendAll")
    fun append(vararg instructions: Instruction?) = append(instructions.filterNotNull())

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

class InstructionSelector(
    optimizationLevel: Compiler.OptimizationLevel
) {
    private val rules = createReplacementRulesFor(optimizationLevel)

    fun transform(root: CodeGenIR, registerTable: VirtualRegisterTable): List<Instruction> {
        Logger.trace { "  Transforming ${root.display()}" }
        root.walkDepthFirst { node ->
            Logger.trace { "    Calculating replacement for ${node.display()}" }
            /*
             * The code below assumes that all rules matching the node have the same result type (most likely register).
             * In this case, we can simply select the rule with the minimal cost and use this as the replacement.
             * Otherwise, we would need to store the best replacement for each possible result type.
             */
            val possibleReplacements = rules.mapNotNull { rule ->
                with(ReplacementScope(registerTable, node, rule.name)) {
                    rule.match(node)?.let { it to rule.name }
                }
            }
            if (possibleReplacements.isEmpty()) {
                node.replacement = null
            } else {
                val minCost = possibleReplacements.minOf { it.first.cost }
                val optimalReplacements = possibleReplacements.filter { it.first.cost == minCost }
                check(optimalReplacements.size <= 1) { "more than one replacement possible for node ${node.display()}:\n${optimalReplacements.joinToString(separator = "\n") { "  - ${it.second}" }}" }
                node.replacement = optimalReplacements.firstOrNull()?.first
            }
            Logger.trace { "      -> ${node.replacement?.let { "${it.node::class.simpleName} (Cost: ${it.cost})" }}" }
        }

        return root.replacement?.instructions?.build() ?: error("no matching replacement found for root node ${root.display()}")
    }
}
