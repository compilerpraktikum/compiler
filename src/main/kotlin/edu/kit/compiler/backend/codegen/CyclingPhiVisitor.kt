package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.optimization.FirmNodeVisitorAdapter
import firm.BackEdges
import firm.Mode
import firm.nodes.Node
import firm.nodes.Phi

class CyclingPhiVisitor(
    val registerTable: VirtualRegisterTable = VirtualRegisterTable(),
) : FirmNodeVisitorAdapter() {

    override fun visit(node: Phi) {
        super.visit(node)
        if (node.mode == Mode.getM()) {
            return
        }

        val successors = BackEdges.getOuts(node).map { it.node }
        val predecessorAndSuccessorAreTheSame =
            successors.any { successor -> node.preds.any { pred -> pred == successor } }
        if (predecessorAndSuccessorAreTheSame) {
            val resultNode = node.graph.newConv(node.block, node, node.mode)
            successors.forEach { it.setPred(0, resultNode) }
        }
    }
}
