package edu.kit.compiler.optimization

import firm.BackEdges
import firm.Graph
import firm.Mode
import firm.nodes.Node
import firm.nodes.NodeVisitor

abstract class WorkListVisitor(private val graph: Graph) : FirmNodeVisitorAdapter() {
    private var hasChanged = false

    protected fun markChanged() {
        hasChanged = true
    }

    fun run() {
        check(BackEdges.enabled(graph)) { "back edges must be enabled" }

        val workList = ArrayDeque<Node>()
        graph.walkTopological(object : NodeVisitor.Default() {
            override fun defaultVisit(n: Node) {
                workList.addLast(n)
            }
        })

        while (workList.isNotEmpty()) {
            val node = workList.removeFirst()
            hasChanged = false
            node.accept(this)
            if (hasChanged) {
                BackEdges.getOuts(node).forEach {
                    val successor = it.node
                    if (successor.mode != Mode.getM() && !workList.contains(successor)) {
                        workList.addLast(successor)
                    }
                }
            }
        }
    }
}
