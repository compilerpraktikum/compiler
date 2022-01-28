package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Constant
import edu.kit.compiler.backend.molkir.Memory
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.utils.MatchPattern
import edu.kit.compiler.utils.ValueHolder
import firm.Mode
import firm.Relation
import firm.nodes.Address
import firm.nodes.Block

fun defaultReplacement(node: CodeGenIR) = Replacement(
    node = node,
    instructions = instructionListOf(),
    cost = 0,
)

sealed class CodeGenIR : MatchPattern<CodeGenIR> {
    var replacement: Replacement? = null

    abstract override fun matches(target: CodeGenIR): Boolean

    /**
     * Sequential Execution of two nodes, representing the value of <pre>second</pre>
     */
    class Seq
    private constructor(private val valueHolder: ValueHolder<CodeGenIR>, private val execHolder: ValueHolder<CodeGenIR>) : CodeGenIR() {
        constructor(value: CodeGenIR, exec: CodeGenIR) : this(
            ValueHolder.Constant(value),
            ValueHolder.Constant(exec),
        )
        constructor(value: ValueHolder.Variable<CodeGenIR>, exec: ValueHolder.Variable<CodeGenIR>) : this(
            value as ValueHolder<CodeGenIR>,
            exec as ValueHolder<CodeGenIR>,
        )

        val value
            get() = valueHolder.get().also {
                check(valueHolder is ValueHolder.Constant)
            }
        val exec
            get() = execHolder.get().also {
                check(execHolder is ValueHolder.Constant)
            }

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Seq)
                return false

            check(valueHolder is ValueHolder.Variable)
            check(execHolder is ValueHolder.Variable)

            valueHolder.set(target.value)
            execHolder.set(target.exec)

            return true
        }
    }

    class RegisterRef
    private constructor(
        private val registerHolder: ValueHolder<Register>,
        private val replacementHolder: ValueHolder.Variable<Replacement>?,
    ) : CodeGenIR() {
        constructor(reg: Register) : this(ValueHolder.Constant(reg), null)
        constructor(
            value: ValueHolder.Variable<Register>,
            nodeHolder: ValueHolder.Variable<Replacement>? = null
        ) : this(value as ValueHolder<Register>, nodeHolder)

        val register: Register
            get() = registerHolder.get().also {
                check(registerHolder is ValueHolder.Constant)
            }

        override fun matches(target: CodeGenIR): Boolean {
            check(registerHolder is ValueHolder.Variable)
            if (target is RegisterRef) {
                replacementHolder?.set(defaultReplacement(target))
                registerHolder.set(target.register)
                return true
            } else {
                val replacement = target.replacement
                if (replacement != null && replacement.node is RegisterRef) {
                    replacementHolder?.set(replacement)
                    registerHolder.set(replacement.node.register)
                    return true
                } else {
                    return false
                }
            }
        }
    }

    data class Const(private val valueHolder: ValueHolder<Value>) : CodeGenIR() {
        data class Value(
            val value: String,
            val mode: Width,
        ) {
            fun toMolki() = Constant(value, mode)
        }

        constructor(const: String, mode: Width) : this(ValueHolder.Constant(Value(const, mode)))

        val value: Value
            get() = valueHolder.get().also {
                check(valueHolder is ValueHolder.Constant)
            }

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Const)
                return false

            when (valueHolder) {
                is ValueHolder.Constant -> {
                    val expected = valueHolder.get()
                    val actual = target.valueHolder.get()
                    return expected.mode == actual.mode && expected.value == actual.value
                }
                is ValueHolder.Variable -> {
                    valueHolder.set(target.value)
                    return true
                }
            }
        }
    }

    // TODO make binops
    data class BinOP(val operation: BinOpENUM, val left: CodeGenIR, val right: CodeGenIR) :
        CodeGenIR() {
        override fun matches(target: CodeGenIR): Boolean {
            if (target !is BinOP || target.operation != operation)
                return false

            val matchesInOrder = left.matches(target.left) && right.matches(target.right)
            if (matchesInOrder)
                return true

            return if (operation.isCommutative) {
                left.matches(target.right) && right.matches(target.left)
            } else {
                false
            }
        }
    }

    // TODO maybe commutative? is this needed?
    data class Compare(val relation: Relation, val left: CodeGenIR, val right: CodeGenIR) : CodeGenIR() {
        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Compare || target.relation != relation)
                return false

            return left.matches(target.left) && right.matches(target.right)
        }
    }

    data class Return(val returnValue: CodeGenIR) : CodeGenIR() {
        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Return)
                return false

            return returnValue.matches(target.returnValue)
        }
    }

    data class Indirection(val address: CodeGenIR) : CodeGenIR() {
        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Indirection)
                return false

            return address.matches(target.address)
        }
    }

    class MemoryAddress
    private constructor(private val memoryHolder: ValueHolder<Memory>) : CodeGenIR() {
        constructor(mem: Memory) : this(ValueHolder.Constant(mem))
        constructor(value: ValueHolder.Variable<Memory>) : this(value as ValueHolder<Memory>)

        val memory: Memory
            get() = memoryHolder.get().also {
                check(memoryHolder is ValueHolder.Constant)
            }

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is MemoryAddress)
                return false

            check(memoryHolder is ValueHolder.Variable)
            memoryHolder.set(target.memoryHolder.get())
            return true
        }
    }

    data class Cond(val cond: CodeGenIR, val ifTrue: CodeGenIR, val ifFalse: CodeGenIR) : CodeGenIR() {
        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Cond)
                return false

            return cond.matches(target.cond) && ifTrue.matches(target.ifTrue) && ifFalse.matches(target.ifFalse)
        }
    }

    class Jmp
    private constructor(private val blockHolder: ValueHolder<Block>) : CodeGenIR() {
        constructor(block: Block) : this(ValueHolder.Constant(block))
        constructor(value: ValueHolder.Variable<Block>) : this(value as ValueHolder<Block>)

        val block: Block
            get() = blockHolder.get().also {
                check(blockHolder is ValueHolder.Constant)
            }

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Jmp)
                return false

            check(blockHolder is ValueHolder.Variable)
            blockHolder.set(target.block)
            return true
        }
    }

    data class Assign(val lhs: CodeGenIR, val rhs: CodeGenIR) : CodeGenIR() {
        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Assign)
                return false

            return lhs.matches(target.lhs) && rhs.matches(target.rhs)
        }
    }

    data class Div(val left: CodeGenIR, val right: CodeGenIR) : CodeGenIR() {
        override fun matches(target: CodeGenIR): Boolean {
            TODO("Not yet implemented")
        }
    }

    data class Mod(val right: CodeGenIR, val left: CodeGenIR) : CodeGenIR() {
        override fun matches(target: CodeGenIR): Boolean {
            TODO("Not yet implemented")
        }
    }

    class Call
    private constructor(
        private val addressHolder: ValueHolder<Address>,
        private val argumentsHolder: ValueHolder<List<CodeGenIR>>,
    ) : CodeGenIR() {
        constructor(address: Address, arguments: List<CodeGenIR>) : this(
            ValueHolder.Constant(address),
            ValueHolder.Constant(arguments)
        )
        constructor(address: ValueHolder<Address>, arguments: ValueHolder.Variable<List<CodeGenIR>>) : this(
            address,
            arguments as ValueHolder<List<CodeGenIR>>,
        )

        val address: Address
            get() = addressHolder.get().also {
                check(addressHolder is ValueHolder.Constant)
            }
        val arguments: List<CodeGenIR>
            get() = argumentsHolder.get().also {
                check(argumentsHolder is ValueHolder.Constant)
            }

        val linkerName: String
            get() = address.entity.ldName!!

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Call)
                return false

            when (addressHolder) {
                is ValueHolder.Constant -> {
                    if (addressHolder.get() != target.address)
                        return false
                }
                is ValueHolder.Variable -> {
                    addressHolder.set(target.address)
                }
            }

            check(argumentsHolder is ValueHolder.Variable)
            argumentsHolder.set(target.arguments)

            return true
        }
    }

    data class UnaryOP(val op: UnaryOpENUM, val value: CodeGenIR) : CodeGenIR() {
        override fun matches(target: CodeGenIR): Boolean {
            TODO("Not yet implemented")
        }
    }

    data class Conv(val fromMode: Mode, val toMode: Mode, val opTree: CodeGenIR) : CodeGenIR() {
        override fun matches(target: CodeGenIR): Boolean {
            TODO("Not yet implemented")
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
            else -> TODO("Not yet implemented")
        }

    fun walkDepthFirst(walker: (CodeGenIR) -> Unit) {
        when (this) {
            is Assign -> {
                lhs.walkDepthFirst(walker)
                rhs.walkDepthFirst(walker)
            }
            is BinOP -> {
                left.walkDepthFirst(walker)
                right.walkDepthFirst(walker)
            }
            is Call -> {
                arguments.forEach {
                    it.walkDepthFirst(walker)
                }
            }
            is Cond -> {
                cond.walkDepthFirst(walker)
                ifTrue.walkDepthFirst(walker)
                ifFalse.walkDepthFirst(walker)
            }
            is Const -> {}
            is Indirection -> {
                address.walkDepthFirst(walker)
            }
            is MemoryAddress -> {}
            is RegisterRef -> {}
            is Compare -> {
                left.walkDepthFirst(walker)
                right.walkDepthFirst(walker)
            }
            is Conv -> {
                opTree.walkDepthFirst(walker)
            }
            is Return -> {
                returnValue.walkDepthFirst(walker)
            }
            is Seq -> {
                // TODO is this the correct order?
                value.walkDepthFirst(walker)
                exec.walkDepthFirst(walker)
            }
            is Div -> TODO()
            is Jmp -> TODO()
            is Mod -> TODO()
            is UnaryOP -> TODO()

            // utility matching nodes should not occur in the real graph
            is Noop,
            is SaveNodeTo -> error("invalid CodeGenIR graph")
        }

        walker(this)
    }
}

class Noop : CodeGenIR() {
    override fun matches(target: CodeGenIR): Boolean {
        error("not a valid match pattern")
    }
}

class SaveNodeTo(private val storage: ValueHolder.Variable<CodeGenIR>, childFn: () -> CodeGenIR) : CodeGenIR() {
    private val child by lazy { childFn() }

    override fun matches(target: CodeGenIR): Boolean {
        storage.set(target)
        return child.matches(target)
    }
}

class GraphVizBuilder {
    private var nextId = 0
    fun freshId() = nextId++

    private val stringBuilder = StringBuilder()

    fun appendLine(line: String) {
        stringBuilder.appendLine(line)
    }

    fun build(): String {
        return stringBuilder.toString().also {
            stringBuilder.clear()
        }
    }
}

fun CodeGenIR.toGraphViz(builder: GraphVizBuilder = GraphVizBuilder()): Int = with(builder) {
    internalToGraphViz()
}

enum class BinOpENUM(val isCommutative: Boolean) {
    ADD(true),
    AND(true),
    EOR(true),
    MUL(true),
    MULH(true),
    OR(true),
    SUB(false),
    SHL(false),
    SHR(false),
    SHRS(false),
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
        val addr = node.address.accept(this)
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
