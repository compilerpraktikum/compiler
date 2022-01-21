package edu.kit.compiler.optimization

import edu.kit.compiler.Compiler
import edu.kit.compiler.Logger
import firm.Graph
import firm.Program

/**
 * Optimizes the given [Graph]. Returns whether any changes have been made.
 */
typealias Optimization = (Graph) -> Boolean

private fun getOptimizationsForLevel(level: Compiler.OptimizationLevel): List<Optimization> = when (level) {
    Compiler.OptimizationLevel.Base -> listOf(::doConstantPropagationAndFolding)
    Compiler.OptimizationLevel.Full -> listOf(
        ::doConstantPropagationAndFolding,
    )
}

private fun runOptimizations(graph: Graph, optimizations: List<Optimization>) {
    do {
        var hasChangedAtLeastOnce = false
        optimizations.forEach { opt ->
            val hasChanged = opt(graph)
            hasChangedAtLeastOnce = hasChangedAtLeastOnce || hasChanged
        }
    } while (hasChangedAtLeastOnce)
}

fun doOptimization(level: Compiler.OptimizationLevel) {
    Logger.debug { "Running optimizations (level: ${level.intValue}) ..." }
    val optimizations = getOptimizationsForLevel(level)
    Program.getGraphs().forEach {
        runOptimizations(it, optimizations)
    }
}
