package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Width
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
        var width: Width
        if (node.mode == Mode.getP()) {
            width = Width.WORD;
        }
        width = when (node.mode.sizeBytes) {
            1 -> Width.BYTE
            2 -> Width.DOUBLE
            4 -> Width.QUAD
            8 -> Width.WORD
            else -> TODO("weird size")
        }
        val reg = registerTable.newRegisterForPhi(node, width = width)
        map[node] = CodeGenIR.RegisterRef(reg)
        registerTable.putPhi(map[node]!!, reg)
    }
}
