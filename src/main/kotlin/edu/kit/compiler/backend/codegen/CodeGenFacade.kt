package edu.kit.compiler.backend.codegen

import edu.kit.compiler.backend.assembly.AssemblyGenerator
import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.register.PlatformInstruction
import edu.kit.compiler.backend.register.PlatformTransformation
import firm.BackEdges
import firm.Graph
import firm.nodes.Block
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class CodeGenFacade(
    private val graphs: Iterable<Graph>,
    private val dumpCodeGenIR: Boolean,
    private val dumpMolkIR: Boolean,
) {
    internal lateinit var blocksWithLayout: Map<Graph, List<List<Instruction>>>
    private lateinit var codeGenIRs: Map<Graph, Map<Block, CodeGenIR>>
    private lateinit var molkiIr: Map<Graph, Map<Block, List<Instruction>>>
    private lateinit var platformCode: Map<Graph, List<PlatformInstruction>>
    private var registerTables: MutableMap<Graph, VirtualRegisterTable> = mutableMapOf()
    private var numberOfArguments: MutableMap<Graph, Int> = mutableMapOf()

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

            val registerTable = VirtualRegisterTable()
            registerTables[graph] = registerTable

            val functionVisitor = FunctionParameterVisitor(registerTable)
            graph.walkTopological(functionVisitor)
            val nodeMapping = functionVisitor.argumentMapping
            numberOfArguments[graph] = functionVisitor.numberOfArguments

            breakCriticalEdges(graph)

            BackEdges.enable(graph)

            val phiVisitor = PhiAssignRegisterVisitor(registerTable, nodeMapping)
            graph.walkTopological(phiVisitor)

            val generationState = FirmToCodeGenTranslator.GenerationState(
                registerTable = registerTable,
                nodeMap = phiVisitor.map,
            )
            graph.walkTopological(FirmToCodeGenTranslator(registerTable, generationState))
            generationState.createPhiMoves()

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

            functionGraph.mapValues { (_, block) ->
                block.transform(registerTable).instructions.build()
            }
        }
    }

    internal fun generateBlockLayout() {
        blocksWithLayout = molkiIr.mapValues { (graph, functionGraph) ->
            val instructions = mutableListOf<List<Instruction>>()
            fun addBlockInstructions(block: Block) {
                val blockInstructions = functionGraph[block] ?: error("no instructions for block $block")
                instructions.add(
                    listOf(Instruction.label(NameMangling.blockLabel(block))) + blockInstructions
                )
            }

            addBlockInstructions(graph.startBlock)
            graph.walkBlocksPostorder { block ->
                if (block != graph.startBlock && block != graph.endBlock) {
                    addBlockInstructions(block)
                }
            }
            addBlockInstructions(graph.endBlock)
            instructions.add(listOf(Instruction.label(NameMangling.functionReturnLabel(graph))))

            instructions
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
            graphPrinter.appendLine("node [shape=box];")
            tree.entries.forEach {
                graphPrinter.appendLine("subgraph ${graphPrinter.freshId()} {")
                graphPrinter.appendLine("label=\"${it.key}\";")
                val blockEntryId = graphPrinter.freshId()
                graphPrinter.appendLine("$blockEntryId[label=\"${it.key}\"];")
                val id = it.value?.toGraphViz(graphPrinter)
                graphPrinter.appendLine("$blockEntryId -> $id;")
                graphPrinter.appendLine("}")
            }
            graphPrinter.appendLine("}")
            val dot = graphPrinter.build()
            renderDotFile(filePrefix, dot)
        }
    }

    private fun dumpCodeGenIRIfEnabled() {
        if (!dumpCodeGenIR)
            return

        codeGenIRs.forEach { (graph, blocks) ->
            GraphvizPrinter.renderCodeGenIrsToFile(
                "graph-${graph.entity.ldName}",
                blocks.mapKeys { NameMangling.blockLabel(it.key) }
            )
        }
    }

    private fun dumpMolkIRIfEnabled() {
        if (!dumpMolkIR)
            return

        val molkiDumpFile = File("./out.molki")
        generateMolkiFile(molkiDumpFile)
    }

    fun generateMolkiFile(molkiFile: File) {
        molkiFile.printWriter().use { printer ->
            blocksWithLayout.forEach { (graph, block) ->
                printer.println(".function ${graph.entity.ldName} ${numberOfArguments[graph]!!} 1")
                block.flatten().forEach {
                    printer.print("    ${it.toMolki()}")
                    if (it is Instruction.Call) {
                        printer.print("   /* external ${it.external} */")
                    }
                    printer.println()
                }
                printer.println(".endfunction")
            }
        }
    }
}
