package edu.kit.compiler.backend.codegen

import com.tylerthrailkill.helpers.prettyprint.pp
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.optimization.FirmNodeVisitorAdapter
import firm.BackEdges
import firm.Graph
import firm.Mode
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

/**
 * We need to be careful with control flow dependencies:
 * If we remove, duplicate or change their order, the resulting program will be incorrect.
 * To solve this, we need to handle "blue" nodes differently:
 *   - For "blue" edges we generate the actual code for (calling, storing, writing), followed by writing to a new virtual
 *     register. The result of the computation is then represented that register.
 *   - For black edges, that reference a "blue" node, we just load from that register.
 */
class FirmToCodeGenTranslator(private val graph: Graph, val registerTable: VirtualRegisterTable = VirtualRegisterTable()) : FirmNodeVisitorAdapter() {

    val blockMap: MutableMap<Block, CodeGenIR?> = mutableMapOf()

    // value map
    var nodeMap: MutableMap<Node, CodeGenIR> = mutableMapOf()
    var currentTree: CodeGenIR? = null
    var currentBlock: Node? = null
    var nextBlock = false

    fun buildTrees(): Map<Block, CodeGenIR?> {
        println(graph.entity.ldName)
        breakCriticalEdges(graph)
        val phiVisitor = PhiAssignRegisterVisitor(registerTable)
        BackEdges.enable(graph)
        graph.walkTopological(phiVisitor)
        println("Phi visitor result: ${phiVisitor.registerTable.map.size} new registers")
        nodeMap = phiVisitor.map
        graph.walkTopological(this)
        // last visited block needs to be entered
        blockMap[currentBlock as Block] = currentTree
        println(blockMap)
        blockMap.pp()
        BackEdges.disable(graph)
        return blockMap
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

    override fun visit(node: Start) {
        super.visit(node)
        updateCurrentTree(CodeGenIR.Const("0", Width.BYTE), node)
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
        val seqPred = nodeMap[node.getPred(0)]!!
        println("visit CALL " + node.block.toString())
        //val mode = (node.type as MethodType).getResType(0).mode

        val call = CodeGenIR.Seq(value = CodeGenIR.Call(addr, arguments), exec = seqPred)
        // R_i = call("foo", 2)
        nodeMap[node] = call
        println(call.toString())

    }

    override fun visit(node: Cmp) {
        super.visit(node)
        updateCurrentBlock(node)
        val left = nodeMap[node.left]!!
        val right = nodeMap[node.right]!!
        val cmp = CodeGenIR.Compare(node.relation, right, left)
        updateCurrentTree(cmp, node)
        println("visit CMP " + node.block.toString())
    }

    //TODO
    override fun visit(node: Cond) {

        super.visit(node)
        updateCurrentBlock(node)
        val projs = BackEdges.getOuts(node).map { it.node as Proj }
        val trueProj = projs.find { it -> it.num == Cond.pnTrue }
        val falseProj = projs.find { it -> it.num == Cond.pnFalse }
        val trueBlock = BackEdges.getOuts(trueProj).iterator().next().node!! as Block
        val falseBlock = BackEdges.getOuts(falseProj).iterator().next().node!! as Block
        println("is already there ${blockMap[trueBlock]}")
        println("visit COND " + node.block.toString())
        nodeMap[node] = CodeGenIR.Cond(
            cond = nodeMap[node.getPred(0)]!!,
            ifTrue = CodeGenIR.Jmp(trueBlock),
            ifFalse = CodeGenIR.Jmp(falseBlock)
        )
    }

    override fun visit(node: Const) {
        super.visit(node)
        updateCurrentBlock(node)
        val tree = CodeGenIR.Const(node.tarval.toString(), Width.fromByteSize(node.mode.sizeBytes)!!)
        updateCurrentTree(tree, node)
        println("visit CONST " + node.block.toString())
        println(node.tarval.toString())
    }

    override fun visit(node: Conv) {
        super.visit(node)
        updateCurrentBlock(node)
        val from = node.op.mode
        val to = node.mode
        val opTree = nodeMap[node.op]!!
        val conv = CodeGenIR.Conv(from, to, opTree)
        nodeMap[node] = conv
        updateCurrentTree(conv, node)
        println("visit CONV " + node.block.toString())
    }

    override fun visit(node: Div) {
        // Div isn't from type BinOP
        super.visit(node)
        updateCurrentBlock(node)
        val div = CodeGenIR.Div(nodeMap[node.left]!!, nodeMap[node.right]!!)
        updateCurrentTree(div, node)
        println("visit DIV " + node.block.toString())
    }

    override fun visit(node: Eor) {
        super.visit(node)
        buildBinOpTree(node, BinOpENUM.EOR)
        println("visit EOR " + node.block.toString())
    }

    //TODO
    override fun visit(node: Jmp) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit JMP " + node.block.toString())
    }

    override fun visit(node: Load) {
        super.visit(node)
        updateCurrentBlock(node)
        updateCurrentTree(
            CodeGenIR.Seq(
                value = CodeGenIR.Indirection(nodeMap[node.getPred(1)]!!),
                exec = nodeMap[node.getPred(0)]!!
            ), node
        )
        println("visit LOAD " + node.block.toString())
    }

    override fun visit(node: Minus) {
        super.visit(node)
        updateCurrentBlock(node)
        updateCurrentTree(CodeGenIR.UnaryOP(op = UnaryOpENUM.MINUS, value = nodeMap[node.op]!!), node)
        println("visit MINUS " + node.block.toString())
    }

    override fun visit(node: Mod) {
        super.visit(node)
        updateCurrentBlock(node)
        updateCurrentTree(CodeGenIR.Mod(left = nodeMap[node.left]!!, right = nodeMap[node.right]!!), node)
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
        updateCurrentTree(CodeGenIR.UnaryOP(op = UnaryOpENUM.NOT, value = nodeMap[node.op]!!), node)
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
            //TODO
            is Load -> {

                //BackEdges.getOuts(pred).forEach{
                //   it ->
                //}
                if (node.mode == Mode.getM()) {
                    println("node: $node")

                    // does the call have a result value?
                    val resultProjection =
                        BackEdges.getOuts(pred).map { it.node }.find { it is Proj && it.mode == Mode.getIs() }
                    if (resultProjection != null) {
                        val reg = registerTable.getOrCreateRegisterFor(resultProjection)

                        val codegenExecute = when (val loadIR = nodeMap[pred]!!) {
                            is CodeGenIR.Seq -> CodeGenIR.Seq(
                                value = CodeGenIR.Assign(
                                    CodeGenIR.RegisterRef(reg),
                                    loadIR.value
                                ), exec = loadIR.exec
                            )
                            else -> error("invalid state")
                        }
                        val codegenRef = CodeGenIR.RegisterRef(reg)

                        nodeMap[resultProjection] = codegenRef
                        nodeMap[node] = codegenExecute
                    } else {
                        nodeMap[node] = nodeMap[pred]!!
                    }


                } else {
                    // can be skipped. The value projection is handled by the control flow projection
                }
            }
            is Store -> nodeMap[node] = nodeMap[pred]!!
            is Start -> nodeMap[node] = nodeMap[pred]!!
            is Call -> {
                //BackEdges.getOuts(pred).forEach{
                //   it ->
                //}
                if (node.mode == Mode.getM()) {
                    println("node: $node")

                    // does the call have a result value?
                    val resultProjection =
                        BackEdges.getOuts(pred).map { it.node }.find { it is Proj && it.mode == Mode.getT() }
                    if (resultProjection != null) {
                        val valueProjection = BackEdges.getOuts(resultProjection).first().node
                        val reg = registerTable.getOrCreateRegisterFor(valueProjection)

                        val codegenExecute = when (val callIR = nodeMap[pred]!!) {
                            is CodeGenIR.Seq -> CodeGenIR.Seq(
                                value = CodeGenIR.Assign(
                                    CodeGenIR.RegisterRef(reg),
                                    callIR.value
                                ), exec = callIR.exec
                            )
                            else -> error("invalid state")
                        }
                        val codegenRef = CodeGenIR.RegisterRef(reg)

                        nodeMap[valueProjection] = codegenRef
                        nodeMap[node] = codegenExecute
                    } else {
                        nodeMap[node] = nodeMap[pred]!!
                    }


                } else {
                    // can be skipped. The value projection is handled by the control flow projection
                }
            } // skip -> this is handled by succeeding proj
            is Proj -> {
                when (val origin = pred.pred) {
                    is Start -> {
                        println("node: $node, pred: $pred, predpred: $origin")
                        allocateArguments(node)
                    }
                    is Call -> {
//                        nodeMap[node] = nodeMap[pred]!!
                        // handled by call outer call case
                    }
                }
            }
            is Cond -> {
                println(pred.toString())
            }
            else -> TODO("missing handling for case $pred")
        }
        println("visit PROJ ${node.toString()} " + node.block.toString())
    }

    private fun allocateArguments(node: Proj) {
        val reg = registerTable.getOrCreateRegisterFor(node)

        val registerRef = CodeGenIR.RegisterRef(reg)
        nodeMap[node] = registerRef
        println("setting nodeMap for $node to $registerRef")
    }

    override fun visit(node: Return) {
        super.visit(node)
        updateCurrentBlock(node)
        println("visit RETURN $node ${node.preds}")
        node.preds.forEach { println("predstree ${nodeMap[it].toString()}") }
        val nodePreds = node.preds.toList()

        val controlFlow = nodePreds[0]
        val value = nodePreds.getOrNull(1)

        val res = if (value == null) {
            CodeGenIR.Return(nodeMap[controlFlow]!!)
        } else {
            CodeGenIR.Return(CodeGenIR.Seq(value = nodeMap[value]!!, exec = nodeMap[controlFlow]!!))
        }

        updateCurrentTree(res, node)
        println("---")
        node.preds.forEach { println("preds ${it.toString()}") }
        println("---")
        println("---")

        println("---")
        println("visit RETURN " + node.block.toString())
    }

    override fun visit(node: Store) {
        node.preds.forEach { println("Store preds are: ${it.mode.toString()}, ${nodeMap[it]}") }
        super.visit(node)
        updateCurrentBlock(node)
        val value = nodeMap[node.value]!!
        val address = nodeMap[node.getPred(1)]!!
        val tree =
            CodeGenIR.Seq(value = CodeGenIR.Assign(lhs = address, rhs = value), exec = nodeMap[node.getPred(0)]!!)
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
