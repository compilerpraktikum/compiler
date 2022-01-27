package edu.kit.compiler.util

data class Edge<NodeId>(val start: NodeId, val end: NodeId)

class DirectedGraph<NodeId> {
    private val edges = mutableListOf<Edge<NodeId>>()

    fun isEmpty() = edges.isEmpty()

    fun addEdge(start: NodeId, end: NodeId) {
        check(start != end) { "self-cycles not allowed" }
        val edge = Edge(start, end)
        check(!edges.contains(edge)) { "multiple edges between the same nodes not allowed" }
        edges.add(edge)
    }

    fun removeEdge(edge: Edge<NodeId>) {
        val successful = edges.remove(edge)
        if (!successful) {
            error("no edge (${edge.start} -> ${edge.end}) in graph")
        }
    }
    fun removeEdge(start: NodeId, end: NodeId) = removeEdge(Edge(start, end))

    fun edgeSequence(): Sequence<Edge<NodeId>> = edges.asSequence()

    fun outEdges(node: NodeId) = edges.asSequence().filter { it.start == node }
    fun outDegree(node: NodeId) = outEdges(node).count()

    fun inEdges(node: NodeId) = edges.asSequence().filter { it.end == node }
    fun inDegree(node: NodeId) = inEdges(node).count()
}
