package edu.kit.compiler.backend.codegen;

import firm.Graph
import firm.nodes.Block

object NameMangling {
    fun mangleBlockName(block: Block) = "block_${block.nr}"
    fun mangleFunctionName(graph: Graph) = "function_return_${graph.entity.ldName}"
}
