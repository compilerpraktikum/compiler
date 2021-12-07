package edu.kit.compiler.optimization

import firm.BackEdges
import firm.Graph
import firm.Mode
import firm.Relation
import firm.TargetValue
import firm.nodes.Add
import firm.nodes.And
import firm.nodes.Binop
import firm.nodes.Cmp
import firm.nodes.Cond
import firm.nodes.Div
import firm.nodes.Load
import firm.nodes.Minus
import firm.nodes.Mul
import firm.nodes.Node
import firm.nodes.Not
import firm.nodes.Or
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
        // TODO use foldMap to perform the propagation/ folding.
    }

    // TODO write more accept methods that write the foldMap and alter hasChanged.
    override fun visit(node: Add) {
        intCalculation(node) {
            foldMap[node] = foldMap[node.left]!!.add(foldMap[node.right])
        }
    }
    override fun visit(node: Sub) {
        intCalculation(node) {
            foldMap[node] = foldMap[node.left]!!.sub(foldMap[node.right])
        }
    }
    override fun visit(node: Mul) {
        intCalculation(node) {
            foldMap[node] = foldMap[node.left]!!.mul(foldMap[node.right])
        }
    }
    override fun visit(node: Div) {
        // div is not a binop.
        doAndRecordFoldMapChange(node) {
            if (foldMap[node.left] == bottomNode || foldMap[node.right] == bottomNode) {
                foldMap[node] = bottomNode
            } else if (foldMap[node.left]!!.isConstant && foldMap[node.right]!!.isConstant) { // if !! fails, init is buggy.
                if (!foldMap[node.right]!!.isNull) {
                    foldMap[node] = foldMap[node.left]!!.div(foldMap[node.right])
                } else foldMap[node] = topNode // TODO reassure that that is correct
            } else {
                foldMap[node] = topNode
            }
        }
    }

    // todo not rly sure
    override fun visit(node: Minus) {
        doAndRecordFoldMapChange(node) {
            if (foldMap[node.block] == bottomNode) {
                foldMap[node] = bottomNode
            } else if (foldMap[node.block]!!.isConstant) { // if !! fails, init is buggy.
                foldMap[node] = foldMap[node.block]!!.neg()
            } else {
                foldMap[node] = topNode
            }
        }
    }

    override fun visit(node: And) {
        intCalculation(node) {
            foldMap[node] = TargetValue(
                if (getAsBool(node.left) && getAsBool(node.right)) 1 else 0,
                Mode.getBu().type.mode
            )
        }
    }

    override fun visit(node: Or) {
        intCalculation(node) {
            foldMap[node] = TargetValue(
                if (getAsBool(node.left) || getAsBool(node.right)) 1 else 0,
                Mode.getBu().type.mode
            )
        }
    }
//    override fun visit(node: Cond) {
//        TODO("implement")
//    }
    override fun visit(node: Not) {
        doAndRecordFoldMapChange(node) {
            if (foldMap[node.block] == bottomNode) {
                foldMap[node] = bottomNode
            } else if (foldMap[node.block]!!.isConstant) { // if !! fails, init is buggy.
                foldMap[node] = getAsTargetValueBool(node.block)
            } else {
                foldMap[node] = topNode
            }
        }
    }

    override fun visit(node: Store) {
        TODO("implement")
    }
    override fun visit(node: Load) {
        TODO("implement")
    }
    override fun visit(node: Return) {
        TODO("implement")
    }
    override fun visit(node: Cmp) {
        intCalculation(node) {
//            foldMap[node] = TargetValue(
//                when (node.relation) {
//                    Relation.Equal -> foldMap[node.left].compare(foldMap[node.right])
//                    Relation.LessGreater -> TODO()
//                    Relation.Less -> TODO()
//                    Relation.Greater -> TODO()
//                    Relation.LessEqual -> TODO()
//                    Relation.GreaterEqual -> TODO()
//                }
//            )
            // todo don't know if that does it..
            foldMap[node] = getAsTargetValueBool(foldMap[node.left]!!.compare(foldMap[node.right]))
        }
    }

    private fun getAsTargetValueBool(relation: Relation): TargetValue = TargetValue(
        if (getAsBool(relation)) 1 else 0,
        Mode.getBu().type.mode
    )

    private fun getAsTargetValueBool(node: Node): TargetValue = TargetValue(
        if (getAsBool(node)) 1 else 0,
        Mode.getBu().type.mode
    )

    private fun getAsBool(relation: Relation): Boolean {
        return when (relation) {
            Relation.False -> false
            Relation.True -> true
            else -> TODO("error in getAsBool better handling")
        }
    }

    private fun intCalculation(node: Binop, doConstOperation: () -> Unit) {
        doAndRecordFoldMapChange(node) {
            if (foldMap[node.left] == bottomNode || foldMap[node.right] == bottomNode) {
                foldMap[node] = bottomNode
            } else if (foldMap[node.left]!!.isConstant && foldMap[node.right]!!.isConstant) { // if !! fails, init is buggy.
                doConstOperation()
            } else {
                foldMap[node] = topNode
            }
        }
    }

    private fun getAsBool(node: Node): Boolean {
        if (foldMap[node]!!.isOne || foldMap[node]!!.asInt() == 0) {
            return foldMap[node]!!.isOne
        } else
        // TODO better handling
            throw RuntimeException("Tried to convert value other than 0 or 1 to bool")
    }

    private fun doAndRecordFoldMapChange(node: Node, function: () -> Unit) {
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

    override fun visit(node: And) {
        init(node)
    }
    override fun visit(node: Or) {
        init(node)
    }
    override fun visit(node: Cond) {
        init(node)
    }
    override fun visit(node: Not) {
        init(node)
    }
}
