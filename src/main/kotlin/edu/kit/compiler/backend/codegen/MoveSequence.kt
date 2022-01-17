package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.util.DirectedGraph
import edu.kit.compiler.util.Edge

data class Move(
    val source: RegisterId,
    val destination: RegisterId,
)

private typealias RegisterGraph = DirectedGraph<RegisterId>

private fun RegisterGraph.findEdgeToLeaf() = edgeSequence().find { outDegree(it.end) == 0 }

private fun RegisterGraph.anyEdge() = edgeSequence().first()

private fun RegisterGraph.onlyInEdge(node: RegisterId) = inEdges(node).toList().also {
    // should have been handled in validation phase during graph construction
    check(it.size <= 1) { "internal error: multiple sources for $node" }
}.first()

private fun Edge<RegisterId>.toMove() = Move(start, end)

fun generateMoveSequence(permutation: List<Move>, generateTempRegister: () -> RegisterId): List<Move> {
    val moves = mutableListOf<Move>()

    val graph = DirectedGraph<RegisterId>().apply {
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
        val tempRegister = generateTempRegister() // TODO should we reuse this? not sure what's easier for register allocation
        val breakingEdge = graph.anyEdge()

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
