package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.assembly.AssemblyGenerator
import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.register.PlatformInstruction
import edu.kit.compiler.backend.register.PlatformTransformation
import firm.BackEdges
import firm.BlockWalker
import firm.Entity
import firm.Graph
import firm.nodes.Block
import java.nio.file.Path

class CodeGenFacade(val graphs: Iterable<Graph>) {
    lateinit var blocksWithLayout: Map<Graph, List<Instruction>>
    lateinit var codeGenIRs: Map<Graph, Map<Block, CodeGenIR>>
    lateinit var molkiIr: Map<Graph, Map<Block, List<Instruction>>>
    lateinit var platformCode: Map<Graph, List<PlatformInstruction>>
    var registerTables: MutableMap<Graph, VirtualRegisterTable> = mutableMapOf()
    var numberOfArguments: MutableMap<Graph, Int> = mutableMapOf()

    fun generate(): Map<Graph, List<PlatformInstruction>> {
        generateCodeGenIR()
        generateMolkiIr()
        generateBlockLayout()
        generatePlatformCode()

        return platformCode
    }

    fun generateAssemblyFile(assemblyFile: Path) {
        AssemblyGenerator(assemblyFile).use { assemblyGenerator ->
            platformCode.forEach { (graph, instructions) ->
                assemblyGenerator.generateFunction(graph.entity.ldName, instructions)
            }
        }
    }

    private fun generateCodeGenIR() {
        codeGenIRs = graphs.associateWith { graph ->
            println(graph.entity.ldName)

            val registerTable: VirtualRegisterTable = VirtualRegisterTable()
            registerTables[graph] = registerTable

            val nodeMapping = FunctionArgumentVisitor(registerTable).argumentMapping

            numberOfArguments[graph] = nodeMapping.size

            breakCriticalEdges(graph)
            val phiVisitor = PhiAssignRegisterVisitor(registerTable, nodeMapping)
            BackEdges.enable(graph)
            graph.walkTopological(phiVisitor)

            val generationState = FirmToCodeGenTranslator.GenerationState(nodeMap = phiVisitor.map)
            graph.walkTopological(FirmToCodeGenTranslator(graph, registerTable, generationState))

            BackEdges.disable(graph)

            generationState.getCodeGenIRs().mapValues {
                it.value.toSeqChain()
            }
        }
    }

    private fun generateMolkiIr() {
        molkiIr = codeGenIRs.mapValues { (graph, functionGraph) ->
            val registerTable = registerTables[graph]!!

            functionGraph.mapValues { (firmBlock, block) ->
                block.transform(registerTable).instructions.build()
            }
        }
    }

    private fun generateBlockLayout() {
        blocksWithLayout = molkiIr.mapValues { (graph, functionGraph) ->
            val blockLayouter = LayoutBlocks(mutableListOf(), functionGraph)
            graph.walkBlocksPostorder(blockLayouter)
            blockLayouter.finalize(graph.entity)
            blockLayouter.instructions
        }
    }

    private fun generatePlatformCode() {
        platformCode = blocksWithLayout.mapValues { (graph, function) ->


            PlatformTransformation.transformFunction(
                function,
                numberOfArguments[graph]!!,
                // TODO: use externalCallingConvention for main
                PlatformTransformation.getInternalCallingConvention()
            )

        }
    }

    /**
     * Lays out basic blocks of one function.
     * Usually called with walkPostorder
     */
    class LayoutBlocks(
        val instructions: MutableList<Instruction>,
        val blockInstructions: Map<Block, List<Instruction>>
    ) :
        BlockWalker {
        override fun visitBlock(block: Block?) {
            instructions.add(Instruction.Label("block_${block!!.nr}"))
            instructions.addAll(blockInstructions[block]!!)
        }

        fun finalize(entity: Entity) {
            instructions.add(Instruction.Label("function_return_${entity.ldName}"))
        }
    }
}
