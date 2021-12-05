package edu.kit.compiler.optimization

import firm.BackEdges
import firm.Graph
import firm.TargetValue
import firm.nodes.Add
import firm.nodes.And
import firm.nodes.Cmp
import firm.nodes.Cond
import firm.nodes.Div
import firm.nodes.Load
import firm.nodes.Minus
import firm.nodes.Mul
import firm.nodes.Node
import firm.nodes.Not
import firm.nodes.Return
import firm.nodes.Store
import firm.nodes.Sub
import java.util.Stack

/**
 * What is expected seems to be a mix of
 *      * Constant Folding (VL and compileroptimizations.com: x = 1 + 8 * 2; ==> x = 17;) and
 *      * Constant Propagation (VL and compileroptimizations.com:
 *          x = 3;
 *          y = x + 4;
 *          ==>
 *          x = 3;
 *          y = 7;
 *      )
 *      Other Example from the Sitzung 8:
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
class ConstantPropagationAndFoldingVisitor() : AbstractNodeVisitor() {

    private var hasChanged = false
    private val workList: Stack<Node> = Stack()
    private val foldMap: MutableMap<Node, TargetValue> = HashMap()
    private val bottomNode = TargetValue.getUnknown()
    private val topNode = TargetValue.getBad()

    private fun consumeHasChanged(): Boolean {
        val tmp = hasChanged
        hasChanged = false
        return tmp
    }

    fun doConstantPropagationAndFolding(graph: Graph) {

        // collect relevant nodes
        graph.walkTopological(ConstantPropagationAndFoldingNodeCollector(workList, foldMap))

        // prepare propagation and folding by performing data river analysis
        while (!workList.empty()) {
            val node = workList.pop()
            node.accept(this)
            if (consumeHasChanged()) {
                BackEdges.getOuts(node).forEach { workList.push(it.node) }
            }
        }
    }

    // TODO write accept methods that write the foldMap and alter hasChanged.
    override fun visit(node: Add) {
        doAndRecordChange(node) {
            if (foldMap[node.left] == bottomNode || foldMap[node.right] == topNode) {
                foldMap[node] = bottomNode
            } else if (foldMap[node.left]!!.isConstant && foldMap[node.right]!!.isConstant) { // if !! fails, init is buggy.
                foldMap[node] = foldMap[node.left]!!.add(foldMap[node.right])
            } else {
                foldMap[node] = topNode
            }
        }
    }

    private fun doAndRecordChange(node: Node, function: () -> Unit) {
        val prev = foldMap[node]
        function()
        if (prev != foldMap[node]) {
            hasChanged = true
        }
    }
}

/**
 * Collect all relevant nodes and initializes them in the foldMap with ⊥ = TargetValue.getUnknown
 */
class ConstantPropagationAndFoldingNodeCollector(private val worklist: Stack<Node>, private val foldMap: MutableMap<Node, TargetValue>) : AbstractNodeVisitor() {
    private fun init(node: Node) {
        worklist.push(node)
        foldMap[node] = TargetValue.getUnknown()
    }
    // TODO determine which nodes to collect

    override fun visit(node: Store) {
        init(node)
    }

    override fun visit(node: Load) {
        init(node)
    }

    override fun visit(node: Add) {
        init(node)
    }
    override fun visit(node: Sub) {
        init(node)
    }
    override fun visit(node: Mul) {
        init(node)
    }
    override fun visit(node: Div) {
        init(node)
    }

    override fun visit(node: Minus) {
        init(node)
    }

    override fun visit(node: Return) {
        init(node)
    }

    override fun visit(node: Cmp) {
        init(node)
    }

    // bool stuff also to be folded.

    override fun visit(node: And) {
        init(node)
    }
    override fun visit(node: Cond) {
        init(node)
    }
    override fun visit(node: Not) {
        init(node)
    }
}
