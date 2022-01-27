package edu.kit.compiler.backend.codegen

import edu.kit.compiler.optimization.FirmNodeVisitorAdapter
import firm.Mode
import firm.nodes.Node
import firm.nodes.Phi

class PhiAssignRegisterVisitor(val registerTable: VirtualRegisterTable = VirtualRegisterTable()) : FirmNodeVisitorAdapter() {
    val map: MutableMap<Node, CodeGenIR> = mutableMapOf()

    override fun visit(node: Phi) {
        println("phi test ${node.mode}")
        super.visit(node)
        if (node.mode == Mode.getM()) {
            return
        }

        val reg = registerTable.getOrCreateRegisterFor(node)
        map[node] = CodeGenIR.RegisterRef(reg)
    }
}
