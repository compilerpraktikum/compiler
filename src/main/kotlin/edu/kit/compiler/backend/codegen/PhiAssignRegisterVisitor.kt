package edu.kit.compiler.backend.codegen

import edu.kit.compiler.optimization.FirmNodeVisitorAdapter
import firm.Mode
import firm.nodes.Node
import firm.nodes.Phi

class PhiAssignRegisterVisitor : FirmNodeVisitorAdapter() {
    val map: MutableMap<Node, CodeGenIR> = mutableMapOf()
    val registerTable = VirtualRegisterTable()
    override fun visit(node: Phi) {
        super.visit(node)
        if (node.mode == Mode.getM()) {
            return
        }

        val reg = registerTable.newRegisterFor(node)
        map[node] = CodeGenIR.RegisterRef(reg)
        registerTable.putNode(map[node]!!, reg)
    }
}
