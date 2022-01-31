package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.assembly.AssemblyGenerator
import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.register.PlatformInstruction
import edu.kit.compiler.backend.register.PlatformTransformation
import firm.BackEdges
import firm.BlockWalker
import firm.Graph
import firm.nodes.Block
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class CodeGenFacade(
    val graphs: Iterable<Graph>,
    val dumpCodeGenIR: Boolean,
    val dumpMolkIR: Boolean,
) {
    lateinit var blocksWithLayout: Map<Graph, List<Instruction>>
    internal lateinit var codeGenIRs: Map<Graph, Map<Block, CodeGenIR>>
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

    internal fun generateCodeGenIR() {
        codeGenIRs = graphs.associateWith { graph ->
            println(graph.entity.ldName)

            val registerTable: VirtualRegisterTable = VirtualRegisterTable()
            registerTables[graph] = registerTable

            val functionVisitor = FunctionParameterVisitor(registerTable)
            graph.walkTopological(functionVisitor)
            val nodeMapping = functionVisitor.argumentMapping
            numberOfArguments[graph] = functionVisitor.numberOfArguments

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
        dumpCodeGenIRIfEnabled()
    }

    internal fun generateMolkiIr() {
        molkiIr = codeGenIRs.mapValues { (graph, functionGraph) ->
            val registerTable = registerTables[graph]!!

            functionGraph.mapValues { (firmBlock, block) ->
                block.transform(registerTable).instructions.build()
            }
        }
    }

    internal fun generateBlockLayout() {
        blocksWithLayout = molkiIr.mapValues { (graph, functionGraph) ->
            val blockLayouter = LayoutBlocks(mutableListOf(), functionGraph)
            graph.walkBlocksPostorder(blockLayouter)
            blockLayouter.finalize(graph)
            blockLayouter.instructions
        }
        dumpMolkIRIfEnabled()
    }

    internal fun generatePlatformCode() {
        platformCode = blocksWithLayout.mapValues { (graph, function) ->
            val callingConvention = if (graph.entity.ldName == "main") {
                PlatformTransformation.getExternalCallingConvention()
            } else {
                PlatformTransformation.getInternalCallingConvention()
            }
            PlatformTransformation.transformFunction(
                function,
                numberOfArguments[graph]!!,
                // TODO: use externalCallingConvention for main
                callingConvention
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
            instructions.add(Instruction.Label(NameMangling.mangleBlockName(block!!)))
            val currentInstructions = blockInstructions[block] ?: error("not instructions for block $block")
            instructions.addAll(currentInstructions)
        }

        fun finalize(graph: Graph) {
            instructions.add(Instruction.Label(NameMangling.mangleFunctionName(graph)))
        }
    }

    object GraphvizPrinter {

        private fun renderDotFile(filePrefix: String, dot: String) {
            val file = Files.createTempFile("graph-$filePrefix", ".dot").toFile()
            file.writeText(dot)
            println("Dot-File: $file")
            try {
                val dotProcess = ProcessBuilder("dot", file.absolutePath, "-T", "svg").start()
                val svgText = dotProcess.inputStream.bufferedReader().readText()
                val output = File("./graph-$filePrefix.svg")
                output.writeText(svgText)
                println("write graph-file to file://${output.absolutePath}")
                println("                    http://vps.csicar.de:8000/${output.name}")
            } catch (ex: IOException) {
                println("rendering graph with dot failed: $ex")
            }
        }

        fun renderCodeGenIrsToFile(filePrefix: String, tree: Map<String, CodeGenIR?>) {
            val graphPrinter = GraphVizBuilder()
            graphPrinter.appendLine("digraph {")
            tree.entries.forEach {
                graphPrinter.appendLine("subgraph ${graphPrinter.freshId()} {")
                graphPrinter.appendLine("label=\"${it.key}\";")
                val blockEntryId = graphPrinter.freshId()
                graphPrinter.appendLine("$blockEntryId[label=\"Block ${it.key}\"];")
                val id = it.value?.toGraphViz(graphPrinter)
                graphPrinter.appendLine("$blockEntryId -> $id;")
                graphPrinter.appendLine("}")
            }
            graphPrinter.appendLine("}")
            val dot = graphPrinter.build()
            renderDotFile(filePrefix, dot)
        }
    }

    fun dumpCodeGenIRIfEnabled() {
        if (!dumpCodeGenIR)
            return

        codeGenIRs.forEach { (graph, blocks) ->
            GraphvizPrinter.renderCodeGenIrsToFile("graph-${graph.entity.ldName}", blocks.mapKeys { "BB ${it.key.nr}" })
        }
    }

    fun dumpMolkIRIfEnabled() {
        if (!dumpMolkIR)
            return

        val molkiDumpFile = File("./out.molki")
        molkiDumpFile.printWriter().use { printer ->
            blocksWithLayout.forEach { (graph, block) ->
                printer.println(".function ${graph.entity.ldName} ${numberOfArguments[graph]!!} 1")
                block.forEach {
                    printer.print("    ${it.toMolki()}")
                    if(it is Instruction.Call) {
                        printer.print("   ; external ${it.external}")
                    }
                    printer.println()
                }
                printer.println(".endfunction")
            }
        }
    }
}
