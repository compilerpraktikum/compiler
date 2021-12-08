package edu.kit.compiler.optimization

import edu.kit.compiler.semantic.AstNode
import firm.Dump
import firm.Program

object Optimization {

    /**
     * Transform a [Semantic AST][AstNode] into a firm graph
     */
    fun constantPropagationAndFolding() {
        Program.getGraphs().forEach {
            ConstantPropagationAndFoldingVisitor(it).doConstantPropagationAndFolding()

            Dump.dumpGraph(it, "afterConstantPropAndFold")
        }
    }
}
