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
import firm.nodes.Const
import firm.nodes.Node
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private fun defaultReplacement(node: CodeGenIR) = Replacement(
    node = node,
    instructions = instructionListOf(),
    cost = 0,
)

private fun <T> ValueHolder<T>.getAndAssertConstant() = get().also {
    check(this is ValueHolder.Constant) { "only allowed on real CodeGenIR (forbidden for patterns)" }
}

@OptIn(ExperimentalContracts::class)
private fun <T> ValueHolder<T>.assertVariable() {
    contract {
        returns() implies (this@assertVariable is ValueHolder.Variable)
    }
    check(this is ValueHolder.Variable) { "only allowed on pattern CodeGenIR (forbidden for real tree)" }
}

private fun <T> ValueHolder<T>.matchValue(target: ValueHolder<T>): Boolean {
    val targetValue = target.getAndAssertConstant()
    return when (this) {
        is ValueHolder.Variable -> {
            set(targetValue)
            true
        }
        is ValueHolder.Constant -> {
            get() == targetValue
        }
    }
}

private fun ValueHolder<CodeGenIR>.matchIR(target: ValueHolder<CodeGenIR>): Boolean {
    val targetValue = target.getAndAssertConstant()
    return when (this) {
        is ValueHolder.Variable -> {
            set(targetValue)
            true
        }
        is ValueHolder.Constant -> {
            get().matches(targetValue)
        }
    }
}

sealed class CodeGenIR : MatchPattern<CodeGenIR> {
    var replacement: Replacement? = null
    val alternatives: Sequence<Replacement>
        get() = sequence {
            yield(defaultReplacement(this@CodeGenIR))
            replacement?.let { yield(it) }
        }

    var firmNode: Node? = null

    fun withOrigin(firmNode: Node): CodeGenIR {
        this.firmNode = firmNode
        return this
    }

    fun display() = "${this::class.simpleName} [${firmNode?.nr ?: "anonymous"}]"

    abstract override fun matches(target: CodeGenIR): Boolean

    /**
     * Sequential Execution of two nodes, representing the value of <pre>second</pre>
     */
    class Seq(val first: CodeGenIR, val second: CodeGenIR) : CodeGenIR() {
        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Seq)
                return false

            return first.matches(target.first) && second.matches(target.second)
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
            return target.alternatives.any {
                if (it.node is RegisterRef) {
                    replacementHolder?.set(it)
                    registerHolder.set(it.node.register)
                    return@any true
                }
                return@any false
            }
        }
    }

    data class Const(private val valueHolder: ValueHolder<Value> = ValueHolder.Variable()) : CodeGenIR() {
        data class Value(
            val value: String,
            val mode: Width,
        ) {
            fun toMolkIR() = Constant(value, mode)
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
    class Compare(
        private val relationHolder: ValueHolder<Relation>,
        private val leftHolder: ValueHolder<CodeGenIR>,
        private val rightHolder: ValueHolder<CodeGenIR>
    ) : CodeGenIR() {
        constructor(relation: Relation, left: CodeGenIR, right: CodeGenIR)
            : this(ValueHolder.Constant(relation), ValueHolder.Constant(left), ValueHolder.Constant(right))
        constructor(
            relationHolder: ValueHolder<Relation>,
            left: CodeGenIR,
            right: CodeGenIR
        ) : this(relationHolder, ValueHolder.Constant(left), ValueHolder.Constant(right))

        val relation
            get() = relationHolder.getAndAssertConstant()
        val left
            get() = leftHolder.getAndAssertConstant()
        val right
            get() = rightHolder.getAndAssertConstant()

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Compare)
                return false

            return relationHolder.matchValue(target.relationHolder) &&
                leftHolder.matchIR(target.leftHolder) &&
                rightHolder.matchIR(target.rightHolder)
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

    class Cond(
        val conditionHolder: ValueHolder<CodeGenIR>,
        val trueLabelHolder: ValueHolder<String>,
        val falseLabelHolder: ValueHolder<String>
    ) : CodeGenIR() {
        constructor(condition: CodeGenIR, trueLabel: String, falseLabel: String) : this(
            ValueHolder.Constant(condition),
            ValueHolder.Constant(trueLabel),
            ValueHolder.Constant(falseLabel)
        )
        constructor(
            condition: CodeGenIR,
            trueLabelHolder: ValueHolder<String>,
            falseLabelHolder: ValueHolder<String>
        ) : this(ValueHolder.Constant(condition), trueLabelHolder, falseLabelHolder)

        val condition: CodeGenIR
            get() = conditionHolder.getAndAssertConstant()
        val trueLabel: String
            get() = trueLabelHolder.getAndAssertConstant()
        val falseLabel: String
            get() = falseLabelHolder.getAndAssertConstant()

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Cond)
                return false

            return conditionHolder.matchIR(target.conditionHolder) &&
                trueLabelHolder.matchValue(target.trueLabelHolder) &&
                falseLabelHolder.matchValue(target.falseLabelHolder)
        }
    }

    class Jmp
    private constructor(private val labelHolder: ValueHolder<String>) : CodeGenIR() {
        constructor(label: String) : this(ValueHolder.Constant(label))
        constructor(label: ValueHolder.Variable<String>) : this(label as ValueHolder<String>)

        val label: String
            get() = labelHolder.getAndAssertConstant()

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Jmp)
                return false

            labelHolder.assertVariable()
            labelHolder.set(target.label)
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

    data class UnaryOP(
        val op: ValueHolder<UnaryOpENUM>,
        val value: ValueHolder<CodeGenIR>
    ) : CodeGenIR() {
        constructor(op: UnaryOpENUM, value: CodeGenIR) : this(
            ValueHolder.Constant(op),
            ValueHolder.Constant(value)
        )

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is UnaryOP)
                return false

            return op.matchValue(target.op) &&
                value.
        }
    }

    data class Conv(
        private val fromModeHolder: ValueHolder<Mode>,
        private val toModeHolder: ValueHolder<Mode>,
        private val opTreeHolder: ValueHolder<CodeGenIR>
    ) : CodeGenIR() {
        constructor(fromMode: Mode, toMode: Mode, opTree: CodeGenIR) : this(
            ValueHolder.Constant(fromMode),
            ValueHolder.Constant(toMode),
            ValueHolder.Constant(opTree)
        )
        constructor(fromMode: ValueHolder<Mode>, toMode: ValueHolder<Mode>, opTree: CodeGenIR) : this(
            fromMode,
            toMode,
            ValueHolder.Constant(opTree)
        )

        val fromMode
            get() = fromModeHolder.getAndAssertConstant()
        val toMode
            get() = toModeHolder.getAndAssertConstant()
        val opTree
            get() = opTreeHolder.getAndAssertConstant()

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Conv)
                return false

            return fromModeHolder.matchValue(target.fromModeHolder) &&
                toModeHolder.matchValue(target.toModeHolder) &&
                opTreeHolder.matchIR(target.opTreeHolder)
        }
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
                condition.walkDepthFirst(walker)
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
                second.walkDepthFirst(walker)
                first.walkDepthFirst(walker)
            }
            is Div -> TODO()
            is Jmp -> {}
            is Mod -> TODO()
            is UnaryOP -> TODO()

            is Noop,
            is AnyNode,
            is SaveNodeTo -> error("invalid CodeGenIR graph: graph must not contain utility nodes")
        }

        walker(this)
    }
}

// Below are utility nodes for matching that MUST NOT be used in the real CodeGenIR tree

/**
 * Can be used as replacement for a node that does not have a result (e.g. Assign)
 */
class Noop : CodeGenIR() {
    override fun matches(target: CodeGenIR): Boolean {
        // target cannot be Noop (because Noop is only a utility node)
        return target.replacement.let { it != null && it.node is Noop }
    }
}

/**
 * Matches any node.
 */
object AnyNode : CodeGenIR() {
    override fun matches(target: CodeGenIR): Boolean {
        return true
    }
}

/**
 * Matches a node based on the pattern returned by [childFn] and stores a reference to that node in [storage].
 */
class SaveNodeTo(private val storage: ValueHolder.Variable<CodeGenIR>, childFn: () -> CodeGenIR) : CodeGenIR() {
    private val child by lazy { childFn() }

    override fun matches(target: CodeGenIR): Boolean {
        storage.set(target)
        return child.matches(target)
    }
}

@Suppress("FunctionName")
fun SaveAnyNodeTo(storage: ValueHolder.Variable<CodeGenIR>) = SaveNodeTo(storage) { AnyNode }

class GraphVizBuilder {
    private var nextId = 0
    fun freshId() = nextId++

    private val stringBuilder = StringBuilder()

    fun appendLine(line: String) {
        stringBuilder.appendLine(line)
    }

    fun build(): String = stringBuilder.toString()

    /**
     * @return the nodes graph viz id.
     */
    fun CodeGenIR.internalToGraphViz(): Int {
        val id = freshId()

        fun node(label: String)  {
            val node = this.firmNode
            val firmLabel = if(node == null) { "" } else {
                "${node.nr}\n"
            }
            appendLine("$id [label=\"$firmLabel$label\", tooltip=\"$firmLabel\"];")
        }
        fun Int.edge(label: String) = appendLine("$id -> $this [label=\"$label\"];")

        when (this@internalToGraphViz) {
            is CodeGenIR.Assign -> {
                node("Assign")
                lhs.internalToGraphViz().edge("lhs")
                rhs.internalToGraphViz().edge("rhs")
            }
            is CodeGenIR.BinOP -> {
                node("BinOP $operation")
                left.internalToGraphViz().edge("left")
                right.internalToGraphViz().edge("right")
            }
            is CodeGenIR.Call -> {
                node("Call\n&${address.entity}")
                arguments.forEachIndexed { index, it ->
                    it.internalToGraphViz().edge(index.toString())
                }
            }
            is CodeGenIR.Compare -> {
                node("Compare $relation")
                left.internalToGraphViz().edge("left")
                right.internalToGraphViz().edge("right")
            }
            is CodeGenIR.Cond -> {
                node("Cond")
                condition.internalToGraphViz().edge("cond")
                val trueNode = freshId()
                appendLine("$trueNode[label=\"BB\n$trueLabel\"];")
                val falseNode = freshId()
                appendLine("$falseNode[label=\"BB\n$falseLabel\"];")
                trueNode.edge("true")
                falseNode.edge("false")
            }
            is CodeGenIR.Const -> {
                node("Const ${value.value}\n${value.mode.name}")
            }
            is CodeGenIR.Conv -> {
                node("Conv $fromMode => $toMode")
                opTree.internalToGraphViz().edge("opTree")
            }
            is CodeGenIR.Indirection -> {
                node("Indirection")
                address.internalToGraphViz().edge("address")
            }
            is CodeGenIR.MemoryAddress -> {
                node("MemoryAddress $memory")
            }
            is CodeGenIR.RegisterRef -> {
                node("Ref @${register.id}\n${register.width}")
            }
            is CodeGenIR.Return -> {
                node("Return")
                returnValue.internalToGraphViz().edge("returnValue")
            }
            is CodeGenIR.Seq -> {
                node("Seq")
                first.internalToGraphViz().edge("1st")
                second.internalToGraphViz().edge("2nd")
            }
            is CodeGenIR.Div -> TODO()
            is CodeGenIR.Jmp -> {
                node("JMP\n$label")
            }
            is CodeGenIR.Mod -> TODO()
            is CodeGenIR.UnaryOP -> TODO()
            AnyNode -> TODO()
            is Noop -> TODO()
            is SaveNodeTo -> TODO()
        }
        return id
    }
}

fun CodeGenIR.toGraphViz(builder: GraphVizBuilder = GraphVizBuilder()): Int = with(builder) {
    internalToGraphViz()
}

fun List<CodeGenIR>.toSeqChain() =
    this.reduceRight { left, right -> CodeGenIR.Seq(left, right).withOrigin(left.firmNode!!) }

// TODO rename
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

// TODO rename
enum class UnaryOpENUM {
    MINUS, NOT
}
