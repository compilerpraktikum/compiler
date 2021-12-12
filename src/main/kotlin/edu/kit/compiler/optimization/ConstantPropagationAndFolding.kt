package edu.kit.compiler.optimization

import firm.BackEdges
import firm.Graph
import firm.Mode
import firm.Relation
import firm.TargetValue
import firm.nodes.Add
import firm.nodes.And
import firm.nodes.Binop
import firm.nodes.Call
import firm.nodes.Cmp
import firm.nodes.Const
import firm.nodes.Conv
import firm.nodes.Div
import firm.nodes.Minus
import firm.nodes.Mod
import firm.nodes.Mul
import firm.nodes.Node
import firm.nodes.Not
import firm.nodes.Or
import firm.nodes.Phi
import firm.nodes.Proj
import firm.nodes.Shl
import firm.nodes.Shr
import firm.nodes.Shrs
import firm.nodes.Sub
import java.util.Stack

class ConstantPropagationAndFoldingTransformationVisitor(private val graph: Graph, private val foldMap: MutableMap<Node, TargetValue>) : AbstractNodeVisitor() {

    override fun visit(node: Add) = exchangeNodeTargetValue(node)
    override fun visit(node: Sub) = exchangeNodeTargetValue(node)
    override fun visit(node: Mul) = exchangeNodeTargetValue(node)
    override fun visit(node: Mod) = exchangeDivModTargetValue(node, node.mem)
    override fun visit(node: Div) = exchangeDivModTargetValue(node, node.mem)
    override fun visit(node: Minus) = exchangeNodeTargetValue(node)

    override fun visit(node: Cmp) { } // TODO("implement")
    override fun visit(node: Conv) { } // TODO("implement")

    // gibts zwar nicht im Standard, aber kann ggf als Ergebnis einer anderen Optimierung auftreten (Integer Multiply Optimization)
    override fun visit(node: Shl) = exchangeNodeTargetValue(node)
    override fun visit(node: Shr) = exchangeNodeTargetValue(node)
    override fun visit(node: Shrs) = exchangeNodeTargetValue(node)

    override fun visit(node: And) = exchangeNodeTargetValue(node)
    override fun visit(node: Or) = exchangeNodeTargetValue(node)
    override fun visit(node: Not) = exchangeNodeTargetValue(node)

    override fun visit(node: Call) { } // TODO("implement")
    override fun visit(node: Phi) = exchangeNodeTargetValue(node)

    private fun exchangeNodeTargetValue(node: Node) = Graph.exchange(node, graph.newConst(foldMap[node]))

    private fun exchangeDivModTargetValue(node: Node, memNode: Node) {
        BackEdges.getOuts(node).forEach { outBackEdge ->
            Graph.exchange(
                outBackEdge.node,
                if (outBackEdge.node.mode == Mode.getM()) memNode else graph.newConst(foldMap[node])
            )
        }
    }

    /**
     * perform the propagation/ folding for nodes with targetValues ∉ {⊤, ⊥}
     */
    fun transform() {
        val transformationVisitor = ConstantPropagationAndFoldingTransformationVisitor(graph, foldMap)
        foldMap.filter { it.value.isConstant }
            .forEach { it.key.accept(transformationVisitor) }
    }
}

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
class ConstantPropagationAndFoldingAnalysisVisitor(private val graph: Graph) : AbstractNodeVisitor() {

    class FoldMap : HashMap<Node, TargetValue>() {

        fun getBottomNode(): TargetValue = TargetValue.getUnknown()
        fun getTopNode(): TargetValue = TargetValue.getBad()

        override fun put(key: firm.nodes.Node, value: TargetValue): TargetValue? =
            if (!this.contains(key)) {
                super.put(key, value)
            } else if (!this[key]!!.isConstant || this[key]!! == value) {
                // endless loop prevention. Only set constants once! TODO may not be the cleanest way..
                super.put(key, value)
            } else super.put(key, this.getTopNode())
    }

    private var hasChanged = false
    private val workList: Stack<Node> = Stack()
    private val foldMap = FoldMap()
    private val bottomNode = foldMap.getBottomNode()
    private val topNode = foldMap.getTopNode()

    private val DEBUG = false

    /**
     * perform the constant propagation and folding analysis and return what has to be changed.
     */
    fun doConstantPropagationAndFoldingAnalysis(): MutableMap<Node, TargetValue> {
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

        // TODO rmv debug
        println("---------------------[ foldMap($graph) ${" ".repeat(30 - graph.toString().length)}]---------------------")
        foldMap.forEach {
            println("  - ${it.key} ${" ".repeat(35 - it.key.toString().length)} -> ${targetValueToString(it.value)}")
        }
        return foldMap
    }

    /**
     * println if DEBUG
     */
    private fun println(string: String) { if (DEBUG) kotlin.io.println(string) }

    private fun targetValueToString(targetValue: TargetValue): String =
        if (targetValue == topNode) "⊤" else if (targetValue == bottomNode) "⊥" else targetValue.toString()

    override fun visit(node: Add) = intCalculationSimpleFold(node, TargetValue::add)
    override fun visit(node: Sub) = intCalculationSimpleFold(node, TargetValue::sub)
    override fun visit(node: Shl) = intCalculationSimpleFold(node, TargetValue::shl)
    override fun visit(node: Shr) = intCalculationSimpleFold(node, TargetValue::shr)
    override fun visit(node: Shrs) = intCalculationSimpleFold(node, TargetValue::shrs)
    override fun visit(node: Mul) = intCalculationSimpleFold(node, TargetValue::mul)
    override fun visit(node: Mod) = doAndRecordFoldMapChange(node) {
        // Mod is not a binop.
        if (foldMap[node.left] == bottomNode || foldMap[node.right] == bottomNode) {
            foldMap[node] = bottomNode
        } else if (foldMap[node.left]!!.isConstant && foldMap[node.right]!!.isConstant) { // if !! fails, init is buggy.
            if (!foldMap[node.right]!!.isNull) {
                foldMap[node] = foldMap[node.left]!!.mod(foldMap[node.right])
            } else foldMap[node] = topNode
        } else {
            foldMap[node] = topNode
        }
    }
    override fun visit(node: Div) = doAndRecordFoldMapChange(node) {
        // Div is not a binop.
        if (foldMap[node.left] == bottomNode || foldMap[node.right] == bottomNode) {
            foldMap[node] = bottomNode
        } else if (foldMap[node.left]!!.isConstant && foldMap[node.right]!!.isConstant) { // if !! fails, init is buggy.
            if (!foldMap[node.right]!!.isNull) {
                foldMap[node] = foldMap[node.left]!!.div(foldMap[node.right])
            } else foldMap[node] = topNode
        } else {
            foldMap[node] = topNode
        }
    }

    // todo not rly sure
    override fun visit(node: Minus) = doAndRecordFoldMapChange(node) {
        if (foldMap[node.getPred(0)] == bottomNode) {
            foldMap[node] = bottomNode
        } else if (foldMap[node.getPred(0)]!!.isConstant) { // if !! fails, init is buggy.
            foldMap[node] = foldMap[node.getPred(0)]!!.neg()
        } else {
            foldMap[node] = topNode
        }
    }

    override fun visit(node: And) = intCalculation(node) {
        foldMap[node] = TargetValue(
            if (getAsBool(node.left) && getAsBool(node.right)) 1 else 0,
            Mode.getBu().type.mode
        )
    }

    override fun visit(node: Or) = intCalculation(node) {
        foldMap[node] = TargetValue(
            if (getAsBool(node.left) || getAsBool(node.right)) 1 else 0,
            Mode.getBu().type.mode
        )
    }

    override fun visit(node: Not) = doAndRecordFoldMapChange(node) {
        if (foldMap[node.getPred(0)] == bottomNode) {
            foldMap[node] = bottomNode
        } else if (foldMap[node.getPred(0)]!!.isConstant) { // if !! fails, init is buggy.
            foldMap[node] = getAsTargetValueBool(node.getPred(0)) // TODO wtf, this should be inversed
        } else {
            foldMap[node] = topNode
        }
    }

    override fun visit(node: Cmp) = intCalculation(node) {
        // correct, if compare always returns True or False (which it should, if both targetValues are constants?)
        println("CMP_DEBUG Comparing: ${node.left}, ${node.right}")

        foldMap[node] = compareRelationsAsTargetValueBool(node.relation, foldMap[node.left]!!.compare(foldMap[node.right]))
    }

    override fun visit(node: Const) {
        doAndRecordFoldMapChange(node) {
            foldMap[node] = node.tarval
        }
    }

    override fun visit(node: Phi) = doAndRecordFoldMapChange(node) {
        // todo there are only Phis of length 2, right?
//        println(
//            "PHI_DEBUG($graph) $node  " + "${node.predCount}" +
//                ", ${node.getPred(0)} [${orderOfTargetValue(foldMap[node.getPred(0)]!!)}]" +
//                ", ${node.getPred(1)} [${orderOfTargetValue(foldMap[node.getPred(1)]!!)}]_([${foldMap[node.getPred(1)]}])"
//        )
        foldMap[node] =
            if (node.predCount != 2) topNode
            else if (foldMap[node.getPred(0)]!!.mode == Mode.getBu() || foldMap[node.getPred(1)]!!.mode == Mode.getBu()) {
                // no booleans currently TODO maybe integrate them. Not that easy.
                topNode
            } else if (orderOfTargetValue(foldMap[node.getPred(0)]!!) > orderOfTargetValue(foldMap[node.getPred(1)]!!))
                foldMap[node.getPred(0)]!!
            else foldMap[node.getPred(1)]!!
    }

    private fun compareRelationsAsTargetValueBool(expectedRelation: Relation, gottenRelation: Relation) = TargetValue(
        if (compareRelations(expectedRelation, gottenRelation)) 1 else 0,
        Mode.getBu().type.mode
    )

    private fun getAsTargetValueBool(node: Node) = TargetValue(
        if (getAsBool(node)) 1 else 0,
        Mode.getBu().type.mode
    )

    private fun compareRelations(expectedRelation: Relation, relation: Relation): Boolean = expectedRelation.contains(relation)

    private fun intCalculationSimpleFold(node: Binop, doSimpleFoldConstOperation: (TargetValue, TargetValue) -> TargetValue) =
        intCalculation(node) {
            foldMap[node] = doSimpleFoldConstOperation(foldMap[node.left]!!, (foldMap[node.right]!!))
        }

    private fun intCalculation(node: Binop, doConstOperation: () -> Unit) = doAndRecordFoldMapChange(node) {
        if (foldMap[node.left] == bottomNode || foldMap[node.right] == bottomNode) {
            foldMap[node] = bottomNode
        } else if (foldMap[node.left]!!.isConstant && foldMap[node.right]!!.isConstant) { // if !! fails, init is buggy.
            doConstOperation()
        } else {
            foldMap[node] = topNode
        }
    }

    private fun getAsBool(node: Node): Boolean =
        if (foldMap[node]!!.isOne || foldMap[node]!!.asInt() == 0) {
            foldMap[node]!!.isOne
        } else
        // TODO better handling
            throw RuntimeException("Tried to convert value other than 0 or 1 to bool")

    private fun doAndRecordFoldMapChange(node: Node, function: () -> Unit) {
        val prev = foldMap[node]
        function()
        if (prev != foldMap[node]) {
            hasChanged = true
        }
    }

    /**
     * Returns
     * * -1 | ⊥
     * * 0 | integers
     * * 1 | ⊤
     */
    private fun orderOfTargetValue(targetValue: TargetValue) =
        if (targetValue == bottomNode) -1 else if (targetValue == topNode) 1 else 0

    private fun consumeHasChanged(): Boolean {
        val tmp = hasChanged
        hasChanged = false
        return tmp
    }
}

/**
 * Collect all relevant nodes and initializes them in the foldMap with ⊥ = TargetValue.getUnknown
 *  TODO this pass is probably not necessary (may be integrated in the other)
 */
class ConstantPropagationAndFoldingNodeCollector(
    private val worklist: Stack<Node>,
    private val foldMap: MutableMap<Node, TargetValue>
) : AbstractNodeVisitor() {
    private fun init(node: Node) {
        worklist.push(node)
        foldMap[node] = TargetValue.getUnknown()
    }

    override fun visit(node: Add) = init(node)
    override fun visit(node: Sub) = init(node)
    override fun visit(node: Mul) = init(node)
    override fun visit(node: Mod) = init(node)
    override fun visit(node: Div) = init(node)
    override fun visit(node: Minus) = init(node)
    override fun visit(node: Cmp) = init(node)

    // TODO need to handle this in the other visitor, maybe? ArrayAccess stuff...
    override fun visit(node: Conv) = init(node)
    // needed because phi handling explodes elsewise
    override fun visit(node: Proj) = init(node)

    // gibts zwar nicht im Standard, aber kann ggf als Ergebnis einer anderen Optimierung auftreten (Integer Multiply Optimization)
    override fun visit(node: Shl) = init(node)
    override fun visit(node: Shr) = init(node)
    override fun visit(node: Shrs) = init(node)

    override fun visit(node: And) = init(node)
    override fun visit(node: Or) = init(node)
    override fun visit(node: Not) = init(node)

    override fun visit(node: Call) = init(node)
    override fun visit(node: Const) = init(node)
    override fun visit(node: Phi) = init(node)
}

/**
 * Perform constant propagation and folding on the given [method graph][Graph].
 */
fun doConstantPropagationAndFolding(graph: Graph) {
    // firm meckert sonst.
    BackEdges.enable(graph)
    ConstantPropagationAndFoldingTransformationVisitor(
        graph,
        ConstantPropagationAndFoldingAnalysisVisitor(graph)
            .doConstantPropagationAndFoldingAnalysis()
    ).transform()

    BackEdges.disable(graph)
}
