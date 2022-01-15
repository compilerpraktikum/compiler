package edu.kit.compiler.backend.codegen

import edu.kit.compiler.optimization.FirmNodeVisitorAdapter
import firm.BackEdges
import firm.Graph
import firm.nodes.Add
import firm.nodes.And
import firm.nodes.Block
import firm.nodes.Call
import firm.nodes.Cmp
import firm.nodes.Cond
import firm.nodes.Const
import firm.nodes.Conv
import firm.nodes.Div
import firm.nodes.Eor
import firm.nodes.Jmp
import firm.nodes.Load
import firm.nodes.Minus
import firm.nodes.Mod
import firm.nodes.Mul
import firm.nodes.Mulh
import firm.nodes.Node
import firm.nodes.Not
import firm.nodes.Or
import firm.nodes.Phi
import firm.nodes.Proj
import firm.nodes.Return
import firm.nodes.Shl
import firm.nodes.Shr
import firm.nodes.Shrs
import firm.nodes.Store
import firm.nodes.Sub


class FirmGraphToCodeGenTreeGraphGenerator(private val graph: Graph) : FirmNodeVisitorAdapter() {

    val blockMap: MutableMap<Block, CodeGenIR?> = mutableMapOf()

    var nodeMap: MutableMap<Node, CodeGenIR> = mutableMapOf()
    var registerTable = VirtualRegisterTable()
    var currentTree: CodeGenIR? = null
    var currentBlock: Node? = null
    var nextBlock = false

    fun buildTrees() {
        println(graph.entity.ldName)
        breakCriticalEdges(graph)
        val phiVisitor = PhiAssignRegisterVisitor()
        BackEdges.enable(graph)
        graph.walkTopological(phiVisitor)
        println("Phi visitor result: ${phiVisitor.registerTable.map.size} new registers")
        nodeMap = phiVisitor.map
        registerTable = phiVisitor.registerTable
        graph.walkTopological(this)
        // last visited block needs to be entered
        blockMap[currentBlock as Block] = currentTree
        println(blockMap)
        BackEdges.disable(graph)
        //
    }

    fun updateCurrentBlock(node: Node) {
        if (currentBlock == null) {
            currentBlock = node.block
        } else if (currentBlock != node.block) {
            blockMap[currentBlock as Block] = currentTree!!
            currentBlock = node.block
        }
    }

    fun updateCurrentTree(tree: CodeGenIR, node: Node) {
        nodeMap[node] = tree
        currentTree = tree
    }


    override fun visit(node: Add) {

        if (!nodeMap.contains(node.left) || !nodeMap.contains(node.right)) {
            TODO("should be set everytime?")
            return
        }
        println("visit ADD " + node.block.toString())
        super.visit(node)

        updateCurrentBlock(node)
        val reg = registerTable.newRegisterForNode(node)
        registerTable.putNode(CodeGenIR.RegisterRef(reg), reg)
        val tree = CodeGenIR.BinOP(left = nodeMap[node.left]!!, right = nodeMap[node.right]!!, res = CodeGenIR.RegisterRef(reg), operation = node)
        updateCurrentTree(tree, node)
        println("visit ADD " + node.block.toString())
    }

    override fun visit(node: And) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit AND " + node.block.toString())
    }

    override fun visit(node: Call) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit CALL "+ node.block.toString())
    }

    override fun visit(node: Cmp) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit CMP "+ node.block.toString())
    }

    override fun visit(node: Cond) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit COND "+ node.block.toString())
    }

    override fun visit(node: Const) {
        super.visit(node)
        updateCurrentBlock(node)
        val tree = CodeGenIR.Const(node.tarval.toString())
        updateCurrentTree(tree, node)
        println("visit CONST "+ node.block.toString())
        println(node.tarval.toString())
    }

    override fun visit(node: Conv) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit CONV "+ node.block.toString())
    }

    override fun visit(node: Div) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit DIV "+ node.block.toString())
    }

    override fun visit(node: Eor) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit EOR "+ node.block.toString())
    }

    override fun visit(node: Jmp) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit JMP "+ node.block.toString())
    }

    override fun visit(node: Load) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit LOAD "+ node.block.toString())
    }

    override fun visit(node: Minus) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit MINUS "+ node.block.toString())
    }

    override fun visit(node: Mod) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit MOD "+ node.block.toString())
    }

    override fun visit(node: Mul) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit MUL "+ node.block.toString())
    }

    override fun visit(node: Mulh) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit MULH "+ node.block.toString())
    }

    override fun visit(node: Not) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit NOT "+ node.block.toString())
    }

    override fun visit(node: Or) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit OR "+ node.block.toString())
    }

    override fun visit(node: Phi) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit PHI "+ node.block.toString())
    }

    override fun visit(node: Proj) {
        super.visit(node)
        updateCurrentBlock(node)
        when (node.pred) {

        }
        println("visit PROJ "+ node.block.toString())
    }

    override fun visit(node: Return) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit RETURN "+ node.block.toString())
    }

    override fun visit(node: Store) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit STORE "+ node.block.toString())
    }

    override fun visit(node: Sub) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit SUB "+ node.block.toString())
    }

    override fun visitUnknown(node: Node) {
        super.visitUnknown(node)
        updateCurrentBlock(node)
        println("visit NODE "+ node.block.toString())
    }

    override fun visit(node: Shl) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit SHL "+ node.block.toString())
    }

    override fun visit(node: Shr) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit SHR "+ node.block.toString())
    }

    override fun visit(node: Shrs) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit SHRS "+ node.block.toString())
    }

//TODO Vorgehensweise:
    /*
        * Grundblockweise. Verweise auf andere Grundblöcke müssen aufgelöst werden.
        * Phis?
        *
        * 1. Firm-Graph ==> Code-Gen-Tree
        * 2. Phi-Abbau: f.A. Phis
        *   1. jedes Phi kriegt neues Register r.
        *   2. Für jede Phi-Kante [PhiY->(a,b)] wird in den entsprechenden Grundblöcken ein Assignment y = ... gesetzt.
        *   4. Kritische Kanten: Betrachte [PhiY->(a,b)]. Falls (zB) b in noch einem anderen [PhiZ->(b,c)] verwendet wird,
        *   füge zwischen Block(y) und Block(b), sowie zwischen  Block(z) und Block(b) je einen Block ein, in welchem y bzw. z auf b gesetzt wird.
        * 3. Bottom-Up Pattern Matching auf CodeGenTree. ==> Molki-Code
        * 4. Swap-Problem: Falls [PhiY->(a,PhiZ)] und PhiZ im selben Grundblock liegt, benötigt PhiY den Wert von "vorher"
        *
        *
     */

}
