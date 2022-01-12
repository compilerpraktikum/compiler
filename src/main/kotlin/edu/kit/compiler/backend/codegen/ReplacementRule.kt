package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.MolkIR
import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.Target.InputOutputTarget.Constant
import edu.kit.compiler.backend.molkir.Target.InputOutputTarget.Register
import edu.kit.compiler.backend.molkir.Width
import firm.nodes.Node

class VirtualRegisterTable() {
    val map: MutableMap<CodeGenIR, Register> = mutableMapOf()
    private var nextRegisterId: Int = 0

    fun newRegisterFor(node: CodeGenIR, width: Width): Register {
        val registerId = nextRegisterId++
        val register = Register(RegisterId(registerId), width)
        map[node] = register
        return register
    }

    fun getRegisterFor(node: CodeGenIR): Register? = map[node]
}

typealias Pattern = CodeGenIR

fun interface ReplacementRule {
    fun matches(node: CodeGenIR, registers: VirtualRegisterTable): Pair<CodeGenIR, MolkIR>?
}

val replacementRules: List<ReplacementRule> = listOf(
    // Constant -> Register
    ReplacementRule { node, registers ->
        if (node is CodeGenIR.Const) {
            val register = registers.newRegisterFor(node, Width.DOUBLE)
            CodeGenIR.RegisterRef(register) to Instruction.movq(Constant(node.const), register)
        } else {
            null
        }
    },
    // Read
)

class Rule(private val matcher: (CodeGenIR) -> MatchResult?) {
    fun match(node: CodeGenIR): MatchResult? = matcher(node)

    data class MatchResult(
        val replacement:CodeGenIR,
        val instructions: List<Instruction>,
        val cost: Int = 1,
    )
}

class RuleBuilderContext(
    val node: CodeGenIR,
    //private val registerTable: VirtualRegisterTable = VirtualRegisterTable()
) {
    private var match: Rule.MatchResult? = null

    inline fun <reified FirmNodeType : Node> binOp(block: RuleBuilderContext.(CodeGenIR.BinOP) -> Unit) {
        if (node is CodeGenIR.BinOP && node.operation is FirmNodeType) {
            val context = RuleBuilderContext
        }
    }

    fun result(): Rule.MatchResult? = match
}

fun rule(block: RuleBuilderContext.() -> Unit): Rule {
    return Rule { node ->
        val context = RuleBuilderContext(node)
        context.block()
        context.result()
    }
}
