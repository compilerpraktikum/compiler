package edu.kit.compiler.backend

import edu.kit.compiler.optimization.FirmNodeVisitorAdapter
import firm.BackEdges
import firm.Graph
import firm.Mode
import firm.Program
import firm.nodes.Const
import firm.nodes.Div
import firm.nodes.Mod
import firm.nodes.Node
import firm.nodes.Proj

private fun isSafe(left: Node, right: Node) = when {
    (left is Const && left.tarval.asInt() != Int.MIN_VALUE) -> true
    (right is Const && right.tarval.asInt() != -1) -> true
    else -> false
}

/**
 * Get result projection of [Div] or [Mod]. Can be null if operation result is unused.
 */
private fun Node.getResultProj(): Proj? {
    BackEdges.getOuts(this).forEach {
        val node = it.node
        check(node is Proj) { "invalid division" }
        if (node.num == Div.pnRes) {
            return node
        }
    }
    return null
}

private fun Graph.replaceResultProj(proj: Proj) {
    val newProj = newProj(proj.pred, Mode.getLs(), Div.pnRes)
    Graph.exchange(proj, newProj)
    val result = newConv(newProj.block, newProj, Mode.getIs())
    BackEdges.getOuts(newProj).forEach {
        if (it.node != result) {
            it.node.setPred(it.pos, result)
        }
    }
}

private fun fixJavaCompatibilityFor(graph: Graph) {
    BackEdges.enable(graph)

    graph.walk(object : FirmNodeVisitorAdapter() {
        override fun visit(node: Div) {
            if (isSafe(node.left, node.right))
                return

            node.left = graph.newConv(node.block, node.left, Mode.getLs())
            node.right = graph.newConv(node.block, node.right, Mode.getLs())

            node.resmode = Mode.getLs()

            node.getResultProj()?.also { graph.replaceResultProj(it) }
        }

        override fun visit(node: Mod) {
            if (isSafe(node.left, node.right))
                return

            node.left = graph.newConv(node.block, node.left, Mode.getLs())
            node.right = graph.newConv(node.block, node.right, Mode.getLs())

            node.resmode = Mode.getLs()

            node.getResultProj()?.also { graph.replaceResultProj(it) }
        }
    })

    BackEdges.disable(graph)
}

/**
 * Fix x86 <-> Java compatibility regarding `Int.MIN_VALUE / -1` and `Int.MIN_VALUE % -1` in all [FIRM graphs][Graph].
 * See mjtest exec/Division.java for correct behaviour. X86 throws floating point exception instead.
 */
fun fixJavaCompatibility() {
    Program.getGraphs().forEach(::fixJavaCompatibilityFor)
}
