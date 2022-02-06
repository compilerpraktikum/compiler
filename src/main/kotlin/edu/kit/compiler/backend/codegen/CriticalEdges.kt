package edu.kit.compiler.backend.codegen

import firm.Graph
import firm.nodes.Block
import firm.nodes.Cond
import firm.nodes.Node
import firm.nodes.NodeVisitor
import firm.nodes.Phi
import firm.nodes.Proj
import firm.nodes.Switch

fun breakCriticalEdges(graph: Graph) {
    val blockHasPhi = mutableMapOf<Block, Boolean>()
    graph.walk(object : NodeVisitor.Default() {
        override fun visit(node: Phi) {
            blockHasPhi[node.block as Block] = true
        }
    })

    graph.walkBlocksPostorder { block ->
        // if the block has no phi, then there is no need to break any edges because SSA deconstruction does not need to inject anything in the previous block
        if (blockHasPhi[block] == true) {
            block.preds.forEachIndexed { index, pred ->
                // no need to add another indirection if the previous block has only one successor anyway
                if (pred.hasMultipleSuccessors()) {
                    val newBlock = graph.newBlock(arrayOf(pred))
                    val jump = graph.newJmp(newBlock)
                    block.setPred(index, jump)
                }
            }
        }
    }
}

private fun Node.hasMultipleSuccessors(): Boolean {
    var node = this
    while (node is Proj) {
        node = node.pred
    }
    return node is Cond || node is Switch
}
