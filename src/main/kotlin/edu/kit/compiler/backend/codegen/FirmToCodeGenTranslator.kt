package edu.kit.compiler.backend.codegen

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
import firm.nodes.End
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

private val Node.enclosingBlock: Block
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
        val controlflowDependencies: MutableMap<Block, MutableList<CodeGenIR>> = mutableMapOf(),
        val exitNodes: MutableMap<Block, CodeGenIR> = mutableMapOf(),
        var nodeMap: MutableMap<Node, CodeGenIR> = mutableMapOf()
    ) {
        val finalizedBlocks: MutableSet<Block> = mutableSetOf()

        private fun getOrCreateControlFlowDependencyFor(block: Block) =
            controlflowDependencies.getOrPut(block) { mutableListOf() }


        fun getCurrentIrForBlock(block: Block): MutableList<CodeGenIR> {
            return getOrCreateControlFlowDependencyFor(block)
        }


        fun emitControlFlowDependencyFor(block: Block, codeGenIR: CodeGenIR) {
            getOrCreateControlFlowDependencyFor(block).also {
                println("deps: $it")
            }.add(codeGenIR)
        }

        fun setExitNode(block: Block, codeGenIR: CodeGenIR) {
            check(exitNodes[block] == null) { "setting exit node for $block, which already has an exit node" }
            getOrCreateControlFlowDependencyFor(block)
            exitNodes[block] = codeGenIR
        }

        fun setFinalCodeGenIrForBlock(block: Block, codeGenIR: CodeGenIR) {
            emitControlFlowDependencyFor(block, codeGenIR)
            finalizedBlocks.add(block)
        }

        fun setCodeGenIrForNode(node: Node, codeGenIR: CodeGenIR) {
            nodeMap[node] = codeGenIR
        }

        fun isFinalized() = controlflowDependencies.entries.all { finalizedBlocks.contains(it.key) }

        fun getCodeGenIRs(): Map<Block, List<CodeGenIR>> {
//            check(isFinalized()) { "inconsistent state of blockMap $blockMap" }
            return controlflowDependencies.mapValues { (block, irs) ->
                val exitNode = exitNodes[block] ?: error("no exit node for block $block")
                irs + exitNode
            }
        }

        fun getCodeGenFor(node: Node) = nodeMap[node]
    }

    override fun visit(node: End) {
        super.visit(node)
        setExitNodeFor(node, CodeGenIR.Jmp(NameMangling.mangleFunctionName(node.graph)))
    }

    companion object {
        fun buildTrees(graph: Graph, registerTable: VirtualRegisterTable): Map<Block, CodeGenIR> {
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
            return generationState.getCodeGenIRs().mapValues {
                it.value.toSeqChain()
            }
        }
    }

    private fun getCodeFor(node: Node): CodeGenIR =
        checkNotNull(generationState.getCodeGenFor(node)) { "Node $node has no associated CodeGenIR" }

    private inline fun setCodeFor(node: Node, buildCodeGen: () -> CodeGenIR): CodeGenIR =
        buildCodeGen().also { generationState.setCodeGenIrForNode(node, it.withOrigin(node)) }

    private fun emitControlDependency(node: Node, codeGenIR: CodeGenIR) {
        println("set control dependency for ${node.enclosingBlock} to $codeGenIR ($node)")
        generationState.emitControlFlowDependencyFor(node.enclosingBlock, codeGenIR.withOrigin(node))
    }

    private fun setExitNodeFor(node: Node, codeGenIR: CodeGenIR) {
        generationState.setExitNode(node.enclosingBlock, codeGenIR.withOrigin(node))
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
        val outNodes = BackEdges.getOuts(node).map { it.node }
        val controlFlowProjection = outNodes.firstNotNullOf {
            if (it is Proj && it.mode == Mode.getM()) {
                it
            } else {
                null
            }
        }

        val resultProjection = outNodes.firstNotNullOfOrNull {
            if (it is Proj && it.mode == Mode.getT()) {
                it
            } else {
                null
            }
        }


        val arguments = node.preds
            .filter { it.mode != Mode.getM() }
            .filter { it !is Address }
            .map { getCodeFor(it) }

        val addr = node.preds.firstNotNullOf {
            if (it is Address) {
                it
            } else {
                null
            }
        }

        if (resultProjection != null) {
            println("resultproject $resultProjection")
            val valueProjection = BackEdges.getOuts(resultProjection).first().node
            val reg = registerTable.getOrCreateRegisterFor(valueProjection)

            val codegenRef = CodeGenIR.RegisterRef(reg)

            setCodeFor(valueProjection) {
                codegenRef
            }
            emitControlDependency(
                node,
                CodeGenIR.Assign(codegenRef, CodeGenIR.Call(addr, arguments))
            )

        } else {
            setCodeFor(controlFlowProjection) {
                noop()
            }
            emitControlDependency(
                node,
                CodeGenIR.Call(addr, arguments)
            )

        }

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
            condition = getCodeFor(node.getPred(0)),
            trueLabel = NameMangling.mangleBlockName(trueBlock),
            falseLabel = NameMangling.mangleBlockName(falseBlock)
        )
        setExitNodeFor(node, cond)

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
        val jumpTarget = BackEdges.getOuts(node).first().node!! as Block
        setExitNodeFor(
            node,
            CodeGenIR.Jmp(NameMangling.mangleBlockName(jumpTarget))
        )

        println("visit JMP " + node.block.toString())
    }

    override fun visit(node: Load) {
        super.visit(node)

        println("node: $node")

        val outNodes = BackEdges.getOuts(node).map { it.node }
        val controlFlowProjection = outNodes.firstNotNullOf {
            if (it is Proj && it.mode == Mode.getM()) {
                it
            } else {
                null
            }
        }

        val resultProjection = outNodes.firstNotNullOfOrNull {
            if (it is Proj && it.mode == Mode.getIs()) {
                it
            } else {
                null
            }
        }
        // is the load value used?
        if (resultProjection != null) {
            val reg = registerTable.getOrCreateRegisterFor(resultProjection)

            val resultRegister = CodeGenIR.RegisterRef(reg)

            emitControlDependency(
                node, CodeGenIR.Assign(
                    resultRegister,
                    CodeGenIR.Indirection(getCodeFor(node.getPred(1)))
                )
            )

            setCodeFor(resultProjection) { resultRegister }
        } else {
            // load is unused
            // -> no need to generate control dependency
            // -> no data dependency needs to be generated, because it is not needed
        }


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
        println("visit MULH " + node.block.toString())
        error("mulh not implemented")
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
        if (node.mode == Mode.getM()) {
            // control dependencies, that span block boundaries don't need to be handled explicitly
            return
        }
        val phiRegisterRef = getCodeFor(node) // generated by PhiAssignRegisterVisitor

        val precedingBlocksNode = node.block.preds // node, that is part of the preceding block (according to red edges)
        precedingBlocksNode
            .zip(node.preds) // value nodes, that precede the phi node, which can be part of `node.block`
            .forEach { (precBlock, precNode) ->
                emitControlDependency(
                    precBlock,
                    CodeGenIR.Assign(phiRegisterRef, getCodeFor(precNode))
                )
            }
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
                // handled by load itself
            }
            is Store -> {
                // pred = Store | node = Proj M
                if (node.mode == Mode.getM()) {
                    val value = getCodeFor(pred.value)
                    val address = getCodeFor(pred.getPred(1))

                    emitControlDependency(
                        node,
                        CodeGenIR.Assign(lhs = CodeGenIR.Indirection(address), rhs = value)
                    )
                    setCodeFor(node) { noop() }
                } else {
                    error("unexpected node: $node")
                }
            }
            is Start -> setCodeFor(node) { getCodeFor(pred) }
            is Call -> {
                // handled by call
            }
            is Proj -> {
                // skip -> this is handled by next proj
            }
            is Cond -> {
                println(pred.toString())
            }
            else -> TODO("missing handling for case $pred")
        }
        println("visit PROJ ${node.toString()} " + node.block.toString())
    }

    override fun visit(node: Return) {
        super.visit(node)
        println("visit RETURN $node ${node.preds}")

        val nodePreds = node.preds.toList()
        val value = nodePreds.getOrNull(1)

        val code = if (value == null) {
            CodeGenIR.Return(noop())
        } else {
            CodeGenIR.Return(getCodeFor(value))
        }
        val endBlock = BackEdges.getOuts(node).first().node!! as Block

        setExitNodeFor(node, CodeGenIR.Seq(code, CodeGenIR.Jmp(NameMangling.mangleBlockName(endBlock))))
        println("visit RETURN " + node.block.toString())
    }

    override fun visit(node: Store) {
        super.visit(node)

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
