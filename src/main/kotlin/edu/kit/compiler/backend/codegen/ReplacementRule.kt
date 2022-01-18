package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Constant
import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.Memory
import edu.kit.compiler.backend.molkir.MolkIR
import edu.kit.compiler.backend.molkir.Register as MolkiRegister
import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.Target
import edu.kit.compiler.backend.molkir.Width
import firm.nodes.Node

class VirtualRegisterTable {
    val map: MutableMap<CodeGenIR, MolkiRegister> = mutableMapOf()
    private var nextRegisterId: Int = 0

    fun newRegisterFor(node: CodeGenIR, width: Width): MolkiRegister {
        val registerId = nextRegisterId++
        val register = MolkiRegister(RegisterId(registerId), width)
        map[node] = register
        return register
    }

    fun newRegisterFor(node: Node): MolkiRegister {
        val registerId = nextRegisterId++
        val width = Width.fromByteSize(node.mode.sizeBytes)
            ?: error("cannot infer register width from mode \"${node.mode.name}\"")
        return MolkiRegister(RegisterId(registerId), width)
    }

    fun putNode(node: CodeGenIR, register: MolkiRegister) {
        map[node] = register
    }


    fun getRegisterFor(node: CodeGenIR): MolkiRegister? = map[node]
}


val rules = listOf(
    // Rule 5: movq a(R_j), R_i -> R_k
    rule {
        val constValue = value<String>()
        val register = value<MolkiRegister>()
        val resRegister = value<MolkiRegister>()

        // ADD match
        match(
            CodeGenIR.Indirection(
                CodeGenIR.BinOP(
                    BinOpENUM.ADD,
                    CodeGenIR.Const(constValue),
                    CodeGenIR.RegisterRef(register),
                )
            )
        )

        replaceWith {
//            val newRegister = newRegister()
            CodeGenIR.RegisterRef(resRegister) to listOf(
                Instruction.movq(
                    Memory.constantOffset(const = constValue.get(), base = register.get()),
                    resRegister.get()
                )
            )
        }
    }
)

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
)

class ReplacerScope {
    fun newRegister(): MolkiRegister = TODO()
}

// does not work yet because it does not support the needed dynamic programming approach yet
class Rule(
    private val values: List<ValueHolder<*>>,
    private val matcher: CodeGenIR,
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
