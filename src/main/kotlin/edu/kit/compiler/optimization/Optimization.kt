package edu.kit.compiler.optimization

import firm.Dump
import firm.Graph
import firm.Program

object Optimization {

    /**
     * Perform constant propagation and folding on the given [method graph][Graph].
     */
    fun constantPropagationAndFolding() {
        Program.getGraphs().forEach {
            doConstantPropagationAndFolding(it)

            Dump.dumpGraph(it, "afterConstantPropAndFold")
        }
    }
}
