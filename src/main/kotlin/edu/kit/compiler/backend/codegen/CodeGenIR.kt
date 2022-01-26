package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Memory
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.Width
import firm.Mode
import firm.Relation
import firm.nodes.Address
import firm.nodes.Block
import kotlin.math.max

class GraphPrinter() {
    var id = 0

    fun freshId() = id++
}

sealed class CodeGenIR {
    abstract fun matches(node: CodeGenIR): Boolean
    abstract fun cost(): Int

    //TODO make binops
    data class BinOP(val operation: BinOpENUM, val left: CodeGenIR, val right: CodeGenIR) :
        CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean {
            if (node is CodeGenIR.BinOP && node.operation == operation) {
                return left.matches(node.left) && right.matches(node.right)
            }
            return false
        }

        override fun cost(): Int = left.cost() + right.cost() + 1

    }

    data class Compare(val relation: Relation, val left: CodeGenIR, val right: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean {
            if (node is CodeGenIR.Compare && node.relation == relation) {
                return left.matches(node.left) && right.matches(node.right)
            }
            return false
        }

        override fun cost(): Int = left.cost() + right.cost() + 1

    }

    data class Return(val returnValue: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean =
            if (node is Return) {
                returnValue.matches(node.returnValue)
            } else {
                false
            }

        override fun cost() = 1 + returnValue.cost()


    }

    data class Indirection(val addr: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean {
            if (node is Indirection) {
                return addr.matches(node.addr)
            }
            return false
        }

        override fun cost(): Int = 1 + addr.cost()


    }

    data class MemoryAddress(val mem: ValueHolder<Memory>) : CodeGenIR() {
        constructor(mem: Memory) : this(ValueHolder(mem))

        override fun matches(node: CodeGenIR): Boolean {
            return if (node is MemoryAddress) {
                mem.set(node.mem.get())
                true
            } else {
                false
            }
        }

        override fun cost(): Int =
            3

    }

    data class Cond(val cond: CodeGenIR, val ifTrue: CodeGenIR, val ifFalse: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean =
            if (node is Cond) {
                cond.matches(node.cond) && ifTrue.matches(node.ifTrue) && ifFalse.matches(node.ifFalse)
            } else {
                false
            }

        override fun cost(): Int = 1 + cond.cost() + max(ifTrue.cost(), ifFalse.cost())
    }

    data class Jmp(val block: ValueHolder<Block>) : CodeGenIR() {
        constructor(block: Block) : this(ValueHolder(block))
        override fun matches(node: CodeGenIR) = if (node is Jmp) {
            block.set(node.block.get())
            true
        } else {
            false
        }

        override fun cost() = 1
    }

    data class Assign(val lhs: CodeGenIR, val rhs: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean =
            if (node is Assign) {
                lhs.matches(node.lhs) && rhs.matches(node.rhs)
            } else {
                false
            }

        override fun cost(): Int = lhs.cost() + rhs.cost() + 1

    }

    data class RegisterRef(val reg: ValueHolder<Register>) : CodeGenIR() {
        constructor(reg: Register) : this(ValueHolder(reg))

        override fun matches(node: CodeGenIR): Boolean {
            if (node is CodeGenIR.RegisterRef) {
                reg.set(node.reg.get())
                return true
            }
            return false
        }

        override fun cost(): Int = 1

    }

    data class Const(val const: ValueHolder<String>, val width: ValueHolder<Width>) : CodeGenIR() {
        constructor(const: String, mode: Width) : this(ValueHolder(const), ValueHolder(mode))

        override fun matches(node: CodeGenIR): Boolean {
            if (node is Const) {
                const.set(node.const.get())
                width.set(node.width.get())
                return true
            }
            return false
        }

        override fun cost(): Int = 1
    }

    data class Div(val left: CodeGenIR, val right: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean {
            TODO("Not yet implemented")
        }

        override fun cost(): Int {
            TODO("Not yet implemented")
        }

    }

    data class Call(val name: Address, val arguments: List<CodeGenIR>) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean =
            if (node is Call && node.name == name && node.arguments.size == arguments.size) {
                arguments.zip(node.arguments).all { (self, other) -> self.matches(other) }
            } else {
                false
            }

        override fun cost(): Int = 1 + arguments.sumOf { it.cost() }

        fun getName(): String = name.entity.ldName!!

    }

    data class UnaryOP(val op: UnaryOpENUM, val value: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean {
            TODO("Not yet implemented")
        }

        override fun cost(): Int {
            TODO("Not yet implemented")
        }
    }

    data class Mod(val right: CodeGenIR, val left: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean {
            TODO("Not yet implemented")
        }

        override fun cost(): Int {
            TODO("Not yet implemented")
        }

    }

    data class Conv(val fromMode: Mode, val toMode: Mode, val opTree: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean {
            TODO("Not yet implemented")
        }

        override fun cost(): Int {
            TODO("Not yet implemented")
        }

    }

    /**
     * Sequential Execution of two nodes, representing the value of <pre>second</pre>
     */
    data class Seq(val value: CodeGenIR, val exec: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean =
            if (node is Seq) {
                value.matches(node.value) && exec.matches(node.exec)
            } else {
                false
            }

        override fun cost(): Int = value.cost() + exec.cost()
    }

    fun <T> accept(visitor: CodeGenIRVisitor<T>): T =
        when (this) {
            is Assign -> visitor.visit(this)
            is BinOP -> visitor.visit(this)
            is Call -> visitor.visit(this)
            is Cond -> visitor.visit(this)
            is Const -> visitor.visit(this)
            is Indirection -> visitor.visit(this)
            is MemoryAddress -> visitor.visit(this)
            is RegisterRef -> visitor.visit(this)
            is Compare -> visitor.visit(this)
            is Conv -> visitor.visit(this)
            is Return -> visitor.visit(this)
            is Seq -> visitor.visit(this)
            else -> TODO("Not yet implemented")
        }
}

fun CodeGenIR.toGraphviz(parent: Int, graphPrinter: GraphPrinter): String {
    val id = graphPrinter.freshId()
    return when (this) {
        is CodeGenIR.Assign -> buildString {
            appendLine("$id[label=\"Assign\"];")
            appendLine("$parent -> $id;");
            appendLine(lhs.toGraphviz(id, graphPrinter))
            appendLine(rhs.toGraphviz(id, graphPrinter))
        }
        is CodeGenIR.BinOP -> buildString {
            appendLine("$id[label=\"BinOP $operation\"];")
            appendLine("$parent -> $id;");
            appendLine(left.toGraphviz(id, graphPrinter))
            appendLine(right.toGraphviz(id, graphPrinter))
        }
        is CodeGenIR.Call -> buildString {
            appendLine("$id[label=\"Call &${name.entity}\"];")
            appendLine("$parent -> $id;");
            arguments.forEach {
                appendLine(it.toGraphviz(id, graphPrinter))
            }
        }
        is CodeGenIR.Compare -> buildString {
            appendLine("$id[label=\"Relation $relation\"];")
            appendLine("$parent -> $id;");
            appendLine(left.toGraphviz(id, graphPrinter))
            appendLine(right.toGraphviz(id, graphPrinter))
        }
        is CodeGenIR.Cond -> TODO()
        is CodeGenIR.Const -> buildString {
            appendLine("$id[label=\"Const ${const.get()}\"];")
            appendLine("$parent -> $id;");
        }
        is CodeGenIR.Conv -> buildString {
            appendLine("$id[label=\"Conv $fromMode => $toMode\"];")
            appendLine("$parent -> $id;");
            appendLine(opTree.toGraphviz(id, graphPrinter))
        }
        is CodeGenIR.Indirection -> buildString {
            appendLine("$id[label=\"Indirection\"];")
            appendLine("$parent -> $id;");
            appendLine(addr.toGraphviz(id, graphPrinter))
        }
        is CodeGenIR.MemoryAddress -> buildString {
            appendLine("$id[label=\"MemoryAddress ${mem.get()}\"];")
            appendLine("$parent -> $id;");
        }
        is CodeGenIR.RegisterRef -> {
            val valueId = graphPrinter.freshId()
            val widthId = graphPrinter.freshId()
            return buildString {
                appendLine("$id[label=\"Ref\"];")
                appendLine("$valueId[label=\"@${reg.get().id.value}\"];")
                appendLine("$widthId[label=\"${reg.get().width}\"];")
                appendLine("$id -> $valueId");
                appendLine("$id -> $widthId");
                appendLine("$parent -> $id;");
            }
        }
        is CodeGenIR.Return -> buildString {
            appendLine("$id[label=\"Return\"];")
            appendLine("$parent -> $id;");
            appendLine(returnValue.toGraphviz(id, graphPrinter))
        }
        is CodeGenIR.Seq ->
            buildString {
                appendLine("$id[label=\"Seq\"];")
                appendLine("$parent -> $id;");
                appendLine(value.toGraphviz(id, graphPrinter))
                appendLine(exec.toGraphviz(id, graphPrinter))
            }
        else -> TODO("Not yet implemented")
    }
}

enum class BinOpENUM {
    ADD, AND, EOR, MUL, MULH, OR, SUB, SHL, SHR, SHRS
}

enum class UnaryOpENUM {
    MINUS, NOT
}

interface CodeGenIRVisitor<T> {
    fun visit(node: CodeGenIR.BinOP): T {
        val left: T = node.left.accept(this)
        val right: T = node.right.accept(this)
        return transform(node, left, right)
    }

    fun transform(node: CodeGenIR.BinOP, left: T, right: T): T

    fun visit(node: CodeGenIR.Indirection): T {
        val addr = node.addr.accept(this)
        return transform(node, addr)
    }

    fun transform(node: CodeGenIR.Indirection, addr: T): T

    fun visit(node: CodeGenIR.MemoryAddress): T = transform(node)
    fun transform(node: CodeGenIR.MemoryAddress): T

    fun visit(node: CodeGenIR.Cond): T {
        val cond = node.cond.accept(this)
        val ifTrue = node.ifTrue.accept(this)
        val ifFalse = node.ifFalse.accept(this)
        return transform(node, cond, ifTrue, ifFalse)
    }

    fun transform(node: CodeGenIR.Cond, cond: T, ifTrue: T, ifFalse: T): T

    fun visit(node: CodeGenIR.Assign): T {
        val lhs = node.lhs.accept(this)
        val rhs = node.rhs.accept(this)
        return transform(node, lhs, rhs)
    }

    fun transform(node: CodeGenIR.Assign, lhs: T, rhs: T): T

    fun visit(node: CodeGenIR.RegisterRef): T = transform(node)
    fun transform(node: CodeGenIR.RegisterRef): T


    fun visit(node: CodeGenIR.Const): T = transform(node)
    fun transform(node: CodeGenIR.Const): T


    fun visit(node: CodeGenIR.Call): T {
        val arguments = node.arguments.map { it.accept(this) }
        return transform(node, arguments)
    }

    fun transform(node: CodeGenIR.Call, arguments: List<T>): T

    fun visit(node: CodeGenIR.Compare): T {
        val left = node.left.accept(this)
        val right = node.right.accept(this)
        return transform(node, left, right)
    }

    fun transform(node: CodeGenIR.Compare, left: T, right: T): T

    fun visit(node: CodeGenIR.Conv): T {
        val opTree = node.opTree.accept(this)
        return transform(node, opTree)
    }

    fun transform(node: CodeGenIR.Conv, opTree: T): T

    fun visit(node: CodeGenIR.Return): T {
        val returnValue = node.returnValue.accept(this)
        return transform(node, returnValue)
    }

    fun transform(node: CodeGenIR.Return, returnValue: T): T
    fun visit(node: CodeGenIR.Seq): T {
        val first = node.value.accept(this)
        val second = node.exec.accept(this)
        return transform(node, first, second)
    }

    fun transform(node: CodeGenIR.Seq, value: T, exec: T): T
}
