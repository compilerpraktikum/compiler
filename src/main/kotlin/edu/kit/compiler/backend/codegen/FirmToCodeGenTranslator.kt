package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.RegisterId
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

private val Node.parentBlock: Block
    get() = this.block as Block

/**
 * We need to be careful with control flow dependencies:
 * If we remove, duplicate or change their order, the resulting program will be incorrect.
 * To solve this, we need to handle "blue" nodes differently:
 *   - For "blue" edges we generate the actual code for (calling, storing, writing), followed by writing to a new virtual
 *     register. The result of the computation is then represented that register.
 *   - For black edges, that reference a "blue" node, we just load from that register.
 */
class FirmToCodeGenTranslator(
    private val graph: Graph,
    private val registerTable: VirtualRegisterTable = VirtualRegisterTable(),
    private val generationState: GenerationState
) : FirmNodeVisitorAdapter() {

    class GenerationState(
        val blockMap: MutableMap<Block, CodeGenIR> = mutableMapOf(),
        var nodeMap: MutableMap<Node, CodeGenIR> = mutableMapOf()
    ) {
        val finalizedBlocks: MutableSet<Block> = mutableSetOf()

        fun getCurrentIrForBlock(block: Block): CodeGenIR? {
            return blockMap[block]
        }

        fun updateCodeForBlock(block: Block, codeGenIR: CodeGenIR) {
            blockMap[block] = codeGenIR
        }

        fun setFinalCodeGenIrForBlock(block: Block, codeGenIR: CodeGenIR) {
            updateCodeForBlock(block, codeGenIR)
            finalizedBlocks.add(block)
        }

        fun setCodeGenIrForNode(node: Node, codeGenIR: CodeGenIR) {
            nodeMap[node] = codeGenIR
        }

        fun isFinalized() = blockMap.entries.all { finalizedBlocks.contains(it.key) }

        fun getCodeGenIRs(): MutableMap<Block, CodeGenIR> {
//            check(isFinalized()) { "inconsistent state of blockMap $blockMap" }
            return blockMap
        }

        fun getCodeGenFor(node: Node) = nodeMap[node]
    }

    companion object {
        fun buildTrees(graph: Graph, registerTable: VirtualRegisterTable): Map<Block, CodeGenIR?> {
            println(graph.entity.ldName)
            breakCriticalEdges(graph)
            val phiVisitor = PhiAssignRegisterVisitor(registerTable)
            BackEdges.enable(graph)
            graph.walkTopological(phiVisitor)
            println("Phi visitor result: ${phiVisitor.registerTable.map.size} new registers")
            val generationState = GenerationState(nodeMap = phiVisitor.map)
            graph.walkTopological(FirmToCodeGenTranslator(graph, registerTable, generationState))
            // last visited block needs to be entered
            BackEdges.disable(graph)
            return generationState.getCodeGenIRs()
        }
    }

    private fun getCodeFor(node: Node): CodeGenIR =
        generationState.getCodeGenFor(node)!!

    private inline fun setCodeFor(node: Node, buildCodeGen: () -> CodeGenIR): CodeGenIR =
        buildCodeGen().also { generationState.setCodeGenIrForNode(node, it) }

    private fun updateCurrentTree(tree: CodeGenIR, block: Block) {
        generationState.updateCodeForBlock(block, tree)
        println("setCodeGenIrForNode: ${tree}")
    }

    private fun buildBinOpTree(node: Binop, op: BinOpENUM) {
        setCodeFor(node) {
            CodeGenIR.BinOP(left = getCodeFor(node.left), right = getCodeFor(node.right), operation = op)
        }
    }

    private fun noop() =
        CodeGenIR.Const("0", Width.BYTE)

    override fun visit(node: Start) {
        super.visit(node)
        setCodeFor(node) {
            noop()
        }
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
        super.visit(node)
        val arguments = node.preds
            .filter { it.mode != Mode.getM() }
            .filter { it !is Address }
            .map { getCodeFor(it) }
        val addr = node.getPred(1) as Address

        //val mode = (node.type as MethodType).getResType(0).mode
        val seqPred = generationState.getCurrentIrForBlock(node.block as Block) ?: noop()
        val call = CodeGenIR.Seq(value = CodeGenIR.Call(addr, arguments), exec = seqPred)
        setCodeFor(node) { call }
        updateCurrentTree(call, node.parentBlock)
        println("visit CALL " + node.block.toString())
    }

    override fun visit(node: Cmp) {
        super.visit(node)

        val cmp = setCodeFor(node) {
            CodeGenIR.Compare(node.relation, getCodeFor(node.right), getCodeFor(node.left))
        }
        println("visit CMP ${node.block} -> $cmp")
    }

    override fun visit(node: Cond) {
        super.visit(node)

        val projs = BackEdges.getOuts(node).map { it.node as Proj }
        val trueProj = projs.find { it.num == Cond.pnTrue }
        val falseProj = projs.find { it.num == Cond.pnFalse }
        val trueBlock = BackEdges.getOuts(trueProj).iterator().next().node!! as Block
        val falseBlock = BackEdges.getOuts(falseProj).iterator().next().node!! as Block

        val cond = CodeGenIR.Cond(
            cond = CodeGenIR.Seq(exec = getCodeFor(node.getPred(0)), value = getCodeFor(node.getPred(1))),
            ifTrue = CodeGenIR.Jmp(trueBlock),
            ifFalse = CodeGenIR.Jmp(falseBlock)
        )
//        val cond = CodeGenIR.Seq(
//            exec = currentTree!!, value = CodeGenIR.Cond(
//                cond = nodeMap[node.getPred(0)]!!,
//                ifTrue = CodeGenIR.Jmp(trueBlock),
//                ifFalse = CodeGenIR.Jmp(falseBlock)
//            )
//        )
        updateCurrentTree(cond, node.parentBlock)
        println("visit COND ${node.block}")
    }

    override fun visit(node: Const) {
        super.visit(node)
        setCodeFor(node) {
            CodeGenIR.Const(node.tarval.toString(), Width.fromByteSize(node.mode.sizeBytes)!!)
        }
        println("visit CONST " + node.block.toString())
        println(node.tarval.toString())
    }

    override fun visit(node: Conv) {
        super.visit(node)

        val from = node.op.mode
        val to = node.mode

        setCodeFor(node) {
            CodeGenIR.Conv(from, to, getCodeFor(node.op))
        }
        println("visit CONV " + node.block.toString())
    }

    override fun visit(node: Div) {
        // Div isn't from type BinOP
        super.visit(node)
        setCodeFor(node) {
            CodeGenIR.Div(getCodeFor(node.left), getCodeFor(node.right))
        }
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
        val controlFlowDependencies = generationState.getCurrentIrForBlock(node.block as Block) ?: noop()
        val jmp = setCodeFor(node) {
            CodeGenIR.Seq(
                exec = controlFlowDependencies,
                value = CodeGenIR.Jmp(BackEdges.getOuts(node).first().node!! as Block)
            )
        }
        updateCurrentTree(jmp, node.parentBlock)
        println("visit JMP " + node.block.toString())
    }

    override fun visit(node: Load) {
        super.visit(node)
        updateCurrentTree(
            CodeGenIR.Seq(
                value = CodeGenIR.Indirection(getCodeFor(node.getPred(1))),
                exec = getCodeFor(node.block as Block)
            ), node.parentBlock
        )
        println("visit LOAD " + node.block.toString())
    }

    override fun visit(node: Minus) {
        super.visit(node)
        setCodeFor(node) {
            CodeGenIR.UnaryOP(op = UnaryOpENUM.MINUS, value = getCodeFor(node.op))
        }
        println("visit MINUS " + node.block.toString())
    }

    override fun visit(node: Mod) {
        super.visit(node)
        setCodeFor(node) {
            CodeGenIR.Mod(left = getCodeFor(node.left), right = getCodeFor(node.right))
        }
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
        setCodeFor(node) {
            CodeGenIR.UnaryOP(op = UnaryOpENUM.NOT, value = getCodeFor(node.op))
        }
        println("visit NOT " + node.block.toString())
    }

    override fun visit(node: Or) {
        super.visit(node)
        buildBinOpTree(node, BinOpENUM.OR)
        println("visit OR " + node.block.toString())
    }

    override fun visit(node: Phi) {
        super.visit(node)
        println("visit PHI " + node.block.toString())
    }

    override fun visit(node: Proj) {
        super.visit(node)
        println(node.pred.toString())
        when (val pred = node.pred) {
            is Div -> setCodeFor(node) { getCodeFor(pred) }
            is Mod -> setCodeFor(node) { getCodeFor(pred) }
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

                        val codegenExecute = when (val loadIR = getCodeFor(pred)) {
                            is CodeGenIR.Seq -> CodeGenIR.Seq(
                                value = CodeGenIR.Assign(
                                    CodeGenIR.RegisterRef(reg),
                                    loadIR.value
                                ), exec = loadIR.exec
                            )
                            else -> error("invalid state")
                        }
                        val codegenRef = CodeGenIR.RegisterRef(reg)

                        setCodeFor(resultProjection) { codegenRef }
                        setCodeFor(node) { codegenExecute }
                    } else {
                        setCodeFor(node) { getCodeFor(pred) }
                    }


                } else {
                    // can be skipped. The value projection is handled by the control flow projection
                }
            }
            is Store -> setCodeFor(node) { getCodeFor(pred) }
            is Start -> setCodeFor(node) { getCodeFor(pred) }
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

                        val codegenExecute = when (val callIR = getCodeFor(pred)) {
                            is CodeGenIR.Seq -> CodeGenIR.Seq(
                                value = CodeGenIR.Assign(
                                    CodeGenIR.RegisterRef(reg),
                                    callIR.value
                                ), exec = callIR.exec
                            )
                            else -> error("invalid state")
                        }
                        val codegenRef = CodeGenIR.RegisterRef(reg)

                        setCodeFor(valueProjection) {
                            codegenRef
                        }
                        setCodeFor(node) {
                            codegenExecute
                        }
                        updateCurrentTree(codegenExecute, node.parentBlock)
                    } else {
                        setCodeFor(node) {
                            getCodeFor(pred)
                        }
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
        setCodeFor(node) {
            registerRef
        }
        println("setting nodeMap for $node to $registerRef")
    }

    override fun visit(node: Return) {
        super.visit(node)
        println("visit RETURN $node ${node.preds}")
        node.preds.forEach { println("predstree ${getCodeFor(it).toString()}") }
        val nodePreds = node.preds.toList()

        val controlFlow = generationState.getCurrentIrForBlock(node.block as Block) ?: noop()
        val value = nodePreds.getOrNull(1)

        val res = if (value == null) {
            CodeGenIR.Return(controlFlow)
        } else {
            CodeGenIR.Return(CodeGenIR.Seq(value = getCodeFor(value), exec = controlFlow))
        }
        println("res $res")
        updateCurrentTree(res, node.parentBlock)
        println("visit RETURN " + node.block.toString())
    }

    override fun visit(node: Store) {
        node.preds.forEach { println("Store preds are: ${it.mode.toString()}, ${getCodeFor(it)}") }
        super.visit(node)
        val value = getCodeFor(node.value)
        val address = getCodeFor(node.getPred(1))
        val tree =
            CodeGenIR.Seq(value = CodeGenIR.Assign(lhs = address, rhs = value), exec = getCodeFor(node.getPred(0)))
        updateCurrentTree(tree, node.parentBlock)
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
