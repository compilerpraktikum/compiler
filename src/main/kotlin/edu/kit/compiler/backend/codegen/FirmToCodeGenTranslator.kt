package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.optimization.FirmNodeVisitorAdapter
import firm.BackEdges
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
    private val registerTable: VirtualRegisterTable = VirtualRegisterTable(),
    private val generationState: GenerationState
) : FirmNodeVisitorAdapter() {

    class GenerationState(
        val registerTable: VirtualRegisterTable,
        private val controlFlowDependencies: MutableMap<Block, MutableList<CodeGenIR>> = mutableMapOf(),
        private val exitNodes: MutableMap<Block, CodeGenIR> = mutableMapOf(),
        var nodeMap: MutableMap<Node, CodeGenIR> = mutableMapOf()
    ) {
        private val phisInBlock = mutableMapOf<Block, MutableSet<Phi>>()

        private fun getOrCreateControlFlowDependencyFor(block: Block) =
            controlFlowDependencies.getOrPut(block) { mutableListOf() }

        fun emitControlFlowDependencyFor(block: Block, codeGenIR: CodeGenIR) {
            getOrCreateControlFlowDependencyFor(block).add(codeGenIR)
        }

        fun setExitNode(block: Block, codeGenIR: CodeGenIR) {
            check(exitNodes[block] == null) { "setting exit node for $block, which already has an exit node" }
            getOrCreateControlFlowDependencyFor(block)
            exitNodes[block] = codeGenIR
        }

        fun setCodeGenIrForNode(node: Node, codeGenIR: CodeGenIR) {
            nodeMap[node] = codeGenIR
        }

        fun getCodeGenIRs(): Map<Block, List<CodeGenIR>> {
//            check(isFinalized()) { "inconsistent state of blockMap $blockMap" }
            return controlFlowDependencies.mapValues { (block, irs) ->
                val exitNode = exitNodes[block] ?: error("no exit node for block $block")
                irs + exitNode
            }
        }

        fun createPhiMoves() {
            phisInBlock.forEach { (block, phis) ->
                block.preds.forEachIndexed { predIndex, predNode ->
                    val permutation = mutableListOf<Move>()

                    phis.forEach { phi ->
                        val phiCode = getCodeGenFor(phi) as CodeGenIR.RegisterRef
                        val phiPred = phi.getPred(predIndex)
                        val phiPredCode = getCodeGenFor(phiPred)!!
                        if (phiPredCode is CodeGenIR.RegisterRef) {
                            permutation.add(
                                Move(
                                    phiPredCode.register,
                                    phiCode.register,
                                )
                            )
                        } else {
                            /**
                             * The source is not a register, so might be a complex expression that uses multiple registers,
                             * which is not supported by [generateMoveSequence]. We also cannot store the result of that expression
                             * into the phi-register right now, because that register's old value may still be needed
                             * (for another calculation or phi-move). To solve this, we assign the calculation result to a temp
                             * register now and later move the value from the temp register to the phi-register as part of the
                             * generated move sequence.
                             */
                            val tempRegister = registerTable.newRegister(phiCode.register.width)
                            emitControlFlowDependencyFor(
                                predNode.enclosingBlock,
                                CodeGenIR.Assign(
                                    CodeGenIR.RegisterRef(tempRegister),
                                    phiPredCode
                                )
                            )
                            permutation.add(
                                Move(
                                    tempRegister,
                                    phiCode.register,
                                )
                            )
                        }
                    }
                    generateMoveSequence(permutation, registerTable::newRegister).forEach { (from, to) ->
                        emitControlFlowDependencyFor(
                            predNode.enclosingBlock,
                            CodeGenIR.Assign(
                                to = CodeGenIR.RegisterRef(to),
                                from = CodeGenIR.RegisterRef(from),
                            )
                        )
                    }
                }
            }
        }

        fun addPhi(block: Block, phi: Phi) {
            phisInBlock.computeIfAbsent(block) { mutableSetOf() }.add(phi)
        }

        fun getCodeGenFor(node: Node) = nodeMap[node]
    }

    override fun defaultVisit(node: Node) {
        error("unhandled node type: ${node::class.simpleName}")
    }
    override fun visit(node: Block) {}
    override fun visit(node: Address) {}
    override fun visit(node: Store) {} // handled in Proj

    override fun visit(node: End) {
        setExitNodeFor(node, CodeGenIR.Jmp(NameMangling.functionReturnLabel(node.graph)))
    }

    private fun getCodeFor(node: Node): CodeGenIR =
        checkNotNull(generationState.getCodeGenFor(node)) { "Node $node has no associated CodeGenIR" }

    private inline fun setCodeFor(node: Node, buildCodeGen: () -> CodeGenIR): CodeGenIR =
        buildCodeGen().also { generationState.setCodeGenIrForNode(node, it.withOrigin(node)) }

    private fun emitControlDependency(node: Node, codeGenIR: CodeGenIR) {
        generationState.emitControlFlowDependencyFor(node.enclosingBlock, codeGenIR.withOrigin(node))
    }

    private fun setExitNodeFor(node: Node, codeGenIR: CodeGenIR) {
        generationState.setExitNode(node.enclosingBlock, codeGenIR.withOrigin(node))
    }

    private fun buildBinOpTree(node: Binop, op: BinaryOpType) {
        setCodeFor(node) {
            CodeGenIR.BinaryOp(left = getCodeFor(node.left), right = getCodeFor(node.right), operation = op)
        }
    }

    override fun visit(node: Start) {
        setCodeFor(node) {
            Noop()
        }
    }

    override fun visit(node: Add) {
        buildBinOpTree(node, BinaryOpType.ADD)
    }

    override fun visit(node: And) {
        buildBinOpTree(node, BinaryOpType.AND)
    }

    override fun visit(node: Call) {
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

        val address = node.preds.firstNotNullOf {
            if (it is Address) {
                it
            } else {
                null
            }
        }

        if (resultProjection != null) {
            val valueProjection = BackEdges.getOuts(resultProjection).first().node
            val reg = registerTable.getOrCreateRegisterFor(valueProjection)

            val codegenRef = CodeGenIR.RegisterRef(reg)

            setCodeFor(valueProjection) {
                codegenRef
            }
            emitControlDependency(
                node,
                CodeGenIR.Assign(codegenRef, CodeGenIR.Call(address, arguments))
            )
        } else {
            setCodeFor(controlFlowProjection) {
                Noop()
            }
            emitControlDependency(
                node,
                CodeGenIR.Call(address, arguments)
            )
        }
    }

    override fun visit(node: Cmp) {
        setCodeFor(node) {
            CodeGenIR.Compare(node.relation, getCodeFor(node.left), getCodeFor(node.right))
        }
    }

    override fun visit(node: Cond) {
        val projs = BackEdges.getOuts(node).map { it.node as Proj }
        val trueProj = projs.find { it.num == Cond.pnTrue }
        val falseProj = projs.find { it.num == Cond.pnFalse }
        val trueBlock = BackEdges.getOuts(trueProj).iterator().next().node!! as Block
        val falseBlock = BackEdges.getOuts(falseProj).iterator().next().node!! as Block

        val cond = CodeGenIR.Cond(
            condition = getCodeFor(node.getPred(0)),
            trueLabel = NameMangling.blockLabel(trueBlock),
            falseLabel = NameMangling.blockLabel(falseBlock)
        )
        setExitNodeFor(node, cond)
    }

    override fun visit(node: Const) {
        setCodeFor(node) {
            CodeGenIR.Const(node.tarval.asLong().toString(), Width.fromByteSize(node.mode.sizeBytes)!!)
        }
    }

    override fun visit(node: Conv) {
        val from = node.op.mode
        val to = node.mode

        setCodeFor(node) {
            CodeGenIR.Conv(from, to, getCodeFor(node.op))
        }
    }

    override fun visit(node: Div) {
        // Div isn't from type BinOP
        setCodeFor(node) {
            CodeGenIR.Div(getCodeFor(node.left), getCodeFor(node.right))
        }
    }

    override fun visit(node: Eor) {
        buildBinOpTree(node, BinaryOpType.EOR)
    }

    override fun visit(node: Jmp) {
        val jumpTarget = BackEdges.getOuts(node).first().node!! as Block
        setExitNodeFor(
            node,
            CodeGenIR.Jmp(NameMangling.blockLabel(jumpTarget))
        )
    }

    override fun visit(node: Load) {
        val outNodes = BackEdges.getOuts(node).map { it.node }
        val resultProjection = outNodes.firstNotNullOfOrNull {
            if (it is Proj && it.mode != Mode.getM()) {
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
                node,
                CodeGenIR.Assign(
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
    }

    override fun visit(node: Minus) {
        setCodeFor(node) {
            CodeGenIR.UnaryOp(op = UnaryOpType.MINUS, value = getCodeFor(node.op))
        }
    }

    override fun visit(node: Mod) {
        setCodeFor(node) {
            CodeGenIR.Mod(left = getCodeFor(node.left), right = getCodeFor(node.right))
        }
    }

    override fun visit(node: Mul) {
        buildBinOpTree(node, BinaryOpType.MUL)
    }

    override fun visit(node: Not) {
        setCodeFor(node) {
            CodeGenIR.UnaryOp(op = UnaryOpType.NOT, value = getCodeFor(node.op))
        }
    }

    override fun visit(node: Or) {
        buildBinOpTree(node, BinaryOpType.OR)
    }

    override fun visit(node: Phi) {
        if (node.mode == Mode.getM()) {
            // control dependencies, that span block boundaries don't need to be handled explicitly
            return
        }
        generationState.addPhi(node.block as Block, node)
    }

    override fun visit(node: Proj) {
        when (val pred = node.pred) {
            is Div -> setCodeFor(node) { getCodeFor(pred) }
            is Mod -> setCodeFor(node) { getCodeFor(pred) }
            is Load -> {
                // handled by Load
            }
            is Store -> {
                // pred = Store | node = Proj M
                if (node.mode == Mode.getM()) {
                    val value = getCodeFor(pred.value)
                    val address = getCodeFor(pred.getPred(1))

                    emitControlDependency(
                        node,
                        CodeGenIR.Assign(to = CodeGenIR.Indirection(address), from = value)
                    )
                    setCodeFor(node) { Noop() }
                } else {
                    error("unexpected node: $node")
                }
            }
            is Start -> setCodeFor(node) { getCodeFor(pred) }
            is Call -> {
                // handled by Call
            }
            is Proj -> {
                // handled by next Proj
            }
            is Cond -> {
                // handled by Proj
            }
            else -> error("illegal state for pred: $pred")
        }
    }

    override fun visit(node: Return) {
        val nodePreds = node.preds.toList()
        val value = nodePreds.getOrNull(1)

        val code = if (value == null) {
            CodeGenIR.Return(Noop())
        } else {
            CodeGenIR.Return(getCodeFor(value))
        }
        val endBlock = BackEdges.getOuts(node).first().node!! as Block

        setExitNodeFor(node, CodeGenIR.Seq(code, CodeGenIR.Jmp(NameMangling.blockLabel(endBlock))))
    }

    override fun visit(node: Sub) {
        buildBinOpTree(node, BinaryOpType.SUB)
    }

    override fun visit(node: Shl) {
        buildBinOpTree(node, BinaryOpType.SHL)
    }

    override fun visit(node: Shr) {
        buildBinOpTree(node, BinaryOpType.SHR)
    }

    override fun visit(node: Shrs) {
        buildBinOpTree(node, BinaryOpType.SHRS)
    }

    override fun visit(node: Unknown) {
        setCodeFor(node) {
            CodeGenIR.Const("0", Width.fromByteSize(node.mode.sizeBytes)!!)
        }
    }
}
