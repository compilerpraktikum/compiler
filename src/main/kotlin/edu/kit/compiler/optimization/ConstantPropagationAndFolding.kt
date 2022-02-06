package edu.kit.compiler.optimization

import firm.BackEdges
import firm.Graph
import firm.Mode
import firm.TargetValue
import firm.nodes.Add
import firm.nodes.Address
import firm.nodes.Align
import firm.nodes.Alloc
import firm.nodes.Anchor
import firm.nodes.And
import firm.nodes.Bad
import firm.nodes.Binop
import firm.nodes.Bitcast
import firm.nodes.Block
import firm.nodes.Builtin
import firm.nodes.Call
import firm.nodes.Cmp
import firm.nodes.Cond
import firm.nodes.Confirm
import firm.nodes.Const
import firm.nodes.Conv
import firm.nodes.CopyB
import firm.nodes.Deleted
import firm.nodes.Div
import firm.nodes.Dummy
import firm.nodes.End
import firm.nodes.Eor
import firm.nodes.Free
import firm.nodes.IJmp
import firm.nodes.Id
import firm.nodes.Jmp
import firm.nodes.Load
import firm.nodes.Member
import firm.nodes.Minus
import firm.nodes.Mod
import firm.nodes.Mul
import firm.nodes.Mulh
import firm.nodes.Mux
import firm.nodes.NoMem
import firm.nodes.Node
import firm.nodes.Not
import firm.nodes.Offset
import firm.nodes.Or
import firm.nodes.Phi
import firm.nodes.Pin
import firm.nodes.Proj
import firm.nodes.Raise
import firm.nodes.Return
import firm.nodes.Sel
import firm.nodes.Shl
import firm.nodes.Shr
import firm.nodes.Shrs
import firm.nodes.Size
import firm.nodes.Start
import firm.nodes.Store
import firm.nodes.Sub
import firm.nodes.Switch
import firm.nodes.Sync
import firm.nodes.Tuple
import firm.nodes.Unknown

val ConstantPropagationAndFolding = Optimization("constant propagation and folding", ::applyConstantPropagationAndFolding)

/**
 * What is expected seems to be a mix of
 *      * Constant Folding (lecture and compileroptimizations.com: x = 1 + 8 * 2; ==> x = 17;) and
 *      * Constant Propagation (lecture and compileroptimizations.com:
 *          x = 3;
 *          y = x + 4;
 *          ==>
 *          x = 3;
 *          y = 7;
 *      )
 *      Other examples from the 8th meeting:
 *          int optimizeMe() {
 *              int x = 1;
 *              while (...)
 *                  x = 2 - x;
 *              return x;
 *          }
 *          ==>
 *          int optimizeMe() {
 *              int x = 1;          // dead code after this optimization
 *              while (...)
 *                  x = 1;          // dead code after this optimization
 *              return 1;
 *          }
 *
 */
private fun applyConstantPropagationAndFolding(graph: Graph): Boolean {
    BackEdges.enable(graph)

    val analysis = ConstantAnalysis(graph)
    analysis.run()

    val hasChanged = replaceConstants(graph, analysis.nodeValues)

    // remove Bads introduced in replaceCondition
    firm.bindings.binding_irgopt.remove_bads(graph.ptr)

    // remove unreachable code
    firm.bindings.binding_irgopt.remove_unreachable_code(graph.ptr)
    firm.bindings.binding_irgopt.remove_bads(graph.ptr)

    BackEdges.disable(graph)

    return hasChanged
}

private val Unknown = TargetValue.getUnknown()
private val Bad = TargetValue.getBad()

/**
 * Apply the [operation][op] to the operands ([left], [right]). Correctly handles [Unknown] and [Bad].
 */
private fun apply(op: TargetValue.(TargetValue) -> TargetValue, left: TargetValue, right: TargetValue) = when {
    (left == Unknown || right == Unknown) -> Unknown
    (left == Bad || right == Bad) -> Bad
    else -> left.op(right)
}

/**
 * Apply the [operation][op] to the operand ([operand]). Correctly handles [Unknown] and [Bad].
 */
private fun apply(op: TargetValue.() -> TargetValue, operand: TargetValue) = when {
    operand.isConstant -> operand.op()
    else -> operand
}

/**
 * Check that a target value is never updated to another value that is "lower" in the flat lattice (Unknown < { all constants } < Bad).
 */
private fun assertOrder(old: TargetValue, new: TargetValue) {
    when {
        old == Unknown -> {}
        old.isConstant -> {
            check(new != Unknown)
        }
        old == Bad -> {
            check(new == Bad)
        }
    }
}

/**
 * Supremum of the operands ([left], [right]) in the flat lattice (Unknown < { all constants } < Bad).
 */
private fun supremum(left: TargetValue, right: TargetValue) = when {
    left == Unknown -> right
    right == Unknown -> left
    left == right -> left
    else -> Bad
}

private fun Sequence<TargetValue>.supremum(): TargetValue = reduce(::supremum)

private fun defaultTargetValue(node: Node): TargetValue = when (node) {
    is Add,
    is And,
    is Cmp,
    is Confirm,
    is Const,
    is Conv,
    is Div,
    is Eor,
    is Id,
    is Minus,
    is Mod,
    is Mul,
    is Not,
    is Or,
    is Phi,
    is Proj,
    is Shl,
    is Shr,
    is Shrs,
    is Sub,
    is Unknown -> Unknown

    is Address,
    is Load -> Bad

    is Cond -> Unknown // not really a value but simplifies handling, see visit(node: Cond)

    is Align,
    is Alloc,
    is Anchor,
    is Bad,
    is Bitcast,
    is Block,
    is Builtin,
    is Call,
    is CopyB,
    is Deleted,
    is Dummy,
    is End,
    is Free,
    is IJmp,
    is Jmp,
    is Member, // handled in lowerSels()
    is Mux,
    is Mulh,
    is NoMem,
    is Offset,
    is Pin,
    is Raise,
    is Return,
    is Sel,
    is Size,
    is Start,
    is Store,
    is Switch,
    is Sync,
    is Tuple -> error("unhandled node type: ${node.javaClass.simpleName}")

    else -> error("unknown node type: ${node.javaClass.simpleName}")
}

private class ConstantAnalysis(graph: Graph) : WorkListVisitor(graph) {
    val nodeValues = mutableMapOf<Node, TargetValue>()

    private fun update(node: Node, newValue: TargetValue) {
        val oldValue = getValueOf(node)
        if (oldValue != newValue) {
            assertOrder(oldValue, newValue)
            nodeValues[node] = newValue
            markChanged()
        }
    }

    private fun getValueOf(node: Node) = nodeValues.computeIfAbsent(node) { defaultTargetValue(node) }

    private fun updateWith(node: Binop, op: TargetValue.(TargetValue) -> TargetValue) {
        update(node, apply(op, getValueOf(node.left), getValueOf(node.right)))
    }

    private fun updateWith(node: Node, left: Node, right: Node, op: TargetValue.(TargetValue) -> TargetValue) {
        update(node, apply(op, getValueOf(left), getValueOf(right)))
    }

    private fun updateWith(node: Node, operand: Node, op: TargetValue.() -> TargetValue) {
        update(node, apply(op, getValueOf(operand)))
    }

    override fun defaultVisit(node: Node) {
        error("unhandled node type: ${node.javaClass.simpleName}")
    }

    override fun visit(node: Const) = update(node, node.tarval)
    override fun visit(node: Id) = update(node, getValueOf(node.pred))
    override fun visit(node: Confirm) = update(node, getValueOf(node.value))

    override fun visit(node: Minus) = updateWith(node, node.op, TargetValue::neg)
    override fun visit(node: Not) = updateWith(node, node.op, TargetValue::not)

    override fun visit(node: Add) = updateWith(node, TargetValue::add)
    override fun visit(node: Mul) = updateWith(node, TargetValue::mul)
    override fun visit(node: Sub) = updateWith(node, TargetValue::sub)

    override fun visit(node: And) = updateWith(node, TargetValue::and)
    override fun visit(node: Eor) = updateWith(node, TargetValue::eor)
    override fun visit(node: Or) = updateWith(node, TargetValue::or)
    override fun visit(node: Shl) = updateWith(node, TargetValue::shl)
    override fun visit(node: Shr) = updateWith(node, TargetValue::shr)
    override fun visit(node: Shrs) = updateWith(node, TargetValue::shrs)

    override fun visit(node: Div) = updateWith(node, node.left, node.right, TargetValue::div)
    override fun visit(node: Mod) = updateWith(node, node.left, node.right, TargetValue::mod)

    override fun visit(node: Conv) {
        val value = getValueOf(node.op)
        if (value.isConstant) {
            update(node, value.convertTo(node.mode))
        } else { // unknown or bad
            update(node, value)
        }
    }

    override fun visit(node: Cmp) {
        fun TargetValue.cmp(other: TargetValue): TargetValue {
            val relation = compare(other)
            return if (node.relation.contains(relation)) {
                TargetValue.getBTrue()
            } else {
                TargetValue.getBFalse()
            }
        }

        updateWith(node, TargetValue::cmp)
    }

    override fun visit(node: Phi) {
        if (node.mode == Mode.getM())
            return // has no value

        val value = node.preds.asSequence().map(::getValueOf).supremum()
        update(node, value)
    }

    override fun visit(node: Proj) {
        if (node.mode == Mode.getM())
            return // has no value

        val value = when (val pred = node.pred) {
            is Div, is Mod, is Proj -> getValueOf(pred)
            is Start, is Cond, is Call, is Load -> Bad
            else -> error("unexpected node type: ${pred.javaClass.simpleName}")
        }
        update(node, value)
    }

    /*
     * Store selector value in condition because when encountering the condition during transformation, the selector
     * might have already been replaced by a constant and lookup in nodeValues with the new value of [cond.selector]
     * thereby fails. Also simplifies the code in [replaceConstants].
     */
    override fun visit(node: Cond) = update(node, getValueOf(node.selector))

    // ignored
    override fun visit(node: Address) {}
    override fun visit(node: Block) {}
    override fun visit(node: Call) {}
    override fun visit(node: End) {}
    override fun visit(node: Jmp) {}
    override fun visit(node: Load) {}
    override fun visit(node: Return) {}
    override fun visit(node: Start) {}
    override fun visit(node: Store) {}
    override fun visit(node: Unknown) {}
}

/**
 * Replace all nodes within [nodeValues] that have a constant [TargetValue] with a [Const] node.
 */
private fun replaceConstants(graph: Graph, nodeValues: Map<Node, TargetValue>): Boolean {
    var hasChanged = false
    nodeValues.forEach { (node, targetValue) ->
        if (!targetValue.isConstant) {
            return@forEach
        }

        when (node) {
            is Const -> {}
            is Div -> {
                graph.replaceWithMem(node, node.mem, targetValue)
                hasChanged = true
            }
            is Mod -> {
                graph.replaceWithMem(node, node.mem, targetValue)
                hasChanged = true
            }
            is Cond -> {
                graph.replaceCondition(node, targetValue.isOne)
                hasChanged = true
            }
            else -> {
                graph.replace(node, targetValue)
                hasChanged = true
            }
        }
    }
    return hasChanged
}

fun Graph.replace(node: Node, targetValue: TargetValue) = Graph.exchange(node, newConst(targetValue))

fun Graph.replaceWithMem(node: Node, mem: Node, targetValue: TargetValue) {
    val constNode = newConst(targetValue)
    BackEdges.getOuts(node).forEach { edge ->
        Graph.exchange(
            edge.node,
            if (edge.node.mode == Mode.getM()) {
                mem
            } else {
                constNode
            }
        )
    }
}

fun BackEdges.Edge.rewireTo(target: Node) {
    node.setPred(pos, target)
}

fun Graph.replaceCondition(node: Cond, selector: Boolean) {
    val (trueProj, falseProj) = node.getBranches()

    val trueEdge = BackEdges.getOuts(trueProj).first()
    val falseEdge = BackEdges.getOuts(falseProj).first()

    if (selector) {
        trueEdge.rewireTo(newJmp(node.block))
        falseEdge.rewireTo(newBad(Mode.getX()))
    } else {
        falseEdge.rewireTo(newJmp(node.block))
        trueEdge.rewireTo(newBad(Mode.getX()))
    }

    keepAlive(node.block)
}

/**
 * Get true and false [Proj] of the [condition][Cond].
 */
fun Cond.getBranches(): Pair<Proj, Proj> {
    val branches = BackEdges.getOuts(this).map { it.node }.toList()
    check(branches.size == 2) { "invalid condition" }

    val (first, second) = branches
    first as Proj
    second as Proj

    return if (first.num == Cond.pnTrue) {
        first to second
    } else {
        second to first
    }
}
