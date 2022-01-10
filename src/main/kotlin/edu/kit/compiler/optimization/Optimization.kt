package edu.kit.compiler.optimization

import edu.kit.compiler.Compiler
import firm.Program

/**
 * Perform constant propagation and folding.
 */
private fun constantPropagationAndFolding() {
    Program.getGraphs().forEach {
        doConstantPropagationAndFolding(it)
    }
}

fun doOptimization(level: Compiler.OptimizationLevel) {
    constantPropagationAndFolding()
}
