package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.optimization.FirmNodeVisitorAdapter
import firm.BackEdges
import firm.Mode
import firm.nodes.Address
import firm.nodes.Call
import firm.nodes.Cond
import firm.nodes.Div
import firm.nodes.Load
import firm.nodes.Mod
import firm.nodes.Node
import firm.nodes.Proj
import firm.nodes.Start
import firm.nodes.Store

/**
 * The visitor will allocate the correct registers for function arguments
 * (@0 ... @n) and store how many arguments the function needs
 */
class FunctionArgumentVisitor(
    private val registerTable: VirtualRegisterTable,
    val argumentMapping: MutableMap<Node, CodeGenIR> = mutableMapOf()
) : FirmNodeVisitorAdapter() {

    override fun visit(node: Proj) {
        super.visit(node)

        when (val pred = node.pred) {
            is Proj -> {
                when (val origin = pred.pred) {
                    is Start -> {
                        println("node: $node, pred: $pred, predpred: $origin")
                        allocateArguments(node)
                    }
                }
            }
        }
    }


    private fun allocateArguments(node: Proj) {
        val reg = registerTable.getOrCreateRegisterFor(node)
        val registerRef = CodeGenIR.RegisterRef(reg)
        argumentMapping[node] = registerRef
        println("setting nodeMap for $node to $registerRef")
    }
}
