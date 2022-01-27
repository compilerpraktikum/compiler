package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.Width
import firm.nodes.Node
import edu.kit.compiler.backend.molkir.Register as MolkiRegister

class VirtualRegisterTable {
    val map: MutableMap<Node, MolkiRegister> = mutableMapOf()
    private var nextRegisterId: Int = 0

    /**
     *
     */
    fun getOrCreateRegisterFor(node: Node): MolkiRegister {
        return map[node] ?: newRegisterFor(node)
    }

    fun setRegisterFor(node: Node, register: MolkiRegister) {
        if (map[node] == null) {
            map[node] = register
        } else {
            error("invalid call to setRegisterFor: node $node already has a register")
        }
    }


    private fun newRegisterFor(node: Node): MolkiRegister {
        if (map[node] != null) error("invalid call to newRegisterFor node $node. The node already has a register")
        val registerId = nextRegisterId++
        val width = Width.fromByteSize(node.mode.sizeBytes)
            ?: error("cannot infer register width from mode \"${node.mode.name}\" of node $node")
        val molkiRegister = MolkiRegister(RegisterId(registerId), width)
        map[node] = molkiRegister
        return molkiRegister
    }

    fun newRegister(width: Width): MolkiRegister {
        val registerId = nextRegisterId++
        return MolkiRegister(RegisterId(registerId), width)
    }

    fun getRegisterFor(node: Node): MolkiRegister? = map[node]
}


data class ValueHolder<T>(private var value: T? = null) {

    fun set(v: T) {
        check(value == null) { "cannot set value twice" }
        value = v
    }

    fun get(): T = value ?: error("value not initialized")

    fun reset() {
        value = null
    }
}

data class MatchResult(
    val replacement: CodeGenIR,
    val instructions: List<Instruction>,
    val cost: Int,
) {

}

class ReplacerScope(private val registerTable: VirtualRegisterTable) {
    fun newRegister(width: Width): MolkiRegister = registerTable.newRegister(width)
}

// does not work yet because it does not support the needed dynamic programming approach yet
class Rule(
    private val values: List<ValueHolder<*>>,
    private val matcher: CodeGenIR,
    private val replacement: ReplacerScope.() -> Pair<CodeGenIR, List<Instruction>>,
) {
    fun match(node: CodeGenIR, virtualRegisterTable: VirtualRegisterTable): MatchResult? {
        values.forEach { it.reset() }

        val matches = matcher.matches(node)
        if (!matches) {
            return null
        }
        val scope = ReplacerScope(virtualRegisterTable)
        val (replacement, instructions) = scope.replacement()


        return MatchResult(
            replacement,
            instructions,
            matcher.cost(),
        )
    }
}

class RuleBuilderScope {
    private val values = mutableListOf<ValueHolder<*>>()
    private var matchPattern: CodeGenIR? = null
    private var replacement: (ReplacerScope.() -> Pair<CodeGenIR, List<Instruction>>)? = null

    fun <T> value(): ValueHolder<T> {
        return ValueHolder<T>().also {
            values.add(it)
        }
    }

    fun match(pattern: CodeGenIR) {
        matchPattern = pattern
    }

    fun replaceWith(block: ReplacerScope.() -> Pair<CodeGenIR, List<Instruction>>) {
        replacement = block
    }

    fun build(): Rule = Rule(values, matchPattern!!, replacement!!)
}

fun rule(block: RuleBuilderScope.() -> Unit): Rule {
    val scope = RuleBuilderScope()
    scope.block()
    return scope.build()
}
