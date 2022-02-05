package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.optimization.FirmNodeVisitorAdapter
import firm.MethodType
import firm.nodes.End
import firm.nodes.Node
import firm.nodes.Proj
import firm.nodes.Start

/**
 * The visitor will allocate the correct registers for function arguments
 * (@0 ... @n) and store how many arguments the function needs
 */
class FunctionParameterVisitor(
    private val registerTable: VirtualRegisterTable,
    val argumentMapping: MutableMap<Node, CodeGenIR> = mutableMapOf(),
) : FirmNodeVisitorAdapter() {
    val parameters: MutableMap<Int, Proj> = mutableMapOf()
    var numberOfArguments: Int = 0

    override fun visit(node: End) {
        val methodType = node.graph.entity.type as MethodType
        numberOfArguments = methodType.nParams

        for (i in 0 until numberOfArguments) {
            val argType = methodType.getParamType(i)
            val matchingNode = parameters[i]
            if (matchingNode == null) {
                val width = Width.fromByteSize(argType.mode.sizeBytes)!!
                registerTable.newRegister(width)
            } else {
                allocateParameter(matchingNode)
            }
        }
    }

    override fun visit(node: Proj) {
        super.visit(node)

        when (val pred = node.pred) {
            is Proj -> {
                when (val origin = pred.pred) {
                    is Start -> {
                        parameters[node.num] = node
                    }
                }
            }
        }
    }

    private fun allocateParameter(node: Proj) {
        val reg = registerTable.getOrCreateRegisterFor(node)
        val registerRef = CodeGenIR.RegisterRef(reg)
        argumentMapping[node] = registerRef
    }
}
