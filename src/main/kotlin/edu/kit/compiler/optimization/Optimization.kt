package edu.kit.compiler.optimization

import edu.kit.compiler.Compiler
import edu.kit.compiler.utils.Logger
import firm.ClassType
import firm.Dump
import firm.Graph
import firm.Program

private fun Graph.display(): String =
    entity?.let {
        val parentName = it.owner?.let { owner -> (owner as? ClassType)?.name } ?: "<???>"
        "$parentName.${it.name}"
    } ?: "<???>"

/**
 * Optimizes the given [Graph]. Returns whether any changes have been made.
 */
data class Optimization(
    val name: String,
    val apply: (Graph) -> Boolean,
)

private fun getOptimizationsForLevel(level: Compiler.OptimizationLevel): List<Optimization> = when (level) {
    Compiler.OptimizationLevel.Base -> listOf(ConstantPropagationAndFolding)
    Compiler.OptimizationLevel.Full -> listOf(
        ConstantPropagationAndFolding,
        ArithmeticOptimization,
    )
}

private fun runOptimizations(graph: Graph, optimizations: List<Optimization>, dumpAfterEachOptimization: Boolean) {
    var iteration = 0
    do {
        Logger.trace { "    Iteration ${++iteration}" }
        var hasChangedAtLeastOnce = false
        optimizations.forEachIndexed { index, opt ->
            Logger.trace { "      - Applying ${opt.name}" }
            val hasChanged = opt.apply(graph)
            hasChangedAtLeastOnce = hasChangedAtLeastOnce || hasChanged
            Logger.trace { "         -> Has Changed? ${ if (hasChanged) "yes" else "no" }" }
            if (dumpAfterEachOptimization) {
                Dump.dumpGraph(graph, "after-optimization-$iteration-${index + 1}-${opt.name.replace("\\s".toRegex(), "-")}")
            }
        }
    } while (hasChangedAtLeastOnce)
}

fun doOptimization(level: Compiler.OptimizationLevel, dumpAfterEachOptimization: Boolean) {
    Logger.debug { "Running optimizations (level: ${level.intValue}) ..." }
    val optimizations = getOptimizationsForLevel(level)
    Program.getGraphs().forEach {
        Logger.trace { "  Optimizing ${it.display()}" }
        runOptimizations(it, optimizations, dumpAfterEachOptimization)
    }
    Logger.trace { "Optimizations completed." }
}
