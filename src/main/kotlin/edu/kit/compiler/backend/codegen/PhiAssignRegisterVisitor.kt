package edu.kit.compiler.backend.codegen

import edu.kit.compiler.optimization.FirmNodeVisitorAdapter
import firm.Mode
import firm.nodes.Node
import firm.nodes.Phi

class PhiAssignRegisterVisitor(
    private val registerTable: VirtualRegisterTable = VirtualRegisterTable(),
    val map: MutableMap<Node, CodeGenIR> = mutableMapOf()
) : FirmNodeVisitorAdapter() {

    override fun visit(node: Phi) {
        super.visit(node)
        if (node.mode == Mode.getM()) {
            return
        }

        val reg = registerTable.getOrCreateRegisterFor(node)
        map[node] = CodeGenIR.RegisterRef(reg)
    }
}
