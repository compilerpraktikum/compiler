package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Memory
import edu.kit.compiler.backend.molkir.Register
import firm.Mode
import firm.Relation
import firm.nodes.Address
import kotlin.math.max

class GraphPrinter() {
    var id = 0

    fun freshId() = id++
}

sealed class CodeGenIR {
    abstract fun matches(node: CodeGenIR): Boolean
    abstract fun cost(): Int
    abstract fun toGraphviz(parent: Int, graphPrinter: GraphPrinter) : String

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

        override fun toGraphviz(parent: Int, graphPrinter: GraphPrinter) : String {
            val id = graphPrinter.freshId()
            return buildString {
                appendLine("$id[label=\"BinOP $operation\"];")
                appendLine("$parent -> $id;");
                appendLine(left.toGraphviz(id, graphPrinter))
                appendLine(right.toGraphviz(id, graphPrinter))
            }
        }
    }

    data class Compare(val relation: Relation, val left: CodeGenIR, val right: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean {
            if (node is CodeGenIR.Compare && node.relation == relation) {
                return left.matches(node.left) && right.matches(node.right)
            }
            return false
        }

        override fun cost(): Int = left.cost() + right.cost() + 1

        override fun toGraphviz(parent: Int, graphPrinter: GraphPrinter) : String {
            val id = graphPrinter.freshId()
            return buildString {
                appendLine("$id[label=\"Relation $relation\"];")
                appendLine("$parent -> $id;");
                appendLine(left.toGraphviz(id, graphPrinter))
                appendLine(right.toGraphviz(id, graphPrinter))
            }
        }
    }

    data class Return(val returnValue: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean {
            TODO("Not yet implemented")
        }

        override fun cost(): Int {
            TODO("Not yet implemented")
        }

        override fun toGraphviz(parent: Int, graphPrinter: GraphPrinter) : String {
            val id = graphPrinter.freshId()
            return buildString {
                appendLine("$id[label=\"Return\"];")
                appendLine("$parent -> $id;");
                appendLine(returnValue.toGraphviz(id, graphPrinter))
            }
        }

    }

    data class Indirection(val addr: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean {
            if (node is Indirection) {
                return addr.matches(node.addr)
            }
            return false
        }

        override fun cost(): Int = 1 + addr.cost()

        override fun toGraphviz(parent: Int, graphPrinter: GraphPrinter) : String {
            val id = graphPrinter.freshId()
            return buildString {
                appendLine("$id[label=\"Indirection\"];")
                appendLine("$parent -> $id;");
                appendLine(addr.toGraphviz(id, graphPrinter))
            }
        }

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

        override fun toGraphviz(parent: Int, graphPrinter: GraphPrinter) : String {
            val id = graphPrinter.freshId()
            return buildString {
                appendLine("$id[label=\"MemoryAddress ${mem.get()}\"];")
                appendLine("$parent -> $id;");
            }
        }
    }

    data class Cond(val cond: CodeGenIR, val ifTrue: CodeGenIR, val ifFalse: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean =
            if (node is Cond) {
                cond.matches(node.cond) && ifTrue.matches(node.ifTrue) && ifFalse.matches(node.ifFalse)
            } else {
                false
            }

        override fun cost(): Int = 1 + cond.cost() + max(ifTrue.cost(), ifFalse.cost())
        override fun toGraphviz(parent: Int, graphPrinter: GraphPrinter): String {
            TODO("Not yet implemented")
        }

    }

    data class Assign(val lhs: CodeGenIR, val rhs: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean =
            if (node is Assign) {
                lhs.matches(node.lhs) && rhs.matches(node.rhs)
            } else {
                false
            }

        override fun cost(): Int = lhs.cost() + rhs.cost() + 1

        override fun toGraphviz(parent: Int, graphPrinter: GraphPrinter) : String {
            val id = graphPrinter.freshId()
            return buildString {
                appendLine("$id[label=\"Assign\"];")
                appendLine("$parent -> $id;");
                appendLine(lhs.toGraphviz(id, graphPrinter))
                appendLine(rhs.toGraphviz(id, graphPrinter))
            }
        }
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

        override fun toGraphviz(parent: Int, graphPrinter: GraphPrinter) : String {
            val id = graphPrinter.freshId()
            return buildString {
                appendLine("$id[label=\"RegisterRef ${reg.get().id.value} ${reg.get().width}\"];")
                appendLine("$parent -> $id;");
            }
        }
    }

    data class Const(val const: ValueHolder<String>) : CodeGenIR() {
        constructor(const: String) : this(ValueHolder(const))

        override fun matches(node: CodeGenIR): Boolean {
            if (node is Const) {
                println("match ${node.const} with $const")
                const.set(node.const.get())
                return true
            }
            return false
        }

        override fun cost(): Int = 1

        override fun toGraphviz(parent: Int, graphPrinter: GraphPrinter) : String {
            val id = graphPrinter.freshId()
            return buildString {
                appendLine("$id[label=\"Const ${const.get()}\"];")
                appendLine("$parent -> $id;");
            }
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

        override fun toGraphviz(parent: Int, graphPrinter: GraphPrinter) : String {
            val id = graphPrinter.freshId()
            return buildString {
                appendLine("$id[label=\"Call &${name.entity}\"];")
                appendLine("$parent -> $id;");
                arguments.forEach {
                    appendLine(it.toGraphviz(id, graphPrinter))
                }
            }
        }

    }

    data class Conv(val fromMode: Mode, val toMode: Mode, val opTree: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean {
            TODO("Not yet implemented")
        }

        override fun cost(): Int {
            TODO("Not yet implemented")
        }

        override fun toGraphviz(parent: Int, graphPrinter: GraphPrinter): String {
            TODO("Not yet implemented")
        }
    }

    /**
     * Sequential Execution of two nodes, representing the value of <pre>second</pre>
     */
    data class Seq(val value: CodeGenIR, val exec: CodeGenIR) : CodeGenIR() {
        override fun matches(node: CodeGenIR): Boolean =
            if(node is Seq) {
                value.matches(node.value) && exec.matches(node.exec)
            } else {
                false
            }

        override fun cost(): Int = value.cost() + exec.cost()

        override fun toGraphviz(parent: Int, graphPrinter: GraphPrinter) : String {
            val id = graphPrinter.freshId()
            return buildString {
                appendLine("$id[label=\"Seq\"];")
                appendLine("$parent -> $id;");
                appendLine(value.toGraphviz(id, graphPrinter))
                appendLine(exec.toGraphviz(id, graphPrinter))
            }
        }

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
        }
}

enum class BinOpENUM {
    ADD, AND, EOR, MUL, MULH, OR, SUB, SHL, SHR, SHRS
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

    fun transform(node: CodeGenIR.Seq, first: T, second: T): T
}
