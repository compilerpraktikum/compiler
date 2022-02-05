package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.utils.DirectedGraph
import edu.kit.compiler.utils.Edge

data class Move(
    val source: Register,
    val destination: Register,
)

private typealias RegisterGraph = DirectedGraph<Register>

private fun RegisterGraph.findEdgeToLeaf() = edgeSequence().find { outDegree(it.end) == 0 }

private fun RegisterGraph.anyEdge() = edgeSequence().first()

private fun RegisterGraph.onlyInEdge(node: Register) = inEdges(node).toList().also {
    // should have been handled in validation phase during graph construction
    check(it.size <= 1) { "internal error: multiple sources for $node" }
}.first()

private fun Edge<Register>.toMove() = Move(start, end)

/**
 * Generates a sequence of [move operations][Move] that implements the given [permutation] of registers.
 * @param[permutation] permutation of registers that may have conflicts (e.g. a -> b, b -> a)
 * @param[generateTempRegister] generates a new [RegisterId] that can be used as a temporary register for conflict resolution
 * @return a sequence of [moves][Move] without any conflicts (e.g. b -> temp, a -> b, temp -> a)
 */
fun generateMoveSequence(permutation: List<Move>, generateTempRegister: (Width) -> Register): List<Move> {
    val moves = mutableListOf<Move>()

    val graph = RegisterGraph().apply {
        permutation.forEach {
            addEdge(it.source, it.destination)
        }

        // validate
        edgeSequence().forEach {
            check(inDegree(it.end) == 1) { "invalid permutation: multiple sources for ${it.end}" }
        }
    }

    // simple case (acyclic)
    var leafEdge = graph.findEdgeToLeaf()
    while (leafEdge != null) {
        moves.add(leafEdge.toMove())
        graph.removeEdge(leafEdge)
        leafEdge = graph.findEdgeToLeaf()
    }

    // break cycles
    while (!graph.isEmpty()) {
        val breakingEdge = graph.anyEdge()
        val tempRegister = generateTempRegister(breakingEdge.end.width) // TODO should we reuse this? not sure what's easier for register allocation

        moves.add(Move(breakingEdge.end, tempRegister))
        moves.add(breakingEdge.toMove())
        graph.removeEdge(breakingEdge)

        var currentEdge = graph.onlyInEdge(breakingEdge.start)
        while (currentEdge.start != breakingEdge.end) {
            moves.add(currentEdge.toMove())
            graph.removeEdge(currentEdge)
            currentEdge = graph.onlyInEdge(currentEdge.start)
        }

        moves.add(Move(tempRegister, currentEdge.end))
        graph.removeEdge(currentEdge)
    }

    return moves
}
