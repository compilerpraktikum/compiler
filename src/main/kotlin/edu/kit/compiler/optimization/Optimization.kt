package edu.kit.compiler.optimization

import firm.Graph
import firm.Program

object Optimization {

    /**
     * Perform constant propagation and folding on the given [method graph][Graph].
     */
    fun constantPropagationAndFolding(dumpMethodGraph: Boolean = false) {
        Program.getGraphs().forEach {
            doConstantPropagationAndFolding(it)
            if (dumpMethodGraph) firm.Dump.dumpGraph(it, "afterConstantPropAndFold")
        }
    }
}
