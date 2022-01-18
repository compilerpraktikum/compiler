package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Memory
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.Target
import firm.TargetValue
import firm.nodes.Address
import firm.nodes.Node
import kotlin.math.max

sealed class CodeGenIR {
    abstract fun match(node: CodeGenIR): Boolean
    abstract fun cost(): Int

    //TODO make binops
    data class BinOP(val operation: BinOpENUM, val left: CodeGenIR, val right: CodeGenIR) :
        CodeGenIR() {
        override fun match(node: CodeGenIR): Boolean {
            if (node is CodeGenIR.BinOP && node.operation == operation) {
                return left.match(node.left) && right.match(node.right)
            }
            return false
        }

        override fun cost(): Int = left.cost() + right.cost() + 1
    }

    data class Indirection(val addr: CodeGenIR) : CodeGenIR() {
        override fun match(node: CodeGenIR): Boolean {
            if (node is Indirection) {
                return addr.match(node.addr)
            }
            return false
        }

        override fun cost(): Int = 1 + addr.cost()

    }

    data class MemoryAddress(val reg: Memory) : CodeGenIR() {
        override fun match(node: CodeGenIR): Boolean {
            TODO("Not yet implemented")
        }

        override fun cost(): Int {
            TODO("Not yet implemented")
        }
    }

    data class Cond(val cond: CodeGenIR, val ifTrue: CodeGenIR, val ifFalse: CodeGenIR) : CodeGenIR() {
        override fun match(node: CodeGenIR): Boolean =
            if (node is Cond) {
                cond.match(node.cond) && ifTrue.match(node.ifTrue) && ifFalse.match(node.ifFalse)
            } else {
                false
            }

        override fun cost(): Int = 1 + cond.cost() + max(ifTrue.cost(), ifFalse.cost())
    }

    data class Assign(val lhs: CodeGenIR, val rhs: CodeGenIR) : CodeGenIR() {
        override fun match(node: CodeGenIR): Boolean =
            if (node is Assign) {
                lhs.match(node.lhs) && rhs.match(node.rhs)
            } else {
                false
            }

        override fun cost(): Int = lhs.cost() + rhs.cost() + 1
    }

    data class RegisterRef(val reg: ValueHolder<Register>) : CodeGenIR() {
        constructor(reg: Register) : this(ValueHolder(reg))

        override fun match(node: CodeGenIR): Boolean {
            if (node is CodeGenIR.RegisterRef) {
                reg.set(node.reg.get())
                return true
            }
            return false
        }

        override fun cost(): Int = 1
    }

    data class Const(val const: ValueHolder<String>) : CodeGenIR() {
        constructor(const: String) : this(ValueHolder(const))

        override fun match(node: CodeGenIR): Boolean {
            if (node is Const) {
                const.set(node.const.get())
                return true
            }
            return false
        }

        override fun cost(): Int = 1
    }

    data class Call(val name: Address, val arguments: List<CodeGenIR>) : CodeGenIR() {
        override fun match(node: CodeGenIR): Boolean =
            if (node is Call && node.name == name && node.arguments.size == arguments.size) {
                arguments.zip(node.arguments).all { (self, other) -> self.match(other) }
            } else {
                false
            }

        override fun cost(): Int = 1 + arguments.sumOf { it.cost() }

        fun getName(): String = name.entity.ldName!!

    }
}

enum class BinOpENUM {
    ADD, AND, CMP, EOR, MUL, MULH, OR, SUB, SHL, SHR, SHRS
}
