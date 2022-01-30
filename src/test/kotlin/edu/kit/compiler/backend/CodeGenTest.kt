package edu.kit.compiler.backend

import com.tylerthrailkill.helpers.prettyprint.pp
import edu.kit.compiler.ast.validate
import edu.kit.compiler.backend.codegen.BinOpENUM
import edu.kit.compiler.backend.codegen.CodeGenFacade
import edu.kit.compiler.backend.codegen.CodeGenIR
import edu.kit.compiler.backend.codegen.FirmToCodeGenTranslator
import edu.kit.compiler.backend.codegen.GraphVizBuilder
import edu.kit.compiler.backend.codegen.VirtualRegisterTable
import edu.kit.compiler.backend.codegen.toGraphViz
import edu.kit.compiler.backend.codegen.transform
import edu.kit.compiler.backend.molkir.Instruction
import edu.kit.compiler.backend.molkir.MolkIR
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.lexer.Lexer
import edu.kit.compiler.lexer.StringTable
import edu.kit.compiler.lexer.initializeKeywords
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.semantic.doSemanticAnalysis
import edu.kit.compiler.source.SourceFile
import edu.kit.compiler.transform.Transformation
import firm.Dump
import firm.Entity
import firm.Graph
import firm.Program
import firm.Util
import firm.nodes.Block
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CodeGenTest {

    private fun dumpGraphs(phase: String) {
        Program.getGraphs().forEach { Dump.dumpGraph(it, phase) }
    }

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

    private fun renderCodeGenIrsToFile(filePrefix: String, tree: Map<String, CodeGenIR?>) {
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

    private fun generateGraph(code: String,
                              registerTable: VirtualRegisterTable = VirtualRegisterTable()
    ): List<Graph> {
        val sourceFile = SourceFile.from("/path/to/file", code)
        val stringTable = StringTable(StringTable::initializeKeywords)
        val lexer = Lexer(sourceFile, stringTable)
        val parser = Parser(sourceFile, lexer.tokens())
        val program = parser.parse().also {
            sourceFile.printAnnotations()
        }.validate()!!
        doSemanticAnalysis(program, sourceFile, stringTable)
        Transformation.transform(program, stringTable)
        dumpGraphs("after-construction")
        Util.lowerSels()
        dumpGraphs("after-lowering")
        return Program.getGraphs()!!.toList()
    }

    private fun setupGraph(
        code: String,
        registerTable: VirtualRegisterTable = VirtualRegisterTable()
    ): CodeGenFacade {
        val graphs = generateGraph(code, registerTable)
        val codegen = CodeGenFacade(graphs)
        codegen.generateCodeGenIR()
        codegen.codeGenIRs.forEach { (graph, blockMap) ->
            renderCodeGenIrsToFile(graph.entity.ldName, blockMap.mapKeys {
                it.key.toString()
                    .replace("Block BB[", "")
                    .replace(" ", "")
                    .replace("]", "")
                    .replace(":", "-")
            })
        }
        return codegen
    }

    @Test
    fun testBasic() {
        setupGraph(
            """
            class Test {
                public int i;
                public static void main(String[] args) {}

                public int test() { return System.in.read() +1 ;}
            }
            """.trimIndent()
        )
    }

    @Test
    fun testPhi() {
        setupGraph(
            """
                class Test {
                    public static void main(String [] args) {
                        int i = System.in.read();
                        int j = 0;
                        while(j < 10) {
                            j = j + i;
                        }
                        System.out.println(j);
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testStore() {
        setupGraph(
            """
                class Test {
                    public int field;
                    public void test() {
                        int j = 9;
                        if(field > 2) {
                            j = 2;
                        } else {
                            j = 5;
                        }
                    }
                    public static void main(String [] args) {}
                }
            """.trimIndent()
        )
    }

    @Test
    fun testStatementSeq() {
        setupGraph(
            """
            class Test {
                public int i;

                public static void main(String[] args) {
                    int a = System.in.read();
                    System.out.println(a);
                    System.out.println(8);
                }

                public void inc() {
                    i=i+1;
                }


            }
            """.trimIndent()
        )
    }

    @Test
    fun testStatementReuse() {
        setupGraph(
            """
            class Test {
                public int i;

                public static void main(String[] args) {
                    Test test = new Test();
                    test.i =   System.in.read() +  System.in.read();
                    int a = System.in.read();
                    System.out.println(a);
                    System.out.println(a);
                }


            }
            """.trimIndent()
        )
    }

    @Test
    fun testCall() {
        setupGraph(
            """
                class Test {
                    public static void main(String[] args) {}
                    public int foo(int x) { return 2+x; }
                    public int bar(int y, int z) { return foo(3+y)+2; }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testDiv() {
        setupGraph(
            """
                class Test {
                    public static void main(String[] args) {
                        int i = 10 / 2;
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testMod() {
        setupGraph(
            """
                class Test {
                    public static void main(String[] args) {
                        int i = 10 % 2;
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testCond() {
        setupGraph(
            """
                class Test {
                    public int i;
                    public static void main(String[] args) {
                        Test tester = new Test();
                        tester.i = tester.test();
                        while (true) {
                            System.out.println(tester.i);
                        }
                    }

                    public int test() {
                        System.out.println(1);
                        System.out.println(2);
                        if (3 == 2) {
                            System.out.println(1);
                            return 2;
                        } else {
                            return 3;
                        }
                    }
                }
            """.trimIndent()
        )
    }


    private fun transformToMolki(
        registerTable: VirtualRegisterTable = VirtualRegisterTable(),
        block: (VirtualRegisterTable) -> CodeGenIR
    ): List<Instruction> {
        val res = block(registerTable).transform(registerTable)
        println("codegen tree:")
        res.node.pp()
        println("molki output:")
        val instructions = res.instructions.build()
        instructions.forEach { it.toMolki().pp() }
        assertNotNull(res, "expect global MatchResult not be non-null")
        return instructions
    }

    private fun assertMolkiEquals(actual: List<MolkIR>, expected: List<String>) {
        assertEquals(expected, actual.map { it.toMolki() })
    }

    @Test
    fun testBothRead() {
        val registerTable = VirtualRegisterTable()
        val fascade = setupGraph(
            """
            class Test {
                    public int i;
                    public static void main(String[] args) {}

                    public int t() { return i; }
                    /*public int inc() { return i=i+1; }

                     public int test() {
                        System.out.println(1);
                        System.out.println(2);
                        if (3 == 2) {
                            System.out.println(1);
                            return 2;
                        } else {
                            return 3;
                        }
                    }*/
            }
        """.trimIndent(), registerTable
        )

        fascade.generateMolkiIr()
        fascade.generateBlockLayout()
        fascade.blocksWithLayout.forEach { (graph, block) ->
            println("## ${graph.entity.ldName}")
            block.forEach { println("   ${it.toMolki()}")
            }
        }

    }

    @Test
    fun testReplacementSystemAdd() {
        val res = transformToMolki() {
            CodeGenIR.BinOP(BinOpENUM.ADD, CodeGenIR.Const("2", Width.QUAD), CodeGenIR.Const("3", Width.QUAD))
        }
        assertMolkiEquals(
            res, listOf(
                "movq $2, %@0",
                "movq $3, %@1",
                "addq [ %@0 | %@1 ] -> %@2"
            )
        )
    }

    @Test
    fun testNestedAdd() {

        val res = transformToMolki() {
            CodeGenIR.BinOP(
                BinOpENUM.ADD,
                CodeGenIR.Const("22", Width.QUAD),
                CodeGenIR.BinOP(BinOpENUM.ADD, CodeGenIR.Const("33", Width.QUAD), CodeGenIR.Const("44", Width.QUAD))
            )
        }
        assertMolkiEquals(
            res, listOf(
                "movq $22, %@0",
                "movq $33, %@1",
                "movq $44, %@2",
                "addq [ %@1 | %@2 ] -> %@3",
                "addq [ %@0 | %@3 ] -> %@4",
            )
        )
    }

    @Test
    fun testSeq() {
        val res = transformToMolki {
            CodeGenIR.Seq(
                value = CodeGenIR.Const("0", Width.DOUBLE),
                exec = CodeGenIR.Const("0", Width.WORD)
            )
        }
        assertMolkiEquals(
            res, listOf(
                "movq $0, %@0w",
                // "jmp functionReturn" //TODO test this
            )
        )
    }

    @Test
    fun testReplacementReturnReg() {
        val res = transformToMolki {
            CodeGenIR.Seq(
                value = CodeGenIR.Return(CodeGenIR.RegisterRef(it.newRegister(Width.QUAD))),
                exec = CodeGenIR.Const("0", Width.WORD)
            )
        }
        assertMolkiEquals(
            res, listOf(
                "movq %@0, %@r0",
                // "jmp functionReturn" //TODO test this
            )
        )
    }

    @Test
    fun testReplacementReturnConst() {
        val res = transformToMolki {
            CodeGenIR.Return(CodeGenIR.Const("0", Width.WORD))
        }
        assertMolkiEquals(
            res, listOf(
                "movq $0, %@r0w",
            )
        )
    }

    @Test
    fun testReplacementReturnSum() {
        val res = transformToMolki {
            CodeGenIR.Return(
                CodeGenIR.BinOP(
                    BinOpENUM.ADD,
                    left = CodeGenIR.Const("7", Width.WORD),
                    right = CodeGenIR.RegisterRef(it.newRegister(Width.WORD))
                )
            )
        }
        assertMolkiEquals(
            res, listOf(
                "movq $7, %@1w",
                "addq [ %@1w | %@0w ] -> %@2w",
                "movq %@2w, %@r0w"
            )
        )
    }

    @Test
    fun testReplacementReturnNoop() {
        val res = transformToMolki {
            CodeGenIR.Return(
                CodeGenIR.Seq(
                    value = CodeGenIR.Const("0", Width.DOUBLE),
                    exec = CodeGenIR.Const("0", Width.WORD)
                )
            )
        }
        assertMolkiEquals(
            res, listOf(
                "movq $0 %@r0",
                "jmp functionReturn"
            )
        )
    }

    @Test
    fun testReplacementSystem() {
        val res = transformToMolki() {
            CodeGenIR.BinOP(
                BinOpENUM.ADD,
                CodeGenIR.Const("2", Width.QUAD),
                CodeGenIR.RegisterRef(
                    Register(
                        id = it.newRegister(Width.QUAD).id,
                        width = Width.QUAD
                    )
                )
            )
        }
        assertMolkiEquals(
            res, listOf(
                "movq $2, %@1",
                "addq [ %@1 | %@0 ] -> %@2",
            )
        )
    }

    @Test
    fun testWithPlatform() {
        val graph = generateGraph("class Test { public static void main(String[] args) {} }")
        val codeGenFacade = CodeGenFacade(graph)
        val platformCodes = codeGenFacade.generate()
        platformCodes.forEach {  (graph, instructions) ->
            println("graph: ${graph.entity.ldName}")
            codeGenFacade.blocksWithLayout[graph]!!.map { it.toMolki() }.pp()
            println(instructions.joinToString("\n   ") { it.toAssembler() })
        }
    }


}
