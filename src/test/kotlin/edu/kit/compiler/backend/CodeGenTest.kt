package edu.kit.compiler.backend

import com.tylerthrailkill.helpers.prettyprint.pp
import edu.kit.compiler.ast.validate
import edu.kit.compiler.backend.codegen.BinOpENUM
import edu.kit.compiler.backend.codegen.CodeGenIR
import edu.kit.compiler.backend.codegen.FirmToCodeGenTranslator
import edu.kit.compiler.backend.codegen.GraphPrinter
import edu.kit.compiler.backend.codegen.MatchResult
import edu.kit.compiler.backend.codegen.ReplacementSystem
import edu.kit.compiler.backend.codegen.VirtualRegisterTable
import edu.kit.compiler.backend.molkir.MolkIR
import edu.kit.compiler.backend.molkir.Register
import edu.kit.compiler.backend.molkir.Width
import edu.kit.compiler.initializeKeywords
import edu.kit.compiler.lexer.Lexer
import edu.kit.compiler.lexer.StringTable
import edu.kit.compiler.parser.Parser
import edu.kit.compiler.semantic.doSemanticAnalysis
import edu.kit.compiler.source.SourceFile
import firm.Dump
import firm.Program
import firm.Util
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

    fun setupGraph(code: String) {
        val sourceFile = SourceFile.from("/path/to/file", code)
        val stringTable = StringTable(StringTable::initializeKeywords)
        val lexer = Lexer(sourceFile, stringTable)
        val parser = Parser(sourceFile, lexer.tokens())
        val program = parser.parse().also {
            sourceFile.printAnnotations()
        }.validate()!!
        doSemanticAnalysis(program, sourceFile, stringTable)
//        Transformation.transform(program)
        dumpGraphs("after-construction")
        Util.lowerSels()
        dumpGraphs("after-lowering")
        Program.getGraphs().forEach {
            val generator = FirmToCodeGenTranslator(it)
            val blocks = generator.buildTrees()
            blocks.values.forEach {
                val file = Files.createTempFile("graph-", ".dot").toFile()
                file.writeText(
                    "digraph {${it?.toGraphviz(0, GraphPrinter())}}"
                )
                try {

                    val dotProcess = ProcessBuilder("dot", file.absolutePath, "-T", "svg").start()
                    val svgText = dotProcess.inputStream.bufferedReader().readText()
                    val output = File("./graph.svg")
                    output.writeText(svgText)
                    println("write graph-file to ${output.absolutePath}")
                } catch (ex: IOException) {
                    println("rendering graph with dot failed: $ex")
                }
            }
        }
    }

    @Test
    fun testClausesNPE() {
        setupGraph("class clazz { public static void main(String[] args) {test();} public int test() {return 23+3; } }")
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
    fun testStatementSeq() {
        setupGraph(
            """
            class Test {
                public int i;

                public static void main(String[] args) {
                    int a = System.in.read();
                    System.out.println(a);
                    System.out.println(a);
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
                    /*int a = System.in.read();
                    System.out.println(a);
                    System.out.println(a); */

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

    private fun transformToMolki(block: (VirtualRegisterTable) -> CodeGenIR): MatchResult {
        val registerTable = VirtualRegisterTable()
        val res = block(registerTable).accept(ReplacementSystem(registerTable))
        println("codegen tree:")
        res?.replacement.pp()
        println("molki output:")
        res?.instructions?.forEach { it.toMolki().pp() }
        assertNotNull(res, "expect global MatchResult not be non-null")
        return res
    }

    private fun assertMolkiEquals(actual: List<MolkIR>, expected: List<String>) {
        assertEquals(actual.map { it.toMolki() }, expected)
    }

    @Test
    fun testReplacementSystemConst() {
        val instructions = transformToMolki() {
            CodeGenIR.Const("2")
        }
    }

    @Test
    fun testReplacementSystemAdd() {
        val res = transformToMolki() {
            CodeGenIR.BinOP(BinOpENUM.ADD, CodeGenIR.Const("2"), CodeGenIR.Const("3"))
        }
        assertMolkiEquals(
            res.instructions,
            listOf(
                "movq 2(), %@0",
                "movq 3(), %@1",
                "addq [ %@0 | %@1 ] -> %@2"
            )
        )
    }

    @Test
    fun testNestedAdd() {

        val res = transformToMolki() {
            CodeGenIR.BinOP(
                BinOpENUM.ADD,
                CodeGenIR.Const("22"),
                CodeGenIR.BinOP(BinOpENUM.ADD, CodeGenIR.Const("33"), CodeGenIR.Const("44"))
            )
        }
        assertMolkiEquals(
            res.instructions,
            listOf(
                "movq 22(), %@0",
                "movq 33(), %@1",
                "movq 44(), %@2",
                "addq [ %@1 | %@2 ] -> %@3",
                "addq [ %@0 | %@3 ] -> %@4",
            )
        )
    }

    @Test
    fun testReplacementSystem() {
        val res = transformToMolki() {
            CodeGenIR.BinOP(
                BinOpENUM.ADD,
                CodeGenIR.Const("2"),
                CodeGenIR.RegisterRef(
                    Register(
                        id = it.newRegister(Width.QUAD).id,
                        width = Width.QUAD
                    )
                )
            )
        }
        assertMolkiEquals(
            res.instructions,
            listOf(
                "movq 2(), %@1",
                "addq [ %@1 | %@0 ] -> %@2",
            )
        )
    }
}
