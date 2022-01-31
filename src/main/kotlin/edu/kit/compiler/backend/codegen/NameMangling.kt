package edu.kit.compiler.backend.codegen;

import edu.kit.compiler.semantic.InternalFunction
import firm.Graph
import firm.nodes.Block

object NameMangling {
    fun mangleBlockName(block: Block) = "block_${block.nr}"
    fun mangleFunctionName(graph: Graph) = "function_return_${graph.entity.ldName}"

    fun isExternalFunction(functionName: String?): Boolean = externalFunctions.contains(functionName)

    private val externalFunctions = listOf(
        InternalFunction.SYSTEM_IN_READ.name,
        InternalFunction.SYSTEM_OUT_PRINTLN.name,
        InternalFunction.SYSTEM_OUT_WRITE.name,
        InternalFunction.SYSTEM_OUT_FLUSH.name,
        "allocate"
    )
}
