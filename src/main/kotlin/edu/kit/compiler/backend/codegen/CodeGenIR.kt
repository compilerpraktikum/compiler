@file:Suppress("DataClassPrivateConstructor")

package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Constant
import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.Memory
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.Target
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.utils.MatchPattern
import edu.kit.compiler.utils.ValueHolder
import firm.Mode
import firm.Relation
import firm.nodes.Address
import firm.nodes.Node
import kotlin.reflect.KProperty
import edu.kit.compiler.backend.molkir.Instruction.Companion as Instructions

private fun defaultReplacement(node: CodeGenIR) = Replacement(
    node = node,
    instructions = instructionListOf(),
    cost = 0,
)

private fun <T> ValueHolder<T>.getAndAssertConstant() = get().also {
    check(this is ValueHolder.Constant) { "only allowed on real CodeGenIR (forbidden for patterns)" }
}

private fun <T> T.toConst() = ValueHolder.Constant(this)

/**
 * Helper to allow the following short syntax:
 * ```kt
 * val value by valueHolder.getter()
 * ```
 */
private fun <T> ValueHolder<T>.getter() = object {
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = getAndAssertConstant()
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
    /**
     * [Replacement] with the minimal cost that was found during pattern matching.
     */
    var replacement: Replacement? = null

    /**
     * Sequence to cycle through the original node and a possible [Replacement].
     */
    val alternatives: Sequence<Replacement>
        get() = sequence {
            yield(defaultReplacement(this@CodeGenIR))
            replacement?.let { yield(it) }
        }

    /**
     * The firm node that this [node][CodeGenIR] was constructed for.
     */
    var firmNode: Node? = null

    fun withOrigin(firmNode: Node): CodeGenIR {
        this.firmNode = firmNode
        return this
    }

    fun display() = "${this::class.simpleName} [${firmNode?.nr ?: "anonymous"}]"

    /**
     * Matches the [target node][target] against the pattern represented by `this`.
     */
    abstract override fun matches(target: CodeGenIR): Boolean

    /**
     * Sequential Execution of two nodes.
     */
    data class Seq(val first: CodeGenIR, val second: CodeGenIR) : CodeGenIR() {
        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Seq)
                return false

            return first.matches(target.first) && second.matches(target.second)
        }
    }

    data class RegisterRef
    private constructor(
        private val registerHolder: ValueHolder<Register>,
        private val replacementHolder: ValueHolder.Variable<Replacement>?,
    ) : CodeGenIR() {
        constructor(reg: Register) : this(reg.toConst(), null)
        constructor(
            value: ValueHolder.Variable<Register>,
            replacementHolder: ValueHolder.Variable<Replacement>? = null
        ) : this(value as ValueHolder<Register>, replacementHolder)

        val register: Register by registerHolder.getter()

        override fun matches(target: CodeGenIR): Boolean {
            check(registerHolder is ValueHolder.Variable)
            return target.alternatives.any {
                if (it.node !is RegisterRef)
                    return@any false

                replacementHolder?.set(it)
                registerHolder.set(it.node.register)
                return@any true
            }
        }
    }

    data class Const(private val valueHolder: ValueHolder<Value> = ValueHolder.Variable()) : CodeGenIR() {
        constructor(const: String, mode: Width) : this(Value(const, mode).toConst())

        data class Value(val value: String, val mode: Width) {
            fun toMolkIR() = Constant(value, mode)
        }

        val value: Value by valueHolder.getter()

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Const)
                return false

            return valueHolder.matchValue(target.valueHolder)
        }
    }

    data class BinaryOp(
        private val operationHolder: ValueHolder<BinaryOpType>,
        private val leftHolder: ValueHolder<CodeGenIR>,
        private val rightHolder: ValueHolder<CodeGenIR>
    ) : CodeGenIR() {
        constructor(operation: BinaryOpType, left: CodeGenIR, right: CodeGenIR) : this(operation.toConst(), left.toConst(), right.toConst())
        constructor(operation: ValueHolder<BinaryOpType>, left: CodeGenIR, right: CodeGenIR) : this(operation, left.toConst(), right.toConst())

        val operation by operationHolder.getter()
        val left by leftHolder.getter()
        val right by rightHolder.getter()

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is BinaryOp)
                return false

            if (!operationHolder.matchValue(target.operationHolder))
                return false

            val matchesInOrder = leftHolder.matchIR(target.leftHolder) && rightHolder.matchIR(target.rightHolder)
            if (matchesInOrder)
                return true

            return if (target.operationHolder.get().isCommutative) {
                leftHolder.matchIR(target.rightHolder) && rightHolder.matchIR(target.leftHolder)
            } else {
                false
            }
        }
    }

    // TODO maybe commutative? is this needed?
    data class Compare(
        private val relationHolder: ValueHolder<Relation>,
        private val leftHolder: ValueHolder<CodeGenIR>,
        private val rightHolder: ValueHolder<CodeGenIR>
    ) : CodeGenIR() {
        constructor(relation: Relation, left: CodeGenIR, right: CodeGenIR) :
            this(relation.toConst(), left.toConst(), right.toConst())
        constructor(relationHolder: ValueHolder<Relation>, left: CodeGenIR, right: CodeGenIR) :
            this(relationHolder, left.toConst(), right.toConst())

        val relation by relationHolder.getter()
        val left by leftHolder.getter()
        val right by rightHolder.getter()

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

    data class MemoryAddress(
        private val memoryHolder: ValueHolder<Memory>,
        private val replacementHolder: ValueHolder.Variable<Replacement>?
    ) : CodeGenIR() {
        constructor(memory: Memory) : this(memory.toConst(), null)

        val memory: Memory by memoryHolder.getter()

        override fun matches(target: CodeGenIR): Boolean {
            check(memoryHolder is ValueHolder.Variable)
            return target.alternatives.any {
                if (it.node !is MemoryAddress)
                    return@any false

                replacementHolder?.set(it)
                memoryHolder.set(it.node.memory)
                return@any true
            }
        }
    }

    data class Cond(
        private val conditionHolder: ValueHolder<CodeGenIR>,
        private val trueLabelHolder: ValueHolder<String>,
        private val falseLabelHolder: ValueHolder<String>
    ) : CodeGenIR() {
        constructor(condition: CodeGenIR, trueLabel: String, falseLabel: String) :
            this(condition.toConst(), trueLabel.toConst(), falseLabel.toConst())
        constructor(condition: CodeGenIR, trueLabelHolder: ValueHolder<String>, falseLabelHolder: ValueHolder<String>) :
            this(condition.toConst(), trueLabelHolder, falseLabelHolder)

        val condition by conditionHolder.getter()
        val trueLabel by trueLabelHolder.getter()
        val falseLabel by falseLabelHolder.getter()

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Cond)
                return false

            return conditionHolder.matchIR(target.conditionHolder) &&
                trueLabelHolder.matchValue(target.trueLabelHolder) &&
                falseLabelHolder.matchValue(target.falseLabelHolder)
        }
    }

    data class Jmp(private val labelHolder: ValueHolder<String>) : CodeGenIR() {
        constructor(label: String) : this(label.toConst())

        val label by labelHolder.getter()

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Jmp)
                return false

            return labelHolder.matchValue(target.labelHolder)
        }
    }

    data class Assign(val to: CodeGenIR, val from: CodeGenIR) : CodeGenIR() {
        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Assign)
                return false

            return to.matches(target.to) && from.matches(target.from)
        }
    }

    data class Div(
        private val leftHolder: ValueHolder<CodeGenIR>,
        private val rightHolder: ValueHolder<CodeGenIR>
    ) : CodeGenIR() {
        constructor(left: CodeGenIR, right: CodeGenIR) : this(left.toConst(), right.toConst())

        val left by leftHolder.getter()
        val right by rightHolder.getter()

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Div) {
                return false
            }
            return leftHolder.matchIR(target.leftHolder) && rightHolder.matchIR(target.rightHolder)
        }
    }

    data class Mod(
        private val leftHolder: ValueHolder<CodeGenIR>,
        private val rightHolder: ValueHolder<CodeGenIR>,
    ) : CodeGenIR() {
        constructor(left: CodeGenIR, right: CodeGenIR) : this(left.toConst(), right.toConst())

        val left by leftHolder.getter()
        val right by rightHolder.getter()

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Mod) {
                return false
            }
            return leftHolder.matchIR(target.leftHolder) && rightHolder.matchIR(target.rightHolder)
        }
    }

    data class Call
    private constructor(
        private val addressHolder: ValueHolder<Address>,
        private val argumentsHolder: ValueHolder<List<CodeGenIR>>,
    ) : CodeGenIR() {
        constructor(address: Address, arguments: List<CodeGenIR>) : this(
            address.toConst(),
            arguments.toConst(),
        )
        constructor(address: ValueHolder<Address>, arguments: ValueHolder.Variable<List<CodeGenIR>>) :
            this(address, arguments as ValueHolder<List<CodeGenIR>>)

        val address by addressHolder.getter()
        val arguments by argumentsHolder.getter()

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is Call)
                return false

            if (!addressHolder.matchValue(target.addressHolder))
                return false

            check(argumentsHolder is ValueHolder.Variable)
            argumentsHolder.set(target.arguments)

            return true
        }
    }

    data class UnaryOp(
        private val operationHolder: ValueHolder<UnaryOpType>,
        private val operandHolder: ValueHolder<CodeGenIR>
    ) : CodeGenIR() {
        constructor(op: UnaryOpType, value: CodeGenIR) : this(op.toConst(), value.toConst())
        constructor(op: ValueHolder<UnaryOpType>, value: CodeGenIR) : this(op, value.toConst())

        val operation by operationHolder.getter()
        val operand by operandHolder.getter()

        override fun matches(target: CodeGenIR): Boolean {
            if (target !is UnaryOp)
                return false

            return operationHolder.matchValue(target.operationHolder) &&
                operandHolder.matchIR(target.operandHolder)
        }
    }

    data class Conv(
        private val fromModeHolder: ValueHolder<Mode>,
        private val toModeHolder: ValueHolder<Mode>,
        private val opTreeHolder: ValueHolder<CodeGenIR>
    ) : CodeGenIR() {
        constructor(fromMode: Mode, toMode: Mode, opTree: CodeGenIR) :
            this(fromMode.toConst(), toMode.toConst(), opTree.toConst())
        constructor(fromMode: ValueHolder<Mode>, toMode: ValueHolder<Mode>, opTree: CodeGenIR) :
            this(fromMode, toMode, opTree.toConst())

        val fromMode by fromModeHolder.getter()
        val toMode by toModeHolder.getter()
        val opTree by opTreeHolder.getter()

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
                to.walkDepthFirst(walker)
                from.walkDepthFirst(walker)
            }
            is BinaryOp -> {
                left.walkDepthFirst(walker)
                right.walkDepthFirst(walker)
            }
            is Call -> {
                arguments.forEach {
                    it.walkDepthFirst(walker)
                }
            }
            is Compare -> {
                left.walkDepthFirst(walker)
                right.walkDepthFirst(walker)
            }
            is Cond -> {
                condition.walkDepthFirst(walker)
            }
            is Const -> {}
            is Conv -> {
                opTree.walkDepthFirst(walker)
            }
            is Div -> {
                left.walkDepthFirst(walker)
                right.walkDepthFirst(walker)
            }
            is Indirection -> {
                address.walkDepthFirst(walker)
            }
            is Jmp -> {}
            is MemoryAddress -> {}
            is Mod -> {
                left.walkDepthFirst(walker)
                right.walkDepthFirst(walker)
            }
            is RegisterRef -> {}
            is Return -> {
                returnValue.walkDepthFirst(walker)
            }
            is Seq -> {
                second.walkDepthFirst(walker)
                first.walkDepthFirst(walker)
            }
            is UnaryOp -> {
                operand.walkDepthFirst(walker)
            }

            AnyNode,
            is ConstOrRegisterRef,
            is Noop,
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
 * Matches a node based on the pattern returned by `childFn()` and stores a reference to that node in [storage].
 */
class SaveNodeTo(private val storage: ValueHolder.Variable<CodeGenIR>, childFn: () -> CodeGenIR) : CodeGenIR() {
    private val child by lazy { childFn() }

    override fun matches(target: CodeGenIR): Boolean {
        storage.set(target)
        return child.matches(target)
    }
}

class ConstOrRegisterRef(
    private val targetHolder: ValueHolder.Variable<Target.Input>,
    private val replacementHolder: ValueHolder.Variable<Replacement>,
) : CodeGenIR() {
    private val constHolder = ValueHolder.Variable<Const.Value>()
    private val constPattern = Const(constHolder)
    private val registerHolder = ValueHolder.Variable<Register>()
    private val registerPattern = RegisterRef(registerHolder, replacementHolder)

    override fun matches(target: CodeGenIR): Boolean {
        return if (constPattern.matches(target)) {
            targetHolder.set(constHolder.get().toMolkIR())
            replacementHolder.set(defaultReplacement(target))
            true
        } else if (registerPattern.matches(target)) {
            targetHolder.set(registerHolder.get())
            // properly filling the replacementHolder is already handled within matches()
            true
        } else {
            false
        }
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
     * @return the node's graph viz id.
     */
    fun CodeGenIR.internalToGraphViz(): Int {
        val id = freshId()

        fun node(label: String) {
            val firmLabel = firmNode?.let { "${it.nr}\n" } ?: ""
            appendLine("$id [label=\"$firmLabel$label\", tooltip=\"$firmLabel\"];")
        }
        fun Int.edge(label: String) = appendLine("$id -> $this [label=\"$label\"];")

        when (this@internalToGraphViz) {
            is CodeGenIR.Assign -> {
                node("Assign")
                to.internalToGraphViz().edge("to")
                from.internalToGraphViz().edge("from")
            }
            is CodeGenIR.BinaryOp -> {
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
                node("Conv $fromMode (${fromMode.sizeBytes}) => $toMode (${toMode.sizeBytes})")
                opTree.internalToGraphViz().edge("opTree")
            }
            is CodeGenIR.Div -> {
                node("Div")
                left.internalToGraphViz().edge("left")
                right.internalToGraphViz().edge("right")
            }
            is CodeGenIR.Indirection -> {
                node("Indirection")
                address.internalToGraphViz().edge("address")
            }
            is CodeGenIR.Jmp -> {
                node("JMP\n$label")
            }
            is CodeGenIR.MemoryAddress -> {
                node("MemoryAddress $memory")
            }
            is CodeGenIR.Mod -> {
                node("Mod")
                left.internalToGraphViz().edge("left")
                right.internalToGraphViz().edge("right")
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
            is CodeGenIR.UnaryOp -> {
                node("UnaryOp $operation")
                operand.internalToGraphViz().edge("operand")
            }

            AnyNode,
            is ConstOrRegisterRef,
            is Noop,
            is SaveNodeTo -> error("invalid CodeGenIR graph: graph must not contain utility nodes")
        }
        return id
    }
}

fun CodeGenIR.toGraphViz(builder: GraphVizBuilder = GraphVizBuilder()): Int = with(builder) {
    internalToGraphViz()
}

fun List<CodeGenIR>.toSeqChain() =
    this.reduceRight { left, right -> CodeGenIR.Seq(left, right) }

enum class BinaryOpType(
    val isCommutative: Boolean,
    val molkiOp: (Target.Input, Target.Input, Target.Output) -> Instruction
) {
    ADD(true, Instructions::add),
    AND(true, Instructions::and),
    EOR(true, Instructions::xor),
    MUL(true, Instructions::imul),
    OR(true, Instructions::or),
    SUB(false, Instructions::sub),
    SHL(false, Instructions::shl),
    SHR(false, Instructions::shr),
    SHRS(false, Instructions::sar),
}

enum class UnaryOpType(
    val molkiOp: (Target.Input, Target.Output) -> Instruction
) {
    MINUS(Instructions::neg),
    NOT(Instructions::not),
}
