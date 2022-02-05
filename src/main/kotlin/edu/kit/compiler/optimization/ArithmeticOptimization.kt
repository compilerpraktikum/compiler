@file:Suppress("FunctionName")

package edu.kit.compiler.optimization

import edu.kit.compiler.utils.MatchPattern
import edu.kit.compiler.utils.ReplacementBuilderScope
import edu.kit.compiler.utils.ValueHolder
import edu.kit.compiler.utils.rule
import firm.BackEdges
import firm.Graph
import firm.Mode
import firm.nodes.Add
import firm.nodes.Binop
import firm.nodes.Div
import firm.nodes.Minus
import firm.nodes.Mod
import firm.nodes.Mul
import firm.nodes.Not
import firm.nodes.Sub
import kotlin.reflect.KClass
import firm.nodes.Const as FirmConst
import firm.nodes.Node as FirmNode

val ArithmeticOptimization = Optimization("arithmetic optimization", ::applyArithmeticOptimization)

private fun applyArithmeticOptimization(graph: Graph): Boolean {
    var hasChanged = false

    BackEdges.enable(graph)

    graph.walkTopological(object : FirmNodeVisitorAdapter() {
        override fun defaultVisit(node: FirmNode) {
            rules.forEach { rule ->
                if (node.block == null) {
                    // node is a Block -> irrelevant for arithmetic optimization
                    return@forEach
                }

                val replacement = with(ReplacementScope(graph, node.block)) {
                    rule.match(node)
                }
                if (replacement != null) {
                    when (replacement) {
                        is Replacement.Node -> Graph.exchange(node, replacement.node)
                        is Replacement.NodeWithMem -> {
                            BackEdges.getOuts(node).forEach {
                                if (it.node.mode == Mode.getM()) {
                                    Graph.exchange(it.node, replacement.mem)
                                } else {
                                    Graph.exchange(it.node, replacement.node)
                                }
                            }
                        }
                    }

                    hasChanged = true
                    return // do not check other rules
                }
            }
        }
    })

    BackEdges.disable(graph)

    return hasChanged
}

private sealed class Replacement {
    class Node(val node: FirmNode) : Replacement()
    class NodeWithMem(
        val node: FirmNode,
        val mem: FirmNode,
    ) : Replacement()
}

private class ReplacementScope(val graph: Graph, val block: FirmNode) : ReplacementBuilderScope {
    fun node(node: FirmNode) = Replacement.Node(node)
    fun nodeWithMem(node: FirmNode, mem: FirmNode) = Replacement.NodeWithMem(node, mem)
}

private sealed class NodePattern : MatchPattern<FirmNode>

private object AnyNode : NodePattern() {
    override fun matches(target: FirmNode): Boolean {
        return true
    }
}

private class Const(private val value: ValueHolder<Int>? = null) : NodePattern() {
    override fun matches(target: firm.nodes.Node): Boolean {
        if (target !is FirmConst)
            return false

        if (target.mode != Mode.getIs())
            return false

        return when (value) {
            null -> true
            is ValueHolder.Variable -> {
                value.set(target.tarval.asInt())
                true
            }
            is ValueHolder.Constant -> target.tarval.asInt() == value.get()
        }
    }
}

private class InternalBinaryOp<C : FirmNode>(
    private val left: NodePattern,
    private val right: NodePattern,
    private val type: KClass<C>,
) : NodePattern() {
    override fun matches(target: FirmNode): Boolean {
        if (target::class != type)
            return false

        when (target) {
            is Binop -> {
                val matchesInOrder = left.matches(target.left) && right.matches(target.right)
                if (matchesInOrder)
                    return true

                return if (target.isCommutative) {
                    left.matches(target.right) && right.matches(target.left)
                } else {
                    false
                }
            }
            is Div -> return left.matches(target.left) && right.matches(target.right)
            is Mod -> return left.matches(target.left) && right.matches(target.right)
            else -> error("unknown binop: ${target::class.simpleName}")
        }
    }
}
private inline fun <reified C : FirmNode> BinaryOp(left: NodePattern, right: NodePattern) =
    InternalBinaryOp(left, right, C::class)

private val FirmNode.isCommutative: Boolean
    get() = when (this) {
        is Add, is Mul -> true
        else -> false
    }

private class InternalUnaryOp<C : FirmNode>(
    private val operand: NodePattern,
    private val type: KClass<C>,
) : NodePattern() {
    override fun matches(target: FirmNode): Boolean {
        if (target::class != type)
            return false

        return when (target) {
            is Minus -> operand.matches(target.op)
            is Not -> operand.matches(target.op)
            else -> error("unknown unary op: ${target::class.simpleName}")
        }
    }
}
private inline fun <reified C : FirmNode> UnaryOp(operand: NodePattern) =
    InternalUnaryOp(operand, C::class)

// utilities

private class OneOf(private val options: Array<out NodePattern>) : NodePattern() {
    override fun matches(target: firm.nodes.Node): Boolean {
        return options.any { it.matches(target) }
    }
}
private fun OneOf(vararg options: NodePattern) = OneOf(options)

private class Condition(private val child: NodePattern, private val condition: () -> Boolean) : NodePattern() {
    override fun matches(target: firm.nodes.Node): Boolean {
        return child.matches(target) && condition()
    }
}

private class SaveNodeTo(private val storage: ValueHolder.Variable<FirmNode>, childFn: () -> NodePattern) : NodePattern() {
    private val child by lazy { childFn() }

    override fun matches(target: FirmNode): Boolean {
        storage.set(target)
        return child.matches(target)
    }
}

private fun SaveAnyNodeTo(storage: ValueHolder.Variable<FirmNode>) = SaveNodeTo(storage) { AnyNode }

@Suppress("FunctionName")
private fun SaveConstTo(storage: ValueHolder.Variable<FirmNode>) = SaveNodeTo(storage) { Const() }

@Suppress("FunctionName")
private fun SaveNonConstTo(value: ValueHolder.Variable<FirmNode>) =
    Condition(SaveAnyNodeTo(value)) { value.get() !is FirmConst }

// rules

private val rules = listOf(

    rule("(any + 0)") {
        val any = variable<FirmNode>()

        match(
            BinaryOp<Add>(
                SaveAnyNodeTo(any),
                Const(constant(0))
            )
        )

        replaceWith {
            node(any.get())
        }
    },
    rule("(any + (-subtrahend)))") {
        val any = variable<FirmNode>()
        val subtrahend = variable<FirmNode>()

        match(
            BinaryOp<Add>(
                SaveAnyNodeTo(any),
                UnaryOp<Minus>(
                    SaveAnyNodeTo(subtrahend)
                )
            )
        )

        replaceWith {
            node(
                graph.newSub(block, any.get(), subtrahend.get())
            )
        }
    },

    rule("(any - 0)") {
        val any = variable<FirmNode>()

        match(
            BinaryOp<Sub>(
                SaveAnyNodeTo(any),
                Const(constant(0))
            )
        )

        replaceWith {
            node(any.get())
        }
    },
    rule("(0 - any)") {
        val any = variable<FirmNode>()

        match(
            BinaryOp<Sub>(
                Const(constant(0)),
                SaveAnyNodeTo(any)
            )
        )

        replaceWith {
            node(graph.newMinus(block, any.get()))
        }
    },
    rule("(any - any)") {
        val sub = variable<FirmNode>()
        val left = variable<FirmNode>()
        val right = variable<FirmNode>()

        match(
            SaveNodeTo(sub) {
                BinaryOp<Sub>(
                    SaveAnyNodeTo(left),
                    SaveAnyNodeTo(right)
                )
            }
        )

        condition {
            left.get() == right.get()
        }

        replaceWith {
            node(graph.newConst(0, sub.get().mode))
        }
    },

    rule("(any * 1)") {
        val any = variable<FirmNode>()

        match(
            BinaryOp<Mul>(
                SaveAnyNodeTo(any),
                Const(constant(1))
            )
        )

        replaceWith {
            node(any.get())
        }
    },
    rule("(any * 0)") {
        val zero = variable<FirmNode>()

        match(
            BinaryOp<Mul>(
                AnyNode,
                SaveNodeTo(zero) { Const(constant(0)) }
            )
        )

        replaceWith {
            node(zero.get())
        }
    },
    rule("(any * -1)") {
        val any = variable<FirmNode>()

        match(
            BinaryOp<Mul>(
                SaveAnyNodeTo(any),
                Const(constant(-1))
            )
        )

        replaceWith {
            node(graph.newMinus(block, any.get()))
        }
    },

    rule("(any / 1)") {
        val div = variable<FirmNode>()
        val any = variable<FirmNode>()

        match(
            SaveNodeTo(div) {
                BinaryOp<Div>(
                    SaveAnyNodeTo(any),
                    Const(constant(1))
                )
            }
        )

        replaceWith {
            nodeWithMem(
                any.get(),
                (div.get() as Div).mem
            )
        }
    },
    rule("(any / -1)") {
        val div = variable<FirmNode>()
        val any = variable<FirmNode>()

        match(
            SaveNodeTo(div) {
                BinaryOp<Div>(
                    SaveAnyNodeTo(any),
                    Const(constant(-1))
                )
            }
        )

        replaceWith {
            nodeWithMem(
                graph.newMinus(block, any.get()),
                (div.get() as Div).mem
            )
        }
    },

    rule("(any % 1) or (any % -1)") {
        val mod = variable<FirmNode>()

        match(
            SaveNodeTo(mod) {
                BinaryOp<Mod>(
                    AnyNode,
                    OneOf(Const(constant(1)), Const(constant(-1)))
                )
            }
        )

        replaceWith {
            val modNode = mod.get() as Mod
            nodeWithMem(
                graph.newConst(0, modNode.resmode),
                modNode.mem
            )
        }
    },

    // Move constants as far out as possible to allow constant folding to combine them if in a chain of operations.
    moveConstantOutwardsRule<Add>("(any + const) + nonConst -> (any + nonConst) + const", Graph::newAdd),
    groupConstantsRule<Add>("(any + innerConst) + outerConst -> any + (innerConst + outerConst)", Graph::newAdd, Graph::newAdd),

    moveConstantOutwardsRule<Mul>("(any * const) * nonConst -> (any * nonConst) * const", Graph::newMul),
    groupConstantsRule<Mul>("(any * innerConst) * outerConst -> any * (innerConst * outerConst)", Graph::newMul, Graph::newMul),

    moveConstantOutwardsRule<Sub>("(any - const) - nonConst -> (any - nonConst) - const", Graph::newSub),
    groupConstantsRule<Sub>("(any - innerConst) - outerConst -> any - (innerConst + outerConst)", Graph::newSub, Graph::newAdd),

    // distributive
    distributiveRule<Add>("(any * left) + (any * right) -> any * (left + right)", Graph::newAdd),
    distributiveRule<Sub>("(any * left) - (any * right) -> any * (left - right)", Graph::newSub),
)

// (any + const) + nonConst -> (any + nonConst) + const
private inline fun <reified NodeType : FirmNode> moveConstantOutwardsRule(
    name: String,
    crossinline newNode: Graph.(FirmNode, FirmNode, FirmNode) -> FirmNode,
) = rule<FirmNode, Replacement, ReplacementScope>(name) {
    val any = variable<FirmNode>()
    val const = variable<FirmNode>()
    val nonConst = variable<FirmNode>()

    match(
        BinaryOp<NodeType>(
            BinaryOp<NodeType>(
                SaveAnyNodeTo(any),
                SaveConstTo(const),
            ),
            SaveNonConstTo(nonConst)
        )
    )

    replaceWith {
        node(
            graph.newNode(
                block,
                graph.newNode(block, any.get(), nonConst.get()),
                const.get()
            )
        )
    }
}

// (any + innerConst) + outerConst -> any + (innerConst + outerConst)
private inline fun <reified NodeType : FirmNode> groupConstantsRule(
    name: String,
    crossinline newOuterNode: Graph.(FirmNode, FirmNode, FirmNode) -> FirmNode,
    crossinline newInnerNode: Graph.(FirmNode, FirmNode, FirmNode) -> FirmNode,
) = rule<FirmNode, Replacement, ReplacementScope>(name) {
    val any = variable<FirmNode>()
    val innerConst = variable<FirmNode>()
    val outerConst = variable<FirmNode>()

    match(
        BinaryOp<NodeType>(
            BinaryOp<NodeType>(
                SaveAnyNodeTo(any),
                SaveConstTo(innerConst)
            ),
            SaveConstTo(outerConst)
        )
    )

    replaceWith {
        node(
            graph.newOuterNode(
                block,
                any.get(),
                graph.newInnerNode(block, innerConst.get(), outerConst.get())
            )
        )
    }
}

private inline fun <reified C : FirmNode> distributiveRule(
    name: String,
    crossinline newInnerNode: Graph.(FirmNode, FirmNode, FirmNode) -> FirmNode
) = rule<FirmNode, Replacement, ReplacementScope>(name) {
    val leftFirst = variable<FirmNode>()
    val leftSecond = variable<FirmNode>()
    val rightFirst = variable<FirmNode>()
    val rightSecond = variable<FirmNode>()

    match(
        BinaryOp<C>(
            BinaryOp<Mul>(
                SaveAnyNodeTo(leftFirst),
                SaveAnyNodeTo(leftSecond)
            ),
            BinaryOp<Mul>(
                SaveAnyNodeTo(rightFirst),
                SaveAnyNodeTo(rightSecond)
            ),
        )
    )

    var same: FirmNode? = null
    var otherLeft: FirmNode? = null
    var otherRight: FirmNode? = null
    condition {
        return@condition when {
            leftFirst.get() == rightFirst.get() -> {
                same = leftFirst.get()
                otherLeft = leftSecond.get()
                otherRight = rightSecond.get()
                true
            }
            leftFirst.get() == rightSecond.get() -> {
                same = leftFirst.get()
                otherLeft = leftSecond.get()
                otherRight = rightFirst.get()
                true
            }
            leftSecond.get() == rightFirst.get() -> {
                same = leftSecond.get()
                otherLeft = leftFirst.get()
                otherRight = rightSecond.get()
                true
            }
            leftSecond.get() == rightSecond.get() -> {
                same = leftSecond.get()
                otherLeft = leftFirst.get()
                otherRight = rightFirst.get()
                true
            }
            else -> false
        }
    }

    replaceWith {
        node(
            graph.newMul(
                block,
                same!!,
                graph.newInnerNode(block, otherLeft!!, otherRight!!)
            )
        )
    }
}
