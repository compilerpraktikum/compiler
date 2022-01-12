package edu.kit.compiler.optimization

import firm.BackEdges
import firm.Graph
import firm.nodes.Load
import firm.nodes.Proj
import firm.nodes.Store
import java.util.Stack

/**
 * Collect all relevant store nodes
 */
class StoreAfterStoreNodeCollector(
    // TODO
    private val LocalVarStoreStack: Stack<Store>,
    private val localVarStoreToAddressProjNodeMap: HashMap<Store, Proj>,
    private val localVarLoadToAddressProjNodeMap: HashMap<Load, Proj>,
) : FirmNodeVisitorAdapter() {

    override fun visit(node: Store) {
        // TODO some kind of Erkennung arrayaccess/ local var access etc? Or on-the-fly Erkennung und hier nur store nodes sammeln.
        // TODO Grundblöcke? Worklist muss nach Kontrollfluss sortiert sein! Was leistet der topologische Iterator da?

        // -- case: this.vari = ..; --
        // 1 = "memory address"
        val possibleMemProj = node.getPred(1)
        if (possibleMemProj is Proj) {
            LocalVarStoreStack.push(node)
            localVarStoreToAddressProjNodeMap[node] = possibleMemProj
        }

        // -- case: this.arr[5] = ...; TODO need to traverse much nodes, see testForwardStore.mj
    }

    override fun visit(node: Load) {

        // -- case: this.vari = ..; --
        // 1 = "memory address"
        val possibleMemProj = node.getPred(1)
        if (possibleMemProj is Proj) {
            localVarLoadToAddressProjNodeMap[node] = possibleMemProj
        }
    }
}

/**
 * Perform store after store optimization on the given [method graph][Graph].
 */
fun doStoreAfterStoreOptimization(graph: Graph) {
    val LocalVarStoreStack: Stack<Store> = Stack()
    val localVarStoreToAddressProjNodeMap = HashMap<Store, Proj>()
    val localVarLoadToAddressProjNodeMap = HashMap<Load, Proj>()

    // firm meckert sonst.
    BackEdges.enable(graph)

    println("---------------------[ storeAfterStore($graph) ${" ".repeat(80 - graph.toString().length)}]---------------------")
    graph.walkTopological(StoreAfterStoreNodeCollector(LocalVarStoreStack, localVarStoreToAddressProjNodeMap, localVarLoadToAddressProjNodeMap))

    println("--[ LocalVarStoreStack ]--")
    LocalVarStoreStack.forEach {
        println("  - $it ${" ".repeat(35 - it.toString().length)}")
    }
    println("--[ localVarStoreToAddressProjNodeMap ]--")
    localVarStoreToAddressProjNodeMap.forEach {
        println("  - $it ${" ".repeat(35 - it.toString().length)}")
    }
    println("--[ localVarLoadToAddressProjNodeMap ]--")
    localVarLoadToAddressProjNodeMap.forEach {
        println("  - $it ${" ".repeat(35 - it.toString().length)}")
    }

//    ConstantPropagationAndFoldingTransformationVisitor(
//        graph,
//        ConstantPropagationAndFoldingAnalysisVisitor(graph)
//            .doConstantPropagationAndFoldingAnalysis()
//    ).transform()

    BackEdges.disable(graph)
}
