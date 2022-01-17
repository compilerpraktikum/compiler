package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.MolkIR
import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.Target
import edu.kit.compiler.backend.molkir.Target.InputOutputTarget.Constant
import edu.kit.compiler.backend.molkir.Target.InputOutputTarget.Register
import edu.kit.compiler.backend.molkir.Width
import firm.nodes.Node

class VirtualRegisterTable {
    val map: MutableMap<CodeGenIR, Register> = mutableMapOf()
    private var nextRegisterId: Int = 0

    fun newRegisterFor(node: CodeGenIR, width: Width): Register {
        val registerId = nextRegisterId++
        val register = Register(RegisterId(registerId), width)
        map[node] = register
        return register
    }

    fun newRegisterFor(node: Node): Register {
        val registerId = nextRegisterId++
        val width = Width.fromByteSize(node.mode.sizeBytes)
            ?: error("cannot infer register width from mode \"${node.mode.name}\"")
        return Register(RegisterId(registerId), width)
    }

    fun putNode(node: CodeGenIR, register: Register) {
        map[node] = register
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

sealed class MatchIR {
    abstract fun match(node: CodeGenIR): Boolean
    abstract fun cost(): Int

    class Const(
        private val value: ValueHolder<Int>
    ) : MatchIR() {
        override fun match(node: CodeGenIR): Boolean {
            if (node is CodeGenIR.Const) {
                value.set(node.const.toInt())
                return true
            }
            return false
        }

        override fun cost(): Int = 1
    }

    class Register(
        private val id: ValueHolder<Target.InputOutputTarget.Register>
    ) : MatchIR() {
        override fun match(node: CodeGenIR): Boolean {
            if (node is CodeGenIR.RegisterRef) {
                id.set(node.reg)
                return true
            }
            return false
        }

        override fun cost(): Int = 1
    }

    class Load(
        private val address: MatchIR
    ) : MatchIR() {
        override fun match(node: CodeGenIR): Boolean {
            if (node is CodeGenIR.Indirection) {
                return address.match(node.addr)
            }
            return false
        }
        override fun cost(): Int = address.cost() + 42
    }

    class Add(
        private val left: MatchIR,
        private val right: MatchIR,
    ) : MatchIR() {
        override fun match(node: CodeGenIR): Boolean {
            if (node is CodeGenIR.BinOP && node.operation is firm.nodes.Add) {
                return left.match(node.left) && right.match(node.right)
            }
            return false
        }

        override fun cost(): Int = left.cost() + right.cost() + 1
    }
}

val rules = listOf(
    rule {
        val constValue = value<Int>()
        val register = value<Register>()

        match(
            MatchIR.Load(
                MatchIR.Add(
                    MatchIR.Const(constValue),
                    MatchIR.Register(register),
                )
            )
        )

        replaceWith {
            val newRegister = newRegister()
            CodeGenIR.RegisterRef(newRegister) to listOf(
                Instruction.movq(
                    Target.InputOutputTarget.Memory(const = constValue.get(), base = register.get()),
                    newRegister
                )
            )
        }
    }
)

class ValueHolder<T> {
    private var value: T? = null

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
)

class ReplacerScope {
    fun newRegister(): Register = TODO()
}

// does not work yet because it does not support the needed dynamic programming approach yet
class Rule(
    private val values: List<ValueHolder<*>>,
    private val matcher: MatchIR,
    private val replacement: ReplacerScope.() -> Pair<CodeGenIR, List<Instruction>>,
) {
    fun match(node: CodeGenIR): MatchResult? {
        values.forEach { it.reset() }

        val matches = matcher.match(node)
        if (!matches) {
            return null
        }

        val scope = ReplacerScope()
        val result = scope.replacement()
        return MatchResult(
            result.first,
            result.second,
            matcher.cost(),
        )
    }
}

class RuleBuilderScope {
    private val values = mutableListOf<ValueHolder<*>>()
    private var matchPattern: MatchIR? = null
    private var replacement: (ReplacerScope.() -> Pair<CodeGenIR, List<Instruction>>)? = null

    fun <T> value(): ValueHolder<T> {
        return ValueHolder<T>().also {
            values.add(it)
        }
    }

    fun match(pattern: MatchIR) {
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
