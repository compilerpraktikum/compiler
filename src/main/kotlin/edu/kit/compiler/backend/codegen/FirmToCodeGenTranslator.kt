package edu.kit.compiler.backend.codegen

import com.tylerthrailkill.helpers.prettyprint.pp
import edu.kit.compiler.backend.molkir.Memory
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.RegisterId
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.optimization.FirmNodeVisitorAdapter
import firm.BackEdges
import firm.Graph
import firm.MethodType
import firm.Mode
import firm.Type
import firm.nodes.Add
import firm.nodes.Address
import firm.nodes.And
import firm.nodes.Binop
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
import firm.nodes.Start
import firm.nodes.Store
import firm.nodes.Sub
import firm.nodes.Unknown

class FirmToCodeGenTranslator(private val graph: Graph) : FirmNodeVisitorAdapter() {

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
        blockMap.pp()
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
        println(tree.toString())
    }

    private fun buildBinOpTree(node: Binop, op: BinOpENUM) {
        check(nodeMap.contains(node.left)) { "expected pred node left to be initialized ${node.left}" }
        check(nodeMap.contains(node.right)) { "expected pred node right to be initialized ${node.right}" }


        updateCurrentBlock(node)
        val tree = CodeGenIR.BinOP(left = nodeMap[node.left]!!, right = nodeMap[node.right]!!, operation = op)
        updateCurrentTree(tree, node)
    }

    override fun visit(node: Add) {
        super.visit(node)
        buildBinOpTree(node, BinOpENUM.ADD)
        println("visit ADD " + node.block.toString())
    }

    override fun visit(node: And) {
        super.visit(node)
        buildBinOpTree(node, BinOpENUM.AND)
        println("visit AND " + node.block.toString())
    }

    override fun visit(node: Call) {
        println("visit CALL " + node.block.toString())
        super.visit(node)
        updateCurrentBlock(node)
        node.preds.forEach { println("Preds are: ${it.toString()}") }
        val arguments = node.preds
            .filter { it.mode != Mode.getM() }
            .filter { it !is Address }
            .map { nodeMap[it]!! }
        val addr = node.getPred(1) as Address
        println("visit CALL " + node.block.toString())
        //val mode = (node.type as MethodType).getResType(0).mode

        val call = CodeGenIR.Call(addr, arguments)
        // R_i = call("foo", 2)
        nodeMap[node] = call
        println(call.toString())

    }

    override fun visit(node: Cmp) {
        super.visit(node)
        updateCurrentBlock(node)
        val left = nodeMap[node.left]!!
        val right = nodeMap[node.right]!!
        val cmp = CodeGenIR.Compare(node.relation,right, left)
        updateCurrentTree(cmp, node)
        println("visit CMP " + node.block.toString())
    }

    override fun visit(node: Cond) {
        super.visit(node)
        BackEdges.getOuts(node).forEach { it -> println(it.node.toString()) }
        updateCurrentBlock(node)

        println("visit COND " + node.block.toString())
    }

    override fun visit(node: Const) {
        super.visit(node)
        updateCurrentBlock(node)
        val tree = CodeGenIR.Const(node.tarval.toString())
        updateCurrentTree(tree, node)
        println("visit CONST " + node.block.toString())
        println(node.tarval.toString())
    }

    override fun visit(node: Conv) {
        super.visit(node)
        updateCurrentBlock(node)
        val opMode = node.op.mode
        val mode = node.mode
        val opTree = nodeMap[node.op]!!
        val conv = CodeGenIR.Conv(opMode, mode, opTree)
        nodeMap[node] = conv
        updateCurrentTree(conv, node)
        println("visit CONV " + node.block.toString())
    }

    override fun visit(node: Div) {
        // Div isn't from type BinOP
        super.visit(node)
        updateCurrentBlock(node)
        println("visit DIV " + node.block.toString())
    }

    override fun visit(node: Eor) {
        super.visit(node)
        buildBinOpTree(node, BinOpENUM.EOR)
        println("visit EOR " + node.block.toString())
    }

    override fun visit(node: Jmp) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit JMP " + node.block.toString())
    }

    override fun visit(node: Load) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit LOAD " + node.block.toString())
    }

    override fun visit(node: Minus) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit MINUS " + node.block.toString())
    }

    override fun visit(node: Mod) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit MOD " + node.block.toString())
    }

    override fun visit(node: Mul) {
        super.visit(node)
        buildBinOpTree(node, BinOpENUM.MUL)
        println("visit MUL " + node.block.toString())
    }

    override fun visit(node: Mulh) {
        super.visit(node)
        buildBinOpTree(node, BinOpENUM.MULH)
        println("visit MULH " + node.block.toString())
    }

    override fun visit(node: Not) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit NOT " + node.block.toString())
    }

    override fun visit(node: Or) {
        super.visit(node)
        buildBinOpTree(node, BinOpENUM.OR)
        println("visit OR " + node.block.toString())
    }

    override fun visit(node: Phi) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit PHI " + node.block.toString())
    }

    override fun visit(node: Proj) {
        super.visit(node)
        updateCurrentBlock(node)
        println(node.pred.toString())
        when (val pred = node.pred) {
            is Div -> nodeMap[node] = nodeMap[pred]!!
            is Mod -> nodeMap[node] = nodeMap[pred]!!
            is Load -> nodeMap[node] = nodeMap[pred]!!
            is Store -> nodeMap[pred.getPred(0)]
            is Start -> Unit // skip -> this is handled by succeeding proj
            is Call -> Unit // skip -> this is handled by succeeding proj
            is Proj -> {
                when (val origin = pred.pred) {
                    is Start ->  {
                        println("node: $node, pred: $pred, predpred: $origin")
                        allocateArguments(node)
                    }
                    is Call -> {
                        nodeMap[node] = nodeMap[origin]!!
                    }
                }
            }
            else -> TODO("missing handling for case $pred")
        }
        println("visit PROJ ${node.toString()} " + node.block.toString())
    }

    private fun allocateArguments(node: Proj) {
        val reg = registerTable.newRegisterFor(node)

        val registerRef = CodeGenIR.RegisterRef(reg)
        nodeMap[node] = registerRef
        println("setting nodeMap for $node to $registerRef")
        registerTable.putNode(registerRef, reg)
    }

    override fun visit(node: Return) {
        super.visit(node)
        updateCurrentBlock(node)
        println("---")
        node.preds.forEach {println("preds ${it.toString()}") }
        println("---")
        println("---")
        node.preds.forEach {println("predstree ${nodeMap[it].toString()}") }
        println("---")
        println("visit RETURN " + node.block.toString())
    }

    override fun visit(node: Store) {
        node.preds.forEach { println("Store preds are: ${it.mode.toString()}, ${nodeMap[it]}") }
        super.visit(node)
        updateCurrentBlock(node)
        val value = nodeMap[node.value]!!
        val address = nodeMap[node.getPred(1)]!!
        val tree = CodeGenIR.Assign(lhs = address, rhs = value)
        updateCurrentTree(tree, node)
        println("visit STORE " + node.block.toString())
    }

    override fun visit(node: Sub) {
        super.visit(node)
        buildBinOpTree(node, BinOpENUM.SUB)
        println("visit SUB " + node.block.toString())
    }
    //TODO
   override fun visit(node: Unknown) {
        super.visitUnknown(node)
        updateCurrentBlock(node)
        println("visit NODE " + node.block.toString())
    }

    override fun visit(node: Shl) {
        super.visit(node)
        buildBinOpTree(node, BinOpENUM.SHL)
        println("visit SHL " + node.block.toString())
    }

    override fun visit(node: Shr) {
        super.visit(node)
        buildBinOpTree(node, BinOpENUM.SHR)
        println("visit SHR " + node.block.toString())
    }

    override fun visit(node: Shrs) {
        super.visit(node)
        buildBinOpTree(node, BinOpENUM.SHRS)
        println("visit SHRS " + node.block.toString())
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
